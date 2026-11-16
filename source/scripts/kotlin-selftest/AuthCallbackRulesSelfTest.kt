import com.mohammedsamir.halaqati.data.AuthCallbackRules

fun main() {
    val prefix = "com.mohammedsamir.halaqati://auth/callback"
    val recovery = AuthCallbackRules.parse(
        "$prefix#access_token=a.b.c&refresh_token=r&type=recovery",
        prefix,
    ) ?: error("recovery callback not parsed")
    check(recovery.type == "recovery")
    check(recovery.accessToken == "a.b.c")
    check(recovery.refreshToken == "r")
    check(recovery.error == null)

    val failure = AuthCallbackRules.parse("$prefix#error_description=Expired%20link&type=recovery", prefix)
        ?: error("error callback not parsed")
    check(failure.error == "Expired link")
    check(AuthCallbackRules.parse("https://invalid.example/#access_token=x", prefix) == null)
    check(AuthCallbackRules.parse("$prefix.evil#access_token=x&refresh_token=y", prefix) == null)
    check(AuthCallbackRules.parse("com.mohammedsamir.halaqati://auth/callback/extra#access_token=x&refresh_token=y", prefix) == null)
    println("AuthCallbackRules self-test passed")
}
