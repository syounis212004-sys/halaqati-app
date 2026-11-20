package com.mohammedsamir.halaqati.data

/** Retry schedule after a failed reconnect sync (the old code tried once and waited for the 15 min worker). */
object SyncRetryPolicy {
    private val schedule = longArrayOf(5_000L, 15_000L, 45_000L, 120_000L, 300_000L)
    fun delayMs(attempt: Int): Long? = schedule.getOrNull(attempt.coerceAtLeast(0))
}
