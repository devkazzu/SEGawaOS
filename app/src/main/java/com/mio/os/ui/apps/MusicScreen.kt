package com.mio.os.ui.apps

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mio.os.core.LauncherHelper
import com.mio.os.core.LrcLine
import com.mio.os.core.Lyrics
import com.mio.os.core.Mio
import com.mio.os.core.Playlists
import com.mio.os.core.MioEqualizer
import com.mio.os.core.MioNav
import com.mio.os.core.MioPlayer
import com.mio.os.core.Track
import com.mio.os.ui.components.GlassCard
import com.mio.os.ui.components.MioLogo
import com.mio.os.ui.components.MioWallpaper
import com.mio.os.ui.components.ThinSlider
import com.mio.os.ui.components.bounceClick
import com.mio.os.ui.setup.MioTextField
import com.mio.os.ui.theme.LocalAccent
import com.mio.os.ui.theme.LocalSettings
import com.mio.os.ui.theme.MioColors
import com.mio.os.ui.theme.MioType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun MusicScreen() {
    val context = LocalContext.current
    val settings = LocalSettings.current
    val accent = LocalAccent.current
    val tracksFlow = Mio.media.tracks.collectAsState()
    val current by MioPlayer.current.collectAsState()
    val playing by MioPlayer.playing.collectAsState()
    val position by MioPlayer.position.collectAsState()
    val duration by MioPlayer.duration.collectAsState()

    var showEq by remember { mutableStateOf(false) }
    var seeking by remember { mutableFloatStateOf(-1f) }
    var musicTab by remember { mutableStateOf(0) }
    var sleepMin by remember { mutableIntStateOf(0) }
    var sleepLeft by remember { mutableStateOf("") }
    var addFor by remember { mutableStateOf<Track?>(null) }
    var lrcLines by remember { mutableStateOf<List<LrcLine>>(emptyList()) }
    var lrcName by remember { mutableStateOf("") }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { Mio.media }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) {
            val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                context, Manifest.permission.READ_MEDIA_AUDIO
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) permissionLauncher.launch(Manifest.permission.READ_MEDIA_AUDIO)
        }
        Mio.media.load()
    }

    val lrcLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
        }.getOrNull()
        val parsed = if (text.isNullOrBlank()) emptyList() else Lyrics.parse(text)
        if (parsed.isEmpty()) {
            LauncherHelper.toast(context, "No timed lines found in that file")
        } else {
            lrcLines = parsed
            lrcName = uri.lastPathSegment?.substringAfterLast('/')?.take(40) ?: "lyrics.lrc"
            musicTab = 2
        }
    }

    LaunchedEffect(sleepMin) {
        if (sleepMin > 0) {
            val end = System.currentTimeMillis() + sleepMin * 60_000L
            while (System.currentTimeMillis() < end) {
                val s = ((end - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
                sleepLeft = "%d:%02d".format(s / 60, s % 60)
                delay(1000)
            }
            MioPlayer.pause()
            sleepMin = 0
        } else {
            sleepLeft = ""
        }
    }

    val library = remember(tracksFlow.value) { Mio.media.tracksWithDemo() }
    val demo = remember { Mio.media.demoTrack }

    Box(Modifier.fillMaxSize()) {
        MioWallpaper(settings, extraDim = 0.6f)
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp)) {

            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(38.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.09f))
                        .bounceClick { MioNav.pop() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.ArrowBack, "Back", tint = Color.White, modifier = Modifier.size(19.dp))
                }
                Spacer(Modifier.weight(1f))
                Text("Music", style = MioType.title.copy(fontSize = 14.sp, letterSpacing = 1.2.sp))
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.size(38.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.09f))
                        .bounceClick { showEq = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Equalizer, "Equalizer", tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(Modifier.height(12.dp))

            // Artwork
            Box(
                Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(30.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.linearGradient(listOf(accent.copy(alpha = 0.55f), MioColors.PurpleDeep, Color(0xFF0B0716)))
                    )
                )
                val art = remember(current?.albumId) { current?.let { Mio.media.albumArt(it) } }
                if (art != null) {
                    Image(
                        bitmap = art, contentDescription = null,
                        contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        MioLogo(Modifier.size(64.dp), Color.White.copy(alpha = 0.9f))
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "SEGAWA OS Sessions",
                            style = MioType.micro.copy(fontSize = 10.sp, color = Color.White.copy(alpha = 0.7f)),
                        )
                    }
                }
                val favId = (current ?: demo).id.toString()
                val isFav = favId in settings.favTracks
                Box(
                    Modifier.align(Alignment.TopEnd).padding(12.dp).size(32.dp).clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.35f))
                        .bounceClick {
                            Mio.prefs.edit {
                                it.copy(favTracks = if (isFav) it.favTracks - favId else it.favTracks + favId)
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.Favorite, "Favorite",
                        tint = if (isFav) Color(0xFFFF5C7A) else Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            val shown = current ?: demo
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        shown.title,
                        style = MioType.h2.copy(fontSize = 19.sp),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        shown.artist,
                        style = MioType.body.copy(fontSize = 11.5.sp),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(Icons.Rounded.MoreVert, null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
            }

            Spacer(Modifier.height(14.dp))

            val total = if (duration > 0) duration else shown.durationMs
            val progress = when {
                seeking >= 0f -> seeking
                total > 0 -> (position.toFloat() / total).coerceIn(0f, 1f)
                else -> 0f
            }
            ThinSlider(
                value = progress,
                onValueChange = { f -> seeking = f },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.fillMaxWidth()) {
                Text(fmt(if (seeking >= 0f) (seeking * total).toLong() else position), style = MioType.micro.copy(fontSize = 9.5.sp))
                Spacer(Modifier.weight(1f))
                Text(fmt(total), style = MioType.micro.copy(fontSize = 9.5.sp))
            }
            Spacer(Modifier.height(6.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ControlButton(Icons.Rounded.SkipPrevious, 26.dp) { MioPlayer.previous(context) }
                Spacer(Modifier.width(26.dp))
                Box(
                    Modifier.size(66.dp).clip(CircleShape)
                        .background(Brush.linearGradient(listOf(Color.White, Color.White.copy(alpha = 0.86f))))
                        .bounceClick(scaleDown = 0.92f) {
                            if (seeking >= 0f) {
                                MioPlayer.seekTo((seeking * total).toLong())
                                seeking = -1f
                            }
                            if (current == null) {
                                MioPlayer.enqueue(context, shown, library)
                            } else {
                                MioPlayer.toggle(context)
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        if (playing) "Pause" else "Play",
                        tint = Color(0xFF0C0818), modifier = Modifier.size(32.dp),
                    )
                }
                Spacer(Modifier.width(26.dp))
                ControlButton(Icons.Rounded.SkipNext, 26.dp) { MioPlayer.next(context) }
            }

            Spacer(Modifier.height(18.dp))

            MusicTabs(musicTab) { musicTab = it }
            Spacer(Modifier.height(8.dp))
            when (musicTab) {
                0 -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (tracksFlow.value.isEmpty()) "DEMO TRACK — add music to your device to build a library" else "UP NEXT",
                            style = MioType.micro.copy(fontSize = 9.sp, letterSpacing = 1.4.sp),
                            modifier = Modifier.weight(1f),
                        )
                        SleepPill(sleepMin, sleepLeft) {
                            sleepMin = when (sleepMin) {
                                0 -> 15
                                15 -> 30
                                30 -> 60
                                else -> 0
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(Modifier.weight(1f)) {
                        items(library) { track ->
                            TrackRow(
                                track = track,
                                selected = track.id == current?.id,
                                playing = playing && track.id == current?.id,
                                onClick = { MioPlayer.enqueue(context, track, library) },
                                onAdd = { addFor = track },
                            )
                        }
                    }
                }
                1 -> PlaylistsTab(library, Modifier.weight(1f))
                else -> LyricsTab(
                    track = current ?: demo,
                    position = position,
                    lines = lrcLines,
                    fileName = lrcName,
                    modifier = Modifier.weight(1f),
                    onLoad = { lrcLauncher.launch(arrayOf("text/plain", "*/*")) },
                )
            }
        }
    }

    if (showEq) {
        EqualizerSheet(onDismiss = { showEq = false })
    }

    addFor?.let { track ->
        AddToPlaylistSheet(
            track = track,
            onDismiss = { addFor = null },
        )
    }
}

private fun fmt(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    return String.format(Locale.US, "%d:%02d", totalSec / 60, totalSec % 60)
}

@Composable
private fun ControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    size: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
) {
    Icon(
        icon, null, tint = Color.White.copy(alpha = 0.9f),
        modifier = Modifier.size(size).bounceClick(scaleDown = 0.9f, onClick = onClick),
    )
}

@Composable
private fun TrackRow(track: Track, selected: Boolean, playing: Boolean, onClick: () -> Unit, onAdd: (() -> Unit)? = null) {
    val accent = LocalAccent.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .bounceClick(scaleDown = 0.98f, onClick = onClick)
            .padding(vertical = 9.dp, horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(38.dp).clip(RoundedCornerShape(11.dp))
                .background(
                    if (selected) accent.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.07f)
                ),
            contentAlignment = Alignment.Center,
        ) {
            val art = remember(track.id) { runCatching { Mio.media.albumArt(track) }.getOrNull() }
            if (art != null) {
                Image(bitmap = art, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                MioLogo(Modifier.size(16.dp), Color.White.copy(alpha = 0.8f))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                style = MioType.title.copy(fontSize = 12.5.sp, color = if (selected) accent else MioColors.Text),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                track.artist,
                style = MioType.micro.copy(fontSize = 9.5.sp),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Text(fmt(track.durationMs), style = MioType.micro.copy(fontSize = 9.5.sp))
        if (onAdd != null) {
            Text(
                "+",
                style = MioType.title.copy(fontSize = 17.sp, color = accent),
                modifier = Modifier.bounceClick(onClick = onAdd).padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
            )
        }
    }
}

@Composable
private fun EqualizerSheet(onDismiss: () -> Unit) {
    val bands = remember { MioEqualizer.bands() }
    var levels by remember { mutableStateOf(bands.map { it.levelMb.toFloat() }) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0D0918),
        dragHandle = { com.mio.os.ui.components.SheetHandle() },
    ) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding()) {
            Text("Equalizer", style = MioType.h2)
            Spacer(Modifier.height(6.dp))
            Text(
                "Applied through android.media.audiofx to the current playback session.",
                style = MioType.body.copy(fontSize = 11.sp),
            )
            Spacer(Modifier.height(14.dp))
            if (bands.isEmpty()) {
                GlassCard(Modifier.fillMaxWidth(), corner = 16.dp, alpha = 0.07f) {
                    Text(
                        "The equalizer becomes available while a track is playing on a device that exposes audio effects.",
                        style = MioType.body.copy(fontSize = 11.5.sp),
                        modifier = Modifier.padding(16.dp),
                    )
                }
            } else {
                Row(
                    Modifier.fillMaxWidth().height(170.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    bands.forEachIndexed { i, band ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                            Box(
                                Modifier.height(110.dp).width(26.dp).clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.07f)),
                                contentAlignment = Alignment.BottomCenter,
                            ) {
                                val range = (band.max - band.min).coerceAtLeast(1)
                                val frac = ((levels.getOrElse(i) { 0f } - band.min) / range).coerceIn(0f, 1f)
                                Box(
                                    Modifier.fillMaxWidth().fillMaxSize(frac).clip(CircleShape)
                                        .background(LocalAccent.current.copy(alpha = 0.8f))
                                        .bounceClick(scaleDown = 0.98f) { }
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(band.label, style = MioType.micro.copy(fontSize = 8.sp))
                            Row {
                                EqStep("−") {
                                    val next = levels.toMutableList()
                                    next[i] = (next[i] - 200f).coerceAtLeast(band.min.toFloat())
                                    levels = next
                                    MioEqualizer.setBandLevel(i, next[i].toInt())
                                }
                                Spacer(Modifier.width(4.dp))
                                EqStep("+") {
                                    val next = levels.toMutableList()
                                    next[i] = (next[i] + 200f).coerceAtMost(band.max.toFloat())
                                    levels = next
                                    MioEqualizer.setBandLevel(i, next[i].toInt())
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun EqStep(label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(20.dp).clip(RoundedCornerShape(6.dp)).background(Color.White.copy(alpha = 0.08f))
            .bounceClick(scaleDown = 0.9f, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MioType.micro.copy(fontSize = 10.sp, color = Color.White))
    }
}

@Composable
private fun MusicTabs(selected: Int, onSelect: (Int) -> Unit) {
    val accent = LocalAccent.current
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("Songs", "Playlists", "Lyrics").forEachIndexed { i, label ->
            Box(
                Modifier.clip(RoundedCornerShape(50))
                    .background(if (selected == i) accent.copy(alpha = 0.30f) else Color.White.copy(alpha = 0.07f))
                    .bounceClick(scaleDown = 0.95f) { onSelect(i) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MioType.micro.copy(
                        fontSize = 10.5.sp,
                        color = if (selected == i) Color.White else Color.White.copy(alpha = 0.6f),
                    ),
                )
            }
        }
    }
}

@Composable
private fun SleepPill(minutes: Int, left: String, onClick: () -> Unit) {
    val accent = LocalAccent.current
    Box(
        Modifier.clip(RoundedCornerShape(50))
            .background(if (minutes > 0) accent.copy(alpha = 0.30f) else Color.White.copy(alpha = 0.07f))
            .bounceClick(scaleDown = 0.95f, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (minutes == 0) "Sleep: off" else "Sleep ${minutes}m ($left)",
            style = MioType.micro.copy(fontSize = 9.5.sp, color = Color.White.copy(alpha = 0.85f)),
        )
    }
}

@Composable
private fun PlaylistsTab(library: List<Track>, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val settings = LocalSettings.current
    val playlists = remember(settings.playlists) { Playlists.all(settings.playlists) }
    var name by remember { mutableStateOf("") }
    var openId by remember { mutableStateOf<String?>(null) }

    fun save(next: List<com.mio.os.core.Playlist>) {
        Mio.prefs.edit { it.copy(playlists = Playlists.persist(next)) }
    }

    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                MioTextField(value = name, onValueChange = { name = it }, label = "New playlist name")
            }
            Spacer(Modifier.width(8.dp))
            Text(
                "Create",
                style = MioType.micro.copy(fontSize = 11.sp, color = Color.White),
                modifier = Modifier.clip(RoundedCornerShape(50))
                    .background(LocalAccent.current.copy(alpha = 0.4f))
                    .bounceClick(scaleDown = 0.94f) {
                        if (name.isNotBlank()) {
                            save(Playlists.create(playlists, name))
                            name = ""
                        }
                    }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        if (playlists.isEmpty()) {
            GlassCard(Modifier.fillMaxWidth(), corner = 16.dp, alpha = 0.06f) {
                Text(
                    "No playlists yet. Create one above, then tap + on any song to add it.",
                    style = MioType.body.copy(fontSize = 11.5.sp),
                    modifier = Modifier.padding(16.dp),
                )
            }
        } else {
            LazyColumn(Modifier.weight(1f)) {
                items(playlists, key = { it.id }) { p ->
                    val songs = remember(p, library) { p.songIds.mapNotNull { id -> library.find { it.id == id } } }
                    GlassCard(
                        Modifier.fillMaxWidth().padding(vertical = 5.dp)
                            .bounceClick(scaleDown = 0.98f) { openId = if (openId == p.id) null else p.id },
                        corner = 18.dp, alpha = 0.07f,
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(p.name, style = MioType.title.copy(fontSize = 13.sp))
                                    Text(
                                        "${songs.size} song" + if (songs.size == 1) "" else "s",
                                        style = MioType.micro.copy(fontSize = 10.sp),
                                    )
                                }
                                Text(
                                    "Play",
                                    style = MioType.micro.copy(fontSize = 10.5.sp, color = LocalAccent.current),
                                    modifier = Modifier.bounceClick {
                                        if (songs.isNotEmpty()) MioPlayer.playQueue(context, songs, 0)
                                        else LauncherHelper.toast(context, "Playlist is empty")
                                    }.padding(8.dp),
                                )
                                Text(
                                    "Delete",
                                    style = MioType.micro.copy(fontSize = 10.5.sp, color = Color.White.copy(alpha = 0.5f)),
                                    modifier = Modifier.bounceClick {
                                        save(Playlists.delete(playlists, p.id))
                                    }.padding(8.dp),
                                )
                            }
                            if (openId == p.id && songs.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                songs.forEach { s ->
                                    Row(
                                        Modifier.fillMaxWidth().padding(vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            "${s.title} — ${s.artist}",
                                            style = MioType.body.copy(fontSize = 11.sp),
                                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f),
                                        )
                                        Text(
                                            "Remove",
                                            style = MioType.micro.copy(fontSize = 9.5.sp, color = Color.White.copy(alpha = 0.55f)),
                                            modifier = Modifier.bounceClick {
                                                save(Playlists.removeSong(playlists, p.id, s.id))
                                            }.padding(start = 8.dp, top = 4.dp, bottom = 4.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LyricsTab(
    track: Track,
    position: Long,
    lines: List<LrcLine>,
    fileName: String,
    modifier: Modifier = Modifier,
    onLoad: () -> Unit,
) {
    val accent = LocalAccent.current
    Column(modifier) {
        if (lines.isEmpty()) {
            GlassCard(Modifier.fillMaxWidth(), corner = 16.dp, alpha = 0.06f) {
                Column(Modifier.padding(16.dp)) {
                    Text("Synced lyrics (.lrc)", style = MioType.title.copy(fontSize = 13.sp))
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Pick the song's .lrc file and SEGAWA OS highlights each line as it plays. " +
                            "Android doesn't let apps browse your folders freely, so the file is picked " +
                            "once through the system picker and never uploaded anywhere.",
                        style = MioType.body.copy(fontSize = 11.sp, lineHeight = 16.sp),
                    )
                    Spacer(Modifier.height(10.dp))
                    Row {
                        Text(
                            "Load .lrc file",
                            style = MioType.micro.copy(fontSize = 11.sp, color = Color.White),
                            modifier = Modifier.clip(RoundedCornerShape(50))
                                .background(accent.copy(alpha = 0.4f))
                                .bounceClick(scaleDown = 0.94f, onClick = onLoad)
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                        )
                    }
                }
            }
        } else {
            Text(
                "${track.title} · $fileName",
                style = MioType.micro.copy(fontSize = 9.5.sp),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            val active = lines.indexOfLast { it.ms <= position }.coerceAtLeast(0)
            LazyColumn(Modifier.weight(1f)) {
                items(lines.size) { i ->
                    val line = lines[i]
                    val on = i == active
                    Text(
                        line.text,
                        style = (if (on) MioType.title else MioType.body).copy(
                            fontSize = if (on) 14.sp else 12.sp,
                            color = if (on) accent else Color.White.copy(alpha = 0.55f),
                        ),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Text(
                    "Load a different file",
                    style = MioType.micro.copy(fontSize = 10.sp, color = Color.White.copy(alpha = 0.6f)),
                    modifier = Modifier.bounceClick(onClick = onLoad).padding(8.dp),
                )
            }
        }
    }
}

@Composable
private fun AddToPlaylistSheet(track: Track, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val settings = LocalSettings.current
    val playlists = remember(settings.playlists) { Playlists.all(settings.playlists) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0D0918),
        dragHandle = { com.mio.os.ui.components.SheetHandle() },
    ) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text("Add to playlist", style = MioType.h2)
            Spacer(Modifier.height(4.dp))
            Text(
                track.title,
                style = MioType.body.copy(fontSize = 11.5.sp),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(10.dp))
            if (playlists.isEmpty()) {
                Text(
                    "No playlists yet — create one on the Playlists tab first.",
                    style = MioType.body.copy(fontSize = 11.5.sp),
                )
            } else {
                playlists.forEach { p ->
                    val has = track.id in p.songIds
                    Row(
                        Modifier.fillMaxWidth()
                            .bounceClick(scaleDown = 0.98f) {
                                val next = if (has) {
                                    Playlists.removeSong(playlists, p.id, track.id)
                                } else {
                                    Playlists.addSong(playlists, p.id, track.id)
                                }
                                Mio.prefs.edit { it.copy(playlists = Playlists.persist(next)) }
                                LauncherHelper.toast(
                                    context,
                                    if (has) "Removed from ${p.name}" else "Added to ${p.name}",
                                )
                                onDismiss()
                            }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(p.name, style = MioType.title.copy(fontSize = 12.5.sp))
                            Text(
                                "${p.songIds.size} songs",
                                style = MioType.micro.copy(fontSize = 9.5.sp),
                            )
                        }
                        Text(
                            if (has) "✓" else "+",
                            style = MioType.title.copy(
                                fontSize = 16.sp,
                                color = if (has) LocalAccent.current else Color.White.copy(alpha = 0.6f),
                            ),
                        )
                    }
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}
