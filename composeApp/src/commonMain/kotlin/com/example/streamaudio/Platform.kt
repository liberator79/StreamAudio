package com.example.streamaudio

import kotlinx.coroutines.flow.StateFlow

/** Short platform tag sent in discovery replies ("android" / "desktop"). */
expect val platformTag: String

/** Human-friendly name of this device, shown to listeners during discovery. */
expect fun deviceName(): String

/** Raises the calling thread to audio priority. */
expect fun boostAudioThreadPriority()

/**
 * Keeps the network radio out of power-save while streaming. Wi-Fi power save is the
 * biggest source of latency spikes (100-300 ms bursts) on phones.
 */
expect fun holdLowLatencyNetwork(tag: String): AutoCloseable

/**
 * Called when the user starts listening. Android starts a foreground playback service so
 * audio keeps playing with the screen off; desktop does nothing.
 */
expect fun onListeningStarted()

/** The one receiver for the whole process, so playback isn't tied to a screen or ViewModel. */
object ListenerSession {
    val receiver = UdpAudioReceiver()
}

/** Null when this device can host; otherwise a user-facing reason why it can't. */
expect val hostingUnsupportedReason: String?

/** Platform entry point for broadcasting this device's audio. */
expect fun createHostController(): HostController

/** Tiny persistent key/value store for UI preferences. */
expect object AppSettings {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
}

interface HostController {
    val state: StateFlow<HostState>
    fun start()
    fun stop()
}

/**
 * A blocking source of 48 kHz / 16-bit / stereo PCM. All calls happen on the capture thread.
 */
interface AudioSource {
    val name: String

    /** Opens the device; throws with a user-readable message on failure. */
    fun open()

    /** Reads up to [length] bytes. Returns 0 if nothing arrived within ~100 ms, -1 if the source ended. */
    fun read(buffer: ByteArray, offset: Int, length: Int): Int

    fun close()
}
