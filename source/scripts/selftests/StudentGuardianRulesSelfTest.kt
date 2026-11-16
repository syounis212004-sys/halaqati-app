import com.mohammedsamir.halaqati.data.StudentGuardianRules

fun main() {
    val rows = listOf(
        StudentGuardianRules.GuardianOption("g1", "أحمد أبو يونس", "ahmad@example.com", "0599000001"),
        StudentGuardianRules.GuardianOption("g2", "محمد سمير", "m@example.com", "0599000002"),
    )
    check(StudentGuardianRules.filter(rows, "احمد").single().id == "g1")
    check(StudentGuardianRules.filter(rows, "00002").single().id == "g2")
    check(StudentGuardianRules.filter(rows, "ahmad@").single().id == "g1")
    check(StudentGuardianRules.filter(rows, "").size == 2)
    println("StudentGuardianRulesSelfTest PASS")
}
