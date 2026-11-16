import com.mohammedsamir.halaqati.plans.RecitationGapRules
import com.mohammedsamir.halaqati.quran.QuranLocation

private fun check(ok: Boolean, msg: String) { if (!ok) { println("FAIL: $msg"); kotlin.system.exitProcess(1) } }

fun main() {
    val rev = "reverse_surahs_forward_ayahs"
    val fwd = "forward_surahs_forward_ayahs"
    check(RecitationGapRules.gap(QuranLocation(71, 13), QuranLocation(55, 1), rev)?.skippedSurahs == 15, "71 -> 55 skips 15 surahs")
    check(RecitationGapRules.gap(QuranLocation(6, 165), QuranLocation(5, 1), rev) == null, "adjacent surahs")
    check(RecitationGapRules.gap(QuranLocation(5, 57), QuranLocation(5, 58), rev) == null, "same surah")
    check(RecitationGapRules.gap(QuranLocation(5, 57), QuranLocation(6, 1), rev) == null, "going backwards is not a gap")
    check(RecitationGapRules.gap(QuranLocation(2, 10), QuranLocation(5, 1), fwd)?.skippedSurahs == 2, "forward route")
    check(RecitationGapRules.gap(null, QuranLocation(5, 1), rev) == null, "no anchor")
    check(RecitationGapRules.message(RecitationGapRules.gap(QuranLocation(71, 13), QuranLocation(55, 1), rev)!!).contains("71:13"), "message")
    println("RecitationGapRulesSelfTest PASS")
}
