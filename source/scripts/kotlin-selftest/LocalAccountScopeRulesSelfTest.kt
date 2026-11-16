import com.mohammedsamir.halaqati.data.LocalAccountScopeRules

fun main() {
    val a = LocalAccountScopeRules.ownerKey("user-a", "archive_v3")
    val b = LocalAccountScopeRules.ownerKey("user-b", "archive_v3")
    check(a != b)
    check(LocalAccountScopeRules.belongsTo(a, "user-a"))
    check(!LocalAccountScopeRules.belongsTo(a, "user-b"))
    check(runCatching { LocalAccountScopeRules.ownerKey("", "archive") }.isFailure)
    check(runCatching { LocalAccountScopeRules.ownerKey("user|bad", "archive") }.isFailure)
    println("LocalAccountScopeRules self-tests passed")
}
