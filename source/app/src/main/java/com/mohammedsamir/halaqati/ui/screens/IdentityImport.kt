package com.mohammedsamir.halaqati.ui.screens

import java.time.LocalDate

/** One pasted line: student name, national id, birth date (already normalised). */
data class IdentityRow(val name: String, val nationalId: String, val birthDate: String)

/**
 * RC27.47: parses rows pasted from Excel / CSV: name, national id, birth date, separated by tab, comma, semicolon or |.
 * Pure Kotlin so it is unit-tested. Dates may be yyyy-MM-dd, yyyy/MM/dd, or dd/MM/yyyy (Arabic digits are fine).
 */
object IdentityImport {
    private val arabicDigits = '٠'..'٩'

    private fun latinDigits(s: String): String = s.map { c -> if (c in arabicDigits) '0' + (c - '٠') else c }.joinToString("")

    /** Same name written with different alef/yaa/taa-marbuta forms or extra spaces still matches. */
    fun normName(s: String): String = s.trim()
        .replace(Regex("[\\u064B-\\u065F\\u0670\\u0640]"), "")
        .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا')
        .replace('ى', 'ي').replace('ة', 'ه')
        .replace(Regex("\\s+"), " ")
        .lowercase()

    fun parseDate(raw: String, today: LocalDate = LocalDate.now()): String? {
        val t = latinDigits(raw.trim()).replace('/', '-').replace('.', '-')
        val iso = Regex("^(\\d{4})-(\\d{1,2})-(\\d{1,2})$").matchEntire(t)
        val dmy = Regex("^(\\d{1,2})-(\\d{1,2})-(\\d{4})$").matchEntire(t)
        val parts: Triple<Int, Int, Int> = when {
            iso != null -> Triple(iso.groupValues[1].toInt(), iso.groupValues[2].toInt(), iso.groupValues[3].toInt())
            dmy != null -> Triple(dmy.groupValues[3].toInt(), dmy.groupValues[2].toInt(), dmy.groupValues[1].toInt())
            else -> return null
        }
        val date = runCatching { LocalDate.of(parts.first, parts.second, parts.third) }.getOrNull() ?: return null
        return if (date.isAfter(today) || date.year < 1950) null else date.toString()
    }

    /** Returns the readable rows and the number of lines that could not be read. */
    fun parse(text: String, today: LocalDate = LocalDate.now()): Pair<List<IdentityRow>, Int> {
        val rows = ArrayList<IdentityRow>()
        var bad = 0
        text.lines().map { it.trim() }.filter { it.isNotEmpty() }.forEach { line ->
            val cells = line.split(Regex("[\\t,;|]")).map { it.trim() }.filter { it.isNotEmpty() }
            val nid = cells.getOrNull(1)?.let { latinDigits(it).filter { c -> c.isDigit() } }.orEmpty()
            val date = cells.getOrNull(2)?.let { parseDate(it, today) }
            if (cells.size >= 3 && nid.length >= 5 && date != null) rows += IdentityRow(cells[0], nid, date) else bad++
        }
        return rows to bad
    }
}
