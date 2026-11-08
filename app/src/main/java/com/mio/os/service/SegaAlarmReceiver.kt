package com.mio.os.service

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.mio.os.MainActivity
import com.mio.os.core.Mio
import com.mio.os.core.MioPlayer
import com.mio.os.core.Routines
import com.mio.os.core.WallpaperRepo

/**
 * Schedules and runs wallpaper rotation plus automation routines.
 *
 * Uses inexact alarms (battery-friendly, no exact-alarm permission) and
 * re-arms everything after reboot. All work here is idempotent: firing twice
 * only rotates twice / re-notifies.
 */
class SegaAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                val s = Mio.prefs.current
                if (s.wallRotationMin > 0) scheduleRotation(app, s.wallRotationMin)
                Routines.all(s.routines).filter { it.enabled }.forEach {
                    scheduleRoutine(app, it.id, it.hour, it.minute)
                }
            }
            ACTION_ROTATE -> {
                val s = Mio.prefs.current
                val next = WallpaperRepo.nextRotation(s.wallpaperId, s.favWalls)
                Mio.prefs.edit {
                    it.copy(
                        wallpaperId = next,
                        wallpaperUri = null,
                        wallHistory = WallpaperRepo.pushHistory(it.wallHistory, next),
                    )
                }
            }
            ACTION_ROUTINE -> {
                val id = intent.getStringExtra(EXTRA_ID) ?: return
                val r = Routines.all(Mio.prefs.current.routines).firstOrNull { it.id == id }
                if (r == null || !r.enabled) return
                runRoutine(app, r.kind, r.payload, r.name)
                scheduleRoutine(app, r.id, r.hour, r.minute)
            }
        }
    }

    private fun runRoutine(context: Context, kind: String, payload: String, name: String) {
        when (kind) {
            "music" -> {
                val tracks = Mio.media.tracksWithDemo()
                if (tracks.isNotEmpty()) MioPlayer.enqueue(context, tracks.first(), tracks)
            }
            "wallpaper" -> {
                val id = payload.ifBlank {
                    WallpaperRepo.nextRotation(Mio.prefs.current.wallpaperId, Mio.prefs.current.favWalls)
                }
                Mio.prefs.edit { it.copy(wallpaperId = id, wallpaperUri = null) }
            }
            "app" -> if (payload.isNotBlank()) runCatching { Mio.apps.launchPackage(payload) }
            "focus" -> {
                runCatching {
                    val nm = context.getSystemService(NotificationManager::class.java)
                    if (nm != null && nm.isNotificationPolicyAccessGranted &&
                        nm.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL
                    ) {
                        nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                    }
                }
                notify(context, "Focus routine", name)
            }
            else -> notify(context, "Reminder", payload.ifBlank { name })
        }
    }

    private fun notify(context: Context, title: String, text: String) {
        runCatching {
            if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            runCatching {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL, "Routines & reminders", NotificationManager.IMPORTANCE_DEFAULT),
                )
            }
            val tap = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val n = NotificationCompat.Builder(context, CHANNEL)
                .setContentTitle(title)
                .setContentText(text.take(120))
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentIntent(tap)
                .setAutoCancel(true)
                .build()
            nm.notify(NOTIF_ID + title.hashCode(), n)
        }
    }

    companion object {
        const val ACTION_ROTATE = "com.mio.os.ROTATE_WALLPAPER"
        const val ACTION_ROUTINE = "com.mio.os.RUN_ROUTINE"
        const val EXTRA_ID = "id"
        private const val CHANNEL = "segawa_routines"
        private const val NOTIF_ID = 4100

        fun scheduleRotation(context: Context, minutes: Int) {
            val mins = minutes.coerceAtLeast(15)
            val am = context.getSystemService(AlarmManager::class.java) ?: return
            am.setInexactRepeating(
                AlarmManager.RTC,
                System.currentTimeMillis() + mins * 60_000L,
                mins * 60_000L,
                rotateIntent(context),
            )
        }

        fun cancelRotation(context: Context) {
            val am = context.getSystemService(AlarmManager::class.java) ?: return
            am.cancel(rotateIntent(context))
        }

        fun scheduleRoutine(context: Context, id: String, hour: Int, minute: Int) {
            val am = context.getSystemService(AlarmManager::class.java) ?: return
            val cal = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
                set(java.util.Calendar.MINUTE, minute.coerceIn(0, 59))
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
                if (timeInMillis <= System.currentTimeMillis()) add(java.util.Calendar.DAY_OF_YEAR, 1)
            }
            runCatching {
                am.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, cal.timeInMillis, routineIntent(context, id)
                )
            }
        }

        fun cancelRoutine(context: Context, id: String) {
            val am = context.getSystemService(AlarmManager::class.java) ?: return
            am.cancel(routineIntent(context, id))
        }

        private fun rotateIntent(context: Context): PendingIntent {
            val i = Intent(context, SegaAlarmReceiver::class.java).setAction(ACTION_ROTATE)
            return PendingIntent.getBroadcast(
                context, 9001, i,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        private fun routineIntent(context: Context, id: String): PendingIntent {
            val i = Intent(context, SegaAlarmReceiver::class.java)
                .setAction(ACTION_ROUTINE).putExtra(EXTRA_ID, id)
            return PendingIntent.getBroadcast(
                context, 9100 + (id.hashCode() and 0x7FFF), i,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}
