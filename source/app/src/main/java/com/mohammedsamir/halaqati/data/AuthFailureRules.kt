package com.mohammedsamir.halaqati.data

/**
 * Tells a refresh that can still succeed later (network, rate limit, a rotated token another caller already saved) from
 * one that never can: Supabase has deleted this device's session, so its refresh token no longer exists.
 *
 * Field case (2026-11-22): the Android session of an administrator disappeared on the server while the web session of the
 * same account stayed valid. Every app call then failed with an expired token and `refresh_token_not_found`; the app kept
 * retrying (hundreds of requests per minute) and showed a red error for 10+ minutes until the user signed out by hand.
 * With this rule the app knows at the FIRST such answer, stops calling the server and asks for a sign-in straight away.
 * Queued offline work is untouched (sign-out never clears the local database).
 */
object AuthFailureRules {
    private val goneCodes = setOf("refresh_token_not_found", "session_not_found", "session_expired")

    fun isSessionGone(status: Int, errorCode: String?, message: String?): Boolean {
        if (status != 400 && status != 401 && status != 403) return false
        if (errorCode != null && errorCode.lowercase() in goneCodes) return true
        // Older GoTrue versions send only the message.
        return message.orEmpty().contains("Refresh Token Not Found", ignoreCase = true)
    }

    const val SESSION_GONE_MESSAGE = "انتهت جلسة الدخول على هذا الجهاز. سجّل الدخول من جديد (لن تضيع بياناتك المحفوظة)."
}
