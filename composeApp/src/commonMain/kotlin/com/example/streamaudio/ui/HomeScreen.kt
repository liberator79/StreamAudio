package com.example.streamaudio.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.streamaudio.hostingUnsupportedReason

@Composable
fun HomeScreen(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    onHost: () -> Unit,
    onListen: () -> Unit,
) {
    PageScaffold(
        title = "StreamAudio",
        actions = { ThemeToggle(themeMode, onThemeModeChange) },
    ) {
        Spacer(Modifier.height(16.dp))
        Text(
            "Share sound between your devices",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Play what's on your PC or phone through another device on the same Wi-Fi, with very little delay.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))

        val unsupported = hostingUnsupportedReason
        ModeCard(
            icon = Icons.Rounded.Podcasts,
            title = "Host",
            subtitle = unsupported ?: "Broadcast this device's audio",
            container = MaterialTheme.colorScheme.primaryContainer,
            content = MaterialTheme.colorScheme.onPrimaryContainer,
            accent = MaterialTheme.colorScheme.primary,
            onAccent = MaterialTheme.colorScheme.onPrimary,
            enabled = unsupported == null,
            onClick = onHost,
        )
        Spacer(Modifier.height(16.dp))
        ModeCard(
            icon = Icons.Rounded.Headphones,
            title = "Listen",
            subtitle = "Play audio from a host on your network",
            container = MaterialTheme.colorScheme.tertiaryContainer,
            content = MaterialTheme.colorScheme.onTertiaryContainer,
            accent = MaterialTheme.colorScheme.tertiary,
            onAccent = MaterialTheme.colorScheme.onTertiary,
            enabled = true,
            onClick = onListen,
        )

        Spacer(Modifier.height(32.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                Icons.Rounded.Wifi,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Text(
                "Both devices need to be on the same Wi-Fi network",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ModeCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    container: Color,
    content: Color,
    accent: Color,
    onAccent: Color,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(28.dp),
        color = container,
        modifier = Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.55f),
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(shape = RoundedCornerShape(20.dp), color = accent, modifier = Modifier.size(64.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = onAccent, modifier = Modifier.size(32.dp))
                }
            }
            Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge, color = content)
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = content.copy(alpha = 0.8f))
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = content)
        }
    }
}

@Composable
private fun ThemeToggle(mode: ThemeMode, onChange: (ThemeMode) -> Unit) {
    val (icon, label) = when (mode) {
        ThemeMode.System -> Icons.Rounded.BrightnessAuto to "Theme: follow system"
        ThemeMode.Light -> Icons.Rounded.LightMode to "Theme: light"
        ThemeMode.Dark -> Icons.Rounded.DarkMode to "Theme: dark"
    }
    IconButton(onClick = { onChange(ThemeMode.entries[(mode.ordinal + 1) % ThemeMode.entries.size]) }) {
        Icon(icon, contentDescription = label)
    }
}
