package com.example.streamaudio

class AndroidAudioCapture : AudioCapturer {

    private val player = NetworkAudioPlayer()

    override fun startCapturing() {
        println()
    }

    override fun stopCapturing() {
        TODO("Not yet implemented")
    }
}