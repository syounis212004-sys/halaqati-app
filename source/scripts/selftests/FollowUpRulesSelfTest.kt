package com.mohammedsamir.halaqati.ui.dashboard

data class DashboardAttentionItem(val studentId: String, val studentName: String, val reason: String)

fun main() {
    val a = DashboardAttentionItem("a", "أحمد", "متأخر")
    val b = DashboardAttentionItem("b", "محمد", "جودة")
    val groups = FollowUpRules.group(
        delayed = listOf(a, b),
        quality = listOf(a),
        absence = emptyList(),
        notHeard = emptyList(),
    )
    check(groups.first().title == "متابعة عاجلة")
    check(groups.first().items.single().studentId == "a")
    check(groups.flatMap { it.items }.count { it.studentId == "a" } == 1)
    check(groups.flatMap { it.items }.count { it.studentId == "b" } == 1)
    println("FollowUpRulesSelfTest PASS")
}
