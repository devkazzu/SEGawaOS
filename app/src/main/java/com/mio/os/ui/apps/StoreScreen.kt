package com.mio.os.ui.apps

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mio.os.core.LauncherHelper
import com.mio.os.core.Mio
import com.mio.os.core.MioNav
import com.mio.os.core.MioScreen
import com.mio.os.core.SegaThemeSpec
import com.mio.os.core.SegaThemes
import com.mio.os.core.applyTo
import com.mio.os.ui.components.GlassCard
import com.mio.os.ui.components.SectionHeader
import com.mio.os.ui.components.bounceClick
import com.mio.os.ui.settings.MioScreenScaffold
import com.mio.os.ui.settings.SettingsPill
import com.mio.os.ui.theme.LocalSettings
import com.mio.os.ui.theme.MioColors
import com.mio.os.ui.theme.MioType

/**
 * Theme store: the honest on-device edition. Every theme, wallpaper and
 * icon style shown here ships inside the app and applies instantly — no
 * downloads, no accounts, no network. A community store with hosted packs
 * would need an account backend, which is out of scope for 2.0.
 */
@Composable
fun StoreScreen() {
    val context = LocalContext.current
    val settings = LocalSettings.current

    MioScreenScaffold(title = "Theme store", subtitle = "Built in · free · offline") { m ->
        Column(m.padding(bottom = 28.dp)) {
            SectionHeader("Themes (${SegaThemes.size})")
            SegaThemes.forEach { spec ->
                ThemePackRow(
                    spec = spec,
                    applied = settings.themeId == spec.id,
                    onApply = {
                        spec.applyTo(Mio.prefs)
                        LauncherHelper.toast(context, "Applied ${spec.name}")
                    },
                )
            }

            SectionHeader("More built-ins")
            GlassCard(Modifier.fillMaxWidth(), corner = 18.dp, alpha = 0.06f) {
                Column(Modifier.padding(vertical = 4.dp)) {
                    StoreLinkRow("Wallpapers", "7 hand-tuned scenes + your photos", MioColors.PurpleSoft) {
                        MioNav.push(MioScreen.THEMES)
                    }
                    StoreLinkRow("Icon styles", "Shape, badge and background studio", MioColors.Amber) {
                        MioNav.push(MioScreen.ICONS)
                    }
                    StoreLinkRow("Automation packs", "Routines for focus, mornings, nights", MioColors.Blue) {
                        MioNav.push(MioScreen.AUTOMATION)
                    }
                    StoreLinkRow("Universal search", "Find anything across apps & notes", MioColors.Pink) {
                        MioNav.push(MioScreen.SEARCH)
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            GlassCard(Modifier.fillMaxWidth(), corner = 18.dp, alpha = 0.06f) {
                Column(Modifier.padding(16.dp)) {
                    Text("Why no downloads?", style = MioType.title.copy(fontSize = 12.5.sp))
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Community packs need hosted storage, moderation and accounts. SEGAWA OS 2.0 " +
                            "keeps everything on-device instead — but themes export as shareable text " +
                            "from the Themes screen, so you can still trade them with friends.",
                        style = MioType.body.copy(fontSize = 11.sp, lineHeight = 16.sp),
                    )
                    Spacer(Modifier.height(10.dp))
                    Row {
                        SettingsPill("Open Themes") { MioNav.push(MioScreen.THEMES) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemePackRow(spec: SegaThemeSpec, applied: Boolean, onApply: () -> Unit) {
    GlassCard(Modifier.fillMaxWidth().padding(vertical = 5.dp), corner = 18.dp, alpha = 0.07f) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
                Box(Modifier.size(30.dp).clip(CircleShape).background(Color(spec.accent)))
                Box(Modifier.size(30.dp).clip(CircleShape).background(Color(spec.accent2)))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(spec.name, style = MioType.title.copy(fontSize = 13.sp))
                Text(
                    if (applied) "Applied ✓" else "Wallpaper: ${spec.wallpaper}",
                    style = MioType.micro.copy(
                        fontSize = 10.sp,
                        color = if (applied) MioColors.Green else Color.White.copy(alpha = 0.55f),
                    ),
                )
            }
            if (!applied) {
                Text(
                    "Apply",
                    style = MioType.micro.copy(fontSize = 11.sp, color = Color.White),
                    modifier = Modifier.clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.14f))
                        .bounceClick(scaleDown = 0.94f, onClick = onApply)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun StoreLinkRow(title: String, subtitle: String, dot: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .bounceClick(scaleDown = 0.98f, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MioType.title.copy(fontSize = 12.5.sp))
            Text(subtitle, style = MioType.micro.copy(fontSize = 10.sp))
        }
        Text("›", style = MioType.title.copy(fontSize = 16.sp, color = Color.White.copy(alpha = 0.5f)))
    }
}
