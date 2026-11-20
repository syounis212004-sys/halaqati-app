package com.mohammedsamir.halaqati.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanHealthMemoTest {
    private class MemStore : PlanHealthMemo.Store {
        val data = HashMap<String, String>()
        override fun read(owner: String): String? = data[owner]
        override fun write(owner: String, text: String) { data[owner] = text }
    }

    private fun inputs(completed: Double = 4.0) = PlanHealthMemo.Inputs(scheduled = true, health = "green", ratio = 1.0, expected = 4.0, completed = completed, behind = 0.0)

    @Test
    fun nothingIsKnownAtFirst() {
        assertFalse(PlanHealthMemo.unchanged(MemStore(), "u1", "2026-11-20", "p1", "s1", PlanHealthMemo.signature("2026-11-20", inputs())))
    }

    @Test
    fun anIdenticalRecordedStateIsSkippedAndAChangedOneIsNot() {
        val store = MemStore(); val day = "2026-11-20"
        val sig = PlanHealthMemo.signature(day, inputs())
        PlanHealthMemo.remember(store, "u1", day, "p1", "s1", sig)
        assertTrue(PlanHealthMemo.unchanged(store, "u1", day, "p1", "s1", sig))
        assertFalse(PlanHealthMemo.unchanged(store, "u1", day, "p1", "s1", PlanHealthMemo.signature(day, inputs(completed = 5.0))))
        assertFalse(PlanHealthMemo.unchanged(store, "u1", day, "p1", "s2", sig))
        assertFalse(PlanHealthMemo.unchanged(store, "u1", day, "p2", "s1", sig))
    }

    @Test
    fun theMemoIsPerAccountAndStartsFreshEachDay() {
        val store = MemStore(); val sig = PlanHealthMemo.signature("2026-11-20", inputs())
        PlanHealthMemo.remember(store, "u1", "2026-11-20", "p1", "s1", sig)
        assertFalse(PlanHealthMemo.unchanged(store, "u2", "2026-11-20", "p1", "s1", sig))
        assertFalse(PlanHealthMemo.unchanged(store, "u1", "2026-11-21", "p1", "s1", PlanHealthMemo.signature("2026-11-21", inputs())))
        PlanHealthMemo.remember(store, "u1", "2026-11-21", "p1", "s1", PlanHealthMemo.signature("2026-11-21", inputs()))
        assertFalse("yesterday's entries are dropped", PlanHealthMemo.unchanged(store, "u1", "2026-11-20", "p1", "s1", sig))
    }

    @Test
    fun rememberingManyPairsKeepsEveryOne() {
        val store = MemStore(); val day = "2026-11-20"; val sig = PlanHealthMemo.signature(day, inputs())
        repeat(150) { PlanHealthMemo.remember(store, "u1", day, "p$it", "s$it", sig) }
        repeat(150) { assertTrue(PlanHealthMemo.unchanged(store, "u1", day, "p$it", "s$it", sig)) }
    }

    @Test
    fun aCorruptStoredValueIsTreatedAsEmpty() {
        val store = MemStore(); store.data["u1"] = "garbage\u0000\n\n===="
        assertFalse(PlanHealthMemo.unchanged(store, "u1", "2026-11-20", "p1", "s1", "x"))
        PlanHealthMemo.remember(store, "u1", "2026-11-20", "p1", "s1", "x")
        assertTrue(PlanHealthMemo.unchanged(store, "u1", "2026-11-20", "p1", "s1", "x"))
    }

    @Test
    fun theSignatureTracksEveryNumberTheServerRecordsAndIgnoresRoundingNoise() {
        val a = PlanHealthMemo.signature("d", inputs())
        assertEquals(a, PlanHealthMemo.signature("d", inputs().copy(ratio = 1.00000001)))
        assertFalse(a == PlanHealthMemo.signature("d", inputs().copy(health = "red")))
        assertFalse(a == PlanHealthMemo.signature("d", inputs().copy(scheduled = false)))
        assertFalse(a == PlanHealthMemo.signature("d", inputs().copy(behind = 2.0)))
        assertFalse(a == PlanHealthMemo.signature("d", inputs().copy(expected = 5.0)))
    }
}
