package com.example.streamaudio

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class AudioClient {

    private var socket: DatagramSocket? = null
    // You can keep AudioPlayer here if you want to manage it internally,
    // or rely on the onData callback to handle playing.
    private val player = AudioPlayer()

    suspend fun startListening(ip: String, port: Int, onData: (ByteArray, Int) -> Unit) {
        withContext(Dispatchers.IO) {
            try {
                println("🔌 Opening UDP Socket...")
                // UDP doesn't "connect" like TCP. We just open a port.
                socket = DatagramSocket()

                // We need the Server's Address to send the Handshake
                val serverAddress = InetAddress.getByName(ip)

                // --- 1. THE HANDSHAKE ---
                // We MUST send a packet to the server first so it knows our IP/Port.
                // Otherwise, the server creates the audio but has nobody to send it to.
                val helloMessage = "HELLO_ANDROID".toByteArray()
                val helloPacket = DatagramPacket(helloMessage, helloMessage.size, serverAddress, port)

                println("👋 Sending Handshake to $ip:$port...")
                // Send multiple times to ensure it punches through UDP packet loss
                repeat(5) {
                    socket?.send(helloPacket)
                    // Tiny delay to ensure packets don't clump
                    Thread.sleep(10)
                }
                println("✅ Handshake Sent! Waiting for Audio...")

                // Start the player (if you are managing it here)
                player.startAudio()

                // --- 2. THE RECEIVE LOOP ---
                val buffer = ByteArray(4096) // Big enough to hold the server's chunks (1024)
                val packet = DatagramPacket(buffer, buffer.size)

                // Loop while Coroutine is active and socket is open
                while (isActive && socket != null && !socket!!.isClosed) {
                    try {
                        // This BLOCKS until a packet arrives
                        socket?.receive(packet)

                        // packet.length is the actual amount of data received
                        if (packet.length > 0) {
                            onData(packet.data, packet.length)
                        }
                    } catch (e: Exception) {
                        if (!isActive) break // Coroutine cancelled, exit loop
                        println("⚠️ Receive error: ${e.message}")
                    }
                }

            } catch (e: Exception) {
                println("❌ Client Error: ${e.message}")
                e.printStackTrace()
            } finally {
                stopListening()
            }
        }
    }

    fun stopListening() {
        try { player.stop() } catch (e: Exception) {}
        try { socket?.close() } catch (e: Exception) {}
        socket = null
        println("Client Stopped")
    }
}