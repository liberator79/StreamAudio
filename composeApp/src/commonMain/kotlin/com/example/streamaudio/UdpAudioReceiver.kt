package com.example.streamaudio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

enum class ReceiverStatus { Idle, Connecting, Buffering, Playing, HostLost, Error }

data class ReceiverState(
    val status: ReceiverStatus = ReceiverStatus.Idle,
    val host: String = "",
    val latencyMs: Int = 0,
    val lossPercent: Float = 0f,
    val level: Float = 0f,
    val message: String? = null,
) {
    val isActive get() = status != ReceiverStatus.Idle && status != ReceiverStatus.Error
}

/**
 * Listener pipeline:
 *
 *   network thread ──► jitter queue ──► playback thread ──► speaker
 *
 * The network thread never blocks on the speaker, so the OS socket buffer never fills up with
 * stale audio. The playback thread keeps the queue near the [LatencyMode] target and throws
 * away surplus packets after a Wi-Fi burst or clock drift. That's what stops latency creeping up.
 */
class UdpAudioReceiver {

    private class Chunk(val data: ByteArray = ByteArray(StreamConfig.PACKET_BYTES), var length: Int = 0)

    private inner class Session(val host: String, val port: Int) {
        @Volatile var running = true
        @Volatile var socket: DatagramSocket? = null
        val queue = ArrayBlockingQueue<Chunk>(MAX_QUEUE)
        val pool = ArrayBlockingQueue<Chunk>(MAX_QUEUE + 8)

        // Stats (written by one thread each, read for display only).
        @Volatile var received = 0
        @Volatile var lost = 0
        @Volatile var lastPacketAt = 0L
        @Volatile var level = 0f
        @Volatile var outputLatencyMs = 0
        @Volatile var playing = false

        fun obtain(): Chunk = pool.poll() ?: Chunk()
        fun recycle(chunk: Chunk) { pool.offer(chunk) }
    }

    private val _state = MutableStateFlow(ReceiverState())
    val state: StateFlow<ReceiverState> = _state.asStateFlow()

    @Volatile private var session: Session? = null
    @Volatile var latencyMode: LatencyMode = LatencyMode.BALANCED

    fun start(host: String, port: Int = StreamConfig.DEFAULT_PORT) {
        stop()
        val s = Session(host.trim(), port)
        synchronized(this) { session = s }
        _state.value = ReceiverState(ReceiverStatus.Connecting, host = s.host)
        Thread({ networkLoop(s) }, "StreamAudio-Receive").apply { isDaemon = true; start() }
        Thread({ playbackLoop(s) }, "StreamAudio-Playback").apply { isDaemon = true; start() }
    }

    fun stop() {
        val s = synchronized(this) { session.also { session = null } } ?: return
        // The network thread notices within one receive timeout, says BYE and closes the socket.
        s.running = false
        _state.value = ReceiverState()
    }

    // ---------------------------------------------------------------- network

    private fun networkLoop(s: Session) {
        val netLock = holdLowLatencyNetwork("StreamAudio:listen")
        var byePacket: DatagramPacket? = null
        try {
            val address = InetAddress.getByName(s.host)
            val socket = DatagramSocket().apply {
                receiveBufferSize = 256 * 1024
                soTimeout = 200
                runCatching { trafficClass = 0xB8 }
            }
            s.socket = socket
            if (!s.running) { socket.close(); return }

            val hello = StreamProtocol.control(StreamProtocol.TYPE_HELLO)
            val helloPacket = DatagramPacket(hello, hello.size, address, s.port)
            val bye = StreamProtocol.control(StreamProtocol.TYPE_BYE)
            byePacket = DatagramPacket(bye, bye.size, address, s.port)
            val buffer = ByteArray(StreamProtocol.MAX_DATAGRAM + 64)
            val packet = DatagramPacket(buffer, buffer.size)

            var lastHello = 0L
            var lastStats = 0L
            var expectedSeq = 0
            var haveSeq = false
            var hostSaidBye = false

            while (s.running) {
                val now = System.currentTimeMillis()
                if (now - lastHello >= StreamConfig.HEARTBEAT_INTERVAL_MS) {
                    runCatching { socket.send(helloPacket) }
                    lastHello = now
                }
                if (now - lastStats >= 250) {
                    publishStats(s, hostSaidBye)
                    lastStats = now
                }

                try {
                    packet.setLength(buffer.size) // receive() shrinks length; reset or later packets get truncated
                    socket.receive(packet)
                } catch (_: SocketTimeoutException) {
                    continue
                }

                when (StreamProtocol.typeOf(buffer, packet.length)) {
                    StreamProtocol.TYPE_AUDIO -> {
                        hostSaidBye = false
                        val payload = packet.length - StreamProtocol.AUDIO_HEADER_SIZE
                        if (payload <= 0 || payload > StreamConfig.PACKET_BYTES) continue
                        val seq = StreamProtocol.readInt(buffer, StreamProtocol.HEADER_SIZE)

                        if (haveSeq) {
                            val delta = seq - expectedSeq
                            when {
                                delta < 0 && delta > -1000 -> continue // late / duplicate: already played past it
                                delta in 1..1000 -> s.lost += delta
                            }
                        }
                        haveSeq = true
                        expectedSeq = seq + 1
                        s.received++
                        s.lastPacketAt = now

                        val chunk = s.obtain()
                        System.arraycopy(buffer, StreamProtocol.AUDIO_HEADER_SIZE, chunk.data, 0, payload)
                        chunk.length = payload - payload % StreamConfig.BYTES_PER_FRAME
                        if (!s.queue.offer(chunk)) {
                            // Hard cap reached (playback stalled): drop the oldest to stay current.
                            s.queue.poll()?.let(s::recycle)
                            s.queue.offer(chunk)
                        }
                    }
                    StreamProtocol.TYPE_BYE -> hostSaidBye = true
                }
            }
        } catch (e: Exception) {
            if (s.running) {
                val message = when (e) {
                    is java.net.UnknownHostException -> "Couldn't find host \"${s.host}\""
                    is IOException -> "Network error: ${e.message}"
                    else -> e.message ?: "Connection failed"
                }
                fail(s, message)
            }
        } finally {
            runCatching { netLock.close() }
            s.socket?.let { socket ->
                byePacket?.let { runCatching { socket.send(it) } }
                socket.close()
            }
        }
    }

    private fun publishStats(s: Session, hostSaidBye: Boolean) {
        if (session !== s) return
        val silentFor = System.currentTimeMillis() - s.lastPacketAt
        val status = when {
            s.lastPacketAt == 0L -> ReceiverStatus.Connecting
            hostSaidBye || silentFor > 2500 -> ReceiverStatus.HostLost
            s.playing -> ReceiverStatus.Playing
            else -> ReceiverStatus.Buffering
        }
        val total = s.received + s.lost
        val loss = if (total > 0) s.lost * 100f / total else 0f
        // Reset loss counters periodically so the figure reflects recent conditions.
        if (total > 2000) { s.received = 0; s.lost = 0 }
        val latency = s.queue.size * StreamConfig.PACKET_MS + s.outputLatencyMs
        _state.update {
            it.copy(
                status = status,
                latencyMs = latency,
                lossPercent = loss,
                level = if (status == ReceiverStatus.Playing) s.level else 0f,
                message = null,
            )
        }
    }

    // ---------------------------------------------------------------- playback

    private fun playbackLoop(s: Session) {
        boostAudioThreadPriority()
        val player = AudioPlayer()
        try {
            player.start()
            s.outputLatencyMs = player.outputLatencyMs
            var primed = false
            var peak = 0f
            var lastLevel = 0L

            // Adaptive headroom on top of the chosen mode. Grows after jitter-induced dropouts
            // (e.g. Wi-Fi power save when the phone's screen is off), shrinks back when stable.
            var extra = 0
            var underrunAt = 0L
            var lastAdjust = System.currentTimeMillis()

            while (s.running) {
                val target = maxOf(2, latencyMode.targetMs / StreamConfig.PACKET_MS) + extra
                val slack = maxOf(4, target / 2)

                if (!primed) {
                    // Pre-buffer up to the target before (re)starting so we don't stutter.
                    if (s.queue.size >= target) {
                        primed = true
                        val now = System.currentTimeMillis()
                        // A short gap means the network stalled (not the host going quiet): add headroom.
                        if (underrunAt != 0L && now - underrunAt < 1500 && extra < MAX_EXTRA_PACKETS) {
                            extra = minOf(MAX_EXTRA_PACKETS, extra + 4)
                        }
                        lastAdjust = now
                    } else {
                        s.playing = false
                        Thread.sleep(2)
                        continue
                    }
                }

                // Latency control: if a burst piled up more than target+slack, skip back to target.
                if (s.queue.size > target + slack) {
                    while (s.queue.size > target) s.queue.poll()?.let(s::recycle)
                }

                val chunk = s.queue.poll(StreamConfig.PACKET_MS * 6L, TimeUnit.MILLISECONDS)
                if (chunk == null) {
                    primed = false // starved; rebuffer
                    s.playing = false
                    underrunAt = System.currentTimeMillis()
                    continue
                }
                if (extra > 0) {
                    val now = System.currentTimeMillis()
                    if (now - lastAdjust > 20_000) { // 20 s without a dropout: give back 10 ms
                        extra -= 2
                        lastAdjust = now
                    }
                }
                s.playing = true
                player.write(chunk.data, 0, chunk.length) // blocks at the device's real playback rate
                peak = maxOf(peak, StreamProtocol.peakLevel(chunk.data, 0, chunk.length))
                s.recycle(chunk)

                val now = System.currentTimeMillis()
                if (now - lastLevel >= 50) {
                    s.level = peak
                    peak = 0f
                    lastLevel = now
                }
            }
        } catch (_: InterruptedException) {
        } catch (e: Exception) {
            if (s.running) fail(s, "Audio output error: ${e.message}")
        } finally {
            player.stop()
        }
    }

    private fun fail(s: Session, message: String) {
        val wasCurrent = synchronized(this) {
            if (session === s) { session = null; true } else false
        }
        if (!wasCurrent) return
        s.running = false
        s.socket?.close()
        _state.value = ReceiverState(ReceiverStatus.Error, host = s.host, message = message)
    }

    private companion object {
        /** Hard ceiling of 1 s; the latency controller normally keeps it far lower. */
        const val MAX_QUEUE = 1000 / StreamConfig.PACKET_MS

        /** Adaptive headroom never exceeds 200 ms. */
        const val MAX_EXTRA_PACKETS = 200 / StreamConfig.PACKET_MS
    }
}
