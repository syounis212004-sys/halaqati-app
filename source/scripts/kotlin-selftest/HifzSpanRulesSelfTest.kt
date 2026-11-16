import com.mohammedsamir.halaqati.plans.HifzSpanRules
import com.mohammedsamir.halaqati.plans.PlanRangeRebaseRules
import com.mohammedsamir.halaqati.plans.ProgramKindRules
import com.mohammedsamir.halaqati.plans.SmartPlanEligibility

private fun check(ok: Boolean, msg: String) { if (!ok) { println("FAIL: $msg"); kotlin.system.exitProcess(1) } }

fun main() {
    val rev = "reverse_surahs_forward_ayahs"
    val counter: (com.mohammedsamir.halaqati.quran.QuranLocation, com.mohammedsamir.halaqati.quran.QuranLocation, String) -> Int? =
        { s, e, r -> (PlanRangeRebaseRules.position(e, r) - PlanRangeRebaseRules.position(s, r) + 1).toInt() }
    // Ayah-level entries: span from the first start to the last end, regardless of per-entry pages_count.
    val entries = listOf(
        HifzSpanRules.Entry(33, 51, 33, 73, 0.2),
        HifzSpanRules.Entry(32, 1, 31, 29, 0.3),
        HifzSpanRules.Entry(33, 60, 33, 65, 0.1), // inside old ground: adds nothing
    )
    val span = HifzSpanRules.pages(entries, rev, counter)
    check(span == 1979 / 15.0, "span $span")
    check(span > entries.sumOf { it.pagesCount }, "span differs from the pages_count sum")
    // No counter -> plain sum (pure tests / failure).
    check(HifzSpanRules.pages(entries, rev, null) == 0.6000000000000001 || kotlin.math.abs(HifzSpanRules.pages(entries, rev, null) - 0.6) < 1e-9, "no counter -> sum")
    check(HifzSpanRules.pages(entries, rev) { _, _, _ -> null }.let { kotlin.math.abs(it - 0.6) < 1e-9 }, "counter null -> sum")
    // Entries without ayahs are added by their own pages.
    val mixed = entries + HifzSpanRules.Entry(null, null, null, null, 2.0)
    check(HifzSpanRules.pages(mixed, rev, counter) == 1979 / 15.0 + 2.0, "loose pages added")
    check(HifzSpanRules.pages(emptyList(), rev, counter) == 0.0, "empty")

    // Eligibility: only regular halaqat get smart plans.
    val ids = SmartPlanEligibility.ineligibleHalaqaIds(listOf("a" to "regular", "b" to "training", "c" to null, "d" to "tilawah", "e" to "tajweed", "f" to "course"))
    check(ids == setOf("b", "d", "e", "f"), "ineligible $ids")
    check(SmartPlanEligibility.isEligible("a", ids) && SmartPlanEligibility.isEligible(null, ids) && !SmartPlanEligibility.isEligible("b", ids), "isEligible")

    // Tilawah + tajweed are one program.
    check(ProgramKindRules.canonical("tajweed") == "tilawah" && ProgramKindRules.canonical("tilawah") == "tilawah", "canonical")
    check(ProgramKindRules.canonical(null) == "regular" && ProgramKindRules.canonical("training") == "training", "canonical others")
    println("HifzSpanRulesSelfTest PASS")
}
