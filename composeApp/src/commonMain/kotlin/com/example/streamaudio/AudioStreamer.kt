package com.example.streamaudio

expect class AudioStreamer() {
    fun startStreaming(port : Int = 50005)

    fun stopStreaming()

    fun isStreaming() : Boolean

    fun getServerIp() : String
}