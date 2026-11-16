import com.mohammedsamir.halaqati.ui.dashboard.DashboardRules

fun main() {
    check(DashboardRules.isAbsentStatus("absent"))
    check(DashboardRules.isAbsentStatus("excused"))
    check(!DashboardRules.isAbsentStatus("late"))
    check(DashboardRules.hiddenCount(41, 2) == 39)
    check(DashboardRules.hiddenCount(2, 5) == 0)
    check(DashboardRules.displayRange("القصص", 29, 20) == "القصص 20–29")
    check(DashboardRules.displayRange("الفتح", 16, 24) == "الفتح 16–24")
    println("DashboardRules self-tests passed")
}
