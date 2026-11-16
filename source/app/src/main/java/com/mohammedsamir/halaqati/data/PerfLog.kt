package com.mohammedsamir.halaqati.data

/** RC27.10: tiny in-memory timing log (last 60 entries), shown in Settings → التشخيص to find slow loads by number. */
object PerfLog {
    data class Entry(val name: String, val millis: Long, val at: Long)

    private val entries = java.util.concurrent.ConcurrentLinkedDeque<Entry>()

    fun <T> time(name: String, block: () -> T): T {
        val start = System.nanoTime()
        try {
            return block()
        } finally {
            record(name, (System.nanoTime() - start) / 1_000_000L)
        }
    }

    fun record(name: String, millis: Long) {
        entries.addFirst(Entry(name, millis, System.currentTimeMillis()))
        while (entries.size > 60) entries.pollLast()
    }

    fun snapshot(): List<Entry> = entries.toList()
}
