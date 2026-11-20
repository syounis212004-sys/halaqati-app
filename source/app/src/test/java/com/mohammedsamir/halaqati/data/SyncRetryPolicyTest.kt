package com.mohammedsamir.halaqati.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SyncRetryPolicyTest {
    @Test fun delaysGrowThenStop() {
        assertEquals(5_000L, SyncRetryPolicy.delayMs(0))
        assertEquals(15_000L, SyncRetryPolicy.delayMs(1))
        assertEquals(45_000L, SyncRetryPolicy.delayMs(2))
        assertEquals(120_000L, SyncRetryPolicy.delayMs(3))
        assertEquals(300_000L, SyncRetryPolicy.delayMs(4))
        assertNull("after the schedule the 15 min worker takes over", SyncRetryPolicy.delayMs(5))
    }

    @Test fun negativeAttemptIsTreatedAsFirst() = assertEquals(5_000L, SyncRetryPolicy.delayMs(-3))
}
