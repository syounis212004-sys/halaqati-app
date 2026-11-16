package com.mohammedsamir.halaqati.model

import java.time.LocalDate

data class CertificateRequiredFieldError(val key: String, val arabicLabel: String)

object CertificateRules {
    private val legacyPattern = Regex("^HAL-CERT-\\d{4}-\\d{5,}$")

    fun normalizeSeriesCode(raw: String): String = raw
        .uppercase()
        .filter { it in 'A'..'Z' || it in '0'..'9' }
        .take(6)

    fun validateRequiredSnapshot(
        snapshot: CertificateRenderSnapshot,
        requireOfficialNumber: Boolean,
    ): List<CertificateRequiredFieldError> = buildList {
        fun need(key: String, label: String, value: String) {
            if (value.isBlank()) add(CertificateRequiredFieldError(key, label))
        }
        need("center_name", "اسم المركز", snapshot.centerName)
        need("halaqa_name", "اسم الحلقة", snapshot.halaqaName)
        need("kicker", "عنوان شهادة تقدير وإنجاز", "شهادة تقدير وإنجاز")
        need("title", "عنوان الشهادة", snapshot.title)
        need("student_name", "اسم الطالب", snapshot.studentName)
        need("achievement", "نص الإنجاز", snapshot.achievement)
        need("issue_date", "تاريخ الإصدار", snapshot.issueDate)
        if (snapshot.intro.isNotEmpty()) need("intro", "المقدمة", snapshot.intro)
        if (snapshot.dua.isNotEmpty()) need("dua", "الدعاء", snapshot.dua)
        if (requireOfficialNumber) need("official_number", "رقم الشهادة الرسمي", snapshot.officialNumber)
        if (snapshot.issueDate.isNotBlank() && runCatching { LocalDate.parse(snapshot.issueDate) }.isFailure) {
            add(CertificateRequiredFieldError("issue_date", "تاريخ إصدار صالح"))
        }
    }

    fun canReviseIdentity(
        original: CertificateRecord,
        candidateStudentId: String,
        candidateSeriesKey: String,
        candidateIssueDate: String,
    ): Boolean {
        val oldYear = original.issueDate?.let { runCatching { LocalDate.parse(it).year }.getOrNull() }
        val newYear = runCatching { LocalDate.parse(candidateIssueDate).year }.getOrNull()
        val originalSeries = original.seriesKey ?: inferSeriesKey(original.certificateType)
        return original.studentId == candidateStudentId && originalSeries == candidateSeriesKey && oldYear == newYear
    }

    fun isLegacyNumber(number: String?): Boolean = !number.isNullOrBlank() && legacyPattern.matches(number)

    fun inferSeriesKey(certificateType: String): String = when (certificateType) {
        "sard" -> "sard"
        "hifz" -> "khatm"
        else -> "general"
    }
}

fun normalizeSeriesCode(raw: String): String = CertificateRules.normalizeSeriesCode(raw)
fun validateRequiredSnapshot(snapshot: CertificateRenderSnapshot, requireOfficialNumber: Boolean): List<CertificateRequiredFieldError> =
    CertificateRules.validateRequiredSnapshot(snapshot, requireOfficialNumber)
fun canReviseIdentity(original: CertificateRecord, candidateStudentId: String, candidateSeriesKey: String, candidateIssueDate: String): Boolean =
    CertificateRules.canReviseIdentity(original, candidateStudentId, candidateSeriesKey, candidateIssueDate)
fun isLegacyNumber(number: String?): Boolean = CertificateRules.isLegacyNumber(number)
