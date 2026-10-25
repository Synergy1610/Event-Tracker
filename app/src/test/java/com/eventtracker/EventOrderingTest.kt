package com.eventtracker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class EventOrderingTest {
    private val today = LocalDate.of(2026, 10, 24)

    @Test
    fun usesUpcomingDeadlineBeforeEventDateAndSwitchesAfterDeadline() {
        val event = TrackedEvent(
            name = "Workshop",
            eventDate = "2026-11-10",
            registrationDeadline = "2026-10-25",
        )

        assertEquals(LocalDate.of(2026, 10, 25), primaryDateFor(event, today))
        assertEquals(LocalDate.of(2026, 11, 10), primaryDateFor(event, LocalDate.of(2026, 10, 26)))
    }

    @Test
    fun closedDeadlineWithoutEventDateMovesToPendingSection() {
        val event = TrackedEvent(name = "TBD event", registrationDeadline = "2026-10-23")

        assertTrue(isWaitingForEventDate(event, today))
        assertFalse(isWaitingForEventDate(event, LocalDate.of(2026, 10, 23)))
        assertEquals(listOf(event), sortedPendingDateEvents(listOf(event), today))
        assertTrue(sortedActiveEvents(listOf(event), today).isEmpty())
    }

    @Test
    fun homepageIncludesAllItemsOnTheNearestDate() {
        val first = TrackedEvent(name = "Alpha", eventDate = "2026-10-25")
        val sameDate = TrackedEvent(name = "Beta", registrationDeadline = "2026-10-25")
        val later = TrackedEvent(name = "Later", eventDate = "2026-10-26")

        assertEquals(listOf(first, sameDate), upcomingEventsForDate(listOf(later, sameDate, first), today))
    }
}
