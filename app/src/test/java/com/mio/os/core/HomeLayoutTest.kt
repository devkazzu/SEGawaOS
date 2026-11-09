package com.mio.os.core

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeLayoutTest {

    @Test
    fun orderKeysEmptyOrderKeepsGiven() {
        val keys = listOf("a", "b", "c")
        assertEquals(keys, HomeLayout.orderKeys(keys, emptyList()))
    }

    @Test
    fun orderKeysArrangesThenAppendsNew() {
        val keys = listOf("a", "b", "c", "new")
        assertEquals(listOf("c", "a", "b", "new"), HomeLayout.orderKeys(keys, listOf("c", "a")))
    }

    @Test
    fun orderKeysIgnoresUnknownAndDupes() {
        assertEquals(
            listOf("b", "a"),
            HomeLayout.orderKeys(listOf("a", "b"), listOf("b", "ghost", "b")),
        )
    }

    @Test
    fun moveRelocatesItem() {
        assertEquals(listOf("b", "c", "a"), HomeLayout.move(listOf("a", "b", "c"), 0, 2))
        assertEquals(listOf("c", "a", "b"), HomeLayout.move(listOf("a", "b", "c"), 2, 0))
    }

    @Test
    fun moveOutOfRangeReturnsList() {
        val list = listOf("a", "b")
        assertEquals(list, HomeLayout.move(list, -1, 0))
        assertEquals(list, HomeLayout.move(list, 0, 5))
        assertEquals(list, HomeLayout.move(list, 1, 1))
    }
}
