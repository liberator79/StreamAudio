package com.example.streamaudio

import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.StateFlow

actual val platformTag: String = "android"

actual fun deviceName(): String {
    val ctx = AndroidPermissionHelper.context
    val userName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
        runCatching { Settings.Global.getString(ctx.contentResolver, Settings.Global.DEVICE_NAME) }.getOrNull()
    } else null
    return userName?.takeIf { it.isNotBlank() }
        ?: "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"
}

actual fun boostAudioThreadPriority() {
    runCatching { Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO) }
}

actual fun holdLowLatencyNetwork(tag: String): AutoCloseable {
    val ctx = AndroidPermissionHelper.context
    val held = mutableListOf<() -> Unit>()

    // Keep the CPU running with the screen off, otherwise the audio threads stall.
    (ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.let { pm ->
        val wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, tag).apply { setReferenceCounted(false) }
        if (runCatching { wake.acquire() }.isSuccess) held += { if (wake.isHeld) wake.release() }
    }

    (ctx.getSystemService(Context.WIFI_SERVICE) as? WifiManager)?.let { wifi ->
        // HIGH_PERF keeps Wi-Fi out of power save with the screen off (what media players use);
        // LOW_LATENCY (Android 10+) additionally cuts latency while the screen is on.
        @Suppress("DEPRECATION")
        val modes = buildList {
            add(WifiManager.WIFI_MODE_FULL_HIGH_PERF)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) add(WifiManager.WIFI_MODE_FULL_LOW_LATENCY)
        }
        for (mode in modes) {
            val lock = wifi.createWifiLock(mode, "$tag:$mode").apply { setReferenceCounted(false) }
            if (runCatching { lock.acquire() }.isSuccess) held += { if (lock.isHeld) lock.release() }
        }
    }
    return AutoCloseable { held.forEach { runCatching(it) } }
}

actual fun onListeningStarted() {
    val ctx = AndroidPermissionHelper.context
    runCatching {
        ContextCompat.startForegroundService(ctx, Intent(ctx, ListenService::class.java))
    }
}

actual val hostingUnsupportedReason: String? =
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) "Sharing audio needs Android 10 or newer" else null

/**
 * The single host instance for the process. The foreground service drives it; the UI only
 * observes [state] and asks the service to start or stop.
 */
object AndroidHost : HostController {
    val engine = HostEngine()
    override val state: StateFlow<HostState> = engine.state

    override fun start() {
        val ctx = AndroidPermissionHelper.context
        val intent = Intent(ctx, AudioStreamService::class.java).setAction(AudioStreamService.ACTION_START)
        ContextCompat.startForegroundService(ctx, intent)
    }

    override fun stop() {
        val ctx = AndroidPermissionHelper.context
        ctx.stopService(Intent(ctx, AudioStreamService::class.java))
        engine.stop()
    }
}

actual fun createHostController(): HostController = AndroidHost

actual object AppSettings {
    private val prefs by lazy {
        AndroidPermissionHelper.context.getSharedPreferences("stream_audio", Context.MODE_PRIVATE)
    }

    actual fun getString(key: String): String? = prefs.getString(key, null)
    actual fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }
}
