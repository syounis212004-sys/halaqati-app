package com.mohammedsamir.halaqati.data

import android.content.Context
import android.content.SharedPreferences
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * RC79: spares the database. Heavy per-student / per-person reads (student profile, staff photos) are fetched from the server at
 * most once per "cycle". A cycle starts at 20:00 Gaza time (after the evening session upload) or as soon as this device saves new
 * session data (recitations / attendance / sessions). In between, screens read the copy already stored on the device.
 */
object RefreshGate {
    private val gaza: ZoneId = ZoneId.of("Asia/Gaza")
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences("halaqati_refresh_gate", Context.MODE_PRIVATE)
    }

    private fun cycleStart(now: Instant = Instant.now()): Long {
        val zoned = now.atZone(gaza)
        val today = LocalDate.from(zoned)
        val anchorDay = if (zoned.hour >= 20) today else today.minusDays(1)
        return anchorDay.atTime(20, 0).atZone(gaza).toInstant().toEpochMilli()
    }

    /** True when [key] has not been refreshed since the current cycle began or since new session data was saved here. */
    private fun scoped(key: String): String = (runCatching { AppGraph.store.load()?.userId }.getOrNull() ?: "anon") + "|" + key

    fun due(key: String): Boolean {
        val p = prefs ?: return true
        val last = p.getLong("last:" + scoped(key), 0L)
        return last < cycleStart() || last < p.getLong("dirty", 0L)
    }

    fun mark(key: String) {
        prefs?.edit()?.putLong("last:" + scoped(key), System.currentTimeMillis())?.apply()
    }

    /** Called when this device saves recitations / attendance / sessions: the next profile open refreshes once. */
    fun invalidate() {
        prefs?.edit()?.putLong("dirty", System.currentTimeMillis())?.apply()
    }
}
