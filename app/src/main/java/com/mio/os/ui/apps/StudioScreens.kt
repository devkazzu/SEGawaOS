package com.mio.os.ui.apps

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mio.os.core.IconProfiles
import com.mio.os.core.IconStudio
import com.mio.os.core.LauncherHelper
import com.mio.os.core.Mio
import com.mio.os.ui.components.AppIconView
import com.mio.os.ui.components.GlassCard
import com.mio.os.ui.components.SectionHeader
import com.mio.os.ui.components.bounceClick
import com.mio.os.ui.settings.MioScreenScaffold
import com.mio.os.ui.settings.SettingsPill
import com.mio.os.ui.setup.MioTextField
import com.mio.os.ui.theme.LocalAccent
import com.mio.os.ui.theme.LocalApps
import com.mio.os.ui.theme.LocalSettings
import com.mio.os.ui.theme.MioType

/** Deep-link target: quick sheets set this before opening the Icon Studio. */
object IconStudioRequest {
    var pkg: String? = null
}

/**
 * Icon Studio: named icon profiles (shape + size + background) plus per-app
 * custom images picked from storage. Everything renders through [AppIconView],
 * so home, drawer, dock and folders stay consistent.
 */
@Composable
fun IconStudioScreen() {
    val context = LocalContext.current
    val settings = LocalSettings.current
    val apps = LocalApps.current
    var query by remember { mutableStateOf("") }
    var target by remember { mutableStateOf(IconStudioRequest.pkg.also { IconStudioRequest.pkg = null }) }
    val custom = remember(settings.customIcons) { IconStudio.decode(settings.customIcons) }
    val pickIcon = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val pkg = target
        if (uri != null && pkg != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            val cur = IconStudio.decode(Mio.prefs.current.customIcons) + (pkg to uri.toString())
            Mio.prefs.edit { it.copy(customIcons = IconStudio.encode(cur)) }
            LauncherHelper.toast(context, "Custom icon saved")
        }
        target = null
    }
    val filtered = remember(apps, query) {
        if (query.isBlank()) apps
        else apps.filter { it.name.contains(query, true) || it.packageName.contains(query, true) }
    }

    MioScreenScaffold(title = "Icon studio", subtitle = "Profiles & per-app icons") { m ->
        Column(m.padding(bottom = 26.dp)) {
            SectionHeader("Profiles")
            IconProfiles.forEach { p ->
                val selected = settings.iconProfile == p.id
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                        .background(
                            if (selected) LocalAccent.current.copy(alpha = 0.14f)
                            else Color.White.copy(alpha = 0.04f)
                        )
                        .bounceClick(scaleDown = 0.98f) {
                            Mio.prefs.edit { it.copy(iconProfile = p.id, iconShape = p.shape, iconScale = p.scale) }
                        }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(34.dp).clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        LocalAccent.current.copy(alpha = 0.35f + p.bgAlpha * 0.65f),
                                        LocalAccent.current.copy(alpha = 0.12f),
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("S", style = MioType.title.copy(fontSize = 14.sp, color = Color.White))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            (if (selected) "✓ " else "") + p.name,
                            style = MioType.title.copy(fontSize = 13.sp),
                        )
                        Text(
                            "${p.shape} · ${(p.scale * 100).toInt()}% · bg ${(p.bgAlpha * 100).toInt()}%",
                            style = MioType.micro.copy(fontSize = 10.sp),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            SectionHeader("Live preview")
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                apps.take(6).forEach { AppIconView(it, 44.dp) }
            }

            SectionHeader("Per-app icons (${custom.size} custom)")
            MioTextField(value = query, onValueChange = { query = it }, label = "Search apps")
            Spacer(Modifier.height(10.dp))
            LazyColumn(Modifier.fillMaxWidth().height(300.dp)) {
                items(filtered, key = { it.key }) { app ->
                    val hasCustom = app.packageName in custom
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                            .bounceClick(scaleDown = 0.98f) {
                                target = app.packageName
                                pickIcon.launch(arrayOf("image/*"))
                            }
                            .padding(vertical = 7.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppIconView(app, 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                app.name,
                                style = MioType.title.copy(fontSize = 12.5.sp),
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                if (hasCustom) "Custom icon ✓ – tap to change" else "Original icon – tap to customize",
                                style = MioType.micro.copy(fontSize = 9.5.sp),
                                maxLines = 1,
                            )
                        }
                        if (hasCustom) {
                            SettingsPill("Restore") {
                                Mio.prefs.edit {
                                    it.copy(customIcons = IconStudio.encode(IconStudio.decode(it.customIcons) - app.packageName))
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row {
                SettingsPill("Restore all originals") {
                    Mio.prefs.edit { it.copy(customIcons = "") }
                }
            }
            Spacer(Modifier.height(14.dp))
            GlassCard(Modifier.fillMaxWidth(), corner = 18.dp, alpha = 0.06f) {
                Column(Modifier.padding(16.dp)) {
                    Text("About third-party packs", style = MioType.title.copy(fontSize = 13.sp))
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Android exposes no public icon-pack API, so SEGAWA cannot import Nova-style " +
                            "packs directly. Profiles plus per-app images cover the same ground: every " +
                            "icon on home, drawer, dock and folders follows your studio choices.",
                        style = MioType.body.copy(fontSize = 11.sp, lineHeight = 16.sp),
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
        }
    }
}
