package com.example.streamaudio

import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.SourceDataLine

actual class AudioPlayer actual constructor() {

    private var sourceLine: SourceDataLine? = null
    private var isPlaying = false

    actual fun startAudio() {
        if (isPlaying) return

        try {
            val format = AudioFormat(48000f, 16, 2, true, false)
            val info = DataLine.Info(SourceDataLine::class.java, format)

            if (!AudioSystem.isLineSupported(info)) return

            sourceLine = AudioSystem.getLine(info) as SourceDataLine

            sourceLine?.open(format, 4096)
            sourceLine?.start()
            isPlaying = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    actual fun playAudio(data: ByteArray, size: Int) {
        if (!isPlaying) return
        sourceLine?.write(data, 0, size)
    }

    actual fun stop() {
        isPlaying = false
        try {
            sourceLine?.drain()
            sourceLine?.stop()
            sourceLine?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        sourceLine = null
    }
}