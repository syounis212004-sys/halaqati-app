package com.mohammedsamir.halaqati.plans

/** RC12.1: canonical handling for legacy/imported plan IDs. */
object SmartPlanTagRules {
    fun normalizeId(raw: String?): String? = raw?.trim()?.takeIf {
        it.isNotEmpty() && !it.equals("null", ignoreCase = true) && !it.equals("undefined", ignoreCase = true)
    }
}
