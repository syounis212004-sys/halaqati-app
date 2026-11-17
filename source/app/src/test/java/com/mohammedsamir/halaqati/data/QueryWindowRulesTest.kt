package com.mohammedsamir.halaqati.data

import org.junit.Assert.assertEquals
import org.junit.Test

class QueryWindowRulesTest {
    /** The audit finding: an oldest-first query with a ceiling dropped today's recitations once the table grew. */
    @Test fun aCappedNewestFirstReadKeepsTheNewestRowsInOldestFirstOrder() {
        val table = (1..10).toList()                       // 1 = oldest ... 10 = newest
        val ceiling = 4
        val oldWay = table.take(ceiling)                   // order=created_at.asc&limit=4
        val newWay = QueryWindowRules.oldestFirst(table.sortedDescending().take(ceiling)) // desc + limit, then flipped
        assertEquals(listOf(1, 2, 3, 4), oldWay)           // today's rows (9, 10) were lost
        assertEquals(listOf(7, 8, 9, 10), newWay)          // newest kept, still oldest-first for the engine
    }

    @Test fun belowTheCeilingNothingChanges() {
        val table = listOf(1, 2, 3)
        assertEquals(table, QueryWindowRules.oldestFirst(table.sortedDescending().take(100)))
        assertEquals(emptyList<Int>(), QueryWindowRules.oldestFirst(emptyList<Int>()))
    }
}
