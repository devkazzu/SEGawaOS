package com.mio.os.core

import android.app.Activity
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast

/**
 * Real Android home-screen plumbing – no simulation.
 *
 * - [isDefault] asks the package manager which activity currently owns the HOME intent.
 * - [requestDefault] uses the Android 10+ RoleManager flow (the system's own
 *   "use as Home app" dialog) and falls back to the Home-app chooser / settings
 *   screen on older releases.
 */
object LauncherHelper {

    fun homeIntent(): Intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)

    fun isDefault(context: Context): Boolean {
        val pm = context.packageManager
        @Suppress("DEPRECATION")
        val resolved = pm.resolveActivity(homeIntent(), android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
        val pkg = resolved?.activityInfo?.packageName ?: return false
        return pkg == context.packageName
    }

    fun roleHeld(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return isDefault(context)
        val rm = context.getSystemService(RoleManager::class.java) ?: return isDefault(context)
        return rm.isRoleAvailable(RoleManager.ROLE_HOME) && rm.isRoleHeld(RoleManager.ROLE_HOME)
    }

    /**
     * The system role-request dialog (Android 10+). Returns null when the device
     * has no role manager, in which case callers use [homeSettingsIntent] or
     * [chooserIntent].
     */
    fun roleRequestIntent(context: Context): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val rm = context.getSystemService(RoleManager::class.java) ?: return null
        if (!rm.isRoleAvailable(RoleManager.ROLE_HOME)) return null
        if (rm.isRoleHeld(RoleManager.ROLE_HOME)) return null
        return rm.createRequestRoleIntent(RoleManager.ROLE_HOME)
    }

    /** Opens Settings ▸ Apps ▸ Default apps ▸ Home app. */
    fun homeSettingsIntent(context: Context): Intent? {
        val candidates = listOf(
            Intent(Settings.ACTION_HOME_SETTINGS),
            Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
            Intent(Settings.ACTION_SETTINGS),
        )
        for (i in candidates) {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (context.packageManager.resolveActivity(i, 0) != null) return i
        }
        return null
    }

    /** The "which home app?" chooser Android shows when two launchers exist. */
    fun chooserIntent(): Intent =
        homeIntent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

    /** Home app "Always / Just once" hint copied from the system dialog wording. */
    fun activationHint(): String =
        "Android will ask you to choose a Home app. Pick MIO OS, then tap “Always” " +
            "(or open Settings ▸ Default apps ▸ Home app and select MIO OS)."

    fun launch(context: Context, intent: Intent?) {
        if (intent == null) {
            toast(context, "This device has no home-app settings screen")
            return
        }
        val ok = runCatching { context.startActivity(intent) }.isSuccess
        if (!ok) toast(context, "Could not open the Home app settings")
    }

    fun toast(context: Context, msg: String) {
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }

    fun selfComponent(context: Context): ComponentName? {
        val i = Intent(context, com.mio.os.MainActivity::class.java)
        return i.component
    }

    fun batteryOptimizationIntent(context: Context): Intent? {
        val i = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return if (context.packageManager.resolveActivity(i, 0) != null) i else null
    }

    fun notificationAccessIntent(context: Context): Intent =
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun writeSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + context.packageName))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun canWriteSettings(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.System.canWrite(context)

    fun dndAccess(context: Context): Boolean {
        val nm = context.getSystemService(android.app.NotificationManager::class.java)
        return nm?.isNotificationPolicyAccessGranted == true
    }

    fun dndAccessIntent(context: Context): Intent =
        Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun requiresActivity(context: Context): Activity? = context as? Activity
}
