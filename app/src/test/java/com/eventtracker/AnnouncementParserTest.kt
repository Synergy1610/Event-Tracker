package com.eventtracker

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class AnnouncementParserTest {
    @Test
    fun extractsEventDeadlineTimesAndRegistrationUrl() {
        val suggestions = AnnouncementParser.parse(
            """Campus Tech Fest
Registration closes on 20 Oct 2026 at 5:30 PM.
The event will be held on 24 October 2026 at 7 PM.
Register here: https://college.example/register""",
            today = LocalDate.of(2026, 1, 1),
        )

        assertEquals("Campus Tech Fest", suggestions.name)
        assertEquals("24/10/2026", suggestions.eventDate)
        assertEquals("19:00", suggestions.eventTime)
        assertEquals("20/10/2026", suggestions.registrationDeadline)
        assertEquals("17:30", suggestions.deadlineTime)
        assertEquals("https://college.example/register", suggestions.link)
    }

    @Test
    fun defaultsAmbiguousNumericDatesToDayMonthAndSupports24HourTime() {
        val suggestions = AnnouncementParser.parse(
            "Registration deadline: 10/11 at 23:59",
            today = LocalDate.of(2026, 1, 1),
        )

        assertEquals("", suggestions.eventDate)
        assertEquals("10/11/2026", suggestions.registrationDeadline)
        assertEquals("23:59", suggestions.deadlineTime)
    }

    @Test
    fun usesNearbyLabelsAndTimesOnFollowingLines() {
        val suggestions = AnnouncementParser.parse(
            """Career Workshop
Registration deadline:
15 Nov 2026
Time: 5 PM
Event date:
20 Nov 2026
Time: 9:30 AM""",
            today = LocalDate.of(2026, 1, 1),
        )

        assertEquals("15/11/2026", suggestions.registrationDeadline)
        assertEquals("17:00", suggestions.deadlineTime)
        assertEquals("20/11/2026", suggestions.eventDate)
        assertEquals("09:30", suggestions.eventTime)
    }
}
