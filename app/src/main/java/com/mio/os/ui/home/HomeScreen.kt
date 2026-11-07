package com.mio.os.ui.home

import android.text.format.DateFormat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.mio.os.ui.components.AppShortcut
import com.mio.os.core.DeviceStats
import com.mio.os.core.Mio
import com.mio.os.core.MioApp
import com.mio.os.core.MioNav
import com.mio.os.core.MioNotifications
import com.mio.os.core.MioPlayer
import com.mio.os.core.MioScreen
import com.mio.os.core.MioSettings
import com.mio.os.core.Track
import com.mio.os.core.Weather
import com.mio.os.core.WeatherRepo
import com.mio.os.ui.components.AmbientOrb
import com.mio.os.ui.components.AppIconView
import com.mio.os.ui.components.GlassCard
import com.mio.os.ui.components.MioLogo
import com.mio.os.ui.components.MioWallpaper
import com.mio.os.ui.components.PageDots
import com.mio.os.ui.components.SheetHandle
import com.mio.os.ui.components.bounceClick
import com.mio.os.ui.theme.LocalAccent
import com.mio.os.ui.theme.LocalApps
import com.mio.os.ui.theme.LocalSettings
import com.mio.os.ui.theme.MioColors
import com.mio.os.ui.theme.MioType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen() {
    val settings = LocalSettings.current
    val apps = LocalApps.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val battery by DeviceStats.battery.collectAsState()
    val notifications by MioNotifications.count.collectAsState()
    val notifApps by MioNotifications.apps.collectAsState()
    val track by MioPlayer.current.collectAsState()
    val playing by MioPlayer.playing.collectAsState()

    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }

    var weather by remember { mutableStateOf<Weather?>(null) }
    LaunchedEffect(settings.city) { weather = WeatherRepo.get(context, settings.city) }

    val hidden = settings.hiddenApps
    val visible = remember(apps, hidden) { apps.filter { it.packageName !in hidden } }
    val columns = settings.gridColumns
    val rows = 4
    val perPage = (columns * rows).coerceAtLeast(4)
    val pages = remember(visible, perPage) {
        visible.chunked(perPage).ifEmpty { listOf(emptyList()) }
    }
    val pagerState = rememberPagerState(pageCount = { pages.size.coerceAtLeast(1) })

    var showSheet by remember { mutableStateOf(false) }
    var selectedApp by remember { mutableStateOf<MioApp?>(null) }

    var dragAccum by remember { mutableFloatStateOf(0f) }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { dragAccum = 0f },
                    onDragEnd = {
                        if (dragAccum < -90f) MioNav.push(MioScreen.DRAWER)
                        else if (dragAccum > 90f) MioNav.push(MioScreen.CONTROL)
                        dragAccum = 0f
                    },
                    onVerticalDrag = { change, amount ->
                        dragAccum += amount
                        change.consume()
                    },
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(onLongPress = { showSheet = true })
            }
    ) {
        MioWallpaper(settings)

        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp)
        ) {
            TopStatusRow(
                settings = settings,
                notifications = notifications,
                battery = battery.level,
                charging = battery.charging,
            )
            Spacer(Modifier.height(6.dp))
            ClockBlock(settings = settings, weather = weather, time = now)
            Spacer(Modifier.height(14.dp))
            if (track != null || playing) {
                MusicWidget(track = track, playing = playing)
                Spacer(Modifier.height(12.dp))
            }
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                pageSpacing = 12.dp,
            ) { page ->
                AppGrid(
                    apps = pages.getOrElse(page) { emptyList() },
                    columns = columns,
                    rows = rows,
                    iconSize = (46 * settings.iconScale).dp,
                    showLabels = settings.showLabels,
                    notifApps = notifApps,
                    onAppClick = { Mio.apps.launch(it) },
                    onAppLongClick = { selectedApp = it },
                )
            }
            Spacer(Modifier.height(8.dp))
            PageDots(
                count = pages.size,
                index = pagerState.currentPage,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Spacer(Modifier.height(12.dp))
            SearchPill()
            Spacer(Modifier.height(12.dp))
            if (settings.showDock) {
                Dock(
                    apps = dockApps(visible, settings),
                    onAppClick = { Mio.apps.launch(it) },
                    onAppLongClick = { selectedApp = it },
                )
                Spacer(Modifier.height(14.dp))
            }
        }
    }

    if (showSheet) {
        HomeSheet(onDismiss = { showSheet = false })
    }
    selectedApp?.let { app ->
        AppQuickSheet(
            app = app,
            onDismiss = { selectedApp = null },
            onHide = {
                Mio.prefs.setHidden(app.packageName, true)
                selectedApp = null
            },
        )
    }
}

/* ------------------------------------------------------------------------- */

private fun dockApps(visible: List<MioApp>, settings: MioSettings): List<MioApp> {
    val byPkg = visible.associateBy { it.packageName }
    val chosen = settings.dockApps.mapNotNull { byPkg[it] }
    if (chosen.size >= 4) return chosen.take(5)
    val keywords = listOf("dialer", "phone", "mms", "messaging", "browser", "chrome", "camera", "settings")
    val auto = keywords.flatMap { k ->
        visible.filter { it.packageName.contains(k, true) }.take(1)
    }.distinctBy { it.packageName }
    return (chosen + auto).distinctBy { it.packageName }.ifEmpty { visible.take(4) }.take(4)
}

@Composable
private fun TopStatusRow(settings: MioSettings, notifications: Int, battery: Int, charging: Boolean) {
    val hour = remember { java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) }
    val greeting = when (hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..20 -> "Good evening"
        else -> "Good night"
    }
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                "$greeting, ${settings.userName}",
                style = MioType.body.copy(color = Color.White.copy(alpha = 0.82f), fontSize = 12.sp),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("MIO", style = MioType.label.copy(color = LocalAccent.current, letterSpacing = 2.2.sp, fontSize = 10.sp))
                Spacer(Modifier.width(4.dp))
                Text("OS", style = MioType.label.copy(color = Color.White.copy(alpha = 0.75f), letterSpacing = 2.2.sp, fontSize = 10.sp))
            }
        }
        Box {
            IconCircleMini(Icons.Rounded.Notifications)
            if (notifications > 0) {
                Box(
                    Modifier.align(Alignment.TopEnd).size(14.dp).clip(CircleShape)
                        .background(LocalAccent.current),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (notifications > 9) "9+" else "$notifications",
                        style = MioType.micro.copy(fontSize = 8.sp, color = Color.White),
                    )
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier.clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.10f))
                .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(50))
                .bounceClick { MioNav.push(MioScreen.SETTINGS) }
                .padding(horizontal = 10.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                (if (charging) "⚡ " else "") + "$battery%",
                style = MioType.micro.copy(fontSize = 10.sp, color = Color.White.copy(alpha = 0.9f)),
            )
        }
        Spacer(Modifier.width(8.dp))
        IconCircleMini(Icons.Rounded.Settings, onClick = { MioNav.push(MioScreen.SETTINGS) })
    }
}

@Composable
private fun IconCircleMini(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: (() -> Unit)? = null,
) {
    val modifier = Modifier.size(34.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.10f))
    val clickable = if (onClick != null) modifier.bounceClick(onClick = onClick) else modifier
    Box(clickable, contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun ClockBlock(settings: MioSettings, weather: Weather?, time: Long) {
    val accent = LocalAccent.current
    val date = Date(time)
    val timeText = remember(time, settings.clock24h) {
        DateFormat.format(if (settings.clock24h) "HH:mm" else "h:mm", date).toString()
    }
    val dateText = remember(time) {
        DateFormat.format("EEE, MMM d", date).toString()
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.bounceClick(scaleDown = 0.97f) { MioNav.push(MioScreen.CLOCK) }) {
            Text(
                timeText,
                style = MioType.clock.copy(
                    brush = Brush.verticalGradient(
                        listOf(Color.White, Color.White.copy(alpha = 0.75f), accent.copy(alpha = 0.95f))
                    )
                ),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    dateText,
                    style = MioType.body.copy(color = Color.White.copy(alpha = 0.72f), fontSize = 12.5.sp),
                )
                Spacer(Modifier.width(8.dp))
                Box(Modifier.size(4.dp).clip(CircleShape).background(accent))
                Spacer(Modifier.width(8.dp))
                Text(
                    settings.city,
                    style = MioType.body.copy(color = Color.White.copy(alpha = 0.72f), fontSize = 12.5.sp),
                )
            }
        }
        Spacer(Modifier.weight(1f))
        if (settings.showWeather) {
            WeatherPill(weather) { MioNav.push(MioScreen.WEATHER) }
        }
    }
}

@Composable
private fun WeatherPill(weather: Weather?, onClick: () -> Unit) {
    GlassCard(
        Modifier.bounceClick { onClick() },
        corner = 22.dp,
        alpha = 0.13f,
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WeatherGlyph(weather?.icon ?: "cloud-sun", size = 26.dp)
            Spacer(Modifier.width(9.dp))
            Column {
                Text(
                    if (weather != null) "${weather.tempC}°" else "—",
                    style = MioType.h2.copy(fontSize = 18.sp),
                )
                Text(
                    (weather?.condition ?: "Loading").take(12),
                    style = MioType.micro.copy(fontSize = 9.5.sp),
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
fun WeatherGlyph(icon: String, size: Dp = 26.dp) {
    val accent = LocalAccent.current
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val sunCenter = Offset(w * 0.36f, h * 0.34f)
        val sunR = w * 0.20f
        fun drawSun() {
            drawCircle(
                Brush.radialGradient(listOf(Color(0xFFFFE3A3), Color(0xFFFFB86B)), sunCenter, sunR * 1.4f),
                radius = sunR, center = sunCenter,
            )
            repeat(8) { i ->
                val a = Math.toRadians(i * 45.0)
                val start = Offset(
                    sunCenter.x + (sunR * 1.35f * Math.cos(a)).toFloat(),
                    sunCenter.y + (sunR * 1.35f * Math.sin(a)).toFloat(),
                )
                val end = Offset(
                    sunCenter.x + (sunR * 1.75f * Math.cos(a)).toFloat(),
                    sunCenter.y + (sunR * 1.75f * Math.sin(a)).toFloat(),
                )
                drawLine(Color(0xFFFFC46B).copy(alpha = 0.9f), start, end, strokeWidth = w * 0.045f, cap = StrokeCap.Round)
            }
        }
        fun drawCloud(alpha: Float = 1f) {
            val c = Color(0xFFD9DDF5).copy(alpha = alpha)
            drawCircle(c, radius = w * 0.17f, center = Offset(w * 0.42f, h * 0.60f))
            drawCircle(c, radius = w * 0.21f, center = Offset(w * 0.60f, h * 0.56f))
            drawCircle(c, radius = w * 0.15f, center = Offset(w * 0.76f, h * 0.62f))
            drawRoundRect(
                c,
                topLeft = Offset(w * 0.34f, h * 0.60f),
                size = Size(w * 0.50f, h * 0.17f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(h * 0.085f),
            )
        }
        when (icon) {
            "sun" -> drawSun()
            "cloud-sun" -> {
                drawSun()
                drawCloud()
            }
            "cloud" -> drawCloud()
            "rain" -> {
                drawCloud(Color(0xFFB9C2E8))
                repeat(3) { i ->
                    val x = w * (0.42f + i * 0.14f)
                    drawLine(
                        accent.copy(alpha = 0.85f), Offset(x, h * 0.80f), Offset(x - w * 0.04f, h * 0.94f),
                        strokeWidth = w * 0.05f, cap = StrokeCap.Round,
                    )
                }
            }
            "snow" -> {
                drawCloud()
                repeat(3) { i ->
                    drawCircle(Color.White.copy(alpha = 0.9f), radius = w * 0.035f, center = Offset(w * (0.42f + i * 0.15f), h * 0.86f))
                }
            }
            "storm" -> {
                drawCloud(Color(0xFFB9C2E8))
                val bolt = Path().apply {
                    moveTo(w * 0.55f, h * 0.74f)
                    lineTo(w * 0.44f, h * 0.92f)
                    lineTo(w * 0.54f, h * 0.92f)
                    lineTo(w * 0.48f, h * 1.02f)
                    lineTo(w * 0.64f, h * 0.86f)
                    lineTo(w * 0.54f, h * 0.86f)
                    close()
                }
                drawPath(bolt, Color(0xFFFFD166))
            }
            "fog" -> {
                drawCloud()
                repeat(2) { i ->
                    drawLine(
                        Color.White.copy(alpha = 0.55f),
                        Offset(w * 0.30f, h * (0.82f + i * 0.10f)),
                        Offset(w * 0.74f, h * (0.82f + i * 0.10f)),
                        strokeWidth = w * 0.05f, cap = StrokeCap.Round,
                    )
                }
            }
            else -> drawCloud()
        }
    }
}

@Composable
private fun MusicWidget(track: Track?, playing: Boolean) {
    val context = LocalContext.current
    val accent = LocalAccent.current
    GlassCard(Modifier.fillMaxWidth(), corner = 24.dp, alpha = 0.11f) {
        Row(
            Modifier.padding(10.dp).bounceClick(scaleDown = 0.98f) { MioNav.push(MioScreen.MUSIC) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val art = remember(track?.albumId) {
                track?.let { Mio.media.albumArt(it) }
            }
            Box(
                Modifier.size(46.dp).clip(RoundedCornerShape(14.dp))
                    .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.7f), MioColors.PurpleDeep))),
                contentAlignment = Alignment.Center,
            ) {
                if (art != null) {
                    androidx.compose.foundation.Image(
                        bitmap = art, contentDescription = null,
                        contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    MioLogo(Modifier.size(22.dp), color = Color.White.copy(alpha = 0.9f))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    track?.title ?: "MIO Music",
                    style = MioType.title.copy(fontSize = 13.5.sp),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    track?.artist ?: "Tap to open the player",
                    style = MioType.micro.copy(fontSize = 10.sp),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.SkipPrevious, contentDescription = "Previous",
                    tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(22.dp)
                        .bounceClick { MioPlayer.previous(context) },
                )
                Spacer(Modifier.width(10.dp))
                Box(
                    Modifier.size(34.dp).clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.92f))
                        .bounceClick {
                            if (track == null) MioNav.push(MioScreen.MUSIC)
                            else MioPlayer.toggle(context)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (playing) "Pause" else "Play",
                        tint = Color(0xFF120C22), modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Icon(
                    Icons.Rounded.SkipNext, contentDescription = "Next",
                    tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(22.dp)
                        .bounceClick { MioPlayer.next(context) },
                )
            }
        }
    }
}

@Composable
private fun AppGrid(
    apps: List<MioApp>,
    columns: Int,
    rows: Int,
    iconSize: Dp,
    showLabels: Boolean,
    notifApps: Set<String>,
    onAppClick: (MioApp) -> Unit,
    onAppLongClick: (MioApp) -> Unit,
) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
        val chunks = apps.chunked(columns)
        repeat(rows) { r ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                val rowApps = chunks.getOrElse(r) { emptyList() }
                repeat(columns) { c ->
                    val app = rowApps.getOrNull(c)
                    if (app == null) {
                        Spacer(Modifier.width(iconSize + 22.dp).height(1.dp))
                    } else {
                        AppShortcut(
                            app = app,
                            iconSize = iconSize,
                            showLabel = showLabels,
                            showBadge = app.packageName in notifApps,
                            onClick = { onAppClick(app) },
                            onLongClick = { onAppLongClick(app) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchPill() {
    GlassCard(Modifier.fillMaxWidth(), corner = 50.dp, alpha = 0.10f) {
        Row(
            Modifier.fillMaxWidth().bounceClick { MioNav.push(MioScreen.DRAWER) }
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.Search, contentDescription = null,
                tint = Color.White.copy(alpha = 0.65f), modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "Search apps, settings, AI…",
                style = MioType.body.copy(fontSize = 12.5.sp, color = Color.White.copy(alpha = 0.55f)),
            )
            Spacer(Modifier.weight(1f))
            Icon(
                Icons.Rounded.MoreVert, contentDescription = null,
                tint = Color.White.copy(alpha = 0.45f), modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun Dock(apps: List<MioApp>, onAppClick: (MioApp) -> Unit, onAppLongClick: (MioApp) -> Unit) {
    GlassCard(Modifier.fillMaxWidth(), corner = 30.dp, alpha = 0.13f) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            apps.take(4).forEach { app ->
                Box(Modifier.bounceClick(onClick = { onAppClick(app) }, onLongClick = { onAppLongClick(app) })) {
                    AppIconView(app, 48.dp)
                }
            }
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(15.dp))
                    .background(Color.White.copy(alpha = 0.10f))
                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(15.dp))
                    .bounceClick { MioNav.push(MioScreen.DRAWER) },
                contentAlignment = Alignment.Center,
            ) {
                MioLogo(Modifier.size(22.dp))
            }
        }
    }
}

/* ------------------------------------------------------------------------- */
/* Sheets                                                                     */
/* ------------------------------------------------------------------------- */

@Composable
private fun HomeSheet(onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val settings = LocalSettings.current
    val context = LocalContext.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF0D0918),
        scrimColor = Color.Black.copy(alpha = 0.55f),
        dragHandle = { SheetHandle() },
    ) {
        Column(Modifier.padding(horizontal = 18.dp).navigationBarsPadding()) {
            Text("Home screen", style = MioType.h2)
            Spacer(Modifier.height(4.dp))
            Text("Customize MIO OS", style = MioType.body)
            Spacer(Modifier.height(16.dp))
            HomeAction("Wallpaper & themes", "Mountain, anime, aurora, live accents", Icons.Rounded.Settings) {
                onDismiss(); MioNav.push(MioScreen.THEMES)
            }
            HomeAction("Personalization", "Grid, icon size, fonts, accent colour", Icons.Rounded.Settings) {
                onDismiss(); MioNav.push(MioScreen.APPEARANCE)
            }
            HomeAction("Set MIO OS as default", "Confirm the home app selection", Icons.Rounded.Settings) {
                onDismiss(); MioNav.push(MioScreen.HOME_SETTINGS)
            }
            HomeAction("MIO settings", "All settings and permissions", Icons.Rounded.Settings) {
                onDismiss(); MioNav.push(MioScreen.SETTINGS)
            }
            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun HomeAction(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).bounceClick(scaleDown = 0.98f, onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.07f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = LocalAccent.current, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MioType.title)
            Text(subtitle, style = MioType.micro)
        }
    }
}

@Composable
private fun AppQuickSheet(app: MioApp, onDismiss: () -> Unit, onHide: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF0D0918),
        scrimColor = Color.Black.copy(alpha = 0.55f),
        dragHandle = { SheetHandle() },
    ) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIconView(app, 46.dp)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(app.name, style = MioType.h2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(app.packageName, style = MioType.micro, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.height(18.dp))
            HomeAction("Open", "Launch ${app.name}", Icons.Rounded.PlayArrow) {
                onDismiss(); Mio.apps.launch(app)
            }
            HomeAction("App info", "Permissions, storage, notifications", Icons.Rounded.Settings) {
                onDismiss(); Mio.apps.openAppInfo(app.packageName)
            }
            HomeAction("Hide from home", "Still available in hidden apps", Icons.Rounded.MoreVert) {
                onHide()
            }
            if (!app.isSystem) {
                HomeAction("Uninstall", "Open the system uninstaller", Icons.Rounded.MoreVert) {
                    onDismiss(); Mio.apps.uninstall(app.packageName)
                }
            }
            Spacer(Modifier.height(18.dp))
        }
    }
}
