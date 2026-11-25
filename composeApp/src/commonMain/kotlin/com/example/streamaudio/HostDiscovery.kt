package com.example.streamaudio

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketTimeoutException

data class DiscoveredHost(val name: String, val address: String, val isPhone: Boolean)

data class LocalAddress(val address: String, val interfaceName: String)

object HostDiscovery {

    /** Broadcasts a DISCOVER packet and collects HOST_INFO replies. Blocking. */
    fun discover(port: Int = StreamConfig.DEFAULT_PORT, timeoutMs: Long = 1500): List<DiscoveredHost> {
        val found = LinkedHashMap<String, DiscoveredHost>()
        DatagramSocket().use { socket ->
            socket.broadcast = true
            socket.soTimeout = 150
            val probe = StreamProtocol.control(StreamProtocol.TYPE_DISCOVER)
            val targets = broadcastAddresses() + InetAddress.getByName("255.255.255.255")

            val buffer = ByteArray(512)
            val packet = DatagramPacket(buffer, buffer.size)
            val deadline = System.currentTimeMillis() + timeoutMs
            var nextProbe = 0L
            while (System.currentTimeMillis() < deadline) {
                val now = System.currentTimeMillis()
                if (now >= nextProbe) {
                    // Re-probe a few times: broadcast is unreliable on Wi-Fi.
                    for (target in targets) {
                        runCatching { socket.send(DatagramPacket(probe, probe.size, target, port)) }
                    }
                    nextProbe = now + 400
                }
                try {
                    packet.setLength(buffer.size)
                    socket.receive(packet)
                } catch (_: SocketTimeoutException) {
                    continue
                }
                if (StreamProtocol.typeOf(buffer, packet.length) != StreamProtocol.TYPE_HOST_INFO) continue
                val info = buffer.decodeToString(StreamProtocol.HEADER_SIZE, packet.length)
                val platform = info.substringBefore('|', "")
                val name = info.substringAfter('|').ifBlank { "Unknown device" }
                val address = packet.address.hostAddress ?: continue
                found[address] = DiscoveredHost(name, address, isPhone = platform == "android")
            }
        }
        return found.values.toList()
    }

    private fun broadcastAddresses(): List<InetAddress> = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.interfaceAddresses }
            .mapNotNull { it.broadcast }
            .distinct()
    }.getOrDefault(emptyList())

    /** IPv4 addresses on this machine that a listener could reach, best (Wi-Fi / Ethernet) first. */
    fun localAddresses(): List<LocalAddress> = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback && !it.isVirtual && !looksVirtual(it) }
            .flatMap { nif ->
                nif.inetAddresses.toList()
                    .filterIsInstance<Inet4Address>()
                    .filter { it.isSiteLocalAddress }
                    .mapNotNull { addr -> addr.hostAddress?.let { LocalAddress(it, nif.displayName ?: nif.name) } }
            }
            .sortedBy { rank(it.interfaceName) }
    }.getOrDefault(emptyList())

    private fun looksVirtual(nif: NetworkInterface): Boolean {
        val name = "${nif.name} ${nif.displayName}".lowercase()
        return listOf("virtual", "vethernet", "vmware", "vbox", "hyper-v", "wsl", "docker", "tap", "tun", "zerotier", "tailscale")
            .any { it in name }
    }

    private fun rank(name: String): Int {
        val n = name.lowercase()
        return when {
            "wlan" in n || "wi-fi" in n || "wifi" in n || "wireless" in n -> 0
            "eth" in n || "ethernet" in n -> 1
            else -> 2
        }
    }
}
