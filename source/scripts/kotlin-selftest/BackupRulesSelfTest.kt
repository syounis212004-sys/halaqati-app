import com.mohammedsamir.halaqati.data.BackupRules

private fun check(ok: Boolean, msg: String) { if (!ok) { println("FAIL: $msg"); kotlin.system.exitProcess(1) } }

fun main() {
    check(BackupRules.TABLES.toSet().size == BackupRules.TABLES.size, "no duplicate tables")
    check("students" in BackupRules.TABLES && "recitation_entries" in BackupRules.TABLES && "smart_plans" in BackupRules.TABLES, "core tables")
    check(BackupRules.pageQuery("students", 2000) == "select=*&order=id.asc&offset=2000&limit=1000", BackupRules.pageQuery("students", 2000))
    check(BackupRules.pageQuery("student_guardians", 0).contains("order=student_id.asc,guardian_id.asc"), "composite pk order")
    check(BackupRules.orderFor("smart_plan_health_daily") == "plan_id.asc,student_id.asc,day", "health order")
    check(BackupRules.isLastPage(999) && !BackupRules.isLastPage(1000), "last page")
    check(BackupRules.fileName("2026-10-13") == "halaqati-backup-2026-10-13.zip", "file name")
    println("BackupRulesSelfTest PASS")
}
