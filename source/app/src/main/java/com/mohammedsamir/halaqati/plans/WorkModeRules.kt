package com.mohammedsamir.halaqati.plans

/**
 * RC27.10: "work mode". A regular halaqa and a program (التلاوة والتجويد, qualifying course, ...) are measured differently,
 * so a person who only works with programs must never see hifz / sard / review / tathbit surfaces, and the reverse.
 *
 * Pure Kotlin (no Android types) so it is covered by the self-tests.
 */
enum class WorkMode { REGULAR, PROGRAM, MIXED }

object WorkModeRules {
    fun isProgramType(halaqaType: String?): Boolean = !SmartPlanEligibility.isHalaqaType(halaqaType)

    /** Mode of a set of halaqa types: all programs -> PROGRAM, all regular -> REGULAR, otherwise MIXED. Empty -> REGULAR. */
    fun modeOf(types: Collection<String?>): WorkMode {
        if (types.isEmpty()) return WorkMode.REGULAR
        val programs = types.count { isProgramType(it) }
        return when (programs) {
            0 -> WorkMode.REGULAR
            types.size -> WorkMode.PROGRAM
            else -> WorkMode.MIXED
        }
    }

    /**
     * Mode the screens should follow.
     * - a halaqa is selected in the scope bar: that halaqa's kind;
     * - nothing selected: the mode of everything the person can reach (MIXED stays MIXED; screens then show the two kinds apart).
     */
    fun effective(selectedType: String?, hasSelection: Boolean, accessibleTypes: Collection<String?>): WorkMode =
        if (hasSelection) (if (isProgramType(selectedType)) WorkMode.PROGRAM else WorkMode.REGULAR) else modeOf(accessibleTypes)

    /** In MIXED mode a screen resolves to one kind through a local toggle (default: regular halaqat). */
    fun resolveKind(mode: WorkMode, mixedChoice: WorkMode?): WorkMode =
        if (mode == WorkMode.MIXED) (if (mixedChoice == WorkMode.PROGRAM) WorkMode.PROGRAM else WorkMode.REGULAR) else mode

    fun showSmartPlans(mode: WorkMode): Boolean = mode != WorkMode.PROGRAM
    fun showHifzSurfaces(mode: WorkMode): Boolean = mode != WorkMode.PROGRAM

    /** Students a screen should list for [mode]: program members (+ primary) or regular-halaqa students. */
    fun studentBelongs(mode: WorkMode, primaryHalaqaType: String?, memberHalaqaTypes: Collection<String?>): Boolean {
        val all = listOf(primaryHalaqaType) + memberHalaqaTypes
        return when (mode) {
            WorkMode.PROGRAM -> all.any { isProgramType(it) }
            WorkMode.REGULAR -> isProgramType(primaryHalaqaType).not() || memberHalaqaTypes.any { !isProgramType(it) }
            WorkMode.MIXED -> true
        }
    }

    /** Certificate types: (key, label). Programs: تجويد / تميز / المركز الأول / الطالب المثالي. */
    fun certificateTypes(mode: WorkMode): List<Pair<String, String>> = when (mode) {
        WorkMode.PROGRAM -> listOf(
            "tajweed" to "شهادة تجويد",
            "excellence" to "شهادة تميز",
            "rank" to "المركز الأول",
            "ideal" to "الطالب المثالي",
        )
        else -> listOf(
            "sard" to "إتمام سرد", "khatam" to "ختم القرآن", "monthly" to "التميز الشهري",
            "ideal" to "الطالب المثالي", "rank" to "المراكز الأولى",
        )
    }

    fun isProgramCertificate(type: String): Boolean = type == "tajweed" || type == "excellence"

    /** Ranking tracks: (key, label). Programs are ranked by tilawah / tajweed performance, never by hifz amounts. */
    fun rankingTracks(mode: WorkMode): List<Pair<String, String>> = when (mode) {
        WorkMode.PROGRAM -> listOf("score" to "درجة التلاوة والتجويد", "attendance" to "الحضور", "recitations" to "التسميعات", "points" to "النقاط")
        else -> listOf("hifz" to "الحفظ", "review" to "المراجعة", "sard" to "السرد")
    }

    /** Report kinds for programs: attendance, evaluation results and a course report. */
    fun reportKinds(mode: WorkMode): List<Pair<String, String>> = when (mode) {
        WorkMode.PROGRAM -> listOf("attendance" to "تقرير الحضور", "evaluation" to "نتائج التقييم", "course" to "تقرير الدورة")
        else -> emptyList()
    }

    /** Overall tilawah score of one entry's selection_data (tilawah_assessment.overall_score), or null. */
    fun programScore(overallScores: List<Double>): Double? =
        overallScores.filter { it in 0.0..100.0 }.takeIf { it.isNotEmpty() }?.average()

    data class ProgramRankInput(val studentId: String, val scores: List<Double>, val presentDays: Int, val recitations: Int, val points: Int)
    data class ProgramRankRow(val studentId: String, val score: Double?, val presentDays: Int, val recitations: Int, val points: Int)

    /** Ordered by score, then attendance, recitations and points (the user's specified order). Ties keep input order. */
    fun rankProgram(rows: List<ProgramRankInput>): List<ProgramRankRow> =
        rows.map { ProgramRankRow(it.studentId, programScore(it.scores), it.presentDays, it.recitations, it.points) }
            .sortedWith(
                compareByDescending<ProgramRankRow> { it.score ?: -1.0 }
                    .thenByDescending { it.presentDays }
                    .thenByDescending { it.recitations }
                    .thenByDescending { it.points },
            )
}
