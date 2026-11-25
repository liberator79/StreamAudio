package com.example.streamaudio

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.os.Build
import androidx.annotation.RequiresApi

/** Captures audio other apps are playing (Android 10+), via MediaProjection. */
@RequiresApi(Build.VERSION_CODES.Q)
internal class PlaybackCaptureSource(private val projection: MediaProjection) : AudioSource {
    override val name = "System audio"

    private var record: AudioRecord? = null

    @SuppressLint("MissingPermission") // RECORD_AUDIO is requested before hosting starts.
    override fun open() {
        val channelMask = AudioFormat.CHANNEL_IN_STEREO
        val encoding = AudioFormat.ENCODING_PCM_16BIT
        val minBuffer = AudioRecord.getMinBufferSize(StreamConfig.SAMPLE_RATE, channelMask, encoding)
        val bufferSize = maxOf(minBuffer * 2, StreamConfig.PACKET_BYTES * 8)

        val config = AudioPlaybackCaptureConfiguration.Builder(projection)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
            .build()

        val r = AudioRecord.Builder()
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(encoding)
                    .setSampleRate(StreamConfig.SAMPLE_RATE)
                    .setChannelMask(channelMask)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setAudioPlaybackCaptureConfig(config)
            .build()

        if (r.state != AudioRecord.STATE_INITIALIZED) {
            r.release()
            throw IllegalStateException("Couldn't open audio capture (is microphone permission granted?)")
        }
        r.startRecording()
        record = r
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val r = record ?: return -1
        val n = r.read(buffer, offset, length)
        return if (n < 0) -1 else n
    }

    override fun close() {
        record?.let { runCatching { it.stop() }; it.release() }
        record = null
    }
}
