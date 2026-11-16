import com.mohammedsamir.halaqati.data.GoogleAuthRules

fun main() {
    check(GoogleAuthRules.sha256Hex("abc") == "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")
    val nonce = GoogleAuthRules.newNonce()
    check(nonce.raw.isNotBlank())
    check(nonce.hashed.length == 64)
    check(nonce.hashed == GoogleAuthRules.sha256Hex(nonce.raw))
    println("GoogleAuthRulesSelfTest: PASS")
}
