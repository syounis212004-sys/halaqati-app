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
        val s = d.startsOn?.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() ?: run { e["startsOn"] = "تاريخ البداية غير صالح"; null } }
        val en = d.endsOn?.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() ?: run { e["endsOn"] = "تاريخ النهاية غير صالح"; null } }
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
 * Donor contests need the database migration `20261125_v2920_donor_contests.sql`, which is applied only with the owner's approval.
 * Until then the feature stays out of every menu and home screen. Flip to true (and release) after the migration is applied.
 */
object ContestsFeature { @Volatile var enabled: Boolean = false }
