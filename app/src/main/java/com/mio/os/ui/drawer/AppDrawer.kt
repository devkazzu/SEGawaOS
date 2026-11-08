package com.mio.os.ui.drawer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mio.os.core.Mio
import com.mio.os.core.MioApp
import com.mio.os.ui.apps.IconStudioRequest
import com.mio.os.core.MioCategory
import com.mio.os.core.MioNav
import com.mio.os.core.MioNotifications
import com.mio.os.core.MioScreen
import com.mio.os.ui.components.AppIconView
import com.mio.os.ui.components.FolderPickerSheet
import com.mio.os.ui.components.GlassCard
import com.mio.os.ui.components.MioLogo
import com.mio.os.ui.components.MioWallpaper
import com.mio.os.ui.components.SectionHeader
import com.mio.os.ui.components.SheetHandle
import com.mio.os.ui.components.bounceClick
import com.mio.os.ui.theme.LocalAccent
import com.mio.os.ui.theme.LocalApps
import com.mio.os.ui.theme.LocalSettings
import com.mio.os.ui.theme.MioColors
import com.mio.os.ui.theme.MioType
import kotlinx.coroutines.launch

@Composable
fun AppDrawer() {
    val settings = LocalSettings.current
    val apps = LocalApps.current
    val scope = rememberCoroutineScope()
    val gridState = rememberLazyGridState()
    val focus = LocalFocusManager.current
    val notifApps by MioNotifications.apps.collectAsState()

    var query by remember { mutableStateOf("") }
    var tab by remember { mutableStateOf(0) }
    var category by remember { mutableStateOf<MioCategory?>(null) }
    var favOnly by remember { mutableStateOf(false) }
    var sheetApp by remember { mutableStateOf<MioApp?>(null) }
    var folderApp by remember { mutableStateOf<MioApp?>(null) }

    val filtered = remember(apps, query, category, favOnly, settings.sortMode, settings.hiddenApps, settings.favorites) {
        var list = apps.filter { it.packageName !in settings.hiddenApps }
        if (category != null) list = list.filter { it.category == category }
        if (favOnly) list = list.filter { it.packageName in settings.favorites }
        if (query.isNotBlank()) {
            list = list.filter {
                it.name.contains(query, true) || it.packageName.contains(query, true)
            }
        }
        when (settings.sortMode) {
            "RECENT" -> {
                val order = settings.recentApps
                list.sortedBy { order.indexOf(it.packageName).let { i -> if (i < 0) 999 else i } }
            }
            "CATEGORY" -> list.sortedWith(compareBy({ it.category.ordinal }, { it.name.lowercase() }))
            else -> list.sortedBy { it.name.lowercase() }
        }
    }

    Box(Modifier.fillMaxSize().background(MioColors.Bg.copy(alpha = 0.94f))) {
        MioWallpaper(settings, extraDim = 0.55f)
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
        ) {
            // Header
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MioLogo(Modifier.size(26.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (tab == 0) "Apps" else "Widgets",
                        style = MioType.h1.copy(fontSize = 24.sp),
                    )
                    Text(
                        if (query.isBlank()) {
                            if (filtered.size == 1) "1 app" else "${filtered.size} apps"
                        } else {
                            if (filtered.isEmpty()) "No matches" else "${filtered.size} result" +
                                if (filtered.size == 1) "" else "s"
                        },
                        style = MioType.micro.copy(fontSize = 10.sp),
                    )
                }
                Box(
                    Modifier.size(34.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.09f))
                        .bounceClick { MioNav.push(MioScreen.SETTINGS) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Settings, null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(17.dp))
                }
            }

            // Tabs
            Row(
                Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TabPill("Apps", tab == 0) { tab = 0 }
                Spacer(Modifier.width(8.dp))
                TabPill("Widgets", tab == 1) { tab = 1 }
                Spacer(Modifier.weight(1f))
                TabPill("Hidden", false) { MioNav.push(MioScreen.HIDDEN) }
            }

            // Search
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.09f))
                    .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(50))
                    .padding(horizontal = 16.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Rounded.Search, null, tint = Color.White.copy(alpha = 0.55f),
                    modifier = Modifier.size(17.dp),
                )
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(
                            "Search apps…",
                            style = MioType.body.copy(fontSize = 13.sp, color = Color.White.copy(alpha = 0.45f)),
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(color = MioColors.Text, fontSize = 13.sp),
                        cursorBrush = SolidColor(LocalAccent.current),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            filtered.firstOrNull()?.let { Mio.apps.launch(it) }
                            focus.clearFocus()
                        }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (query.isNotEmpty()) {
                    Icon(
                        Icons.Rounded.Close, "Clear", tint = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp).bounceClick { query = "" },
                    )
                }
            }

            if (tab == 0) {
                CategoryChips(
                    selected = category,
                    favOnly = favOnly,
                    onSelect = { category = it },
                    onFavToggle = { favOnly = !favOnly },
                )
                if (apps.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            MioLogo(Modifier.size(40.dp))
                            Spacer(Modifier.height(12.dp))
                            Text("Scanning installed apps…", style = MioType.body)
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(5),
                        state = gridState,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        items(filtered, key = { it.key }) { app ->
                            DrawerTile(
                                app = app,
                                badge = app.packageName in notifApps,
                                onClick = { Mio.apps.launch(app) },
                                onLongClick = { sheetApp = app },
                            )
                        }
                    }
                }
            } else {
                WidgetsTab(Modifier.weight(1f))
            }

            // Bottom bar
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BottomPill("SEGAWA AI") { MioNav.push(MioScreen.AI) }
                Spacer(Modifier.width(8.dp))
                BottomPill("Themes") { MioNav.push(MioScreen.THEMES) }
                Spacer(Modifier.weight(1f))
                Text(
                    if (settings.sortMode == "ALPHABETICAL") "A–Z" else settings.sortMode.lowercase()
                        .replaceFirstChar { it.uppercase() },
                    style = MioType.micro.copy(color = Color.White.copy(alpha = 0.55f)),
                )
                Spacer(Modifier.width(12.dp))
                Box(
                    Modifier.size(38.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.10f))
                        .bounceClick { MioNav.home() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Close, "Home", tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(18.dp))
                }
            }
        }
    }

    sheetApp?.let { app ->
        DrawerAppSheet(
            app,
            onDismiss = { sheetApp = null },
            onMoveToFolder = { folderApp = app; sheetApp = null },
        )
    }
    folderApp?.let { app ->
        FolderPickerSheet(app = app, onDismiss = { folderApp = null })
    }
}

@Composable
private fun TabPill(text: String, selected: Boolean, onClick: () -> Unit) {
    val accent = LocalAccent.current
    Box(
        Modifier.clip(RoundedCornerShape(50))
            .background(if (selected) Color.White.copy(alpha = 0.14f) else Color.Transparent)
            .then(if (selected) Modifier.border(1.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(50)) else Modifier)
            .bounceClick(scaleDown = 0.96f, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(
            text,
            style = MioType.label.copy(
                color = if (selected) Color.White else Color.White.copy(alpha = 0.55f),
                fontSize = 11.5.sp,
            ),
        )
    }
}

@Composable
private fun BottomPill(text: String, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.08f))
            .bounceClick(scaleDown = 0.96f, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(text, style = MioType.micro.copy(color = Color.White.copy(alpha = 0.8f), fontSize = 10.5.sp))
    }
}

@Composable
private fun CategoryChips(
    selected: MioCategory?,
    favOnly: Boolean,
    onSelect: (MioCategory?) -> Unit,
    onFavToggle: () -> Unit,
) {
    val cats = remember { MioCategory.entries.toList() }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Chip("★ Favorites", favOnly) { onFavToggle() }
        Chip("All", selected == null && !favOnly) { onSelect(null) }
        cats.forEach { c ->
            Chip(c.label, selected == c) { onSelect(if (selected == c) null else c) }
        }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    val accent = LocalAccent.current
    Box(
        Modifier.clip(RoundedCornerShape(50))
            .background(if (selected) accent.copy(alpha = 0.30f) else Color.White.copy(alpha = 0.06f))
            .border(
                1.dp,
                if (selected) accent.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.08f),
                RoundedCornerShape(50),
            )
            .bounceClick(scaleDown = 0.95f, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            label,
            style = MioType.micro.copy(
                color = if (selected) Color.White else Color.White.copy(alpha = 0.65f),
                fontSize = 10.sp,
            ),
        )
    }
}

@Composable
private fun DrawerTile(app: MioApp, badge: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    Column(
        Modifier.padding(vertical = 9.dp).bounceClick(onClick = onClick, onLongClick = onLongClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            AppIconView(app, 50.dp)
            if (badge) {
                Box(
                    Modifier.align(Alignment.TopEnd).size(8.dp).clip(CircleShape)
                        .background(LocalAccent.current)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            app.name,
            style = MioType.micro.copy(fontSize = 9.5.sp, color = Color.White.copy(alpha = 0.8f)),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun WidgetsTab(modifier: Modifier = Modifier) {
    val settings = LocalSettings.current
    Column(modifier.padding(horizontal = 20.dp)) {
        SectionHeader("SEGAWA widgets")
        WidgetRow(
            title = "Music player",
            subtitle = "Shows on the home screen while you listen",
            enabled = true,
        ) { }
        WidgetRow(
            title = "Weather widget",
            subtitle = "Live conditions for ${settings.city}",
            enabled = settings.showWeather,
        ) { on -> Mio.prefs.edit { it.copy(showWeather = on) } }
        WidgetRow(
            title = "Clock & date",
            subtitle = "24-hour format: ${if (settings.clock24h) "on" else "off"}",
            enabled = true,
        ) { on -> Mio.prefs.edit { it.copy(clock24h = on) } }
        WidgetRow(
            title = "Dock",
            subtitle = "App shortcuts pinned to the bottom",
            enabled = settings.showDock,
        ) { on -> Mio.prefs.edit { it.copy(showDock = on) } }

        Spacer(Modifier.height(14.dp))
        GlassCard(Modifier.fillMaxWidth(), corner = 18.dp, alpha = 0.06f) {
            Column(Modifier.padding(16.dp)) {
                Text("Android widgets", style = MioType.title)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Third-party widgets are owned by their apps and need an AppWidgetProvider host. " +
                        "SEGAWA OS ships its own SEGAWA Clock widget – long-press your home screen in the " +
                        "system launcher to add any app's widget alongside it.",
                    style = MioType.body.copy(fontSize = 11.5.sp),
                )
            }
        }
    }
}

@Composable
private fun WidgetRow(title: String, subtitle: String, enabled: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MioType.title)
            Text(subtitle, style = MioType.micro)
        }
        MioSwitch(checked = enabled, onCheckedChange = onToggle)
    }
}

@Composable
fun MioSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val accent = LocalAccent.current
    Box(
        Modifier.size(width = 44.dp, height = 26.dp).clip(CircleShape)
            .background(if (checked) accent.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.14f))
            .bounceClick(scaleDown = 0.95f) { onCheckedChange(!checked) },
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            Modifier.padding(horizontal = 3.dp).size(20.dp).clip(CircleShape).background(Color.White)
        )
    }
}

@Composable
private fun DrawerAppSheet(app: MioApp, onDismiss: () -> Unit, onMoveToFolder: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
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
                Column(Modifier.weight(1f)) {
                    Text(app.name, style = MioType.h2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(app.packageName, style = MioType.micro, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.height(16.dp))
            val settings = LocalSettings.current
            val isFav = app.packageName in settings.favorites
            SheetItem(if (isFav) "★ Favorited" else "☆ Favorite") {
                Mio.prefs.edit {
                    it.copy(favorites = if (isFav) it.favorites - app.packageName else it.favorites + app.packageName)
                }
            }
            val inDock = app.packageName in settings.dockApps
            SheetItem(if (inDock) "Remove from dock" else "Pin to dock") {
                Mio.prefs.edit {
                    val d = if (inDock) it.dockApps - app.packageName
                    else (it.dockApps + app.packageName).take(5)
                    it.copy(dockApps = d)
                }
            }
            SheetItem("Custom icon") {
                onDismiss(); IconStudioRequest.pkg = app.packageName; MioNav.push(MioScreen.ICONS)
            }
            SheetItem("Open app") { onDismiss(); Mio.apps.launch(app) }
            SheetItem("App info") { onDismiss(); Mio.apps.openAppInfo(app.packageName) }
            SheetItem("Move to folder") { onMoveToFolder() }
            SheetItem("Hide from drawer") {
                Mio.prefs.setHidden(app.packageName, true)
                onDismiss()
            }
            SheetItem("Uninstall") { onDismiss(); Mio.apps.uninstall(app.packageName) }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun SheetItem(title: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).bounceClick(scaleDown = 0.98f, onClick = onClick)
            .padding(vertical = 13.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MioType.title, modifier = Modifier.weight(1f))
        Icon(Icons.Rounded.MoreVert, null, tint = Color.White.copy(alpha = 0.3f), modifier = Modifier.size(16.dp))
    }
}
