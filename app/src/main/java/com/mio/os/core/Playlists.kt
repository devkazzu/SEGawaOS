package com.mio.os.core

import org.json.JSONArray
import org.json.JSONObject

/** User playlists: an id, a display name and MediaStore audio ids. */
data class Playlist(val id: String, val name: String, val songIds: List<Long>)

object Playlists {

    fun all(raw: String): List<Playlist> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val ids = o.optJSONArray("songs")
                val songIds = ArrayList<Long>()
                if (ids != null) for (k in 0 until ids.length()) songIds += ids.optLong(k)
                Playlist(
                    id = o.optString("id", "$i"),
                    name = o.optString("name", "Playlist"),
                    songIds = songIds,
                )
            }
        }.getOrDefault(emptyList())
    }

    fun persist(list: List<Playlist>): String {
        val arr = JSONArray()
        list.forEach { p ->
            val songs = JSONArray()
            p.songIds.forEach { songs.put(it) }
            arr.put(JSONObject().put("id", p.id).put("name", p.name).put("songs", songs))
        }
        return arr.toString()
    }

    fun create(list: List<Playlist>, name: String): List<Playlist> =
        list + Playlist(
            id = System.currentTimeMillis().toString(),
            name = name.trim().take(40).ifBlank { "Playlist" },
            songIds = emptyList(),
        )

    fun addSong(list: List<Playlist>, id: String, songId: Long): List<Playlist> =
        list.map {
            if (it.id == id && songId !in it.songIds) it.copy(songIds = it.songIds + songId) else it
        }

    fun removeSong(list: List<Playlist>, id: String, songId: Long): List<Playlist> =
        list.map { if (it.id == id) it.copy(songIds = it.songIds - songId) else it }

    fun delete(list: List<Playlist>, id: String): List<Playlist> =
        list.filterNot { it.id == id }
}
