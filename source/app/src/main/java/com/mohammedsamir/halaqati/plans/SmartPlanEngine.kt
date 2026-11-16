package com.mohammedsamir.halaqati.plans

import com.mohammedsamir.halaqati.quran.QuranMetadata

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.floor

/**
 * Native port of the deterministic Smart Plans core from Halaqati v2.44.0.
 *
 * This file is deliberately Android-free so it can be unit-tested with plain Kotlin.
 * Day-of-week values follow the legacy JavaScript convention: Sunday=0 ... Saturday=6.
 */
object SmartPlanEngine {
    private val evaluationRank = mapOf(
        "repeat" to 0,
        "weak" to 0,
        "good" to 2,
        "very_good" to 3,
        "excellent" to 4,
    )

    private val typeActivities = mapOf(
        "hifz" to setOf("new_hifz", "hifz"),
        "review" to setOf("review", "review_near", "review_far"),
        "sard" to setOf("sard"),
        "tathbit" to setOf("tathbit", "review_far"),
    )

    data class PauseRange(val from: LocalDate, val to: LocalDate, val reason: String = "pause")

    data class Settings(
        val minimumDailyMode: String = "dynamic",
        val minimumDailyPages: Double = 0.0,
        val pauseRanges: List<PauseRange> = emptyList(),
        val intensiveWeekFrom: LocalDate? = null,
        val intensiveWeekTo: LocalDate? = null,
        val intensiveWeekdays: Set<Int> = setOf(0, 1, 2, 3, 4, 5),
        val memorizationLineKeys: List<String> = emptyList(),
        val routeDirection: String? = null,
    )

    data class Plan(
        val id: String,
        val scopeType: String = "student",
        val halaqaId: String,
        val studentId: String? = null,
        val categoryId: String? = null,
        val parentPlanId: String? = null,
        val planType: String = "hifz",
        val targetPages: Double,
        val startDate: LocalDate,
        val endDate: LocalDate,
        val scheduleWeekdays: Set<Int> = setOf(0, 1, 2, 3, 4),
        val catchUpWeekday: Int? = null,
        val maxDailyMultiplier: Double = 1.5,
        val autoExtend: Boolean = true,
        val qualityGateEnabled: Boolean = true,
        val minimumEvaluation: String = "very_good",
        val rangeStartPage: Int? = null,
        val rangeEndPage: Int? = null,
        val milestoneMode: String = "none",
        val milestoneRequiresApproval: Boolean = true,
        val delayAlertDays: Int = 3,
        val status: String = "active",
        val updatedAt: String = "",
        val createdAt: String = "",
        val settings: Settings = Settings(),
    )

    data class Student(
        val id: String,
        val halaqaId: String,
        val categoryId: String? = null,
        /** RC15.1: canonical student track; tathbit students are isolated from general hifz/sard plans. */
        val track: String? = null,
    )

    data class Recitation(
        val id: String,
        val studentId: String,
        val activityType: String,
        val date: LocalDate,
        val startPage: Int? = null,
        val endPage: Int? = null,
        val startSurah: Int? = null,
        val startAyah: Int? = null,
        val endSurah: Int? = null,
        val endAyah: Int? = null,
        val pagesCount: Double = 0.0,
        val evaluation: String? = null,
        val mistakes: Int = 0,
        val prompts: Int = 0,
        val taggedPlanId: String? = null,
        val taggedPhaseId: String? = null,
        val qcfLineKeys: List<String> = emptyList(),
        val createdAt: String = "",
    )

    data class Event(
        val type: String,
        val pages: Double = 0.0,
        val milestoneKey: String? = null,
        val date: LocalDate? = null,
    )

    data class PageRange(val start: Int, val end: Int)

    data class Milestone(
        val key: String,
        val label: String,
        val startPage: Int,
        val endPage: Int,
    )

    data class Assignment(
        val kind: String,
        val pages: Double,
        val startPage: Int?,
        val endPage: Int?,
        val isCatchUp: Boolean,
        val blockedByRetry: Boolean,
        val retryRanges: List<PageRange> = emptyList(),
        val milestone: Milestone? = null,
        val lineKeys: List<String> = emptyList(),
    )

    data class WeeklyProgress(
        val weekStart: LocalDate,
        val weekEnd: LocalDate,
        val scheduledDays: Int,
        val elapsedScheduledDays: Int,
        val remainingScheduledDays: Int,
        val target: Double,
        val requiredByToday: Double,
        val completed: Double,
        val remaining: Double,
        val aheadBy: Double,
        val behindBy: Double,
    )

    data class Progress(
        val completed: Double,
        val completedToday: Double,
        val remaining: Double,
        val percentage: Double,
        val originalDaily: Double,
        val plannedToday: Double,
        val dailyTarget: Double,
        val recoveryShare: Double,
        val dailyCap: Double,
        val plannedByToday: Double,
        val deficit: Double,
        val aheadBy: Double,
        val behindBy: Double,
        val minimumDaily: Double,
        val dynamicDailyMinimum: Double,
        val consecutiveMissed: Int,
        val suggestedEndDate: LocalDate,
        val extensionNeeded: Boolean,
        val completedPages: List<Int>,
        val retryPages: List<Int>,
        val nextPage: Int?,
        val health: String,
        val healthLabel: String,
        val pace: String,
        val paceLabel: String,
        val weekly: WeeklyProgress,
        val assignment: Assignment,
    )

    private data class Completion(
        val amount: Double,
        val completedPages: List<Int>,
        val retryPages: List<Int>,
        val completedLineKeys: List<String>,
        val retryLineKeys: List<String>,
        val nextPage: Int?,
        val dailyAmounts: Map<LocalDate, Double>,
        val linePlan: Boolean,
    )

    fun round(value: Double, digits: Int = 2): Double {
        val factor = Math.pow(10.0, digits.toDouble())
        return floor((value + Double.MIN_VALUE) * factor + 0.5) / factor
    }

    fun weekday(date: LocalDate): Int = date.dayOfWeek.value % 7

    fun isPassing(recitation: Recitation, plan: Plan): Boolean {
        var evaluation = recitation.evaluation.orEmpty().lowercase()
        if (evaluation.isBlank() || evaluation == "not_rated") {
            val pages = recitation.pagesCount
            if (pages > 0.0) {
                // Legacy fallback used by v2.44 engine for old rows lacking evaluation.
                val effective = max(1.0, pages)
                val density = (recitation.mistakes + recitation.prompts * 0.25) / effective
                val safety = (100.0 - density * 15.0).coerceIn(0.0, 100.0)
                evaluation = when {
                    safety > 85.0 -> "excellent"
                    safety >= 75.0 -> "very_good"
                    safety >= 70.0 -> "good"
                    else -> "repeat"
                }
            }
        }
        if (evaluation in setOf("repeat", "weak", "إعادة", "ضعيف")) return false
        if (evaluation in setOf("good", "very_good", "excellent")) return true
        if (!plan.qualityGateEnabled) return true
        val threshold = evaluationRank[plan.minimumEvaluation] ?: evaluationRank.getValue("good")
        return (evaluationRank[evaluation] ?: -1) >= threshold
    }

    fun planDates(plan: Plan, from: LocalDate? = null, to: LocalDate? = null): List<LocalDate> {
        val start = maxOf(plan.startDate, from ?: plan.startDate)
        val end = minOf(plan.endDate, to ?: plan.endDate)
        if (start > end) return emptyList()
        return generateSequence(start) { it.plusDays(1) }
            .takeWhile { it <= end }
            .filter { isRegularPlanDay(plan, it) }
            .toList()
    }

    private fun pauseReason(plan: Plan, date: LocalDate): String = plan.settings.pauseRanges
        .firstOrNull { date >= it.from && date <= it.to }
        ?.reason
        .orEmpty()

    private fun intensiveSardDay(plan: Plan, date: LocalDate): Boolean {
        if (plan.planType != "sard") return false
        val from = plan.settings.intensiveWeekFrom ?: return false
        val to = plan.settings.intensiveWeekTo ?: return false
        return date in from..to && weekday(date) in plan.settings.intensiveWeekdays
    }

    private fun isRegularPlanDay(plan: Plan, date: LocalDate): Boolean {
        if (pauseReason(plan, date).isNotBlank()) return false
        val day = weekday(date)
        if (intensiveSardDay(plan, date)) return day != plan.catchUpWeekday
        return day in plan.scheduleWeekdays && day != plan.catchUpWeekday
    }

    private fun isCatchUpDay(plan: Plan, date: LocalDate): Boolean =
        plan.catchUpWeekday != null && weekday(date) == plan.catchUpWeekday

    private fun activityMatches(plan: Plan, recitation: Recitation): Boolean {
        val tagged = SmartPlanTagRules.normalizeId(recitation.taggedPlanId)
        if (tagged != null && tagged != plan.id && plan.id != "__review_source__") return false
        if (tagged == plan.id) return true
        return recitation.activityType.lowercase() in typeActivities[plan.planType].orEmpty()
    }

    private fun relevantRecords(plan: Plan, studentId: String, recitations: List<Recitation>): List<Recitation> =
        recitations
            .asSequence()
            .filter { it.studentId == studentId }
            .filter { activityMatches(plan, it) }
            .filter { it.date >= plan.startDate && it.date <= plan.endDate }
            .sortedWith(compareBy<Recitation> { it.date }.thenBy { it.createdAt })
            .toList()

    /**
     * Returns the pages covered by a legacy/full-page recitation in the order the
     * recitation was stored. This is deliberately not a Quran traversal resolver:
     * RC8 traversal meaning lives in QuranRangeEngine/QCF line keys. Here Smart Plan
     * only needs set coverage, while preserving the original start -> end order.
     */
    private fun recordPages(recitation: Recitation, plan: Plan): List<Int> {
        val start = recitation.startPage ?: recitation.endPage ?: return emptyList()
        val end = recitation.endPage ?: recitation.startPage ?: return emptyList()
        val pages = if (start <= end) (start..end).toList() else (start downTo end).toList()
        val planPages = planPageOrder(plan)
        if (planPages.isEmpty()) return pages.filter { it in 1..604 }
        val allowed = planPages.toHashSet()
        return pages.filter { it in 1..604 && it in allowed }
    }

    private fun planPageOrder(plan: Plan): List<Int> {
        val start = plan.rangeStartPage ?: return emptyList()
        val end = plan.rangeEndPage ?: return emptyList()
        return if (start <= end) (start..end).toList() else (start downTo end).toList()
    }

    private fun normalizeLineKey(value: String): String? {
        val match = Regex("^(\\d{1,3}):(\\d{1,2})$").matchEntire(value) ?: return null
        val page = match.groupValues[1].toInt()
        val line = match.groupValues[2].toInt()
        return if (page in 1..604 && line in 1..15) "$page:$line" else null
    }

    /**
     * RC56: the number of lines a line-based plan is paced over. A range that holds fewer lines than the plan's target
     * (for example a range clamped to a nearby terminal) must not shrink the quota: with 51 lines left and a 30-page
     * target the student was shown as 7 pages "ahead" when he was 7 pages behind.
     */
    private fun quotaLines(plan: Plan, lineKeys: List<String>): Int =
        max(lineKeys.size, ceil(plan.targetPages * 15.0).toInt())

    private fun planLineKeys(plan: Plan): List<String> = buildList {
        val seen = mutableSetOf<String>()
        plan.settings.memorizationLineKeys.forEach { raw ->
            normalizeLineKey(raw)?.let { if (seen.add(it)) add(it) }
        }
    }

    private fun recordLineKeys(recitation: Recitation): List<String> {
        val explicit = recitation.qcfLineKeys.mapNotNull(::normalizeLineKey).distinct()
        if (explicit.isNotEmpty()) return explicit
        val pages = recordPages(
            recitation,
            Plan(
                id = "line-fallback",
                halaqaId = "",
                targetPages = 604.0,
                startDate = LocalDate.MIN,
                endDate = LocalDate.MAX,
            ),
        )
        return buildList {
            pages.forEach { page -> for (line in 1..15) add("$page:$line") }
        }
    }

    private fun eventAdjustment(events: List<Event>): Double = events.sumOf { event ->
        when (event.type) {
            "manual_credit" -> event.pages
            "manual_debit" -> -event.pages
            else -> 0.0
        }
    }

    /**
     * RC27.7: counts Quran lines between two locations along [route] (QCF line model). Installed by the app
     * (it needs the Quran asset); null in pure tests, in which case the older counting is used unchanged.
     */
    @Volatile var spanLineCounter: ((com.mohammedsamir.halaqati.quran.QuranLocation, com.mohammedsamir.halaqati.quran.QuranLocation, String) -> Int?)? = null

    /**
     * RC27.7: a hifz plan's completed amount is the continuous stretch from the FIRST ayah recited in the plan
     * month to the LAST ayah reached (along the route), not the sum of per-recitation page estimates and not
     * only what falls inside the stored range. Daily amounts are the new lines each day pushed the frontier.
     * Returns null when it cannot be computed (no counter, no ayah-level recitations).
     */
    /**
     * RC27.10: same job as [spanLineCounter] but returns the line KEYS, so the span is built incrementally
     * (each stretch is counted once and unioned) instead of recounting from the first ayah for every recitation.
     */
    @Volatile var spanLineKeyProvider: ((com.mohammedsamir.halaqati.quran.QuranLocation, com.mohammedsamir.halaqati.quran.QuranLocation, String) -> List<String>?)? = null

    private fun spanCompletionIncremental(
        provider: (com.mohammedsamir.halaqati.quran.QuranLocation, com.mohammedsamir.halaqati.quran.QuranLocation, String) -> List<String>?,
        route: String,
        ayahLevel: List<Recitation>,
    ): Pair<Double, Map<LocalDate, Double>>? {
        fun loc(s: Int?, a: Int?) = com.mohammedsamir.halaqati.quran.QuranLocation(s!!, a!!)
        val base = ayahLevel.map { loc(it.startSurah, it.startAyah) }.minByOrNull { PlanRangeRebaseRules.position(it, route) } ?: return null
        val daily = linkedMapOf<LocalDate, Double>()
        val covered = HashSet<String>()
        var frontier: com.mohammedsamir.halaqati.quran.QuranLocation? = null
        for (rec in ayahLevel) {
            val end = loc(rec.endSurah, rec.endAyah)
            val current = frontier
            if (current != null && PlanRangeRebaseRules.position(end, route) <= PlanRangeRebaseRules.position(current, route)) continue
            val keys = provider(current ?: base, end, route) ?: return null
            val before = covered.size
            covered.addAll(keys)
            val delta = covered.size - before
            if (delta > 0) daily[rec.date] = (daily[rec.date] ?: 0.0) + delta / 15.0
            frontier = end
        }
        return (covered.size / 15.0) to daily
    }

    private fun spanCompletion(plan: Plan, relevant: List<Recitation>): Pair<Double, Map<LocalDate, Double>>? {
        // RC36: Hifz AND Sard are measured the same way: one continuous stretch from the FIRST ayah recited in the phase to the
        // LAST ayah reached, counted once however many days or times the same ayahs were recited.
        if (plan.planType != "hifz" && plan.planType != "sard") return null
        spanLineKeyProvider?.let { provider ->
            val route = plan.settings.routeDirection ?: "reverse_surahs_forward_ayahs"
            val ayahLevel = relevant.filter {
                isPassing(it, plan) && it.startSurah != null && it.startAyah != null && it.endSurah != null && it.endAyah != null
            }
            if (ayahLevel.isEmpty()) return null
            spanCompletionIncremental(provider, route, ayahLevel)?.let { return it }
        }
        val counter = spanLineCounter ?: return null
        val route = plan.settings.routeDirection ?: "reverse_surahs_forward_ayahs"
        val ayahLevel = relevant.filter {
            isPassing(it, plan) && it.startSurah != null && it.startAyah != null && it.endSurah != null && it.endAyah != null
        }
        if (ayahLevel.isEmpty()) return null
        fun loc(s: Int?, a: Int?) = com.mohammedsamir.halaqati.quran.QuranLocation(s!!, a!!)
        val base = ayahLevel.map { loc(it.startSurah, it.startAyah) }.minByOrNull { PlanRangeRebaseRules.position(it, route) } ?: return null
        val daily = linkedMapOf<LocalDate, Double>()
        var frontier: com.mohammedsamir.halaqati.quran.QuranLocation? = null
        var coveredLines = 0
        for (rec in ayahLevel) {
            val end = loc(rec.endSurah, rec.endAyah)
            val current = frontier
            if (current != null && PlanRangeRebaseRules.position(end, route) <= PlanRangeRebaseRules.position(current, route)) continue
            val total = counter(base, end, route) ?: return null
            val delta = total - coveredLines
            if (delta > 0) {
                daily[rec.date] = (daily[rec.date] ?: 0.0) + delta / 15.0
                coveredLines = total
            }
            frontier = end
        }
        return (coveredLines / 15.0) to daily
    }

    private fun buildCompletion(
        plan: Plan,
        studentId: String,
        recitations: List<Recitation>,
        events: List<Event>,
    ): Completion {
        val base = buildCompletionBase(plan, studentId, recitations, events)
        val span = spanCompletion(plan, relevantRecords(plan, studentId, recitations)) ?: return base
        return base.copy(
            amount = (span.first + eventAdjustment(events)).coerceAtLeast(0.0),
            dailyAmounts = span.second,
        )
    }

    private fun buildCompletionBase(
        plan: Plan,
        studentId: String,
        recitations: List<Recitation>,
        events: List<Event>,
    ): Completion {
        val relevant = relevantRecords(plan, studentId, recitations)
        val orderedLineKeys = planLineKeys(plan)
        val linePlan = orderedLineKeys.isNotEmpty()
        // RC34: Sard is measured in recited pages (pages_count). Page-number sets are unreliable for a Sard that crosses surahs in
        // reverse surah order (the stored start/end page can be the same page for 18 pages of recitation).
        val exactPagePlan = !linePlan && plan.planType != "sard" && plan.rangeStartPage != null && plan.rangeEndPage != null
        val dailyAmounts = linkedMapOf<LocalDate, Double>()

        if (linePlan) {
            data class LineState(val passing: Boolean, val date: LocalDate)
            val state = linkedMapOf<String, LineState>()
            val allowed = orderedLineKeys.toSet()
            relevant.forEach { rec ->
                val passing = isPassing(rec, plan)
                recordLineKeys(rec).forEach { key -> if (key in allowed) state[key] = LineState(passing, rec.date) }
            }
            state.forEach { (_, item) ->
                if (item.passing) dailyAmounts[item.date] = (dailyAmounts[item.date] ?: 0.0) + 1.0 / 15.0
            }
            val completedLineKeys = orderedLineKeys.filter { state[it]?.passing == true }
            val retryLineKeys = orderedLineKeys.filter { state.containsKey(it) && state[it]?.passing == false }
            val completedPages = completedLineKeys.map { it.substringBefore(':').toInt() }.distinct().sorted()
            val retryPages = retryLineKeys.map { it.substringBefore(':').toInt() }.distinct().sorted()
            val nextKey = orderedLineKeys.firstOrNull { state[it]?.passing != true }
            val rawAmount = completedLineKeys.size / 15.0 + eventAdjustment(events)
            return Completion(
                amount = rawAmount.coerceAtLeast(0.0),
                completedPages = completedPages,
                retryPages = retryPages,
                completedLineKeys = completedLineKeys,
                retryLineKeys = retryLineKeys,
                nextPage = nextKey?.substringBefore(':')?.toIntOrNull(),
                dailyAmounts = dailyAmounts,
                linePlan = true,
            )
        }

        if (exactPagePlan) {
            data class PageState(val passing: Boolean, val date: LocalDate)
            val state = linkedMapOf<Int, PageState>()
            relevant.forEach { rec ->
                val coveredPages = recordPages(rec, plan)
                if (coveredPages.isEmpty()) return@forEach
                val passing = isPassing(rec, plan)
                coveredPages.forEach { page -> state[page] = PageState(passing, rec.date) }
            }
            val completedPages = state.filterValues { it.passing }.keys.sorted()
            val retryPages = state.filterValues { !it.passing }.keys.sorted()
            state.forEach { (_, item) ->
                if (item.passing) dailyAmounts[item.date] = (dailyAmounts[item.date] ?: 0.0) + 1.0
            }
            val completedSet = completedPages.toSet()
            val nextPage = planPageOrder(plan).firstOrNull { it !in completedSet }
            val rawAmount = completedPages.size.toDouble() + eventAdjustment(events)
            return Completion(
                amount = rawAmount.coerceAtLeast(0.0),
                completedPages = completedPages,
                retryPages = retryPages,
                completedLineKeys = emptyList(),
                retryLineKeys = emptyList(),
                nextPage = nextPage,
                dailyAmounts = dailyAmounts,
                linePlan = false,
            )
        }

        var amount = 0.0
        val retries = mutableListOf<Int>()
        relevant.forEach { rec ->
            if (!isPassing(rec, plan)) {
                retries += recordPages(rec, plan)
                return@forEach
            }
            val coveredPages = recordPages(rec, plan)
            val pages = max(0.0, rec.pagesCount.takeIf { it > 0 } ?: coveredPages.size.toDouble())
            amount += pages
            dailyAmounts[rec.date] = (dailyAmounts[rec.date] ?: 0.0) + pages
        }
        amount = (amount + eventAdjustment(events)).coerceAtLeast(0.0)
        return Completion(
            amount = amount,
            completedPages = emptyList(),
            retryPages = retries.distinct().sorted(),
            completedLineKeys = emptyList(),
            retryLineKeys = emptyList(),
            nextPage = plan.rangeStartPage,
            dailyAmounts = dailyAmounts,
            linePlan = false,
        )
    }

    fun exactDailyQuota(targetPages: Double, totalDays: Int, dayIndex: Int): Int {
        val target = max(0, round(targetPages).toInt())
        val days = max(1, totalDays)
        val index = dayIndex.coerceIn(0, days - 1)
        val before = round(index * target.toDouble() / days).toInt()
        val through = round((index + 1) * target.toDouble() / days).toInt()
        return max(0, through - before)
    }

    fun exactCumulativeQuota(targetPages: Double, totalDays: Int, elapsedDays: Int): Int {
        val target = max(0, round(targetPages).toInt())
        val days = max(1, totalDays)
        val elapsed = elapsedDays.coerceIn(0, days)
        return round(elapsed * target.toDouble() / days).toInt().coerceIn(0, target)
    }

    private fun lineQuota(targetLines: Int, totalDays: Int, dayIndex: Int): Double =
        exactDailyQuota(targetLines.toDouble(), totalDays, dayIndex) / 15.0

    private fun cumulativeLineQuota(targetLines: Int, totalDays: Int, elapsedDays: Int): Double =
        exactCumulativeQuota(targetLines.toDouble(), totalDays, elapsedDays) / 15.0

    /**
     * RC9.1 single source of truth for how much of a plan should be due after a
     * given number of scheduled days. Daily/weekly/core-state calculations all
     * use this function so their numbers cannot drift apart.
     */
    private fun cumulativePlannedAmount(
        plan: Plan,
        lineKeys: List<String>,
        linePlan: Boolean,
        exactPagePlan: Boolean,
        totalDays: Int,
        elapsedDays: Int,
        originalDaily: Double,
        customMinimum: Double,
    ): Double {
        val base = min(
            plan.targetPages,
            when {
                linePlan -> cumulativeLineQuota(quotaLines(plan, lineKeys), totalDays, elapsedDays)
                exactPagePlan -> exactCumulativeQuota(plan.targetPages, totalDays, elapsedDays).toDouble()
                else -> elapsedDays * originalDaily
            },
        )
        return if (customMinimum > 0.0) {
            max(base, min(plan.targetPages, elapsedDays * customMinimum))
        } else base
    }

    private fun weeklyProgress(
        plan: Plan,
        dates: List<LocalDate>,
        lineKeys: List<String>,
        linePlan: Boolean,
        exactPagePlan: Boolean,
        totalDays: Int,
        originalDaily: Double,
        customMinimum: Double,
        completion: Completion,
        today: LocalDate,
    ): WeeklyProgress {
        val weekStart = today.minusDays(weekday(today).toLong())
        val weekEnd = weekStart.plusDays(6)
        val beforeWeekCount = dates.count { it < weekStart }
        val throughWeekCount = dates.count { it <= weekEnd }
        val throughTodayCount = dates.count { it <= minOf(today, weekEnd) }
        val scheduledDays = (throughWeekCount - beforeWeekCount).coerceAtLeast(0)
        val elapsedScheduledDays = (throughTodayCount - beforeWeekCount).coerceIn(0, scheduledDays)
        val remainingScheduledDays = dates.count { it > today && it <= weekEnd }

        val beforeWeek = cumulativePlannedAmount(
            plan, lineKeys, linePlan, exactPagePlan, totalDays, beforeWeekCount, originalDaily, customMinimum,
        )
        val throughWeek = cumulativePlannedAmount(
            plan, lineKeys, linePlan, exactPagePlan, totalDays, throughWeekCount, originalDaily, customMinimum,
        )
        val throughToday = cumulativePlannedAmount(
            plan, lineKeys, linePlan, exactPagePlan, totalDays, throughTodayCount, originalDaily, customMinimum,
        )
        val target = max(0.0, throughWeek - beforeWeek)
        val required = max(0.0, throughToday - beforeWeek)
        val completed = completion.dailyAmounts
            .filterKeys { it >= weekStart && it <= minOf(today, weekEnd) }
            .values
            .sum()

        return WeeklyProgress(
            weekStart = weekStart,
            weekEnd = weekEnd,
            scheduledDays = scheduledDays,
            elapsedScheduledDays = elapsedScheduledDays,
            remainingScheduledDays = remainingScheduledDays,
            target = round(target),
            requiredByToday = round(required),
            completed = round(completed),
            remaining = round(max(0.0, target - completed)),
            aheadBy = round(max(0.0, completed - required)),
            behindBy = round(max(0.0, required - completed)),
        )
    }

    private fun extendEndDate(plan: Plan, pagesNeeded: Double, dailyCapacity: Double): LocalDate {
        // RC57: plan end dates are never extended automatically.
        if (true) return plan.endDate
        if (!plan.autoExtend || pagesNeeded <= 0.0 || dailyCapacity <= 0.0) return plan.endDate
        var remaining = pagesNeeded
        var cursor = plan.endDate
        var guard = 0
        while (remaining > 0.0 && guard < 730) {
            cursor = cursor.plusDays(1)
            if (isRegularPlanDay(plan, cursor)) remaining -= dailyCapacity
            guard += 1
        }
        return cursor
    }

    private fun calculateConsecutiveMissed(
        plan: Plan,
        dailyAmounts: Map<LocalDate, Double>,
        currentDate: LocalDate,
        baseDaily: Double,
    ): Int {
        val dates = planDates(plan)
        val totalDays = max(1, dates.size)
        val prior = dates.filter { it < currentDate }.asReversed()
        val lineKeys = planLineKeys(plan)
        val exactLines = lineKeys.isNotEmpty()
        val exactPages = !exactLines && plan.rangeStartPage != null && plan.rangeEndPage != null
        var missed = 0
        for (date in prior) {
            val index = dates.indexOf(date)
            val expected = when {
                exactLines -> lineQuota(quotaLines(plan, lineKeys), totalDays, index)
                exactPages -> exactDailyQuota(plan.targetPages, totalDays, index).toDouble()
                else -> baseDaily
            }
            val custom = if (plan.planType == "hifz" && plan.settings.minimumDailyMode == "custom") {
                max(0.0, plan.settings.minimumDailyPages)
            } else 0.0
            val floor = max(expected, custom)
            if (floor <= 0.0) continue
            val amount = dailyAmounts[date] ?: 0.0
            if (amount + 0.001 < floor) missed += 1 else break
        }
        return missed
    }

    private data class Status(val health: String, val healthLabel: String, val pace: String, val paceLabel: String)

    private fun statusFromRatio(ratio: Double): Status {
        val band = SmartPlanHealthRules.fromRatio(ratio)
        return when (band.code) {
            SmartPlanHealthRules.BLUE -> Status(band.code, band.label, "ahead", "متقدم")
            SmartPlanHealthRules.YELLOW, SmartPlanHealthRules.RED -> Status(band.code, band.label, "behind", "متأخر")
            else -> Status(band.code, band.label, "on_track", "ملتزم")
        }
    }

    fun compressPages(pages: List<Int>): List<PageRange> {
        val sorted = pages.filter { it in 1..604 }.distinct().sorted()
        val output = mutableListOf<PageRange>()
        for (page in sorted) {
            val last = output.lastOrNull()
            if (last != null && page == last.end + 1) {
                output[output.lastIndex] = last.copy(end = page)
            } else {
                output += PageRange(page, page)
            }
        }
        return output
    }

    private fun milestoneRange(mode: String, id: Int): Milestone {
        return if (mode == "juz") {
            require(id in 1..QuranMetadata.JUZ_COUNT)
            val first = QuranMetadata.juzStart(id)
            val last = QuranMetadata.juzEnd(id)
            Milestone(
                key = "juz:$id",
                label = "اختبار الجزء $id",
                startPage = QuranMetadata.pageFor(first.surah, first.ayah),
                endPage = QuranMetadata.pageFor(last.surah, last.ayah),
            )
        } else {
            require(id in 1..60)
            val firstRub = ((id - 1) * 4) + 1
            val lastRub = id * 4
            val first = QuranMetadata.rubStart(firstRub)
            val last = QuranMetadata.rubEnd(lastRub)
            Milestone(
                key = "hizb:$id",
                label = "اختبار الحزب $id",
                startPage = QuranMetadata.pageFor(first.surah, first.ayah),
                endPage = QuranMetadata.pageFor(last.surah, last.ayah),
            )
        }
    }

    private fun pendingMilestone(plan: Plan, completion: Completion, events: List<Event>): Milestone? {
        if (!plan.milestoneRequiresApproval || plan.milestoneMode == "none" || plan.rangeStartPage == null) return null
        if (plan.milestoneMode !in setOf("juz", "hizb")) return null
        val approved = events.filter { it.type == "milestone_approved" }.mapNotNull { it.milestoneKey }.toSet()
        val planEnd = plan.rangeEndPage ?: return null
        val orderedLines = planLineKeys(plan)
        val completedLines = completion.completedLineKeys.toSet()
        val maximum = if (plan.milestoneMode == "juz") QuranMetadata.JUZ_COUNT else 60

        for (id in 1..maximum) {
            val milestone = milestoneRange(plan.milestoneMode, id)
            if (completion.linePlan) {
                // v2.44 hifz line plans may run from the last surahs toward Al-Baqarah.
                // The lower page boundary must be reached and every planned line inside
                // the milestone range must pass before opening the milestone test.
                val required = orderedLines.filter { key ->
                    val page = key.substringBefore(':').toIntOrNull() ?: return@filter false
                    page in milestone.startPage..milestone.endPage
                }
                val reachesReverseBoundary = required.any { key ->
                    key.substringBefore(':').toIntOrNull() == milestone.startPage
                }
                val boundaryCompleted = reachesReverseBoundary && required.isNotEmpty() && required.all(completedLines::contains)
                if (boundaryCompleted && milestone.key !in approved) {
                    return milestone.copy(
                        startPage = max(milestone.startPage, plan.rangeStartPage),
                        endPage = min(milestone.endPage, planEnd),
                    )
                }
                continue
            }

            if (milestone.endPage < plan.rangeStartPage || milestone.endPage > planEnd) continue
            val boundaryCompleted = milestone.endPage in completion.completedPages
            if (boundaryCompleted && milestone.key !in approved) {
                return milestone.copy(
                    startPage = max(milestone.startPage, plan.rangeStartPage),
                    endPage = min(milestone.endPage, planEnd),
                )
            }
        }
        return null
    }

    fun calculateProgress(
        plan: Plan,
        studentId: String,
        recitations: List<Recitation> = emptyList(),
        events: List<Event> = emptyList(),
        today: LocalDate = LocalDate.now(),
    ): Progress {
        val dates = planDates(plan)
        val totalDays = max(1, dates.size)
        val lineKeys = planLineKeys(plan)
        val linePlan = lineKeys.isNotEmpty()
        // RC34: Sard is measured in recited pages (pages_count). Page-number sets are unreliable for a Sard that crosses surahs in
        // reverse surah order (the stored start/end page can be the same page for 18 pages of recitation).
        val exactPagePlan = !linePlan && plan.planType != "sard" && plan.rangeStartPage != null && plan.rangeEndPage != null
        val originalDaily = plan.targetPages / totalDays
        val scopedEvents = events.filter { event -> event.date == null || (event.date >= plan.startDate && event.date <= plan.endDate) }
        val completion = buildCompletion(plan, studentId, recitations, scopedEvents)

        val elapsedDates = dates.filter { it <= today }
        val priorDates = dates.filter { it < today }
        val completedBeforeToday = completion.dailyAmounts.filterKeys { it < today }.values.sum()

        val dynamicDailyMinimum = if (plan.planType == "hifz") {
            round(if (linePlan) (quotaLines(plan, lineKeys) / 15.0) / totalDays else originalDaily)
        } else 0.0
        val customMinimum = if (plan.planType == "hifz" && plan.settings.minimumDailyMode == "custom") {
            max(0.0, plan.settings.minimumDailyPages)
        } else 0.0
        val minimumDaily = customMinimum.takeIf { it > 0.0 } ?: dynamicDailyMinimum
        val plannedBeforeToday = cumulativePlannedAmount(
            plan, lineKeys, linePlan, exactPagePlan, totalDays, priorDates.size, originalDaily, customMinimum,
        )
        val expectedByNow = cumulativePlannedAmount(
            plan, lineKeys, linePlan, exactPagePlan, totalDays, elapsedDates.size, originalDaily, customMinimum,
        )

        val deficit = max(0.0, plannedBeforeToday - completedBeforeToday)
        val remaining = max(0.0, plan.targetPages - completion.amount)
        val completedToday = round(completion.dailyAmounts[today] ?: 0.0)
        val futureRegularDates = dates.filter { it >= today }

        var dailyCap = when {
            linePlan -> max(1.0, ceil((quotaLines(plan, lineKeys).toDouble() / totalDays) * plan.maxDailyMultiplier)) / 15.0
            exactPagePlan -> max(1.0, ceil(originalDaily * plan.maxDailyMultiplier))
            else -> originalDaily * plan.maxDailyMultiplier
        }
        if (customMinimum > 0.0) dailyCap = max(dailyCap, customMinimum)

        var dueToday = 0.0
        var plannedToday = 0.0
        var recoveryShare = 0.0
        if (today >= plan.startDate && today <= plan.endDate) {
            if (isRegularPlanDay(plan, today)) {
                val index = dates.indexOf(today)
                plannedToday = when {
                    linePlan && index >= 0 -> lineQuota(quotaLines(plan, lineKeys), totalDays, index)
                    exactPagePlan && index >= 0 -> exactDailyQuota(plan.targetPages, totalDays, index).toDouble()
                    else -> originalDaily
                }
                if (customMinimum > 0.0) plannedToday = max(plannedToday, min(customMinimum, remaining + completedToday))

                if (linePlan) {
                    val remainingDays = max(1, futureRegularDates.size)
                    val recoveryNeed = ceil(deficit * 15.0 / remainingDays) / 15.0
                    val recoveryRoom = max(0.0, dailyCap - plannedToday)
                    recoveryShare = min(recoveryRoom, recoveryNeed)
                    dueToday = plannedToday + recoveryShare
                } else if (exactPagePlan) {
                    val remainingDays = max(1, futureRegularDates.size)
                    val recoveryNeed = ceil(deficit / remainingDays)
                    val recoveryRoom = max(0.0, dailyCap - plannedToday)
                    recoveryShare = min(recoveryRoom, recoveryNeed)
                    dueToday = plannedToday + recoveryShare
                } else {
                    recoveryShare = deficit / max(1, futureRegularDates.size)
                    dueToday = plannedToday + recoveryShare
                }
            } else if (isCatchUpDay(plan, today)) {
                recoveryShare = min(deficit, dailyCap)
                dueToday = recoveryShare
            }
        }

        val dailyTarget = when {
            linePlan -> max(0.0, minOf(remaining + completedToday, dailyCap, ceil(dueToday * 15.0) / 15.0))
            exactPagePlan -> max(0.0, minOf(remaining + completedToday, dailyCap, round(dueToday)))
            else -> round(minOf(remaining + completedToday, dailyCap, dueToday))
        }
        dueToday = when {
            linePlan -> round(max(0.0, min(remaining, ceil((dailyTarget - completedToday) * 15.0) / 15.0)))
            exactPagePlan -> max(0.0, min(remaining, round(dailyTarget - completedToday)))
            else -> round(max(0.0, min(remaining, dailyTarget - completedToday)))
        }

        val ratio = if (expectedByNow > 0.0) completion.amount / expectedByNow else if (completion.amount > 0.0) 1.1 else 1.0
        val consecutiveMissed = calculateConsecutiveMissed(plan, completion.dailyAmounts, today, originalDaily)
        val status = statusFromRatio(ratio)
        val remainingCapacity = futureRegularDates.size * dailyCap
        val uncoveredPages = max(0.0, remaining - remainingCapacity)
        val suggestedEndDate = extendEndDate(plan, uncoveredPages, dailyCap)
        // RC23: milestone exams are independent/manual sessions. They never block or replace
        // the daily smart-plan assignment. Legacy milestone settings remain readable only for
        // historical compatibility; the plan engine deliberately ignores them.
        val milestone: Milestone? = null
        val blockedByRetry = plan.planType == "hifz" && (completion.retryLineKeys.isNotEmpty() || completion.retryPages.isNotEmpty())
        val percentage = if (plan.targetPages > 0.0) round(completion.amount / plan.targetPages * 100.0, 1) else 0.0

        val dueLineKeys = if (linePlan && !blockedByRetry && milestone == null && dueToday > 0.0) {
            val completedSet = completion.completedLineKeys.toSet()
            lineKeys.filter { it !in completedSet }.take(ceil(dueToday * 15.0).toInt().coerceAtLeast(0))
        } else emptyList()

        var assignment = Assignment(
            kind = plan.planType,
            pages = dueToday,
            startPage = completion.nextPage,
            endPage = if (!linePlan && completion.nextPage != null && dueToday > 0.0) {
                val ordered = planPageOrder(plan)
                val count = max(1, ceil(dueToday).toInt())
                val fromIndex = ordered.indexOf(completion.nextPage)
                if (fromIndex >= 0) ordered.drop(fromIndex).take(count).lastOrNull() ?: completion.nextPage else completion.nextPage
            } else completion.nextPage,
            isCatchUp = isCatchUpDay(plan, today),
            blockedByRetry = blockedByRetry,
            lineKeys = dueLineKeys,
        )

        if (blockedByRetry) {
            val retryRanges = compressPages(completion.retryPages)
            assignment = assignment.copy(
                kind = "tathbit",
                pages = if (linePlan) round(completion.retryLineKeys.size / 15.0) else completion.retryPages.size.toDouble(),
                startPage = retryRanges.firstOrNull()?.start ?: completion.nextPage,
                endPage = retryRanges.firstOrNull()?.end ?: completion.nextPage,
                retryRanges = retryRanges,
                lineKeys = completion.retryLineKeys,
            )
        }

        val weekly = weeklyProgress(
            plan = plan,
            dates = dates,
            lineKeys = lineKeys,
            linePlan = linePlan,
            exactPagePlan = exactPagePlan,
            totalDays = totalDays,
            originalDaily = originalDaily,
            customMinimum = customMinimum,
            completion = completion,
            today = today,
        )

        return Progress(
            completed = round(completion.amount),
            completedToday = completedToday,
            remaining = round(remaining),
            percentage = percentage,
            originalDaily = round(originalDaily),
            plannedToday = round(plannedToday),
            dailyTarget = round(dailyTarget),
            recoveryShare = round(recoveryShare),
            dailyCap = round(dailyCap),
            plannedByToday = round(expectedByNow),
            deficit = round(deficit),
            aheadBy = round(max(0.0, completion.amount - expectedByNow)),
            behindBy = round(max(0.0, expectedByNow - completion.amount)),
            minimumDaily = round(minimumDaily),
            dynamicDailyMinimum = round(dynamicDailyMinimum),
            consecutiveMissed = consecutiveMissed,
            suggestedEndDate = suggestedEndDate,
            extensionNeeded = suggestedEndDate != plan.endDate,
            completedPages = completion.completedPages,
            retryPages = completion.retryPages,
            nextPage = completion.nextPage,
            health = status.health,
            healthLabel = status.healthLabel,
            pace = status.pace,
            paceLabel = status.paceLabel,
            weekly = weekly,
            assignment = assignment,
        )
    }

    fun effectivePlans(plans: List<Plan>, student: Student, date: LocalDate): List<Plan> {
        val candidates = plans.filter { plan ->
            val scopeMatches = when (plan.scopeType) {
                "halaqa" -> true
                "category" -> plan.categoryId != null && plan.categoryId == student.categoryId
                "student" -> plan.studentId == student.id
                else -> false
            }
            // RC15.1: a tathbit student only ever sees tathbit plans; a general-hifz student never does.
            // This stops halaqa/category hifz+sard phase plans from absorbing tathbit students.
            val trackMatches = if (com.mohammedsamir.halaqati.data.TrackActivityPolicy.isTathbit(student.track))
                plan.planType == "tathbit" else plan.planType != "tathbit"
            trackMatches && plan.status == "active" &&
                plan.halaqaId == student.halaqaId &&
                date >= plan.startDate && date <= plan.endDate && scopeMatches
        }
        val priority = mapOf("halaqa" to 1, "category" to 2, "student" to 3)
        return listOf("hifz", "review", "sard", "tathbit").mapNotNull { type ->
            candidates.filter { it.planType == type }.maxWithOrNull(
                compareBy<Plan> { priority[it.scopeType] ?: 0 }
                    .thenBy { it.updatedAt.ifBlank { it.createdAt } },
            )
        }
    }

    data class ReviewSuggestions(
        val nearPages: List<Int>,
        val nearRanges: List<PageRange>,
        val farPages: List<Int>,
        val farRanges: List<PageRange>,
    )

    fun reviewSuggestions(
        studentId: String,
        recitations: List<Recitation>,
        nearPages: Int = 7,
        farPages: Int = 20,
        today: LocalDate = LocalDate.now(),
    ): ReviewSuggestions {
        val pageDates = linkedMapOf<Int, LocalDate>()
        val synthetic = Plan(
            id = "__review_source__",
            halaqaId = "*",
            planType = "hifz",
            targetPages = 604.0,
            startDate = LocalDate.of(1900, 1, 1),
            endDate = LocalDate.of(2999, 12, 31),
        )
        recitations
            .filter { it.studentId == studentId && it.activityType in setOf("new_hifz", "hifz") }
            .sortedWith(compareBy<Recitation> { it.date }.thenBy { it.createdAt })
            .forEach { rec ->
                if (!isPassing(rec, synthetic)) return@forEach
                val coveredPages = recordPages(rec, synthetic)
                if (coveredPages.isEmpty()) return@forEach
                coveredPages.forEach { page -> pageDates[page] = rec.date }
            }

        val recent = pageDates.entries
            .sortedWith(compareByDescending<Map.Entry<Int, LocalDate>> { it.value }.thenByDescending { it.key })
            .take(ceil(nearPages.toDouble()).toInt())
            .map { it.key }
        val recentSet = recent.toSet()
        val old = pageDates.keys.filter { it !in recentSet }.sorted()
        val epochWeeks = ChronoUnit.WEEKS.between(LocalDate.of(1970, 1, 1), today).toInt()
        val start = if (old.isNotEmpty()) Math.floorMod(epochWeeks, old.size) else 0
        val far = buildList {
            for (offset in 0 until min(ceil(farPages.toDouble()).toInt(), old.size)) {
                add(old[(start + offset) % old.size])
            }
        }
        return ReviewSuggestions(
            nearPages = recent.sorted(),
            nearRanges = compressPages(recent),
            farPages = far.sorted(),
            farRanges = compressPages(far),
        )
    }
}
