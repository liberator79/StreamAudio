package com.example.streamaudio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.net.BindException

enum class HostStatus { Idle, Starting, Live, Error }

data class HostState(
    val status: HostStatus = HostStatus.Idle,
    val listeners: Int = 0,
    val sourceName: String = "",
    val level: Float = 0f,
    val message: String? = null,
)

/**
 * Pulls fixed 5 ms packets from an [AudioSource] on a dedicated audio-priority thread and
 * pushes them straight to the network: no queues, no coroutines, nothing to back up.
 */
class HostEngine(private val port: Int = StreamConfig.DEFAULT_PORT) {

    private class Session(val server: UdpAudioServer, val source: AudioSource, val netLock: AutoCloseable) {
        @Volatile var running = true
    }

    private val _state = MutableStateFlow(HostState())
    val state: StateFlow<HostState> = _state.asStateFlow()

    @Volatile private var session: Session? = null

    fun start(source: AudioSource) {
        synchronized(this) {
            if (session != null) return
            _state.value = HostState(status = HostStatus.Starting, sourceName = source.name)

            val server = try {
                UdpAudioServer(port) { count -> updateIfCurrent { it.copy(listeners = count) } }
            } catch (e: BindException) {
                _state.value = HostState(HostStatus.Error, message = "Port $port is already in use. Is another copy of StreamAudio hosting?")
                return
            } catch (e: Exception) {
                _state.value = HostState(HostStatus.Error, message = "Couldn't open the network socket: ${e.message}")
                return
            }

            val s = Session(server, source, holdLowLatencyNetwork("StreamAudio:host"))
            session = s
            Thread({ captureLoop(s) }, "StreamAudio-Capture").apply {
                isDaemon = true
                start()
            }
        }
    }

    fun stop() {
        val s = synchronized(this) {
            val current = session ?: return
            session = null
            current
        }
        s.running = false
        s.server.stop()
        runCatching { s.netLock.close() }
        _state.value = HostState()
    }

    /** Surfaces a problem that happened before capture could start (e.g. a permission was refused). */
    fun reportError(message: String) {
        synchronized(this) {
            if (session != null) return
            _state.value = HostState(HostStatus.Error, message = message)
        }
    }

    private fun captureLoop(s: Session) {
        boostAudioThreadPriority()
        try {
            s.source.open()
        } catch (e: Exception) {
            fail(s, e.message ?: "Couldn't start audio capture")
            runCatching { s.source.close() }
            return
        }

        updateIfCurrent(s) { it.copy(status = HostStatus.Live, sourceName = s.source.name, message = null) }

        val buffer = ByteArray(StreamConfig.PACKET_BYTES)
        var peak = 0f
        var lastLevelPublish = 0L
        try {
            while (s.running) {
                val read = s.source.read(buffer, 0, buffer.size)
                if (read < 0) throw IllegalStateException("The audio device stopped delivering data")
                if (read > 0) {
                    // Keep packets frame-aligned so a short read never shifts the channels.
                    val aligned = read - read % StreamConfig.BYTES_PER_FRAME
                    if (aligned > 0) {
                        s.server.sendAudio(buffer, 0, aligned)
                        peak = maxOf(peak, StreamProtocol.peakLevel(buffer, 0, aligned))
                    }
                }
                val now = System.currentTimeMillis()
                if (now - lastLevelPublish >= 50) {
                    val level = peak
                    updateIfCurrent(s) { it.copy(level = level) }
                    peak = 0f
                    lastLevelPublish = now
                }
            }
        } catch (e: Exception) {
            if (s.running) fail(s, e.message ?: "Audio capture failed")
        } finally {
            runCatching { s.source.close() }
        }
    }

    private fun fail(s: Session, message: String) {
        val wasCurrent = synchronized(this) {
            if (session === s) { session = null; true } else false
        }
        if (!wasCurrent) return
        s.running = false
        s.server.stop()
        runCatching { s.netLock.close() }
        _state.value = HostState(HostStatus.Error, sourceName = s.source.name, message = message)
    }

    private inline fun updateIfCurrent(s: Session? = null, transform: (HostState) -> HostState) {
        val current = session ?: return
        if (s != null && current !== s) return
        _state.update(transform)
    }
}
