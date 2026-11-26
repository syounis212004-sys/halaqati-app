package com.mohammedsamir.halaqati.reminders

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mohammedsamir.halaqati.push.NotificationHelper

/** Shows the reminder (when it is still on and the person is signed in) and schedules the next one. */
class ReminderWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val r = ReminderPrefs.load(ctx)
        val text = ReminderTexts.forRole(ReminderPrefs.role(ctx))
        if (r.enabled && text != null) NotificationHelper.showReminder(ctx, text.first, text.second)
        ReminderScheduler.apply(ctx)
        return Result.success()
    }
}

/** Fixed texts per role, kept in the app (they never come from the server). */
object ReminderTexts {
    fun forRole(role: String): Pair<String, String>? = when (role.uppercase()) {
        "GUARDIAN" -> "تكفّل بحلقة أبنائك" to "ساهم بكفالة أو دعم لحلقة أبنائك. افتح صفحة المساهمة."
        "TEACHER", "SUPERVISOR", "ADMIN" -> "أنشئ مسابقة لحلقتك" to "حفّز طلابك بمسابقة جديدة. افتح صفحة المساهمة."
        else -> null
    }
}
