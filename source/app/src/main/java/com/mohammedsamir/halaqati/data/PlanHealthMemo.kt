package com.mohammedsamir.halaqati.data

/**
 * A smart-plan health record that was already sent to the server today, with exactly the same numbers, is not sent again.
 * Measured: `rc19_record_plan_health` was 227 of 831 Android requests in one day (27%), one POST per (plan, student) on every
 * 6-hour alerts run. The server de-duplicates notifications, but the request itself was still made.
 * Only a record that finished cleanly (no notification owed, or the notification sent and marked) is remembered, so a failed
 * notification is retried by the next run. Entries are kept per account and only for the current day.
 */
object PlanHealthMemo {
    data class Inputs(
        val scheduled: Boolean, val health: String, val ratio: Double,
        val expected: Double, val completed: Double, val behind: Double,
    )

    interface Store {
        fun read(owner: String): String?
        fun write(owner: String, text: String)
    }

    private fun round(v: Double) = String.format(java.util.Locale.ROOT, "%.4f", v)

    fun signature(day: String, i: Inputs): String =
        listOf(day, i.scheduled.toString(), i.health, round(i.ratio), round(i.expected), round(i.completed), round(i.behind)).joinToString("|")

    private fun key(planId: String, studentId: String) = "$planId:$studentId"

    private fun load(store: Store, owner: String, day: String): LinkedHashMap<String, String> {
        val out = LinkedHashMap<String, String>()
        val lines = (store.read(owner) ?: return out).split('\n')
        if (lines.firstOrNull() != "day=$day") return out
        for (line in lines.drop(1)) {
            val parts = line.split('\t')
            if (parts.size == 2 && parts[0].isNotBlank()) out[parts[0]] = parts[1]
        }
        return out
    }

    fun unchanged(store: Store, owner: String, day: String, planId: String, studentId: String, signature: String): Boolean =
        load(store, owner, day)[key(planId, studentId)] == signature

    fun remember(store: Store, owner: String, day: String, planId: String, studentId: String, signature: String) {
        val map = load(store, owner, day)
        map[key(planId, studentId)] = signature
        store.write(owner, "day=$day\n" + map.entries.joinToString("\n") { "${it.key}\t${it.value}" })
    }
}
