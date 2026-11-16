import com.mohammedsamir.halaqati.data.RuntimeStabilityRules
import com.mohammedsamir.halaqati.plans.SmartPlanUiRules

fun main() {
    check(RuntimeStabilityRules.canOpenFromCachedProfile(hasSession = true, hasCachedProfile = true))
    check(!RuntimeStabilityRules.canOpenFromCachedProfile(hasSession = true, hasCachedProfile = false))

    val first = RuntimeStabilityRules.milestoneEventKey("plan-1", "student-1", "juz:27")
    val second = RuntimeStabilityRules.milestoneEventKey("plan-1", "student-1", "juz:27")
    val other = RuntimeStabilityRules.milestoneEventKey("plan-1", "student-1", "juz:28")
    check(first == second)
    check(first != other)

    check(SmartPlanUiRules.performanceBand(115.0).label == "متقدم")
    check(SmartPlanUiRules.performanceBand(95.0).label == "على المسار")
    check(SmartPlanUiRules.performanceBand(80.0).label == "يحتاج انتباه")
    check(SmartPlanUiRules.performanceBand(69.9).label == "يحتاج متابعة")
    check(SmartPlanUiRules.schedulePerformancePercent(completed = 13.67, requiredByToday = 20.0) < 70.0)
    check(SmartPlanUiRules.schedulePerformancePercent(completed = 10.0, requiredByToday = 0.0) == 100.0)

    println("RC9.3 FIX3 runtime/UX rules self-tests passed")
}
