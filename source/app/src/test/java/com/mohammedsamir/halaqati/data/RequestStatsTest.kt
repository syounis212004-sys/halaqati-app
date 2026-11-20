package com.mohammedsamir.halaqati.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RequestStatsTest {
    @Test
    fun countsRequestsAndBytesPerEndpointWithoutQueryValues() {
        val stats = RequestStats()
        stats.record("GET", "https://x.supabase.co/rest/v1/students?select=id&limit=500", 1200)
        stats.record("GET", "https://x.supabase.co/rest/v1/students?select=*&limit=10", 300)
        stats.record("POST", "https://x.supabase.co/rest/v1/rpc/rc19_record_plan_health", 90)
        val snap = stats.snapshot()
        assertEquals(3, stats.totalRequests()); assertEquals(1590L, stats.totalBytes())
        assertEquals(2, snap.first { it.endpoint == "GET /rest/v1/students" }.count)
        assertEquals(1500L, snap.first { it.endpoint == "GET /rest/v1/students" }.bytes)
        assertTrue(snap.none { it.endpoint.contains("select=") })
    }

    @Test
    fun taggedCountersAndResetWork() {
        val stats = RequestStats()
        stats.record("GET", "https://x/rest/v1/sessions", 10)
        assertEquals(1, stats.requestsSince(stats.mark()) + 1)
        val mark = stats.mark(); stats.record("GET", "https://x/rest/v1/sessions", 10); stats.record("GET", "https://x/rest/v1/profiles", 5)
        assertEquals(2, stats.requestsSince(mark))
        stats.reset(); assertEquals(0, stats.totalRequests())
    }

    @Test
    fun theWindowIsBoundedSoItCannotGrowForever() {
        val stats = RequestStats(maxEndpoints = 3)
        repeat(10) { stats.record("GET", "https://x/rest/v1/t$it", 1) }
        assertTrue(stats.snapshot().size <= 4) // 3 endpoints + one overflow bucket
        assertEquals(10, stats.totalRequests())
    }
}
