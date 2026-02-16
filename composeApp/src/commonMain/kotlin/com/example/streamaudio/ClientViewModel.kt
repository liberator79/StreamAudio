package com.example.streamaudio

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ClientViewModel {
    private val client = AudioClient()
    private val player = AudioPlayer() // The Speaker Output

    // UI State
    private val _isConnected = MutableStateFlow(false)
    val isConnected = _isConnected.asStateFlow()

    private val _statusMessage = MutableStateFlow("Enter Host IP to Join")
    val statusMessage = _statusMessage.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO)

    fun toggleConnection(ipAddress: String) {
        if (_isConnected.value) {
            disconnect()
        } else {
            connect(ipAddress)
        }
    }

    private fun connect(ip: String) {
        if (ip.isBlank()) {
            _statusMessage.value = "Please enter a valid IP"
            return
        }

        _statusMessage.value = "Connecting to $ip..."

        scope.launch {
            try {
                player.startAudio()

                client.startListening(ip, 50005) { audioData, size ->
                    player.playAudio(audioData, size)
                }
                disconnect()
            } catch (e: Exception) {
                _statusMessage.value = "Connection Failed: ${e.message}"
                disconnect()
            }
        }
        _isConnected.value = true
        _statusMessage.value = "Connected! Listening..."
    }

    private fun disconnect() {
        _isConnected.value = false
        _statusMessage.value = "Disconnected"

        try {
            client.stopListening()
            player.stop()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}