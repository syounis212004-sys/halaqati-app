import com.mohammedsamir.halaqati.quran.RecitationParityRules

fun main() {
    check(RecitationParityRules.effectivePages(79.41, listOf("506:1", "506:2", "506:3", "506:4", "506:5", "506:6", "506:7", "506:8")) == 8.0 / 15.0)
    check(RecitationParityRules.rankingTrackForActivity("new_hifz") == "hifz")
    check(RecitationParityRules.rankingTrackForActivity("review_far") == "review")
    check(RecitationParityRules.rankingTrackForActivity("sard") == "sard")
    check(!RecitationParityRules.activityMatchesRanking("sard", "hifz"))
    check(RecitationParityRules.canonicalProgramTrack("tathbit") == "tathbit")
    check(RecitationParityRules.canonicalProgramTrack(null) == "general_hifz")
    val naba = RecitationParityRules.quranRangeLabel(78, 1, 78, 40, 582, 583)
    check(naba == "سورة النبأ · من الآية 1 إلى الآية 40") { naba }
    val crossSurah = RecitationParityRules.quranRangeLabel(42, 27, 43, 18, 486, 491)
    check(crossSurah == "من سورة الشورى آية 27 إلى سورة الزخرف آية 18") { crossSurah }
    val pageFallback = RecitationParityRules.quranRangeLabel(null, null, null, null, 504, 505)
    check(!pageFallback.contains("ص 504") && !pageFallback.contains("→")) { pageFallback }
    check(pageFallback.contains("سورة") && pageFallback.contains("آية")) { pageFallback }
    println("RecitationParityRulesSelfTest PASS")
}
