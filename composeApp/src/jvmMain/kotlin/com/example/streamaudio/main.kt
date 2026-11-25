package com.example.streamaudio

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import org.jetbrains.compose.resources.painterResource
import streamaudio.composeapp.generated.resources.Res
import streamaudio.composeapp.generated.resources.app_icon

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "StreamAudio",
        icon = painterResource(Res.drawable.app_icon),
        state = rememberWindowState(size = DpSize(480.dp, 820.dp)),
    ) {
        window.minimumSize = java.awt.Dimension(380, 600)
        App()
    }
}
