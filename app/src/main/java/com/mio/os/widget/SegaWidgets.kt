package com.mio.os.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.text.format.DateFormat
import android.widget.RemoteViews
import com.mio.os.MainActivity
import com.mio.os.R
import com.mio.os.core.MioPlayer
import com.mio.os.service.MusicService

/**
 * SEGAWA AppWidget engine: real home-screen widgets any launcher can host.
 *
 * Clock (auto-ticking [android.widget.TextClock]), Search (deep link into
 * universal search), Music (live track + transport wired to [MusicService])
 * and Battery (sticky-broadcast reading + manual refresh). Updates are
 * event-driven; no polling service runs in the background.
 */
object SegaWidgets {

    fun updateAll(context: Context) {
        updateClock(context)
        updateSearch(context)
        updateMusic(context)
        updateBattery(context)
    }

    fun updateClock(context: Context) {
        val ids = ids(context, SegaClockWidget::class.java)
        if (ids.isEmpty()) return
        val views = RemoteViews(context.packageName, R.layout.widget_clock).apply {
            setTextViewText(R.id.clock_date, DateFormat.format("EEE, MMM d", System.currentTimeMillis()).toString())
            setOnClickPendingIntent(R.id.widget_root, launchTap(context, "clock", 101))
        }
        AppWidgetManager.getInstance(context).updateAppWidget(ids, views)
    }

    fun updateSearch(context: Context) {
        val ids = ids(context, SegaSearchWidget::class.java)
        if (ids.isEmpty()) return
        val views = RemoteViews(context.packageName, R.layout.widget_search).apply {
            setOnClickPendingIntent(R.id.widget_root, launchTap(context, "search", 102))
        }
        AppWidgetManager.getInstance(context).updateAppWidget(ids, views)
    }

    fun updateMusic(context: Context) {
        val ids = ids(context, SegaMusicWidget::class.java)
        if (ids.isEmpty()) return
        val track = MioPlayer.current.value
        val playing = MioPlayer.playing.value
        val views = RemoteViews(context.packageName, R.layout.widget_music).apply {
            setTextViewText(R.id.music_title, track?.title ?: "SEGAWA Music")
            setTextViewText(R.id.music_artist, track?.artist ?: "Nothing playing")
            setImageViewResource(
                R.id.music_toggle,
                if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
            )
            setOnClickPendingIntent(R.id.widget_root, launchTap(context, "music", 103))
            setOnClickPendingIntent(R.id.music_toggle, serviceTap(context, MusicService.ACTION_TOGGLE, 201))
            setOnClickPendingIntent(R.id.music_next, serviceTap(context, MusicService.ACTION_NEXT, 202))
            setOnClickPendingIntent(R.id.music_prev, serviceTap(context, MusicService.ACTION_PREV, 203))
        }
        AppWidgetManager.getInstance(context).updateAppWidget(ids, views)
    }

    fun updateBattery(context: Context) {
        val ids = ids(context, SegaBatteryWidget::class.java)
        if (ids.isEmpty()) return
        val batt = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batt?.let {
            val l = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val s = it.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (l >= 0 && s > 0) l * 100 / s else -1
        } ?: -1
        val status = batt?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        val views = RemoteViews(context.packageName, R.layout.widget_battery).apply {
            setTextViewText(R.id.battery_level, if (level >= 0) "$level%" else "—")
            setTextViewText(R.id.battery_state, if (charging) "Charging" else "On battery")
            setOnClickPendingIntent(R.id.widget_root, launchTap(context, "about", 104))
            setOnClickPendingIntent(R.id.battery_refresh, selfUpdate(context, SegaBatteryWidget::class.java, 301))
        }
        AppWidgetManager.getInstance(context).updateAppWidget(ids, views)
    }

    private fun ids(context: Context, cls: Class<*>): IntArray =
        AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, cls))

    private fun launchTap(context: Context, screen: String, rc: Int): PendingIntent {
        val i = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(MainActivity.EXTRA_OPEN_SCREEN, screen)
        return PendingIntent.getActivity(
            context, rc, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun serviceTap(context: Context, action: String, rc: Int): PendingIntent {
        val i = Intent(context, MusicService::class.java).setAction(action)
        return PendingIntent.getService(
            context, rc, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun selfUpdate(context: Context, cls: Class<*>, rc: Int): PendingIntent {
        val i = Intent(context, cls)
            .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids(context, cls))
        return PendingIntent.getBroadcast(
            context, rc, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

class SegaClockWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) =
        SegaWidgets.updateClock(context)
}

class SegaSearchWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) =
        SegaWidgets.updateSearch(context)
}

class SegaMusicWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) =
        SegaWidgets.updateMusic(context)
}

class SegaBatteryWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) =
        SegaWidgets.updateBattery(context)
}
