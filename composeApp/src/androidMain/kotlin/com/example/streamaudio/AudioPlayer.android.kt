package com.example.streamaudio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack

actual class AudioPlayer actual constructor() {

    private var audioTrack: AudioTrack? = null
    private var isPlaying = false

    actual fun startAudio() {
        if (isPlaying) return

        val sampleRate = 48000
        val channelConfig = AudioFormat.CHANNEL_OUT_STEREO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT

        val minBufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        val bufferSize = maxOf(minBufferSize, 4096)

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(audioFormat)
                    .setSampleRate(sampleRate)
                    .setChannelMask(channelConfig)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(bufferSize)
            .build()

        audioTrack?.play()
        isPlaying = true
    }

    actual fun playAudio(data: ByteArray, size: Int) {
        if (!isPlaying) return
        audioTrack?.write(data, 0, size)
    }

    actual fun stop() {
        isPlaying = false
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        audioTrack = null
    }
}