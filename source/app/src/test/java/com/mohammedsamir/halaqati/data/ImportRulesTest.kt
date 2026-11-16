package com.mohammedsamir.halaqati.data

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class ImportRulesTest {
    @Test fun parsesArabicAndPersianDigitsStrictly() {
        assertEquals(12.5, ImportRules.decimal("١٢٫٥".replace('٫', '.'))!!, 0.001)
        assertEquals(25, ImportRules.integer("۲۵"))
        assertNull(ImportRules.integer("3.5"))
        assertNull(ImportRules.decimal("1,234.5"))
    }

    @Test fun parsesDatesAndExcelSerials() {
        assertEquals(LocalDate.of(2026, 9, 9), ImportRules.date("٩/٩/٢٠٢٦"))
        assertEquals(LocalDate.of(2026, 9, 9), ImportRules.date("2026-09-09"))
        assertEquals(LocalDate.of(1900, 1, 1), ImportRules.date("2"))
        assertEquals(YearMonth.of(2026, 9), ImportRules.month("2026-09"))
    }

    @Test fun onlyKnownActivityTypesPass() {
        assertEquals("new_hifz", ImportRules.activityType("حفظ"))
        assertEquals("sard", ImportRules.activityType("سرد"))
        assertEquals("review_near", ImportRules.activityType("تثبيت"))
        assertEquals("review_far", ImportRules.activityType("مراجعة بعيدة"))
        assertNull(ImportRules.activityType("نوع غير معروف"))
    }

    @Test fun validatesRecitationBoundaries() {
        assertTrue(ImportRules.validRecitation("new_hifz", 1.0, 0, 0, 1, 1))
        assertFalse(ImportRules.validRecitation(null, 1.0, 0, 0, 1, 1))
        assertFalse(ImportRules.validRecitation("new_hifz", -1.0, 0, 0, 1, 1))
        assertFalse(ImportRules.validRecitation("new_hifz", 1.0, -1, 0, 1, 1))
        assertFalse(ImportRules.validRecitation("new_hifz", 1.0, 0, 0, 605, 605))
        assertFalse(ImportRules.validRecitation("new_hifz", 1.0, 0, 0, 20, 10))
    }

    @Test fun validatesMonthlyGoals() {
        assertTrue(ImportRules.validMonthlyGoals(20.0, 30.0, 10.0, 20))
        assertFalse(ImportRules.validMonthlyGoals(-1.0, 0.0, 0.0, 20))
        assertFalse(ImportRules.validMonthlyGoals(0.0, 0.0, 0.0, 32))
    }

    @Test fun preflightClassifiesRowsBeforeCommit() {
        assertEquals(ImportRules.RowDisposition.VALID, ImportRules.studentDisposition("أحمد", "الحفظ العام"))
        assertEquals(ImportRules.RowDisposition.INVALID, ImportRules.studentDisposition("أحمد", "مسار ثالث"))
        assertEquals(ImportRules.RowDisposition.SKIP, ImportRules.studentDisposition("", "الحفظ العام"))
        assertEquals(
            ImportRules.RowDisposition.VALID,
            ImportRules.recitationDisposition("أحمد", "2026-09-09", "حفظ", "1", "0", "0", "1", "1"),
        )
        assertEquals(
            ImportRules.RowDisposition.INVALID,
            ImportRules.recitationDisposition("أحمد", "2026-09-09", "نوع غير معروف", "1"),
        )
        assertEquals(
            ImportRules.RowDisposition.SKIP,
            ImportRules.monthlyGoalDisposition("أحمد", "2026-09", "0", "0", "0", "20"),
        )
    }
}
