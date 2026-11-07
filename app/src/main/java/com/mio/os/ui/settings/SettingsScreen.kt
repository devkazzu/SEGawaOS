package com.mio.os.ui.settings

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Photo
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.mio.os.core.DeviceStats
import com.mio.os.core.LauncherHelper
import com.mio.os.core.Mio
import com.mio.os.core.MioApp
import com.mio.os.core.MioNav
import com.mio.os.core.MioNotifications
import com.mio.os.core.MioScreen
import com.mio.os.core.MioSettings
import com.mio.os.core.SystemControls
import com.mio.os.ui.components.GlassCard
import com.mio.os.ui.components.GlassRow
import com.mio.os.ui.components.MioLogo
import com.mio.os.ui.components.MioWallpaper
import com.mio.os.ui.components.SectionHeader
import com.mio.os.ui.components.ThinSlider
import com.mio.os.ui.components.bounceClick
import com.mio.os.ui.components.rememberIsDefaultLauncher
import com.mio.os.ui.drawer.MioSwitch
import com.mio.os.ui.theme.LocalAccent
import com.mio.os.ui.theme.LocalApps
import com.mio.os.ui.theme.LocalSettings
import com.mio.os.ui.theme.MioColors
import com.mio.os.ui.theme.MioType

/* ------------------------------------------------------------------------- */
/* Shared chrome                                                              */
/* ------------------------------------------------------------------------- */

@Composable
fun MioScreenScaffold(
    title: String,
    subtitle: String? = null,
    scroll: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable (Modifier) -> Unit,
) {
    val settings = LocalSettings.current
    Box(Modifier.fillMaxSize().background(MioColors.Bg)) {
        MioWallpaper(settings, extraDim = 0.72f)
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(38.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.09f))
                        .bounceClick { MioNav.pop() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.ArrowBack, "Back", tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(19.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MioType.h2.copy(fontSize = 20.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (subtitle != null) Text(subtitle, style = MioType.micro.copy(fontSize = 10.sp))
                }
                trailing?.invoke()
            }
            val scrollState = rememberScrollState()
            val modifier = if (scroll) {
                Modifier.fillMaxSize().verticalScroll(scrollState).padding(horizontal = 16.dp)
            } else {
                Modifier.fillMaxSize().padding(horizontal = 16.dp)
            }
            content(modifier)
        }
    }
}

@Composable
fun SettingsIconBadge(icon: ImageVector, tint: Color = LocalAccent.current) {
    Box(
        Modifier.size(36.dp).clip(RoundedCornerShape(11.dp))
            .background(tint.copy(alpha = 0.16f))
            .border(1.dp, tint.copy(alpha = 0.20f), RoundedCornerShape(11.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(17.dp))
    }
}

@Composable
fun SettingsGroup(content: @Composable () -> Unit) {
    GlassCard(Modifier.fillMaxWidth(), corner = 22.dp, alpha = 0.055f) {
        Column(Modifier.padding(6.dp)) { content() }
    }
}

/* ------------------------------------------------------------------------- */
/* Main settings                                                             */
/* ------------------------------------------------------------------------- */

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val settings = LocalSettings.current
    val apps = LocalApps.current
    val isDefault by rememberIsDefaultLauncher()
    val notifCount by MioNotifications.count.collectAsState()
    val battery by DeviceStats.battery.collectAsState()

    MioScreenScaffold(title = "Settings", subtitle = "MIO OS v1.0 · ${apps.size} apps") { m ->
        Column(m.padding(bottom = 30.dp)) {

            // Default-launcher hero card
            GlassCard(
                Modifier.fillMaxWidth(),
                corner = 24.dp,
                alpha = if (isDefault) 0.10f else 0.14f,
            ) {
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MioLogo(Modifier.size(34.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (isDefault) "MIO OS is your home app" else "Set MIO OS as default",
                                style = MioType.h2.copy(fontSize = 16.sp),
                            )
                            Text(
                                if (isDefault) "Home button opens MIO OS"
                                else "Required to use MIO OS every day",
                                style = MioType.micro.copy(fontSize = 10.5.sp),
                            )
                        }
                        if (isDefault) {
                            Box(
                                Modifier.size(26.dp).clip(CircleShape)
                                    .background(MioColors.Green.copy(alpha = 0.20f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Rounded.Check, null, tint = MioColors.Green, modifier = Modifier.size(15.dp))
                            }
                        }
                    }
                    if (!isDefault) {
                        Spacer(Modifier.height(14.dp))
                        Row {
                            SettingsPill("Set as default", primary = true) { requestDefault(context) }
                            Spacer(Modifier.width(8.dp))
                            SettingsPill("Home app settings") {
                                LauncherHelper.launch(context, LauncherHelper.homeSettingsIntent(context))
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            LauncherHelper.activationHint(),
                            style = MioType.body.copy(fontSize = 10.5.sp, lineHeight = 15.sp),
                        )
                    }
                }
            }

            SectionHeader("Personalization")
            SettingsGroup {
                GlassRow(
                    "Wallpaper & themes",
                    "Mountain, anime, aurora · ${settings.wallpaperId}",
                    leading = { SettingsIconBadge(Icons.Rounded.Wallpaper, MioColors.PurpleSoft) },
                    onClick = { MioNav.push(MioScreen.THEMES) },
                    trailing = { Chevron() },
                )
                SettingsGroupDivider()
                GlassRow(
                    "Appearance",
                    "Grid, icon size, fonts, accent colour",
                    leading = { SettingsIconBadge(Icons.Rounded.Palette, MioColors.Pink) },
                    onClick = { MioNav.push(MioScreen.APPEARANCE) },
                    trailing = { Chevron() },
                )
                SettingsGroupDivider()
                GlassRow(
                    "Home screen",
                    "Layout, dock, pages, default launcher",
                    leading = { SettingsIconBadge(Icons.Rounded.Settings, MioColors.Blue) },
                    onClick = { MioNav.push(MioScreen.HOME_SETTINGS) },
                    trailing = { Chevron() },
                )
            }

            SectionHeader("Apps & tools")
            SettingsGroup {
                GlassRow(
                    "App management",
                    "${apps.size} apps · ${settings.hiddenApps.size} hidden",
                    leading = { SettingsIconBadge(Icons.Rounded.Folder, MioColors.Amber) },
                    onClick = { MioNav.push(MioScreen.HIDDEN) },
                    trailing = { Chevron() },
                )
                SettingsGroupDivider()
                GlassRow(
                    "Mio AI assistant",
                    if (settings.aiEndpoint.isBlank()) "Local mode · connect an API in the AI app" else "Custom endpoint connected",
                    leading = { SettingsIconBadge(Icons.Rounded.Cloud, MioColors.PurpleSoft) },
                    onClick = { MioNav.push(MioScreen.AI) },
                    trailing = { Chevron() },
                )
                SettingsGroupDivider()
                GlassRow(
                    "Music player",
                    "Local library, equalizer, lock-screen controls",
                    leading = { SettingsIconBadge(Icons.Rounded.MusicNote, MioColors.Blue) },
                    onClick = { MioNav.push(MioScreen.MUSIC) },
                    trailing = { Chevron() },
                )
                SettingsGroupDivider()
                GlassRow(
                    "Gallery & files",
                    "Photos, videos, downloads and storage",
                    leading = { SettingsIconBadge(Icons.Rounded.Photo, MioColors.Green) },
                    onClick = { MioNav.push(MioScreen.FILES) },
                    trailing = { Chevron() },
                )
            }

            SectionHeader("System")
            SettingsGroup {
                GlassRow(
                    "Privacy & permissions",
                    "Notification badges, DND access, media",
                    leading = { SettingsIconBadge(Icons.Rounded.Shield, MioColors.Green) },
                    onClick = { MioNav.push(MioScreen.PRIVACY) },
                    trailing = { Chevron() },
                )
                SettingsGroupDivider()
                GlassRow(
                    "Battery & performance",
                    "Battery ${battery.level}% · ${if (battery.charging) "charging" else "on battery"}",
                    leading = { SettingsIconBadge(Icons.Rounded.BatteryChargingFull, MioColors.Amber) },
                    onClick = {
                        LauncherHelper.launch(context, SystemControls.batterySettings(context))
                    },
                    trailing = { Chevron() },
                )
                SettingsGroupDivider()
                GlassRow(
                    "Wi-Fi & network",
                    "Open the Android network panel",
                    leading = { SettingsIconBadge(Icons.Rounded.Wifi, MioColors.Blue) },
                    onClick = { SystemControls.open(context, SystemControls.internetPanel(context)) },
                    trailing = { Chevron() },
                )
                SettingsGroupDivider()
                GlassRow(
                    "Storage",
                    "${DeviceStats.storage().usedGb} GB of ${DeviceStats.storage().totalGb} GB used",
                    leading = { SettingsIconBadge(Icons.Rounded.Storage, MioColors.PurpleSoft) },
                    onClick = { SystemControls.open(context, Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) },
                    trailing = { Chevron() },
                )
            }

            SectionHeader("About")
            SettingsGroup {
                GlassRow(
                    "About MIO OS",
                    "v1.0.0 (Rebirth) · Android ${Build.VERSION.RELEASE}",
                    leading = { SettingsIconBadge(Icons.Rounded.Info, MioColors.PurpleSoft) },
                    onClick = { MioNav.push(MioScreen.ABOUT) },
                    trailing = { Chevron() },
                )
                SettingsGroupDivider()
                GlassRow(
                    "Notification indicators",
                    if (notifCount > 0) "$notifCount active notifications" else "No notifications",
                    leading = { SettingsIconBadge(Icons.Rounded.Notifications, MioColors.Pink) },
                    onClick = { MioNav.push(MioScreen.PRIVACY) },
                    trailing = { Chevron() },
                )
            }

            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Text(
                    "MIO OS  ·  built on Android AOSP  ·  concept by RJNX",
                    style = MioType.micro.copy(fontSize = 9.5.sp),
                )
            }
        }
    }
}

@Composable
fun SettingsGroupDivider() {
    Box(
        Modifier.fillMaxWidth().padding(start = 66.dp, end = 16.dp).height(1.dp)
            .background(Color.White.copy(alpha = 0.055f))
    )
}

@Composable
fun Chevron() {
    Text("›", style = MioType.title.copy(fontSize = 18.sp, color = Color.White.copy(alpha = 0.35f)))
}

@Composable
fun SettingsPill(text: String, primary: Boolean = false, onClick: () -> Unit) {
    val accent = LocalAccent.current
    Box(
        Modifier.clip(RoundedCornerShape(50))
            .background(
                if (primary) Brush.horizontalGradient(listOf(accent, MioColors.PurpleSoft))
                else Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.08f)))
            )
            .bounceClick(scaleDown = 0.96f, onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 10.dp),
    ) {
        Text(text, style = MioType.micro.copy(color = Color.White, fontSize = 10.5.sp))
    }
}

fun requestDefault(context: android.content.Context) {
    val role = LauncherHelper.roleRequestIntent(context)
    if (role != null) {
        LauncherHelper.launch(context, role)
    } else {
        val home = LauncherHelper.homeSettingsIntent(context) ?: LauncherHelper.chooserIntent()
        LauncherHelper.launch(context, home)
    }
}

/* ------------------------------------------------------------------------- */
/* Home screen settings                                                      */
/* ------------------------------------------------------------------------- */

@Composable
fun HomeScreenSettings() {
    val context = LocalContext.current
    val settings = LocalSettings.current
    val isDefault by rememberIsDefaultLauncher()

    MioScreenScaffold(title = "Home screen", subtitle = "Layout & launcher status") { m ->
        Column(m.padding(bottom = 30.dp)) {
            GlassCard(Modifier.fillMaxWidth(), corner = 22.dp, alpha = 0.10f) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        if (isDefault) "Default home app: MIO OS" else "Not the default home app",
                        style = MioType.h2.copy(fontSize = 15.sp),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (isDefault)
                            "Pressing Home from any app returns to MIO OS. This survives reboots."
                        else
                            "Android requires one extra confirmation before MIO OS can replace your current home app.",
                        style = MioType.body.copy(fontSize = 11.5.sp, lineHeight = 16.sp),
                    )
                    Spacer(Modifier.height(12.dp))
                    Row {
                        SettingsPill(if (isDefault) "Home app settings" else "Set MIO OS as default", primary = !isDefault) {
                            requestDefault(context)
                        }
                        if (!isDefault) {
                            Spacer(Modifier.width(8.dp))
                            SettingsPill("Instruction") { MioNav.push(MioScreen.WELCOME) }
                        }
                    }
                }
            }

            SectionHeader("Grid")
            SettingsGroup {
                StepperRow(
                    title = "Columns",
                    value = "${settings.gridColumns}",
                    onMinus = { Mio.prefs.edit { it.copy(gridColumns = (it.gridColumns - 1).coerceIn(3, 6)) } },
                    onPlus = { Mio.prefs.edit { it.copy(gridColumns = (it.gridColumns + 1).coerceIn(3, 6)) } },
                )
                SettingsGroupDivider()
                SliderRow(
                    title = "Icon size",
                    value = settings.iconScale,
                    range = 0.75f..1.3f,
                    onChange = { v -> Mio.prefs.edit { it.copy(iconScale = v) } },
                )
                SettingsGroupDivider()
                ToggleRow(
                    title = "App labels",
                    subtitle = "Show names under icons",
                    checked = settings.showLabels,
                    onChange = { v -> Mio.prefs.edit { it.copy(showLabels = v) } },
                )
                SettingsGroupDivider()
                ToggleRow(
                    title = "Dock",
                    subtitle = "Pinned shortcuts at the bottom",
                    checked = settings.showDock,
                    onChange = { v -> Mio.prefs.edit { it.copy(showDock = v) } },
                )
                SettingsGroupDivider()
                ToggleRow(
                    title = "Weather widget",
                    subtitle = "Live conditions for ${settings.city}",
                    checked = settings.showWeather,
                    onChange = { v -> Mio.prefs.edit { it.copy(showWeather = v) } },
                )
                SettingsGroupDivider()
                ToggleRow(
                    title = "24-hour clock",
                    subtitle = "Digital clock format",
                    checked = settings.clock24h,
                    onChange = { v -> Mio.prefs.edit { it.copy(clock24h = v) } },
                )
            }

            SectionHeader("Apps")
            SettingsGroup {
                GlassRow(
                    "Hidden apps",
                    "${settings.hiddenApps.size} hidden from home & drawer",
                    leading = { SettingsIconBadge(Icons.Rounded.Search, MioColors.PurpleSoft) },
                    onClick = { MioNav.push(MioScreen.HIDDEN) },
                    trailing = { Chevron() },
                )
                SettingsGroupDivider()
                GlassRow(
                    "Drawer sorting",
                    settings.sortMode.lowercase().replaceFirstChar { it.uppercase() },
                    leading = { SettingsIconBadge(Icons.Rounded.Folder, MioColors.Amber) },
                    onClick = {
                        val next = when (settings.sortMode) {
                            "ALPHABETICAL" -> "RECENT"
                            "RECENT" -> "CATEGORY"
                            else -> "ALPHABETICAL"
                        }
                        Mio.prefs.edit { it.copy(sortMode = next) }
                    },
                    trailing = { Chevron() },
                )
            }
            Spacer(Modifier.height(22.dp))
        }
    }
}

@Composable
fun StepperRow(title: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MioType.title, modifier = Modifier.weight(1f))
        StepButton("−", onMinus)
        Text(
            value,
            style = MioType.title.copy(fontSize = 13.sp),
            modifier = Modifier.width(38.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        StepButton("+", onPlus)
    }
}

@Composable
private fun StepButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(Color.White.copy(alpha = 0.08f))
            .bounceClick(scaleDown = 0.9f, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MioType.title.copy(fontSize = 15.sp))
    }
}

@Composable
fun SliderRow(title: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MioType.title, modifier = Modifier.weight(1f))
            Text(
                String.format("%.2f×", value),
                style = MioType.micro.copy(fontSize = 10.sp, color = Color.White.copy(alpha = 0.7f)),
            )
        }
        ThinSlider(
            value = (value - range.start) / (range.endInclusive - range.start),
            onValueChange = { f -> onChange(range.start + f * (range.endInclusive - range.start)) },
        )
    }
}

@Composable
fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MioType.title.copy(fontSize = 13.5.sp))
            Text(subtitle, style = MioType.micro.copy(fontSize = 10.sp))
        }
        MioSwitch(checked = checked, onCheckedChange = onChange)
    }
}

/* ------------------------------------------------------------------------- */
/* Privacy & permissions                                                     */
/* ------------------------------------------------------------------------- */

@Composable
fun PrivacyScreen() {
    val context = LocalContext.current
    var refresh by remember { mutableStateOf(0) }
    val notifAccess = remember(refresh) {
        val nm = context.getSystemService(NotificationManager::class.java)
        @Suppress("DEPRECATION")
        nm?.isNotificationListenerAccessGranted(
            android.content.ComponentName(context, com.mio.os.service.MioNotificationListener::class.java)
        ) == true
    }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { refresh++ }

    fun has(perm: String) = ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED

    MioScreenScaffold(title = "Privacy & permissions", subtitle = "Everything runs on your device") { m ->
        Column(m.padding(bottom = 30.dp)) {
            GlassCard(Modifier.fillMaxWidth(), corner = 20.dp, alpha = 0.07f) {
                Column(Modifier.padding(16.dp)) {
                    Text("Local-first by design", style = MioType.title.copy(fontSize = 13.5.sp))
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "MIO OS has no analytics, no account and no cloud sync. Preferences, notes and " +
                            "playlists stay in this app's private storage. Only the weather widget and an " +
                            "AI endpoint you configure yourself use the network.",
                        style = MioType.body.copy(fontSize = 11.5.sp, lineHeight = 17.sp),
                    )
                }
            }

            SectionHeader("Permissions")
            SettingsGroup {
                PermissionItem(
                    title = "Notifications",
                    subtitle = if (Build.VERSION.SDK_INT >= 33)
                        if (has(Manifest.permission.POST_NOTIFICATIONS)) "Granted" else "Needed for media controls"
                    else "Granted automatically",
                    granted = Build.VERSION.SDK_INT < 33 || has(Manifest.permission.POST_NOTIFICATIONS),
                ) {
                    if (Build.VERSION.SDK_INT >= 33) {
                        permLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                    }
                }
                SettingsGroupDivider()
                PermissionItem(
                    title = "Photos & media",
                    subtitle = if (Build.VERSION.SDK_INT >= 33)
                        if (has(Manifest.permission.READ_MEDIA_IMAGES)) "Granted" else "Wallpapers, gallery, music"
                    else if (has(Manifest.permission.READ_EXTERNAL_STORAGE)) "Granted" else "Wallpapers, gallery, music",
                    granted = if (Build.VERSION.SDK_INT >= 33)
                        has(Manifest.permission.READ_MEDIA_IMAGES)
                    else has(Manifest.permission.READ_EXTERNAL_STORAGE),
                ) {
                    if (Build.VERSION.SDK_INT >= 33) {
                        permLauncher.launch(
                            arrayOf(
                                Manifest.permission.READ_MEDIA_IMAGES,
                                Manifest.permission.READ_MEDIA_AUDIO,
                                Manifest.permission.READ_MEDIA_VIDEO,
                            )
                        )
                    } else {
                        permLauncher.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE))
                    }
                }
                SettingsGroupDivider()
                PermissionItem(
                    title = "Notification access",
                    subtitle = if (notifAccess) "Granted" else "Real app badges on the home screen",
                    granted = notifAccess,
                ) {
                    LauncherHelper.launch(context, LauncherHelper.notificationAccessIntent(context))
                    refresh++
                }
                SettingsGroupDivider()
                PermissionItem(
                    title = "Do Not Disturb access",
                    subtitle = if (LauncherHelper.dndAccess(context)) "Granted" else "Control centre DND toggle",
                    granted = LauncherHelper.dndAccess(context),
                ) {
                    LauncherHelper.launch(context, LauncherHelper.dndAccessIntent(context))
                    refresh++
                }
                SettingsGroupDivider()
                PermissionItem(
                    title = "Modify system settings",
                    subtitle = if (LauncherHelper.canWriteSettings(context)) "Granted" else "Brightness slider & rotation",
                    granted = LauncherHelper.canWriteSettings(context),
                ) {
                    LauncherHelper.launch(context, LauncherHelper.writeSettingsIntent(context))
                    refresh++
                }
                SettingsGroupDivider()
                PermissionItem(
                    title = "Device administrator",
                    subtitle = if (SystemControls.isDeviceAdmin(context)) "Granted – lock device enabled" else "Optional – lock the phone from MIO OS",
                    granted = SystemControls.isDeviceAdmin(context),
                ) {
                    if (!SystemControls.isDeviceAdmin(context)) {
                        LauncherHelper.launch(
                            context,
                            Intent(android.app.admin.DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                                .putExtra(
                                    android.app.admin.DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                                    android.content.ComponentName(context, com.mio.os.service.MioDeviceAdmin::class.java)
                                )
                                .putExtra(
                                    android.app.admin.DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                                    "MIO OS uses this only to lock your screen from the control centre."
                                )
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                        refresh++
                    }
                }
            }

            SectionHeader("Battery")
            SettingsGroup {
                GlassRow(
                    "Ignore battery optimisations",
                    "Keeps the Home app always ready",
                    leading = { SettingsIconBadge(Icons.Rounded.BatteryChargingFull, MioColors.Amber) },
                    onClick = {
                        LauncherHelper.launch(context, LauncherHelper.batteryOptimizationIntent(context))
                    },
                    trailing = { Chevron() },
                )
                SettingsGroupDivider()
                GlassRow(
                    "Usage access (optional)",
                    "Enables system recent-apps ranking",
                    leading = { SettingsIconBadge(Icons.Rounded.Storage, MioColors.Blue) },
                    onClick = {
                        LauncherHelper.launch(
                            context,
                            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    },
                    trailing = { Chevron() },
                )
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun PermissionItem(title: String, subtitle: String, granted: Boolean, onAction: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(28.dp).clip(CircleShape)
                .background(
                    if (granted) MioColors.Green.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.07f)
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (granted) Icons.Rounded.Check else Icons.Rounded.Shield,
                null,
                tint = if (granted) MioColors.Green else Color.White.copy(alpha = 0.45f),
                modifier = Modifier.size(15.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MioType.title.copy(fontSize = 13.sp))
            Text(subtitle, style = MioType.micro.copy(fontSize = 10.sp))
        }
        if (!granted) SettingsPill("Grant", primary = true, onClick = onAction)
    }
}
