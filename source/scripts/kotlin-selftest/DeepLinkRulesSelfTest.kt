import com.mohammedsamir.halaqati.data.DeepLinkRules

fun main() {
    val studentId = "11111111-2222-3333-4444-555555555555"
    check(DeepLinkRules.navigation("student:$studentId") == DeepLinkRules.Destination("profile", studentId))
    check(DeepLinkRules.navigation("halaqati://student/$studentId") == DeepLinkRules.Destination("profile", studentId))
    check(DeepLinkRules.navigation("halaqati://reports") == DeepLinkRules.Destination("reports"))
    check(DeepLinkRules.navigation("notifications") == DeepLinkRules.Destination("notifications"))
    check(DeepLinkRules.navigation("halaqati://auth/callback") == null)
    check(DeepLinkRules.navigation("halaqati://unknown") == null)
    check(DeepLinkRules.navigation("student:not-a-uuid") == null)
    check(DeepLinkRules.navigation("halaqati://student/not-a-uuid") == null)
    println("DeepLinkRules self-tests passed")
}
