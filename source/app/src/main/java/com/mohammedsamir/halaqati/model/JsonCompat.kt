package com.mohammedsamir.halaqati.model

import org.json.JSONObject
import com.mohammedsamir.halaqati.plans.SmartPlanTagRules

/**
 * RC12.1 compatibility normalizer for legacy JSON coming from Supabase/imports.
 * org.json's optString() renders JSONObject.NULL as the literal string "null".
 * That is dangerous for IDs because "null" then behaves like a real foreign key.
 */
fun JSONObject.optNullableString(name: String): String? {
    if (!has(name) || isNull(name)) return null
    val raw = opt(name)
    if (raw == null || raw === JSONObject.NULL) return null
    return SmartPlanTagRules.normalizeId(raw.toString())
}
