package com.mio.os.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchIndexTest {

    private fun run(
        q: String,
        apps: List<Pair<String, String>> = emptyList(),
        notes: List<Pair<Long, String>> = emptyList(),
        tracks: List<Pair<Long, String>> = emptyList(),
        walls: List<Pair<String, String>> = emptyList(),
        settings: List<Pair<String, String>> = emptyList(),
        limit: Int = 24,
    ) = SearchIndex.search(q, apps, notes, tracks, walls, settings, limit)

    @Test
    fun shortQueryReturnsEmpty() {
        val apps = listOf("a" to "Camera")
        assertTrue(run("", apps).isEmpty())
        assertTrue(run("c", apps).isEmpty())
        assertTrue(run("  ", apps).isEmpty())
    }

    @Test
    fun exactMatchRanksFirst() {
        val apps = listOf("m" to "MyCameraPro", "c" to "Camera", "x" to "CameraX")
        val hits = run("camera", apps)
        assertEquals(listOf("Camera", "CameraX", "MyCameraPro"), hits.map { it.title })
        assertTrue(hits.all { it.kind == "app" })
    }

    @Test
    fun noMatchReturnsEmpty() {
        assertTrue(run("zzz", listOf("a" to "Camera")).isEmpty())
    }

    @Test
    fun noteHitUsesPayloadId() {
        val hits = run("milk", notes = listOf(7L to "buy milk tomorrow"))
        assertEquals(1, hits.size)
        assertEquals("note", hits.first().kind)
        assertEquals("7", hits.first().payload)
    }

    @Test
    fun trackHitSplitsTitle() {
        val hits = run("midnight", tracks = listOf(3L to "Midnight drive — Neon Coast"))
        assertEquals(1, hits.size)
        assertEquals("music", hits.first().kind)
        assertEquals("Midnight drive", hits.first().title)
    }

    @Test
    fun wallMatchesByIdOrLabel() {
        val walls = listOf("neon_tokyo" to "Neon Tokyo")
        assertEquals(1, run("neon", walls = walls).size)
        assertEquals(1, run("tokyo", walls = walls).size)
    }

    @Test
    fun settingHitCarriesScreen() {
        val hits = run("privacy", settings = listOf("PRIVACY" to "Privacy & permissions"))
        assertEquals("PRIVACY", hits.first().payload)
    }

    @Test
    fun limitIsRespected() {
        val apps = (1..30).map { "pkg$it" to "Camera app $it" }
        assertEquals(5, run("camera", apps, limit = 5).size)
    }
}
