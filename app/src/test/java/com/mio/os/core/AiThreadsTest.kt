package com.mio.os.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiThreadsTest {

    private fun thread(id: String, updated: Long) = AiThread(
        id = id, title = "Chat $id",
        messages = listOf(
            AiMessage("user", "hello there", 1L),
            AiMessage("assistant", "hi!", 2L, local = false),
        ),
        updated = updated,
    )

    @Test
    fun allEmptyAndGarbage() {
        assertTrue(AiThreads.all("").isEmpty())
        assertTrue(AiThreads.all("{oops").isEmpty())
    }

    @Test
    fun persistAllRoundTrip() {
        val list = listOf(thread("a", 2L), thread("b", 1L))
        val back = AiThreads.all(AiThreads.persist(list))
        assertEquals(listOf("a", "b"), back.map { it.id })
        assertEquals("Chat a", back.first().title)
        assertEquals(2, back.first().messages.size)
        assertEquals(false, back.first().messages[1].local)
        assertEquals("hi!", back.first().messages[1].text)
    }

    @Test
    fun persistSortsNewestFirst() {
        val back = AiThreads.all(AiThreads.persist(listOf(thread("old", 1L), thread("new", 9L))))
        assertEquals(listOf("new", "old"), back.map { it.id })
    }

    @Test
    fun titleForUsesFirstWords() {
        assertEquals("one two three four five six", AiThreads.titleFor("one two three four five six seven"))
        assertEquals("Chat", AiThreads.titleFor("   "))
        assertEquals(40, AiThreads.titleFor("x".repeat(100)).length)
    }

    @Test
    fun freshIsEmpty() {
        val t = AiThreads.fresh()
        assertTrue(t.messages.isEmpty())
        assertTrue(t.id.isNotBlank())
    }
}
