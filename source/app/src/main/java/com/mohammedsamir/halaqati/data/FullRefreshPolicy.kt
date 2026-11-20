package com.mohammedsamir.halaqati.data

import com.mohammedsamir.halaqati.model.UserRole

/**
 * Rules for the periodic full pull (`fullRefreshNow`): which parts each role needs, when a round counts as successful, and when the next
 * attempt is allowed. Before this a round stamped "done" BEFORE pulling anything and reported success whatever happened, so a dropped
 * connection after the first request blocked every retry for 30 minutes and the sync screen still said "synced".
 */
object FullRefreshPolicy {
    enum class Step {
        STUDENTS, SESSIONS, SESSION_DETAILS, PLANS, ACCOUNTS, GUARDIAN_ACCOUNTS, HALAQAS, CATEGORIES, RANKING, PLAN_EVENTS,
        PLAN_SNAPSHOTS, REPORTS, SUPERVISOR_VISITS, GOALS, SUPERVISION, ANNOUNCEMENTS, FORUM,
    }

    /** The pull is a success only when these worked; the rest are best-effort warm-ups. */
    val CRITICAL: Set<Step> = setOf(Step.STUDENTS, Step.SESSIONS, Step.PLANS)

    /** After a failed round the next attempt is allowed this long after it (not after the full 30-minute gap). */
    const val RETRY_AFTER_FAILURE_MS = 5 * 60_000L

    private val staffOnly = setOf(Step.RANKING, Step.REPORTS, Step.SUPERVISOR_VISITS, Step.ACCOUNTS, Step.GUARDIAN_ACCOUNTS, Step.SUPERVISION)

    fun stepsFor(role: UserRole): Set<Step> = when (role) {
        UserRole.ADMIN, UserRole.SUPERVISOR -> Step.entries.toSet()
        UserRole.TEACHER -> Step.entries.toSet() - setOf(Step.ACCOUNTS, Step.GUARDIAN_ACCOUNTS)
        // guardians, donors and unknown roles: their screens read per-child / per-donor RPCs on demand; the staff pulls only return empty rows
        else -> Step.entries.toSet() - staffOnly
    }

    fun isSuccess(results: Map<Step, Boolean>): Boolean = CRITICAL.all { results[it] == true }

    /** The value stored as "last full pull": [now] on success, otherwise a stamp that makes the next attempt due in [RETRY_AFTER_FAILURE_MS]. */
    fun lastStampToStore(now: Long, success: Boolean, minGapMs: Long): Long =
        if (success) now else now + RETRY_AFTER_FAILURE_MS - minGapMs

    fun errorText(results: Map<Step, Boolean>): String? =
        Step.entries.filter { results[it] == false }.takeIf { it.isNotEmpty() }?.joinToString(",") { it.name }
}
