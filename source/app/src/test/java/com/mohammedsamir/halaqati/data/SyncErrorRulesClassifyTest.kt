package com.mohammedsamir.halaqati.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** RC27.45: the outbox decides "retry later" vs "park it" from this table, so it is pinned by tests. */
class SyncErrorRulesClassifyTest {
    @Test fun expiredSessionIsRetryable() {
        assertEquals(SyncErrorRules.Kind.SESSION, SyncErrorRules.classify(401, null))
    }

    @Test fun serverErrorsAndOfflineAreTransient() {
        assertEquals(SyncErrorRules.Kind.TRANSIENT, SyncErrorRules.classify(500, null))
        assertEquals(SyncErrorRules.Kind.TRANSIENT, SyncErrorRules.classify(503, null))
        assertEquals(SyncErrorRules.Kind.TRANSIENT, SyncErrorRules.classify(0, null))
        assertEquals(SyncErrorRules.Kind.TRANSIENT, SyncErrorRules.classify(429, null))
    }

    @Test fun databaseRuleViolationsAreParkedNotRetried() {
        assertEquals(SyncErrorRules.Kind.INVALID_PAYLOAD, SyncErrorRules.classify(400, "23514"))
        assertEquals(SyncErrorRules.Kind.INVALID_PAYLOAD, SyncErrorRules.classify(409, "23505"))
        assertEquals(SyncErrorRules.Kind.INVALID_PAYLOAD, SyncErrorRules.classify(400, null))
    }

    @Test fun permissionErrors() {
        assertEquals(SyncErrorRules.Kind.PERMISSION, SyncErrorRules.classify(403, null))
        assertEquals(SyncErrorRules.Kind.PERMISSION, SyncErrorRules.classify(400, "42501"))
    }
}
