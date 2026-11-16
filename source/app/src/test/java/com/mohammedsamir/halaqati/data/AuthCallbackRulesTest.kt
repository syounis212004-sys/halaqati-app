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
