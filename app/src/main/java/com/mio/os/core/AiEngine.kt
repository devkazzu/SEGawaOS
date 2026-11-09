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

data class AiMessage(
    val role: String,
    val text: String,
    val time: Long = System.currentTimeMillis(),
    /** True when answered on-device (local command, local model or fallback). */
    val local: Boolean = true,
)

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
            return AiMessage("assistant", action, local = true)
        }
        val settings = Mio.prefs.current
        if (settings.aiEndpoint.isNotBlank()) {
            val remote = runCatching { remote(context, settings, history, prompt) }.getOrNull()
            if (!remote.isNullOrBlank()) return AiMessage("assistant", remote, local = false)
        }
        return AiMessage("assistant", LocalAssistant.reply(context, prompt), local = true)
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

        SEGAWA_SCREENS.forEach { (keys, screen, label) ->
            if (keys.any { k -> p == "open $k" || p == "launch $k" || p == "go to $k" || p == "show $k" }) {
                MioNav.push(screen)
                return "Opening $label."
            }
        }

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

        Regex("brightness (\\d{1,3})").find(p)?.let {
            val v = it.groupValues[1].toIntOrNull()?.coerceIn(0, 100) ?: return@let
            val ok = SystemControls.setSystemBrightness(context, (v * 255 / 100).coerceIn(1, 255))
            return if (ok) "Brightness $v%."
            else "Grant “Modify system settings” in Settings ▸ Privacy to change brightness."
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
        if (p.startsWith("remember that ")) {
            val fact = prompt.trim().removePrefix("remember that ").removePrefix("Remember that ").trim()
            if (fact.length >= 4) {
                AiMemory.rememberFact(fact)
                return "Got it — I will remember that."
            }
        }
        Regex("^my (.+?) is (.+)$").find(p)?.let {
            val key = it.groupValues[1].trim()
            val value = it.groupValues[2].trim().removeSuffix(".")
            if (key.length >= 2 && value.length >= 1) {
                AiMemory.rememberPair(key, prompt.substringAfter(" is ").trim())
                return "Noted — your $key is saved on this device."
            }
        }
        Regex("what(?:'s| is) my (.+)").find(p)?.let {
            val key = it.groupValues[1].trim().removeSuffix("?").trim()
            val hit = AiMemory.recall(key)
            return hit ?: "I don't have anything saved about your $key yet. Tell me with “my $key is …”."
        }
        if (p == "what do you remember" || p == "what do you remember about me") {
            return AiMemory.describe()
        }
        if (p.startsWith("forget my ")) {
            val key = p.removePrefix("forget my ").trim()
            return if (AiMemory.forget(key)) "Forgot everything about your $key."
            else "I had nothing saved about your $key."
        }
        if (p.startsWith("note ") || (p.startsWith("remember ") && !p.startsWith("remember that "))) {
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
        if ((p.contains("next") || p.contains("skip")) && (p.contains("song") || p.contains("track") || p.contains("music"))) {
            MioPlayer.next(context)
            return "Skipped to the next track."
        }
        if (p.contains("previous") || (p.contains("last") && p.contains("song")) || p.contains("go back") && p.contains("song")) {
            MioPlayer.previous(context)
            return "Back to the previous track."
        }
        if (p.contains("volume")) {
            val max = SystemControls.mediaVolumeMax(context).coerceAtLeast(1)
            val cur = SystemControls.mediaVolume(context)
            val next = when {
                p.contains("mute") || p.contains("silent") || p.contains(" 0") -> 0
                p.contains("max") || p.contains("full") || p.contains("100") -> max
                p.contains("up") || p.contains("louder") || p.contains("+") -> (cur + max / 5).coerceAtMost(max)
                p.contains("down") || p.contains("quieter") || p.contains("lower") -> (cur - max / 5).coerceAtLeast(0)
                else -> null
            }
            if (next != null) {
                SystemControls.setMediaVolume(context, next)
                return "Volume ${(next * 100 / max)}%."
            }
            return "Volume is at ${(cur * 100 / max)}%. Say “volume up” or “volume 50”."
        }
        Regex("volume (\\d{1,3})").find(p)?.let {
            val v = it.groupValues[1].toIntOrNull()?.coerceIn(0, 100) ?: return@let
            SystemControls.setMediaVolume(context, v * SystemControls.mediaVolumeMax(context) / 100)
            return "Volume $v%."
        }
        if ((p.contains("dnd") || p.contains("do not disturb")) && (p.contains("on") || p.contains("off") || p.contains("enable") || p.contains("disable"))) {
            if (!LauncherHelper.dndAccess(context)) {
                SystemControls.open(context, LauncherHelper.dndAccessIntent(context))
                return "Grant Do Not Disturb access first — I opened the system screen."
            }
            val wantOn = p.contains("on") || p.contains("enable")
            if (SystemControls.dnd.value != wantOn) SystemControls.toggleDnd(context)
            return if (wantOn) "Do Not Disturb on." else "Do Not Disturb off."
        }
        if (p.contains("wallpaper") && (p.contains("next") || p.contains("rotate") || p.contains("change") || p.contains("shuffle"))) {
            val id = WallpaperRepo.nextRotation(Mio.prefs.current.wallpaperId, Mio.prefs.current.favWalls)
            Mio.prefs.edit { it.copy(wallpaperId = id, wallpaperUri = null) }
            return "Wallpaper rotated."
        }
        if ((p.startsWith("use ") || p.startsWith("switch to ") || p.startsWith("theme ")) && p.contains("theme") || p.startsWith("apply theme ")) {
            val want = p.removePrefix("use ").removePrefix("switch to ").removePrefix("theme ").removePrefix("apply theme ").removeSuffix(" theme").trim()
            val spec = SegaThemes.firstOrNull { it.name.lowercase(Locale.getDefault()).contains(want) || it.id == want }
            if (spec != null) {
                spec.applyTo(Mio.prefs)
                return "Applied the ${spec.name} theme."
            }
            return "I know these themes: " + SegaThemes.joinToString(", ") { it.name } + "."
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
                "Try: “open camera”, “open themes”, “torch on”, “volume 40”, “next song”, “note buy milk”, " +
                    "“my bike is red”, “what is my bike?”, “use cyberpunk theme”, “next wallpaper”, “dnd on”."
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

/** SEGAWA screens addressable by voice or text ("open themes", "go to notes"). */
private val SEGAWA_SCREENS: List<Triple<List<String>, MioScreen, String>> = listOf(
    Triple(listOf("themes", "theme", "personalization"), MioScreen.THEMES, "Themes"),
    Triple(listOf("notes", "note"), MioScreen.NOTES, "Notes"),
    Triple(listOf("music", "player", "songs"), MioScreen.MUSIC, "Music"),
    Triple(listOf("browser", "web", "internet"), MioScreen.BROWSER, "Browser"),
    Triple(listOf("settings"), MioScreen.SETTINGS, "Settings"),
    Triple(listOf("gallery", "photos"), MioScreen.GALLERY, "Gallery"),
    Triple(listOf("files", "file manager"), MioScreen.FILES, "Files"),
    Triple(listOf("clock", "alarm", "timer"), MioScreen.CLOCK, "Clock"),
    Triple(listOf("calendar"), MioScreen.CALENDAR, "Calendar"),
    Triple(listOf("weather"), MioScreen.WEATHER, "Weather"),
    Triple(listOf("search"), MioScreen.SEARCH, "Search"),
    Triple(listOf("automation", "routines"), MioScreen.AUTOMATION, "Automation"),
    Triple(listOf("backup"), MioScreen.BACKUP, "Backup"),
    Triple(listOf("store", "theme store"), MioScreen.STORE, "the Theme store"),
    Triple(listOf("control center", "controls"), MioScreen.CONTROL, "Control Center"),
    Triple(listOf("drawer", "apps", "app drawer"), MioScreen.DRAWER, "the app drawer"),
    Triple(listOf("home", "home screen"), MioScreen.HOME, "home"),
    Triple(listOf("calculator"), MioScreen.CALCULATOR, "Calculator"),
    Triple(listOf("gaming", "games"), MioScreen.GAMING, "Gaming"),
    Triple(listOf("about"), MioScreen.ABOUT, "About"),
)

/**
 * Tiny on-device memory: free-form facts ("remember that …") plus key/value
 * pairs ("my bike is red"). Stored as JSON in [MioSettings.aiMemory]; nothing
 * ever leaves the device.
 */
object AiMemory {

    private fun doc(): JSONObject {
        val raw = Mio.prefs.current.aiMemory
        return runCatching { JSONObject(raw) }.getOrDefault(JSONObject())
    }

    private fun save(doc: JSONObject) {
        Mio.prefs.edit { it.copy(aiMemory = doc.toString()) }
    }

    fun rememberFact(fact: String) {
        val doc = doc()
        val arr = doc.optJSONArray("facts") ?: JSONArray()
        val next = JSONArray()
        next.put(fact.take(200))
        for (i in 0 until arr.length().coerceAtMost(49)) next.put(arr.optString(i))
        doc.put("facts", next)
        save(doc)
    }

    fun rememberPair(key: String, value: String) {
        val doc = doc()
        val pairs = doc.optJSONObject("pairs") ?: JSONObject()
        pairs.put(key.lowercase(Locale.getDefault()).take(40), value.take(200))
        doc.put("pairs", pairs)
        save(doc)
    }

    fun recall(key: String): String? {
        val k = key.lowercase(Locale.getDefault())
        val pairs = doc().optJSONObject("pairs")
        if (pairs != null) {
            pairs.keys().forEach { name ->
                if (k.contains(name) || name.contains(k)) {
                    return "Your $name is ${pairs.optString(name)}."
                }
            }
        }
        val arr = doc().optJSONArray("facts")
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val f = arr.optString(i)
                if (f.lowercase(Locale.getDefault()).contains(k)) return "You told me: “$f”."
            }
        }
        return null
    }

    fun forget(key: String): Boolean {
        val k = key.lowercase(Locale.getDefault())
        val doc = doc()
        var changed = false
        val pairs = doc.optJSONObject("pairs")
        if (pairs != null) {
            val drop = ArrayList<String>()
            pairs.keys().forEach { name -> if (k.contains(name) || name.contains(k)) drop += name }
            drop.forEach { pairs.remove(it); changed = true }
            doc.put("pairs", pairs)
        }
        val arr = doc.optJSONArray("facts")
        if (arr != null) {
            val next = JSONArray()
            for (i in 0 until arr.length()) {
                val f = arr.optString(i)
                if (f.lowercase(Locale.getDefault()).contains(k)) changed = true else next.put(f)
            }
            doc.put("facts", next)
        }
        if (changed) save(doc)
        return changed
    }

    fun describe(): String {
        val doc = doc()
        val pairs = doc.optJSONObject("pairs")
        val bits = ArrayList<String>()
        if (pairs != null) {
            pairs.keys().forEach { name -> bits += "your $name is ${pairs.optString(name)}" }
        }
        val arr = doc.optJSONArray("facts")
        if (arr != null) for (i in 0 until arr.length()) bits += "“${arr.optString(i)}”"
        return if (bits.isEmpty()) {
            "Nothing yet. Tell me things like “my bike is red” or “remember that I water plants on Sundays”."
        } else {
            "Here's what I remember:\n• " + bits.take(10).joinToString("\n• ")
        }
    }
}

/** Chat threads: capped JSON in [MioSettings.aiThreads], newest first. */
data class AiThread(val id: String, val title: String, val messages: List<AiMessage>, val updated: Long)

object AiThreads {

    fun all(raw: String): List<AiThread> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val msgs = ArrayList<AiMessage>()
                val marr = o.optJSONArray("messages")
                if (marr != null) {
                    for (k in 0 until marr.length()) {
                        val m = marr.optJSONObject(k) ?: continue
                        val text = m.optString("text", "")
                        if (text.isBlank()) continue
                        msgs += AiMessage(
                            m.optString("role", "assistant"), text,
                            m.optLong("time", 0L), m.optBoolean("local", true),
                        )
                    }
                }
                AiThread(
                    o.optString("id", "$i"), o.optString("title", "Chat").ifBlank { "Chat" },
                    msgs, o.optLong("updated", 0L),
                )
            }.sortedByDescending { it.updated }
        }.getOrDefault(emptyList())
    }

    fun persist(list: List<AiThread>): String {
        val arr = JSONArray()
        list.sortedByDescending { it.updated }.take(20).forEach { t ->
            val marr = JSONArray()
            t.messages.takeLast(80).forEach { m ->
                marr.put(
                    JSONObject().put("role", m.role).put("text", m.text)
                        .put("time", m.time).put("local", m.local),
                )
            }
            arr.put(
                JSONObject().put("id", t.id).put("title", t.title.take(48))
                    .put("messages", marr).put("updated", t.updated),
            )
        }
        return arr.toString()
    }

    fun titleFor(firstText: String): String {
        val words = firstText.trim().split(Regex("\\s+")).take(6).joinToString(" ")
        return words.take(40).ifBlank { "Chat" }
    }

    fun fresh(): AiThread = AiThread(
        System.currentTimeMillis().toString(), "New chat", emptyList(), System.currentTimeMillis(),
    )
}
