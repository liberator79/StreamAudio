package com.example.streamaudio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ClientViewModel : ViewModel() {
    private val receiver = ListenerSession.receiver
    val state: StateFlow<ReceiverState> = receiver.state

    private val _hosts = MutableStateFlow<List<DiscoveredHost>>(emptyList())
    val hosts: StateFlow<List<DiscoveredHost>> = _hosts.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private val _latencyMode = MutableStateFlow(
        AppSettings.getString(KEY_LATENCY)?.let { saved -> LatencyMode.entries.firstOrNull { it.name == saved } }
            ?: LatencyMode.BALANCED
    )
    val latencyMode: StateFlow<LatencyMode> = _latencyMode.asStateFlow()

    val lastHost: String = AppSettings.getString(KEY_LAST_HOST).orEmpty()

    init {
        receiver.latencyMode = _latencyMode.value
    }

    fun scan() {
        if (_scanning.value) return
        _scanning.value = true
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { HostDiscovery.discover() }.getOrDefault(emptyList()) }
            _hosts.value = result
            _scanning.value = false
        }
    }

    fun connect(host: String) {
        val trimmed = host.trim()
        if (trimmed.isEmpty()) return
        AppSettings.putString(KEY_LAST_HOST, trimmed)
        receiver.start(trimmed)
        onListeningStarted()
    }

    fun disconnect() = receiver.stop()

    fun setLatencyMode(mode: LatencyMode) {
        _latencyMode.value = mode
        receiver.latencyMode = mode
        AppSettings.putString(KEY_LATENCY, mode.name)
    }

    // No onCleared() stop: playback outlives the screen (screen off, app swiped away). On Android
    // it ends via Disconnect in the app or the notification; on desktop when the app exits.

    private companion object {
        const val KEY_LATENCY = "latency_mode"
        const val KEY_LAST_HOST = "last_host"
    }
}
