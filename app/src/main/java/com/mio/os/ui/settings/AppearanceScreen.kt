package com.mio.os.ui.settings

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mio.os.R
import com.mio.os.core.Mio
import com.mio.os.ui.components.GlassCard
import com.mio.os.ui.components.SectionHeader
import com.mio.os.ui.components.bounceClick
import com.mio.os.ui.theme.LocalAccent
import com.mio.os.ui.theme.LocalSettings
import com.mio.os.ui.theme.MioColors
import com.mio.os.ui.theme.MioType

val MioAccents: List<Pair<String, Color>> = listOf(
    "Aurora" to Color(0xFF7C5CFF),
    "Violet" to Color(0xFFA855F7),
    "Indigo" to Color(0xFF5B6CFF),
    "Sky" to Color(0xFF4D8DFF),
    "Mint" to Color(0xFF34D399),
    "Peach" to Color(0xFFFFB86B),
    "Rose" to Color(0xFFF472B6),
    "Crimson" to Color(0xFFFF5C7A),
    "Ice" to Color(0xFF7EE7FF),
    "Sand" to Color(0xFFE9D8A6),
)

data class WallpaperOption(val id: String, val label: String, val drawable: Int?)

val MioWallpapers = listOf(
    WallpaperOption("mountain", "Mountain", R.drawable.wall_mountain),
    WallpaperOption("anime", "Anime dusk", R.drawable.wall_anime),
    WallpaperOption("aurora", "Aurora", R.drawable.wall_aurora),
    WallpaperOption("violet", "Violet field", null),
)

@Composable
fun AppearanceScreen() {
    val settings = LocalSettings.current

    MioScreenScaffold(title = "Appearance", subtitle = "Grid, icons, type & motion") { m ->
        Column(m.padding(bottom = 30.dp)) {
            SectionHeader("Accent colour")
            GlassCard(Modifier.fillMaxWidth(), corner = 20.dp, alpha = 0.06f) {
                Column(Modifier.padding(14.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        MioAccents.take(5).forEach { (name, color) ->
                            AccentSwatch(name, color, settings.accent == color.toArgb())
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        MioAccents.drop(5).forEach { (name, color) ->
                            AccentSwatch(name, color, settings.accent == color.toArgb())
                        }
                    }
                }
            }

            SectionHeader("Home grid")
            SettingsGroup {
                StepperRow(
                    "Columns",
                    "${settings.gridColumns}",
                    { Mio.prefs.edit { it.copy(gridColumns = (it.gridColumns - 1).coerceIn(3, 6)) } },
                    { Mio.prefs.edit { it.copy(gridColumns = (it.gridColumns + 1).coerceIn(3, 6)) } },
                )
                SettingsGroupDivider()
                SliderRow("Icon size", settings.iconScale, 0.75f..1.3f) { v ->
                    Mio.prefs.edit { it.copy(iconScale = v) }
                }
                SettingsGroupDivider()
                SliderRow("Label size (applies to all text)", settings.fontScale, 0.85f..1.2f) { v ->
                    Mio.prefs.edit { it.copy(fontScale = v) }
                }
                SettingsGroupDivider()
                IconShapeRow(settings.iconShape) { shape -> Mio.prefs.edit { it.copy(iconShape = shape) } }
            }

            SectionHeader("Glass & lighting")
            SettingsGroup {
                SliderRow("Glass intensity", settings.glass, 0f..1f) { v ->
                    Mio.prefs.edit { it.copy(glass = v) }
                }
                SettingsGroupDivider()
                SliderRow("Wallpaper dim", settings.dim, 0f..0.6f) { v ->
                    Mio.prefs.edit { it.copy(dim = v) }
                }
            }

            SectionHeader("Motion")
            SettingsGroup {
                ToggleRow(
                    "Animations",
                    "Launcher transitions and springy taps",
                    settings.animations,
                ) { v -> Mio.prefs.edit { it.copy(animations = v) } }
                SettingsGroupDivider()
                ToggleRow(
                    "Reduce motion",
                    "Disable ambient glows for slower devices",
                    settings.reduceMotion,
                ) { v -> Mio.prefs.edit { it.copy(reduceMotion = v) } }
                SettingsGroupDivider()
                ToggleRow(
                    "Show labels",
                    "App names under icons",
                    settings.showLabels,
                ) { v -> Mio.prefs.edit { it.copy(showLabels = v) } }
            }

            SectionHeader("Icon packs")
            GlassCard(Modifier.fillMaxWidth(), corner = 18.dp, alpha = 0.06f) {
                Column(Modifier.padding(16.dp)) {
                    Text("Third-party icon packs", style = MioType.title.copy(fontSize = 13.sp))
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Android has no public icon-pack API: packs like Nova's rely on their own " +
                            "proprietary format. MIO OS therefore styles every icon itself – shape, " +
                            "size and background tint – so all installed apps stay visually consistent.",
                        style = MioType.body.copy(fontSize = 11.sp, lineHeight = 16.sp),
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun AccentSwatch(name: String, color: Color, selected: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(44.dp).clip(CircleShape)
                .background(Brush.linearGradient(listOf(color, color.copy(alpha = 0.65f))))
                .then(if (selected) Modifier.border(2.dp, Color.White, CircleShape) else Modifier)
                .bounceClick(scaleDown = 0.9f) {
                    Mio.prefs.edit { it.copy(accent = color.toArgb()) }
                },
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.height(5.dp))
        Text(name, style = MioType.micro.copy(fontSize = 8.5.sp))
    }
}

@Composable
private fun IconShapeRow(current: String, onChange: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Icon shape", style = MioType.title.copy(fontSize = 13.5.sp), modifier = Modifier.weight(1f))
        listOf("squircle" to "Squircle", "rounded" to "Round", "circle" to "Circle").forEach { (id, label) ->
            val selected = current == id
            Box(
                Modifier.padding(start = 6.dp).clip(RoundedCornerShape(50))
                    .background(if (selected) LocalAccent.current.copy(alpha = 0.30f) else Color.White.copy(alpha = 0.07f))
                    .border(
                        1.dp,
                        if (selected) LocalAccent.current.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.08f),
                        RoundedCornerShape(50),
                    )
                    .bounceClick(scaleDown = 0.94f) { onChange(id) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(
                    label,
                    style = MioType.micro.copy(
                        fontSize = 9.5.sp,
                        color = if (selected) Color.White else Color.White.copy(alpha = 0.6f),
                    ),
                )
            }
        }
    }
}

@Composable
fun WallpaperPreview(
    option: WallpaperOption,
    selected: Boolean,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 150.dp,
    onClick: () -> Unit,
) {
    Box(
        modifier.height(height).clip(RoundedCornerShape(18.dp))
            .border(
                1.5.dp,
                if (selected) LocalAccent.current else Color.White.copy(alpha = 0.10f),
                RoundedCornerShape(18.dp),
            )
            .bounceClick(scaleDown = 0.97f, onClick = onClick),
    ) {
        if (option.drawable != null) {
            androidx.compose.foundation.Image(
                painter = painterResource(option.drawable),
                contentDescription = option.label,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.linearGradient(listOf(Color(0xFF1B0F3A), Color(0xFF070410), Color(0xFF3B1E86)))
                )
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)))
            )
        )
        Text(
            option.label,
            style = MioType.micro.copy(fontSize = 10.sp, color = Color.White),
            modifier = Modifier.align(Alignment.BottomStart).padding(10.dp),
        )
        if (selected) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(8.dp).size(20.dp).clip(CircleShape)
                    .background(LocalAccent.current),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(12.dp))
            }
        }
    }
}
