package com.example.streamaudio

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import com.example.streamaudio.ui.HomeScreen
import com.example.streamaudio.ui.ListenScreen
import com.example.streamaudio.ui.StreamAudioTheme
import com.example.streamaudio.ui.ThemeMode
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test

/** Renders screens offscreen in both themes. Only runs when SCREENSHOTS_DIR is set. */
class ScreenshotRender {
    @Test
    fun renderScreens() {
        val dir = System.getenv("SCREENSHOTS_DIR") ?: return
        val client = ClientViewModel()
        for (mode in listOf(ThemeMode.Light, ThemeMode.Dark)) {
            render(File(dir, "home_${mode.name.lowercase()}.png")) {
                StreamAudioTheme(mode) { HomeScreen(mode, {}, {}, {}) }
            }
            render(File(dir, "listen_${mode.name.lowercase()}.png")) {
                StreamAudioTheme(mode) { ListenScreen(client) {} }
            }
        }
    }

    private fun render(file: File, content: @androidx.compose.runtime.Composable () -> Unit) {
        ImageComposeScene(width = 420 * 2, height = 860 * 2, density = Density(2f), content = content).use { scene ->
            scene.render(0)
            Thread.sleep(1700) // let discovery finish so the list state settles
            scene.render(2_000_000_000L)
            val image = scene.render(2_500_000_000L)
            file.writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
        }
    }
}
