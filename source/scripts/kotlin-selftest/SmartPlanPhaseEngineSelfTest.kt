import com.mohammedsamir.halaqati.plans.SmartPlanEngine
import com.mohammedsamir.halaqati.plans.SmartPlanPhaseEngine
import java.time.LocalDate

fun main() {
    fun phase(target: Double? = 60.0, distribution: String = SmartPlanPhaseEngine.DISTRIBUTION_FLEXIBLE) =
        SmartPlanPhaseEngine.Phase(
            id = "sard-phase", planId = "plan", order = 2, type = SmartPlanPhaseEngine.TYPE_SARD,
            startDate = LocalDate.parse("2026-09-25"), endDate = LocalDate.parse("2026-09-30"),
            scheduleWeekdays = (0..6).toSet(), goalMode = if (target == null) SmartPlanPhaseEngine.GOAL_OPEN else SmartPlanPhaseEngine.GOAL_PAGES,
            targetPages = target, targetUnits = null, goalUnit = "pages", distributionMode = distribution,
            startMode = SmartPlanPhaseEngine.START_LAST_SARD,
        )

    val flexible = SmartPlanPhaseEngine.progress(phase(), 18.0, LocalDate.parse("2026-09-26"))
    check(flexible.remainingPages == 42.0)
    check(flexible.suggestedTodayPages == 8.4) { "Flexible Sard target must rebalance: ${flexible.suggestedTodayPages}" }

    val early = SmartPlanPhaseEngine.progress(phase(), 67.0, LocalDate.parse("2026-09-27"))
    check(early.completedEarly && early.surplusPages == 7.0)

    val open = SmartPlanPhaseEngine.progress(phase(null), 22.0, LocalDate.parse("2026-09-27"))
    check(open.targetPages == null && open.suggestedTodayPages == null)

    val carried = SmartPlanPhaseEngine.progress(
        phase(target = 60.0), 18.0, LocalDate.parse("2026-09-26"),
        SmartPlanPhaseEngine.StudentState("sard-phase", "s", carriedShortfallPages = 10.0),
    )
    check(carried.targetPages == 70.0 && carried.remainingPages == 52.0)
    check(carried.suggestedTodayPages == 10.4) { "Carry must be part of flexible redistribution: ${carried.suggestedTodayPages}" }

    val manualStart = phase().copy(
        startMode = SmartPlanPhaseEngine.START_MANUAL,
        rangeStartSurah = 78,
        rangeStartAyah = 1,
    )
    check(SmartPlanPhaseEngine.configuredStart(manualStart)?.surah == 78)
    check(!SmartPlanPhaseEngine.usesHistoricalSardAnchor(manualStart))
    check(SmartPlanPhaseEngine.usesHistoricalSardAnchor(phase()))
    val genericAnchor = phase().copy(type = SmartPlanPhaseEngine.TYPE_HIFZ, startMode = SmartPlanPhaseEngine.START_LAST_ACTIVITY)
    check(SmartPlanPhaseEngine.usesHistoricalActivityAnchor(genericAnchor))
    val tathbitAnchor = phase().copy(type = SmartPlanPhaseEngine.TYPE_TATHBIT, startMode = SmartPlanPhaseEngine.START_LAST_ACTIVITY)
    check(SmartPlanPhaseEngine.usesHistoricalActivityAnchor(tathbitAnchor))

    val recitations = listOf(
        SmartPlanEngine.Recitation("h", "s", "new_hifz", LocalDate.parse("2026-09-26"), endPage = 485, endSurah = 42, endAyah = 53, createdAt = "2"),
        SmartPlanEngine.Recitation("sard", "s", "sard", LocalDate.parse("2026-09-25"), endPage = 582, endSurah = 78, endAyah = 40, createdAt = "1"),
    )
    val anchor = SmartPlanPhaseEngine.latestSardAnchor(recitations, LocalDate.parse("2026-09-26"))
    check(anchor?.recitationId == "sard" && anchor.surah == 78 && anchor.ayah == 40)

    val hifz = phase().copy(id = "h", order = 1, type = SmartPlanPhaseEngine.TYPE_HIFZ, startDate = LocalDate.parse("2026-09-01"), endDate = LocalDate.parse("2026-09-24"))
    val sard = phase()
    val exception = SmartPlanPhaseEngine.StudentState("h", "student", SmartPlanPhaseEngine.STATE_ACTIVE, endDateOverride = LocalDate.parse("2026-09-28"))
    val active = SmartPlanPhaseEngine.effectiveActivePhase(listOf(hifz, sard), listOf(exception), "student", LocalDate.parse("2026-09-27"))
    check(active?.id == "h") { "Student exception must keep Hifz active" }

    check(SmartPlanPhaseEngine.previousShortfall(SmartPlanPhaseEngine.progress(phase(), 44.0, LocalDate.parse("2026-09-30"))) == 16.0)

    // RC11.2: generic tests do not move Sard anchor.
    val withGenericTest = recitations + SmartPlanEngine.Recitation(
        "test", "s", "test", LocalDate.parse("2026-09-26"), endPage = 590, endSurah = 85, endAyah = 22, createdAt = "9"
    )
    check(SmartPlanPhaseEngine.latestSardAnchor(withGenericTest, LocalDate.parse("2026-09-26"))?.recitationId == "sard")

    // Gap days keep plan context visible but have no effective active phase.
    val gapHifz = hifz.copy(endDate = LocalDate.parse("2026-09-20"))
    val gapSard = sard.copy(startDate = LocalDate.parse("2026-09-25"))
    check(SmartPlanPhaseEngine.effectiveActivePhase(listOf(gapHifz, gapSard), emptyList(), "student", LocalDate.parse("2026-09-22")) == null)
    check(SmartPlanPhaseEngine.contextPhase(listOf(gapHifz, gapSard), emptyList(), "student", LocalDate.parse("2026-09-22"))?.id == "h")
    check(SmartPlanPhaseEngine.lifecycle(gapHifz, null, LocalDate.parse("2026-09-22")) == SmartPlanPhaseEngine.Lifecycle.ENDED)

    val gapIssues = SmartPlanPhaseEngine.validateTimeline(listOf(gapHifz, gapSard))
    check(gapIssues.any { it.code == "gap" && !it.blocking })
    val overlapIssues = SmartPlanPhaseEngine.validateTimeline(listOf(hifz.copy(endDate = LocalDate.parse("2026-09-26")), sard))
    check(overlapIssues.any { it.code == "overlap" && it.blocking })

    check(SmartPlanPhaseEngine.DEFAULT_SARD_TARGET_PAGES == 25.0)
    check(SmartPlanPhaseEngine.ROUTE_FROM_NAS_TO_BAQARAH == "reverse_surahs_forward_ayahs")
    println("SmartPlanPhaseEngineSelfTest PASS")
}
