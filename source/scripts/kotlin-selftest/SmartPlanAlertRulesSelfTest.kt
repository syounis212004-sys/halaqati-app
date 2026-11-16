import com.mohammedsamir.halaqati.data.SmartPlanAlertRules
import com.mohammedsamir.halaqati.quran.QuranLocation

fun main() {
    check(!SmartPlanAlertRules.shouldSendDelay(2, 3))
    check(SmartPlanAlertRules.shouldSendDelay(3, 3))
    check(!SmartPlanAlertRules.shouldSendHomePrep(16, true, 10))
    check(SmartPlanAlertRules.shouldSendHomePrep(17, true, 10))
    check(SmartPlanAlertRules.preferredPlanType("sard") == "hifz")
    check(SmartPlanAlertRules.preferredPlanType("tathbit") == "tathbit")
    val label = SmartPlanAlertRules.assignmentLabel("hifz", 20, 22)
    check(label.startsWith("حفظ ") && label.contains("سورة") && label.contains("آية") && !label.contains("ص ")) { label }
    check(!SmartPlanAlertRules.assignmentLabel("test", 41, 41).contains("اختبار"))
    check(SmartPlanAlertRules.assignmentLabel("hifz", 42, 48, QuranLocation(2, 255), QuranLocation(2, 282)) == "حفظ سورة البقرة من الآية 255 إلى الآية 282")
    println("SmartPlanAlertRules self-test passed")
}
