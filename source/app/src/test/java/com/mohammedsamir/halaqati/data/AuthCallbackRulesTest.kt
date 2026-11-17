package com.mohammedsamir.halaqati.data

import org.junit.Assert.*
import org.junit.Test

class AuthCallbackRulesTest {
    private val prefix = "com.mohammedsamir.halaqati://auth/callback"

    @Test fun parsesRecoveryFragment() {
        val parsed = AuthCallbackRules.parse(
            "$prefix#access_token=aaa.bbb.ccc&refresh_token=refresh-1&type=recovery",
            prefix,
        )!!
        assertEquals("recovery", parsed.type)
        assertEquals("aaa.bbb.ccc", parsed.accessToken)
        assertEquals("refresh-1", parsed.refreshToken)
        assertNull(parsed.error)
    }

    @Test fun parsesOAuthQueryAndFragmentTogether() {
        val parsed = AuthCallbackRules.parse(
            "$prefix?type=signup#access_token=token&refresh_token=refresh",
            prefix,
        )!!
        assertEquals("signup", parsed.type)
        assertEquals("token", parsed.accessToken)
    }

    @Test fun decodesErrorAndRejectsOtherSchemes() {
        val parsed = AuthCallbackRules.parse("$prefix#error_description=Access%20denied&type=recovery", prefix)!!
        assertEquals("Access denied", parsed.error)
        assertNull(AuthCallbackRules.parse("https://example.com/#access_token=x", prefix))
    }
    @Test fun rejectsLookalikeCallbackPathsAndAuthorities() {
        assertNull(AuthCallbackRules.parse("$prefix.evil#access_token=x&refresh_token=y", prefix))
        assertNull(AuthCallbackRules.parse("com.mohammedsamir.halaqati://auth@evil.example/callback#access_token=x&refresh_token=y", prefix))
        assertNull(AuthCallbackRules.parse("com.mohammedsamir.halaqati://auth/callback/extra#access_token=x&refresh_token=y", prefix))
    }

    @Test fun readsPkceCodeFromQuery() {
        assertEquals("abc123", AuthCallbackRules.parse("$prefix?code=abc123", prefix)!!.code)
    }
}

class AuthTokenLinkGateTest {
    private val day = AuthCallbackRules.EMAIL_LINK_TTL_MS

    @org.junit.Test fun forgedRecoveryLinkIsRefusedWhenNothingWasRequested() {
        // The audit finding: `type=recovery` alone used to be enough to accept somebody else's tokens.
        org.junit.Assert.assertFalse(AuthCallbackRules.tokenLinkAllowed("recovery", pkcePending = false, pendingEmail = null, pendingAtMs = 0, nowMs = 1_000))
        org.junit.Assert.assertFalse(AuthCallbackRules.tokenLinkAllowed("signup", pkcePending = true, pendingEmail = "", pendingAtMs = 0, nowMs = 1_000))
    }

    @org.junit.Test fun requestedRecoveryLinkIsAcceptedOnlyInsideItsWindow() {
        org.junit.Assert.assertTrue(AuthCallbackRules.tokenLinkAllowed("recovery", false, "t@example.com", pendingAtMs = 1_000, nowMs = 1_000 + day))
        org.junit.Assert.assertFalse(AuthCallbackRules.tokenLinkAllowed("recovery", false, "t@example.com", pendingAtMs = 1_000, nowMs = 1_001 + day))
        // A clock set back must not extend the window.
        org.junit.Assert.assertFalse(AuthCallbackRules.tokenLinkAllowed("recovery", false, "t@example.com", pendingAtMs = 5_000, nowMs = 1_000))
    }

    @org.junit.Test fun untypedTokensNeedOurOwnPendingSignIn() {
        org.junit.Assert.assertTrue(AuthCallbackRules.tokenLinkAllowed("", pkcePending = true, pendingEmail = null, pendingAtMs = 0, nowMs = 1))
        org.junit.Assert.assertFalse(AuthCallbackRules.tokenLinkAllowed("", pkcePending = false, pendingEmail = "t@example.com", pendingAtMs = 0, nowMs = 1))
    }

    @org.junit.Test fun emailedLinkMustBelongToTheRequestedAccount() {
        org.junit.Assert.assertTrue(AuthCallbackRules.tokenEmailMatches("recovery", " Teacher@Example.com ", "teacher@example.com"))
        org.junit.Assert.assertFalse(AuthCallbackRules.tokenEmailMatches("recovery", "teacher@example.com", "attacker@example.com"))
        org.junit.Assert.assertFalse(AuthCallbackRules.tokenEmailMatches("recovery", "teacher@example.com", ""))
        org.junit.Assert.assertFalse(AuthCallbackRules.tokenEmailMatches("recovery", null, "attacker@example.com"))
        org.junit.Assert.assertTrue(AuthCallbackRules.tokenEmailMatches("", null, "anyone@example.com"))
    }
}
