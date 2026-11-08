package com.mio.os.ui.apps

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Photo
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import coil.compose.AsyncImage
import com.mio.os.R
import com.mio.os.core.DeviceStats
import com.mio.os.core.Mio
import com.mio.os.core.MioApp
import com.mio.os.core.MioNav
import com.mio.os.ui.components.AppIconView
import com.mio.os.ui.components.GlassCard
import com.mio.os.ui.components.ProgressRing
import com.mio.os.ui.components.SectionHeader
import com.mio.os.ui.components.bounceClick
import com.mio.os.ui.drawer.MioSwitch
import com.mio.os.ui.settings.MioScreenScaffold
import com.mio.os.ui.settings.SettingsPill
import com.mio.os.ui.theme.LocalAccent
import com.mio.os.ui.theme.LocalApps
import com.mio.os.ui.theme.MioColors
import com.mio.os.ui.theme.MioType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/* ------------------------------------------------------------------------- */
/* Files                                                                      */
/* ------------------------------------------------------------------------- */

data class FileBucket(val label: String, val count: Int, val sizeMb: Long, val mime: String)

suspend fun queryBuckets(): List<FileBucket> = withContext(Dispatchers.IO) {
    val buckets = listOf(
        Triple("Images", MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE, "image/*"),
        Triple("Videos", MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO, "video/*"),
        Triple("Audio", MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO, "audio/*"),
        Triple("Documents", 0, "application/*"),
    )
    buckets.map { (label, type, mime) ->
        var count = 0
        var size = 0L
        runCatching {
            val selection = if (label == "Documents") {
                "${MediaStore.Files.FileColumns.MEDIA_TYPE} = ? OR ${MediaStore.Files.FileColumns.MEDIA_TYPE} IS NULL"
            } else {
                "${MediaStore.Files.FileColumns.MEDIA_TYPE} = ?"
            }
            val args = if (label == "Documents") arrayOf("0") else arrayOf(type.toString())
            val cursor = Mio.context.contentResolver.query(
                MediaStore.Files.getContentUri("external"),
                arrayOf(MediaStore.Files.FileColumns.SIZE),
                selection, args, null,
            )
            cursor?.use { c ->
                while (c.moveToNext()) {
                    count++
                    size += c.getLong(0)
                }
            }
        }
        FileBucket(label, count, size / (1024 * 1024), mime)
    }
}

@Composable
fun FilesScreen() {
    val context = LocalContext.current
    val storage = remember { DeviceStats.storage() }
    var buckets by remember { mutableStateOf<List<FileBucket>>(emptyList()) }
    var scanning by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        buckets = queryBuckets()
        scanning = false
    }

    MioScreenScaffold(title = "Files", subtitle = "Storage & media overview") { m ->
        Column(m.padding(bottom = 28.dp)) {
            GlassCard(Modifier.fillMaxWidth(), corner = 24.dp, alpha = 0.08f) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(contentAlignment = Alignment.Center) {
                        ProgressRing(progress = storage.percent / 100f, modifier = Modifier.size(84.dp))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${storage.percent}%", style = MioType.h2.copy(fontSize = 18.sp))
                            Text("used", style = MioType.micro.copy(fontSize = 9.sp))
                        }
                    }
                    Spacer(Modifier.width(18.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Internal storage", style = MioType.title.copy(fontSize = 13.5.sp))
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${storage.usedGb} GB of ${storage.totalGb} GB",
                            style = MioType.body.copy(fontSize = 11.5.sp),
                        )
                        Spacer(Modifier.height(8.dp))
                        Row {
                            SettingsPill("Open Android storage") {
                                runCatching {
                                    context.startActivity(
                                        Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            SectionHeader("Categories")
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxWidth().height(310.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                userScrollEnabled = false,
            ) {
                items(buckets) { b ->
                    val icon = when (b.label) {
                        "Images" -> Icons.Rounded.Photo
                        "Videos" -> Icons.Rounded.Apps
                        "Audio" -> Icons.Rounded.MusicNote
                        else -> Icons.Rounded.Folder
                    }
                    val tint = when (b.label) {
                        "Images" -> MioColors.Green
                        "Videos" -> MioColors.Blue
                        "Audio" -> MioColors.Pink
                        else -> MioColors.Amber
                    }
                    GlassCard(
                        Modifier.fillMaxWidth().bounceClick(scaleDown = 0.97f) {
                            openMime(context, b.mime)
                        },
                        corner = 18.dp, alpha = 0.07f,
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(38.dp).clip(RoundedCornerShape(12.dp))
                                    .background(tint.copy(alpha = 0.18f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
                            }
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(b.label, style = MioType.title.copy(fontSize = 12.5.sp))
                                Text(
                                    if (b.sizeMb > 0) "${b.count} · ${b.sizeMb} MB" else "${b.count} files",
                                    style = MioType.micro.copy(fontSize = 9.5.sp),
                                )
                            }
                        }
                    }
                }
            }

            SectionHeader("Full file access")
            GlassCard(Modifier.fillMaxWidth(), corner = 18.dp, alpha = 0.06f) {
                Column(Modifier.padding(16.dp)) {
                    Text("Why SEGAWA Files is a viewer", style = MioType.title.copy(fontSize = 13.sp))
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Android 11+ blocks raw filesystem browsing unless an app is granted the " +
                            "restricted “All files access” permission. SEGAWA OS deliberately keeps the " +
                            "normal MediaStore permissions and hands deep browsing to your system " +
                            "file manager – that keeps the app store-compliant and battery friendly.",
                        style = MioType.body.copy(fontSize = 11.sp, lineHeight = 16.sp),
                    )
                    Spacer(Modifier.height(12.dp))
                    Row {
                        SettingsPill("All files access", primary = true) {
                            runCatching {
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                        Uri.parse("package:" + context.packageName)
                                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        SettingsPill("Open documents") {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_OPEN_DOCUMENT)
                                        .setType("*/*")
                                        .addCategory(Intent.CATEGORY_OPENABLE)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        }
                    }
                }
            }
            if (scanning) {
                Spacer(Modifier.height(10.dp))
                Text("Scanning media…", style = MioType.micro.copy(fontSize = 10.sp))
            }
            Spacer(Modifier.height(18.dp))
        }
    }
}

private fun openMime(context: android.content.Context, mime: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW).setType(mime).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }.onFailure {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_OPEN_DOCUMENT).setType(mime).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}

/* ------------------------------------------------------------------------- */
/* Gallery                                                                    */
/* ------------------------------------------------------------------------- */

data class GalleryImage(val id: Long, val uri: String, val name: String, val size: Long)

@Composable
fun GalleryScreen() {
    val context = LocalContext.current
    var images by remember { mutableStateOf<List<GalleryImage>>(emptyList()) }
    var preview by remember { mutableStateOf<GalleryImage?>(null) }

    LaunchedEffect(Unit) {
        images = withContext(Dispatchers.IO) {
            val out = ArrayList<GalleryImage>()
            runCatching {
                val proj = arrayOf(
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DISPLAY_NAME,
                    MediaStore.Images.Media.SIZE,
                )
                context.contentResolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, proj, null, null,
                    "${MediaStore.Images.Media.DATE_ADDED} DESC"
                )?.use { c ->
                    while (c.moveToNext() && out.size < 300) {
                        val id = c.getLong(0)
                        out += GalleryImage(
                            id = id,
                            uri = android.content.ContentUris.withAppendedId(
                                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id
                            ).toString(),
                            name = c.getString(1) ?: "Image",
                            size = c.getLong(2),
                        )
                    }
                }
            }
            out
        }
    }

    MioScreenScaffold(title = "Gallery", subtitle = "${images.size} photos", scroll = false) { m ->
        if (images.isEmpty()) {
            Column(m) {
                Spacer(Modifier.height(60.dp))
                com.mio.os.ui.components.EmptyState(
                    "No photos yet",
                    "Grant photos & media access in Settings ▸ Privacy, then your camera roll appears here.",
                )
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    SettingsPill("Grant access", primary = true) {
                        MioNav.push(com.mio.os.core.MioScreen.PRIVACY)
                    }
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = m,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
            ) {
                items(images) { img ->
                    Box(
                        Modifier.aspectRatio(1f).clip(RoundedCornerShape(10.dp))
                            .bounceClick(scaleDown = 0.95f) { preview = img }
                    ) {
                        AsyncImage(
                            model = img.uri, contentDescription = img.name,
                            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }

    preview?.let { img ->
        ModalBottomSheet(
            onDismissRequest = { preview = null },
            containerColor = Color(0xFF0D0918),
            dragHandle = { com.mio.os.ui.components.SheetHandle() },
        ) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Box(
                    Modifier.fillMaxWidth().aspectRatio(0.8f).clip(RoundedCornerShape(20.dp))
                ) {
                    AsyncImage(
                        model = img.uri, contentDescription = img.name,
                        contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize(),
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(img.name, style = MioType.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${img.size / 1024} KB", style = MioType.micro)
                Spacer(Modifier.height(12.dp))
                Row {
                    SettingsPill("Set as wallpaper", primary = true) {
                        WallpaperSetter.setFromUri(context, img.uri)
                        preview = null
                    }
                    Spacer(Modifier.width(8.dp))
                    SettingsPill("Share") {
                        runCatching {
                            context.startActivity(
                                Intent.createChooser(
                                    Intent(Intent.ACTION_SEND).apply {
                                        type = "image/*"
                                        putExtra(Intent.EXTRA_STREAM, Uri.parse(img.uri))
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }, "Share image"
                                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

object WallpaperSetter {
    fun setFromUri(context: android.content.Context, uri: String) {
        runCatching {
            val bmp = context.contentResolver.openInputStream(Uri.parse(uri))?.use {
                android.graphics.BitmapFactory.decodeStream(it)
            }
            if (bmp != null) {
                android.app.WallpaperManager.getInstance(context).setBitmap(bmp)
                com.mio.os.core.LauncherHelper.toast(context, "Wallpaper applied to Android")
            }
        }.onFailure {
            com.mio.os.core.LauncherHelper.toast(context, "Could not set the wallpaper")
        }
    }

    fun setFromDrawable(context: android.content.Context, resId: Int) {
        runCatching {
            val drawable = androidx.core.content.res.ResourcesCompat.getDrawable(context.resources, resId, null)
            val bmp = drawable?.toBitmap(1080, 1920)
            if (bmp != null) {
                android.app.WallpaperManager.getInstance(context).setBitmap(bmp)
                com.mio.os.core.LauncherHelper.toast(context, "Wallpaper applied to Android")
            }
        }.onFailure {
            com.mio.os.core.LauncherHelper.toast(context, "Could not set the wallpaper")
        }
    }
}

/* ------------------------------------------------------------------------- */
/* Hidden apps                                                               */
/* ------------------------------------------------------------------------- */

@Composable
fun HiddenAppsScreen() {
    val apps = LocalApps.current
    val hidden = com.mio.os.ui.theme.LocalSettings.current.hiddenApps
    var query by remember { mutableStateOf("") }
    val filtered = remember(apps, query) {
        apps.filter { query.isBlank() || it.name.contains(query, true) }
    }

    MioScreenScaffold(title = "App management", subtitle = "${apps.size} apps · ${hidden.size} hidden", scroll = false) { m ->
        Column(m) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.08f))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Search, null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(10.dp))
                androidx.compose.foundation.text.BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = MioType.title.copy(fontSize = 12.5.sp),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(LocalAccent.current),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(10.dp))
            LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)) {
                items(filtered) { app ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppIconView(app, 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(app.name, style = MioType.title.copy(fontSize = 13.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                app.category.label + if (app.isSystem) " · system" else "",
                                style = MioType.micro.copy(fontSize = 9.5.sp),
                            )
                        }
                        MioSwitch(
                            checked = app.packageName in hidden,
                            onCheckedChange = { hide -> Mio.prefs.setHidden(app.packageName, hide) },
                        )
                    }
                }
            }
        }
    }
}

/* ------------------------------------------------------------------------- */
/* About                                                                      */
/* ------------------------------------------------------------------------- */

@Composable
fun AboutScreen() {
    val context = LocalContext.current
    val info = remember { DeviceStats.info() }
    val storage = remember { DeviceStats.storage() }
    val ram = remember { DeviceStats.ram(context) }
    val apps = LocalApps.current

    MioScreenScaffold(title = "About SEGAWA OS", subtitle = "v1.1.0 (Hiro)") { m ->
        Column(m.padding(bottom = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier.size(96.dp).clip(RoundedCornerShape(28.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(28.dp)),
            ) {
                Image(
                    painter = painterResource(R.drawable.segawa_mark),
                    contentDescription = "SEGAWA OS brand mark",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Spacer(Modifier.height(12.dp))
            Text("SEGAWA OS", style = MioType.h1.copy(fontSize = 24.sp, letterSpacing = 1.sp))
            Text("v1.1.0 (Hiro)", style = MioType.micro.copy(fontSize = 10.5.sp))
            Spacer(Modifier.height(2.dp))
            Text(
                "Your Anime. Your World. Your OS.",
                style = MioType.body.copy(fontSize = 11.sp, color = MioColors.Pink),
            )
            Spacer(Modifier.height(4.dp))
            Text("By Raju Mahato · RJNX / Devkazzu", style = MioType.body.copy(fontSize = 10.5.sp))

            Spacer(Modifier.height(20.dp))
            GlassCard(Modifier.fillMaxWidth(), corner = 20.dp, alpha = 0.06f) {
                Column(Modifier.padding(vertical = 6.dp)) {
                    AboutRow("Device", "${info.manufacturer} ${info.model}")
                    AboutRow("Android", "${info.android} (API ${info.sdk})")
                    AboutRow("Processor", info.soc)
                    AboutRow("Cores", "${info.cores}")
                    AboutRow("RAM", "${ram.usedMb} MB / ${ram.totalMb} MB (${ram.percent}%)")
                    AboutRow("Storage", "${storage.usedGb} GB / ${storage.totalGb} GB")
                    AboutRow("Launcher apps", "${apps.size}")
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                StatBlock("RAM", "${ram.percent}%", Modifier.weight(1f), MioColors.Blue)
                Spacer(Modifier.width(10.dp))
                StatBlock("Storage", "${storage.percent}%", Modifier.weight(1f), MioColors.Green)
            }

            Spacer(Modifier.height(16.dp))
            GlassCard(Modifier.fillMaxWidth(), corner = 20.dp, alpha = 0.06f) {
                Column(Modifier.padding(16.dp)) {
                    Text("Screen time · last 24 h", style = MioType.title.copy(fontSize = 13.sp))
                    Spacer(Modifier.height(6.dp))
                    if (!DeviceStats.hasUsageAccess(context)) {
                        Text(
                            "See which apps you actually use. SEGAWA OS reads this only on-device, " +
                                "after you grant Usage Access in system settings.",
                            style = MioType.body.copy(fontSize = 11.sp, lineHeight = 16.sp),
                        )
                        Spacer(Modifier.height(10.dp))
                        Row {
                            SettingsPill("Grant usage access") {
                                runCatching {
                                    context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                                }.onFailure {
                                    com.mio.os.core.LauncherHelper.toast(context, "Could not open usage settings")
                                }
                            }
                        }
                    } else {
                        val top = remember { DeviceStats.topUsage(context) }
                        if (top.isEmpty()) {
                            Text(
                                "No screen time recorded yet — check back later.",
                                style = MioType.body.copy(fontSize = 11.sp),
                            )
                        } else {
                            top.forEach { (pkg, min) ->
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        pkg.substringAfterLast('.').take(26),
                                        style = MioType.body.copy(fontSize = 11.5.sp),
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        if (min < 60) "${min}m" else "${min / 60}h ${min % 60}m",
                                        style = MioType.title.copy(
                                            fontSize = 11.5.sp,
                                            color = Color.White.copy(alpha = 0.9f),
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            GlassCard(Modifier.fillMaxWidth(), corner = 20.dp, alpha = 0.06f) {
                Column(Modifier.padding(16.dp)) {
                    Text("Open & transparent", style = MioType.title.copy(fontSize = 13.sp))
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "SEGAWA OS is a normal, unrooted Android launcher: Kotlin + Jetpack Compose, " +
                            "no analytics, no hidden services. System-level features that require " +
                            "privileged permissions are clearly labelled and hand off to the " +
                            "official Android settings panels.",
                        style = MioType.body.copy(fontSize = 11.sp, lineHeight = 16.sp),
                    )
                    Spacer(Modifier.height(12.dp))
                    Row {
                        SettingsPill("Default launcher settings") {
                            com.mio.os.ui.settings.requestDefault(context)
                        }
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun AboutRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 9.dp)) {
        Text(label, style = MioType.body.copy(fontSize = 11.5.sp), modifier = Modifier.width(96.dp))
        Text(
            value,
            style = MioType.title.copy(fontSize = 11.5.sp, color = Color.White.copy(alpha = 0.9f)),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StatBlock(label: String, value: String, modifier: Modifier, color: Color) {
    GlassCard(modifier, corner = 18.dp, alpha = 0.06f) {
        Column(Modifier.padding(14.dp)) {
            Text(label, style = MioType.micro.copy(fontSize = 9.5.sp))
            Spacer(Modifier.height(4.dp))
            Text(value, style = MioType.h2.copy(fontSize = 20.sp, color = color))
        }
    }
}
