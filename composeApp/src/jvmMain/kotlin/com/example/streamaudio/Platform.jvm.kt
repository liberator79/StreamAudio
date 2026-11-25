package com.example.streamaudio

import kotlinx.coroutines.flow.StateFlow
import java.net.InetAddress
import java.util.prefs.Preferences

actual val platformTag: String = "desktop"

actual fun deviceName(): String =
    System.getenv("COMPUTERNAME")
        ?: runCatching { InetAddress.getLocalHost().hostName }.getOrNull()
        ?: "Desktop"

actual fun boostAudioThreadPriority() {
    Thread.currentThread().priority = Thread.MAX_PRIORITY
}

actual fun holdLowLatencyNetwork(tag: String): AutoCloseable = AutoCloseable { }

actual fun onListeningStarted() = Unit

actual val hostingUnsupportedReason: String? = null

private object DesktopHost : HostController {
    private val engine = HostEngine()
    override val state: StateFlow<HostState> = engine.state
    override fun start() = engine.start(SystemAudioSource())
    override fun stop() = engine.stop()
}

actual fun createHostController(): HostController = DesktopHost

actual object AppSettings {
    private val prefs: Preferences = Preferences.userRoot().node("com/example/streamaudio")
    actual fun getString(key: String): String? = prefs.get(key, null)
    actual fun putString(key: String, value: String) {
        prefs.put(key, value)
        runCatching { prefs.flush() }
    }
}
