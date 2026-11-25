package lab.dkmushaf

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF

/** Pure drawing and geometry for one precomputed page, shared by the screen and the tests. All numbers follow precomputed/layout.ts (digitalkhatt.org, MIT). */
object DkRender {
    fun lineHeight(wPx: Float) = wPx * 232f / 1440f
    fun scaleFor(p: DkPage, wPx: Float) = wPx / p.lines.maxOf { it.width }

    /** Draws every line right-aligned; glyph origin = pen after the advance + x offset, y flipped (font units are y-up). */
    fun draw(nc: Canvas, data: DkData, p: DkPage, wPx: Float, paint: Paint) {
        val s = scaleFor(p, wPx); val lh = lineHeight(wPx)
        for ((li, line) in p.lines.withIndex()) {
            nc.save(); nc.translate(wPx, li * lh + lh * 0.72f); nc.scale(s * line.xscale, -s)
            var pos = -line.x
            for (g in line.glyphs) {
                pos -= g.adv
                val path = data.path(g.cp, g.lt, g.rt) ?: continue
                nc.save(); nc.translate(pos + g.xo, g.yo); nc.drawPath(path, paint); nc.restore()
            }
            nc.restore()
        }
    }

    /** One rectangle per word (glyphs between space glyphs), in canvas pixels, plus the line index. */
    fun wordRects(p: DkPage, wPx: Float): List<Pair<Int, RectF>> {
        val s = scaleFor(p, wPx); val lh = lineHeight(wPx); val out = ArrayList<Pair<Int, RectF>>()
        for ((li, line) in p.lines.withIndex()) {
            var pos = -line.x; var minL = Float.MAX_VALUE; var maxR = -Float.MAX_VALUE; var inWord = false
            fun flush() {
                if (inWord && minL < maxR) out.add(li to RectF(wPx + minL * s * line.xscale, li * lh, wPx + maxR * s * line.xscale, (li + 1) * lh))
                minL = Float.MAX_VALUE; maxR = -Float.MAX_VALUE; inWord = false
            }
            for (g in line.glyphs) {
                if (g.cp == 32) { pos -= g.adv; flush(); continue }
                val right = pos; pos -= g.adv
                if (g.adv > 0) { inWord = true; minL = minOf(minL, pos); maxR = maxOf(maxR, right) }
            }
            flush()
        }
        return out
    }
}
