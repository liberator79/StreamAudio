package com.example.streamaudio

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class StreamingPipelineTest {

    private fun freePort(): Int {
        val s = DatagramSocket(null)
        s.bind(InetSocketAddress("0.0.0.0", 0))
        val port = s.localPort
        s.close()
        check(port > 0) { "no free port: $port" }
        return port
    }

    private fun waitUntil(timeoutMs: Long, condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return true
            Thread.sleep(10)
        }
        return condition()
    }

    @Test
    fun serverRegistersListenerAndSendsSequencedAudio() {
        val port = freePort()
        val listeners = AtomicInteger(-1)
        val server = UdpAudioServer(port) { listeners.set(it) }
        DatagramSocket().use { client ->
            client.soTimeout = 1000
            val hello = StreamProtocol.control(StreamProtocol.TYPE_HELLO)
            client.send(DatagramPacket(hello, hello.size, InetSocketAddress("127.0.0.1", port)))
            assertTrue(waitUntil(1000) { listeners.get() == 1 }, "listener should register")

            val pcm = ByteArray(StreamConfig.PACKET_BYTES) { it.toByte() }
            server.sendAudio(pcm, 0, pcm.size)
            server.sendAudio(pcm, 0, pcm.size)

            val buf = ByteArray(2048)
            val seqs = (0 until 2).map {
                val p = DatagramPacket(buf, buf.size)
                client.receive(p)
                assertEquals(StreamProtocol.TYPE_AUDIO, StreamProtocol.typeOf(buf, p.length))
                assertEquals(StreamProtocol.AUDIO_HEADER_SIZE + StreamConfig.PACKET_BYTES, p.length)
                assertEquals(pcm[100], buf[StreamProtocol.AUDIO_HEADER_SIZE + 100])
                StreamProtocol.readInt(buf, StreamProtocol.HEADER_SIZE)
            }
            assertEquals(listOf(0, 1), seqs)

            val bye = StreamProtocol.control(StreamProtocol.TYPE_BYE)
            client.send(DatagramPacket(bye, bye.size, InetSocketAddress("127.0.0.1", port)))
            assertTrue(waitUntil(1000) { listeners.get() == 0 }, "listener should unregister on BYE")
        }
        server.stop()
    }

    @Test
    fun serverAnswersDiscovery() {
        val port = freePort()
        val server = UdpAudioServer(port) {}
        DatagramSocket().use { client ->
            client.soTimeout = 1000
            val probe = StreamProtocol.control(StreamProtocol.TYPE_DISCOVER)
            client.send(DatagramPacket(probe, probe.size, InetSocketAddress("127.0.0.1", port)))
            val buf = ByteArray(512)
            val p = DatagramPacket(buf, buf.size)
            client.receive(p)
            assertEquals(StreamProtocol.TYPE_HOST_INFO, StreamProtocol.typeOf(buf, p.length))
            val info = buf.decodeToString(StreamProtocol.HEADER_SIZE, p.length)
            assertTrue(info.startsWith("desktop|"), "host info was '$info'")
        }
        server.stop()
    }

    /** Emits near-silent PCM in real time, but can stall and then dump a backlog (like a Wi-Fi burst). */
    private class FakeSource : AudioSource {
        override val name = "Fake"
        @Volatile var stallUntil = 0L
        private var nextAt = 0L
        private var phase = 0.0

        override fun open() { nextAt = System.nanoTime() }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            while (System.currentTimeMillis() < stallUntil) Thread.sleep(1)
            val wait = nextAt - System.nanoTime()
            if (wait > 0) Thread.sleep(wait / 1_000_000, (wait % 1_000_000).toInt())
            nextAt += StreamConfig.PACKET_MS * 1_000_000L
            var i = offset
            while (i < offset + length) {
                // ~ -60 dBFS so the test is effectively inaudible on real speakers.
                val s = (30 * kotlin.math.sin(phase)).toInt()
                phase += 2 * Math.PI * 440 / StreamConfig.SAMPLE_RATE
                buffer[i] = s.toByte(); buffer[i + 1] = (s shr 8).toByte()
                buffer[i + 2] = s.toByte(); buffer[i + 3] = (s shr 8).toByte()
                i += 4
            }
            return length
        }

        override fun close() {}
    }

    @Test
    fun endToEndLatencyStaysBoundedAfterABurst() {
        val port = freePort()
        val engine = HostEngine(port)
        val source = FakeSource()
        engine.start(source)
        assertTrue(waitUntil(2000) { engine.state.value.status == HostStatus.Live })

        val receiver = UdpAudioReceiver()
        receiver.latencyMode = LatencyMode.LOW
        receiver.start("127.0.0.1", port)
        try {
            assertTrue(waitUntil(5000) { receiver.state.value.status == ReceiverStatus.Playing }, "never started playing: ${receiver.state.value}")
            assertTrue(waitUntil(2000) { engine.state.value.listeners == 1 })

            // Stall the host for 400 ms; its packets then arrive in one burst.
            source.stallUntil = System.currentTimeMillis() + 400
            Thread.sleep(1500)

            val after = receiver.state.value
            println("Receiver after burst: $after")
            assertEquals(ReceiverStatus.Playing, after.status)
            // Without latency control the 400 ms backlog would stay queued forever.
            // Budget: 30 ms target + 20 ms slack + 40 ms device buffer + margin.
            val budget = 150
            assertTrue(after.latencyMs <= budget, "latency ${after.latencyMs} ms exceeded $budget ms")
        } finally {
            receiver.stop()
            engine.stop()
        }
        assertTrue(waitUntil(6000) { engine.state.value.listeners == 0 })
    }

    /** Wi-Fi power save (phone screen off) delivers packets in clumps; the buffer should adapt and settle. */
    @Test
    fun bufferAdaptsToClumpedDelivery() {
        val port = freePort()
        val engine = HostEngine(port)
        val source = FakeSource()
        engine.start(source)
        val receiver = UdpAudioReceiver()
        receiver.latencyMode = LatencyMode.LOW
        receiver.start("127.0.0.1", port)
        val clumping = java.util.concurrent.atomic.AtomicBoolean(true)
        val clumper = Thread {
            while (clumping.get()) {
                source.stallUntil = System.currentTimeMillis() + 90 // hold 90 ms, then release in a burst
                Thread.sleep(100)
            }
        }.apply { isDaemon = true; start() }
        try {
            assertTrue(waitUntil(5000) { receiver.state.value.status == ReceiverStatus.Playing })
            // Sample over time once it has had a chance to adapt.
            Thread.sleep(4000)
            var playingSamples = 0
            val latencies = mutableListOf<Int>()
            repeat(20) {
                Thread.sleep(100)
                val st = receiver.state.value
                if (st.status == ReceiverStatus.Playing) playingSamples++
                latencies += st.latencyMs
            }
            println("Clumped delivery: playing $playingSamples/20, latency ${latencies.minOrNull()}..${latencies.maxOrNull()} ms")
            assertTrue(playingSamples >= 18, "playback kept dropping out ($playingSamples/20 samples playing)")
            assertTrue(latencies.max() <= 330, "latency grew unbounded: ${latencies.max()} ms")
        } finally {
            clumping.set(false)
            clumper.join()
            receiver.stop()
            engine.stop()
        }
    }

    @Test
    fun wasapiLoopbackOpensAndReads() {
        if (!com.sun.jna.Platform.isWindows()) return
        val source = WasapiLoopbackSource()
        try {
            source.open()
            val buf = ByteArray(StreamConfig.PACKET_BYTES)
            var total = 0
            val end = System.currentTimeMillis() + 400
            while (System.currentTimeMillis() < end) {
                val n = source.read(buf, 0, buf.size)
                assertTrue(n >= 0)
                total += n
            }
            println("WASAPI loopback captured $total bytes in 400 ms (0 is normal if nothing is playing)")
        } finally {
            source.close()
        }
        assertNotNull(source)
    }
}
