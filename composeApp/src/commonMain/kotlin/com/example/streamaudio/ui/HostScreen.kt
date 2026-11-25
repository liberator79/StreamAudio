package com.example.streamaudio.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.example.streamaudio.HostStatus
import com.example.streamaudio.ServerViewModel

@Composable
fun HostScreen(
    viewModel: ServerViewModel,
    onStart: () -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val addresses by viewModel.addresses.collectAsState()

    // Start automatically on first entry only (not again after rotation); afterwards the user is in control.
    var autoStarted by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        viewModel.refreshAddresses()
        if (!autoStarted && state.status == HostStatus.Idle) onStart()
        autoStarted = true
    }

    val live = state.status == HostStatus.Live
    PageScaffold(
        title = "Host",
        onBack = {
            viewModel.stop()
            onBack()
        },
    ) {
        Spacer(Modifier.height(8.dp))
        Beacon(
            icon = Icons.Rounded.Podcasts,
            active = live,
            color = MaterialTheme.colorScheme.primary,
            onColor = MaterialTheme.colorScheme.onPrimary,
        )
        Spacer(Modifier.height(8.dp))
        when (state.status) {
            HostStatus.Live -> StatusPill("LIVE", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
            HostStatus.Starting -> StatusPill("Starting…", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
            HostStatus.Error -> StatusPill("Not sharing", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer, showDot = false)
            HostStatus.Idle -> StatusPill("Stopped", MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.onSurfaceVariant, showDot = false)
        }

        Spacer(Modifier.height(20.dp))
        LevelBars(level = if (live) state.level else 0f, color = MaterialTheme.colorScheme.primary)
        if (live && state.level < 0.003f) {
            Text(
                "Nothing playing yet. Start some music or a video on this device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        state.message?.let {
            Spacer(Modifier.height(16.dp))
            NoticeCard(it, isError = state.status == HostStatus.Error)
        }

        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(Icons.Rounded.Groups, "Listeners", state.listeners.toString(), Modifier.weight(1f))
            StatTile(Icons.Rounded.Speaker, "Source", state.sourceName.ifEmpty { "-" }, Modifier.weight(1f))
            StatTile(Icons.Rounded.GraphicEq, "Quality", "48 kHz", Modifier.weight(1f))
        }

        SectionTitle("How to connect")
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    "On the other device, open StreamAudio and tap Listen. This device shows up automatically. You can also type in its address:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                if (addresses.isEmpty()) {
                    Text(
                        "No Wi-Fi or Ethernet connection found",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                addresses.forEachIndexed { index, address ->
                    if (index > 0) HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    Text(
                        address.address,
                        style = MaterialTheme.typography.headlineSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        address.interfaceName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }

        Spacer(Modifier.height(28.dp))
        when (state.status) {
            HostStatus.Live, HostStatus.Starting -> FilledTonalButton(
                onClick = viewModel::stop,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                if (state.status == HostStatus.Starting) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onErrorContainer)
                } else {
                    Icon(Icons.Rounded.Stop, contentDescription = null)
                }
                Spacer(Modifier.width(8.dp))
                Text("Stop sharing", style = MaterialTheme.typography.titleMedium)
            }
            HostStatus.Idle, HostStatus.Error -> Button(
                onClick = onStart,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (state.status == HostStatus.Error) "Try again" else "Start sharing", style = MaterialTheme.typography.titleMedium)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
