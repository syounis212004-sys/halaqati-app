import com.mohammedsamir.halaqati.plans.OrgAttentionRules
import com.mohammedsamir.halaqati.plans.OrgAttentionRules.PlanInput
import com.mohammedsamir.halaqati.plans.OrgAttentionRules.StudentInput

private fun check(ok: Boolean, msg: String) { if (!ok) { println("FAIL: $msg"); kotlin.system.exitProcess(1) } }

fun main() {
    val s = listOf(
        StudentInput("a", "أ", "active", "h1", "c1"),
        StudentInput("b", "ب", "active", "h1", "c2"),
        StudentInput("c", "ج", "active", "h2", null),
        StudentInput("d", "د", "withdrawn", "h1", null),
        StudentInput("e", "هـ", "active", "h1", null),
    )
    val plans = listOf(
        PlanInput("student", "a", null, "h1", "active", "2026-10-01", "2026-10-31"),
        PlanInput("category", null, "c2", "h1", "active", "2026-10-01", "2026-10-31"),
        PlanInput("student", "c", null, "h2", "completed", "2026-10-01", "2026-10-31"),   // completed does not cover
        PlanInput("student", "e", null, "h1", "active", "2026-09-01", "2026-09-30"),      // expired
    )
    val none = OrgAttentionRules.studentsWithoutPlan(s, plans, "2026-10-13").map { it.id }
    check(none == listOf("c", "e"), "without plan: $none")
    check(OrgAttentionRules.studentsWithoutPlan(s, plans, "2026-10-13", "h2").map { it.id } == listOf("c"), "halaqa filter")
    val halaqaPlan = plans + PlanInput("halaqa", null, null, "h2", "active", "2026-10-01", "2026-10-31")
    check(OrgAttentionRules.studentsWithoutPlan(s, halaqaPlan, "2026-10-13").map { it.id } == listOf("e"), "halaqa-scope plan covers")
    val ng = OrgAttentionRules.studentsWithoutGuardian(s, setOf("a", "c")).map { it.id }
    check(ng == listOf("b", "e"), "without guardian: $ng (withdrawn excluded)")
    println("OrgAttentionRulesSelfTest PASS")
}
