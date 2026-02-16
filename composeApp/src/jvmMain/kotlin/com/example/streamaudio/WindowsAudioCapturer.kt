package com.example.streamaudio

import java.net.ServerSocket
import java.net.Socket
import javax.sound.sampled.*
import kotlin.concurrent.thread

class WindowsAudioCapturer : AudioCapturer {

    private var serverSocket: ServerSocket? = null
    private var clientSocket: Socket? = null
    private var isRunning = false
    private var targetLine: TargetDataLine? = null
    private val format = AudioFormat(48000f, 16, 2, true, false)
    private val port = 50005

    override fun startCapturing() {
        if (isRunning) return

        isRunning = true

        thread(start = true) {
            try {
                println("🚀 Starting Server on port $port...")
                serverSocket = ServerSocket(port)

                println("⏳ Waiting for Android to connect...")
                clientSocket = serverSocket?.accept()

                println("✅ Android Connected! Starting Stream...")
                streamAudioToClient()

            } catch (e: Exception) {
                println("❌ Server Error: ${e.message}")
                e.printStackTrace()
                cleanup()
            }
        }
    }

    private fun streamAudioToClient() {
        val outputStream = clientSocket?.getOutputStream() ?: return
        val mixerInfo = AudioSystem.getMixerInfo().firstOrNull {
            it.name.contains("Stereo Mix", ignoreCase = true) &&
                    !it.description.contains("Port", ignoreCase = true)
        }

        if (mixerInfo == null) {
            println("❌ Error: Stereo Mix not found!")
            return
        }

        val mixer = AudioSystem.getMixer(mixerInfo)
        val info = DataLine.Info(TargetDataLine::class.java, format)

        if (!mixer.isLineSupported(info)) {
            println("❌ Error: Your Stereo Mix is not set to 48000Hz.")
            println("   Please change Windows Sound Settings -> Stereo Mix -> Properties -> Advanced to 48000Hz.")
            return
        }

        targetLine = mixer.getLine(info) as TargetDataLine
        targetLine?.open(format)
        targetLine?.start()

        println("🎙️ Streaming Live Audio...")

        val buffer = ByteArray(4096)

        try {
            while (isRunning && clientSocket?.isConnected == true && targetLine?.isOpen == true) {
                val bytesRead = targetLine?.read(buffer, 0, buffer.size) ?: 0

                if (bytesRead > 0) {
                    outputStream.write(buffer, 0, bytesRead)
                }
            }
        } catch (e: Exception) {
            println("❌ Connection Broken")
        } finally {
            cleanup()
        }
    }

    override fun stopCapturing() {
        isRunning = false

        thread(start = true) {
            println("🛑 Stopping Capture (Background)...")
            cleanup()
        }
    }
    private fun cleanup() {
        isRunning = false
        try {
            if (targetLine?.isOpen == true) {
                targetLine?.stop()
                targetLine?.close()
            }
        } catch (e: Exception) {
            println("⚠️ Error closing audio: ${e.message}")
        }

        try {
            if (clientSocket?.isClosed == false) {
                clientSocket?.close()
            }
        } catch (e: Exception) {
            println("⚠️ Error closing client: ${e.message}")
        }

        try {
            if (serverSocket?.isClosed == false) {
                serverSocket?.close()
            }
        } catch (e: Exception) {
            println("⚠️ Error closing server: ${e.message}")
        }

        println("✅ Cleanup Complete")
    }
}