package com.mohammedsamir.halaqati.data

/** RC24.4: pure helper for the queued session-DELETE safety check. */
object SessionDeleteGuard {
    private val idRegex = Regex("""(?:^|[?&])id=eq\.([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})(?:&|$)""")

    fun sessionIdFromPath(path: String): String? {
        if (!path.contains("/rest/v1/sessions")) return null
        return idRegex.find(path)?.groupValues?.get(1)
    }
}
