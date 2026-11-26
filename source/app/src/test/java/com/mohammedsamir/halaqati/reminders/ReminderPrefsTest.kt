package com.mohammedsamir.halaqati.reminders

import androidx.test.core.app.ApplicationProvider
import java.time.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class ReminderPrefsTest {
    private val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test fun firstRunIsTheDefaultAndSavedChoicesRoundTrip() {
        assertEquals(Reminder.DEFAULT, ReminderPrefs.load(ctx))
        val r = Reminder(false, setOf(DayOfWeek.SUNDAY, DayOfWeek.WEDNESDAY), 21, 45)
        ReminderPrefs.save(ctx, r)
        assertEquals(r, ReminderPrefs.load(ctx))
    }

    @Test fun roleIsKeptUntilSignOutClearsIt() {
        ReminderPrefs.setRole(ctx, "GUARDIAN"); assertEquals("GUARDIAN", ReminderPrefs.role(ctx))
        ReminderPrefs.setRole(ctx, null); assertTrue(ReminderPrefs.role(ctx).isEmpty())
    }
}
