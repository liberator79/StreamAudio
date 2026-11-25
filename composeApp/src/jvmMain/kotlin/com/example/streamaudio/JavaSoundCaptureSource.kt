package com.example.streamaudio

import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.TargetDataLine

/** Fallback capture through a loopback-style recording device such as "Stereo Mix" or a PulseAudio monitor. */
internal class JavaSoundCaptureSource : AudioSource {
    override var name = "Stereo Mix"
        private set

    private var line: TargetDataLine? = null

    override fun open() {
        val format = AudioFormat(StreamConfig.SAMPLE_RATE.toFloat(), 16, StreamConfig.CHANNELS, true, false)
        val info = DataLine.Info(TargetDataLine::class.java, format)
        val keywords = listOf("stereo mix", "what u hear", "wave out", "loopback", "monitor of")

        val mixerInfo = AudioSystem.getMixerInfo().firstOrNull { mi ->
            val label = mi.name.lowercase()
            keywords.any { it in label } && AudioSystem.getMixer(mi).isLineSupported(info)
        } ?: throw IllegalStateException(
            "No system-audio capture device found. On Windows, enable \"Stereo Mix\" in Sound settings → Recording."
        )

        name = mixerInfo.name.substringBefore('(').trim().ifEmpty { mixerInfo.name }
        line = (AudioSystem.getMixer(mixerInfo).getLine(info) as TargetDataLine).apply {
            open(format, StreamConfig.PACKET_BYTES * 8)
            start()
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val l = line ?: return -1
        return l.read(buffer, offset, length)
    }

    override fun close() {
        line?.let { runCatching { it.stop(); it.close() } }
        line = null
    }
}

/** Tries WASAPI loopback first (Windows), then any JavaSound loopback device. */
internal class SystemAudioSource : AudioSource {
    private var delegate: AudioSource? = null
    override val name: String get() = delegate?.name ?: "System audio"

    override fun open() {
        val errors = mutableListOf<String>()
        val candidates = buildList {
            if (com.sun.jna.Platform.isWindows()) add(::WasapiLoopbackSource)
            add(::JavaSoundCaptureSource)
        }
        for (create in candidates) {
            val source = create()
            try {
                source.open()
                delegate = source
                return
            } catch (e: Throwable) {
                runCatching { source.close() }
                errors += e.message ?: e.toString()
            }
        }
        throw IllegalStateException(errors.lastOrNull() ?: "No audio capture device available")
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int = delegate?.read(buffer, offset, length) ?: -1

    override fun close() {
        delegate?.close()
        delegate = null
    }
}
