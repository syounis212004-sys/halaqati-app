package com.mohammedsamir.halaqati.data

import org.json.JSONArray

/**
 * Lists that are capped by a row ceiling must lose their OLDEST rows, never the newest ones.
 * Such queries are therefore read newest-first, and this turns the result back into the oldest-first order
 * the plan engine walks through.
 */
object QueryWindowRules {
    fun <T> oldestFirst(newestFirst: List<T>): List<T> = newestFirst.asReversed().toList()

    fun oldestFirst(newestFirst: JSONArray): JSONArray {
        val out = JSONArray()
        for (index in newestFirst.length() - 1 downTo 0) out.put(newestFirst.get(index))
        return out
    }
}
