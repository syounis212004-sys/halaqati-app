import com.mohammedsamir.halaqati.data.StudentTrackRules

fun main() {
    check(StudentTrackRules.allowed == setOf("general_hifz", "tathbit"))
    check(StudentTrackRules.canonicalStored("sard") == "general_hifz")
    check(StudentTrackRules.parseInput("سرد") == "general_hifz")
    check(StudentTrackRules.parseInput("تثبيت") == "tathbit")
    check(StudentTrackRules.parseInput("مسار ثالث") == null)
    check(StudentTrackRules.postgrestFilter("track_code", "general_hifz") == "track_code=in.(general_hifz,sard)")
    println("StudentTrackRules self-tests passed")
}
