package com.mohammedsamir.halaqati.data

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/** Pure Kotlin validation/parsing rules shared by Import Center and tests. */
object ImportRules {
    private val datePatterns = listOf(
        "yyyy-MM-dd", "d/M/yyyy", "dd/MM/yyyy", "d-M-yyyy", "dd-MM-yyyy", "yyyy/M/d",
    ).map(DateTimeFormatter::ofPattern)

    fun normalizeDigits(value: String): String = buildString(value.length) {
        value.forEach { c ->
            append(
                when (c) {
                    '٠', '۰' -> '0'; '١', '۱' -> '1'; '٢', '۲' -> '2'; '٣', '۳' -> '3'; '٤', '۴' -> '4'
                    '٥', '۵' -> '5'; '٦', '۶' -> '6'; '٧', '۷' -> '7'; '٨', '۸' -> '8'; '٩', '۹' -> '9'
                    else -> c
                },
            )
        }
    }

    fun decimal(value: String): Double? {
        val raw = normalizeDigits(value).trim()
        if (raw.isBlank()) return null
        // A single comma is accepted as decimal separator; mixed separators are rejected.
        if (raw.contains(',') && raw.contains('.')) return null
        val parsed = raw.replace(',', '.').toDoubleOrNull() ?: return null
        return parsed.takeIf { it.isFinite() }
    }

    fun integer(value: String): Int? {
        val d = decimal(value) ?: return null
        if (d % 1.0 != 0.0 || d < Int.MIN_VALUE || d > Int.MAX_VALUE) return null
        return d.toInt()
    }

    fun date(value: String): LocalDate? {
        val raw = normalizeDigits(value).trim()
        if (raw.isBlank()) return null
        decimal(raw)?.let { serial ->
            if (serial in 1.0..100000.0 && serial % 1.0 == 0.0) {
                return runCatching { LocalDate.of(1899, 12, 30).plusDays(serial.toLong()) }.getOrNull()
            }
        }
        datePatterns.forEach { formatter ->
            runCatching { LocalDate.parse(raw, formatter) }.getOrNull()?.let { return it }
        }
        return null
    }

    fun month(value: String): YearMonth? {
        val raw = normalizeDigits(value).trim()
        if (raw.isBlank()) return null
        runCatching { YearMonth.parse(raw) }.getOrNull()?.let { return it }
        if (raw.length >= 7) runCatching { YearMonth.parse(raw.take(7)) }.getOrNull()?.let { return it }
        return date(raw)?.let(YearMonth::from)
    }

    fun activityType(value: String): String? {
        val raw = value.trim()
        if (raw.isBlank()) return null
        val n = raw.lowercase()
            .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا')
        return when {
            n == "new_hifz" || n == "hifz" || n.contains("حفظ") -> "new_hifz"
            n == "sard" || n.contains("سرد") -> "sard"
            n == "review_far" || n.contains("بعيد") -> "review_far"
            n == "review_near" || n.startsWith("review") || n.contains("مراج") || n.contains("تثبيت") -> "review_near"
            n == "test" || n.contains("اختبار") -> "test"
            n == "tilawah" || n.contains("تلاو") -> "tilawah"
            else -> null
        }
    }

    fun studentTrack(value: String): String? = StudentTrackRules.parseInput(value)

    fun validPage(value: Int?): Boolean = value == null || value in 1..604

    fun validPageRange(start: Int?, end: Int?): Boolean =
        validPage(start) && validPage(end) && (start == null || end == null || start <= end)

    fun validRecitation(
        activityType: String?,
        pages: Double?,
        mistakes: Int?,
        prompts: Int?,
        startPage: Int?,
        endPage: Int?,
    ): Boolean {
        if (activityType == null || pages == null || !pages.isFinite() || pages <= 0.0 || pages > 604.0) return false
        if (mistakes != null && mistakes < 0) return false
        if (prompts != null && prompts < 0) return false
        return validPageRange(startPage, endPage)
    }

    fun validMonthlyGoals(hifz: Double?, review: Double?, sard: Double?, expectedDays: Int?): Boolean {
        val values = listOf(hifz, review, sard).filterNotNull()
        if (values.any { !it.isFinite() || it < 0.0 || it > 604.0 }) return false
        if (expectedDays != null && expectedDays !in 1..31) return false
        return true
    }
    enum class RowDisposition { VALID, SKIP, INVALID }

    fun studentDisposition(name: String, track: String, dateOfBirth: String = ""): RowDisposition {
        if (name.trim().length < 2) return RowDisposition.SKIP
        if (studentTrack(track) == null) return RowDisposition.INVALID
        if (dateOfBirth.isNotBlank() && date(dateOfBirth) == null) return RowDisposition.INVALID
        return RowDisposition.VALID
    }

    fun recitationDisposition(
        student: String,
        dateValue: String,
        activityValue: String,
        pagesValue: String,
        mistakesValue: String = "",
        promptsValue: String = "",
        startPageValue: String = "",
        endPageValue: String = "",
    ): RowDisposition {
        if (student.trim().isBlank()) return RowDisposition.SKIP
        if (date(dateValue) == null) return RowDisposition.INVALID
        val activity = activityType(activityValue) ?: return RowDisposition.INVALID
        val pages = decimal(pagesValue) ?: return RowDisposition.INVALID
        val mistakes = if (mistakesValue.isBlank()) 0 else integer(mistakesValue) ?: return RowDisposition.INVALID
        val prompts = if (promptsValue.isBlank()) 0 else integer(promptsValue) ?: return RowDisposition.INVALID
        val start = if (startPageValue.isBlank()) null else integer(startPageValue) ?: return RowDisposition.INVALID
        val end = if (endPageValue.isBlank()) null else integer(endPageValue) ?: return RowDisposition.INVALID
        return if (validRecitation(activity, pages, mistakes, prompts, start, end)) RowDisposition.VALID else RowDisposition.INVALID
    }

    fun monthlyGoalDisposition(
        student: String,
        monthValue: String,
        hifzValue: String = "",
        reviewValue: String = "",
        sardValue: String = "",
        expectedDaysValue: String = "",
    ): RowDisposition {
        if (student.trim().isBlank()) return RowDisposition.SKIP
        if (month(monthValue) == null) return RowDisposition.INVALID
        fun goal(raw: String): Double? = if (raw.isBlank()) 0.0 else decimal(raw)
        val hifz = goal(hifzValue) ?: return RowDisposition.INVALID
        val review = goal(reviewValue) ?: return RowDisposition.INVALID
        val sard = goal(sardValue) ?: return RowDisposition.INVALID
        val expectedDays = if (expectedDaysValue.isBlank()) 20 else integer(expectedDaysValue) ?: return RowDisposition.INVALID
        if (!validMonthlyGoals(hifz, review, sard, expectedDays)) return RowDisposition.INVALID
        if (hifz == 0.0 && review == 0.0 && sard == 0.0) return RowDisposition.SKIP
        return RowDisposition.VALID
    }

}
