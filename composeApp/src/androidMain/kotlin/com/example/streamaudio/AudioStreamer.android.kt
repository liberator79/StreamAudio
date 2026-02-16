package com.example.streamaudio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.SocketAddress
import java.util.Collections

actual class AudioStreamer actual constructor() {

    private var isRunning = false
    private val scope = CoroutineScope(Dispatchers.IO)
    private var streamJob: Job? = null
    private val connectedClients = Collections.synchronizedSet(HashSet<SocketAddress>())

    private var socket: DatagramSocket? = null
    private var audioRecord: AudioRecord? = null
    private var mediaProjection: MediaProjection? = null

    actual fun isStreaming(): Boolean = isRunning

    @RequiresApi(Build.VERSION_CODES.Q)
    actual fun startStreaming(port: Int) {
        if (isRunning) return

        val context = AndroidPermissionHelper.context
        val intent = AndroidPermissionHelper.permissionIntent
        val resultCode = AndroidPermissionHelper.permissionResultCode

        if (context == null || intent == null || resultCode == 0) {
            println("❌ Missing Permissions!")
            return
        }

        isRunning = true
        connectedClients.clear()

        try {
            val projectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = projectionManager.getMediaProjection(resultCode, intent)

            // 🔴 FIX: Enable SO_REUSEADDR to prevent "Address already in use" crashes
            socket = DatagramSocket(null).apply {
                reuseAddress = true
                bind(InetSocketAddress(port))
            }
            println("📡 Android Audio Host Started on Port $port")

            startHandshakeListener()

            streamJob = scope.launch {
                captureAndStream()
            }

        } catch (e: Exception) {
            println("❌ Error starting stream: ${e.message}")
            stopStreaming()
        }
    }

    private fun startHandshakeListener() {
        scope.launch {
            val buffer = ByteArray(256)
            val packet = DatagramPacket(buffer, buffer.size)

            println("👂 Waiting for listeners...")
            while (isRunning && isActive) {
                try {
                    socket?.receive(packet)
                    val clientAddress = packet.socketAddress
                    if (connectedClients.add(clientAddress)) {
                        println("Client Connected: $clientAddress")
                    }
                } catch (e: Exception) {
                    // Socket closed
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun captureAndStream() {
        try {
            val sampleRate = 48000
            val channelConfig = AudioFormat.CHANNEL_IN_STEREO
            val encoding = AudioFormat.ENCODING_PCM_16BIT

            val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, encoding)
            val bufferSize = maxOf(minBufferSize, 8192)

            val config = AudioPlaybackCaptureConfiguration.Builder(mediaProjection!!)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                .build()

            val audioFormat = AudioFormat.Builder()
                .setEncoding(encoding)
                .setSampleRate(sampleRate)
                .setChannelMask(channelConfig)
                .build()

            audioRecord = AudioRecord.Builder()
                .setAudioFormat(audioFormat)
                .setBufferSizeInBytes(bufferSize)
                .setAudioPlaybackCaptureConfig(config)
                .build()

            audioRecord?.startRecording()
            println("🎤 Internal Audio Capture Started!")

            val buffer = ByteArray(1024)

            while (isRunning && streamJob?.isActive == true) {
                val bytesRead = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                if (bytesRead > 0) {
                    synchronized(connectedClients) {
                        val iterator = connectedClients.iterator()
                        while (iterator.hasNext()) {
                            val client = iterator.next()
                            try {
                                val packet = DatagramPacket(buffer, bytesRead, client)
                                socket?.send(packet)
                            } catch (e: Exception) {
                                iterator.remove()
                            }
                        }
                    }
                }
            }

        } catch (e: Exception) {
            println("❌ Capture Error: ${e.message}")
            e.printStackTrace()
        } finally {
            cleanup()
        }
    }

    actual fun stopStreaming() {
        isRunning = false
        cleanup()
    }

    private fun cleanup() {
        try { streamJob?.cancel() } catch (e: Exception) {}
        try { audioRecord?.stop(); audioRecord?.release() } catch (e: Exception) {}
        try { mediaProjection?.stop() } catch (e: Exception) {}

        // 🔴 FIX: Force close socket
        try {
            if (socket != null && !socket!!.isClosed) {
                socket!!.close()
            }
        } catch (e: Exception) {}

        audioRecord = null
        socket = null
        mediaProjection = null
        println("🛑 Stream Stopped")
    }

    actual fun getServerIp(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            for (networkInterface in interfaces) {
                if (!networkInterface.isUp || networkInterface.isLoopback) continue
                val addresses = networkInterface.inetAddresses
                for (addr in addresses) {
                    if (addr is Inet4Address && addr.isSiteLocalAddress) {
                        return addr.hostAddress
                    }
                }
            }
        } catch (e: Exception) { }
        return "Unknown IP"
    }
}