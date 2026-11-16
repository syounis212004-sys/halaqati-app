import com.mohammedsamir.halaqati.plans.SmartPlanTagRules

fun main() {
    check(SmartPlanTagRules.normalizeId(null) == null)
    check(SmartPlanTagRules.normalizeId("") == null)
    check(SmartPlanTagRules.normalizeId(" null ") == null)
    check(SmartPlanTagRules.normalizeId("undefined") == null)
    check(SmartPlanTagRules.normalizeId("abc-123") == "abc-123")
    println("SmartPlanTagRulesSelfTest PASS")
}
