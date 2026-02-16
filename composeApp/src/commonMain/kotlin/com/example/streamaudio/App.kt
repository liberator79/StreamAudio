package com.example.streamaudio

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Earbuds
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material.icons.filled.MusicVideo
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class Screen { LANDING, HOSTING, LISTENING }

@Composable
fun App(
    checkPermissions: ((onGranted: () -> Unit) -> Unit)? = null
) {
    val serverViewModel = remember { ServerViewModel() }
    val clientViewModel = remember { ClientViewModel() }
    MaterialTheme {
        var currentScreen by remember { mutableStateOf(Screen.LANDING) }
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            when(currentScreen){
                Screen.LANDING -> HomeScreen(
                    navigateToChild = { targetScreen -> currentScreen = targetScreen },
                    onCheckPermissions = checkPermissions
                )
                Screen.HOSTING -> HostingScreen(
                    navigateBack = { currentScreen = Screen.LANDING },
                    viewModel = serverViewModel
                )
                Screen.LISTENING -> ListeningScreen(
                    navigateBack = { currentScreen = Screen.LANDING },
                    viewModel = clientViewModel,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navigateToChild : (targetScreen : Screen) -> Unit,
    onCheckPermissions: ((onGranted: () -> Unit) -> Unit)? = null
){
    Scaffold(
        topBar = { TopAppBar(title = { Text("Home", fontWeight = FontWeight.Bold, fontSize = 28.sp) }) }
    ) { innerPadding ->

        Row (
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ){
            BigButton(
                icon = Icons.Default.PlayArrow,
                text = "Host",
                onClick = {
                    if(onCheckPermissions != null){
                        onCheckPermissions{
                            navigateToChild(Screen.HOSTING)
                        }
                    }else{
                        navigateToChild(Screen.HOSTING)
                    }
                }
            )

            BigButton(
                icon = Icons.Default.Earbuds,
                text = "Listen",
                onClick = { navigateToChild(Screen.LISTENING) }
            )
        }
    }
}

@Composable
fun BigButton(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(16.dp)
    ) {
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(8.dp),
            shadowElevation = 10.dp,
            tonalElevation = 2.dp,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.size(150.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = text,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListeningScreen(
    viewModel: ClientViewModel,
    navigateBack: () -> Unit
) {
    val isConnected by viewModel.isConnected.collectAsState()
    val status by viewModel.statusMessage.collectAsState()
    var ipInput by remember { mutableStateOf("") }
    DisposableEffect(Unit) {
        onDispose {
            //viewModel.disconnect()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Audio Receiver", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = navigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = if (isConnected) Icons.Default.VolumeUp else Icons.Default.VolumeMute,
                contentDescription = "Speaker",
                modifier = Modifier.size(100.dp),
                tint = if (isConnected) MaterialTheme.colorScheme.primary else Color.Gray
            )

            Spacer(modifier = Modifier.height(32.dp))

            OutlinedTextField(
                value = ipInput,
                onValueChange = { ipInput = it },
                label = { Text("Host IP Address") },
                placeholder = { Text("e.g. 192.168.1.5") },
                singleLine = true,
                enabled = !isConnected,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = status,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )

            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = { viewModel.toggleConnection(ipInput) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isConnected) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text(if (isConnected) "DISCONNECT" else "CONNECT")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HostingScreen(
    viewModel: ServerViewModel,
    navigateBack: () -> Unit
) {
    val ipAddress by viewModel.serverIp.collectAsState()
    val status by viewModel.statusMessage.collectAsState()

    DisposableEffect(Unit) {
        viewModel.toggleStreaming()
        onDispose {
            viewModel.toggleStreaming()
        }
    }

    val infiniteTransition = rememberInfiniteTransition()
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.2f,
        animationSpec = infiniteRepeatable(animation = tween(1000), repeatMode = RepeatMode.Reverse)
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 0.5f,
        animationSpec = infiniteRepeatable(animation = tween(1000), repeatMode = RepeatMode.Reverse)
    )

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Broadcasting", fontWeight = FontWeight.Bold)
                        Text("ON AIR", fontSize = 12.sp, color = Color.Red, fontWeight = FontWeight.Bold)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = navigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier.padding(innerPadding).fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // --- Pulsing Icon ---
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier.size(150.dp).scale(scale).alpha(alpha)
                        .clip(CircleShape).background(Color.Blue.copy(alpha = 0.3f))
                )
                Surface(
                    shape = CircleShape, color = Color.Blue,
                    modifier = Modifier.size(100.dp), shadowElevation = 10.dp
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.MusicNote, "Music", tint = Color.White, modifier = Modifier.size(50.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text("Streaming Audio...", style = MaterialTheme.typography.headlineSmall)

            Spacer(modifier = Modifier.height(16.dp))

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("HOST IP ADDRESS", style = MaterialTheme.typography.labelMedium)
                    Text(
                        text = ipAddress, // 👈 Shows real IP now
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text("Status: $status", style = MaterialTheme.typography.bodySmall)

            Spacer(modifier = Modifier.height(48.dp))

            Button(
                onClick = navigateBack,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                contentPadding = PaddingValues(horizontal = 32.dp, vertical = 16.dp)
            ) {
                Icon(Icons.Default.Stop, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Stop Stream", color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
    }
}