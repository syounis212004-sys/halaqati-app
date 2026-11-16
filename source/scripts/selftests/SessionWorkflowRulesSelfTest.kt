import com.mohammedsamir.halaqati.ui.sessions.SessionWorkflowRules
fun main(){
    check(SessionWorkflowRules.sessionTypeForAssignment("sard")=="sard")
    check(SessionWorkflowRules.sessionTypeForAssignment("tathbit")=="review")
    check(SessionWorkflowRules.sessionTypeForAssignment("review")=="review")
    check(SessionWorkflowRules.sessionTypeForAssignment("test")=="test")
    check(SessionWorkflowRules.sessionTypeForAssignment("hifz")=="tasmee")
    check(SessionWorkflowRules.manualSessionTypes==listOf("tasmee","sard","review","test","tajweed","lesson","other"))
    check(SessionWorkflowRules.studentState(null,null,false)==SessionWorkflowRules.StudentState.UNMARKED)
    check(SessionWorkflowRules.studentState("present",SessionWorkflowRules.NOT_RECITED_MARKER,false)==SessionWorkflowRules.StudentState.NOT_RECITED)
    check(SessionWorkflowRules.studentState("present",SessionWorkflowRules.NOT_RECITED_MARKER,true)==SessionWorkflowRules.StudentState.RECITED)
    check(SessionWorkflowRules.matchesFilter(SessionWorkflowRules.StudentState.NOT_RECITED,"not_recited"))
    check(SessionWorkflowRules.priority(SessionWorkflowRules.StudentState.UNMARKED)<SessionWorkflowRules.priority(SessionWorkflowRules.StudentState.RECITED))
    println("SessionWorkflowRules RC9.2 self-tests passed")
}
