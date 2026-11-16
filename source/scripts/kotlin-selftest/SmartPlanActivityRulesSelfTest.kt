import com.mohammedsamir.halaqati.plans.SmartPlanActivityRules
import com.mohammedsamir.halaqati.plans.SmartPlanEngine
import com.mohammedsamir.halaqati.quran.QuranLocation
import java.time.LocalDate

fun main() {
    check(SmartPlanActivityRules.enabledActivities("hifz", emptyList()) == listOf("hifz"))
    check(SmartPlanActivityRules.enabledActivities("hifz", listOf("hifz", "sard", "hifz")) == listOf("hifz", "sard"))

    val rows = listOf(
        SmartPlanEngine.Recitation("h1", "s", "new_hifz", LocalDate.of(2026,9,20), endSurah=12, endAyah=30, createdAt="1"),
        SmartPlanEngine.Recitation("s1", "s", "sard", LocalDate.of(2026,9,21), endSurah=67, endAyah=30, createdAt="2"),
        SmartPlanEngine.Recitation("t1", "s", "test", LocalDate.of(2026,9,23), endSurah=78, endAyah=40, createdAt="9"),
        SmartPlanEngine.Recitation("h2", "s", "new_hifz", LocalDate.of(2026,9,22), endSurah=12, endAyah=52, createdAt="3"),
    )
    check(SmartPlanActivityRules.latestAnchor(rows, "hifz")?.id == "h2")
    check(SmartPlanActivityRules.latestAnchor(rows, "sard")?.id == "s1")
    check(SmartPlanActivityRules.routeFor("sard", "hifz", null, null) == "reverse_surahs_forward_ayahs")
    check(SmartPlanActivityRules.routeFor("sard", "tathbit", null, null) == "forward_surahs_forward_ayahs")
    check(SmartPlanActivityRules.routeFor("tathbit", "tathbit", null, null) == "forward_surahs_forward_ayahs")
    check(SmartPlanActivityRules.activityTypeFor("tathbit") == "tathbit")

    check(SmartPlanActivityRules.terminalSurah("hifz", "reverse_surahs_forward_ayahs") == 2)
    check(SmartPlanActivityRules.terminalSurah("sard", "reverse_surahs_forward_ayahs") == 2)
    check(SmartPlanActivityRules.terminalSurah("sard", "forward_surahs_forward_ayahs") == 114)
    check(SmartPlanActivityRules.terminalSurah("tathbit", "reverse_surahs_forward_ayahs") == 1)

    check(SmartPlanActivityRules.nextLocation(QuranLocation(12, 30), "reverse_surahs_forward_ayahs") == QuranLocation(12, 31))
    check(SmartPlanActivityRules.nextLocation(QuranLocation(12, 111), "reverse_surahs_forward_ayahs") == QuranLocation(11, 1))
    check(SmartPlanActivityRules.nextLocation(QuranLocation(6, 165), "reverse_surahs_forward_ayahs") == QuranLocation(5, 1))
    check(SmartPlanActivityRules.nextLocation(QuranLocation(41, 54), "reverse_surahs_forward_ayahs") == QuranLocation(40, 1))
    check(SmartPlanActivityRules.nextLocation(QuranLocation(12, 111), "forward_surahs_forward_ayahs") == QuranLocation(13, 1))
    // RC8.6 regressions from device QA: Al-Mu'minun ends at 118, so reverse-surah hifz
    // continues at Al-Hajj 1. The amount must come from the plan/student target, not one stale line.
    check(SmartPlanActivityRules.nextLocation(QuranLocation(23, 118), "reverse_surahs_forward_ayahs") == QuranLocation(22, 1))
    val ward = SmartPlanActivityRules.effectiveDailyPages(
        activity = "hifz",
        engineDaily = 1.0 / 15.0,
        originalDaily = 1.25,
        minimumDaily = 0.5,
        plannedDaily = 1.5,
        weeklyDaily = 1.4,
        assignmentPages = 1.0 / 15.0,
        assignmentKind = "hifz",
    )
    check(ward == 1.5) { "Daily hifz must not collapse to one ayah/line: $ward" }
    // RC44: the student's weekly target must not raise the daily hifz ward above the plan's own figures.
    val planOnly = SmartPlanActivityRules.effectiveDailyPages(
        activity = "hifz", engineDaily = 0.47, originalDaily = 0.375, minimumDaily = 0.4, plannedDaily = 0.375,
        weeklyDaily = 1.4, assignmentPages = 0.0, assignmentKind = "hifz",
    )
    check(planOnly == 0.47) { "weekly student target leaked into the daily hifz ward: $planOnly" }

    println("SmartPlanActivityRulesSelfTest PASS")
}
