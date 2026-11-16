import com.mohammedsamir.halaqati.data.ConflictRules

fun main() {
    check(!ConflictRules.changedSinceBase(null, "2026-09-24T10:00:00Z"))
    check(!ConflictRules.changedSinceBase("2026-09-24T10:00:00Z", "2026-09-24T10:00:00Z"))
    check(ConflictRules.changedSinceBase("2026-09-24T10:00:00Z", "2026-09-24T10:00:01Z"))
    check(!ConflictRules.changedSinceBase("2026-09-24T10:00:01Z", "2026-09-24T10:00:00Z"))
    check(ConflictRules.changedSinceBase("revision-a", "revision-b"))
    println("ConflictRules self-tests passed")
}
