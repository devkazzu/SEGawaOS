package com.mio.os.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WallpaperRepoTest {

    @Test
    fun nextRotationCyclesBuiltIns() {
        val all = WallpaperRepo.builtIns
        assertEquals(all[1], WallpaperRepo.nextRotation(all[0], emptySet()))
        assertEquals(all[0], WallpaperRepo.nextRotation(all.last(), emptySet()))
    }

    @Test
    fun nextRotationUnknownCurrentReturnsFirst() {
        assertEquals(WallpaperRepo.builtIns.first(), WallpaperRepo.nextRotation("custom-uri", emptySet()))
    }

    @Test
    fun nextRotationUsesFavoritesPool() {
        val favs = setOf("aurora", "violet")
        assertEquals("violet", WallpaperRepo.nextRotation("aurora", favs))
        assertEquals("aurora", WallpaperRepo.nextRotation("violet", favs))
    }

    @Test
    fun nextRotationIgnoresUnknownFavorites() {
        val favs = setOf("deleted-id")
        assertEquals(
            WallpaperRepo.builtIns.first(),
            WallpaperRepo.nextRotation("deleted-id", favs),
        )
    }

    @Test
    fun pushHistoryIsMostRecentFirstWithoutDupes() {
        assertEquals(listOf("b", "a"), WallpaperRepo.pushHistory(listOf("a"), "b"))
        assertEquals(listOf("a", "b"), WallpaperRepo.pushHistory(listOf("b", "a"), "a"))
    }

    @Test
    fun pushHistoryRespectsCap() {
        val history = (1..30).map { "w$it" }
        val next = WallpaperRepo.pushHistory(history, "new", max = 5)
        assertEquals(5, next.size)
        assertEquals("new", next.first())
    }
}
