package com.mio.os.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IconStudioTest {

    @Test
    fun encodeDecodeRoundTrip() {
        val map = mapOf(
            "com.example.app" to "content://media/images/1",
            "com.other" to "content://a b/c?x=1&y=2",
        )
        assertEquals(map, IconStudio.decode(IconStudio.encode(map)))
    }

    @Test
    fun decodeBlankIsEmpty() {
        assertTrue(IconStudio.decode("").isEmpty())
        assertTrue(IconStudio.decode("   ").isEmpty())
    }

    @Test
    fun decodeSkipsBadParts() {
        val back = IconStudio.decode("abc&=x&ok=1")
        assertEquals(mapOf("ok" to "1"), back)
    }

    @Test
    fun profileKnownAndFallback() {
        assertEquals("Glass", IconStudio.profile("glass").name)
        assertEquals(IconProfiles.first(), IconStudio.profile("nope"))
    }
}
