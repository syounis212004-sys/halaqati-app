package com.mohammedsamir.halaqati.plans

import com.mohammedsamir.halaqati.quran.QuranMetadata
import java.time.LocalDate
import kotlin.math.max

/**
 * RC9.1 canonical Smart Plan state exposed to repositories/UI.
 *
 * SmartPlanEngine remains responsible for deterministic plan arithmetic. This layer
 * converts that arithmetic into one immutable state object so every screen reads the
 * same target/completed/required/remaining/week/anchor/next-assignment values.
 */
object SmartPlanCoreStateEngine {
    enum class StatusCode {
        NOT_STARTED,
        ON_TRACK,
        AHEAD,
        BEHIND,
        COMPLETE_EARLY,
        COMPLETE,
    }

    data class MemorizationAnchor(
        val recitationId: String,
        val date: LocalDate,
        val surah: Int,
        val ayah: Int,
        val page: Int,
    )

    data class State(
        val planId: String,
        val asOfDate: LocalDate,
        val status: StatusCode,
        val statusLabel: String,
        val targetPages: Double,
        val requiredByToday: Double,
        val completedPages: Double,
        val remainingPages: Double,
        val surplusPages: Double,
        val completedEarly: Boolean,
        val daysRemainingInPlan: Int,
        val aheadBySchedule: Double,
        val behindBySchedule: Double,
        val scheduleDebt: Double,
        val completionPercentage: Double,
        val weekly: SmartPlanEngine.WeeklyProgress,
        val lastMemorizationAnchor: MemorizationAnchor?,
        val nextAssignment: SmartPlanEngine.Assignment?,
    )

    fun build(
        plan: SmartPlanEngine.Plan,
        progress: SmartPlanEngine.Progress,
        recitations: List<SmartPlanEngine.Recitation>,
        today: LocalDate,
        weeklyTargetOverride: Double? = null,
    ): State {
        val weekly = applyWeeklyTargetOverride(
            base = progress.weekly,
            plan = plan,
            configuredWeeklyTarget = weeklyTargetOverride,
        )
        val status = statusFor(plan, progress, today)
        val surplus = SmartPlanEngine.round(max(0.0, progress.completed - plan.targetPages))
        val completedEarly = status == StatusCode.COMPLETE_EARLY
        val daysRemaining = if (today < plan.endDate) java.time.temporal.ChronoUnit.DAYS.between(today, plan.endDate).toInt() else 0
        val assignment = progress.assignment.takeIf {
            it.pages > EPSILON || it.blockedByRetry || it.milestone != null || it.lineKeys.isNotEmpty()
        }
        return State(
            planId = plan.id,
            asOfDate = today,
            status = status,
            statusLabel = statusLabel(status),
            targetPages = SmartPlanEngine.round(plan.targetPages),
            requiredByToday = progress.plannedByToday,
            completedPages = progress.completed,
            remainingPages = max(0.0, progress.remaining),
            surplusPages = surplus,
            completedEarly = completedEarly,
            daysRemainingInPlan = daysRemaining,
            aheadBySchedule = progress.aheadBy,
            behindBySchedule = progress.behindBy,
            scheduleDebt = progress.deficit,
            completionPercentage = progress.percentage,
            weekly = weekly,
            lastMemorizationAnchor = latestMemorizationAnchor(plan, recitations, today),
            nextAssignment = assignment,
        )
    }

    private fun statusFor(
        plan: SmartPlanEngine.Plan,
        progress: SmartPlanEngine.Progress,
        today: LocalDate,
    ): StatusCode = when {
        today < plan.startDate -> StatusCode.NOT_STARTED
        progress.remaining <= EPSILON && today < plan.endDate -> StatusCode.COMPLETE_EARLY
        progress.remaining <= EPSILON -> StatusCode.COMPLETE
        progress.behindBy > EPSILON -> StatusCode.BEHIND
        progress.aheadBy > EPSILON -> StatusCode.AHEAD
        else -> StatusCode.ON_TRACK
    }

    private fun statusLabel(status: StatusCode): String = when (status) {
        StatusCode.NOT_STARTED -> "لم تبدأ الخطة"
        StatusCode.ON_TRACK -> "على الخطة"
        StatusCode.AHEAD -> "متقدم"
        StatusCode.BEHIND -> "متأخر"
        StatusCode.COMPLETE_EARLY -> "أتم الخطة مبكرًا"
        StatusCode.COMPLETE -> "أكمل الخطة"
    }

    /**
     * A configured student weekly target is authoritative when present. For a
     * partial first/last week it is prorated only across plan weekdays that are
     * actually available in that week. This keeps a 40-page weekly target as
     * 40 pages in a normal five-day week, but does not charge a student for days
     * before a plan starts or after it ends.
     */
    private fun applyWeeklyTargetOverride(
        base: SmartPlanEngine.WeeklyProgress,
        plan: SmartPlanEngine.Plan,
        configuredWeeklyTarget: Double?,
    ): SmartPlanEngine.WeeklyProgress {
        val configured = configuredWeeklyTarget?.takeIf { it > EPSILON } ?: return base
        val normalScheduledDays = plan.scheduleWeekdays.count { it != plan.catchUpWeekday }.coerceAtLeast(1)
        if (base.scheduledDays <= 0) return base.copy(
            target = 0.0,
            requiredByToday = 0.0,
            remaining = 0.0,
            aheadBy = SmartPlanEngine.round(base.completed),
            behindBy = 0.0,
        )

        val target = configured * base.scheduledDays / normalScheduledDays
        val required = configured * base.elapsedScheduledDays / normalScheduledDays
        val completed = base.completed
        return base.copy(
            target = SmartPlanEngine.round(target),
            requiredByToday = SmartPlanEngine.round(required),
            remaining = SmartPlanEngine.round(max(0.0, target - completed)),
            aheadBy = SmartPlanEngine.round(max(0.0, completed - required)),
            behindBy = SmartPlanEngine.round(max(0.0, required - completed)),
        )
    }

    /**
     * Memorization owns its own anchor. Sard/test/review records are deliberately
     * excluded so none of them can move the next hifz location.
     */
    private fun latestMemorizationAnchor(
        plan: SmartPlanEngine.Plan,
        recitations: List<SmartPlanEngine.Recitation>,
        today: LocalDate,
    ): MemorizationAnchor? {
        val latest = recitations.asSequence()
            .filter { it.date <= today }
            .filter { it.activityType.lowercase() in HIFZ_ACTIVITIES }
            .filter { SmartPlanEngine.isPassing(it, plan) }
            .filter { it.endSurah != null && it.endAyah != null }
            .maxWithOrNull(compareBy<SmartPlanEngine.Recitation> { it.date }.thenBy { it.createdAt })
            ?: return null

        val surah = latest.endSurah ?: return null
        val ayah = latest.endAyah ?: return null
        if (surah !in 1..114 || ayah !in 1..QuranMetadata.maxAyah(surah)) return null
        val page = latest.endPage?.takeIf { it in 1..604 } ?: QuranMetadata.pageFor(surah, ayah)
        return MemorizationAnchor(
            recitationId = latest.id,
            date = latest.date,
            surah = surah,
            ayah = ayah,
            page = page,
        )
    }

    private val HIFZ_ACTIVITIES = setOf("new_hifz", "hifz")
    private const val EPSILON = 0.001
}
