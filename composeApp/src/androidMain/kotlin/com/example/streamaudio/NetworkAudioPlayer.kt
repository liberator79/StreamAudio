package com.example.streamaudio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.Socket

class NetworkAudioPlayer {

    private var isPlaying = false
    private var socket: Socket? = null

    // MUST match the Server's format (48000Hz)
    private val sampleRate = 48000

    suspend fun startPlaying(serverIp: String, port: Int = 50005) {
        withContext(Dispatchers.IO) {
            try {
                // 1. Configure the Player (AudioTrack)
                val bufferSize = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_STEREO,
                    AudioFormat.ENCODING_PCM_16BIT
                ) * 2 // Slightly larger buffer for network stability

                val audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                // 2. Connect to Windows PC
                println("Connecting to $serverIp...")
                socket = Socket(serverIp, port)
                val inputStream = socket?.getInputStream() ?: return@withContext

                audioTrack.play()
                isPlaying = true

                println("🎵 Connected! Playing Audio...")

                // 3. Read Network -> Write to Speakers
                val buffer = ByteArray(4096)
                while (isPlaying && socket?.isConnected == true) {
                    val bytesRead = inputStream.read(buffer)
                    if (bytesRead > 0) {
                        audioTrack.write(buffer, 0, bytesRead)
                    }
                }

                audioTrack.stop()
                audioTrack.release()

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun stop() {
        isPlaying = false
        socket?.close()
    }
}