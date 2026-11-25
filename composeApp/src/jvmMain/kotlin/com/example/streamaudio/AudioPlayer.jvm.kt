package com.example.streamaudio

import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.SourceDataLine

actual class AudioPlayer actual constructor() {

    private var line: SourceDataLine? = null

    actual val outputLatencyMs: Int
        get() = line?.let { it.bufferSize * 1000 / (StreamConfig.SAMPLE_RATE * StreamConfig.BYTES_PER_FRAME) } ?: 0

    actual fun start() {
        if (line != null) return
        val format = AudioFormat(StreamConfig.SAMPLE_RATE.toFloat(), 16, StreamConfig.CHANNELS, true, false)
        val info = DataLine.Info(SourceDataLine::class.java, format)
        // 40 ms device buffer: enough to ride out scheduler hiccups without underrun crackle
        // (the old 4 KB / 21 ms buffer underran constantly), small enough to keep delay low.
        val bufferBytes = StreamConfig.SAMPLE_RATE * StreamConfig.BYTES_PER_FRAME * 40 / 1000
        line = (AudioSystem.getLine(info) as SourceDataLine).apply {
            open(format, bufferBytes)
            start()
        }
    }

    actual fun write(data: ByteArray, offset: Int, length: Int) {
        line?.write(data, offset, length)
    }

    actual fun stop() {
        val l = line ?: return
        line = null
        // No drain(): it blocks until the buffer plays out, which made disconnecting sluggish.
        runCatching { l.stop(); l.flush(); l.close() }
    }
}
