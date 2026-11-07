package com.mio.os.core

import android.app.NotificationManager
import android.app.UiModeManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import android.view.Window
import android.view.WindowManager
import androidx.core.content.getSystemService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Thin, honest wrapper over the Android APIs a normal launcher is allowed to use.
 * Anything that needs a system privilege opens the relevant system panel instead
 * of pretending to toggle it.
 */
object SystemControls {

    private val _torch = MutableStateFlow(false)
    val torch: StateFlow<Boolean> = _torch

    private val _dnd = MutableStateFlow(false)
    val dnd: StateFlow<Boolean> = _dnd

    private val _appDark = MutableStateFlow(true)
    val appDark: StateFlow<Boolean> = _appDark

    fun isTorchAvailable(context: Context): Boolean = runCatching {
        val cm = context.getSystemService<CameraManager>() ?: return false
        cm.cameraIdList.any { id ->
            cm.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
    }.getOrDefault(false)

    fun toggleTorch(context: Context): Boolean {
        val cm = context.getSystemService<CameraManager>() ?: return false
        val enabled = runCatching {
            val id = cm.cameraIdList.firstOrNull { cid ->
                cm.getCameraCharacteristics(cid).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                    cm.getCameraCharacteristics(cid).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            } ?: cm.cameraIdList.firstOrNull { cid ->
                cm.getCameraCharacteristics(cid).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return false
            val next = !_torch.value
            cm.setTorchMode(id, next)
            _torch.value = next
            next
        }.getOrDefault(false)
        return enabled
    }

    fun refreshDnd(context: Context) {
        val nm = context.getSystemService<NotificationManager>() ?: return
        _dnd.value = runCatching { nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL }
            .getOrDefault(false)
    }

    /** Requires the user to grant "Do Not Disturb access" once – a real system permission. */
    fun toggleDnd(context: Context): Boolean {
        val nm = context.getSystemService<NotificationManager>() ?: return false
        if (!nm.isNotificationPolicyAccessGranted) return false
        val on = nm.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL
        nm.setInterruptionFilter(
            if (on) NotificationManager.INTERRUPTION_FILTER_PRIORITY
            else NotificationManager.INTERRUPTION_FILTER_ALL
        )
        _dnd.value = on
        return true
    }

    /** App-level dark mode via the platform UiModeManager (API 31+). */
    fun toggleAppDark(context: Context) {
        val next = !_appDark.value
        _appDark.value = next
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val um = context.getSystemService<UiModeManager>()
            um?.setApplicationNightMode(
                if (next) UiModeManager.MODE_NIGHT_YES else UiModeManager.MODE_NIGHT_NO
            )
        }
    }

    // ---- Brightness -------------------------------------------------------

    fun systemBrightness(context: Context): Int = runCatching {
        Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
    }.getOrDefault(128).coerceIn(1, 255)

    fun setSystemBrightness(context: Context, value: Int): Boolean {
        if (!LauncherHelper.canWriteSettings(context)) return false
        return runCatching {
            Settings.System.putInt(
                context.contentResolver, Settings.System.SCREEN_BRIGHTNESS,
                value.coerceIn(1, 255)
            )
        }.getOrDefault(false)
    }

    /** Always works: changes the brightness of MIO OS' own window instantly. */
    fun setWindowBrightness(window: Window, value: Int) {
        runCatching {
            val lp = window.attributes
            lp.screenBrightness = (value / 255f).coerceIn(0.02f, 1f)
            window.attributes = lp
        }
    }

    fun autoBrightness(context: Context): Boolean = runCatching {
        Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE) ==
            Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
    }.getOrDefault(false)

    fun setAutoBrightness(context: Context, enabled: Boolean): Boolean {
        if (!LauncherHelper.canWriteSettings(context)) return false
        return runCatching {
            Settings.System.putInt(
                context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE,
                if (enabled) Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
                else Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            )
        }.getOrDefault(false)
    }

    // ---- Volume -----------------------------------------------------------

    fun audio(context: Context): AudioManager? = context.getSystemService<AudioManager>()

    fun mediaVolume(context: Context): Int {
        val am = audio(context) ?: return 0
        return runCatching { am.getStreamVolume(AudioManager.STREAM_MUSIC) }.getOrDefault(0)
    }

    fun mediaVolumeMax(context: Context): Int {
        val am = audio(context) ?: return 15
        return runCatching { am.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }.getOrDefault(15)
    }

    fun setMediaVolume(context: Context, v: Int) {
        val am = audio(context) ?: return
        runCatching {
            am.setStreamVolume(
                AudioManager.STREAM_MUSIC, v.coerceIn(0, mediaVolumeMax(context)),
                AudioManager.FLAG_SHOW_UI
            )
        }
    }

    // ---- Radio / system panels -------------------------------------------

    fun wifiPanel(context: Context) =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            Intent(Settings.Panel.ACTION_WIFI).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        else Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun bluetoothPanel(context: Context) =
        Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun internetPanel(context: Context) =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        else Intent(Settings.ACTION_WIRELESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun volumePanel(context: Context) =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            Intent(Settings.Panel.ACTION_VOLUME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        else Intent(Settings.ACTION_SOUND_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun airplanePanel(context: Context) =
        Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun displaySettings(context: Context) =
        Intent(Settings.ACTION_DISPLAY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun batterySettings(context: Context) =
        Intent(Intent.ACTION_POWER_USAGE_SUMMARY).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun rotationSettings(context: Context) =
        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun locationSettings(context: Context) =
        Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun castSettings(context: Context) =
        Intent(Settings.ACTION_CAST_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun hotspotSettings(context: Context) =
        Intent(Settings.ACTION_WIRELESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun wifiEnabled(context: Context): Boolean = runCatching {
        context.applicationContext.getSystemService<WifiManager>()?.isWifiEnabled == true
    }.getOrDefault(false)

    fun open(context: Context, intent: Intent) {
        runCatching { context.startActivity(intent) }
    }

    fun expandStatusBar(context: Context) {
        runCatching {
            val sb = Class.forName("android.app.StatusBarManager")
            val svc = context.getSystemService(sb.name)
            val m = sb.getMethod("expandNotificationsPanel")
            m.invoke(svc)
        }.onFailure {
            LauncherHelper.toast(context, "Notification shade expansion is restricted on this Android version")
        }
    }

    fun showVolumeUi(context: Context) {
        val am = audio(context) ?: return
        runCatching {
            am.adjustStreamVolume(
                AudioManager.STREAM_MUSIC, AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI
            )
        }
    }

    // ---- Lock the device (device admin, real API) ------------------------

    fun isDeviceAdmin(context: Context): Boolean {
        val dpm = context.getSystemService<DevicePolicyManager>() ?: return false
        val cn = ComponentName(context, com.mio.os.service.MioDeviceAdmin::class.java)
        return runCatching { dpm.isAdminActive(cn) }.getOrDefault(false)
    }

    fun lockDevice(context: Context): Boolean {
        val dpm = context.getSystemService<DevicePolicyManager>() ?: return false
        val cn = ComponentName(context, com.mio.os.service.MioDeviceAdmin::class.java)
        if (!isDeviceAdmin(context)) return false
        return runCatching { dpm.lockNow(); true }.getOrDefault(false)
    }
}
