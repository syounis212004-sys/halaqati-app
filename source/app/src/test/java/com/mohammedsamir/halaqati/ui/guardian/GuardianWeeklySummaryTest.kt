package com.mohammedsamir.halaqati.ui.guardian

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GuardianWeeklySummaryTest {
    private fun overview(lines: List<GuardianRecitationLine>) = GuardianOverview(
        childName = "طالب",
        tathbit = false,
        trackLabel = "الحفظ العام",
        subtitle = "",
        todayAttendance = null,
        todaySessionExists = null,
        todaySessionStarted = null,
        todayRecited = false,
        todayPages = 0.0,
        todayRange = null,
        planTitle = null,
        planActivities = emptyList(),
        monthMain = 0.0,
        monthMainLabel = "الحفظ",
        monthPresent = 0,
        monthSessions = 0,
        quality = null,
        latest = lines,
        updatedAt = null,
    )

    private fun line(date: String, pages: Double, quality: Double?) =
        GuardianRecitationLine(date = date, activityLabel = "حفظ", range = "x", pages = pages, quality = quality)

    @Test fun countsOnlyTheLastSevenDays() {
        val today = LocalDate.of(2026, 10, 20)
        val d = GuardianWeeklySummary.digest(
            overview(listOf(line("2026-10-20", 2.0, 80.0), line("2026-10-18", 1.0, 100.0), line("2026-10-10", 5.0, 50.0))),
            today,
        )
        assertEquals(2, d.recitations)
        assertEquals(2, d.days)
        assertEquals(3.0, d.pages, 0.0001)
        assertEquals(90.0, d.quality ?: -1.0, 0.0001)
    }

    @Test fun emptyWeekHasNoQuality() {
        val d = GuardianWeeklySummary.digest(overview(emptyList()), LocalDate.of(2026, 10, 20))
        assertEquals(0, d.recitations)
        assertNull(d.quality)
    }
}
