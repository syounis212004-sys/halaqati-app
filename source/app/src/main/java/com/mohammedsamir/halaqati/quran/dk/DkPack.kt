package com.mohammedsamir.halaqati.quran.dk

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import java.util.zip.GZIPInputStream

/**
 * EXPERIMENTAL DigitalKhatt page viewer support. Off by default and absent from production builds: the glyph streams encode the Quran text, so the
 * pack is never committed; the experimental workflow generates it (scripts/dk/make_dk_assets.py). Without the pack [available] is false and no UI appears.
 * Rendering follows precomputed/layout.ts of digitalkhatt.org (MIT); font outlines come from the Madina font (OFL 1.1).
 */
object DkPack {
    /** Pages whose DigitalKhatt layout still needs a human decision against the printed 1421H page: they keep the current viewer. */
    val PAGES_NEEDING_REVIEW: Set<Int> = setOf(177)

    fun available(ctx: Context): Boolean = try { ctx.assets.open("dk/ids.bin").close(); true } catch (_: Exception) { false }
    fun supports(ctx: Context, page: Int): Boolean = page in 1..604 && page !in PAGES_NEEDING_REVIEW && available(ctx)

    private const val PREFS = "halaqati_dk_experimental"
    fun enabled(ctx: Context): Boolean = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("enabled", true) && available(ctx)
    fun setEnabled(ctx: Context, on: Boolean) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("enabled", on).apply()

    @Volatile private var shared: DkData? = null
    /** The glyph outlines are parsed once per process (blocking: call off the main thread). */
    fun data(ctx: Context): DkData = shared ?: synchronized(this) { shared ?: DkData(ctx.applicationContext).also { it.loadGlyphs(); shared = it } }

    @Volatile private var idLines: List<String>? = null
    /** Word id (sura:ayah:position or sura:ayah:end, the ids MushafEngine uses) of every DK word of the page's text lines, in reading order. */
    fun idsOf(ctx: Context, page: Int): List<String> {
        val all = idLines ?: synchronized(this) {
            idLines ?: ctx.assets.open("dk/ids.bin").use { GZIPInputStream(it).bufferedReader().readText().split("\n") }.also { idLines = it }
        }
        return all.getOrNull(page - 1)?.split(",")?.filter { it.isNotEmpty() } ?: emptyList()
    }
}

/** Ink colours (ARGB) by role: running text, the sura-name line, ayah-number markers and pause/sajdah/hizb signs. */
class DkColors(val ink: Int, val header: Int, val ayah: Int, val sign: Int)

/** Pure geometry and drawing for one precomputed page. [pitch] is the line pitch in pixels (the layout's own is lineHeight(w)). */
object DkRender {
    const val AYAH_FIRST = 58193; const val AYAH_LAST = 58478
    /** Unicode pause signs U+06D6..U+06DC, rub el hizb U+06DE and sajdah U+06E9 as they appear in the stream. */
    fun isSign(cp: Int) = cp in 1750..1756 || cp == 1758 || cp == 1769

    fun lineHeight(wPx: Float) = wPx * 232f / 1440f
    fun scaleFor(p: DkPage, wPx: Float) = wPx / p.lines.maxOf { it.width }

    fun draw(nc: Canvas, data: DkData, p: DkPage, wPx: Float, pitch: Float, paint: Paint, colors: DkColors? = null) {
        val s = scaleFor(p, wPx)
        for ((li, line) in p.lines.withIndex()) {
            nc.save(); nc.translate(wPx, li * pitch + pitch * 0.72f); nc.scale(s * line.xscale, -s)
            var pos = -line.x
            for (g in line.glyphs) {
                pos -= g.adv
                val path = data.path(g.cp, g.lt, g.rt) ?: continue
                if (colors != null) paint.color = when {
                    line.type == 1 -> colors.header
                    line.type == 0 && g.cp in AYAH_FIRST..AYAH_LAST -> colors.ayah
                    line.type == 0 && isSign(g.cp) -> colors.sign
                    else -> colors.ink
                }
                nc.save(); nc.translate(pos + g.xo, g.yo); nc.drawPath(path, paint); nc.restore()
            }
            nc.restore()
        }
    }

    /** One rectangle per DK word of the text lines (type 0), in reading order, as drawn by [draw] with the same [pitch]. */
    fun wordRects(p: DkPage, wPx: Float, pitch: Float): List<RectF> {
        val s = scaleFor(p, wPx); val out = ArrayList<RectF>()
        for ((li, line) in p.lines.withIndex()) {
            if (line.type != 0) continue
            var pos = -line.x; var minL = Float.MAX_VALUE; var maxR = -Float.MAX_VALUE; var inWord = false
            fun flush() {
                if (inWord && minL < maxR) out.add(RectF(wPx + minL * s * line.xscale, li * pitch, wPx + maxR * s * line.xscale, (li + 1) * pitch))
                minL = Float.MAX_VALUE; maxR = -Float.MAX_VALUE; inWord = false
            }
            for (g in line.glyphs) {
                if (g.cp == 32) { pos -= g.adv; flush(); continue }
                val right = pos; pos -= g.adv
                inWord = true; minL = minOf(minL, pos); maxR = maxOf(maxR, right)
            }
            flush()
        }
        return out
    }

    /** id -> rectangle; words that share an id (a pair DK splits) are joined. Ayah-end markers are not markable and are left out. */
    fun rectsById(ids: List<String>, rects: List<RectF>): LinkedHashMap<String, RectF> {
        val m = LinkedHashMap<String, RectF>()
        for (i in 0 until minOf(ids.size, rects.size)) {
            if (ids[i].endsWith(":end")) continue
            val r = rects[i]; val e = m[ids[i]]
            if (e == null) m[ids[i]] = RectF(r) else e.union(r)
        }
        return m
    }
}
