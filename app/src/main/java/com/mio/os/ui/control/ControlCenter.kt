package com.mio.os.ui.control

import android.text.format.DateFormat
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
import androidx.compose.material.icons.rounded.AirplanemodeActive
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import com.mio.os.core.DeviceStats
import com.mio.os.core.LauncherHelper
import com.mio.os.core.Mio
import com.mio.os.core.MioNav
import com.mio.os.core.MioPlayer
import com.mio.os.core.MioScreen
import com.mio.os.core.SystemControls
import com.mio.os.core.Weather
import com.mio.os.core.WeatherRepo
import com.mio.os.ui.components.GlassCard
import com.mio.os.ui.components.MioLogo
import com.mio.os.ui.components.MioWallpaper
import com.mio.os.ui.components.ThinSlider
import com.mio.os.ui.components.bounceClick
import com.mio.os.ui.theme.LocalAccent
import com.mio.os.ui.theme.LocalSettings
import com.mio.os.ui.theme.MioColors
import com.mio.os.ui.theme.MioType
import kotlinx.coroutines.delay
import java.util.Date

/**
 * MIO Control Center. Every tile does something real:
 * toggles use the actual system APIs a launcher may use, and the ones Android
 * restricts (airplane mode, hotspot, cast, rotation lock) open the matching
 * system panel instead of pretending.
 */
@Composable
fun ControlCenter() {
    val context = LocalContext.current
    val settings = LocalSettings.current
    val battery by DeviceStats.battery.collectAsState()
    val torch by SystemControls.torch.collectAsState()
    val dnd by SystemControls.dnd.collectAsState()
    val track by MioPlayer.current.collectAsState()
    val playing by MioPlayer.playing.collectAsState()

    var wifiOn by remember { mutableStateOf(SystemControls.wifiEnabled(context)) }
    var brightness by remember { mutableFloatStateOf(SystemControls.systemBrightness(context) / 255f) }
    var volume by remember { mutableFloatStateOf(SystemControls.mediaVolume(context).toFloat() / SystemControls.mediaVolumeMax(context)) }

    var weather by remember { mutableStateOf<Weather?>(null) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        weather = WeatherRepo.get(context, settings.city)
        SystemControls.refreshDnd(context)
        while (true) {
            now = System.currentTimeMillis()
            wifiOn = SystemControls.wifiEnabled(context)
            delay(2500)
        }
    }

    Box(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xF20A0616), Color(0xF507030F))))
    ) {
        MioWallpaper(settings, extraDim = 0.62f)
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            // header
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        DateFormat.format("HH:mm", Date(now)).toString(),
                        style = MioType.clock.copy(fontSize = 34.sp, letterSpacing = (-1).sp),
                    )
                    Text(
                        DateFormat.format("EEE, MMM d", Date(now)).toString() + " · " + (weather?.city ?: settings.city),
                        style = MioType.micro.copy(fontSize = 10.5.sp, color = Color.White.copy(alpha = 0.6f)),
                    )
                }
                Box(
                    Modifier.size(36.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.09f))
                        .bounceClick { MioNav.push(MioScreen.SETTINGS) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Settings, null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier.size(36.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.09f))
                        .bounceClick {
                            if (!SystemControls.lockDevice(context)) MioNav.push(MioScreen.LOCK)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Lock, "Lock", tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(17.dp))
                }
            }

            Spacer(Modifier.height(16.dp))

            // Big connectivity tiles
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BigTile(
                    icon = Icons.Rounded.Wifi,
                    title = "Wi-Fi",
                    subtitle = if (wifiOn) "Connected" else "Off",
                    active = wifiOn,
                    modifier = Modifier.weight(1f),
                ) { SystemControls.open(context, SystemControls.wifiPanel(context)) }
                BigTile(
                    icon = Icons.Rounded.Bluetooth,
                    title = "Bluetooth",
                    subtitle = "Tap to manage",
                    active = false,
                    modifier = Modifier.weight(1f),
                ) { SystemControls.open(context, SystemControls.bluetoothPanel(context)) }
            }

            Spacer(Modifier.height(14.dp))

            // Round toggles
            val toggles = listOf(
                ToggleSpec("Torch", Icons.Rounded.FlashlightOn, torch) {
                    if (!SystemControls.isTorchAvailable(context)) {
                        LauncherHelper.toast(context, "No camera flash on this device")
                    } else if (!SystemControls.toggleTorch(context)) {
                        LauncherHelper.toast(context, "Flashlight is used by another app")
                    }
                },
                ToggleSpec("Airplane", Icons.Rounded.AirplanemodeActive, false) {
                    SystemControls.open(context, SystemControls.airplanePanel(context))
                },
                ToggleSpec("Rotate", Icons.Rounded.ScreenRotation, false) {
                    LauncherHelper.toast(context, "Rotation lock is a system-only setting – opening Display")
                    SystemControls.open(context, SystemControls.displaySettings(context))
                },
                ToggleSpec("DND", Icons.Rounded.DoNotDisturbOn, dnd) {
                    if (!LauncherHelper.dndAccess(context)) {
                        LauncherHelper.toast(context, "Grant Do Not Disturb access to MIO OS")
                        SystemControls.open(context, LauncherHelper.dndAccessIntent(context))
                    } else {
                        SystemControls.toggleDnd(context)
                    }
                },
                ToggleSpec("Cast", Icons.Rounded.Cast, false) {
                    SystemControls.open(context, SystemControls.castSettings(context))
                },
                ToggleSpec("Hotspot", Icons.Rounded.WifiTethering, false) {
                    SystemControls.open(context, SystemControls.hotspotSettings(context))
                },
                ToggleSpec("Night", Icons.Rounded.DarkMode, true) {
                    SystemControls.toggleAppDark(context)
                },
                ToggleSpec("Battery", Icons.Rounded.BatteryChargingFull, battery.charging) {
                    SystemControls.open(context, SystemControls.batterySettings(context))
                },
            )
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                toggles.chunked(4).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        row.forEach { t ->
                            ToggleCircle(t.icon, t.label, t.active) { t.action() }
                        }
                        repeat(4 - row.size) { Spacer(Modifier.width(1.dp)) }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Brightness & volume sliders in glass cards
            GlassCard(Modifier.fillMaxWidth(), corner = 24.dp, alpha = 0.07f) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Brightness6, null, tint = Color.White.copy(alpha = 0.75f), modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(10.dp))
                        ThinSlider(
                            value = brightness,
                            onValueChange = { v ->
                                brightness = v
                                val window = (context as? android.app.Activity)?.window
                                if (window != null) {
                                    SystemControls.setWindowBrightness(window, (v * 255).toInt())
                                }
                                SystemControls.setSystemBrightness(context, (v * 255).toInt())
                            },
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("${(brightness * 100).toInt()}%", style = MioType.micro.copy(fontSize = 10.sp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.VolumeUp, null, tint = Color.White.copy(alpha = 0.75f), modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(10.dp))
                        ThinSlider(
                            value = volume,
                            onValueChange = { v ->
                                volume = v
                                SystemControls.setMediaVolume(
                                    context,
                                    (v * SystemControls.mediaVolumeMax(context)).toInt(),
                                )
                            },
                            modifier = Modifier.weight(1f),
                            active = MioColors.Blue,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("${(volume * 100).toInt()}%", style = MioType.micro.copy(fontSize = 10.sp))
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // music mini card
            GlassCard(Modifier.fillMaxWidth(), corner = 22.dp, alpha = 0.08f) {
                Row(
                    Modifier.padding(12.dp).bounceClick(scaleDown = 0.98f) { MioNav.push(MioScreen.MUSIC) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(38.dp).clip(RoundedCornerShape(11.dp))
                            .background(Brush.linearGradient(listOf(LocalAccent.current, MioColors.PurpleDeep))),
                        contentAlignment = Alignment.Center,
                    ) { MioLogo(Modifier.size(18.dp), Color.White) }
                    Spacer(Modifier.width(11.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            track?.title ?: "MIO Music",
                            style = MioType.title.copy(fontSize = 12.5.sp),
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            track?.artist ?: "Nothing playing",
                            style = MioType.micro.copy(fontSize = 9.5.sp),
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Icon(
                        Icons.Rounded.SkipPrevious, "Previous", tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(20.dp).bounceClick { MioPlayer.previous(context) },
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier.size(30.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.92f))
                            .bounceClick {
                                if (track == null) MioNav.push(MioScreen.MUSIC) else MioPlayer.toggle(context)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            if (playing) "Pause" else "Play",
                            tint = Color(0xFF120C22), modifier = Modifier.size(18.dp),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        Icons.Rounded.SkipNext, "Next", tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(20.dp).bounceClick { MioPlayer.next(context) },
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(
                Modifier.fillMaxWidth().padding(bottom = 22.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Battery ${battery.level}%  ·  ${if (battery.charging) "Charging" else "On battery"}  ·  " +
                        "${battery.temperatureC}°C",
                    style = MioType.micro.copy(fontSize = 10.sp, color = Color.White.copy(alpha = 0.65f)),
                )
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.09f))
                        .bounceClick { MioNav.push(MioScreen.SETTINGS) }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                ) {
                    Text("All settings", style = MioType.micro.copy(color = Color.White, fontSize = 10.sp))
                }
            }
        }
    }
}

private data class ToggleSpec(
    val label: String,
    val icon: ImageVector,
    val active: Boolean,
    val action: () -> Unit,
)

@Composable
private fun BigTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val bg = if (active) {
        Brush.linearGradient(listOf(MioColors.BlueTile, Color(0xFF4F8BFF)))
    } else {
        Brush.linearGradient(listOf(Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.05f)))
    }
    Box(
        modifier.height(94.dp).clip(RoundedCornerShape(24.dp)).background(bg)
            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(24.dp))
            .bounceClick(scaleDown = 0.96f, onClick = onClick)
            .padding(14.dp),
    ) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Icon(icon, null, tint = Color.White.copy(alpha = 0.95f), modifier = Modifier.size(21.dp))
            Column {
                Text(title, style = MioType.title.copy(fontSize = 13.sp), color = Color.White)
                Text(
                    subtitle,
                    style = MioType.micro.copy(fontSize = 9.5.sp, color = Color.White.copy(alpha = 0.7f)),
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun ToggleCircle(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    val accent = LocalAccent.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(58.dp).clip(CircleShape)
                .background(
                    if (active) Brush.linearGradient(listOf(accent.copy(alpha = 0.9f), MioColors.PurpleSoft.copy(alpha = 0.9f)))
                    else Brush.linearGradient(listOf(Color.White.copy(alpha = 0.09f), Color.White.copy(alpha = 0.05f)))
                )
                .border(1.dp, Color.White.copy(alpha = if (active) 0.25f else 0.09f), CircleShape)
                .bounceClick(scaleDown = 0.9f, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, label, tint = Color.White.copy(alpha = 0.95f), modifier = Modifier.size(23.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            style = MioType.micro.copy(fontSize = 9.sp, color = Color.White.copy(alpha = 0.6f)),
        )
    }
}
