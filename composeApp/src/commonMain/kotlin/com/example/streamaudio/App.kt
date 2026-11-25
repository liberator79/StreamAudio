package com.example.streamaudio

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.streamaudio.ui.HomeScreen
import com.example.streamaudio.ui.HostScreen
import com.example.streamaudio.ui.ListenScreen
import com.example.streamaudio.ui.StreamAudioTheme
import com.example.streamaudio.ui.ThemeMode

enum class Screen { Home, Host, Listen }

/**
 * @param requestHostPermission Android runs its capture-permission flow here before hosting
 * starts; desktop passes null and starts straight away.
 */
@Composable
fun App(
    requestHostPermission: ((onGranted: () -> Unit) -> Unit)? = null,
    onOpenListen: (() -> Unit)? = null,
) {
    var themeMode by remember {
        mutableStateOf(AppSettings.getString(KEY_THEME)?.let { s -> ThemeMode.entries.firstOrNull { it.name == s } } ?: ThemeMode.System)
    }
    val serverViewModel = viewModel { ServerViewModel() }
    val clientViewModel = viewModel { ClientViewModel() }
    var screen by rememberSaveable { mutableStateOf(Screen.Home) }

    StreamAudioTheme(themeMode) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            AnimatedContent(
                targetState = screen,
                transitionSpec = {
                    val forward = targetState != Screen.Home
                    (slideInHorizontally(tween(260)) { w -> if (forward) w / 5 else -w / 5 } + fadeIn(tween(260)))
                        .togetherWith(slideOutHorizontally(tween(200)) { w -> if (forward) -w / 5 else w / 5 } + fadeOut(tween(200)))
                },
                label = "screen",
            ) { current ->
                when (current) {
                    Screen.Home -> HomeScreen(
                        themeMode = themeMode,
                        onThemeModeChange = {
                            themeMode = it
                            AppSettings.putString(KEY_THEME, it.name)
                        },
                        onHost = { screen = Screen.Host },
                        onListen = {
                            onOpenListen?.invoke()
                            screen = Screen.Listen
                        },
                    )
                    Screen.Host -> HostScreen(
                        viewModel = serverViewModel,
                        onStart = {
                            if (requestHostPermission != null) {
                                requestHostPermission { serverViewModel.start() }
                            } else {
                                serverViewModel.start()
                            }
                        },
                        onBack = { screen = Screen.Home },
                    )
                    Screen.Listen -> ListenScreen(
                        viewModel = clientViewModel,
                        onBack = { screen = Screen.Home },
                    )
                }
            }
        }
    }
}

private const val KEY_THEME = "theme_mode"
