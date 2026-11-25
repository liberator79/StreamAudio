package com.example.streamaudio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat

/**
 * Owns the one and only host pipeline on Android. Android requires the media-projection
 * foreground service to be running *before* the projection is obtained, so the projection
 * and the capture both live here.
 */
class AudioStreamService : Service() {

    private var projection: MediaProjection? = null

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            // The user revoked sharing from the system UI.
            stopSelf()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopSelf()
            ACTION_START -> startHosting()
        }
        return START_NOT_STICKY
    }

    private fun startHosting() {
        AndroidPermissionHelper.init(applicationContext)
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            failAndStop("Sharing audio needs Android 10 or newer")
            return
        }
        if (projection != null) return // Already hosting.

        val (resultCode, data) = AndroidPermissionHelper.consumeProjectionPermission() ?: run {
            failAndStop("Screen-sharing permission is needed to capture audio")
            return
        }
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val p = runCatching { manager.getMediaProjection(resultCode, data) }.getOrNull() ?: run {
            failAndStop("Android refused the audio-capture session. Try again.")
            return
        }
        p.registerCallback(projectionCallback, Handler(Looper.getMainLooper()))
        projection = p
        AndroidHost.engine.start(PlaybackCaptureSource(p))
    }

    private fun failAndStop(message: String) {
        stopSelf()
        AndroidHost.engine.reportError(message)
    }

    override fun onDestroy() {
        AndroidHost.engine.stop()
        projection?.let {
            it.unregisterCallback(projectionCallback)
            it.stop()
        }
        projection = null
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Audio sharing", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val immutable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            immutable,
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, AudioStreamService::class.java).setAction(ACTION_STOP),
            immutable,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Sharing audio")
            .setContentText("Listeners on your Wi-Fi can hear this device")
            .setSmallIcon(R.drawable.ic_stat_stream)
            .setContentIntent(openApp)
            .addAction(0, "Stop", stop)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        const val ACTION_START = "com.example.streamaudio.START"
        const val ACTION_STOP = "com.example.streamaudio.STOP"
        private const val CHANNEL_ID = "audio_stream_channel"
        private const val NOTIFICATION_ID = 101
    }
}
