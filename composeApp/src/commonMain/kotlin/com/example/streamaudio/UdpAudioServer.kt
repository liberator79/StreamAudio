package com.example.streamaudio

import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap

/**
 * Host-side UDP endpoint. Tracks listeners by heartbeat, answers discovery broadcasts and
 * fans out audio packets. [sendAudio] must only be called from a single (capture) thread.
 */
internal class UdpAudioServer(
    private val port: Int,
    private val onListenersChanged: (Int) -> Unit,
) {
    private val clients = ConcurrentHashMap<SocketAddress, Long>()
    private val socket: DatagramSocket = DatagramSocket(null).also {
        it.reuseAddress = true
        it.bind(InetSocketAddress(port)) // (not inside apply{}: there `port` would mean DatagramSocket.getPort())
    }.apply {
        broadcast = true
        sendBufferSize = 64 * 1024
        // DSCP EF ("voice"): Wi-Fi access points with WMM put these in the low-latency queue.
        runCatching { trafficClass = 0xB8 }
    }

    private val sendBuffer = ByteArray(StreamProtocol.MAX_DATAGRAM)
    private val sendPacket = DatagramPacket(sendBuffer, sendBuffer.size)
    private var sequence = 0

    private val hostInfo = StreamProtocol.control(
        StreamProtocol.TYPE_HOST_INFO,
        "$platformTag|${deviceName()}".encodeToByteArray(),
    )

    init {
        Thread({ controlLoop() }, "StreamAudio-HostControl").apply {
            isDaemon = true
            start()
        }
    }

    private fun controlLoop() {
        val buffer = ByteArray(512)
        val packet = DatagramPacket(buffer, buffer.size)
        socket.soTimeout = 1000
        while (!socket.isClosed) {
            try {
                packet.setLength(buffer.size)
                socket.receive(packet)
                handleControl(packet)
            } catch (_: SocketTimeoutException) {
            } catch (_: IOException) {
                if (socket.isClosed) break
            }
            pruneStaleClients()
        }
    }

    private fun handleControl(packet: DatagramPacket) {
        val address = packet.socketAddress
        when (StreamProtocol.typeOf(packet.data, packet.length)) {
            StreamProtocol.TYPE_HELLO -> {
                if (clients.put(address, now()) == null) publishCount()
            }
            StreamProtocol.TYPE_BYE -> {
                if (clients.remove(address) != null) publishCount()
            }
            StreamProtocol.TYPE_DISCOVER -> {
                runCatching { socket.send(DatagramPacket(hostInfo, hostInfo.size, address)) }
            }
        }
    }

    private fun pruneStaleClients() {
        val cutoff = now() - StreamConfig.CLIENT_TIMEOUT_MS
        if (clients.entries.removeIf { it.value < cutoff }) publishCount()
    }

    private fun publishCount() = onListenersChanged(clients.size)

    fun sendAudio(pcm: ByteArray, offset: Int, length: Int) {
        val seq = sequence++
        if (clients.isEmpty() || socket.isClosed) return
        StreamProtocol.writeHeader(sendBuffer, StreamProtocol.TYPE_AUDIO)
        StreamProtocol.writeInt(sendBuffer, StreamProtocol.HEADER_SIZE, seq)
        System.arraycopy(pcm, offset, sendBuffer, StreamProtocol.AUDIO_HEADER_SIZE, length)
        sendPacket.setData(sendBuffer, 0, StreamProtocol.AUDIO_HEADER_SIZE + length)
        for (client in clients.keys) {
            sendPacket.socketAddress = client
            try {
                socket.send(sendPacket)
            } catch (_: IOException) {
                // Transient (e.g. Wi-Fi roaming); the heartbeat timeout removes dead listeners.
            }
        }
    }

    fun stop() {
        if (socket.isClosed) return
        val goodbyeTo = clients.keys.toList()
        clients.clear()
        onListenersChanged(0)
        // Sending isn't allowed on Android's main thread, so say goodbye from a worker.
        val closer = Thread({
            val bye = StreamProtocol.control(StreamProtocol.TYPE_BYE)
            for (client in goodbyeTo) runCatching { socket.send(DatagramPacket(bye, bye.size, client)) }
            socket.close()
        }, "StreamAudio-HostClose")
        closer.start()
        // Wait briefly so the port is free if the user immediately hosts again.
        runCatching { closer.join(250) }
    }

    private fun now() = System.currentTimeMillis()
}
