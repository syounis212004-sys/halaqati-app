package com.mohammedsamir.halaqati.quran

import org.json.JSONArray
import org.json.JSONObject

/** Local draft codec; stores the exact range, not just its bounding pages. */
object TilawahDraftJson {
    fun strings(a: JSONArray?): List<String> =
        if (a == null) emptyList() else (0 until a.length()).map { a.getString(it) }
    private fun ints(a: JSONArray?): List<Int> =
        if (a == null) emptyList() else (0 until a.length()).map { a.getInt(it) }
    private fun location(l: QuranLocation?) = l?.let { JSONArray().put(it.surah).put(it.ayah) }
    private fun location(a: JSONArray?): QuranLocation? = a?.let {
        val s = it.getInt(0); val v = it.getInt(1)
        QuranMetadata.globalAyah(s, v)
        QuranLocation(s, v)
    }
    fun encodeRange(r: QuranRangeResult): JSONObject = JSONObject()
        .put("input_method", r.inputMethod)
        .put("direction", r.requestedDirection.name)
        .put("traversal", r.resolvedTraversal.name)
        .put("start", location(r.start) ?: JSONObject.NULL)
        .put("end", location(r.end) ?: JSONObject.NULL)
        .put("start_page", r.startPage ?: JSONObject.NULL)
        .put("end_page", r.endPage ?: JSONObject.NULL)
        .put("lines", JSONArray(r.qcfLineKeys))
        .put("ayah_count", r.ayahCount).put("surah_count", r.surahCount)
        .put("surahs", JSONArray(r.selectedSurahs)).put("juz", JSONArray(r.selectedJuz))
        .put("segments", JSONArray(r.orderedSegments.map { s -> JSONObject()
            .put("start", location(s.start)).put("end", location(s.end)).put("direction", s.ayahDirection.name) }))
    fun decodeRange(o: JSONObject): QuranRangeResult {
        val lines = strings(o.getJSONArray("lines")).distinct()
        require(lines.isNotEmpty() && lines.size <= 1200 && lines.all {
            val p = it.split(':')
            p.size == 2 && (p[0].toIntOrNull() ?: 0) in 1..604 && (p[1].toIntOrNull() ?: 0) in 1..15
        }) { "نطاق المسودة غير صالح" }
        val arr = o.getJSONArray("segments")
        val segments = (0 until arr.length()).map { i ->
            val row = arr.getJSONObject(i)
            QuranSegment(requireNotNull(location(row.getJSONArray("start"))), requireNotNull(location(row.getJSONArray("end"))),
                QuranAyahDirection.valueOf(row.getString("direction")))
        }
        val start = requireNotNull(location(o.optJSONArray("start")))
        val end = requireNotNull(location(o.optJSONArray("end")))
        val startPage = o.getInt("start_page").also { require(it in 1..604) }
        val endPage = o.getInt("end_page").also { require(it in 1..604) }
        return QuranRangeResult(o.getString("input_method"), QuranTraversalDirection.valueOf(o.getString("direction")),
            QuranResolvedTraversal.valueOf(o.getString("traversal")), segments, start, end, startPage, endPage,
            lines.size / 15.0, o.getInt("ayah_count"), o.getInt("surah_count"), lines, ints(o.optJSONArray("surahs")), ints(o.optJSONArray("juz")))
    }
}
