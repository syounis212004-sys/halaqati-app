package com.mohammedsamir.halaqati.data

import org.json.JSONObject
import java.time.LocalDate

/** Donor contests (المسابقات): pure validation, state-transition and list rules. The server enforces the same limits (see migration 20261125). */
enum class ContestStatus(val wire: String, val label: String) {
    PENDING("pending", "بانتظار المدير"),
    APPROVED("approved", "معتمدة"),
    REJECTED("rejected", "مرفوضة");

    companion object {
        fun from(v: String?) = entries.firstOrNull { it.wire.equals(v?.trim(), true) } ?: PENDING
    }
}

data class ContestDraft(
    val name: String,
    val title: String,
    val conditions: String,
    val reward: String,
    val startsOn: String? = null,
    val endsOn: String? = null,
)

data class Contest(
    val id: String,
    val name: String,
    val title: String,
    val conditions: String,
    val reward: String,
    val status: ContestStatus,
    val halaqaId: String?,
    val halaqaName: String?,
    val donorName: String?,
    val rejectReason: String?,
    val startsOn: String?,
    val endsOn: String?,
    val createdAt: String?,
) {
    companion object {
        private fun JSONObject.str(k: String): String? = if (isNull(k)) null else optString(k).trim().takeIf { it.isNotEmpty() && it != "null" }
        fun fromJson(o: JSONObject) = Contest(
            id = o.optString("id"), name = o.optString("name"), title = o.optString("title"),
            conditions = o.optString("conditions"), reward = o.optString("reward"),
            status = ContestStatus.from(o.optString("status")),
            halaqaId = o.str("halaqa_id"), halaqaName = o.str("halaqa_name"), donorName = o.str("donor_name"),
            rejectReason = o.str("reject_reason"), startsOn = o.str("starts_on"), endsOn = o.str("ends_on"),
            createdAt = o.str("created_at"),
        )
    }
}

enum class ContestPickerState { NO_HALAQA, SINGLE, CHOOSE }

object ContestRules {
    const val NAME_MIN = 3; const val NAME_MAX = 80
    const val TITLE_MIN = 3; const val TITLE_MAX = 120
    const val CONDITIONS_MIN = 10; const val CONDITIONS_MAX = 2000
    const val REWARD_MIN = 3; const val REWARD_MAX = 500
    const val REASON_MIN = 3; const val REASON_MAX = 500

    fun DATE_ERROR(which: String) = "تاريخ $which غير صالح. اكتبه هكذا: 01-12-2026 (يوم-شهر-سنة)"

    /**
     * A date typed by a person. Accepts day-month-year (01-12-2026, 1/12/2026) and year-month-day (2026-12-01), with - / or . between the parts,
     * Arabic-Indic or Persian digits, and ignores the invisible direction marks that right-to-left text carries. Null when it is not a real date.
     */
    fun parseDate(raw: String): LocalDate? {
        val t = raw.map { c ->
            when (c) { in '\u0660'..'\u0669' -> '0' + (c - '\u0660'); in '\u06F0'..'\u06F9' -> '0' + (c - '\u06F0'); else -> c }
        }.filterNot { it in '\u200E'..'\u200F' || it in '\u202A'..'\u202E' || it in '\u2066'..'\u2069' || it.isWhitespace() }.joinToString("")
        val m = Regex("^(\\d{1,4})[-/.](\\d{1,2})[-/.](\\d{1,4})$").matchEntire(t) ?: return null
        val a = m.groupValues[1]; val b = m.groupValues[2].toInt(); val c = m.groupValues[3]
        val (y, mo, d) = when {
            a.length == 4 && c.length <= 2 -> Triple(a.toInt(), b, c.toInt())
            c.length == 4 && a.length <= 2 -> Triple(c.toInt(), b, a.toInt())
            else -> return null
        }
        return runCatching { LocalDate.of(y, mo, d) }.getOrNull()
    }

    /** The ISO form the database expects (2026-12-01), or the text unchanged when it is not a date (validation reports it). */
    fun normalizeDate(raw: String?): String? = raw?.trim()?.takeIf { it.isNotEmpty() }?.let { parseDate(it)?.toString() ?: it }

    /** Field key -> Arabic message. Empty map = valid. */
    fun validate(d: ContestDraft): Map<String, String> {
        val e = linkedMapOf<String, String>()
        fun check(key: String, label: String, v: String, min: Int, max: Int) {
            val n = v.trim().length
            when {
                n == 0 -> e[key] = "$label مطلوب"
                n < min -> e[key] = "$label قصير جدًا (الحد الأدنى $min أحرف)"
                n > max -> e[key] = "$label طويل جدًا (الحد الأقصى $max حرفًا)"
            }
        }
        check("name", "اسم المسابقة", d.name, NAME_MIN, NAME_MAX)
        check("title", "العنوان", d.title, TITLE_MIN, TITLE_MAX)
        check("conditions", "الشروط", d.conditions, CONDITIONS_MIN, CONDITIONS_MAX)
        check("reward", "المكافأة", d.reward, REWARD_MIN, REWARD_MAX)
        val s = d.startsOn?.takeIf { it.isNotBlank() }?.let { parseDate(it) ?: run { e["startsOn"] = DATE_ERROR("البداية"); null } }
        val en = d.endsOn?.takeIf { it.isNotBlank() }?.let { parseDate(it) ?: run { e["endsOn"] = DATE_ERROR("النهاية"); null } }
        if (s != null && en != null && en.isBefore(s)) e["endsOn"] = "تاريخ النهاية قبل تاريخ البداية"
        return e
    }

    /** Only a pending contest can be decided. */
    fun canTransition(from: ContestStatus, to: ContestStatus) =
        from == ContestStatus.PENDING && (to == ContestStatus.APPROVED || to == ContestStatus.REJECTED)

    /** Null when the decision may be sent; otherwise "transition", "reason" or "halaqa". */
    fun decisionError(from: ContestStatus, to: ContestStatus, reason: String, halaqaId: String?): String? = when {
        !canTransition(from, to) -> "transition"
        to == ContestStatus.REJECTED && reason.trim().length !in REASON_MIN..REASON_MAX -> "reason"
        to == ContestStatus.APPROVED && halaqaId.isNullOrBlank() -> "halaqa"
        else -> null
    }

    fun pickerState(linkedHalaqaIds: List<String>) = when (linkedHalaqaIds.size) {
        0 -> ContestPickerState.NO_HALAQA
        1 -> ContestPickerState.SINGLE
        else -> ContestPickerState.CHOOSE
    }

    fun filter(items: List<Contest>, status: ContestStatus?) = if (status == null) items else items.filter { it.status == status }

    const val NO_HALAQA_MESSAGE = "لم يُربط حسابك بأي حلقة بعد. سيحدد مدير النظام حلقتك عند مراجعة المسابقة."
}

/**
 * Donor contests need the database migration `20261125_v2920_donor_contests.sql`; it was applied to the project on 2026-11-25 (migration name
 * donor_contests_v2920), so the feature is on. Set to false to hide every contest entry (menus and home cards) without touching the database.
 */
object ContestsFeature { @Volatile var enabled: Boolean = true }
