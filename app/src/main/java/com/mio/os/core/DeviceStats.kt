package com.mio.os.core

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class BatteryState(val level: Int = 0, val charging: Boolean = false, val temperatureC: Float = 0f)

data class StorageState(
    val usedBytes: Long = 0, val totalBytes: Long = 1, val freeBytes: Long = 0,
) {
    val usedGb: String get() = String.format("%.1f", usedBytes / 1_073_741_824.0)
    val totalGb: String get() = String.format("%.0f", totalBytes / 1_073_741_824.0)
    val percent: Int get() = ((usedBytes.toDouble() / totalBytes.toDouble()) * 100).toInt().coerceIn(0, 100)
}

data class RamState(val usedMb: Long = 0, val totalMb: Long = 1) {
    val percent: Int get() = ((usedMb.toDouble() / totalMb.toDouble()) * 100).toInt().coerceIn(0, 100)
}

data class DeviceInfo(
    val model: String = Build.MODEL ?: "Android",
    val manufacturer: String = Build.MANUFACTURER ?: "",
    val android: String = Build.VERSION.RELEASE ?: "",
    val sdk: Int = Build.VERSION.SDK_INT,
    val soc: String = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MODEL else Build.HARDWARE) ?: "Unknown",
    val cores: Int = Runtime.getRuntime().availableProcessors(),
)

object DeviceStats {

    private val _battery = MutableStateFlow(BatteryState())
    val battery: StateFlow<BatteryState> = _battery

    private var registered = false

    fun start(context: Context) {
        if (registered) return
        registered = true
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
        }
        runCatching {
            context.registerReceiver(object : BroadcastReceiver() {
                override fun onReceive(c: Context?, intent: Intent?) {
                    intent ?: return
                    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0)
                    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
                    val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                    val temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f
                    _battery.value = BatteryState(
                        level = (level * 100 / scale).coerceIn(0, 100),
                        charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                            status == BatteryManager.BATTERY_STATUS_FULL,
                        temperatureC = temp,
                    )
                }
            }, filter)
        }
    }

    fun storage(): StorageState {
        val stat = StatFs(Environment.getDataDirectory().absolutePath)
        val total = stat.blockCountLong * stat.blockSizeLong
        val free = stat.availableBlocksLong * stat.blockSizeLong
        return StorageState(usedBytes = total - free, totalBytes = total, freeBytes = free)
    }

    fun ram(context: Context): RamState {
        val am = context.getSystemService(ActivityManager::class.java) ?: return RamState()
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        val total = mi.totalMem / 1_048_576
        val avail = mi.availMem / 1_048_576
        return RamState(usedMb = total - avail, totalMb = total.coerceAtLeast(1))
    }

    fun info(): DeviceInfo = DeviceInfo()

    fun installedAppCount(context: Context): Int =
        context.packageManager.getInstalledApplications(0).size

    /** True when the user granted Usage Access in system settings. */
    fun hasUsageAccess(context: Context): Boolean = runCatching {
        val ops = context.getSystemService(android.app.AppOpsManager::class.java) ?: return false
        val mode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            ops.unsafeCheckOpNoThrow(
                android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(), context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(
                android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(), context.packageName
            )
        }
        mode == android.app.AppOpsManager.MODE_ALLOWED
    }.getOrDefault(false)

    /** Top packages by foreground time in the last 24h (minutes). Empty unless granted. */
    fun topUsage(context: Context, limit: Int = 5): List<Pair<String, Long>> = runCatching {
        if (!hasUsageAccess(context)) return emptyList()
        val usm = context.getSystemService(android.app.usage.UsageStatsManager::class.java)
            ?: return emptyList()
        val end = System.currentTimeMillis()
        val stats = usm.queryUsageStats(
            android.app.usage.UsageStatsManager.INTERVAL_DAILY, end - 86_400_000L, end
        ) ?: return emptyList()
        stats.filter { it.totalTimeInForeground > 0 }
            .sortedByDescending { it.totalTimeInForeground }
            .take(limit)
            .map { it.packageName to it.totalTimeInForeground / 60_000L }
    }.getOrDefault(emptyList())
}
