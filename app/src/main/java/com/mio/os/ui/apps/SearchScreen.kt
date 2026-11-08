package com.mio.os.ui.apps

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mio.os.core.LauncherHelper
import com.mio.os.core.Mio
import com.mio.os.core.MioNav
import com.mio.os.core.MioPlayer
import com.mio.os.core.MioScreen
import com.mio.os.core.NotesRepo
import com.mio.os.core.SearchIndex
import com.mio.os.core.WallpaperRepo
import com.mio.os.ui.components.SectionHeader
import com.mio.os.ui.components.bounceClick
import com.mio.os.ui.settings.MioScreenScaffold
import com.mio.os.ui.settings.MioWallpapers
import com.mio.os.ui.settings.SettingsPill
import com.mio.os.ui.setup.MioTextField
import com.mio.os.ui.theme.LocalAccent
import com.mio.os.ui.theme.LocalApps
import com.mio.os.ui.theme.LocalSettings
import com.mio.os.ui.theme.MioType
import java.net.URLEncoder

private val SettingEntries = listOf(
    "settings" to "Settings",
    "appearance" to "Appearance",
    "home_settings" to "Home screen",
    "privacy" to "Privacy",
    "about" to "About SEGAWA OS",
    "ai" to "SEGAWA AI",
    "music" to "SEGAWA Music",
    "files" to "Files",
    "gallery" to "Gallery",
    "themes" to "Themes",
    "notes" to "Notes",
    "calendar" to "Calendar",
    "clock" to "Clock",
    "calculator" to "Calculator",
    "weather" to "Weather",
    "gaming" to "Gaming",
    "hidden" to "Hidden apps",
    "lock" to "Lock screen",
    "drawer" to "App drawer",
    "control" to "Control centre",
    "search" to "Universal search",
    "arrange" to "Arrange icons",
    "icons" to "Icon studio",
)

/**
 * Universal search across apps, notes, music, wallpapers and settings
 * shortcuts, ranked by [SearchIndex]. Web lookup opens the user's browser.
 */
@Composable
fun SearchScreen() {
    val context = LocalContext.current
    val settings = LocalSettings.current
    val apps = LocalApps.current
    val tracks by Mio.media.tracks.collectAsState()
    var query by remember { mutableStateOf("") }
    val results = remember(query, apps, tracks, settings.hiddenApps) {
        SearchIndex.search(
            query = query,
            apps = apps.filter { it.packageName !in settings.hiddenApps }
                .map { it.packageName to it.name },
            notes = NotesRepo.all().map { it.id to it.text },
            tracks = tracks.map { it.id to "${it.title} — ${it.artist}" },
            walls = MioWallpapers.map { it.id to it.label },
            settings = SettingEntries,
        )
    }

    MioScreenScaffold(title = "Search", subtitle = "Apps · notes · music · settings") { m ->
        Column(m.padding(bottom = 26.dp)) {
            MioTextField(value = query, onValueChange = { query = it }, label = "Search everything")
            Spacer(Modifier.height(12.dp))
            if (query.trim().length < 2) {
                Text(
                    "Type at least 2 characters. Results launch instantly when tapped.",
                    style = MioType.body.copy(fontSize = 11.5.sp),
                )
            } else if (results.isEmpty()) {
                Text("No matches on this device.", style = MioType.body.copy(fontSize = 11.5.sp))
            } else {
                SectionHeader("${results.size} result${if (results.size == 1) "" else "s"}")
                LazyColumn(Modifier.fillMaxWidth().height(380.dp)) {
                    items(results, key = { it.kind + ":" + it.payload }) { hit ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                                .bounceClick(scaleDown = 0.98f) { openHit(hit.kind, hit.payload) }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier.size(38.dp).clip(CircleShape)
                                    .background(LocalAccent.current.copy(alpha = 0.22f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    hit.kind.take(1).uppercase(),
                                    style = MioType.title.copy(fontSize = 14.sp),
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    hit.title,
                                    style = MioType.title.copy(fontSize = 12.5.sp),
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    hit.subtitle,
                                    style = MioType.micro.copy(fontSize = 10.sp),
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Text(
                                hit.kind,
                                style = MioType.micro.copy(
                                    fontSize = 9.sp,
                                    color = Color.White.copy(alpha = 0.5f),
                                ),
                            )
                        }
                    }
                }
            }
            if (settings.searchWeb && query.trim().length >= 2) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SettingsPill("Web: “${query.trim().take(24)}”", primary = true) {
                        val q = URLEncoder.encode(query.trim(), "UTF-8")
                        val ok = runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("https://duckduckgo.com/?q=$q"))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }.isSuccess
                        if (!ok) LauncherHelper.toast(context, "No browser available")
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Web lookup uses your system browser.",
                    style = MioType.micro.copy(fontSize = 9.5.sp),
                )
            }
            Spacer(Modifier.height(18.dp))
        }
    }
}

private fun openHit(kind: String, payload: String) {
    when (kind) {
        "app" -> Mio.apps.launchPackage(payload)
        "note" -> {
            MioNav.home()
            MioNav.push(MioScreen.NOTES)
        }
        "music" -> {
            val all = Mio.media.tracksWithDemo()
            val idx = all.indexOfFirst { it.id.toString() == payload }
            if (idx >= 0) {
                MioPlayer.playQueue(Mio.context, all, idx)
                LauncherHelper.toast(Mio.context, "Playing ${all[idx].title}")
            } else {
                LauncherHelper.toast(Mio.context, "Track unavailable")
            }
            MioNav.home()
        }
        "wallpaper" -> {
            Mio.prefs.edit {
                it.copy(
                    wallpaperId = payload,
                    wallpaperUri = null,
                    wallHistory = WallpaperRepo.pushHistory(it.wallHistory, payload),
                )
            }
            LauncherHelper.toast(Mio.context, "Wallpaper applied")
        }
        "setting" -> {
            val s = when (payload) {
                "settings" -> MioScreen.SETTINGS
                "appearance" -> MioScreen.APPEARANCE
                "home_settings" -> MioScreen.HOME_SETTINGS
                "privacy" -> MioScreen.PRIVACY
                "about" -> MioScreen.ABOUT
                "ai" -> MioScreen.AI
                "music" -> MioScreen.MUSIC
                "files" -> MioScreen.FILES
                "gallery" -> MioScreen.GALLERY
                "themes" -> MioScreen.THEMES
                "notes" -> MioScreen.NOTES
                "calendar" -> MioScreen.CALENDAR
                "clock" -> MioScreen.CLOCK
                "calculator" -> MioScreen.CALCULATOR
                "weather" -> MioScreen.WEATHER
                "gaming" -> MioScreen.GAMING
                "hidden" -> MioScreen.HIDDEN
                "lock" -> MioScreen.LOCK
                "drawer" -> MioScreen.DRAWER
                "control" -> MioScreen.CONTROL
                "arrange" -> MioScreen.ARRANGE
                "icons" -> MioScreen.ICONS
                else -> null
            }
            if (s != null) {
                MioNav.home()
                if (s != MioScreen.SEARCH) MioNav.push(s)
            }
        }
    }
}
