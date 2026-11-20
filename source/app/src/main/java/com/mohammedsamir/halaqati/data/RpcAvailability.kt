package com.mohammedsamir.halaqati.data

/** Server-side helpers (RPCs added by SQL migrations) may not be deployed yet: the app asks once, remembers a "missing" answer for 6 hours and falls back. */
object RpcAvailability {
    const val REMEMBER_MISSING_MS = 6 * 3_600_000L

    fun isMissing(status: Int, sqlState: String?, message: String?): Boolean =
        status == 404 || sqlState == "PGRST202" ||
            (message?.contains("Could not find the function", ignoreCase = true) == true) ||
            (sqlState == "42883")

    fun shouldTry(lastMissingAt: Long, now: Long): Boolean = lastMissingAt == 0L || now - lastMissingAt >= REMEMBER_MISSING_MS
}
