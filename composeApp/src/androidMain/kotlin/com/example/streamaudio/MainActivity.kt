package com.example.streamaudio

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    private var pendingAction: (() -> Unit)? = null

    // 1. Screen Capture Launcher
    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            AndroidPermissionHelper.setPermission(result.data, result.resultCode)

            // Start Foreground Service
            val serviceIntent = Intent(this, AudioStreamService::class.java).apply {
                action = "START"
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }

            // Navigate only AFTER service starts
            pendingAction?.invoke()
        } else {
            pendingAction = null
        }
    }

    // 2. Microphone Permission Launcher
    private val requestAudioPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startMediaProjectionRequest()
        } else {
            Toast.makeText(this, "Permission required for internal audio", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        AndroidPermissionHelper.context = applicationContext

        setContent {
            App(
                checkPermissions = { onGranted ->
                    pendingAction = onGranted

                    // Check Audio Permission FIRST
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                        == PackageManager.PERMISSION_GRANTED) {
                        startMediaProjectionRequest()
                    } else {
                        requestAudioPermission.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }
            )
        }
    }

    private fun startMediaProjectionRequest() {
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val intent = projectionManager.createScreenCaptureIntent()
        projectionLauncher.launch(intent)
    }
}