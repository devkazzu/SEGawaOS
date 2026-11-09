package com.mio.os.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class RoutinesTest {

    private fun routine() = Routine(
        id = "r1", name = "Morning", kind = "music",
        hour = 8, minute = 5, days = setOf(Calendar.MONDAY),
        payload = "", enabled = true,
    )

    @Test
    fun allEmptyAndGarbage() {
        assertTrue(Routines.all("").isEmpty())
        assertTrue(Routines.all("[").isEmpty())
    }

    @Test
    fun persistAllRoundTrip() {
        val back = Routines.all(Routines.persist(listOf(routine())))
        assertEquals(1, back.size)
        assertEquals(routine(), back.first())
    }

    @Test
    fun dueMatchesExactSchedule() {
        assertTrue(Routines.due(routine(), 8, 5, Calendar.MONDAY))
    }

    @Test
    fun dueRejectsMismatch() {
        assertFalse(Routines.due(routine(), 8, 6, Calendar.MONDAY))
        assertFalse(Routines.due(routine(), 8, 5, Calendar.TUESDAY))
        assertFalse(Routines.due(routine().copy(enabled = false), 8, 5, Calendar.MONDAY))
    }

    @Test
    fun dueDailyIgnoresWeekday() {
        val daily = routine().copy(days = emptySet())
        assertTrue(Routines.due(daily, 8, 5, Calendar.SUNDAY))
        assertTrue(Routines.due(daily, 8, 5, Calendar.SATURDAY))
    }

    @Test
    fun describeMentionsTimeDaysAndKind() {
        val text = Routines.describe(routine())
        assertTrue(text.contains("08:05"))
        assertTrue(text.contains("Mon"))
        assertTrue(text.contains("Play music"))
        assertTrue(Routines.describe(routine().copy(days = emptySet())).contains("daily"))
        assertTrue(Routines.describe(routine().copy(kind = "reminder")).contains("Reminder"))
    }
}
