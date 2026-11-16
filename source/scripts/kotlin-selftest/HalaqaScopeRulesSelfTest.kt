import com.mohammedsamir.halaqati.ui.scope.*

fun main() {
    // month parsing and labels
    check(MonthKey.parse("2026-10-12") == MonthKey(2026, 10))
    check(MonthKey.parse("2026-10-12T17:00:00Z") == MonthKey(2026, 10))
    check(MonthKey.parse("2026-13-01") == null)
    check(MonthKey.parse("") == null && MonthKey.parse(null) == null && MonthKey.parse("abc") == null)
    check(MonthKey(2026, 10).label() == "أكتوبر 2026")
    check(MonthKey(2026, 1).previous() == MonthKey(2025, 12))
    check(MonthKey(2026, 10).previous() == MonthKey(2026, 9))

    // grouping: newest first, keeps item order inside a month, undated last
    val dates = listOf("2026-09-30", "2026-10-12", "2026-10-11", "2026-08-01", "bad", "2026-09-01")
    val groups = MonthGrouping.group(dates) { it }
    check(groups.map { it.key } == listOf(MonthKey(2026, 10), MonthKey(2026, 9), MonthKey(2026, 8), MonthKey.UNDATED)) { groups.toString() }
    check(groups[0].items == listOf("2026-10-12", "2026-10-11"))
    check(groups[1].items == listOf("2026-09-30", "2026-09-01"))
    check(groups.last().items == listOf("bad"))
    check(MonthGrouping.group(emptyList<String>()) { it }.isEmpty())

    // default expansion
    val keys = groups.map { it.key }
    check(MonthGrouping.defaultExpanded(keys, MonthKey(2026, 10)) == setOf(MonthKey(2026, 10), MonthKey(2026, 9)))
    check(MonthGrouping.defaultExpanded(keys, MonthKey(2026, 11)) == setOf(MonthKey(2026, 10)))
    check(MonthGrouping.defaultExpanded(keys, MonthKey(2027, 5)) == setOf(MonthKey(2026, 10)))
    check(MonthGrouping.defaultExpanded(emptyList(), MonthKey(2026, 10)).isEmpty())
    // year boundary: January opens December of the previous year
    check(MonthGrouping.defaultExpanded(listOf(MonthKey(2026, 1), MonthKey(2025, 12), MonthKey(2025, 11)), MonthKey(2026, 1)) == setOf(MonthKey(2026, 1), MonthKey(2025, 12)))

    // scope resolution
    check(HalaqaScopeRules.resolve("teacher", listOf("a"), null) == HalaqaScope("a", false, false))
    check(HalaqaScopeRules.resolve("teacher", emptyList(), null) == HalaqaScope(null, false, false))
    check(HalaqaScopeRules.resolve("admin", listOf("a", "b", "c"), null) == HalaqaScope(null, true, true))
    check(HalaqaScopeRules.resolve("admin", listOf("a", "b", "c"), "b") == HalaqaScope("b", false, true))
    check(HalaqaScopeRules.resolve("admin", listOf("a", "b"), "zzz") == HalaqaScope(null, true, true))
    check(HalaqaScopeRules.resolve("supervisor", listOf("a", "b"), null).showOverview)
    check(HalaqaScopeRules.resolve("teacher", listOf("a", "b"), null) == HalaqaScope("a", false, true))
    check(HalaqaScopeRules.resolve("admin", listOf("a", "", "a"), null) == HalaqaScope("a", false, false))

    // overview
    val rows = HalaqaOverview.build(
        listOf("a", "b"),
        listOf(OverviewSession("a", "2026-10-12"), OverviewSession("a", "2026-10-01"), OverviewSession("b", "2026-10-05")),
        listOf(OverviewStudent("a", true), OverviewStudent("a", false), OverviewStudent("b", true), OverviewStudent(null, true)),
        "2026-10-12",
    )
    check(rows[0] == HalaqaOverviewRow("a", 1, true, "2026-10-12", 2)) { rows[0].toString() }
    check(rows[1] == HalaqaOverviewRow("b", 1, false, "2026-10-05", 1)) { rows[1].toString() }

    println("HalaqaScopeRulesSelfTest: OK")
}
