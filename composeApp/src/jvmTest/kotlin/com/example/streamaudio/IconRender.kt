package com.example.streamaudio

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.skia.EncodedImageFormat
import streamaudio.composeapp.generated.resources.Res
import streamaudio.composeapp.generated.resources.app_icon
import java.io.File
import kotlin.test.Test

/**
 * Regenerates the legacy launcher PNGs (Android 7.x, which has no adaptive icons) from
 * composeResources/drawable/app_icon.xml. Runs only when RENDER_ICONS_TO=<res dir> is set.
 */
class IconRender {
    @Test
    fun renderLauncherPngs() {
        val resDir = System.getenv("RENDER_ICONS_TO") ?: return
        val densities = mapOf("mdpi" to 48, "hdpi" to 72, "xhdpi" to 96, "xxhdpi" to 144, "xxxhdpi" to 192)
        for ((bucket, px) in densities) {
            render(File(resDir, "mipmap-$bucket/ic_launcher.png"), px, round = false)
            render(File(resDir, "mipmap-$bucket/ic_launcher_round.png"), px, round = true)
        }
    }

    private fun render(file: File, px: Int, round: Boolean) {
        ImageComposeScene(px, px, Density(1f)) {
            val shape = if (round) Modifier.clip(CircleShape) else Modifier
            Image(
                painterResource(Res.drawable.app_icon),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                // The round variant is clipped to a circle; scale up slightly so the rounded
                // square's corners fall outside it and the gradient fills the circle.
                modifier = Modifier.fillMaxSize().then(shape).then(if (round) Modifier.scale(1.18f) else Modifier),
            )
        }.use { scene ->
            scene.render(0)
            Thread.sleep(300) // let the resource load
            val image = scene.render(1_000_000_000L)
            file.parentFile.mkdirs()
            file.writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
        }
    }
}
