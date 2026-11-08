package com.mio.os.core

import org.json.JSONObject

/**
 * One SEGAWA design-engine theme: a named preset that applies an accent
 * colour, a secondary gradient colour, a wallpaper and lighting values in a
 * single tap. Themes are stored as [MioSettings.themeId] plus the resolved
 * values, so a theme survives process death and still honours later manual
 * tweaks to the accent or wallpaper.
 *
 * Export/import uses a small self-describing JSON document (no new
 * dependency: `org.json` ships with Android) so themes can be shared as text.
 */
data class SegaThemeSpec(
    val id: String,
    val name: String,
    val accent: Int,
    val accent2: Int,
    val wallpaper: String,
    val dim: Float,
    val glass: Float,
) {
    fun toJson(): String = JSONObject()
        .put("segawaTheme", 1)
        .put("id", id)
        .put("name", name)
        .put("accent", hex(accent))
        .put("accent2", hex(accent2))
        .put("wallpaper", wallpaper)
        .put("dim", dim.toDouble())
        .put("glass", glass.toDouble())
        .toString()

    companion object {
        fun fromJson(raw: String): SegaThemeSpec? = runCatching {
            val o = JSONObject(raw)
            if (o.optInt("segawaTheme", 0) != 1) return null
            SegaThemeSpec(
                id = o.optString("id", "custom").take(24).ifBlank { "custom" },
                name = o.optString("name", "Custom").take(32).ifBlank { "Custom" },
                accent = parseColor(o.optString("accent", "#FFFF4FA3")),
                accent2 = parseColor(o.optString("accent2", "#FF7C5CFF")),
                wallpaper = o.optString("wallpaper", "hiro_sakura").take(32),
                dim = o.optDouble("dim", 0.25).toFloat().coerceIn(0f, 0.6f),
                glass = o.optDouble("glass", 1.0).toFloat().coerceIn(0f, 1f),
            )
        }.getOrNull()

        private fun hex(color: Int): String =
            "#" + color.toUInt().toString(16).uppercase().padStart(8, '0')

        private fun parseColor(raw: String): Int = runCatching {
            var c = raw.trim().removePrefix("#")
            if (c.length == 6) c = "FF$c"
            c.toLong(16).toInt()
        }.getOrDefault(0xFFFF4FA3.toInt())
    }
}

/** The eight built-in SEGAWA themes. Index 7 ("custom") holds imports. */
val SegaThemes: List<SegaThemeSpec> = listOf(
    SegaThemeSpec("hiro", "Hiro Segawa", 0xFFFF4FA3.toInt(), 0xFF7C5CFF.toInt(), "hiro_sakura", 0.25f, 1f),
    SegaThemeSpec("anime_pink", "Anime Pink", 0xFFF472B6.toInt(), 0xFFFF4FA3.toInt(), "sakura_void", 0.22f, 1f),
    SegaThemeSpec("purple_neon", "Purple Neon", 0xFF7C5CFF.toInt(), 0xFFA855F7.toInt(), "violet", 0.25f, 1f),
    SegaThemeSpec("cyberpunk", "Cyberpunk", 0xFF00E5FF.toInt(), 0xFFFF2E88.toInt(), "neon_tokyo", 0.30f, 0.9f),
    SegaThemeSpec("amoled", "AMOLED Black", 0xFF8B7CFF.toInt(), 0xFF3C7DFF.toInt(), "sakura_void", 0.40f, 0.6f),
    SegaThemeSpec("glass", "Glass", 0xFF7EE7FF.toInt(), 0xFF4D8DFF.toInt(), "aurora", 0.15f, 1f),
    SegaThemeSpec("minimal", "Minimal", 0xFFE9D8A6.toInt(), 0xFF9C94B8.toInt(), "mountain", 0.20f, 0.7f),
    SegaThemeSpec("custom", "Custom", 0xFFFF4FA3.toInt(), 0xFF7C5CFF.toInt(), "hiro_sakura", 0.25f, 1f),
)

/** Applies the preset: remembers the theme id and resolves every value. */
fun SegaThemeSpec.applyTo(prefs: MioPrefs) {
    prefs.edit {
        it.copy(
            themeId = id,
            accent = accent,
            wallpaperId = wallpaper,
            wallpaperUri = null,
            dim = dim,
            glass = glass,
        )
    }
}
