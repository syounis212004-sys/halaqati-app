package com.mohammedsamir.halaqati.quran

import org.json.JSONArray
import org.json.JSONObject

/** Versioned payload kept inside the existing selection_data.tilawah_assessment JSON. */
object TilawahMarkJson {
    private fun parse(row: JSONObject?): TilawahMark? = row?.let {
        runCatching {
            val category = TilawahCategory.entries.first { c -> c.key == row.getString("category") }
            TilawahMark(row.getString("word_id"), row.getInt("page"), category,
                row.optInt("occurrences", 1), row.optString("correction_state") == "corrected",
                row.optString("note"), row.optString("subtype"),
                row.optString("previous_mark_id").takeIf { id -> id.isNotBlank() && id != "null" })
        }.getOrNull()
    }

    fun decode(rows: JSONArray?): List<TilawahMark> =
        if (rows == null) emptyList() else (0 until rows.length()).mapNotNull { parse(rows.optJSONObject(it)) }.distinctBy { it.markId }

    /** Retain unrecognized rows and fields so an older editor cannot erase future data. */
    fun encode(marks: List<TilawahMark>, original: JSONArray? = null): JSONArray {
        val rows = JSONArray()
        val originals = mutableMapOf<String, JSONObject>()
        if (original != null) for (i in 0 until original.length()) {
            val row = original.optJSONObject(i)
            val mark = parse(row)
            if (mark == null) rows.put(original.opt(i)) else if (row != null) originals[mark.markId] = row
        }
        marks.forEach { mark ->
            rows.put(JSONObject(originals[mark.markId]?.toString() ?: "{}")
                .put("mark_id", mark.markId).put("word_id", mark.wordId).put("page", mark.page)
                .put("class", if (mark.category.major) "major" else "minor")
                .put("category", mark.category.key).put("occurrences", mark.occurrences)
                .put("correction_state", if (mark.corrected) "corrected" else "open")
                .put("note", mark.note).put("subtype", mark.subtype)
                .put("previous_mark_id", mark.previousMarkId ?: JSONObject.NULL))
        }
        return rows
    }
}
