package com.example.streamaudio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class AudioStreamService : Service() {

    private val streamer = AudioStreamer() // Your actual streamer class

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "STOP" -> {
                stopForegroundService()
            }
            "START" -> {
                startForegroundService()
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundService() {
        // 1. Create Notification Channel (Required for Android O+)
        val channelId = "audio_stream_channel"
        val channelName = "Audio Stream Service"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, channelName, NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        // 2. Create Notification
        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Audio Streaming")
            .setContentText("Streaming internal audio...")
            .setSmallIcon(R.mipmap.ic_launcher) // Make sure this icon exists
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        // 3. START FOREGROUND (Crucial Step for Android 14)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                101,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(101, notification)
        }

        // 4. NOW it is safe to start the MediaProjection logic
        // We run this on a background thread to avoid blocking the main thread
        Thread {
            streamer.startStreaming(50005) // Port number
        }.start()
    }

    private fun stopForegroundService() {
        streamer.stopStreaming()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
}