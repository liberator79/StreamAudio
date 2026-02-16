package com.example.streamaudio

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    val capturer = WindowsAudioCapturer()
    Window(
        onCloseRequest = ::exitApplication,
        title = "StreamAudio",
    ) {
        App()
    }
}