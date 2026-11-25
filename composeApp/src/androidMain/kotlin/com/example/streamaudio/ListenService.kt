package com.example.streamaudio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Foreground "media playback" service that runs while listening. Without it Android freezes
 * the app once the screen turns off and the audio cuts out. The receiver itself lives in
 * [ListenerSession]; this service only keeps the process in the foreground and shows
 * the notification.
 */
class ListenService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var started = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        AndroidPermissionHelper.init(applicationContext)
        if (intent?.action == ACTION_DISCONNECT) {
            ListenerSession.receiver.stop() // the state observer below then stops the service
        }

        val receiver = ListenerSession.receiver
        if (!started) {
            started = true
            startInForeground(receiver.state.value)
            scope.launch {
                receiver.state
                    .map { it.isActive to (it.status == ReceiverStatus.HostLost) }
                    .distinctUntilChanged()
                    .collect { (active, _) ->
                        if (!active) {
                            stopForeground(STOP_FOREGROUND_REMOVE)
                            stopSelf()
                        } else {
                            notificationManager().notify(NOTIFICATION_ID, buildNotification(receiver.state.value))
                        }
                    }
            }
        }
        return START_NOT_STICKY
    }

    private fun startInForeground(state: ReceiverState) {
        val notification = buildNotification(state)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun notificationManager() = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun buildNotification(state: ReceiverState): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager().createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Listening", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val immutable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            immutable,
        )
        val disconnect = PendingIntent.getService(
            this, 2,
            Intent(this, ListenService::class.java).setAction(ACTION_DISCONNECT),
            immutable,
        )
        val text = if (state.status == ReceiverStatus.HostLost) {
            "Waiting for ${state.host}…"
        } else {
            "Playing audio from ${state.host}"
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("StreamAudio")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_stat_stream)
            .setContentIntent(openApp)
            .addAction(0, "Disconnect", disconnect)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        const val ACTION_DISCONNECT = "com.example.streamaudio.DISCONNECT"
        private const val CHANNEL_ID = "listen_channel"
        private const val NOTIFICATION_ID = 202
    }
}
