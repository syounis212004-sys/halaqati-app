import com.mohammedsamir.halaqati.data.ImportRules
import java.time.LocalDate

fun main() {
    check(ImportRules.integer("۲۵") == 25)
    check(ImportRules.integer("3.5") == null)
    check(ImportRules.date("٩/٩/٢٠٢٦") == LocalDate.of(2026, 9, 9))
    check(ImportRules.activityType("حفظ") == "new_hifz")
    check(ImportRules.activityType("سرد") == "sard")
    check(ImportRules.activityType("نوع غير معروف") == null)
    check(ImportRules.validRecitation("new_hifz", 1.0, 0, 0, 1, 1))
    check(!ImportRules.validRecitation("new_hifz", 1.0, 0, 0, 605, 605))
    check(!ImportRules.validMonthlyGoals(-1.0, 0.0, 0.0, 20))
    check(ImportRules.studentDisposition("أحمد", "الحفظ العام") == ImportRules.RowDisposition.VALID)
    check(ImportRules.studentDisposition("أحمد", "مسار ثالث") == ImportRules.RowDisposition.INVALID)
    check(ImportRules.recitationDisposition("أحمد", "2026-09-09", "حفظ", "1", "0", "0", "1", "1") == ImportRules.RowDisposition.VALID)
    check(ImportRules.recitationDisposition("أحمد", "2026-09-09", "نوع غريب", "1") == ImportRules.RowDisposition.INVALID)
    check(ImportRules.monthlyGoalDisposition("أحمد", "2026-09", "20", "30", "10", "20") == ImportRules.RowDisposition.VALID)
    check(ImportRules.monthlyGoalDisposition("أحمد", "2026-09", "0", "0", "0", "20") == ImportRules.RowDisposition.SKIP)
    println("ImportRules self-tests passed")
}
