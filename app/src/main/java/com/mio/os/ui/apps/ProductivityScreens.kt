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
    var editing by remember { mutableStateOf<String?>(null) }
    var showSheet by remember { mutableStateOf(false) }

    fun reload() {
        notes = NotesRepo.all()
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
        if (notes.isEmpty()) {
            Column(m) {
                Spacer(Modifier.height(50.dp))
                com.mio.os.ui.components.EmptyState(
                    "Nothing here yet",
                    "Tap + to write your first note. Mio can also save notes for you from the AI screen.",
                )
            }
        } else {
            LazyColumn(m, contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)) {
                items(notes, key = { it.id }) { note ->
                    GlassCard(
                        Modifier.fillMaxWidth().padding(vertical = 5.dp)
                            .bounceClick(scaleDown = 0.98f) { editing = note.text; showSheet = true },
                        corner = 18.dp, alpha = 0.07f,
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                            Column(Modifier.weight(1f)) {
                                Text(note.text, style = MioType.title.copy(fontSize = 13.sp, lineHeight = 19.sp))
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    DateFormat.format("d MMM · HH:mm", note.created).toString(),
                                    style = MioType.micro.copy(fontSize = 9.5.sp),
                                )
                            }
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

    if (showSheet) {
        var text by remember(editing) { mutableStateOf(editing ?: "") }
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            containerColor = Color(0xFF0D0918),
            dragHandle = { com.mio.os.ui.components.SheetHandle() },
        ) {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text(if (editing == null) "New note" else "Edit note", style = MioType.h2)
                Spacer(Modifier.height(12.dp))
                Box(
                    Modifier.fillMaxWidth().height(140.dp).clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.07f))
                        .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(16.dp))
                        .padding(14.dp)
                ) {
                    BasicTextField(
                        value = text,
                        onValueChange = { text = it },
                        textStyle = MioType.title.copy(fontSize = 13.sp),
                        cursorBrush = SolidColor(LocalAccent.current),
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Spacer(Modifier.height(14.dp))
                Row {
                    SettingsPill("Save", primary = true) {
                        if (text.isNotBlank()) {
                            if (editing == null) NotesRepo.add(context, text.trim())
                            else {
                                NotesRepo.delete(notes.firstOrNull { it.text == editing }?.id ?: -1)
                                NotesRepo.add(context, text.trim())
                            }
                            reload()
                        }
                        showSheet = false
                    }
                    Spacer(Modifier.width(8.dp))
                    SettingsPill("Cancel") { showSheet = false }
                }
                Spacer(Modifier.height(28.dp))
            }
        }
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
        subtitle = DateFormat.format("MMMM yyyy", selectedDay).toString(),
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
                            "MIO reads your real Android calendars once you grant READ_CALENDAR. " +
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
                                "Alarms are owned by the system clock app – MIO hands off so they ring even " +
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
