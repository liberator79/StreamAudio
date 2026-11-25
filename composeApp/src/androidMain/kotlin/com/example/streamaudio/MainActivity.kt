package com.example.streamaudio

import android.Manifest
import android.app.Activity
import android.content.Context
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

    private var onHostPermissionGranted: (() -> Unit)? = null

    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        val action = onHostPermissionGranted
        onHostPermissionGranted = null
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            AndroidPermissionHelper.setProjectionPermission(data, result.resultCode)
            action?.invoke()
        } else {
            Toast.makeText(this, "Audio sharing was cancelled", Toast.LENGTH_SHORT).show()
        }
    }

    private val runtimePermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results[Manifest.permission.RECORD_AUDIO] == true || hasRecordPermission()) {
            requestProjection()
        } else {
            onHostPermissionGranted = null
            Toast.makeText(this, "Audio permission is required to share this device's sound", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        AndroidPermissionHelper.init(applicationContext)

        setContent {
            App(
                requestHostPermission = ::requestHostPermission,
                onOpenListen = ::requestNotificationPermission,
            )
        }
    }

    /** Lets the "Playing audio" notification (with its Disconnect button) show on Android 13+. */
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private fun requestHostPermission(onGranted: () -> Unit) {
        onHostPermissionGranted = onGranted
        val needed = buildList {
            if (!hasRecordPermission()) add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (needed.isEmpty()) requestProjection() else runtimePermissions.launch(needed.toTypedArray())
    }

    private fun hasRecordPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun requestProjection() {
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projectionLauncher.launch(manager.createScreenCaptureIntent())
    }
}
