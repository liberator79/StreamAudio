package com.example.streamaudio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build

actual class AudioPlayer actual constructor() {

    private var track: AudioTrack? = null

    actual val outputLatencyMs: Int
        get() = track?.let { it.bufferSizeInFrames * 1000 / StreamConfig.SAMPLE_RATE } ?: 0

    actual fun start() {
        if (track != null) return
        val channelMask = AudioFormat.CHANNEL_OUT_STEREO
        val encoding = AudioFormat.ENCODING_PCM_16BIT
        val minBuffer = AudioTrack.getMinBufferSize(StreamConfig.SAMPLE_RATE, channelMask, encoding)
        // Small device buffer; the receiver's jitter queue absorbs network timing instead.
        val bufferBytes = maxOf(minBuffer, StreamConfig.PACKET_BYTES * 4)

        val builder = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(encoding)
                    .setSampleRate(StreamConfig.SAMPLE_RATE)
                    .setChannelMask(channelMask)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(bufferBytes)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Requests the "fast" mixer path: noticeably less output delay on most phones.
            builder.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
        }

        track = builder.build().also {
            check(it.state == AudioTrack.STATE_INITIALIZED) { "Couldn't open the speaker" }
            it.play()
        }
    }

    actual fun write(data: ByteArray, offset: Int, length: Int) {
        track?.write(data, offset, length)
    }

    actual fun stop() {
        val t = track ?: return
        track = null
        runCatching { t.pause(); t.flush(); t.stop() }
        t.release()
    }
}
