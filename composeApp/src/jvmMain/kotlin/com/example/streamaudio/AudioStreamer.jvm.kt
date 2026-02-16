package com.example.streamaudio

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.SocketAddress
import java.util.Collections
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.Mixer
import javax.sound.sampled.TargetDataLine
import kotlin.concurrent.thread

actual class AudioStreamer actual constructor() {
    @Volatile private var isRunning = false
    private val connectedClients = Collections.synchronizedSet(HashSet<SocketAddress>())

    private var socket: DatagramSocket? = null
    private var targetLine: TargetDataLine? = null

    actual fun isStreaming(): Boolean = isRunning

    actual fun startStreaming(port: Int) {
        if (isRunning) return
        isRunning = true
        connectedClients.clear()

        try {
            socket = DatagramSocket(port)
            println("📡 UDP Host Ready. IP: ${getServerIp()} Port: $port")
        } catch (e: Exception) {
            println("❌ Failed to bind port $port: ${e.message}")
            isRunning = false
            return
        }

        thread(start = true, name = "UDP_Handshake_Listener") {
            try {
                val buffer = ByteArray(256)
                val packet = DatagramPacket(buffer, buffer.size)

                while (isRunning) {
                    socket?.receive(packet)
                    val clientAddress = packet.socketAddress

                    if (connectedClients.add(clientAddress)) {
                        println("New Android Client Connected: $clientAddress")
                    }
                }
            } catch (e: Exception) {
                if (isRunning) println("Socket Error: ${e.message}")
            }
        }

        thread(start = true, name = "Audio_Capture_Thread") {
            try {
                val format = AudioFormat(48000f, 16, 2, true, false)
                var selectedMixer: Mixer? = null
                val mixerInfos = AudioSystem.getMixerInfo()

                for (info in mixerInfos) {
                    val name = info.name.lowercase()
                    if (name.contains("stereo mix") || name.contains("what u hear") || name.contains("wave out")) {
                        val mixer = AudioSystem.getMixer(info)
                        if (mixer.isLineSupported(DataLine.Info(TargetDataLine::class.java, format))) {
                            selectedMixer = mixer
                            println("Found System Audio Device: ${info.name}")
                            break
                        }
                    }
                }

                if (selectedMixer == null) {
                    if (!AudioSystem.isLineSupported(DataLine.Info(TargetDataLine::class.java, format))) {
                        println("Audio format not supported.")
                        return@thread
                    }
                    targetLine = AudioSystem.getLine(DataLine.Info(TargetDataLine::class.java, format)) as TargetDataLine
                } else {
                    targetLine = selectedMixer.getLine(DataLine.Info(TargetDataLine::class.java, format)) as TargetDataLine
                }

                targetLine?.open(format, 8192)
                targetLine?.start()
                println("🎤 Audio Capture Running...")

                val buffer = ByteArray(1024)

                while (isRunning) {
                    val bytesRead = targetLine?.read(buffer, 0, buffer.size) ?: -1

                    if (bytesRead > 0) {
                        synchronized(connectedClients) {
                            for (client in connectedClients) {
                                try {
                                    val packet = DatagramPacket(buffer, bytesRead, client)
                                    socket?.send(packet)
                                } catch (e: Exception) {
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                println("Capture Error: ${e.message}")
                e.printStackTrace()
            } finally {
                cleanup()
            }
        }
    }

    actual fun stopStreaming() {
        isRunning = false
        socket?.close()
        cleanup()
    }

    private fun cleanup() {
        try { targetLine?.stop(); targetLine?.close() } catch (e: Exception) {}
        targetLine = null
        try { socket?.close() } catch (e: Exception) {}
        socket = null
        connectedClients.clear()
    }

    actual fun getServerIp(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (networkInterface.isLoopback || !networkInterface.isUp) continue
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (address is Inet4Address && address.isSiteLocalAddress) {
                        return address.hostAddress
                    }
                }
            }
        } catch (e: Exception) { }
        return "Unknown IP"
    }
}