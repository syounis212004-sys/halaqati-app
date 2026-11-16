import com.mohammedsamir.halaqati.plans.SmartPlanHealthRules

fun main() {
    check(SmartPlanHealthRules.fromProgress(12.0,10.0).code == "blue")
    check(SmartPlanHealthRules.fromProgress(9.5,10.0).code == "green")
    check(SmartPlanHealthRules.fromProgress(8.0,10.0).code == "yellow")
    check(SmartPlanHealthRules.fromProgress(7.4,10.0).code == "red")
    check(!SmartPlanHealthRules.shouldNotifyGuardian(1))
    check(SmartPlanHealthRules.shouldNotifyGuardian(2))
    println("SmartPlanHealthRulesSelfTest PASS")
}
