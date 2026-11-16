import com.mohammedsamir.halaqati.plans.SmartPlanCoreStateEngine
import com.mohammedsamir.halaqati.plans.SmartPlanUiRules
import com.mohammedsamir.halaqati.ui.sessions.SessionWorkflowRules

fun main() {
    val early = SmartPlanUiRules.visualFor(SmartPlanCoreStateEngine.StatusCode.COMPLETE_EARLY, 2.5)
    check(early.label == "أتم الخطة مبكرًا") { early }
    check(early.supportingText == "+2.5 ص إضافية") { early }

    val summary = SessionWorkflowRules.summary(listOf(
        SessionWorkflowRules.StudentState.UNMARKED,
        SessionWorkflowRules.StudentState.NOT_RECITED,
        SessionWorkflowRules.StudentState.RECITED,
        SessionWorkflowRules.StudentState.PRESENT,
        SessionWorkflowRules.StudentState.ABSENT,
    ))
    check(summary.unmarked == 1 && summary.notRecited == 1 && summary.recited == 1 && summary.present == 3 && summary.absent == 1) { summary }
    check(SessionWorkflowRules.matchesSearch("أحمد  عبد الكريم", "احمد عبد"))
    check(SessionWorkflowRules.matchesSearch("إياد محمد", "اياد"))
    println("RC9.3 pure rules self-tests passed")
}
