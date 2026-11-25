package com.mohammedsamir.halaqati.quran.dk

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import java.util.zip.GZIPInputStream

/**
 * The mushaf page pack (DigitalKhatt layout). The glyph streams encode the Quran text, so the pack is never committed: the build workflows generate it
 * from a pinned madina.json (scripts/dk/make_dk_assets.py) and check that it is inside the APK.
 * Rendering follows precomputed/layout.ts of digitalkhatt.org (MIT); font outlines come from the Madina font (OFL 1.1).
 */
object DkPack {
    /** True when the page pack is inside the app (the release build always carries it). */
    fun available(ctx: Context): Boolean = try { ctx.assets.open("dk/ids.bin").close(); true } catch (_: Exception) { false }
    fun supports(ctx: Context, page: Int): Boolean = page in 1..604 && available(ctx)

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
    const val HEADER_SCALE = 0.84f; const val HEADER_BASELINE = 0.575f
    /** Unicode pause signs U+06D6..U+06DC, rub el hizb U+06DE and sajdah U+06E9 as they appear in the stream. */
    fun isSign(cp: Int) = cp in 1750..1756 || cp == 1758 || cp == 1769

    fun lineHeight(wPx: Float) = wPx * 232f / 1440f
    fun scaleFor(p: DkPage, wPx: Float) = wPx / p.lines.maxOf { it.width }

    fun draw(nc: Canvas, data: DkData, p: DkPage, wPx: Float, pitch: Float, paint: Paint, colors: DkColors? = null) {
        val s = scaleFor(p, wPx)
        for ((li, line) in p.lines.withIndex()) {
            nc.save()
            if (line.type == 1) {
                // the sura-name line sits inside its cartouche: a little smaller and raised, so tails such as the one of the ha stay inside the frame
                val k = HEADER_SCALE
                nc.translate(wPx / 2f, li * pitch + pitch * HEADER_BASELINE); nc.scale(k, k); nc.translate(wPx / 2f, 0f)
            } else nc.translate(wPx, li * pitch + pitch * 0.72f)
            nc.scale(s * line.xscale, -s)
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
