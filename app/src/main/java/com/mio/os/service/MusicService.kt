package com.mio.os.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import com.mio.os.MainActivity
import com.mio.os.R
import com.mio.os.core.MioPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Foreground host for media playback: keeps the media notification (with lock
 * screen controls) alive and routes its buttons back into [MioPlayer].
 */
class MusicService : Service() {

    companion object {
        const val CHANNEL_ID = "mio_media"
        const val ACTION_SYNC = "com.mio.os.media.SYNC"
        const val ACTION_TOGGLE = "com.mio.os.media.TOGGLE"
        const val ACTION_NEXT = "com.mio.os.media.NEXT"
        const val ACTION_PREV = "com.mio.os.media.PREV"
        private const val NOTIF_ID = 1001

        fun start(context: Context) {
            val i = Intent(context, MusicService::class.java).setAction(ACTION_SYNC)
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(i)
                else context.startService(i)
            }
        }

        fun send(context: Context, action: String) {
            val i = Intent(context, MusicService::class.java).setAction(action)
            runCatching { context.startService(i) }
        }
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        MioPlayer.ensureSession(this)
        createChannel()
        scope.launch {
            combine(MioPlayer.current, MioPlayer.playing) { _, _ -> Unit }.collect {
                refresh()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE -> MioPlayer.toggle(this)
            ACTION_NEXT -> MioPlayer.next(this)
            ACTION_PREV -> MioPlayer.previous(this)
        }
        refresh()
        return START_STICKY
    }

    private fun refresh() {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        val notif = build()
        if (MioPlayer.playing.value) {
            runCatching { startForeground(NOTIF_ID, notif) }
        } else {
            nm.notify(NOTIF_ID, notif)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) stopForeground(STOP_FOREGROUND_DETACH)
            else @Suppress("DEPRECATION") stopForeground(false)
        }
    }

    private fun build(): Notification {
        val track = MioPlayer.current.value
        val playing = MioPlayer.playing.value
        val builder = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_music)
            .setContentTitle(track?.title ?: "SEGAWA Music")
            .setContentText(track?.artist ?: "Nothing playing")
            .setContentIntent(contentIntent())
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setOngoing(playing)

        val token = MioPlayer.sessionToken()
        if (token != null) {
            builder.setStyle(
                Notification.MediaStyle()
                    .setMediaSession(token)
                    .setShowActionsInCompactView(0, 1, 2)
            )
        }
        builder.addAction(action(R.drawable.ic_stat_prev, "Previous", ACTION_PREV))
        builder.addAction(
            action(
                if (playing) R.drawable.ic_stat_pause else R.drawable.ic_stat_play_arrow,
                if (playing) "Pause" else "Play",
                ACTION_TOGGLE
            )
        )
        builder.addAction(action(R.drawable.ic_stat_next, "Next", ACTION_NEXT))
        return builder.build()
    }

    private fun action(icon: Int, title: String, act: String): Notification.Action {
        val pi = PendingIntent.getService(
            this,
            act.hashCode(),
            Intent(this, MusicService::class.java).setAction(act),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Action.Builder(android.graphics.drawable.Icon.createWithResource(this, icon), title, pi)
            .build()
    }

    private fun contentIntent(): PendingIntent = PendingIntent.getActivity(
        this, 7,
        Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "SEGAWA Music playback", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Media controls for the SEGAWA OS music player"
                    setShowBadge(false)
                    enableVibration(false)
                }
            )
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.launch { }
        super.onDestroy()
    }
}
