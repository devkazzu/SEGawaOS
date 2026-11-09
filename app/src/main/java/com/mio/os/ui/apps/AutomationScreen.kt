package com.mio.os.ui.apps

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mio.os.core.Mio
import com.mio.os.core.Routine
import com.mio.os.core.Routines
import com.mio.os.service.SegaAlarmReceiver
import com.mio.os.ui.components.EmptyState
import com.mio.os.ui.components.GlassCard
import com.mio.os.ui.components.SheetHandle
import com.mio.os.ui.components.bounceClick
import com.mio.os.ui.drawer.MioSwitch
import com.mio.os.ui.settings.MioScreenScaffold
import com.mio.os.ui.settings.SettingsPill
import com.mio.os.ui.settings.StepperRow
import com.mio.os.ui.setup.MioTextField
import com.mio.os.ui.theme.LocalAccent
import com.mio.os.ui.theme.LocalSettings
import com.mio.os.ui.theme.MioColors
import com.mio.os.ui.theme.MioType
import java.util.Calendar

private val RoutineKinds = listOf(
    "reminder" to "Reminder",
    "focus" to "Focus mode",
    "music" to "Play music",
    "wallpaper" to "Wallpaper",
    "app" to "Open app",
)

private val DayLabels = listOf(
    Calendar.SUNDAY to "S",
    Calendar.MONDAY to "M",
    Calendar.TUESDAY to "T",
    Calendar.WEDNESDAY to "W",
    Calendar.THURSDAY to "T",
    Calendar.FRIDAY to "F",
    Calendar.SATURDAY to "S",
)

/**
 * Automation: time-based routines fired by [SegaAlarmReceiver] through exact
 * alarms (re-armed after reboot). Everything runs on-device; schedules and
 * payloads live in [com.mio.os.core.MioSettings.routines].
 */
@Composable
fun AutomationScreen() {
    val context = LocalContext.current
    val settings = LocalSettings.current
    val routines = remember(settings.routines) { Routines.all(settings.routines) }
    var editing by remember { mutableStateOf<Routine?>(null) }
    var showSheet by remember { mutableStateOf(false) }

    fun persist(list: List<Routine>) {
        Mio.prefs.edit { it.copy(routines = Routines.persist(list)) }
        list.forEach { r ->
            if (r.enabled) {
                SegaAlarmReceiver.scheduleRoutine(context, r.id, r.hour, r.minute)
            } else {
                SegaAlarmReceiver.cancelRoutine(context, r.id)
            }
        }
    }

    MioScreenScaffold(
        title = "Automation",
        subtitle = "${routines.count { it.enabled }} active · ${routines.size} total",
        scroll = false,
        trailing = {
            Box(
                Modifier.size(38.dp).clip(CircleShape)
                    .background(Brush.linearGradient(listOf(LocalAccent.current, MioColors.PurpleSoft)))
                    .bounceClick { editing = null; showSheet = true },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Add, "New routine", tint = Color.White, modifier = Modifier.size(19.dp))
            }
        },
    ) { m ->
        Column(m) {
            if (routines.isEmpty()) {
                EmptyState(
                    "No routines yet",
                    "Tap + to schedule a reminder, focus session, morning playlist, wallpaper rotation or app launch.",
                )
            } else {
                LazyColumn(Modifier.weight(1f)) {
                    items(routines, key = { it.id }) { r ->
                        GlassCard(
                            Modifier.fillMaxWidth().padding(vertical = 5.dp)
                                .bounceClick(scaleDown = 0.98f) { editing = r; showSheet = true },
                            corner = 18.dp, alpha = 0.07f,
                        ) {
                            Row(
                                Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(r.name.ifBlank { "Routine" }, style = MioType.title.copy(fontSize = 13.sp))
                                    Spacer(Modifier.height(3.dp))
                                    Text(Routines.describe(r), style = MioType.micro.copy(fontSize = 10.sp))
                                    if (r.payload.isNotBlank() && (r.kind == "reminder" || r.kind == "focus" || r.kind == "app")) {
                                        Text(
                                            r.payload.take(48),
                                            style = MioType.body.copy(fontSize = 10.5.sp),
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                                Icon(
                                    Icons.Rounded.PlayArrow, "Run now",
                                    tint = Color.White.copy(alpha = 0.55f),
                                    modifier = Modifier.size(20.dp).bounceClick {
                                        SegaAlarmReceiver.test(context, r.kind, r.payload, r.name)
                                    },
                                )
                                Spacer(Modifier.width(10.dp))
                                MioSwitch(checked = r.enabled, onCheckedChange = { on ->
                                    persist(routines.map { if (it.id == r.id) it.copy(enabled = on) else it })
                                })
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Routines fire even when SEGAWA OS is closed, using battery-friendly inexact alarms — they may drift a few minutes in Doze mode. Re-armed automatically after reboot.",
                style = MioType.micro.copy(fontSize = 9.5.sp),
            )
        }
    }

    if (showSheet) {
        RoutineEditorSheet(
            initial = editing,
            onDismiss = { showSheet = false },
            onDelete = if (editing == null) null else ({
                val gone = editing!!
                SegaAlarmReceiver.cancelRoutine(context, gone.id)
                persist(routines.filterNot { it.id == gone.id })
                showSheet = false
            }),
            onSave = { draft ->
                val list = if (editing == null) {
                    routines + draft.copy(id = System.currentTimeMillis().toString())
                } else {
                    routines.map { if (it.id == editing!!.id) draft.copy(id = it.id) else it }
                }
                persist(list)
                showSheet = false
            },
        )
    }
}

@Composable
private fun RoutineEditorSheet(
    initial: Routine?,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)?,
    onSave: (Routine) -> Unit,
) {
    var name by remember(initial) { mutableStateOf(initial?.name ?: "") }
    var kind by remember(initial) { mutableStateOf(initial?.kind ?: "reminder") }
    var hour by remember(initial) { mutableStateOf(initial?.hour ?: 8) }
    var minute by remember(initial) { mutableStateOf(initial?.minute ?: 0) }
    var days by remember(initial) { mutableStateOf(initial?.days ?: emptySet()) }
    var payload by remember(initial) { mutableStateOf(initial?.payload ?: "") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0D0918),
        dragHandle = { SheetHandle() },
    ) {
        LazyColumn(Modifier.padding(horizontal = 20.dp)) {
            item {
                Text(if (initial == null) "New routine" else "Edit routine", style = MioType.h2)
                Spacer(Modifier.height(12.dp))
                MioTextField(value = name, onValueChange = { name = it }, label = "Name (e.g. Morning focus)")
                Spacer(Modifier.height(10.dp))
                Text("DOES", style = MioType.micro.copy(fontSize = 9.sp, letterSpacing = 1.2.sp))
                Spacer(Modifier.height(6.dp))
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    RoutineKinds.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { (id, label) ->
                                KindChip(label, kind == id, Modifier.weight(1f)) { kind = id }
                            }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        StepperRow(
                            title = "Hour", value = "%02d".format(hour),
                            onMinus = { hour = (hour + 23) % 24 },
                            onPlus = { hour = (hour + 1) % 24 },
                        )
                    }
                    Box(Modifier.weight(1f)) {
                        StepperRow(
                            title = "Min", value = "%02d".format(minute),
                            onMinus = { minute = (minute + 59) % 60 },
                            onPlus = { minute = (minute + 1) % 60 },
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text("DAYS (NONE = DAILY)", style = MioType.micro.copy(fontSize = 9.sp, letterSpacing = 1.2.sp))
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DayLabels.forEach { (day, label) ->
                        val on = day in days
                        Box(
                            Modifier.size(34.dp).clip(CircleShape)
                                .background(
                                    if (on) LocalAccent.current.copy(alpha = 0.35f)
                                    else Color.White.copy(alpha = 0.07f),
                                )
                                .border(
                                    1.dp,
                                    if (on) LocalAccent.current else Color.White.copy(alpha = 0.12f),
                                    CircleShape,
                                )
                                .bounceClick(scaleDown = 0.9f) {
                                    days = if (on) days - day else days + day
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(label, style = MioType.title.copy(fontSize = 12.sp))
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                MioTextField(
                    value = payload,
                    onValueChange = { payload = it },
                    label = when (kind) {
                        "reminder", "focus" -> "Message text"
                        "app" -> "App package (e.g. com.example.app)"
                        "wallpaper" -> "Wallpaper id, blank = rotate"
                        else -> "Details (optional)"
                    },
                )
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SettingsPill("Save", primary = true) {
                        onSave(
                            Routine(
                                id = initial?.id ?: "",
                                name = name.trim().ifBlank { "Routine" },
                                kind = kind, hour = hour, minute = minute,
                                days = days, payload = payload.trim(),
                                enabled = initial?.enabled ?: true,
                            )
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    if (onDelete != null) {
                        SettingsPill("Delete", onClick = onDelete)
                    } else {
                        SettingsPill("Cancel", onClick = onDismiss)
                    }
                    if (onDelete != null) {
                        Spacer(Modifier.weight(1f))
                        Icon(
                            Icons.Rounded.Delete, "Discard",
                            tint = Color.White.copy(alpha = 0.4f),
                            modifier = Modifier.size(18.dp).bounceClick(onClick = onDismiss),
                        )
                    }
                }
                Spacer(Modifier.height(28.dp))
            }
        }
    }
}

@Composable
private fun KindChip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val accent = LocalAccent.current
    Box(
        modifier.clip(RoundedCornerShape(12.dp))
            .background(if (selected) accent.copy(alpha = 0.30f) else Color.White.copy(alpha = 0.06f))
            .border(
                1.dp,
                if (selected) accent.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.08f),
                RoundedCornerShape(12.dp),
            )
            .bounceClick(scaleDown = 0.95f, onClick = onClick)
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MioType.micro.copy(
                fontSize = 10.sp,
                color = if (selected) Color.White else Color.White.copy(alpha = 0.65f),
            ),
        )
    }
}
