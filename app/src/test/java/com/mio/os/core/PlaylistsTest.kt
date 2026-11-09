package com.mio.os.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistsTest {

    @Test
    fun allEmptyAndGarbage() {
        assertTrue(Playlists.all("").isEmpty())
        assertTrue(Playlists.all("   ").isEmpty())
        assertTrue(Playlists.all("not json").isEmpty())
    }

    @Test
    fun createAddsPlaylist() {
        val next = Playlists.create(emptyList(), "  Road trip  ")
        assertEquals(1, next.size)
        assertEquals("Road trip", next.first().name)
        assertTrue(next.first().songIds.isEmpty())
    }

    @Test
    fun createBlankNameFallsBack() {
        assertEquals("Playlist", Playlists.create(emptyList(), "   ").first().name)
    }

    @Test
    fun addSongSkipsDuplicates() {
        var list = Playlists.create(emptyList(), "Mix")
        val id = list.first().id
        list = Playlists.addSong(list, id, 42L)
        list = Playlists.addSong(list, id, 42L)
        list = Playlists.addSong(list, id, 7L)
        assertEquals(listOf(42L, 7L), list.first().songIds)
    }

    @Test
    fun removeSongKeepsOthers() {
        var list = Playlists.create(emptyList(), "Mix")
        val id = list.first().id
        list = Playlists.addSong(Playlists.addSong(list, id, 1L), id, 2L)
        list = Playlists.removeSong(list, id, 1L)
        assertEquals(listOf(2L), list.first().songIds)
    }

    @Test
    fun deleteRemovesPlaylist() {
        var list = Playlists.create(Playlists.create(emptyList(), "A"), "B")
        list = Playlists.delete(list, list.first().id)
        assertEquals(listOf("B"), list.map { it.name })
    }

    @Test
    fun persistAllRoundTrip() {
        var list = Playlists.create(emptyList(), "Evening")
        list = Playlists.addSong(list, list.first().id, 99L)
        val back = Playlists.all(Playlists.persist(list))
        assertEquals(1, back.size)
        assertEquals("Evening", back.first().name)
        assertEquals(listOf(99L), back.first().songIds)
    }
}
