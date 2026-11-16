import com.mohammedsamir.halaqati.plans.PlanHistoryRules
import com.mohammedsamir.halaqati.plans.PlanHistoryRules.ArchiveInput
import com.mohammedsamir.halaqati.plans.PlanHistoryRules.PlanInput

private fun check(ok: Boolean, msg: String) { if (!ok) { println("FAIL: $msg"); kotlin.system.exitProcess(1) } }

fun main() {
    val plans = listOf(
        PlanInput("p1", "s1", "h1", "2026-09-01", "حفظ", "completed"),
        PlanInput("p2", "s2", "h1", "2026-10-01", "حفظ", "active"),
        PlanInput("p3", "s3", "h2", "2026-09-01", "حفظ", "active"),
    )
    val archives = listOf(
        ArchiveInput("a1", "p2", "s2", "h1", "2026-09-01", "حفظ ١٥", 15.0, 7.0),
        ArchiveInput("a2", "p1", "s1", "h1", "2026-09-01", "حفظ", 5.0, 5.0),   // p1 still sits in September: no double count
        ArchiveInput("a3", "p9", "s4", "h1", "2026-09-01", "", 5.0, 5.0),
        ArchiveInput("a3", "p9", "s4", "h1", "2026-09-01", "", 5.0, 5.0),   // duplicate id
        ArchiveInput("a4", "p2", "s2", "h1", "2026-10-01", "حفظ", 5.0, 1.0), // current month is excluded
    )
    val all = PlanHistoryRules.merge(plans, archives, "2026-10", null)
    check(all.size == 4, "p1,p3 live + a1,a3 archived = 4, got ${all.size}")
    val h1 = PlanHistoryRules.merge(plans, archives, "2026-10", "h1")
    check(h1.size == 3, "halaqa filter, got ${h1.size}")
    check(h1.count { it.status == "منتهية" } == 1 && h1.count { it.status == "مكتملة" } == 2, "labels ${h1.map { it.status }}")
    check(all.none { it.month.startsWith("2026-10") }, "current month excluded")
    check(PlanHistoryRules.merge(emptyList(), emptyList(), "2026-10", null).isEmpty(), "empty")
    println("PlanHistoryRulesSelfTest PASS")
}
