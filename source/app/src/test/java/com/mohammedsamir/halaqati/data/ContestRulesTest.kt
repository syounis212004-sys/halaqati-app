package com.mohammedsamir.halaqati.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContestRulesTest {
    private fun draft(
        name: String = "مسابقة رمضان",
        title: String = "مسابقة الحفظ الكبرى",
        conditions: String = "حفظ جزء كامل خلال الشهر",
        reward: String = "هدية قيمة",
        startsOn: String? = null,
        endsOn: String? = null,
    ) = ContestDraft(name, title, conditions, reward, startsOn, endsOn)

    @Test fun validDraftHasNoErrors() {
        assertTrue(ContestRules.validate(draft()).isEmpty())
    }

    @Test fun everyFieldIsRequired() {
        val e = ContestRules.validate(draft(name = " ", title = "", conditions = "   ", reward = ""))
        assertEquals(setOf("name", "title", "conditions", "reward"), e.keys)
    }

    @Test fun lengthLimitsAreEnforced() {
        assertTrue("name" in ContestRules.validate(draft(name = "ا".repeat(ContestRules.NAME_MAX + 1))))
        assertTrue("title" in ContestRules.validate(draft(title = "ا".repeat(ContestRules.TITLE_MAX + 1))))
        assertTrue("conditions" in ContestRules.validate(draft(conditions = "ا".repeat(ContestRules.CONDITIONS_MAX + 1))))
        assertTrue("reward" in ContestRules.validate(draft(reward = "ا".repeat(ContestRules.REWARD_MAX + 1))))
        assertTrue("name" in ContestRules.validate(draft(name = "اب")))
        assertTrue(ContestRules.validate(draft(name = "ا".repeat(ContestRules.NAME_MAX))).isEmpty())
    }

    @Test fun endDateCannotPrecedeStartDate() {
        assertTrue("endsOn" in ContestRules.validate(draft(startsOn = "2026-12-10", endsOn = "2026-12-01")))
        assertTrue(ContestRules.validate(draft(startsOn = "2026-12-01", endsOn = "2026-12-01")).isEmpty())
        assertTrue("startsOn" in ContestRules.validate(draft(startsOn = "not-a-date")))
    }

    @Test fun onlyPendingMovesToApprovedOrRejected() {
        assertTrue(ContestRules.canTransition(ContestStatus.PENDING, ContestStatus.APPROVED))
        assertTrue(ContestRules.canTransition(ContestStatus.PENDING, ContestStatus.REJECTED))
        assertFalse(ContestRules.canTransition(ContestStatus.APPROVED, ContestStatus.REJECTED))
        assertFalse(ContestRules.canTransition(ContestStatus.REJECTED, ContestStatus.APPROVED))
        assertFalse(ContestRules.canTransition(ContestStatus.PENDING, ContestStatus.PENDING))
        assertFalse(ContestRules.canTransition(ContestStatus.APPROVED, ContestStatus.PENDING))
    }

    @Test fun rejectionNeedsAReasonAndApprovalNeedsAHalaqa() {
        assertNull(ContestRules.decisionError(ContestStatus.PENDING, ContestStatus.REJECTED, "الشروط غير واضحة", null))
        assertEquals("reason", ContestRules.decisionError(ContestStatus.PENDING, ContestStatus.REJECTED, "  ", null))
        assertEquals("reason", ContestRules.decisionError(ContestStatus.PENDING, ContestStatus.REJECTED, "اب", null))
        assertNull(ContestRules.decisionError(ContestStatus.PENDING, ContestStatus.APPROVED, "", "h1"))
        assertEquals("halaqa", ContestRules.decisionError(ContestStatus.PENDING, ContestStatus.APPROVED, "", null))
        assertEquals("halaqa", ContestRules.decisionError(ContestStatus.PENDING, ContestStatus.APPROVED, "", " "))
        assertEquals("transition", ContestRules.decisionError(ContestStatus.APPROVED, ContestStatus.REJECTED, "سبب كافٍ", "h1"))
    }

    @Test fun statusParsingIsTolerant() {
        assertEquals(ContestStatus.APPROVED, ContestStatus.from("approved"))
        assertEquals(ContestStatus.REJECTED, ContestStatus.from("REJECTED"))
        assertEquals(ContestStatus.PENDING, ContestStatus.from(null))
        assertEquals(ContestStatus.PENDING, ContestStatus.from("weird"))
    }

    @Test fun donorWithoutHalaqatGetsAdminAssignmentMessage() {
        assertEquals(ContestPickerState.NO_HALAQA, ContestRules.pickerState(emptyList()))
        assertEquals(ContestPickerState.SINGLE, ContestRules.pickerState(listOf("a")))
        assertEquals(ContestPickerState.CHOOSE, ContestRules.pickerState(listOf("a", "b")))
    }

    @Test fun listFilterKeepsOrderAndStatus() {
        val items = listOf(
            Contest("1", "n", "t", "c", "r", ContestStatus.PENDING, null, null, null, null, null, null, null),
            Contest("2", "n", "t", "c", "r", ContestStatus.APPROVED, null, null, null, null, null, null, null),
        )
        assertEquals(listOf("2"), ContestRules.filter(items, ContestStatus.APPROVED).map { it.id })
        assertEquals(listOf("1", "2"), ContestRules.filter(items, null).map { it.id })
    }

    @Test fun datesAreAcceptedInDayFirstAndYearFirstForms() {
        val want = java.time.LocalDate.of(2026, 12, 1)
        for (t in listOf("2026-12-01", "01-12-2026", "1/12/2026", "01.12.2026", "2026/12/1", "\u0660\u0661-\u0661\u0662-\u0662\u0660\u0662\u0666", "\u200F01-12-2026\u200E", " 01-12-2026 ")) {
            org.junit.Assert.assertEquals(t, want, ContestRules.parseDate(t))
        }
        org.junit.Assert.assertEquals("2026-12-01", ContestRules.normalizeDate("01-12-2026"))
        org.junit.Assert.assertNull(ContestRules.normalizeDate("  "))
    }

    @Test fun impossibleOrAmbiguousDatesAreRejected() {
        for (t in listOf("31-02-2026", "2026-13-01", "12-2026", "01-12-26", "abc", "")) org.junit.Assert.assertNull(t, ContestRules.parseDate(t))
        val d = ContestDraft("اسم المسابقة", "عنوان", "شروط المسابقة كاملة", "جائزة", "01-12-2026", "31-11-2026")
        org.junit.Assert.assertTrue(ContestRules.validate(d).containsKey("endsOn"))
        org.junit.Assert.assertTrue(ContestRules.validate(d.copy(endsOn = "20-12-2026")).isEmpty())
        org.junit.Assert.assertTrue(ContestRules.validate(d.copy(endsOn = "20-11-2026")).containsKey("endsOn"))
    }

    @Test fun creatorLabelsAndMessagesFollowTheRole() {
        org.junit.Assert.assertEquals("المانح", ContestRules.creatorLabel(null))
        org.junit.Assert.assertEquals("المحفظ", ContestRules.creatorLabel("teacher"))
        org.junit.Assert.assertEquals("المشرف", ContestRules.creatorLabel("supervisor"))
        org.junit.Assert.assertEquals("مدير النظام", ContestRules.creatorLabel("admin"))
        org.junit.Assert.assertEquals(ContestRules.NO_HALAQA_MESSAGE, ContestRules.noHalaqaMessage(com.mohammedsamir.halaqati.model.UserRole.OBSERVER))
        org.junit.Assert.assertTrue(ContestRules.noHalaqaMessage(com.mohammedsamir.halaqati.model.UserRole.TEACHER).contains("ربطك بحلقتك"))
    }

    @Test fun formatDateRoundTripsThroughParseDate() {
        val d = java.time.LocalDate.of(2026, 1, 5)
        org.junit.Assert.assertEquals("05-01-2026", ContestRules.formatDate(d))
        org.junit.Assert.assertEquals(d, ContestRules.parseDate(ContestRules.formatDate(d)))
        org.junit.Assert.assertEquals("2026-01-05", ContestRules.normalizeDate(ContestRules.formatDate(d)))
    }

    @Test fun yearsOutsideTwoThousandToTwentyOneHundredAreRejected() {
        for (t in listOf("01-12-0026", "01-01-0000", "01-12-1999", "01-12-2101")) org.junit.Assert.assertNull(t, ContestRules.parseDate(t))
        org.junit.Assert.assertNotNull(ContestRules.parseDate("01-12-2026"))
    }
}
