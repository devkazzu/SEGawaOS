package com.mio.os.ui.apps

import android.Manifest
import android.content.Intent
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mio.os.core.LauncherHelper
import com.mio.os.core.MioNav
import com.mio.os.core.MioScreen
import com.mio.os.core.NotesRepo
import com.mio.os.ui.components.GlassCard
import com.mio.os.ui.components.MioLogo
import com.mio.os.ui.components.SectionHeader
import com.mio.os.ui.components.bounceClick
import com.mio.os.ui.settings.MioScreenScaffold
import com.mio.os.ui.settings.SettingsPill
import com.mio.os.ui.theme.LocalAccent
import com.mio.os.ui.theme.MioColors
import com.mio.os.ui.theme.MioType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Locale

/* ------------------------------------------------------------------------- */
/* Notes                                                                      */
/* ------------------------------------------------------------------------- */

@Composable
fun NotesScreen() {
    val context = LocalContext.current
    var notes by remember { mutableStateOf(NotesRepo.all()) }
    var editing by remember { mutableStateOf<NotesRepo.Note?>(null) }
    var showSheet by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var cat by remember { mutableStateOf<String?>(null) }
    var summaryFor by remember { mutableStateOf<NotesRepo.Note?>(null) }

    fun reload() {
        notes = NotesRepo.all()
    }

    val cats = remember(notes) { notes.mapNotNull { it.category.ifBlank { null } }.distinct().sorted() }
    val shown = remember(notes, query, cat) {
        var list = notes
        if (cat == "★") list = list.filter { it.favorite }
        else if (cat != null) list = list.filter { it.category == cat }
        if (query.isNotBlank()) {
            list = list.filter {
                it.title.contains(query, true) || it.text.contains(query, true) ||
                    it.tags.contains(query, true)
            }
        }
        list.sortedWith(compareByDescending<NotesRepo.Note> { it.pinned }.thenByDescending { it.created })
    }

    MioScreenScaffold(
        title = "Notes",
        subtitle = "${notes.size} notes · stored on device",
        scroll = false,
        trailing = {
            Box(
                Modifier.size(38.dp).clip(CircleShape)
                    .background(Brush.linearGradient(listOf(LocalAccent.current, MioColors.PurpleSoft)))
                    .bounceClick { editing = null; showSheet = true },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Add, "New note", tint = Color.White, modifier = Modifier.size(19.dp))
            }
        },
    ) { m ->
        Column(m) {
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.08f))
                    .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(50))
                    .padding(horizontal = 16.dp, vertical = 11.dp),
            ) {
                if (query.isEmpty()) {
                    Text(
                        "Search notes…",
                        style = MioType.body.copy(fontSize = 13.sp, color = Color.White.copy(alpha = 0.45f)),
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = MioType.title.copy(fontSize = 13.sp, color = Color.White),
                    cursorBrush = SolidColor(LocalAccent.current),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                NoteChip("All", cat == null) { cat = null }
                NoteChip("★ Favorites", cat == "★") { cat = "★" }
                cats.forEach { c -> NoteChip(c, cat == c) { cat = c } }
            }
            Spacer(Modifier.height(10.dp))
            if (shown.isEmpty()) {
                com.mio.os.ui.components.EmptyState(
                    "Nothing here yet",
                    "Tap + to write your first note. Segawa can also save notes for you from the AI screen.",
                )
            } else {
                LazyColumn(
                    Modifier.weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
                ) {
                    items(shown, key = { it.id }) { note ->
                        GlassCard(
                            Modifier.fillMaxWidth().padding(vertical = 5.dp)
                                .bounceClick(scaleDown = 0.98f) { editing = note; showSheet = true },
                            corner = 18.dp, alpha = 0.07f,
                        ) {
                            Column(Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (note.pinned) {
                                        Text("📌 ", style = MioType.micro.copy(fontSize = 10.sp))
                                    }
                                    Text(
                                        note.title.ifBlank { note.text.lineSequence().firstOrNull()?.take(42) ?: "Note" },
                                        style = MioType.title.copy(fontSize = 13.sp),
                                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Icon(
                                        Icons.Rounded.Star,
                                        "Favorite",
                                        tint = if (note.favorite) LocalAccent.current else Color.White.copy(alpha = 0.3f),
                                        modifier = Modifier.size(17.dp).bounceClick {
                                            NotesRepo.toggleFavorite(note.id); reload()
                                        },
                                    )
                                }
                                if (note.title.isNotBlank()) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        note.text,
                                        style = MioType.body.copy(fontSize = 11.5.sp, lineHeight = 16.sp),
                                        maxLines = 3, overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        DateFormat.format("d MMM · HH:mm", note.created).toString() +
                                            (if (note.category.isNotBlank()) " · ${note.category}" else "") +
                                            (if (note.tags.isNotBlank()) " · ${note.tags}" else ""),
                                        style = MioType.micro.copy(fontSize = 9.5.sp),
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        "Summary",
                                        style = MioType.micro.copy(fontSize = 9.5.sp, color = LocalAccent.current),
                                        modifier = Modifier.bounceClick { summaryFor = note }
                                            .padding(horizontal = 6.dp, vertical = 4.dp),
                                    )
                                    Icon(
                                        Icons.Rounded.Delete, "Delete",
                                        tint = Color.White.copy(alpha = 0.35f),
                                        modifier = Modifier.size(16.dp).bounceClick {
                                            NotesRepo.delete(note.id); reload()
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSheet) {
        NoteEditorSheet(
            initial = editing,
            onDismiss = { showSheet = false },
            onSave = { title, text, category, tags, favorite ->
                val now = System.currentTimeMillis()
                NotesRepo.upsert(
                    NotesRepo.Note(
                        id = editing?.id ?: System.nanoTime(),
                        text = text.trim(),
                        created = editing?.created ?: now,
                        pinned = editing?.pinned ?: false,
                        title = title.trim(),
                        category = category.trim().take(24),
                        tags = tags.trim().take(48),
                        favorite = favorite,
                    )
                )
                reload()
                showSheet = false
            },
        )
    }

    summaryFor?.let { note ->
        ModalBottomSheet(
            onDismissRequest = { summaryFor = null },
            containerColor = Color(0xFF0D0918),
            dragHandle = { com.mio.os.ui.components.SheetHandle() },
        ) {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text("Offline summary", style = MioType.h2)
                Spacer(Modifier.height(10.dp))
                Text(
                    NotesRepo.offlineSummary(note.text),
                    style = MioType.body.copy(fontSize = 12.sp, lineHeight = 18.sp),
                )
                Spacer(Modifier.height(28.dp))
            }
        }
    }
}

@Composable
private fun NoteChip(label: String, selected: Boolean, onClick: () -> Unit) {
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
private fun NoteEditorSheet(
    initial: NotesRepo.Note?,
    onDismiss: () -> Unit,
    onSave: (title: String, text: String, category: String, tags: String, favorite: Boolean) -> Unit,
) {
    var title by remember(initial) { mutableStateOf(initial?.title ?: "") }
    var text by remember(initial) { mutableStateOf(initial?.text ?: "") }
    var category by remember(initial) { mutableStateOf(initial?.category ?: "") }
    var tags by remember(initial) { mutableStateOf(initial?.tags ?: "") }
    var favorite by remember(initial) { mutableStateOf(initial?.favorite ?: false) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0D0918),
        dragHandle = { com.mio.os.ui.components.SheetHandle() },
    ) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text(if (initial == null) "New note" else "Edit note", style = MioType.h2)
            Spacer(Modifier.height(12.dp))
            NoteField(value = title, onValueChange = { title = it }, hint = "Title (optional)", single = true)
            Spacer(Modifier.height(10.dp))
            NoteField(value = text, onValueChange = { text = it }, hint = "Write…", single = false, height = 130.dp)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f)) {
                    NoteField(value = category, onValueChange = { category = it }, hint = "Category", single = true)
                }
                Box(Modifier.weight(1f)) {
                    NoteField(value = tags, onValueChange = { tags = it }, hint = "tags, comma", single = true)
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                SettingsPill("Save", primary = true) {
                    if (text.isNotBlank()) onSave(title, text, category, tags, favorite)
                }
                Spacer(Modifier.width(8.dp))
                SettingsPill(if (favorite) "★ Favorited" else "☆ Favorite") { favorite = !favorite }
                Spacer(Modifier.weight(1f))
                SettingsPill("Cancel", onClick = onDismiss)
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun NoteField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    single: Boolean,
    height: androidx.compose.ui.unit.Dp? = null,
) {
    Box(
        Modifier.fillMaxWidth()
            .then(if (height != null) Modifier.height(height) else Modifier)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.07f))
            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(14.dp))
            .padding(12.dp),
    ) {
        if (value.isEmpty()) {
            Text(hint, style = MioType.body.copy(fontSize = 12.sp, color = Color.White.copy(alpha = 0.4f)))
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = single,
            textStyle = MioType.title.copy(fontSize = 13.sp, color = Color.White),
            cursorBrush = SolidColor(LocalAccent.current),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/* ------------------------------------------------------------------------- */
/* Calendar                                                                   */
/* ------------------------------------------------------------------------- */

private data class CalEvent(val title: String, val start: Long, val allDay: Boolean)

@Composable
fun CalendarScreen() {
    val context = LocalContext.current
    val accent = LocalAccent.current
    var monthOffset by remember { mutableStateOf(0) }
    var selectedDay by remember { mutableStateOf(Calendar.getInstance()) }
    var events by remember { mutableStateOf<List<CalEvent>>(emptyList()) }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(Unit) {
        runCatching { permLauncher.launch(Manifest.permission.READ_CALENDAR) }
    }

    LaunchedEffect(selectedDay) {
        events = withContext(Dispatchers.IO) {
            val out = ArrayList<CalEvent>()
            runCatching {
                val dayStart = (selectedDay.clone() as Calendar).apply {
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }
                val start = dayStart.timeInMillis
                val end = start + 24 * 60 * 60 * 1000
                val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
                    .appendPath(start.toString()).appendPath(end.toString()).build()
                val proj = arrayOf(
                    CalendarContract.Instances.TITLE,
                    CalendarContract.Instances.BEGIN,
                    CalendarContract.Instances.ALL_DAY,
                )
                context.contentResolver.query(uri, proj, null, null, null)?.use { c ->
                    while (c.moveToNext() && out.size < 50) {
                        out += CalEvent(
                            c.getString(0) ?: "Event",
                            c.getLong(1),
                            c.getInt(2) == 1,
                        )
                    }
                }
            }
            out
        }
    }

    MioScreenScaffold(
        title = "Calendar",
        subtitle = "Events & reminders",
        scroll = false,
        trailing = {
            Box(
                Modifier.size(38.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.09f))
                    .bounceClick {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_INSERT)
                                    .setData(CalendarContract.Events.CONTENT_URI)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Add, "Add event", tint = Color.White, modifier = Modifier.size(19.dp)) }
        },
    ) { m ->
        Column(m) {
            val cal = remember(monthOffset) {
                (Calendar.getInstance().clone() as Calendar).apply {
                    set(Calendar.DAY_OF_MONTH, 1)
                    add(Calendar.MONTH, monthOffset)
                }
            }
            val monthName = DateFormat.format("MMMM yyyy", cal).toString()
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(monthName, style = MioType.h2.copy(fontSize = 17.sp), modifier = Modifier.weight(1f))
                SmallCircle("‹") { monthOffset -= 1 }
                Spacer(Modifier.width(6.dp))
                SmallCircle("›") { monthOffset += 1 }
            }
            Spacer(Modifier.height(10.dp))

            val firstDayOfWeek = (cal.get(Calendar.DAY_OF_WEEK) - cal.firstDayOfWeek + 7) % 7
            val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
            val cells = List(firstDayOfWeek) { null } + (1..daysInMonth).toList()

            Row(Modifier.fillMaxWidth()) {
                listOf("S", "M", "T", "W", "T", "F", "S").forEach { d ->
                    Text(
                        d, style = MioType.micro.copy(fontSize = 9.5.sp),
                        textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(7),
                modifier = Modifier.fillMaxWidth().height(260.dp),
                userScrollEnabled = false,
            ) {
                items(cells) { day ->
                    val today = Calendar.getInstance()
                    val isToday = day != null &&
                        today.get(Calendar.DAY_OF_MONTH) == day &&
                        today.get(Calendar.MONTH) == cal.get(Calendar.MONTH) &&
                        today.get(Calendar.YEAR) == cal.get(Calendar.YEAR)
                    val isSelected = day != null && selectedDay.get(Calendar.DAY_OF_MONTH) == day &&
                        selectedDay.get(Calendar.MONTH) == cal.get(Calendar.MONTH)
                    Box(
                        Modifier.padding(3.dp).aspectRatio(1f).clip(CircleShape)
                            .background(
                                when {
                                    isSelected -> accent
                                    isToday -> Color.White.copy(alpha = 0.14f)
                                    else -> Color.Transparent
                                }
                            )
                            .bounceClick {
                                if (day != null) {
                                    selectedDay = (cal.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, day) }
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            day?.toString() ?: "",
                            style = MioType.title.copy(
                                fontSize = 11.5.sp,
                                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.75f),
                            ),
                        )
                    }
                }
            }

            SectionHeader("Events · ${DateFormat.format("d MMM", selectedDay)}")
            if (events.isEmpty()) {
                GlassCard(Modifier.fillMaxWidth(), corner = 18.dp, alpha = 0.06f) {
                    Column(Modifier.padding(16.dp)) {
                        Text("No events for this day", style = MioType.title.copy(fontSize = 12.5.sp))
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "SEGAWA reads your real Android calendars once you grant READ_CALENDAR. " +
                                "Creating events opens the official system calendar editor.",
                            style = MioType.body.copy(fontSize = 10.5.sp, lineHeight = 15.sp),
                        )
                        Spacer(Modifier.height(10.dp))
                        SettingsPill("Grant calendar access", primary = true) {
                            runCatching { permLauncher.launch(Manifest.permission.READ_CALENDAR) }
                        }
                    }
                }
            } else {
                LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                    items(events) { e ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(width = 3.dp, height = 30.dp).clip(CircleShape).background(accent))
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(e.title, style = MioType.title.copy(fontSize = 12.5.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    if (e.allDay) "All day" else DateFormat.format("HH:mm", e.start).toString(),
                                    style = MioType.micro.copy(fontSize = 9.5.sp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SmallCircle(label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(30.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.09f))
            .bounceClick(scaleDown = 0.9f, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MioType.title.copy(fontSize = 14.sp))
    }
}

/* ------------------------------------------------------------------------- */
/* Clock                                                                      */
/* ------------------------------------------------------------------------- */

@Composable
fun ClockScreen() {
    val context = LocalContext.current
    val accent = LocalAccent.current
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(0) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    var running by remember { mutableStateOf(false) }
    var elapsed by remember { mutableLongStateOf(0L) }
    var timerSecs by remember { mutableLongStateOf(0L) }
    var timerRunning by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(250)
        }
    }
    LaunchedEffect(running) {
        while (running) {
            elapsed += 100
            delay(100)
        }
    }
    LaunchedEffect(timerRunning) {
        while (timerRunning && timerSecs > 0) {
            delay(1000)
            timerSecs -= 1
        }
        if (timerSecs == 0L) timerRunning = false
    }

    MioScreenScaffold(title = "Clock", subtitle = "Alarms · clock · timer") { m ->
        Column(m, horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(14.dp))
            Text(
                DateFormat.format("HH:mm", now).toString(),
                style = MioType.clock.copy(fontSize = 64.sp, brush = Brush.verticalGradient(listOf(Color.White, accent))),
            )
            Text(
                DateFormat.format("EEE, d MMMM yyyy", now).toString(),
                style = MioType.body.copy(fontSize = 12.sp),
            )
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                listOf("Stopwatch", "Timer", "Alarms").forEachIndexed { i, label ->
                    Box(
                        Modifier.padding(horizontal = 4.dp).clip(RoundedCornerShape(50))
                            .background(if (tab == i) accent.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.07f))
                            .bounceClick(scaleDown = 0.95f) { tab = i }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Text(
                            label,
                            style = MioType.micro.copy(
                                fontSize = 10.5.sp,
                                color = if (tab == i) Color.White else Color.White.copy(alpha = 0.6f),
                            ),
                        )
                    }
                }
            }
            Spacer(Modifier.height(18.dp))

            when (tab) {
                0 -> {
                    Text(fmtStopwatch(elapsed), style = MioType.clock.copy(fontSize = 44.sp, letterSpacing = (-1).sp))
                    Spacer(Modifier.height(16.dp))
                    Row {
                        RoundAction(if (running) Icons.Rounded.Stop else Icons.Rounded.PlayArrow, primary = true) {
                            running = !running
                        }
                        Spacer(Modifier.width(12.dp))
                        RoundAction(Icons.Rounded.Refresh) {
                            running = false; elapsed = 0
                        }
                    }
                }
                1 -> {
                    Text(fmtStopwatch(timerSecs * 1000), style = MioType.clock.copy(fontSize = 44.sp, letterSpacing = (-1).sp))
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(60L, 300L, 600L, 1800L).forEach { secs ->
                            Box(
                                Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.07f))
                                    .bounceClick { timerSecs = secs },
                            ) { Text("${secs / 60}m", style = MioType.micro.copy(fontSize = 10.sp), modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)) }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Row {
                        RoundAction(if (timerRunning) Icons.Rounded.Stop else Icons.Rounded.PlayArrow, primary = true) {
                            if (timerSecs > 0) timerRunning = !timerRunning
                        }
                        Spacer(Modifier.width(12.dp))
                        RoundAction(Icons.Rounded.Refresh) { timerRunning = false; timerSecs = 0 }
                    }
                }
                else -> {
                    GlassCard(Modifier.fillMaxWidth(), corner = 20.dp, alpha = 0.07f) {
                        Column(Modifier.padding(18.dp)) {
                            Text("Set alarms in Android", style = MioType.title.copy(fontSize = 13.sp))
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Alarms are owned by the system clock app – SEGAWA hands off so they ring even " +
                                    "when the launcher is not running.",
                                style = MioType.body.copy(fontSize = 11.sp, lineHeight = 16.sp),
                            )
                            Spacer(Modifier.height(12.dp))
                            Row {
                                SettingsPill("Set alarm", primary = true) {
                                    runCatching {
                                        context.startActivity(
                                            Intent(AlarmClock.ACTION_SET_ALARM)
                                                .putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        )
                                    }
                                }
                                Spacer(Modifier.width(8.dp))
                                SettingsPill("Show alarms") {
                                    runCatching {
                                        context.startActivity(
                                            Intent(AlarmClock.ACTION_SHOW_ALARMS)
                                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(26.dp))
            SectionHeader("World clocks")
            val zones = listOf(
                "Local" to java.util.TimeZone.getDefault().id,
                "Tokyo" to "Asia/Tokyo",
                "London" to "Europe/London",
                "New York" to "America/New_York",
            )
            zones.forEach { (label, id) ->
                val tz = java.util.TimeZone.getTimeZone(id)
                val time = java.text.SimpleDateFormat("HH:mm", Locale.US).apply { timeZone = tz }.format(java.util.Date(now))
                Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(label, style = MioType.title.copy(fontSize = 12.5.sp), modifier = Modifier.weight(1f))
                    Text(time, style = MioType.h2.copy(fontSize = 16.sp))
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

private fun fmtStopwatch(ms: Long): String {
    val total = ms / 1000
    val hundredths = (ms % 1000) / 10
    return String.format(Locale.US, "%02d:%02d.%02d", total / 60, total % 60, hundredths)
}

@Composable
private fun RoundAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    primary: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        Modifier.size(58.dp).clip(CircleShape)
            .background(
                if (primary) Brush.linearGradient(listOf(LocalAccent.current, MioColors.PurpleSoft))
                else Brush.linearGradient(listOf(Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.07f)))
            )
            .bounceClick(scaleDown = 0.92f, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(24.dp))
    }
}

/* ------------------------------------------------------------------------- */
/* Calculator                                                                 */
/* ------------------------------------------------------------------------- */

object Calculator {

    fun evaluate(expr: String): Double? {
        return runCatching { Parser(expr).parse() }.getOrNull()
    }

    private class Parser(private val s: String) {
        private var i = 0

        fun parse(): Double {
            val v = expression()
            skip()
            if (i < s.length) throw IllegalStateException("Unexpected ${s[i]}")
            return v
        }

        private fun skip() {
            while (i < s.length && s[i] == ' ') i++
        }

        private fun expression(): Double {
            var v = term()
            while (true) {
                skip()
                if (i < s.length && (s[i] == '+' || s[i] == '-')) {
                    val op = s[i]; i++
                    val r = term()
                    v = if (op == '+') v + r else v - r
                } else return v
            }
        }

        private fun term(): Double {
            var v = factor()
            while (true) {
                skip()
                if (i < s.length && (s[i] == '*' || s[i] == '/' || s[i] == '%')) {
                    val op = s[i]; i++
                    val r = factor()
                    v = when (op) {
                        '*' -> v * r
                        '/' -> if (r == 0.0) throw ArithmeticException() else v / r
                        else -> v % r
                    }
                } else return v
            }
        }

        private fun factor(): Double {
            skip()
            if (i < s.length && s[i] == '-') { i++; return -factor() }
            if (i < s.length && s[i] == '(') {
                i++
                val v = expression()
                skip()
                if (i < s.length && s[i] == ')') i++
                return v
            }
            val start = i
            while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
            if (start == i) throw IllegalStateException("Number expected")
            return s.substring(start, i).toDouble()
        }
    }
}

@Composable
fun CalculatorScreen() {
    val accent = LocalAccent.current
    var expr by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("0") }

    fun press(key: String) {
        when (key) {
            "C" -> { expr = ""; result = "0" }
            "⌫" -> { if (expr.isNotEmpty()) expr = expr.dropLast(1) }
            "=" -> {
                val v = Calculator.evaluate(expr)
                result = when {
                    v == null -> "Error"
                    v == Math.floor(v) && !v.isInfinite() -> v.toLong().toString()
                    else -> String.format(Locale.US, "%.6f", v).trimEnd('0').trimEnd('.')
                }
            }
            else -> expr += key
        }
    }

    MioScreenScaffold(title = "Calculator", subtitle = "Premium arithmetic") { m ->
        Column(m.fillMaxSize(), horizontalAlignment = Alignment.End) {
            Spacer(Modifier.height(20.dp))
            Text(
                expr.ifEmpty { "0" },
                style = MioType.clock.copy(fontSize = 40.sp, color = Color.White.copy(alpha = 0.65f)),
                maxLines = 2,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                result,
                style = MioType.clock.copy(fontSize = 54.sp, brush = Brush.verticalGradient(listOf(Color.White, accent))),
                maxLines = 1,
            )
            Spacer(Modifier.height(22.dp))
            val keys = listOf(
                listOf("C", "(", ")", "÷"),
                listOf("7", "8", "9", "×"),
                listOf("4", "5", "6", "−"),
                listOf("1", "2", "3", "+"),
                listOf("0", ".", "⌫", "="),
            )
            keys.forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { k ->
                        val isOp = k in listOf("÷", "×", "−", "+", "=")
                        val mapped = when (k) {
                            "÷" -> "/"; "×" -> "*"; "−" -> "-"; else -> k
                        }
                        Box(
                            Modifier.weight(1f).height(64.dp).clip(RoundedCornerShape(20.dp))
                                .background(
                                    when {
                                        k == "=" -> Brush.linearGradient(listOf(accent, MioColors.PurpleSoft))
                                        isOp -> Brush.linearGradient(listOf(Color.White.copy(alpha = 0.12f), Color.White.copy(alpha = 0.08f)))
                                        else -> Brush.linearGradient(listOf(Color.White.copy(alpha = 0.07f), Color.White.copy(alpha = 0.05f)))
                                    }
                                )
                                .bounceClick(scaleDown = 0.92f) { press(mapped) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                k,
                                style = MioType.h2.copy(fontSize = if (k == "=") 24.sp else 20.sp, color = Color.White),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            Spacer(Modifier.height(14.dp))
        }
    }
}
