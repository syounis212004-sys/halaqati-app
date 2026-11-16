import com.mohammedsamir.halaqati.data.SessionDeleteGuard

fun main() {
    val id = "52b37c9e-5363-344f-ac5a-8f622870c8db"
    check(SessionDeleteGuard.sessionIdFromPath("/rest/v1/sessions?id=eq.$id") == id)
    check(SessionDeleteGuard.sessionIdFromPath("/rest/v1/students?id=eq.$id") == null)
    check(SessionDeleteGuard.sessionIdFromPath("/rest/v1/sessions?halaqa_id=eq.x") == null)
    println("SessionDeleteGuardSelfTest: OK")
}
