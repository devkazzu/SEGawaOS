package com.mio.os.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsTest {

    @Test
    fun parseStandardLrc() {
        val lines = Lyrics.parse("[00:12.34]Hello\n[01:02.500]World\n")
        assertEquals(2, lines.size)
        assertEquals(12_340L, lines[0].ms)
        assertEquals("Hello", lines[0].text)
        assertEquals(62_500L, lines[1].ms)
    }

    @Test
    fun parseSortsUnsorted() {
        val lines = Lyrics.parse("[00:30.00]B\n[00:10.00]A\n")
        assertEquals(listOf("A", "B"), lines.map { it.text })
    }

    @Test
    fun parseSkipsMalformed() {
        val lines = Lyrics.parse("no stamp here\n[ab] nope\n[00:01.00]\n[00:02.00]Ok\n")
        assertEquals(1, lines.size)
        assertEquals("Ok", lines.first().text)
    }

    @Test
    fun parseExpandsMultipleStamps() {
        val lines = Lyrics.parse("[00:01.00][00:31.00]Chorus\n")
        assertEquals(listOf(1_000L, 31_000L), lines.map { it.ms })
        assertTrue(lines.all { it.text == "Chorus" })
    }

    @Test
    fun lrcNameForReplacesExtension() {
        assertEquals("song.lrc", Lyrics.lrcNameFor("song.mp3"))
        assertEquals("noext.lrc", Lyrics.lrcNameFor("noext"))
        assertEquals("a.b.lrc", Lyrics.lrcNameFor("a.b.flac"))
    }
}
