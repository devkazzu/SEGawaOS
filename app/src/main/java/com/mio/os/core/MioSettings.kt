package com.mio.os.core

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Every user-visible preference of SEGAWA OS. Persisted in one SharedPreferences
 * file and exposed as a hot [StateFlow] so Compose recomposes instantly.
 */
data class MioSettings(
    val accent: Int = 0xFFFF4FA3.toInt(),
    val gridColumns: Int = 4,
    val iconScale: Float = 1f,
    val hiddenApps: Set<String> = emptySet(),
    val dockApps: List<String> = emptyList(),
    val wallpaperId: String = "hiro_sakura",
    val wallpaperUri: String? = null,
    val dim: Float = 0.25f,
    val userName: String = "Creator",
    val city: String = "Silchar",
    val showWeather: Boolean = true,
    val animations: Boolean = true,
    val glass: Float = 1f,
    val onboarded: Boolean = false,
    val showLabels: Boolean = true,
    val fontScale: Float = 1f,
    val clock24h: Boolean = true,
    val showDock: Boolean = true,
    val sortMode: String = SortMode.ALPHABETICAL.name,
    val recentApps: List<String> = emptyList(),
    val aiEndpoint: String = "",
    val aiKey: String = "",
    val aiModel: String = "gpt-4o-mini",
    val aiHistory: String = "",
    val notes: String = "",
    val folders: String = "",
    val equalizerOn: Boolean = true,
    val lastSong: String = "",
    val iconPack: String = "SEGAWA Aurora",
    val iconShape: String = "squircle",
    val reduceMotion: Boolean = false,
    val themeId: String = "hiro",
    val customThemeJson: String = "",
    val homeOrder: List<String> = emptyList(),
    val favorites: Set<String> = emptySet(),
    val customIcons: String = "",
    val iconProfile: String = "neon",
    val doubleTapAction: String = "search",
    val ccTiles: String = "Torch,Airplane,Rotate,DND,Cast,Hotspot,Night,Battery",
    val tileOrder: String = "",
    val tilesHidden: Set<String> = emptySet(),
    val playlists: String = "",
    val favTracks: Set<String> = emptySet(),
    val routines: String = "",
    val aiActiveThread: String = "",
    val aiThreads: String = "",
    val aiTts: Boolean = false,
    val aiMemory: String = "",
    val searchWeb: Boolean = true,
    val browserHome: String = "https://duckduckgo.com",
    val browserEngine: String = "DuckDuckGo",
    val bookmarks: String = "",
    val browserHistory: String = "",
    val favWalls: Set<String> = emptySet(),
    val wallHistory: List<String> = emptyList(),
    val wallRotationMin: Int = 0,
    val liveWallMode: String = "off",
    val liveWallVideo: String = "",
    val lockClock: String = "neon",
    val backupInfo: String = "",
) {
    val sortModeEnum: SortMode get() = runCatching { SortMode.valueOf(sortMode) }.getOrDefault(SortMode.ALPHABETICAL)
}

enum class SortMode { ALPHABETICAL, RECENT, CATEGORY }

class MioPrefs(context: Context) {

    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("mio_prefs_v1", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(load())
    val state: StateFlow<MioSettings> = _state

    val current: MioSettings get() = _state.value

    fun edit(block: (MioSettings) -> MioSettings) {
        val next = block(_state.value)
        _state.value = next
        save(next)
    }

    fun setHidden(pkg: String, hidden: Boolean) = edit {
        it.copy(hiddenApps = if (hidden) it.hiddenApps + pkg else it.hiddenApps - pkg)
    }

    fun pushRecent(pkg: String) = edit {
        it.copy(recentApps = (listOf(pkg) + it.recentApps.filter { p -> p != pkg }).take(16))
    }

    /** Serializes every entry of mio_prefs_v1 into a self-describing JSON document. */
    fun exportJson(): String {
        val o = JSONObject()
        sp.all.forEach { (k, v) ->
            when (v) {
                is String -> o.put(k, JSONObject().put("t", "s").put("v", v))
                is Int -> o.put(k, JSONObject().put("t", "i").put("v", v))
                is Long -> o.put(k, JSONObject().put("t", "l").put("v", v))
                is Float -> o.put(k, JSONObject().put("t", "f").put("v", v.toDouble()))
                is Boolean -> o.put(k, JSONObject().put("t", "b").put("v", v))
                is Set<*> -> {
                    val arr = JSONArray()
                    v.forEach { arr.put(it.toString()) }
                    o.put(k, JSONObject().put("t", "ss").put("v", arr))
                }
            }
        }
        return JSONObject().put("mioPrefs", 1).put("entries", o).toString()
    }

    /** Restores a document produced by [exportJson]; reloads live state on success. */
    fun importJson(raw: String): Boolean = runCatching {
        val doc = JSONObject(raw)
        if (doc.optInt("mioPrefs", 0) != 1) return false
        val entries = doc.getJSONObject("entries")
        val ed = sp.edit().clear()
        entries.keys().forEach { k ->
            val e = entries.getJSONObject(k)
            when (e.optString("t")) {
                "s" -> ed.putString(k, e.optString("v", ""))
                "i" -> ed.putInt(k, e.optInt("v", 0))
                "l" -> ed.putLong(k, e.optLong("v", 0L))
                "f" -> ed.putFloat(k, e.optDouble("v", 0.0).toFloat())
                "b" -> ed.putBoolean(k, e.optBoolean("v", false))
                "ss" -> {
                    val arr = e.optJSONArray("v")
                    val set = HashSet<String>()
                    if (arr != null) for (i in 0 until arr.length()) set += arr.optString(i)
                    ed.putStringSet(k, set)
                }
            }
        }
        if (!ed.commit()) return false
        _state.value = load()
        true
    }.getOrDefault(false)

    private fun load(): MioSettings {
        val d = MioSettings()
        return MioSettings(
            accent = sp.getInt("accent", d.accent),
            gridColumns = sp.getInt("cols", d.gridColumns),
            iconScale = sp.getFloat("iconScale", d.iconScale),
            hiddenApps = sp.getStringSet("hidden", emptySet())?.toSet() ?: emptySet(),
            dockApps = sp.getString("dock", null)?.split("|")?.filter { it.isNotBlank() } ?: emptyList(),
            wallpaperId = sp.getString("wallpaper", d.wallpaperId) ?: d.wallpaperId,
            wallpaperUri = sp.getString("wallpaperUri", null),
            dim = sp.getFloat("dim", d.dim),
            userName = sp.getString("userName", d.userName) ?: d.userName,
            city = sp.getString("city", d.city) ?: d.city,
            showWeather = sp.getBoolean("showWeather", d.showWeather),
            animations = sp.getBoolean("animations", d.animations),
            glass = sp.getFloat("glass", d.glass),
            onboarded = sp.getBoolean("onboarded", d.onboarded),
            showLabels = sp.getBoolean("labels", d.showLabels),
            fontScale = sp.getFloat("fontScale", d.fontScale),
            clock24h = sp.getBoolean("clock24", d.clock24h),
            showDock = sp.getBoolean("showDock", d.showDock),
            sortMode = sp.getString("sortMode", d.sortMode) ?: d.sortMode,
            recentApps = sp.getString("recent", null)?.split("|")?.filter { it.isNotBlank() } ?: emptyList(),
            aiEndpoint = sp.getString("aiEndpoint", d.aiEndpoint) ?: d.aiEndpoint,
            aiKey = sp.getString("aiKey", d.aiKey) ?: d.aiKey,
            aiModel = sp.getString("aiModel", d.aiModel) ?: d.aiModel,
            aiHistory = sp.getString("aiHistory", d.aiHistory) ?: d.aiHistory,
            notes = sp.getString("notes", d.notes) ?: d.notes,
            folders = sp.getString("folders", d.folders) ?: d.folders,
            equalizerOn = sp.getBoolean("eq", d.equalizerOn),
            lastSong = sp.getString("lastSong", d.lastSong) ?: d.lastSong,
            iconPack = sp.getString("iconPack", d.iconPack) ?: d.iconPack,
            iconShape = sp.getString("iconShape", d.iconShape) ?: d.iconShape,
            reduceMotion = sp.getBoolean("reduceMotion", d.reduceMotion),
            themeId = sp.getString("themeId", d.themeId) ?: d.themeId,
            customThemeJson = sp.getString("customTheme", d.customThemeJson) ?: d.customThemeJson,
            homeOrder = sp.getString("homeOrder", null)?.split("|")?.filter { it.isNotBlank() } ?: emptyList(),
            favorites = sp.getStringSet("favorites", emptySet())?.toSet() ?: emptySet(),
            customIcons = sp.getString("customIcons", d.customIcons) ?: d.customIcons,
            iconProfile = sp.getString("iconProfile", d.iconProfile) ?: d.iconProfile,
            doubleTapAction = sp.getString("doubleTap", d.doubleTapAction) ?: d.doubleTapAction,
            ccTiles = sp.getString("ccTiles", d.ccTiles) ?: d.ccTiles,
            tileOrder = sp.getString("tileOrder", d.tileOrder) ?: d.tileOrder,
            tilesHidden = sp.getStringSet("tilesHidden", emptySet())?.toSet() ?: emptySet(),
            playlists = sp.getString("playlists", d.playlists) ?: d.playlists,
            favTracks = sp.getStringSet("favTracks", emptySet())?.toSet() ?: emptySet(),
            routines = sp.getString("routines", d.routines) ?: d.routines,
            aiActiveThread = sp.getString("aiThread", d.aiActiveThread) ?: d.aiActiveThread,
            aiThreads = sp.getString("aiThreads", d.aiThreads) ?: d.aiThreads,
            aiTts = sp.getBoolean("aiTts", d.aiTts),
            aiMemory = sp.getString("aiMemory", d.aiMemory) ?: d.aiMemory,
            searchWeb = sp.getBoolean("searchWeb", d.searchWeb),
            browserHome = sp.getString("browserHome", d.browserHome) ?: d.browserHome,
            browserEngine = sp.getString("browserEngine", d.browserEngine) ?: d.browserEngine,
            bookmarks = sp.getString("bookmarks", d.bookmarks) ?: d.bookmarks,
            browserHistory = sp.getString("browserHistory", d.browserHistory) ?: d.browserHistory,
            favWalls = sp.getStringSet("favWalls", emptySet())?.toSet() ?: emptySet(),
            wallHistory = sp.getString("wallHistory", null)?.split("|")?.filter { it.isNotBlank() } ?: emptyList(),
            wallRotationMin = sp.getInt("wallRotation", d.wallRotationMin),
            liveWallMode = sp.getString("liveWall", d.liveWallMode) ?: d.liveWallMode,
            liveWallVideo = sp.getString("liveWallVideo", d.liveWallVideo) ?: d.liveWallVideo,
            lockClock = sp.getString("lockClock", d.lockClock) ?: d.lockClock,
            backupInfo = sp.getString("backupInfo", d.backupInfo) ?: d.backupInfo,
        )
    }

    private fun save(s: MioSettings) {
        sp.edit().apply {
            putInt("accent", s.accent)
            putInt("cols", s.gridColumns)
            putFloat("iconScale", s.iconScale)
            putStringSet("hidden", s.hiddenApps)
            putString("dock", s.dockApps.joinToString("|"))
            putString("wallpaper", s.wallpaperId)
            putString("wallpaperUri", s.wallpaperUri)
            putFloat("dim", s.dim)
            putString("userName", s.userName)
            putString("city", s.city)
            putBoolean("showWeather", s.showWeather)
            putBoolean("animations", s.animations)
            putFloat("glass", s.glass)
            putBoolean("onboarded", s.onboarded)
            putBoolean("labels", s.showLabels)
            putFloat("fontScale", s.fontScale)
            putBoolean("clock24", s.clock24h)
            putBoolean("showDock", s.showDock)
            putString("sortMode", s.sortMode)
            putString("recent", s.recentApps.joinToString("|"))
            putString("aiEndpoint", s.aiEndpoint)
            putString("aiKey", s.aiKey)
            putString("aiModel", s.aiModel)
            putString("aiHistory", s.aiHistory)
            putString("notes", s.notes)
            putString("folders", s.folders)
            putBoolean("eq", s.equalizerOn)
            putString("lastSong", s.lastSong)
            putString("iconPack", s.iconPack)
            putString("iconShape", s.iconShape)
            putBoolean("reduceMotion", s.reduceMotion)
            putString("themeId", s.themeId)
            putString("customTheme", s.customThemeJson)
            putString("homeOrder", s.homeOrder.joinToString("|"))
            putStringSet("favorites", s.favorites)
            putString("customIcons", s.customIcons)
            putString("iconProfile", s.iconProfile)
            putString("doubleTap", s.doubleTapAction)
            putString("ccTiles", s.ccTiles)
            putString("tileOrder", s.tileOrder)
            putStringSet("tilesHidden", s.tilesHidden)
            putString("playlists", s.playlists)
            putStringSet("favTracks", s.favTracks)
            putString("routines", s.routines)
            putString("aiThread", s.aiActiveThread)
            putString("aiThreads", s.aiThreads)
            putBoolean("aiTts", s.aiTts)
            putString("aiMemory", s.aiMemory)
            putBoolean("searchWeb", s.searchWeb)
            putString("browserHome", s.browserHome)
            putString("browserEngine", s.browserEngine)
            putString("bookmarks", s.bookmarks)
            putString("browserHistory", s.browserHistory)
            putStringSet("favWalls", s.favWalls)
            putString("wallHistory", s.wallHistory.joinToString("|"))
            putInt("wallRotation", s.wallRotationMin)
            putString("liveWall", s.liveWallMode)
            putString("liveWallVideo", s.liveWallVideo)
            putString("lockClock", s.lockClock)
            putString("backupInfo", s.backupInfo)
        }.apply()
    }
}

/** Tiny service locator – one process, one launcher, no DI framework needed. */
object Mio {
    lateinit var application: Application
        private set
    lateinit var prefs: MioPrefs
        private set
    lateinit var apps: AppsRepository
        private set
    lateinit var media: MediaRepository
        private set

    val context: Context get() = application.applicationContext

    fun init(app: Application) {
        application = app
        prefs = MioPrefs(app)
        apps = AppsRepository(app)
        media = MediaRepository(app)
    }
}
