package com.mohammedsamir.halaqati.data

import java.nio.charset.StandardCharsets
import java.util.UUID

/** Pure RC9.3 FIX3 decisions shared by startup/runtime code and tests. */
object RuntimeStabilityRules {
    fun canOpenFromCachedProfile(hasSession: Boolean, hasCachedProfile: Boolean): Boolean =
        hasSession && hasCachedProfile

    /** Stable key makes the same milestone approval idempotent across taps/retries/restarts. */
    fun milestoneEventKey(planId: String, studentId: String, milestoneKey: String): String {
        val raw = "milestone-approved:${planId.trim()}:${studentId.trim()}:${milestoneKey.trim()}"
        return UUID.nameUUIDFromBytes(raw.toByteArray(StandardCharsets.UTF_8)).toString()
    }
}
