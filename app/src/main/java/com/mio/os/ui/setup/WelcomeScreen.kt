package com.mio.os.ui.setup

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mio.os.core.LauncherHelper
import com.mio.os.core.Mio
import com.mio.os.core.SegaBrand
import com.mio.os.core.MioNav
import com.mio.os.core.MioScreen
import com.mio.os.ui.components.AmbientOrb
import com.mio.os.ui.components.bounceClick
import com.mio.os.ui.components.GlassCard
import com.mio.os.ui.components.GradientPillButton
import com.mio.os.ui.components.SegaLogo
import com.mio.os.ui.components.MioWallpaper
import com.mio.os.ui.components.rememberIsDefaultLauncher
import com.mio.os.ui.theme.LocalAccent
import com.mio.os.ui.theme.LocalSettings
import com.mio.os.ui.theme.MioColors
import com.mio.os.ui.theme.MioType

@Composable
fun WelcomeScreen() {
    val context = LocalContext.current
    val settings = LocalSettings.current
    val isDefault by rememberIsDefaultLauncher()
    var step by remember { mutableStateOf(0) }

    var name by remember { mutableStateOf(settings.userName) }
    var city by remember { mutableStateOf(settings.city) }

    val roleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    fun requestDefault() {
        val role = LauncherHelper.roleRequestIntent(context)
        if (role != null) {
            roleLauncher.launch(role)
        } else {
            LauncherHelper.launch(context, LauncherHelper.homeSettingsIntent(context))
                ?: LauncherHelper.launch(context, LauncherHelper.chooserIntent())
        }
    }

    fun requestPermissions() {
        val list = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list += Manifest.permission.POST_NOTIFICATIONS
            list += Manifest.permission.READ_MEDIA_IMAGES
            list += Manifest.permission.READ_MEDIA_AUDIO
            list += Manifest.permission.READ_MEDIA_VIDEO
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            list += Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (list.isNotEmpty()) permLauncher.launch(list.toTypedArray())
    }

    Box(Modifier.fillMaxSize()) {
        MioWallpaper(settings, extraDim = 0.15f)

        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 26.dp)
        ) {
            Spacer(Modifier.height(28.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                SegaLogo(Modifier.size(30.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    "SEGAWA OS",
                    style = MioType.h2.copy(letterSpacing = 3.sp, fontWeight = FontWeight.Light),
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.10f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text("v" + SegaBrand.VERSION, style = MioType.micro.copy(fontSize = 9.sp))
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Your Anime. Your World. Your OS.",
                style = MioType.micro.copy(fontSize = 10.sp, letterSpacing = 1.sp),
            )

            Spacer(Modifier.height(26.dp))

            AnimatedContent(
                targetState = step,
                transitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(120)) },
                label = "setup",
            ) { s ->
                when (s) {
                    0 -> WelcomeStep()
                    1 -> DefaultLauncherStep(isDefault, onSetDefault = { requestDefault() })
                    2 -> PermissionsStep(onGrant = { requestPermissions() })
                    else -> ProfileStep(name, city, onName = { name = it }, onCity = { city = it })
                }
            }

            Spacer(Modifier.height(26.dp))

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (step > 0) {
                    Box(
                        Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.08f))
                            .bounceClick(scaleDown = 0.96f) { step -= 1 }
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) { Text("Back", style = MioType.title.copy(fontSize = 13.sp)) }
                }
                Spacer(Modifier.weight(1f))
                GradientPillButton(
                    text = when (step) {
                        0 -> "Get started"
                        1 -> if (isDefault) "Continue" else "Set as default"
                        2 -> "Allow"
                        else -> "Enter SEGAWA OS"
                    },
                ) {
                    when (step) {
                        0 -> step = 1
                        1 -> if (isDefault) step = 2 else requestDefault()
                        2 -> { requestPermissions(); step = 3 }
                        else -> {
                            Mio.prefs.edit {
                                it.copy(
                                    onboarded = true,
                                    userName = name.ifBlank { "Creator" },
                                    city = city.ifBlank { "Silchar" },
                                )
                            }
                            MioNav.home()
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                repeat(4) { i ->
                    Box(
                        Modifier.padding(horizontal = 3.dp).height(5.dp)
                            .width(if (i == step) 20.dp else 6.dp)
                            .clip(CircleShape)
                            .background(if (i == step) LocalAccent.current else Color.White.copy(alpha = 0.22f))
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            Text(
                "Skip setup",
                style = MioType.micro.copy(fontSize = 11.sp, color = Color.White.copy(alpha = 0.45f)),
                modifier = Modifier.align(Alignment.CenterHorizontally).bounceClick {
                    Mio.prefs.edit { it.copy(onboarded = true) }
                    MioNav.home()
                },
            )
            Spacer(Modifier.height(26.dp))
        }
    }
}

@Composable
private fun WelcomeStep() {
    Column {
        AmbientOrb(Modifier.size(120.dp))
        Spacer(Modifier.height(18.dp))
        Text(
            "A next-gen mobile\noperating experience",
            style = MioType.h1.copy(fontSize = 30.sp, lineHeight = 36.sp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "A fast, beautiful and customisable home screen with smart AI, " +
                "music, gaming and productivity tools – built for creators, " +
                "students and power users.",
            style = MioType.body.copy(fontSize = 13.sp, lineHeight = 20.sp),
        )
        Spacer(Modifier.height(20.dp))
        val features = listOf(
            "AI powered" to "Segawa assistant",
            "Highly customisable" to "Themes & icons",
            "Minimal & fluid" to "60fps motion",
        )
        features.forEach { (a, b) ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(26.dp).clip(CircleShape)
                        .background(LocalAccent.current.copy(alpha = 0.20f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Check, null, tint = LocalAccent.current, modifier = Modifier.size(14.dp))
                }
                Spacer(Modifier.width(10.dp))
                Text(a, style = MioType.title.copy(fontSize = 13.sp))
                Text(" · " + b, style = MioType.body.copy(fontSize = 12.sp))
            }
        }
    }
}

@Composable
private fun DefaultLauncherStep(isDefault: Boolean, onSetDefault: () -> Unit) {
    val context = LocalContext.current
    Column {
        Text("Make SEGAWA OS your\nhome screen", style = MioType.h1.copy(fontSize = 28.sp, lineHeight = 34.sp))
        Spacer(Modifier.height(10.dp))
        Text(
            "SEGAWA OS is a real Android launcher. Android asks once which app should " +
                "open when you press the Home button – choose SEGAWA OS to use it every day.",
            style = MioType.body.copy(fontSize = 13.sp, lineHeight = 20.sp),
        )
        Spacer(Modifier.height(18.dp))

        GlassCard(Modifier.fillMaxWidth(), corner = 20.dp, alpha = 0.09f) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(34.dp).clip(CircleShape)
                        .background(
                            if (isDefault) MioColors.Green.copy(alpha = 0.22f)
                            else Color.White.copy(alpha = 0.10f)
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.Check, null,
                        tint = if (isDefault) MioColors.Green else Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (isDefault) "SEGAWA OS is your home app" else "SEGAWA OS is not the default home app",
                        style = MioType.title.copy(fontSize = 13.5.sp),
                    )
                    Text(
                        if (isDefault) "Press Home any time to return here"
                        else "Tap the button below, then choose “Always”",
                        style = MioType.micro.copy(fontSize = 10.5.sp),
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        if (!isDefault) {
            GlassCard(Modifier.fillMaxWidth(), corner = 18.dp, alpha = 0.06f) {
                Column(Modifier.padding(16.dp)) {
                    Text("How to select SEGAWA OS", style = MioType.title.copy(fontSize = 13.sp))
                    Spacer(Modifier.height(8.dp))
                    listOf(
                        "1. Tap “Set as default” – Android opens its own Home app chooser.",
                        "2. Pick SEGAWA OS in the list.",
                        "3. Choose “Always” (Android 10+) or “Set as default”.",
                        "4. Press the Home button – SEGAWA OS opens instantly.",
                    ).forEach {
                        Text(
                            it,
                            style = MioType.body.copy(fontSize = 11.5.sp, lineHeight = 18.sp),
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row {
                SmallAction("Open Home settings") {
                    LauncherHelper.launch(context, LauncherHelper.homeSettingsIntent(context))
                }
                Spacer(Modifier.width(8.dp))
                SmallAction("Check again") { onSetDefault() }
            }
        }
    }
}

@Composable
private fun PermissionsStep(onGrant: () -> Unit) {
    Column {
        Text("Grant what you need", style = MioType.h1.copy(fontSize = 28.sp))
        Spacer(Modifier.height(10.dp))
        Text(
            "SEGAWA OS only asks for access it actually uses. Nothing leaves your device " +
                "unless you connect your own AI endpoint.",
            style = MioType.body.copy(fontSize = 13.sp, lineHeight = 20.sp),
        )
        Spacer(Modifier.height(18.dp))
        PermissionRow("Photos & media", "Wallpapers, gallery and local music")
        PermissionRow("Notifications", "Media controls and unlock alerts")
        PermissionRow("Notification access (optional)", "Real app badges on the home screen")
        Spacer(Modifier.height(10.dp))
        Text(
            "You can change any of this later in Settings ▸ Privacy.",
            style = MioType.micro.copy(fontSize = 10.5.sp),
        )
    }
}

@Composable
private fun PermissionRow(title: String, subtitle: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(30.dp).clip(RoundedCornerShape(10.dp))
                .background(LocalAccent.current.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Settings, null, tint = LocalAccent.current, modifier = Modifier.size(15.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MioType.title.copy(fontSize = 13.sp))
            Text(subtitle, style = MioType.micro.copy(fontSize = 10.5.sp))
        }
    }
}

@Composable
private fun ProfileStep(
    name: String,
    city: String,
    onName: (String) -> Unit,
    onCity: (String) -> Unit,
) {
    Column {
        Text("Make it yours", style = MioType.h1.copy(fontSize = 28.sp))
        Spacer(Modifier.height(10.dp))
        Text(
            "Your name appears in the greeting; the city drives the live weather widget.",
            style = MioType.body.copy(fontSize = 13.sp, lineHeight = 20.sp),
        )
        Spacer(Modifier.height(18.dp))
        MioTextField(value = name, onValueChange = onName, label = "Your name")
        Spacer(Modifier.height(12.dp))
        MioTextField(value = city, onValueChange = onCity, label = "City for weather")
        Spacer(Modifier.height(16.dp))
        Text(
            "Weather is powered by the free Open-Meteo API and needs no account.",
            style = MioType.micro.copy(fontSize = 10.5.sp),
        )
    }
}

@Composable
fun MioTextField(value: String, onValueChange: (String) -> Unit, label: String) {
    val accent = LocalAccent.current
    Column(Modifier.fillMaxWidth()) {
        Text(label.uppercase(), style = MioType.micro.copy(fontSize = 9.5.sp, letterSpacing = 1.2.sp))
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(alpha = 0.07f))
                .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 13.dp)
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MioType.title.copy(fontSize = 14.sp),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SmallAction(text: String, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.08f))
            .bounceClick(scaleDown = 0.96f, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp)
    ) {
        Text(text, style = MioType.micro.copy(fontSize = 10.5.sp, color = Color.White.copy(alpha = 0.85f)))
    }
}
