package com.mohammedsamir.halaqati.plans

/**
 * RC27.8: smart plans belong to halaqa students only (activities: hifz / sard / review / tathbit).
 * Students whose primary group is any other program (qualifying course, "التلاوة والتجويد", generic course…)
 * are measured by their program and never get a smart plan, "no plan" alerts or plan creation prompts.
 */
object SmartPlanEligibility {
    /** halaqa_type 'regular' (or unknown/blank, e.g. legacy rows) is a halaqa. */
    fun isHalaqaType(halaqaType: String?): Boolean = halaqaType.isNullOrBlank() || halaqaType == "regular"

    fun ineligibleHalaqaIds(halaqas: List<Pair<String, String?>>): Set<String> =
        halaqas.filter { (id, type) -> id.isNotBlank() && !isHalaqaType(type) }.map { it.first }.toSet()

    fun isEligible(halaqaId: String?, ineligible: Set<String>): Boolean = halaqaId.isNullOrBlank() || halaqaId !in ineligible
}

/** RC27.8: "التلاوة والتجويد" is ONE program; legacy 'tajweed' rows are read as 'tilawah'. */
object ProgramKindRules {
    const val TILAWAH_TAJWEED_LABEL = "التلاوة والتجويد"
    fun canonical(type: String?): String = if (type == "tajweed") "tilawah" else type.orEmpty().ifBlank { "regular" }
    fun isTilawahTajweed(type: String?): Boolean = type == "tajweed" || type == "tilawah"


    val recitationStages = listOf(
        "reading_foundations" to "تأسيس القراءة",
        "noorani" to "القاعدة النورانية",
        "golden_rule" to "القاعدة الذهبية",
        "introductory" to "تمهيدية",
        "qualifying" to "تأهيلية",
        "advanced" to "عليا",
        "sanad_preparation" to "تهيئة السند",
        "sanad_ijazah" to "سند وإجازة",
        "qiraat" to "قراءات",
    )
    fun isRecitationFamily(type: String?, spec: String?): Boolean {
        val family = canonical(type)
        return family != "regular" && (isTilawahTajweed(type) ||
            specialization(spec) in setOf("tilawah", "tajweed_theory", "tajweed_practical", "noorania", "reading_remedial", "ijazah", "qiraat"))
    }
    fun stageLabel(stage: String?): String = recitationStages.firstOrNull { it.first == stage }?.second.orEmpty()
    fun validStage(type: String?, spec: String?, stage: String?): String? =
        stage?.takeIf { isRecitationFamily(type, spec) && stageLabel(it).isNotBlank() }

    fun specialization(value: String?): String = when (value) {
        "intensive_hifz" -> "hifz_intensive"
        "tajweed_applied" -> "tajweed_practical"
        "noorani" -> "noorania"
        "reading_support" -> "reading_remedial"
        else -> value.orEmpty()
    }
}

/**
 * RC27.10: the quick recitation buttons on a student's session card follow the student's kind.
 * Tathbit students: tathbit only. General hifz students: hifz + review (+ sard when the plan has a sard
 * activity this period, or the student has no plan). Program sessions: tilawah + test only.
 */
object SessionQuickActivityRules {
    fun activities(programSession: Boolean, tathbitStudent: Boolean, planActivityKeys: Set<String>): List<Pair<String, String>> = when {
        programSession -> listOf("tilawah" to "تلاوة", "test" to "اختبار")
        tathbitStudent -> listOf("tathbit" to "تثبيت")
        else -> buildList {
            add("new_hifz" to "حفظ")
            add("review_near" to "مراجعة")
            if (planActivityKeys.isEmpty() || "sard" in planActivityKeys) add("sard" to "سرد")
        }
    }
}
