package com.mio.os.core

/**
 * Universal-search index. Pure functions over plain data so the ranking is
 * unit-testable; the UI adapts launcher objects to these entry lists.
 *
 * Hit kinds: "app", "note", "music", "wallpaper", "setting". Payload holds the
 * package name, note id, track id, wallpaper id or settings screen id.
 */
data class SearchHit(
    val kind: String,
    val title: String,
    val subtitle: String,
    val payload: String,
)

object SearchIndex {

    fun search(
        query: String,
        apps: List<Pair<String, String>>,
        notes: List<Pair<Long, String>>,
        tracks: List<Pair<Long, String>>,
        walls: List<Pair<String, String>>,
        settings: List<Pair<String, String>>,
        limit: Int = 24,
    ): List<SearchHit> {
        val q = query.trim().lowercase()
        if (q.length < 2) return emptyList()
        val scored = ArrayList<Pair<Int, SearchHit>>()
        apps.forEach { (pkg, name) ->
            val r = rank(name, q)
            if (r >= 0) scored += r to SearchHit("app", name, pkg, pkg)
        }
        notes.forEach { (id, text) ->
            if (text.contains(q, true)) {
                scored += 3 to SearchHit("note", text.take(48), "Note", id.toString())
            }
        }
        tracks.forEach { (id, label) ->
            val r = rank(label, q)
            if (r >= 0) {
                scored += (r + 1) to SearchHit(
                    "music",
                    label.substringBefore(" — ").take(48),
                    "Music",
                    id.toString(),
                )
            }
        }
        walls.forEach { (id, label) ->
            if (label.contains(q, true) || id.contains(q, true)) {
                scored += 3 to SearchHit("wallpaper", label, "Wallpaper", id)
            }
        }
        settings.forEach { (screen, title) ->
            if (title.contains(q, true)) {
                scored += 2 to SearchHit("setting", title, "Setting", screen)
            }
        }
        return scored.sortedBy { it.first }.map { it.second }.take(limit)
    }

    private fun rank(name: String, q: String): Int = when {
        name.equals(q, true) -> 0
        name.startsWith(q, true) -> 1
        name.contains(q, true) -> 2
        else -> -1
    }
}
