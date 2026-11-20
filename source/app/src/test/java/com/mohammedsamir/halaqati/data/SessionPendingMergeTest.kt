package com.mohammedsamir.halaqati.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionPendingMergeTest {
    @Test
    fun aPendingCreateMissingFromTheServerListIsAddedBack() {
        val plan = SessionPendingMerge.plan(serverIds = setOf("a", "b"), pendingUpsertIds = listOf("c"), pendingDeleteIds = emptyList())
        assertEquals(listOf("c"), plan.addIds); assertTrue(plan.hideIds.isEmpty())
    }

    @Test
    fun aPendingCreateAlreadyOnTheServerIsNotDuplicated() {
        val plan = SessionPendingMerge.plan(setOf("a", "b"), listOf("b", "b"), emptyList())
        assertTrue(plan.addIds.isEmpty())
    }

    @Test
    fun aPendingDeleteHidesTheSessionUntilTheServerAgrees() {
        val plan = SessionPendingMerge.plan(setOf("a", "b"), emptyList(), listOf("a"))
        assertEquals(setOf("a"), plan.hideIds)
    }

    @Test
    fun aCreateFollowedByADeleteOfTheSameSessionAddsNothing() {
        val plan = SessionPendingMerge.plan(setOf("a"), listOf("n"), listOf("n"))
        assertTrue(plan.addIds.isEmpty()); assertEquals(setOf("n"), plan.hideIds)
    }

    @Test
    fun repeatedPendingIdsAreAddedOnce() {
        val plan = SessionPendingMerge.plan(emptySet(), listOf("x", "x", "y"), emptyList())
        assertEquals(listOf("x", "y"), plan.addIds)
    }
}
