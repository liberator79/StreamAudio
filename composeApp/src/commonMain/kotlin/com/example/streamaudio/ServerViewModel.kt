package com.example.streamaudio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ServerViewModel : ViewModel() {
    private val controller = createHostController()
    val state: StateFlow<HostState> = controller.state

    private val _addresses = MutableStateFlow<List<LocalAddress>>(emptyList())
    val addresses: StateFlow<List<LocalAddress>> = _addresses.asStateFlow()

    val port = StreamConfig.DEFAULT_PORT

    fun refreshAddresses() {
        viewModelScope.launch {
            _addresses.value = withContext(Dispatchers.IO) { HostDiscovery.localAddresses() }
        }
    }

    fun start() {
        refreshAddresses()
        controller.start()
    }

    fun stop() = controller.stop()
}
