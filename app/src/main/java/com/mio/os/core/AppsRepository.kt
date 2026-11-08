package com.mio.os.core

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.Settings
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

enum class MioCategory(val label: String) {
    GAME("Games"), AUDIO("Music"), VIDEO("Video"), IMAGE("Photos"),
    SOCIAL("Social"), NEWS("News"), MAPS("Maps"), PRODUCTIVITY("Work"),
    ACCESSIBILITY("Tools"), OTHER("Apps")
}

data class MioApp(
    val packageName: String,
    val activity: String,
    val name: String,
    val icon: ImageBitmap?,
    val tint: Int,
    val category: MioCategory,
    val isSystem: Boolean,
) {
    val key: String get() = "$packageName/$activity"
}

/**
 * Reads every launchable activity on the device (this is exactly what a home
 * screen is allowed and expected to do) and keeps icons decoded off the main
 * thread so scrolling stays at 60fps.
 */
class AppsRepository(private val context: Context) {

    private val _apps = MutableStateFlow<List<MioApp>>(emptyList())
    val apps: StateFlow<List<MioApp>> = _apps

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    @Volatile private var lastLoad = 0L

    suspend fun ensureLoaded(force: Boolean = false) {
        if (!force && _apps.value.isNotEmpty() && System.currentTimeMillis() - lastLoad < 60_000) return
        refresh()
    }

    suspend fun refresh() = withContext(Dispatchers.IO) {
        _loading.value = true
        try {
            val pm = context.packageManager
            val me = context.packageName
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            @Suppress("DEPRECATION")
            val resolved = pm.queryIntentActivities(intent, 0)
            val seen = HashSet<String>()
            val out = ArrayList<MioApp>(resolved.size)
            for (ri in resolved) {
                val ai = ri.activityInfo ?: continue
                if (ai.packageName == me) continue
                if (!seen.add(ai.packageName)) continue
                val label = runCatching { ri.loadLabel(pm).toString() }
                    .getOrDefault(ai.packageName)
                val drawable: Drawable? = runCatching { ri.loadIcon(pm) }.getOrNull()
                val bitmap = drawable?.let { d ->
                    runCatching { d.toBitmap(144, 144).asImageBitmap() }.getOrNull()
                }
                val appInfo: ApplicationInfo? = runCatching {
                    pm.getApplicationInfo(ai.packageName, 0)
                }.getOrNull()
                out += MioApp(
                    packageName = ai.packageName,
                    activity = ai.name,
                    name = label,
                    icon = bitmap,
                    tint = runCatching { (ai.icon ?: 0) }.getOrDefault(0),
                    category = mapCategory(appInfo?.category ?: ApplicationInfo.CATEGORY_UNDEFINED),
                    isSystem = ((appInfo?.flags ?: 0) and ApplicationInfo.FLAG_SYSTEM) != 0,
                )
            }
            _apps.value = out.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
            lastLoad = System.currentTimeMillis()
        } catch (_: Throwable) {
            // A locked-down device can refuse package queries – keep whatever we had.
        } finally {
            _loading.value = false
        }
    }

    private fun mapCategory(c: Int): MioCategory = when (c) {
        ApplicationInfo.CATEGORY_GAME -> MioCategory.GAME
        ApplicationInfo.CATEGORY_AUDIO -> MioCategory.AUDIO
        ApplicationInfo.CATEGORY_VIDEO -> MioCategory.VIDEO
        ApplicationInfo.CATEGORY_IMAGE -> MioCategory.IMAGE
        ApplicationInfo.CATEGORY_SOCIAL -> MioCategory.SOCIAL
        ApplicationInfo.CATEGORY_NEWS -> MioCategory.NEWS
        ApplicationInfo.CATEGORY_MAPS -> MioCategory.MAPS
        ApplicationInfo.CATEGORY_PRODUCTIVITY -> MioCategory.PRODUCTIVITY
        ApplicationInfo.CATEGORY_ACCESSIBILITY -> MioCategory.ACCESSIBILITY
        else -> MioCategory.OTHER
    }

    fun byPackage(pkg: String): MioApp? = _apps.value.firstOrNull { it.packageName == pkg }

    fun launch(app: MioApp) {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            component = ComponentName(app.packageName, app.activity)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
        }
        val ok = runCatching { context.startActivity(intent) }.isSuccess
        if (!ok) runCatching {
            context.packageManager.getLaunchIntentForPackage(app.packageName)?.let {
                context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
        Mio.prefs.pushRecent(app.packageName)
    }

    fun launchPackage(pkg: String) {
        val app = byPackage(pkg) ?: return
        launch(app)
    }

    fun openAppInfo(pkg: String) = safeStart(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", pkg, null))
    )

    fun uninstall(pkg: String) = safeStart(
        Intent(Intent.ACTION_DELETE, Uri.fromParts("package", pkg, null))
    )

    fun openInMarket(pkg: String) = safeStart(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")))

    private fun safeStart(i: Intent) {
        runCatching { context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}
