package com.mohammedsamir.halaqati.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudentGuardianRulesTest {
    private val guardians = listOf(
        StudentGuardianRules.GuardianOption("1", "أحمد أبو يونس", "ahmad@example.com", "0599000001"),
        StudentGuardianRules.GuardianOption("2", "محمد سمير", "mohammad@example.com", "0599000002"),
    )

    @Test fun arabicSearchIgnoresCommonAlefVariants() {
        val result = StudentGuardianRules.filter(guardians, "احمد")
        assertEquals(listOf("1"), result.map { it.id })
    }

    @Test fun searchMatchesPhoneOrEmail() {
        assertEquals("2", StudentGuardianRules.filter(guardians, "00002").single().id)
        assertEquals("1", StudentGuardianRules.filter(guardians, "ahmad@").single().id)
    }

    @Test fun oneGuardianCanBeSelectedForMultipleStudentsBecauseOptionIsAccountScoped() {
        val option = guardians.first()
        assertTrue(StudentGuardianRules.matches(option, "أحمد"))
        assertEquals("1", option.id)
    }

    @Test fun linkedAccountsAreEqualAndUnknownLinksAreNotDropped() {
        val result = StudentGuardianRules.linkedOptions(listOf("2", "1", "3", "2"), guardians)
        assertEquals(listOf("2", "1", "3"), result.map { it.id })
        assertEquals("ولي أمر مرتبط", result.last().fullName)
        org.junit.Assert.assertFalse(result.last().metadataAvailable)
        assertTrue(result.first().metadataAvailable)
        assertEquals("محمد سمير", result.first().fullName)
    }
}
