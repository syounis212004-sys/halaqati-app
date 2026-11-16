package com.mohammedsamir.halaqati.plans

import com.mohammedsamir.halaqati.quran.QuranLocation
import com.mohammedsamir.halaqati.quran.QuranMetadata
import java.time.LocalDate
import kotlin.math.max

/**
 * RC11 Smart Plan Phases 2.0.
 *
 * The phase layer does not replace SmartPlanEngine or QuranRangeEngine. It decides
 * which activity is active, freezes phase results, keeps Hifz/Sard anchors separate,
 * and redistributes the remaining target over the remaining scheduled days.
 */
object SmartPlanPhaseEngine {
    const val TYPE_HIFZ = "hifz"
    const val TYPE_SARD = "sard"
    const val TYPE_TATHBIT = "tathbit"

    const val STATUS_DRAFT = "draft"
    const val STATUS_ACTIVE = "active"
    const val STATUS_COMPLETED = "completed"
    const val STATUS_ARCHIVED = "archived"

    const val STATE_NOT_STARTED = "not_started"
    const val STATE_ACTIVE = "active"
    const val STATE_COMPLETED = "completed"
    const val STATE_SKIPPED = "skipped"

    const val GOAL_PAGES = "pages"
    const val GOAL_JUZ = "juz"
    const val GOAL_RANGE = "quran_range"
    const val GOAL_OPEN = "open"

    const val DISTRIBUTION_EQUAL = "equal"
    const val DISTRIBUTION_FLEXIBLE = "flexible"

    /** Legacy RC11 value retained for old Sard rows. New phases use START_LAST_ACTIVITY. */
    const val START_LAST_SARD = "last_sard"
    const val START_LAST_ACTIVITY = "last_activity"
    const val START_MANUAL = "manual"
    const val START_RANGE = "range_start"
    const val START_PLAN = "plan_start"

    const val DEFAULT_SARD_TARGET_PAGES = 25.0
    const val ROUTE_FROM_NAS_TO_BAQARAH = "reverse_surahs_forward_ayahs"
    const val ROUTE_FROM_BAQARAH_TO_NAS = "forward_surahs_forward_ayahs"

    data class Anchor(
        val recitationId: String,
        val date: LocalDate,
        val surah: Int,
        val ayah: Int,
        val page: Int,
    )

    data class Phase(
        val id: String,
        val planId: String,
        val order: Int,
        val type: String,
        val startDate: LocalDate,
        val endDate: LocalDate,
        val scheduleWeekdays: Set<Int>,
        val goalMode: String,
        val targetPages: Double?,
        val targetUnits: Double?,
        val goalUnit: String,
        val distributionMode: String,
        val startMode: String,
        val rangeStartPage: Int? = null,
        val rangeEndPage: Int? = null,
        val rangeStartSurah: Int? = null,
        val rangeStartAyah: Int? = null,
        val rangeEndSurah: Int? = null,
        val rangeEndAyah: Int? = null,
        val allowSurplus: Boolean = true,
        val status: String = STATUS_ACTIVE,
    )

    data class StudentState(
        val phaseId: String,
        val studentId: String,
        val status: String = STATE_NOT_STARTED,
        val targetPagesOverride: Double? = null,
        val startDateOverride: LocalDate? = null,
        val endDateOverride: LocalDate? = null,
        val frozenCompletedPages: Double? = null,
        val frozenTargetPages: Double? = null,
        val carriedShortfallPages: Double = 0.0,
        val completionPercent: Double? = null,
    )

    data class Progress(
        val targetPages: Double?,
        val completedPages: Double,
        val remainingPages: Double?,
        val percentage: Double?,
        val scheduledDays: Int,
        val elapsedScheduledDays: Int,
        val remainingScheduledDays: Int,
        val suggestedTodayPages: Double?,
        val surplusPages: Double,
        val completedEarly: Boolean,
        val goalComplete: Boolean,
    )

    data class TimelineIssue(
        val code: String,
        val message: String,
        val blocking: Boolean = true,
    )

    enum class Lifecycle { SCHEDULED, ACTIVE, ENDED, COMPLETED, SKIPPED }

    fun weekday(date: LocalDate): Int = date.dayOfWeek.value % 7

    fun scheduledDates(phase: Phase, from: LocalDate? = null, to: LocalDate? = null): List<LocalDate> {
        val start = maxOf(phase.startDate, from ?: phase.startDate)
        val end = minOf(phase.endDate, to ?: phase.endDate)
        if (start > end) return emptyList()
        return generateSequence(start) { it.plusDays(1) }
            .takeWhile { it <= end }
            .filter { weekday(it) in phase.scheduleWeekdays }
            .toList()
    }

    fun resolvedTargetPages(phase: Phase, state: StudentState? = null): Double? {
        state?.targetPagesOverride?.takeIf { it > 0.0 }?.let { return SmartPlanEngine.round(it) }
        phase.targetPages?.takeIf { it > 0.0 }?.let { return SmartPlanEngine.round(it) }
        return when (phase.goalMode) {
            GOAL_JUZ -> phase.targetUnits?.takeIf { it > 0.0 }?.let { SmartPlanEngine.round(it * 20.0) }
            GOAL_OPEN -> null
            else -> null
        }
    }

    fun progress(
        phase: Phase,
        completedPages: Double,
        today: LocalDate,
        state: StudentState? = null,
    ): Progress {
        val baseTarget = resolvedTargetPages(phase, state)
        val carried = state?.carriedShortfallPages?.coerceAtLeast(0.0) ?: 0.0
        val target = when {
            baseTarget != null -> SmartPlanEngine.round(baseTarget + carried)
            carried > EPSILON -> SmartPlanEngine.round(carried)
            else -> null
        }
        val completed = state?.frozenCompletedPages ?: completedPages.coerceAtLeast(0.0)
        val allDates = scheduledDates(phase)
        val elapsed = allDates.count { it <= today }
        val remainingDates = allDates.count { it >= today && it <= phase.endDate }
        val remaining = target?.let { max(0.0, it - completed) }
        val surplus = target?.let { max(0.0, completed - it) } ?: 0.0
        val complete = target != null && remaining != null && remaining <= EPSILON
        val completedEarly = complete && today < phase.endDate
        val suggested = when {
            target == null -> null
            complete -> if (phase.allowSurplus) 0.0 else null
            remainingDates <= 0 -> remaining
            phase.distributionMode == DISTRIBUTION_EQUAL -> SmartPlanEngine.round(target / allDates.size.coerceAtLeast(1))
            else -> SmartPlanEngine.round((remaining ?: 0.0) / remainingDates)
        }
        return Progress(
            targetPages = target,
            completedPages = SmartPlanEngine.round(completed),
            remainingPages = remaining?.let(SmartPlanEngine::round),
            percentage = target?.takeIf { it > EPSILON }?.let { SmartPlanEngine.round((completed / it * 100.0).coerceAtLeast(0.0), 1) },
            scheduledDays = allDates.size,
            elapsedScheduledDays = elapsed,
            remainingScheduledDays = remainingDates,
            suggestedTodayPages = suggested,
            surplusPages = SmartPlanEngine.round(surplus),
            completedEarly = completedEarly,
            goalComplete = complete,
        )
    }

    fun latestSardAnchor(
        recitations: List<SmartPlanEngine.Recitation>,
        today: LocalDate,
    ): Anchor? = SardAnchorResolver.resolve(recitations, today)?.let {
        Anchor(it.recitationId, it.date, it.surah, it.ayah, it.page)
    }

    fun effectiveActivePhase(
        phases: List<Phase>,
        states: List<StudentState>,
        studentId: String,
        today: LocalDate,
    ): Phase? {
        val byPhase = states.filter { it.studentId == studentId }.associateBy { it.phaseId }
        fun effective(phase: Phase): Phase {
            val state = byPhase[phase.id]
            val start = state?.startDateOverride ?: phase.startDate
            val end = state?.endDateOverride ?: phase.endDate
            return phase.copy(startDate = start, endDate = maxOf(start, end))
        }
        return phases.asSequence()
            .filter { it.status == STATUS_ACTIVE }
            .map(::effective)
            .filter { today >= it.startDate && today <= it.endDate }
            .filter { byPhase[it.id]?.status !in setOf(STATE_COMPLETED, STATE_SKIPPED) }
            .sortedBy { it.order }
            .firstOrNull()
    }



    /** RC11.2: a plan with phases must remain visible even on a gap day.
     * The returned phase is display/context only; effectiveActivePhase remains the sole
     * authority for generating a daily assignment. */
    fun contextPhase(
        phases: List<Phase>,
        states: List<StudentState>,
        studentId: String,
        today: LocalDate,
    ): Phase? {
        effectiveActivePhase(phases, states, studentId, today)?.let { return it }
        val byPhase = states.filter { it.studentId == studentId }.associateBy { it.phaseId }
        fun effective(phase: Phase): Phase {
            val state = byPhase[phase.id]
            val start = state?.startDateOverride ?: phase.startDate
            val end = state?.endDateOverride ?: phase.endDate
            return phase.copy(startDate = start, endDate = maxOf(start, end))
        }
        val usable = phases.asSequence()
            .filter { it.status != STATUS_ARCHIVED }
            .map(::effective)
            .filter { byPhase[it.id]?.status != STATE_SKIPPED }
            .sortedBy { it.order }
            .toList()
        return usable.filter { it.endDate < today }.maxByOrNull { it.endDate }
            ?: usable.filter { it.startDate > today }.minByOrNull { it.startDate }
            ?: usable.firstOrNull()
    }

    fun lifecycle(phase: Phase, state: StudentState?, today: LocalDate): Lifecycle {
        if (state?.status == STATE_SKIPPED) return Lifecycle.SKIPPED
        if (state?.status == STATE_COMPLETED || state?.frozenCompletedPages != null) return Lifecycle.COMPLETED
        val start = state?.startDateOverride ?: phase.startDate
        val end = state?.endDateOverride ?: phase.endDate
        return when {
            today < start -> Lifecycle.SCHEDULED
            today > end -> Lifecycle.ENDED
            else -> Lifecycle.ACTIVE
        }
    }

    /** Validates the RC11.2 invariant: one Hifz phase then one Sard phase, with no overlap.
     * Gaps are surfaced explicitly instead of silently falling back to the parent plan. */
    fun validateTimeline(phases: List<Phase>): List<TimelineIssue> {
        val active = phases.filter { it.status != STATUS_ARCHIVED }.sortedWith(compareBy<Phase> { it.startDate }.thenBy { it.order })
        val issues = mutableListOf<TimelineIssue>()
        if (active.count { it.type == TYPE_HIFZ } > 1) issues += TimelineIssue("multiple_hifz", "الخطة الشهرية تدعم مرحلة حفظ واحدة فقط في RC11.2")
        if (active.count { it.type == TYPE_SARD } > 1) issues += TimelineIssue("multiple_sard", "الخطة الشهرية تدعم مرحلة سرد واحدة فقط في RC11.2")
        val tathbitCount = active.count { it.type == TYPE_TATHBIT }
        if (tathbitCount > 0 && active.any { it.type == TYPE_HIFZ || it.type == TYPE_SARD }) {
            issues += TimelineIssue("tathbit_mixed", "خطة التثبيت تحتوي مراحل تثبيت فقط؛ لا يجوز خلطها بحفظ أو سرد")
        }
        if (tathbitCount > 1) issues += TimelineIssue("multiple_tathbit", "الخطة تدعم مرحلة تثبيت واحدة فعّالة")
        active.zipWithNext().forEach { (a, b) ->
            when {
                b.startDate <= a.endDate -> issues += TimelineIssue("overlap", "تداخل بين ${a.startDate}–${a.endDate} و${b.startDate}–${b.endDate}")
                b.startDate > a.endDate.plusDays(1) -> issues += TimelineIssue("gap", "يوجد فراغ زمني من ${a.endDate.plusDays(1)} إلى ${b.startDate.minusDays(1)}", blocking = false)
            }
        }
        val hifz = active.firstOrNull { it.type == TYPE_HIFZ }
        val sard = active.firstOrNull { it.type == TYPE_SARD }
        if (hifz != null && sard != null && sard.order <= hifz.order) issues += TimelineIssue("order", "مرحلة السرد يجب أن تأتي بعد مرحلة الحفظ")
        return issues
    }

    fun configuredStart(phase: Phase): QuranLocation? = when {
        phase.rangeStartSurah != null && phase.rangeStartAyah != null -> QuranLocation(phase.rangeStartSurah, phase.rangeStartAyah)
        phase.rangeStartPage != null -> QuranMetadata.pageStart(phase.rangeStartPage)
        else -> null
    }

    fun usesHistoricalSardAnchor(phase: Phase): Boolean =
        phase.type == TYPE_SARD && phase.startMode in setOf(START_LAST_SARD, START_LAST_ACTIVITY)

    fun usesHistoricalActivityAnchor(phase: Phase): Boolean =
        phase.startMode == START_LAST_ACTIVITY || (phase.type == TYPE_SARD && phase.startMode == START_LAST_SARD)

    /** Shortfall is never automatically carried. This returns the optional suggestion only. */
    fun previousShortfall(previous: Progress): Double = SmartPlanEngine.round(previous.remainingPages ?: 0.0)

    private const val EPSILON = 0.001
}
