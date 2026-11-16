package com.mohammedsamir.halaqati.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalAccountScopeRulesTest {
    @Test
    fun sameSuffix_isolatedAcrossOwners() {
        val a = LocalAccountScopeRules.ownerKey("user-a", "archive_v3")
        val b = LocalAccountScopeRules.ownerKey("user-b", "archive_v3")
        assertNotEquals(a, b)
        assertTrue(LocalAccountScopeRules.belongsTo(a, "user-a"))
        assertFalse(LocalAccountScopeRules.belongsTo(a, "user-b"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun blankOwner_rejected() {
        LocalAccountScopeRules.ownerKey("", "style")
    }
}
