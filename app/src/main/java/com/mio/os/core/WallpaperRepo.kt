package com.mio.os.core

/** Pure wallpaper-studio helpers: rotation pools and recency history. */
object WallpaperRepo {

    val builtIns = listOf(
        "hiro_sakura", "neon_tokyo", "sakura_void",
        "mountain", "anime", "aurora", "violet",
    )

    /**
     * Next wallpaper for auto-rotation: cycles the favourites when the user
     * has any, otherwise cycles every bundled wallpaper.
     */
    fun nextRotation(current: String, favorites: Set<String>): String {
        val pool = if (favorites.isNotEmpty()) {
            builtIns.filter { it in favorites }.ifEmpty { builtIns }
        } else {
            builtIns
        }
        val i = pool.indexOf(current)
        return if (i < 0) pool.first() else pool[(i + 1) % pool.size]
    }

    /** Most-recent-first history without duplicates, capped at [max]. */
    fun pushHistory(history: List<String>, id: String, max: Int = 20): List<String> =
        (listOf(id) + history.filter { it != id }).take(max)
}
