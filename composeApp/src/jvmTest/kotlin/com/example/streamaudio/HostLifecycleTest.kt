package com.example.streamaudio

import java.net.DatagramSocket
import java.net.InetSocketAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HostLifecycleTest {

    /** Real system capture (WASAPI on Windows): start/stop twice must not crash or leak the port. */
    @Test
    fun systemAudioHostCanStartStopRepeatedly() {
        if (!com.sun.jna.Platform.isWindows()) return
        val port = DatagramSocket(null).run { bind(InetSocketAddress("0.0.0.0", 0)); localPort.also { close() } }
        val engine = HostEngine(port)
        repeat(2) { round ->
            engine.start(SystemAudioSource())
            val deadline = System.currentTimeMillis() + 3000
            while (engine.state.value.status == HostStatus.Starting && System.currentTimeMillis() < deadline) Thread.sleep(10)
            assertEquals(HostStatus.Live, engine.state.value.status, "round $round: ${engine.state.value}")
            println("round $round live via '${engine.state.value.sourceName}'")
            Thread.sleep(300)
            engine.stop()
            assertEquals(HostStatus.Idle, engine.state.value.status)
            Thread.sleep(300) // let the capture thread run WASAPI cleanup
        }
        assertTrue(true)
    }
}
