package com.mio.os.core

import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/**
 * Automation routines. Kinds: "music" (play library), "wallpaper" (payload =
 * wallpaper id, blank = rotate), "app" (payload = package), "reminder"
 * (payload = text) and "focus" (DND + reminder).
 *
 * Days use [Calendar] day-of-week values (SUNDAY=1..SATURDAY=7); an empty set
 * means every day.
 */
data class Routine(
    val id: String,
    val name: String,
    val kind: String,
    val hour: Int,
    val minute: Int,
    val days: Set<Int>,
    val payload: String,
    val enabled: Boolean,
)

object Routines {

    fun all(raw: String): List<Routine> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val daysRaw = o.optString("days", "")
                Routine(
                    id = o.optString("id", "$i"),
                    name = o.optString("name", "Routine"),
                    kind = o.optString("kind", "reminder"),
                    hour = o.optInt("hour", 8).coerceIn(0, 23),
                    minute = o.optInt("minute", 0).coerceIn(0, 59),
                    days = daysRaw.split(",").mapNotNull { it.toIntOrNull() }
                        .filter { it in 1..7 }.toSet(),
                    payload = o.optString("payload", ""),
                    enabled = o.optBoolean("enabled", true),
                )
            }
        }.getOrDefault(emptyList())
    }

    fun persist(list: List<Routine>): String {
        val arr = JSONArray()
        list.forEach { r ->
            arr.put(
                JSONObject()
                    .put("id", r.id).put("name", r.name).put("kind", r.kind)
                    .put("hour", r.hour).put("minute", r.minute)
                    .put("days", r.days.sorted().joinToString(","))
                    .put("payload", r.payload).put("enabled", r.enabled),
            )
        }
        return arr.toString()
    }

    /** True when the given wall-clock moment matches the routine schedule. */
    fun due(r: Routine, hour: Int, minute: Int, dayOfWeek: Int): Boolean =
        r.enabled && r.hour == hour && r.minute == minute &&
            (r.days.isEmpty() || dayOfWeek in r.days)

    fun describe(r: Routine): String {
        val time = "%02d:%02d".format(r.hour, r.minute)
        val days = if (r.days.isEmpty()) {
            "daily"
        } else {
            r.days.sorted().joinToString(" ") {
                when (it) {
                    Calendar.SUNDAY -> "Sun"
                    Calendar.MONDAY -> "Mon"
                    Calendar.TUESDAY -> "Tue"
                    Calendar.WEDNESDAY -> "Wed"
                    Calendar.THURSDAY -> "Thu"
                    Calendar.FRIDAY -> "Fri"
                    else -> "Sat"
                }
            }
        }
        val what = when (r.kind) {
            "music" -> "Play music"
            "wallpaper" -> "Rotate wallpaper"
            "app" -> "Open app"
            "focus" -> "Focus mode"
            else -> "Reminder"
        }
        return "$what · $time · $days"
    }
}
