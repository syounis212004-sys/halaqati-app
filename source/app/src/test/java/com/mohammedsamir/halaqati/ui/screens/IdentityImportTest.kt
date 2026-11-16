package com.mohammedsamir.halaqati.ui.screens

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IdentityImportTest {
    private val today = LocalDate.of(2026, 10, 20)

    @Test fun parsesIsoSlashAndDayFirstDates() {
        assertEquals("2015-03-27", IdentityImport.parseDate("2015-03-27", today))
        assertEquals("2015-03-27", IdentityImport.parseDate("2015/3/27", today))
        assertEquals("2015-03-27", IdentityImport.parseDate("27/03/2015", today))
        assertEquals("2015-03-27", IdentityImport.parseDate("٢٧/٠٣/٢٠١٥", today))
    }

    @Test fun rejectsFutureAndImpossibleDates() {
        assertNull(IdentityImport.parseDate("2030-01-01", today))
        assertNull(IdentityImport.parseDate("2015-02-31", today))
        assertNull(IdentityImport.parseDate("abc", today))
    }

    @Test fun namesMatchAcrossAlefAndSpaceVariants() {
        assertEquals(IdentityImport.normName("أحمد  محمد"), IdentityImport.normName("احمد محمد"))
        assertEquals(IdentityImport.normName("فاطمة"), IdentityImport.normName("فاطمه"))
    }

    @Test fun parsesTabSeparatedRowsAndCountsBadLines() {
        val text = "احمد محمد\t123456789\t2015-03-27\nسطر ناقص\t123\nعلي\t987654321\t01/02/2014"
        val (rows, bad) = IdentityImport.parse(text, today)
        assertEquals(2, rows.size)
        assertEquals("123456789", rows[0].nationalId)
        assertEquals("2014-02-01", rows[1].birthDate)
        assertEquals(1, bad)
    }
}
