package com.example.streamaudio

import com.sun.jna.Function
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.Guid
import com.sun.jna.platform.win32.Ole32
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference

/**
 * Captures everything the default Windows output device is playing, using WASAPI shared-mode
 * loopback. Unlike "Stereo Mix" this needs no driver support and nothing to enable in Sound
 * settings, and it goes through far less buffering than JavaSound/DirectSound.
 *
 * The Windows audio engine converts to our 48 kHz / 16-bit / stereo format for us
 * (AUTOCONVERTPCM), whatever the device's own mix format is.
 */
internal class WasapiLoopbackSource : AudioSource {
    override val name = "System audio"

    private var comInitialized = false
    private var enumerator: Pointer? = null
    private var device: Pointer? = null
    private var audioClient: Pointer? = null
    private var captureClient: Pointer? = null

    // Captured audio not yet handed to the caller. Sized for ~200 ms; if the reader falls
    // behind further than that we drop the oldest audio rather than build up delay.
    private val pending = ByteArray(StreamConfig.SAMPLE_RATE / 5 * StreamConfig.BYTES_PER_FRAME)
    private var pendingLength = 0

    private val nextPacketFrames = IntByReference()
    private val dataPointer = PointerByReference()
    private val framesAvailable = IntByReference()
    private val bufferFlags = IntByReference()

    override fun open() {
        check(com.sun.jna.Platform.isWindows()) { "WASAPI is only available on Windows" }

        val init = Ole32.INSTANCE.CoInitializeEx(null, Ole32.COINIT_MULTITHREADED).toInt()
        comInitialized = init == S_OK || init == S_FALSE

        val ref = PointerByReference()
        hr(Ole32.INSTANCE.CoCreateInstance(CLSID_MMDeviceEnumerator, null, CLSCTX_ALL, IID_IMMDeviceEnumerator, ref).toInt(), "create device enumerator")
        enumerator = ref.value

        hr(enumerator!!.invoke(4, E_RENDER, E_CONSOLE, ref), "find the default speaker") // GetDefaultAudioEndpoint
        device = ref.value

        hr(device!!.invoke(3, IID_IAudioClient, CLSCTX_ALL, null, ref), "activate audio client") // Activate
        audioClient = ref.value

        val format = Memory(18).apply {
            setShort(0, WAVE_FORMAT_PCM)
            setShort(2, StreamConfig.CHANNELS.toShort())
            setInt(4, StreamConfig.SAMPLE_RATE)
            setInt(8, StreamConfig.SAMPLE_RATE * StreamConfig.BYTES_PER_FRAME)
            setShort(12, StreamConfig.BYTES_PER_FRAME.toShort())
            setShort(14, 16)
            setShort(16, 0)
        }
        val flags = AUDCLNT_STREAMFLAGS_LOOPBACK or AUDCLNT_STREAMFLAGS_AUTOCONVERTPCM or AUDCLNT_STREAMFLAGS_SRC_DEFAULT_QUALITY
        // Initialize(shared, flags, 100 ms buffer, periodicity 0, format, session null).
        // A capture buffer's size is just headroom; it adds no delay because we drain it continuously.
        hr(audioClient!!.invoke(3, AUDCLNT_SHAREMODE_SHARED, flags, 1_000_000L, 0L, format, null), "initialize loopback capture")

        hr(audioClient!!.invoke(14, IID_IAudioCaptureClient, ref), "get capture client") // GetService
        captureClient = ref.value

        hr(audioClient!!.invoke(10), "start capture") // Start
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val deadline = System.nanoTime() + 100_000_000L
        while (pendingLength < length) {
            if (!drainDevice()) {
                // Loopback produces no packets while nothing is playing.
                if (System.nanoTime() > deadline) return 0
                Thread.sleep(2)
            }
        }
        System.arraycopy(pending, 0, buffer, offset, length)
        pendingLength -= length
        if (pendingLength > 0) System.arraycopy(pending, length, pending, 0, pendingLength)
        return length
    }

    /** Moves every packet WASAPI has ready into [pending]. Returns true if anything arrived. */
    private fun drainDevice(): Boolean {
        val capture = captureClient ?: return false
        var gotData = false
        while (true) {
            hr(capture.invoke(5, nextPacketFrames), "read capture size") // GetNextPacketSize
            if (nextPacketFrames.value == 0) return gotData

            hr(capture.invoke(3, dataPointer, framesAvailable, bufferFlags, null, null), "read capture buffer") // GetBuffer
            val frames = framesAvailable.value
            val bytes = frames * StreamConfig.BYTES_PER_FRAME

            if (bytes > 0) {
                val overflow = pendingLength + bytes - pending.size
                if (overflow > 0) {
                    // Reader fell behind: drop the oldest audio, keep the newest.
                    // Sizes are all whole frames, so this stays frame-aligned.
                    val drop = minOf(pendingLength, overflow)
                    System.arraycopy(pending, drop, pending, 0, pendingLength - drop)
                    pendingLength -= drop
                }
                val writable = minOf(bytes, pending.size - pendingLength)
                if (bufferFlags.value and AUDCLNT_BUFFERFLAGS_SILENT != 0) {
                    pending.fill(0, pendingLength, pendingLength + writable)
                } else {
                    dataPointer.value.read(0, pending, pendingLength, writable)
                }
                pendingLength += writable
            }
            hr(capture.invoke(4, frames), "release capture buffer") // ReleaseBuffer
            gotData = true
        }
    }

    override fun close() {
        audioClient?.let { runCatching { it.invoke(11) } } // Stop
        listOf(captureClient, audioClient, device, enumerator).forEach { ptr ->
            ptr?.let { runCatching { it.invoke(2) } } // Release
        }
        captureClient = null
        audioClient = null
        device = null
        enumerator = null
        if (comInitialized) {
            Ole32.INSTANCE.CoUninitialize()
            comInitialized = false
        }
        pendingLength = 0
    }

    /** Calls method [index] of this COM object's vtable. */
    private fun Pointer.invoke(index: Int, vararg args: Any?): Int {
        val vtable = getPointer(0)
        val fn = Function.getFunction(vtable.getPointer(index.toLong() * Native.POINTER_SIZE), Function.ALT_CONVENTION)
        return fn.invokeInt(arrayOf(this, *args))
    }

    private fun hr(result: Int, action: String) {
        if (result < 0) {
            val hex = "0x%08X".format(result)
            val hint = if (result == AUDCLNT_E_DEVICE_INVALIDATED) " (audio device changed)" else ""
            throw IllegalStateException("Couldn't $action: WASAPI error $hex$hint")
        }
    }

    private companion object {
        const val S_OK = 0
        const val S_FALSE = 1
        const val CLSCTX_ALL = 0x17
        const val E_RENDER = 0
        const val E_CONSOLE = 0
        const val AUDCLNT_SHAREMODE_SHARED = 0
        const val AUDCLNT_STREAMFLAGS_LOOPBACK = 0x00020000
        const val AUDCLNT_STREAMFLAGS_SRC_DEFAULT_QUALITY = 0x08000000
        const val AUDCLNT_STREAMFLAGS_AUTOCONVERTPCM = 0x80000000.toInt()
        const val AUDCLNT_BUFFERFLAGS_SILENT = 0x2
        const val AUDCLNT_E_DEVICE_INVALIDATED = 0x88890004.toInt()
        const val WAVE_FORMAT_PCM: Short = 1

        val CLSID_MMDeviceEnumerator: Guid.GUID = Guid.GUID.fromString("{BCDE0395-E52F-467C-8E3D-C4579291692E}")
        val IID_IMMDeviceEnumerator: Guid.GUID = Guid.GUID.fromString("{A95664D2-9614-4F35-A746-DE8DB63617E6}")
        val IID_IAudioClient: Guid.GUID = Guid.GUID.fromString("{1CB9AD4C-DBFA-4C32-B178-C2F568A703B2}")
        val IID_IAudioCaptureClient: Guid.GUID = Guid.GUID.fromString("{C8ADBD64-E71E-48A0-A4DE-185C395CD317}")
    }
}
