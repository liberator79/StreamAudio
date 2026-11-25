package com.example.streamaudio

/**
 * Fixed wire format shared by every host and listener.
 *
 * Audio is raw PCM, 48 kHz, 16-bit little-endian, stereo, sent in 5 ms packets (960 bytes)
 * so every datagram fits in a single Wi-Fi frame without IP fragmentation.
 */
object StreamConfig {
    const val DEFAULT_PORT = 50005
    const val SAMPLE_RATE = 48_000
    const val CHANNELS = 2
    const val BYTES_PER_FRAME = 4
    const val FRAMES_PER_PACKET = 240
    const val PACKET_BYTES = FRAMES_PER_PACKET * BYTES_PER_FRAME
    const val PACKET_MS = FRAMES_PER_PACKET * 1000 / SAMPLE_RATE

    /** Listener re-sends HELLO this often; the host forgets listeners it hasn't heard from in [CLIENT_TIMEOUT_MS]. */
    const val HEARTBEAT_INTERVAL_MS = 1_000L
    const val CLIENT_TIMEOUT_MS = 4_000L
}

/** How much audio the listener keeps queued: a trade-off between delay and resistance to Wi-Fi hiccups. */
enum class LatencyMode(val label: String, val targetMs: Int, val description: String) {
    LOW("Low", 30, "Lowest delay. Best on a strong, quiet Wi-Fi network."),
    BALANCED("Balanced", 60, "Good for most networks."),
    STABLE("Stable", 120, "Smooth playback on busy or weak Wi-Fi."),
}

/**
 * Datagram layout: `'S' 'A' version type [payload]`.
 * Audio payload is prefixed with a 32-bit big-endian sequence number.
 */
internal object StreamProtocol {
    private const val MAGIC_0 = 'S'.code.toByte()
    private const val MAGIC_1 = 'A'.code.toByte()
    private const val VERSION: Byte = 1

    const val TYPE_AUDIO: Byte = 1
    const val TYPE_HELLO: Byte = 2
    const val TYPE_BYE: Byte = 3
    const val TYPE_DISCOVER: Byte = 4
    const val TYPE_HOST_INFO: Byte = 5

    const val HEADER_SIZE = 4
    const val AUDIO_HEADER_SIZE = HEADER_SIZE + 4
    const val MAX_DATAGRAM = AUDIO_HEADER_SIZE + StreamConfig.PACKET_BYTES

    fun writeHeader(buffer: ByteArray, type: Byte) {
        buffer[0] = MAGIC_0
        buffer[1] = MAGIC_1
        buffer[2] = VERSION
        buffer[3] = type
    }

    /** Returns the packet type, or null if the datagram isn't ours. */
    fun typeOf(buffer: ByteArray, length: Int): Byte? {
        if (length < HEADER_SIZE) return null
        if (buffer[0] != MAGIC_0 || buffer[1] != MAGIC_1 || buffer[2] != VERSION) return null
        return buffer[3]
    }

    fun control(type: Byte, payload: ByteArray = ByteArray(0)): ByteArray {
        val out = ByteArray(HEADER_SIZE + payload.size)
        writeHeader(out, type)
        payload.copyInto(out, HEADER_SIZE)
        return out
    }

    fun writeInt(buffer: ByteArray, offset: Int, value: Int) {
        buffer[offset] = (value ushr 24).toByte()
        buffer[offset + 1] = (value ushr 16).toByte()
        buffer[offset + 2] = (value ushr 8).toByte()
        buffer[offset + 3] = value.toByte()
    }

    fun readInt(buffer: ByteArray, offset: Int): Int =
        ((buffer[offset].toInt() and 0xFF) shl 24) or
            ((buffer[offset + 1].toInt() and 0xFF) shl 16) or
            ((buffer[offset + 2].toInt() and 0xFF) shl 8) or
            (buffer[offset + 3].toInt() and 0xFF)

    /** Peak absolute amplitude (0..1) of 16-bit little-endian PCM. */
    fun peakLevel(pcm: ByteArray, offset: Int, length: Int): Float {
        var peak = 0
        var i = offset
        val end = offset + length - 1
        while (i < end) {
            val sample = (pcm[i].toInt() and 0xFF) or (pcm[i + 1].toInt() shl 8)
            val abs = if (sample < 0) -sample else sample
            if (abs > peak) peak = abs
            i += 2
        }
        return peak / 32768f
    }
}
