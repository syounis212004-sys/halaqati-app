package com.mohammedsamir.halaqati.reminders

import android.content.Context
import java.time.DayOfWeek

/** The contribution reminder: which days, what time, on or off. Lives on this device only (never read from or written to the database). */
data class Reminder(val enabled: Boolean, val days: Set<DayOfWeek>, val hour: Int, val minute: Int) {
    companion object {
        /** Thursday 18:00, on. The person can change or switch it off from the contribution page. */
        val DEFAULT = Reminder(true, setOf(DayOfWeek.THURSDAY), 18, 0)
    }
}

object ReminderPrefs {
    private const val FILE = "halaqati_reminders"

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun load(ctx: Context): Reminder {
        val p = prefs(ctx)
        if (!p.contains("enabled")) return Reminder.DEFAULT
        val days = p.getString("days", null)?.split(',')?.mapNotNull { it.toIntOrNull()?.takeIf { d -> d in 1..7 }?.let(DayOfWeek::of) }?.toSet().orEmpty()
        return Reminder(p.getBoolean("enabled", true), days, p.getInt("hour", 18).coerceIn(0, 23), p.getInt("minute", 0).coerceIn(0, 59))
    }

    fun save(ctx: Context, r: Reminder) {
        prefs(ctx).edit().putBoolean("enabled", r.enabled).putString("days", r.days.joinToString(",") { it.value.toString() })
            .putInt("hour", r.hour).putInt("minute", r.minute).apply()
    }

    /** The signed-in role decides the text; cleared at sign-out so a signed-out device shows no reminder. */
    fun role(ctx: Context): String = prefs(ctx).getString("role", "").orEmpty()
    fun setRole(ctx: Context, role: String?) { prefs(ctx).edit().putString("role", role.orEmpty()).apply() }
}
