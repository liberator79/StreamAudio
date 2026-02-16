package com.example.streamaudio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

class ServerViewModel {
    private val streamer = AudioStreamer()
    private val _isStreaming = MutableStateFlow(false)
    val isStreaming = _isStreaming.asStateFlow()

    private val _serverIp = MutableStateFlow("Loading IP...")
    val serverIp = _serverIp.asStateFlow()

    private val _statusMessage = MutableStateFlow("Ready to Host")
    val statusMessage = _statusMessage.asStateFlow()

    init {
        _serverIp.value = streamer.getServerIp()
    }

    fun toggleStreaming() {
        if (_isStreaming.value) {
            stopServer()
        } else {
            startServer()
        }
    }

    private fun startServer() {
        try {
            val port = 50005
            streamer.startStreaming(port)
            _isStreaming.value = true
            _statusMessage.value = "Streaming on Port $port"
        } catch (e: Exception) {
            _statusMessage.value = "Error: ${e.message}"
            _isStreaming.value = false
        }
    }

    private fun stopServer() {
        streamer.stopStreaming()
        _isStreaming.value = false
        _statusMessage.value = "Server Stopped"
    }
}