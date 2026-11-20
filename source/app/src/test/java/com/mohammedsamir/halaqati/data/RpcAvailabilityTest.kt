package com.mohammedsamir.halaqati.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RpcAvailabilityTest {
    @Test
    fun postgrestMissingFunctionIsRecognised() {
        assertTrue(RpcAvailability.isMissing(404, "PGRST202", "Could not find the function public.rc_ranking_source(p_from) in the schema cache"))
        assertTrue(RpcAvailability.isMissing(404, null, null))
        assertTrue(RpcAvailability.isMissing(400, null, "Could not find the function public.x"))
        assertTrue(RpcAvailability.isMissing(404, "42883", "function does not exist"))
    }

    @Test
    fun otherFailuresAreNotTreatedAsMissing() {
        assertFalse(RpcAvailability.isMissing(401, null, "JWT expired"))
        assertFalse(RpcAvailability.isMissing(500, "XX000", "boom"))
        assertFalse(RpcAvailability.isMissing(403, "42501", "permission denied for function rc_ranking_source"))
        assertFalse(RpcAvailability.isMissing(0, null, "timeout"))
    }

    @Test
    fun aMissingRpcIsRememberedForSixHours() {
        val now = 10_000_000_000L
        assertTrue(RpcAvailability.shouldTry(lastMissingAt = 0L, now = now))
        assertFalse(RpcAvailability.shouldTry(lastMissingAt = now - 3_600_000L, now = now))
        assertTrue(RpcAvailability.shouldTry(lastMissingAt = now - 7 * 3_600_000L, now = now))
    }
}
