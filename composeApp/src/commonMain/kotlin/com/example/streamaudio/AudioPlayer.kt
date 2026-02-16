package com.example.streamaudio

expect class AudioPlayer() {
    fun startAudio()

    fun playAudio(data: ByteArray, size: Int)

    fun stop()
}