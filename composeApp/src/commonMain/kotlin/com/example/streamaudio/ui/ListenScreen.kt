package com.example.streamaudio.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.streamaudio.ClientViewModel
import com.example.streamaudio.DiscoveredHost
import com.example.streamaudio.LatencyMode
import com.example.streamaudio.ReceiverState
import com.example.streamaudio.ReceiverStatus
import kotlin.math.roundToInt

@Composable
fun ListenScreen(viewModel: ClientViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val latencyMode by viewModel.latencyMode.collectAsState()

    PageScaffold(
        title = "Listen",
        onBack = {
            viewModel.disconnect()
            onBack()
        },
    ) {
        AnimatedContent(
            targetState = state.isActive,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "listen",
        ) { active ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (active) {
                    NowPlaying(state, onDisconnect = viewModel::disconnect)
                } else {
                    ConnectPanel(viewModel, state)
                }
                SectionTitle("Latency")
                LatencySelector(latencyMode, viewModel::setLatencyMode)
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun ConnectPanel(viewModel: ClientViewModel, state: ReceiverState) {
    val hosts by viewModel.hosts.collectAsState()
    val scanning by viewModel.scanning.collectAsState()
    var address by rememberSaveable { mutableStateOf(viewModel.lastHost) }

    LaunchedEffect(Unit) { viewModel.scan() }

    state.message?.let {
        Spacer(Modifier.height(8.dp))
        NoticeCard(it, isError = true)
    }

    SectionTitle("Hosts on your network") {
        if (scanning) {
            CircularProgressIndicator(Modifier.size(20.dp).padding(2.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(12.dp))
        } else {
            IconButton(onClick = viewModel::scan) {
                Icon(Icons.Rounded.Refresh, contentDescription = "Search again")
            }
        }
    }

    if (hosts.isEmpty()) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                if (scanning) "Searching…" else "No hosts found. Start hosting on the other device, then refresh. If it still doesn't appear, enter its address below.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(20.dp),
            )
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            hosts.forEach { host -> HostRow(host) { viewModel.connect(host.address) } }
        }
    }

    SectionTitle("Connect by address")
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = address,
            onValueChange = { address = it.filter { c -> !c.isWhitespace() } },
            label = { Text("Host IP address") },
            placeholder = { Text("192.168.1.5") },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { viewModel.connect(address) }),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        Button(
            onClick = { viewModel.connect(address) },
            enabled = address.isNotBlank(),
            modifier = Modifier.height(56.dp).padding(top = 6.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text("Connect")
        }
    }
}

@Composable
private fun HostRow(host: DiscoveredHost, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(44.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (host.isPhone) Icons.Rounded.Smartphone else Icons.Rounded.Computer,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(host.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                Text(host.address, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "Connect", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun NowPlaying(state: ReceiverState, onDisconnect: () -> Unit) {
    val playing = state.status == ReceiverStatus.Playing
    Spacer(Modifier.height(8.dp))
    Beacon(
        icon = if (playing) Icons.AutoMirrored.Rounded.VolumeUp else Icons.Rounded.Headphones,
        active = playing,
        color = MaterialTheme.colorScheme.tertiary,
        onColor = MaterialTheme.colorScheme.onTertiary,
    )
    Spacer(Modifier.height(8.dp))
    val scheme = MaterialTheme.colorScheme
    when (state.status) {
        ReceiverStatus.Playing -> StatusPill("Playing", scheme.tertiaryContainer, scheme.onTertiaryContainer)
        ReceiverStatus.Buffering -> StatusPill("Buffering…", scheme.secondaryContainer, scheme.onSecondaryContainer)
        ReceiverStatus.Connecting -> StatusPill("Connecting…", scheme.secondaryContainer, scheme.onSecondaryContainer)
        ReceiverStatus.HostLost -> StatusPill("Waiting for host…", scheme.errorContainer, scheme.onErrorContainer)
        else -> Unit
    }
    Spacer(Modifier.height(6.dp))
    Text(
        state.host,
        style = MaterialTheme.typography.titleMedium,
        fontFamily = FontFamily.Monospace,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (state.status == ReceiverStatus.HostLost || state.status == ReceiverStatus.Connecting) {
        Text(
            "Playback resumes automatically when the host is sharing.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )
    }

    Spacer(Modifier.height(20.dp))
    LevelBars(level = state.level, color = MaterialTheme.colorScheme.tertiary)
    Spacer(Modifier.height(20.dp))

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatTile(Icons.Rounded.Timer, "Delay", if (playing) "~${state.latencyMs} ms" else "-", Modifier.weight(1f))
        StatTile(Icons.Rounded.NetworkCheck, "Packet loss", if (playing) formatLoss(state.lossPercent) else "-", Modifier.weight(1f))
    }

    Spacer(Modifier.height(24.dp))
    FilledTonalButton(
        onClick = onDisconnect,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
        modifier = Modifier.fillMaxWidth().height(56.dp),
    ) {
        Icon(Icons.Rounded.LinkOff, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text("Disconnect", style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun LatencySelector(mode: LatencyMode, onChange: (LatencyMode) -> Unit) {
    val modes = LatencyMode.entries
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        modes.forEachIndexed { index, m ->
            SegmentedButton(
                selected = m == mode,
                onClick = { onChange(m) },
                shape = SegmentedButtonDefaults.itemShape(index, modes.size),
            ) {
                Text(m.label)
            }
        }
    }
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp, start = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Rounded.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        Text(
            "${mode.description} Buffer: ${mode.targetMs} ms.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun formatLoss(percent: Float): String =
    if (percent < 0.1f) "0%" else "${(percent * 10).roundToInt() / 10f}%"
