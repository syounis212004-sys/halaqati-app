package com.mohammedsamir.halaqati.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class LocalCacheTest {
    @Test
    fun freshnessRejectsExpiredEntriesButAllowsStaleWhenNoMaxAge() {
        val savedAt = Instant.parse("2026-09-23T00:00:00Z")
        val now = savedAt.plusSeconds(3600)

        assertTrue(LocalCache.isUsable(savedAt, null, now))
        assertFalse(LocalCache.isUsable(savedAt, Duration.ofMinutes(5), now))
    }

    @Test
    fun ownerAndKeyMustBeExplicitAndNonBlank() {
        assertTrue(LocalCache.isValidScope("userA", "students"))
        assertFalse(LocalCache.isValidScope("", "students"))
        assertFalse(LocalCache.isValidScope("userA", ""))
    }
}
