package com.example.streamaudio

/** Low-latency PCM output (48 kHz / 16-bit / stereo). Used from a single playback thread. */
expect class AudioPlayer() {
    /** Opens the output device; throws on failure. */
    fun start()

    /** Blocking write: returns once the data is queued in the device buffer. */
    fun write(data: ByteArray, offset: Int, length: Int)

    fun stop()

    /** Approximate delay added by the device buffer. */
    val outputLatencyMs: Int
}
