package com.mohammedsamir.halaqati.reminders

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * Schedules the next reminder as ONE WorkManager job with an initial delay; when it runs it shows the notification and schedules the next one.
 * WorkManager (not an exact alarm) because a reminder does not need to-the-minute timing and exact alarms need a permission the user must grant.
 * Typical drift is a few minutes; battery saving can defer it a little more.
 */
object ReminderScheduler {
    const val WORK_NAME = "halaqati_contribution_reminder"

    /** The first moment after [now] that matches the reminder, or null when it is off or has no day chosen. */
    fun nextAt(now: ZonedDateTime, r: Reminder): ZonedDateTime? {
        if (!r.enabled || r.days.isEmpty()) return null
        for (i in 0..7) {
            val day = now.toLocalDate().plusDays(i.toLong())
            if (day.dayOfWeek !in r.days) continue
            val at = day.atTime(r.hour, r.minute).atZone(now.zone)
            if (at.isAfter(now)) return at
        }
        return null
    }

    /** Reads the saved choice and (re)schedules, or cancels when it is off or nobody is signed in. Safe to call any time. */
    fun apply(ctx: Context, now: ZonedDateTime = ZonedDateTime.now()) {
        val wm = WorkManager.getInstance(ctx.applicationContext)
        val next = if (ReminderPrefs.role(ctx).isBlank()) null else nextAt(now, ReminderPrefs.load(ctx))
        if (next == null) { wm.cancelUniqueWork(WORK_NAME); return }
        val delay = Duration.between(now, next).toMillis().coerceAtLeast(1_000L)
        wm.enqueueUniqueWork(
            WORK_NAME, ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<ReminderWorker>().setInitialDelay(delay, TimeUnit.MILLISECONDS).build(),
        )
    }

    fun cancel(ctx: Context) { WorkManager.getInstance(ctx.applicationContext).cancelUniqueWork(WORK_NAME) }
}
