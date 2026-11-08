package com.mio.os.core

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

data class AiMessage(val role: String, val text: String, val time: Long = System.currentTimeMillis())

/**
 * Modular AI layer.
 *
 * [MioAi] first tries the assistant provider the user configured in Settings
 * (any OpenAI-compatible chat-completions endpoint) and otherwise falls back to
 * [LocalAssistant], which is fully offline, understands device commands and can
 * actually operate the launcher (open apps, torch, brightness, notes…).
 */
object AiEngine {

    suspend fun send(context: Context, history: List<AiMessage>, prompt: String): AiMessage {
        LocalAssistant.execute(context, prompt)?.let { action ->
            return AiMessage("assistant", action)
        }
        val settings = Mio.prefs.current
        if (settings.aiEndpoint.isNotBlank()) {
            val remote = runCatching { remote(context, settings, history, prompt) }.getOrNull()
            if (!remote.isNullOrBlank()) return AiMessage("assistant", remote)
        }
        return AiMessage("assistant", LocalAssistant.reply(context, prompt))
    }

    private fun remote(
        context: Context,
        settings: MioSettings,
        history: List<AiMessage>,
        prompt: String,
    ): String {
        val url = settings.aiEndpoint.trimEnd('/').let {
            if (it.endsWith("/chat/completions")) it else "$it/chat/completions"
        }
        val messages = JSONArray()
        messages.put(JSONObject().put("role", "system").put("content", SYSTEM_PROMPT))
        history.takeLast(10).forEach { m ->
            messages.put(JSONObject().put("role", if (m.role == "user") "user" else "assistant").put("content", m.text))
        }
        messages.put(JSONObject().put("role", "user").put("content", prompt))
        val body = JSONObject()
            .put("model", settings.aiModel.ifBlank { "gpt-4o-mini" })
            .put("messages", messages)
            .put("temperature", 0.7)
            .put("max_tokens", 600)

        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.connectTimeout = 12000
        conn.readTimeout = 25000
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        if (settings.aiKey.isNotBlank()) {
            conn.setRequestProperty("Authorization", "Bearer ${settings.aiKey}")
        }
        try {
            OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }
            if (conn.responseCode !in 200..299) {
                throw IllegalStateException("HTTP ${conn.responseCode}")
            }
            val text = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
            val json = JSONObject(text)
            return json.getJSONArray("choices").getJSONObject(0)
                .getJSONObject("message").optString("content").trim()
        } finally {
            conn.disconnect()
        }
    }

    private const val SYSTEM_PROMPT =
        "You are Segawa, the assistant built into SEGAWA OS, an Android home screen. " +
            "Be concise, warm and practical. Answer in the user's language."
}

/** Offline assistant with real device actions. */
object LocalAssistant {

    fun execute(context: Context, prompt: String): String? {
        val p = prompt.lowercase(Locale.getDefault()).trim()

        if (p.startsWith("open ") || p.startsWith("launch ")) {
            val target = p.removePrefix("open ").removePrefix("launch ").trim()
            val app = Mio.apps.apps.value.firstOrNull {
                it.name.lowercase(Locale.getDefault()).contains(target) ||
                    it.packageName.contains(target)
            }
            return if (app != null) {
                Mio.apps.launch(app)
                "Opening ${app.name}."
            } else {
                "I could not find an app called “$target”. Try the drawer search."
            }
        }

        if (p.contains("torch") || p.contains("flashlight")) {
            val on = p.contains("on") || p.contains("turn on") || !p.contains("off")
            if (SystemControls.isTorchAvailable(context)) {
                val now = SystemControls.torch.value
                if (now != on) SystemControls.toggleTorch(context)
                return if (on) "Flashlight on." else "Flashlight off."
            }
            return "This device has no camera flash."
        }

        if (p.contains("brightness")) {
            if (p.contains("max") || p.contains("full") || p.contains("100")) {
                val ok = SystemControls.setSystemBrightness(context, 250)
                return if (ok) "Brightness set to maximum."
                else "Grant “Modify system settings” in Settings ▸ Privacy to change brightness."
            }
            if (p.contains("dim") || p.contains("low") || p.contains("min")) {
                val ok = SystemControls.setSystemBrightness(context, 30)
                return if (ok) "Brightness lowered." else "I need system settings permission for that."
            }
        }

        if (p.contains("battery")) {
            val b = DeviceStats.battery.value
            return "Battery is at ${b.level}%${if (b.charging) " and charging" else ""}, ${b.temperatureC}°C."
        }
        if (p.contains("storage")) {
            val s = DeviceStats.storage()
            return "${s.usedGb} GB used of ${s.totalGb} GB (${s.percent}%). ${String.format(Locale.US, "%.1f", s.freeBytes / 1_073_741_824.0)} GB free."
        }
        if (p.contains("what time") || p.contains("the time")) {
            return "It is " + android.text.format.DateFormat.format("hh:mm a", System.currentTimeMillis()) + "."
        }
        if (p.contains("what") && p.contains("date") || p.contains("today's date")) {
            return "Today is " + android.text.format.DateFormat.format("EEEE, d MMMM yyyy", System.currentTimeMillis()) + "."
        }
        if (p.contains("default launcher") || p.contains("default home")) {
            return if (LauncherHelper.isDefault(context))
                "SEGAWA OS is your default home app – pressing Home opens it."
            else "SEGAWA OS is not the default home app yet. Open Settings ▸ Home screen ▸ Set as default."
        }
        if (p.startsWith("note ") || p.startsWith("remember ")) {
            val content = prompt.removePrefix("note ").removePrefix("remember ").trim()
            if (content.isNotBlank()) {
                NotesRepo.add(context, content)
                return "Saved to SEGAWA Notes: “$content”."
            }
        }
        if (p.contains("play") && (p.contains("music") || p.contains("song"))) {
            val tracks = Mio.media.tracksWithDemo()
            if (tracks.isEmpty()) return "No local music found yet."
            MioPlayer.enqueue(context, tracks.first(), tracks)
            return "Playing ${tracks.first().title} by ${tracks.first().artist}."
        }
        if (p.contains("pause")) {
            MioPlayer.pause()
            return "Paused."
        }
        if (p.contains("wifi") || p.contains("wi-fi")) {
            SystemControls.open(context, SystemControls.wifiPanel(context))
            return "Opening the Wi-Fi panel."
        }
        if (p.contains("weather")) {
            val w = Mio.prefs.current.city
            return "Opening live weather for $w."
        }
        return null
    }

    fun reply(context: Context, prompt: String): String {
        val p = prompt.lowercase(Locale.getDefault())
        return when {
            p.contains("hello") || p.contains("hi ") || p.trim() == "hi" ->
                "Hey ${Mio.prefs.current.userName}. I can open apps, control the flashlight, read battery, or take notes. What do you need?"
            p.contains("who are you") || p.contains("your name") ->
                "I am Segawa, the assistant inside SEGAWA OS. I run offline on your device by default."
            p.contains("thank") -> "Anytime. Anything else?"
            p.contains("joke") ->
                "Why did the launcher break up with the widget? It needed more space. Bad pun, better battery."
            p.contains("help") || p.contains("what can you do") ->
                "Try: “open camera”, “torch on”, “battery”, “note buy milk”, “play music”, “am I the default launcher?”"
            else ->
                "I am running in local mode, so I can act on your device rather than write long answers. " +
                    "Add your own OpenAI-compatible endpoint under AI ▸ Settings to unlock full conversation."
        }
    }
}

/** Tiny JSON-backed notes store (no database needed for a few hundred notes). */
object NotesRepo {

    data class Note(
        val id: Long, val text: String, val created: Long, val pinned: Boolean = false,
        val title: String = "", val category: String = "", val tags: String = "",
        val favorite: Boolean = false,
    )

    fun all(): List<Note> {
        val raw = Mio.prefs.current.notes
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Note(
                    id = o.optLong("id", i.toLong()),
                    text = o.optString("text", ""),
                    created = o.optLong("created", 0L),
                    pinned = o.optBoolean("pinned", false),
                    title = o.optString("title", ""),
                    category = o.optString("category", ""),
                    tags = o.optString("tags", ""),
                    favorite = o.optBoolean("favorite", false),
                )
            }.filter { it.text.isNotBlank() }.sortedByDescending { it.created }
        }.getOrDefault(emptyList())
    }

    private fun persist(notes: List<Note>) {
        val arr = JSONArray()
        notes.forEach { n ->
            arr.put(
                JSONObject()
                    .put("id", n.id).put("text", n.text)
                    .put("created", n.created).put("pinned", n.pinned)
                    .put("title", n.title).put("category", n.category)
                    .put("tags", n.tags).put("favorite", n.favorite)
            )
        }
        Mio.prefs.edit { it.copy(notes = arr.toString()) }
    }

    fun add(context: Context, text: String) {
        persist(listOf(Note(System.currentTimeMillis(), text, System.currentTimeMillis())) + all())
    }

    fun delete(id: Long) = persist(all().filterNot { it.id == id })

    fun togglePin(id: Long) = persist(all().map { if (it.id == id) it.copy(pinned = !it.pinned) else it })

    fun toggleFavorite(id: Long) =
        persist(all().map { if (it.id == id) it.copy(favorite = !it.favorite) else it })

    fun upsert(note: Note) {
        persist(listOf(note) + all().filterNot { it.id == note.id })
    }

    /** Honest on-device summary: lead sentences plus reading stats. */
    fun offlineSummary(text: String): String {
        val clean = text.replace(Regex("\\s+"), " ").trim()
        if (clean.isEmpty()) return "Empty note."
        val sentences = clean.split(Regex("(?<=[.!?])\\s+")).filter { it.isNotBlank() }
        val head = sentences.take(2).joinToString(" ")
        val words = clean.split(' ').size
        return head + "\n\n$words words · ~${(words / 200).coerceAtLeast(1)} min read · summarized on-device."
    }
}
