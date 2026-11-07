package com.mio.os.ui.apps

import android.content.Intent
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Photo
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mio.os.R
import com.mio.os.core.DeviceStats
import com.mio.os.core.LauncherHelper
import com.mio.os.core.Mio
import com.mio.os.core.MioApp
import com.mio.os.core.MioCategory
import com.mio.os.core.MioNav
import com.mio.os.core.MioPlayer
import com.mio.os.core.SystemControls
import com.mio.os.core.Weather
import com.mio.os.core.WeatherRepo
import com.mio.os.ui.components.AppIconView
import com.mio.os.ui.components.GlassCard
import com.mio.os.ui.components.MioLogo
import com.mio.os.ui.components.MioWallpaper
import com.mio.os.ui.components.ProgressRing
import com.mio.os.ui.components.SectionHeader
import com.mio.os.ui.components.ThinSlider
import com.mio.os.ui.components.bounceClick
import com.mio.os.ui.home.WeatherGlyph
import com.mio.os.ui.settings.MioAccents
import com.mio.os.ui.settings.MioScreenScaffold
import com.mio.os.ui.settings.MioWallpapers
import com.mio.os.ui.settings.SettingsGroup
import com.mio.os.ui.settings.SettingsPill
import com.mio.os.ui.settings.SliderRow
import com.mio.os.ui.settings.ToggleRow
import com.mio.os.ui.settings.WallpaperPreview
import com.mio.os.ui.theme.LocalAccent
import com.mio.os.ui.theme.LocalApps
import com.mio.os.ui.theme.LocalSettings
import com.mio.os.ui.theme.MioColors
import com.mio.os.ui.theme.MioType

import kotlinx.coroutines.delay
import java.util.Date

/* ------------------------------------------------------------------------- */
/* Themes                                                                     */
/* ------------------------------------------------------------------------- */

@Composable
fun ThemesScreen() {
    val context = LocalContext.current
    val settings = LocalSettings.current
    var tab by remember { mutableStateOf(0) }

    val pickLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            Mio.prefs.edit { it.copy(wallpaperUri = uri.toString()) }
        }
    }

    MioScreenScaffold(title = "Themes", subtitle = "Wallpapers · icons · accents") { m ->
        Column(m.padding(bottom = 28.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Featured", "Wallpaper", "Icons", "Lock").forEachIndexed { i, label ->
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(50))
                            .background(if (tab == i) LocalAccent.current.copy(alpha = 0.30f) else Color.White.copy(alpha = 0.06f))
                            .bounceClick(scaleDown = 0.95f) { tab = i }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            label,
                            style = MioType.micro.copy(
                                fontSize = 10.sp,
                                color = if (tab == i) Color.White else Color.White.copy(alpha = 0.6f),
                            ),
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))

            when (tab) {
                0, 1 -> {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        WallpaperPreview(
                            MioWallpapers[0],
                            settings.wallpaperId == "mountain" && settings.wallpaperUri == null,
                            Modifier.weight(1f), 190.dp,
                        ) {
                            Mio.prefs.edit { it.copy(wallpaperId = "mountain", wallpaperUri = null) }
                        }
                        WallpaperPreview(
                            MioWallpapers[1],
                            settings.wallpaperId == "anime" && settings.wallpaperUri == null,
                            Modifier.weight(1f), 190.dp,
                        ) {
                            Mio.prefs.edit { it.copy(wallpaperId = "anime", wallpaperUri = null) }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        WallpaperPreview(
                            MioWallpapers[2],
                            settings.wallpaperId == "aurora" && settings.wallpaperUri == null,
                            Modifier.weight(1f), 190.dp,
                        ) {
                            Mio.prefs.edit { it.copy(wallpaperId = "aurora", wallpaperUri = null) }
                        }
                        WallpaperPreview(
                            MioWallpapers[3],
                            settings.wallpaperId == "violet" && settings.wallpaperUri == null,
                            Modifier.weight(1f), 190.dp,
                        ) {
                            Mio.prefs.edit { it.copy(wallpaperId = "violet", wallpaperUri = null) }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Row {
                        SettingsPill("Apply to Android wallpaper", primary = true) {
                            val res = MioWallpapers.firstOrNull { it.id == settings.wallpaperId }?.drawable
                            if (res != null) WallpaperSetter.setFromDrawable(context, res)
                            else LauncherHelper.toast(context, "Choose an image wallpaper to apply")
                        }
                        Spacer(Modifier.width(8.dp))
                        SettingsPill("From gallery") { pickLauncher.launch(arrayOf("image/*")) }
                    }
                    if (settings.wallpaperUri != null) {
                        Spacer(Modifier.height(10.dp))
                        GlassCard(Modifier.fillMaxWidth(), corner = 16.dp, alpha = 0.06f) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Photo, null, tint = LocalAccent.current, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    "Custom wallpaper active",
                                    style = MioType.micro.copy(fontSize = 10.5.sp),
                                    modifier = Modifier.weight(1f),
                                )
                                SettingsPill("Reset") {
                                    Mio.prefs.edit { it.copy(wallpaperUri = null) }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    SectionHeader("Accent colours")
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        MioAccents.take(5).forEach { (_, color) ->
                            AccentDot(color, settings.accent == color.toArgb())
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        MioAccents.drop(5).forEach { (_, color) ->
                            AccentDot(color, settings.accent == color.toArgb())
                        }
                    }
                }
                2 -> {
                    SectionHeader("Icon style")
                    SettingsGroup {
                        SliderRow("Icon size", settings.iconScale, 0.75f..1.3f) { v ->
                            Mio.prefs.edit { it.copy(iconScale = v) }
                        }
                        ToggleRow(
                            "App labels",
                            "Show app names under the icons",
                            settings.showLabels,
                        ) { v -> Mio.prefs.edit { it.copy(showLabels = v) } }
                        ToggleRow(
                            "Glass cards",
                            "Frosted surfaces across the OS",
                            settings.glass > 0.4f,
                        ) { v -> Mio.prefs.edit { it.copy(glass = if (v) 1f else 0.15f) } }
                    }
                    Spacer(Modifier.height(12.dp))
                    GlassCard(Modifier.fillMaxWidth(), corner = 18.dp, alpha = 0.06f) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Third-party icon packs", style = MioType.title.copy(fontSize = 13.sp))
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Icon packs such as Nova's use a closed, app-private format, so a stock " +
                                    "launcher cannot read them. MIO restyles every installed icon itself " +
                                    "and keeps the real bitmap, so nothing looks out of place.",
                                style = MioType.body.copy(fontSize = 11.sp, lineHeight = 16.sp),
                            )
                        }
                    }
                }
                else -> {
                    SectionHeader("MIO lock screen")
                    Box(
                        Modifier.fillMaxWidth().height(360.dp).clip(RoundedCornerShape(26.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(26.dp))
                            .bounceClick { MioNav.push(com.mio.os.core.MioScreen.LOCK) },
                    ) {
                        MioWallpaper(settings)
                        Column(
                            Modifier.fillMaxSize().padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Spacer(Modifier.height(16.dp))
                            Icon(Icons.Rounded.Lock, null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
                            Spacer(Modifier.height(16.dp))
                            Text(
                                DateFormat.format("HH:mm", Date()).toString(),
                                style = MioType.clock.copy(fontSize = 52.sp, brush = Brush.verticalGradient(listOf(Color.White, LocalAccent.current))),
                            )
                            Text(
                                DateFormat.format("EEE, MMM d", Date()).toString(),
                                style = MioType.body.copy(fontSize = 11.sp, color = Color.White.copy(alpha = 0.7f)),
                            )
                            Spacer(Modifier.weight(1f))
                            Text("Tap to preview the lock screen", style = MioType.micro.copy(fontSize = 10.sp))
                            Spacer(Modifier.height(10.dp))
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    GlassCard(Modifier.fillMaxWidth(), corner = 18.dp, alpha = 0.06f) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Why this is a preview", style = MioType.title.copy(fontSize = 13.sp))
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Android only lets system apps replace the real lock screen. A third-party " +
                                    "launcher cannot draw over or replace it, so MIO provides a matching " +
                                    "in-app lock experience plus music controls on the system lock screen " +
                                    "through the media session.",
                                style = MioType.body.copy(fontSize = 11.sp, lineHeight = 16.sp),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun AccentDot(color: Color, selected: Boolean) {
    Box(
        Modifier.size(38.dp).clip(CircleShape)
            .background(Brush.linearGradient(listOf(color, color.copy(alpha = 0.6f))))
            .then(if (selected) Modifier.border(2.dp, Color.White, CircleShape) else Modifier)
            .bounceClick(scaleDown = 0.9f) { Mio.prefs.edit { it.copy(accent = color.toArgb()) } },
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(Icons.Rounded.Star, null, tint = Color.White, modifier = Modifier.size(15.dp))
    }
}

/* ------------------------------------------------------------------------- */
/* Weather                                                                    */
/* ------------------------------------------------------------------------- */

@Composable
fun WeatherScreen() {
    val context = LocalContext.current
    val settings = LocalSettings.current
    var weather by remember { mutableStateOf<Weather?>(null) }
    var forecast by remember { mutableStateOf<List<WeatherRepo.Day>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(settings.city) {
        weather = WeatherRepo.get(context, settings.city, force = true)
        forecast = WeatherRepo.forecast(settings.city)
        loading = false
    }

    MioScreenScaffold(
        title = "Weather",
        subtitle = settings.city,
        trailing = {
            Box(
                Modifier.size(38.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.09f))
                    .bounceClick { MioNav.push(com.mio.os.core.MioScreen.SETTINGS) },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Settings, "Settings", tint = Color.White, modifier = Modifier.size(18.dp)) }
        },
    ) { m ->
        Column(m.padding(bottom = 26.dp)) {
            GlassCard(Modifier.fillMaxWidth(), corner = 26.dp, alpha = 0.09f) {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    WeatherGlyph(weather?.icon ?: "cloud-sun", 86.dp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (weather != null) "${weather!!.tempC}°" else "—",
                        style = MioType.clock.copy(fontSize = 64.sp, brush = Brush.verticalGradient(listOf(Color.White, LocalAccent.current))),
                    )
                    Text(weather?.condition ?: "Loading…", style = MioType.title.copy(fontSize = 14.sp))
                    Spacer(Modifier.height(4.dp))
                    Text(
                        (weather?.city ?: settings.city) + " · " + (if (weather?.live == true) "live" else "offline estimate"),
                        style = MioType.micro.copy(fontSize = 10.sp),
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        WeatherStat("High", "${weather?.high ?: "—"}°")
                        WeatherStat("Low", "${weather?.low ?: "—"}°")
                        WeatherStat("Wind", "${weather?.windKph ?: "—"} km/h")
                        WeatherStat("Humidity", "${weather?.humidity ?: "—"}%")
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            SectionHeader("5-day forecast")
            if (forecast.isEmpty()) {
                GlassCard(Modifier.fillMaxWidth(), corner = 18.dp, alpha = 0.06f) {
                    Text(
                        if (loading) "Fetching forecast…" else "No network right now – the widget keeps the last known conditions.",
                        style = MioType.body.copy(fontSize = 11.5.sp),
                        modifier = Modifier.padding(16.dp),
                    )
                }
            } else {
                GlassCard(Modifier.fillMaxWidth(), corner = 18.dp, alpha = 0.06f) {
                    Column(Modifier.padding(vertical = 6.dp)) {
                        forecast.forEach { day ->
                            val desc = WeatherRepo.describe(day.code)
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(day.label, style = MioType.title.copy(fontSize = 12.5.sp), modifier = Modifier.width(52.dp))
                                WeatherGlyph(desc.second, 22.dp)
                                Spacer(Modifier.width(10.dp))
                                Text(desc.first, style = MioType.body.copy(fontSize = 11.sp), modifier = Modifier.weight(1f))
                                Text("${day.max}°", style = MioType.title.copy(fontSize = 12.5.sp))
                                Text(" / ${day.min}°", style = MioType.micro.copy(fontSize = 10.5.sp))
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            GlassCard(Modifier.fillMaxWidth(), corner = 18.dp, alpha = 0.06f) {
                Column(Modifier.padding(16.dp)) {
                    Text("Change your city", style = MioType.title.copy(fontSize = 13.sp))
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Tap a preset or type your own city in the field below.",
                        style = MioType.body.copy(fontSize = 11.sp),
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Silchar", "Guwahati", "Delhi", "Mumbai").forEach { c ->
                            SettingsPill(if (settings.city == c) "$c ✓" else c) {
                                Mio.prefs.edit { it.copy(city = c) }
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    com.mio.os.ui.setup.MioTextField(
                        value = settings.city,
                        onValueChange = { v -> Mio.prefs.edit { it.copy(city = v) } },
                        label = "City",
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun WeatherStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MioType.title.copy(fontSize = 13.sp))
        Text(label, style = MioType.micro.copy(fontSize = 9.sp))
    }
}

/* ------------------------------------------------------------------------- */
/* Gaming mode                                                                */
/* ------------------------------------------------------------------------- */

@Composable
fun GamingScreen() {
    val context = LocalContext.current
    val apps = LocalApps.current
    val accent = LocalAccent.current
    val games = remember(apps) { apps.filter { it.category == MioCategory.GAME } }
    var active by remember { mutableStateOf(false) }
    var seconds by remember { mutableLongStateOf(0L) }
    var brightness by remember { mutableStateOf(1f) }

    LaunchedEffect(active) {
        while (active) {
            delay(1000)
            seconds += 1
        }
    }

    MioScreenScaffold(title = "Game mode", subtitle = "Focus, brightness & quick launch") { m ->
        Column(m.padding(bottom = 26.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(26.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF1A0F35), Color(0xFF07030F)))),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(contentAlignment = Alignment.Center) {
                        ProgressRing(
                            progress = if (active) 1f else 0.75f,
                            modifier = Modifier.size(120.dp),
                            strokeWidth = 10.dp,
                        )
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Rounded.PlayArrow, null, tint = accent, modifier = Modifier.size(26.dp))
                            Text(
                                if (active) String.format("%02d:%02d", seconds / 60, seconds % 60) else "Ready",
                                style = MioType.title.copy(fontSize = 13.sp),
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        if (active) "Gaming session active" else "Performance profile: balanced",
                        style = MioType.micro.copy(fontSize = 10.sp),
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            GlassCard(Modifier.fillMaxWidth(), corner = 20.dp, alpha = 0.07f) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Do Not Disturb while gaming", style = MioType.title.copy(fontSize = 12.5.sp))
                            Text("Silences calls & notifications", style = MioType.micro.copy(fontSize = 9.5.sp))
                        }
                        SettingsPill(if (active) "On" else "Off", primary = active) {
                            if (!LauncherHelper.dndAccess(context)) {
                                LauncherHelper.toast(context, "Grant Do Not Disturb access first")
                                SystemControls.open(context, LauncherHelper.dndAccessIntent(context))
                            } else {
                                SystemControls.toggleDnd(context)
                                active = !active
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("Screen brightness", style = MioType.title.copy(fontSize = 12.5.sp))
                    ThinSlider(value = brightness, onValueChange = { v ->
                        brightness = v
                        val window = (context as? android.app.Activity)?.window
                        if (window != null) SystemControls.setWindowBrightness(window, (v * 255).toInt())
                        SystemControls.setSystemBrightness(context, (v * 255).toInt())
                    })
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Boost mode (CPU/GPU governor changes) requires root or a system signature. " +
                            "MIO’s gaming mode relies on DND, brightness and a distraction-free launcher instead.",
                        style = MioType.micro.copy(fontSize = 9.sp, lineHeight = 13.sp),
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            SectionHeader("Installed games")
            if (games.isEmpty()) {
                Text(
                    "No games detected. Android only exposes a game category when an app declares it.",
                    style = MioType.body.copy(fontSize = 11.sp),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(12.dp),
                )
            } else {
                LazyColumn(Modifier.fillMaxWidth().height(240.dp)) {
                    items(games) { g ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 7.dp)
                                .bounceClick(scaleDown = 0.98f) { Mio.apps.launch(g) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AppIconView(g, 40.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                g.name,
                                style = MioType.title.copy(fontSize = 13.sp),
                                modifier = Modifier.weight(1f),
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            Icon(Icons.Rounded.PlayArrow, null, tint = accent, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/* ------------------------------------------------------------------------- */
/* MIO lock screen (in-app, honest preview)                                   */
/* ------------------------------------------------------------------------- */

@Composable
fun LockScreen() {
    val settings = LocalSettings.current
    val context = LocalContext.current
    val track by MioPlayer.current.collectAsState()
    val playing by MioPlayer.playing.collectAsState()
    var now by remember { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }

    Box(Modifier.fillMaxSize()) {
        MioWallpaper(settings, extraDim = 0.1f)
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(24.dp))
            Icon(Icons.Rounded.Lock, null, tint = Color.White.copy(alpha = 0.55f), modifier = Modifier.size(16.dp))
            Spacer(Modifier.height(14.dp))
            Text(
                DateFormat.format("HH:mm", now).toString(),
                style = MioType.clock.copy(
                    fontSize = 78.sp,
                    brush = Brush.verticalGradient(listOf(Color.White, LocalAccent.current)),
                ),
            )
            Text(
                DateFormat.format("EEEE, d MMMM", now).toString(),
                style = MioType.body.copy(fontSize = 12.sp, color = Color.White.copy(alpha = 0.75f)),
            )
            Spacer(Modifier.weight(1f))

            if (track != null) {
                GlassCard(Modifier.fillMaxWidth(), corner = 24.dp, alpha = 0.12f) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        val art = remember(track?.albumId) { track?.let { Mio.media.albumArt(it) } }
                        Box(
                            Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))
                                .background(Brush.linearGradient(listOf(LocalAccent.current, MioColors.PurpleDeep))),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (art != null) androidx.compose.foundation.Image(
                                bitmap = art, contentDescription = null,
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            ) else MioLogo(Modifier.size(20.dp), Color.White)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(track!!.title, style = MioType.title.copy(fontSize = 12.5.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(track!!.artist, style = MioType.micro.copy(fontSize = 9.5.sp), maxLines = 1)
                        }
                        Icon(Icons.Rounded.SkipPrevious, null, tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(20.dp).bounceClick { MioPlayer.previous(context) })
                        Spacer(Modifier.width(10.dp))
                        Icon(
                            if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null,
                            tint = Color.White, modifier = Modifier.size(26.dp).bounceClick { MioPlayer.toggle(context) },
                        )
                        Spacer(Modifier.width(10.dp))
                        Icon(Icons.Rounded.SkipNext, null, tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(20.dp).bounceClick { MioPlayer.next(context) })
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                LockAction(Icons.Rounded.FlashlightOn, "Torch") { SystemControls.toggleTorch(context) }
                LockAction(Icons.Rounded.Photo, "Camera") {
                    runCatching {
                        context.startActivity(
                            Intent(android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }
                LockAction(Icons.Rounded.Lock, "Lock") { MioNav.home() }
            }
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.ArrowUpward, null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Swipe up to unlock", style = MioType.micro.copy(fontSize = 10.sp))
            }
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier.fillMaxWidth().height(44.dp).bounceClick { MioNav.home() },
                contentAlignment = Alignment.Center,
            ) { Spacer(Modifier.size(1.dp)) }
        }
    }
}

@Composable
private fun LockAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(52.dp).clip(CircleShape)
                .background(Color.White.copy(alpha = 0.12f))
                .border(1.dp, Color.White.copy(alpha = 0.10f), CircleShape)
                .bounceClick(scaleDown = 0.9f, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, label, tint = Color.White.copy(alpha = 0.92f), modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(label, style = MioType.micro.copy(fontSize = 9.sp))
    }
}
