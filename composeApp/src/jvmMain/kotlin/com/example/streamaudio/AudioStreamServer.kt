package com.example.streamaudio

import java.net.ServerSocket
import java.net.Socket
import javax.sound.sampled.*
import kotlin.concurrent.thread

class AudioStreamServer {
    private var serverSocket: ServerSocket? = null
    private var clientSocket: Socket? = null
    private var isRunning = false

    // We hardcode the format so both sides agree (48000Hz is standard for Windows)
    private val format = AudioFormat(48000f, 16, 2, true, false)
    private val port = 50005 // The "channel" we broadcast on

    fun start() {
        thread(start = true) {
            try {
                println("🚀 Starting Audio Server on port $port...")
                serverSocket = ServerSocket(port)

                // 1. Wait for the Android phone to connect
                println("⏳ Waiting for client to connect...")
                clientSocket = serverSocket?.accept()
                println("✅ Client Connected: ${clientSocket?.inetAddress}")

                // 2. Start capturing audio (Using your previous logic)
                startStreamingToClient()

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun startStreamingToClient() {
        val outputStream = clientSocket?.getOutputStream() ?: return

        // Find Stereo Mix (DirectSound)
        val mixerInfo = AudioSystem.getMixerInfo().firstOrNull {
            it.name.contains("Stereo Mix", ignoreCase = true) &&
                    !it.description.contains("Port", ignoreCase = true)
        } ?: return

        val mixer = AudioSystem.getMixer(mixerInfo)
        val info = DataLine.Info(TargetDataLine::class.java, format)

        if (!mixer.isLineSupported(info)) return

        val line = mixer.getLine(info) as TargetDataLine
        line.open(format)
        line.start()

        isRunning = true
        val buffer = ByteArray(4096) // Small buffer = Lower Latency

        println("🎙️ Streaming Audio to Network...")

        try {
            while (isRunning && clientSocket?.isConnected == true) {
                // Read from Microphone/Stereo Mix
                val bytesRead = line.read(buffer, 0, buffer.size)

                // Write to Network
                if (bytesRead > 0) {
                    outputStream.write(buffer, 0, bytesRead)
                    outputStream.flush() // Force send immediately
                }
            }
        } catch (e: Exception) {
            println("❌ Connection Lost")
        } finally {
            line.close()
            clientSocket?.close()
        }
    }

    fun stop() {
        isRunning = false
        serverSocket?.close()
    }
}