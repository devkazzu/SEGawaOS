package com.mio.os.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.mio.os.core.MioNotifications

/**
 * Real notification indicators: once the user grants notification access
 * (Settings ▸ Notifications ▸ Device & app notifications), MIO OS can show
 * accurate unread counts and per-app badges on the home screen.
 */
class MioNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        publish()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        publish()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        publish()
    }

    private fun publish() {
        val active = runCatching { activeNotifications?.toList() ?: emptyList() }
            .getOrDefault(emptyList())
        val relevant = active.filter { sbn ->
            sbn != null && !sbn.isOngoing && sbn.notification != null &&
                (sbn.notification.flags and android.app.Notification.FLAG_GROUP_SUMMARY) == 0
        }
        MioNotifications.publish(
            count = relevant.size,
            packages = relevant.mapNotNull { it.packageName }.toSet()
        )
    }
}
