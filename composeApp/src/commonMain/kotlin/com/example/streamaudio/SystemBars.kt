package com.example.streamaudio

import androidx.compose.runtime.Composable

/** Makes status/navigation bar icons readable for the app's current light/dark theme. */
@Composable
expect fun SystemBarAppearance(darkTheme: Boolean)
