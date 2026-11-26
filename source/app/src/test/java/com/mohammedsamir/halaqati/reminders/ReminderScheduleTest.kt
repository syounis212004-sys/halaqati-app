package com.mohammedsamir.halaqati.reminders

import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** When the next reminder fires. Pure date maths; the WorkManager call itself is a thin wrapper around it. */
class ReminderScheduleTest {
    private val gaza = ZoneId.of("Asia/Gaza")
    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int) = ZonedDateTime.of(y, mo, d, h, mi, 0, 0, gaza)
    private val thursday6pm = Reminder(true, setOf(DayOfWeek.THURSDAY), 18, 0)

    @Test fun defaultIsOnThursdayAtSixPm() {
        assertEquals(thursday6pm, Reminder.DEFAULT)
    }

    @Test fun sameDayLaterTodayFires() {
        // Thursday 26 Nov 2026, 09:00 -> today 18:00
        assertEquals(at(2026, 11, 26, 18, 0), ReminderScheduler.nextAt(at(2026, 11, 26, 9, 0), thursday6pm))
    }

    @Test fun timeAlreadyPassedMovesToNextWeek() {
        assertEquals(at(2026, 12, 3, 18, 0), ReminderScheduler.nextAt(at(2026, 11, 26, 18, 0), thursday6pm))
        assertEquals(at(2026, 12, 3, 18, 0), ReminderScheduler.nextAt(at(2026, 11, 26, 20, 30), thursday6pm))
    }

    @Test fun pickedDaysAreAllConsidered() {
        val r = Reminder(true, setOf(DayOfWeek.SATURDAY, DayOfWeek.TUESDAY), 7, 30)
        // Thursday -> next is Saturday 28 Nov
        assertEquals(at(2026, 11, 28, 7, 30), ReminderScheduler.nextAt(at(2026, 11, 26, 20, 0), r))
        // Saturday after the time -> Tuesday 1 Dec
        assertEquals(at(2026, 12, 1, 7, 30), ReminderScheduler.nextAt(at(2026, 11, 28, 8, 0), r))
    }

    @Test fun everyDayAfterTheTimeGoesToTomorrow() {
        val r = Reminder(true, DayOfWeek.entries.toSet(), 6, 0)
        assertEquals(at(2026, 11, 27, 6, 0), ReminderScheduler.nextAt(at(2026, 11, 26, 6, 1), r))
    }

    @Test fun offOrNoDayMeansNothingIsScheduled() {
        assertNull(ReminderScheduler.nextAt(at(2026, 11, 26, 9, 0), thursday6pm.copy(enabled = false)))
        assertNull(ReminderScheduler.nextAt(at(2026, 11, 26, 9, 0), thursday6pm.copy(days = emptySet())))
    }

    @Test fun textsExistOnlyForTheRolesThatCanActOnThem() {
        assertEquals("تكفّل بحلقة أبنائك", ReminderTexts.forRole("GUARDIAN")?.first)
        for (r in listOf("TEACHER", "SUPERVISOR", "ADMIN")) assertEquals("أنشئ مسابقة لحلقتك", ReminderTexts.forRole(r)?.first)
        for (r in listOf("OBSERVER", "STUDENT", "", "UNKNOWN")) assertNull(r, ReminderTexts.forRole(r))
    }
}
