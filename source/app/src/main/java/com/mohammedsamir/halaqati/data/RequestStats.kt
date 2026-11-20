package com.mohammedsamir.halaqati.data

/**
 * In-memory counters of Supabase requests and response bytes per endpoint (query values are never kept). Used by DEBUG builds to take the
 * before/after baseline on a device or emulator; it is bounded so it cannot grow forever. Release builds do not record.
 */
class RequestStats(private val maxEndpoints: Int = 200) {
    data class Entry(val endpoint: String, val count: Int, val bytes: Long)

    private val map = LinkedHashMap<String, LongArray>()
    private var total = 0
    private var bytesTotal = 0L

    private fun endpointOf(method: String, url: String): String {
        val afterHost = url.substringAfter("://", url).let { rest -> rest.indexOf('/').let { i -> if (i >= 0) rest.substring(i) else "/" } }
        return method.uppercase() + " " + afterHost.substringBefore('?')
    }

    @Synchronized fun record(method: String, url: String, bytes: Long) {
        var key = endpointOf(method, url)
        if (key !in map && map.size >= maxEndpoints) key = "OTHER"
        val slot = map.getOrPut(key) { LongArray(2) }
        slot[0] += 1; slot[1] += bytes
        total += 1; bytesTotal += bytes
    }

    @Synchronized fun snapshot(): List<Entry> = map.entries.map { Entry(it.key, it.value[0].toInt(), it.value[1]) }.sortedByDescending { it.count }
    @Synchronized fun totalRequests(): Int = total
    @Synchronized fun totalBytes(): Long = bytesTotal
    @Synchronized fun mark(): Int = total
    @Synchronized fun requestsSince(mark: Int): Int = total - mark
    @Synchronized fun reset() { map.clear(); total = 0; bytesTotal = 0L }

    companion object { val global = RequestStats() }
}
