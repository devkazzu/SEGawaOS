package com.segawa.os.ui

import androidx.compose.foundation.lazy.grid.*
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.storage.StorageManager
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Obsidian = Color(0xFF08090C)
private val Panel = Color(0xFF111319)
private val Panel2 = Color(0xFF191C24)
private val White = Color(0xFFF5F5F5)
private val Muted = Color(0xFF858995)
private val Crimson = Color(0xFFE63946)
private val Silver = Color(0xFFC0C5CE)

private enum class Page(val label: String) {
    HOME("Home"), APPS("Apps"), DEVICE("Device"), SETTINGS("Settings")
}

@Composable
fun SegawaApp(vm: SegawaViewModel) {
    val accentName by vm.accent.collectAsState()
    val mode by vm.mode.collectAsState()
    val accent = when (accentName) { "Argent" -> Silver; "Mono" -> White; else -> Crimson }
    val dark = mode != "Light"
    val bg = if (dark) Obsidian else Color(0xFFF3F3F5)
    val fg = if (dark) White else Color(0xFF111319)

    var page by remember { mutableStateOf(Page.HOME) }
    var overlay by remember { mutableStateOf<String?>(null) }

    MaterialTheme(
        colorScheme = if (dark) darkColorScheme(primary = accent, background = bg, surface = Panel, onSurface = fg)
                      else lightColorScheme(primary = accent, background = bg, surface = Color.White, onSurface = fg)
    ) {
        Scaffold(
            containerColor = bg,
            topBar = {
                Row(
                    Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 22.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("SEGawa", color = fg, fontSize = 21.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                        Text("BEYOND ORDINARY", color = Muted, fontSize = 9.sp, letterSpacing = 2.5.sp)
                    }
                    IconButton(onClick = { overlay = "Search" }) { Icon(Icons.Rounded.Search, null, tint = fg) }
                    IconButton(onClick = { overlay = "Control Center" }) { Icon(Icons.Rounded.Tune, null, tint = fg) }
                }
            },
            bottomBar = {
                NavigationBar(containerColor = Panel, contentColor = White) {
                    Page.values().forEach { item ->
                        NavigationBarItem(
                            selected = page == item,
                            onClick = { page = item; overlay = null },
                            icon = {
                                Icon(when(item) {
                                    Page.HOME -> Icons.Rounded.Home
                                    Page.APPS -> Icons.Rounded.GridView
                                    Page.DEVICE -> Icons.Rounded.Memory
                                    Page.SETTINGS -> Icons.Rounded.Settings
                                }, contentDescription = item.label)
                            },
                            label = { Text(item.label, fontSize = 10.sp) },
                            colors = NavigationBarItemDefaults.colors(selectedIconColor = accent, selectedTextColor = accent, indicatorColor = Panel2, unselectedIconColor = Muted, unselectedTextColor = Muted)
                        )
                    }
                }
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (page) {
                    Page.HOME -> HomeScreen(accent, vm) { overlay = it }
                    Page.APPS -> AppsScreen(accent)
                    Page.DEVICE -> DeviceScreen(accent)
                    Page.SETTINGS -> SettingsScreen(accent, accentName, mode, vm) { overlay = it }
                }
                if (overlay != null) {
                    Surface(Modifier.fillMaxSize(), color = bg) {
                        when (overlay) {
                            "Focus" -> FocusScreen(accent, vm) { overlay = null }
                            "Personalization" -> PersonalizationScreen(accent, accentName, mode, vm)
                                { overlay = null }
                            "Control Center" -> ControlCenter(accent) { overlay = null }
                            "Search" -> SearchScreen(accent) { overlay = null }
                            "Activity" -> ActivityScreen(accent, vm) { overlay = null }
                            else -> Unit
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(accent: Color, vm: SegawaViewModel, open: (String) -> Unit) {
    val context = LocalContext.current
    val sessions by vm.sessions.collectAsState()
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
    val battery = remember { batteryPercent(context) }
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp),
        contentPadding = PaddingValues(bottom = 22.dp)
    ) {
        item {
            Spacer(Modifier.height(8.dp))
            Text(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(now)), fontSize = 56.sp, fontWeight = FontWeight.Light, color = White, letterSpacing = (-2).sp)
            Text(SimpleDateFormat("EEEE  •  dd MMMM yyyy", Locale.getDefault()).format(Date(now)).uppercase(), color = Muted, fontSize = 10.sp, letterSpacing = 1.8.sp)
            Spacer(Modifier.height(8.dp))
            Text("静けさの中に、力を。", color = accent, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text("A quieter way to command your day.", color = Muted, fontSize = 12.sp)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard("BATTERY", "$battery%", Icons.Rounded.BatteryFull, accent, Modifier.weight(1f))
                MetricCard("FOCUS SESSIONS", "${sessions.count { it.completed }}", Icons.Rounded.Bolt, accent, Modifier.weight(1f))
            }
        }
        item {
            SectionHeader("COMMAND CENTER", "Quick access")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickTile("Focus mode", Icons.Rounded.Timer, accent, Modifier.weight(1f)) { open("Focus") }
                QuickTile("Control", Icons.Rounded.Tune, accent, Modifier.weight(1f)) { open("Control Center") }
                QuickTile("Customize", Icons.Rounded.Palette, accent, Modifier.weight(1f)) { open("Personalization") }
            }
        }
        item {
            SectionHeader("YOUR DEVICE", "At a glance")
            SegawaCard {
                InfoLine("Device", "${Build.MANUFACTURER} ${Build.MODEL}")
                InfoLine("Android", "Version ${Build.VERSION.RELEASE}")
                InfoLine("Build", Build.DISPLAY.take(26))
            }
        }
        item {
            SectionHeader("RECENT ACTIVITY", "Local history")
            SegawaCard {
                if (sessions.isEmpty()) {
                    Text("No focus sessions yet. Start your first session and your history will appear here.", color = Muted, fontSize = 12.sp)
                } else {
                    sessions.take(4).forEach {
                        InfoLine(if (it.completed) "Focus completed" else "Focus ended", "${it.durationMinutes} min  •  ${SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(Date(it.startedAt))}")
                    }
                }
                TextButton(onClick = { open("Activity") }) { Text("View activity", color = accent) }
            }
        }
    }
}

@Composable
private fun AppsScreen(accent: Color) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var grid by remember { mutableStateOf(true) }
    val apps = remember {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            .map { it.activityInfo }
            .distinctBy { it.packageName }
            .sortedBy { it.loadLabel(context.packageManager).toString().lowercase() }
    }
    val filtered = apps.filter { it.loadLabel(context.packageManager).toString().contains(query, true) }
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Spacer(Modifier.height(8.dp))
        SearchField(query, { query = it }, accent, "Search installed apps")
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("${filtered.size} APPLICATIONS", color = Muted, fontSize = 10.sp, letterSpacing = 1.5.sp, modifier = Modifier.weight(1f))
            IconButton(onClick = { grid = !grid }) { Icon(if (grid) Icons.Rounded.ViewList else Icons.Rounded.GridView, null, tint = accent) }
        }
        if (grid) {
            LazyVerticalGrid(columns = GridCells.Adaptive(82.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(filtered.size) { index ->
                    val info = filtered[index]
                    Column(
                        Modifier.clip(RoundedCornerShape(16.dp)).clickable {
                            val launch = context.packageManager.getLaunchIntentForPackage(info.packageName)
                            if (launch != null) context.startActivity(launch)
                        }.padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(Modifier.size(48.dp), shape = RoundedCornerShape(15.dp), color = Panel2) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(info.loadLabel(context.packageManager).toString().take(1).uppercase(), color = White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(info.loadLabel(context.packageManager).toString(), color = White, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(filtered.size) { index ->
                    val info = filtered[index]
                    SegawaCard(Modifier.fillMaxWidth().clickable {
                        context.packageManager.getLaunchIntentForPackage(info.packageName)?.let(context::startActivity)
                    }) {
                        Text(info.loadLabel(context.packageManager).toString(), color = White)
                        Text(info.packageName, color = Muted, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceScreen(accent: Color) {
    val context = LocalContext.current
    val battery = batteryPercent(context)
    val storage = remember {
        val stat = android.os.StatFs(android.os.Environment.getDataDirectory().path)
        val total = stat.totalBytes / (1024.0 * 1024 * 1024)
        val free = stat.availableBytes / (1024.0 * 1024 * 1024)
        Pair(total - free, total)
    }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { Spacer(Modifier.height(8.dp)); Text("Device Center", color = White, fontSize = 28.sp, fontWeight = FontWeight.Light); Text("A clear view of your hardware.", color = Muted, fontSize = 12.sp) }
        item {
            SegawaCard {
                InfoLine("Manufacturer", Build.MANUFACTURER)
                InfoLine("Model", Build.MODEL)
                InfoLine("Android version", Build.VERSION.RELEASE)
                InfoLine("SDK level", Build.VERSION.SDK_INT.toString())
                InfoLine("Battery", "$battery%")
                InfoLine("Security patch", Build.VERSION.SECURITY_PATCH)
            }
        }
        item {
            SegawaCard {
                Text("INTERNAL STORAGE", color = Muted, fontSize = 10.sp, letterSpacing = 1.5.sp)
                Text(String.format(Locale.getDefault(), "%.1f / %.1f GB", storage.first, storage.second), color = White, fontSize = 23.sp)
                LinearProgressIndicator(progress = { if (storage.second > 0) (storage.first / storage.second).toFloat().coerceIn(0f, 1f) else 0f }, modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape), color = accent, trackColor = Panel2)
                Text("App-visible shared storage estimate. System partitions are not included.", color = Muted, fontSize = 10.sp)
            }
        }
        item {
            SegawaCard {
                Text("SYSTEM STATUS", color = Muted, fontSize = 10.sp, letterSpacing = 1.5.sp)
                InfoLine("Device", if (Build.DEVICE.isNotBlank()) Build.DEVICE else "Unavailable")
                InfoLine("Product", Build.PRODUCT)
                InfoLine("Brand", Build.BRAND)
                InfoLine("Hardware", Build.HARDWARE)
            }
        }
        item { Text("SEGawa only displays information available through public Android APIs. Some hardware metrics are restricted by Android.", color = Muted, fontSize = 11.sp) }
    }
}

@Composable
private fun FocusScreen(accent: Color, vm: SegawaViewModel, close: () -> Unit) {
    var preset by remember { mutableStateOf(25) }
    var remaining by remember { mutableIntStateOf(25 * 60) }
    var running by remember { mutableStateOf(false) }
    var startedAt by remember { mutableLongStateOf(0L) }
    var saved by remember { mutableStateOf(false) }
    LaunchedEffect(running) {
        while (running && remaining > 0) {
            delay(1000)
            remaining--
        }
        if (running && remaining == 0 && !saved) {
            vm.saveSession(preset, true)
            saved = true
            running = false
        }
    }
    val progress = 1f - remaining.toFloat() / (preset * 60).coerceAtLeast(1)
    Column(Modifier.fillMaxSize().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = close) { Icon(Icons.Rounded.ArrowBack, null, tint = White) }
            Text("FOCUS MODE", color = Muted, fontSize = 11.sp, letterSpacing = 2.sp)
        }
        Spacer(Modifier.height(30.dp))
        Box(Modifier.size(250.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxSize(), color = accent, trackColor = Panel2, strokeWidth = 7.dp)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(String.format("%02d:%02d", remaining / 60, remaining % 60), color = White, fontSize = 54.sp, fontWeight = FontWeight.Light)
                Text(if (running) "IN SESSION" else if (remaining == 0) "COMPLETE" else "READY WHEN YOU ARE", color = Muted, fontSize = 9.sp, letterSpacing = 1.8.sp)
            }
        }
        Spacer(Modifier.height(28.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(25, 50, 90).forEach { minutes ->
                FilterChip(selected = preset == minutes, onClick = {
                    if (!running) { preset = minutes; remaining = minutes * 60; saved = false }
                }, label = { Text("${minutes}m") }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = accent, selectedLabelColor = White))
            }
        }
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { if (remaining > 0) running = !running }, colors = ButtonDefaults.buttonColors(containerColor = accent)) {
                Icon(if (running) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null)
                Spacer(Modifier.width(6.dp)); Text(if (running) "Pause" else "Start")
            }
            OutlinedButton(onClick = {
                if (running || remaining != preset * 60) vm.saveSession(preset, false)
                running = false; remaining = preset * 60; saved = false
            }) { Icon(Icons.Rounded.Refresh, null); Spacer(Modifier.width(6.dp)); Text("Reset") }
        }
        Spacer(Modifier.height(24.dp))
        Text("Choose a duration. Your focus history stays on this device.", color = Muted, fontSize = 11.sp)
    }
}

@Composable
private fun PersonalizationScreen(accent: Color, accentName: String, mode: String, vm: SegawaViewModel, close: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        HeaderBack("Personalization", close)
        Text("ACCENT COLOR", color = Muted, fontSize = 10.sp, letterSpacing = 1.6.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf("Crimson" to Crimson, "Argent" to Silver, "Mono" to White).forEach { (name, color) ->
                FilterChip(selected = accentName == name, onClick = { vm.setAccent(name) }, label = { Text(name) }, leadingIcon = { Box(Modifier.size(10.dp).clip(CircleShape).background(color)) })
            }
        }
        Text("APPEARANCE", color = Muted, fontSize = 10.sp, letterSpacing = 1.6.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Dark", "Light", "System").forEach { value ->
                FilterChip(selected = mode == value, onClick = { vm.setMode(value) }, label = { Text(value) })
            }
        }
        SegawaCard {
            Text("Design philosophy", color = White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text("Quiet surfaces. Intentional contrast. Crimson as a signal, never decoration.", color = Muted, fontSize = 12.sp)
        }
        Text("Changes are saved automatically on this device.", color = Muted, fontSize = 11.sp)
    }
}

@Composable
private fun SettingsScreen(accent: Color, accentName: String, mode: String, vm: SegawaViewModel, open: (String) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Settings", color = White, fontSize = 28.sp, fontWeight = FontWeight.Light)
        Text("Make SEGawa yours.", color = Muted, fontSize = 12.sp)
        SegawaCard {
            SettingsRow("Personalization", "Accent, appearance and visual identity", Icons.Rounded.Palette) { open("Personalization") }
            HorizontalDivider(color = Color(0x22FFFFFF))
            SettingsRow("Focus mode", "Pomodoro timer and session tracking", Icons.Rounded.Timer) { open("Focus") }
            HorizontalDivider(color = Color(0x22FFFFFF))
            SettingsRow("Activity history", "Your locally stored focus sessions", Icons.Rounded.History) { open("Activity") }
            HorizontalDivider(color = Color(0x22FFFFFF))
            SettingsRow("Control center", "Shortcuts to Android settings", Icons.Rounded.Tune) { open("Control Center") }
        }
        SegawaCard {
            InfoLine("Application", "SEGawa OS")
            InfoLine("Version", "1.0.0")
            InfoLine("Privacy", "Local-first • No account")
            InfoLine("Accent", accentName)
            InfoLine("Appearance", mode)
        }
        Text("SEGawa OS is a companion dashboard. It does not replace Android system services or bypass platform permissions.", color = Muted, fontSize = 11.sp)
    }
}

@Composable
private fun ControlCenter(accent: Color, close: () -> Unit) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        HeaderBack("Control Center", close)
        Text("SYSTEM SHORTCUTS", color = Muted, fontSize = 10.sp, letterSpacing = 1.6.sp)
        val actions = listOf(
            Triple("Wi-Fi", Icons.Rounded.Wifi, Settings.ACTION_WIFI_SETTINGS),
            Triple("Bluetooth", Icons.Rounded.Bluetooth, Settings.ACTION_BLUETOOTH_SETTINGS),
            Triple("Display", Icons.Rounded.Brightness6, Settings.ACTION_DISPLAY_SETTINGS),
            Triple("Sound", Icons.Rounded.VolumeUp, Settings.ACTION_SOUND_SETTINGS),
            Triple("Battery", Icons.Rounded.BatteryFull, Settings.ACTION_BATTERY_SAVER_SETTINGS),
            Triple("Network", Icons.Rounded.NetworkCell, Settings.ACTION_WIRELESS_SETTINGS)
        )
        actions.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { (label, icon, action) ->
                    QuickTile(label, icon, accent, Modifier.weight(1f)) {
                        runCatching { context.startActivity(Intent(action)) }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        Text("These shortcuts open Android settings. Protected system controls remain managed by Android.", color = Muted, fontSize = 11.sp)
    }
}

@Composable
private fun SearchScreen(accent: Color, close: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val options = listOf("Home", "Apps", "Device Center", "Focus Mode", "Personalization", "Control Center", "Activity History", "Settings")
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        HeaderBack("Search", close)
        SearchField(query, { query = it }, accent, "Search SEGawa")
        options.filter { it.contains(query, true) }.forEach {
            SegawaCard(Modifier.fillMaxWidth().padding(vertical = 4.dp)) { Text(it, color = White) }
        }
        if (options.none { it.contains(query, true) }) Text("No matching features found.", color = Muted)
    }
}

@Composable
private fun ActivityScreen(accent: Color, vm: SegawaViewModel, close: () -> Unit) {
    val sessions by vm.sessions.collectAsState()
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        HeaderBack("Activity", close)
        Text("${sessions.size} FOCUS RECORDS", color = Muted, fontSize = 10.sp, letterSpacing = 1.5.sp)
        if (sessions.isEmpty()) {
            SegawaCard(Modifier.fillMaxWidth()) { Text("Your activity will appear here after your first focus session.", color = Muted, fontSize = 12.sp) }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(sessions) { session ->
                    SegawaCard(Modifier.fillMaxWidth()) {
                        InfoLine(if (session.completed) "Completed session" else "Interrupted session", "${session.durationMinutes} minutes")
                        Text(SimpleDateFormat("dd MMM yyyy • HH:mm", Locale.getDefault()).format(Date(session.startedAt)), color = Muted, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchField(value: String, onValue: (String) -> Unit, accent: Color, hint: String) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Panel2).padding(horizontal = 14.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Search, null, tint = Muted)
        Spacer(Modifier.width(9.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(hint, color = Muted, fontSize = 13.sp)
            BasicTextField(value, onValue, singleLine = true, textStyle = androidx.compose.ui.text.TextStyle(color = White, fontSize = 13.sp), modifier = Modifier.fillMaxWidth())
        }
        if (value.isNotEmpty()) IconButton(onClick = { onValue("") }, modifier = Modifier.size(22.dp)) { Icon(Icons.Rounded.Close, null, tint = Muted) }
    }
}

@Composable
private fun HeaderBack(title: String, close: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = close) { Icon(Icons.Rounded.ArrowBack, null, tint = White) }
        Text(title, color = White, fontSize = 25.sp, fontWeight = FontWeight.Light)
    }
}

@Composable
private fun SectionHeader(kicker: String, title: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            Text(kicker, color = Muted, fontSize = 9.sp, letterSpacing = 1.7.sp)
            Text(title, color = White, fontSize = 17.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun SegawaCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier = modifier.animateContentSize(), color = Panel, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, Color(0x18FFFFFF))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
private fun MetricCard(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, accent: Color, modifier: Modifier = Modifier) {
    SegawaCard(modifier) {
        Icon(icon, null, tint = accent, modifier = Modifier.size(20.dp))
        Text(value, color = White, fontSize = 25.sp, fontWeight = FontWeight.Light)
        Text(label, color = Muted, fontSize = 9.sp, letterSpacing = 1.2.sp)
    }
}

@Composable
private fun QuickTile(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, accent: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(modifier = modifier.height(92.dp).clickable(onClick = onClick), color = Panel, shape = RoundedCornerShape(17.dp), border = BorderStroke(1.dp, Color(0x18FFFFFF))) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(21.dp))
            Text(label, color = White, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Muted, fontSize = 12.sp)
        Spacer(Modifier.width(12.dp))
        Text(value, color = White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SettingsRow(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Crimson, modifier = Modifier.size(23.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = Muted, fontSize = 10.sp)
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = Muted)
    }
}

private fun batteryPercent(context: Context): Int {
    val manager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    return manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).takeIf { it in 0..100 } ?: -1
}
