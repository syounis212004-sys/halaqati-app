package com.mohammedsamir.halaqati.ui.dashboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DailyOperationStateEngineTest {
    @Test fun offDayHasNoExpectedStudentsOrDailyMetrics() {
        val state = DailyOperationStateEngine.evaluate(setOf("1", "2"), emptyList(), emptyList())
        assertEquals(DailyOperationStateEngine.Mode.OFF_DAY, state.mode)
        assertFalse(state.countDailyMetrics)
        assertTrue(state.expectedStudentIds.isEmpty())
    }

    @Test fun scheduledDayWithoutStartedSessionDoesNotMarkEveryoneNotHeard() {
        val state = DailyOperationStateEngine.evaluate(
            activeStudentIds = setOf("1", "2"),
            scheduled = listOf(
                DailyOperationStateEngine.ScheduledActivity("1", DailyOperationStateEngine.Activity.HIFZ),
                DailyOperationStateEngine.ScheduledActivity("2", DailyOperationStateEngine.Activity.HIFZ),
            ),
            sessions = emptyList(),
        )
        assertEquals(DailyOperationStateEngine.Mode.NO_SESSION_YET, state.mode)
        assertFalse(state.countDailyMetrics)
        assertEquals(setOf("1", "2"), state.expectedStudentIds)
    }

    @Test fun openSessionEnablesMetricsOnlyForScheduledStudents() {
        val state = DailyOperationStateEngine.evaluate(
            activeStudentIds = setOf("1", "2", "3"),
            scheduled = listOf(
                DailyOperationStateEngine.ScheduledActivity("1", DailyOperationStateEngine.Activity.SARD),
                DailyOperationStateEngine.ScheduledActivity("2", DailyOperationStateEngine.Activity.SARD),
            ),
            sessions = listOf(DailyOperationStateEngine.SessionSignal("sard", "open")),
        )
        assertEquals(DailyOperationStateEngine.Mode.SARD_DAY, state.mode)
        assertTrue(state.countDailyMetrics)
        assertEquals(setOf("1", "2"), state.expectedStudentIds)
    }
    @Test fun saturdayIsOffWhenNotInConfiguredWeekdays() {
        assertFalse(
            DailyOperationStateEngine.isScheduledDay(
                date = LocalDate.of(2026, 9, 26),
                start = LocalDate.of(2026, 9, 1),
                end = LocalDate.of(2026, 9, 30),
                scheduleWeekdays = setOf(0, 1, 2, 3, 4),
                catchUpWeekday = null,
            )
        )
    }

}
