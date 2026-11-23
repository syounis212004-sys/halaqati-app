package com.mohammedsamir.halaqati.data

import org.junit.Assert.*
import org.junit.Test

class AuthFailureRulesTest {
    @Test fun missingRefreshTokenIsDefinitive() {
        assertTrue(AuthFailureRules.isSessionGone(400, "refresh_token_not_found", "Invalid Refresh Token: Refresh Token Not Found"))
        assertTrue(AuthFailureRules.isSessionGone(400, null, "Invalid Refresh Token: Refresh Token Not Found"))
        assertTrue(AuthFailureRules.isSessionGone(403, "session_not_found", null))
        assertTrue(AuthFailureRules.isSessionGone(400, "REFRESH_TOKEN_NOT_FOUND", ""))
    }

    @Test fun transientOrRecoverableFailuresAreNot() {
        assertFalse(AuthFailureRules.isSessionGone(429, "over_request_rate_limit", "rate limit"))
        assertFalse("a server error is never proof", AuthFailureRules.isSessionGone(500, null, "Refresh Token Not Found"))
        assertFalse(AuthFailureRules.isSessionGone(0, null, "timeout"))
        // a rotated token another caller may already have saved keeps the lenient 3-failures / 10-minutes policy
        assertFalse(AuthFailureRules.isSessionGone(400, "refresh_token_already_used", "Invalid Refresh Token: Already Used"))
        assertFalse(AuthFailureRules.isSessionGone(400, "validation_failed", "bad body"))
    }
}
