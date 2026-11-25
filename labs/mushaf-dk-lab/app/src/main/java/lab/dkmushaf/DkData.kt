package lab.dkmushaf

import android.content.Context
import android.graphics.Path
import android.util.JsonReader
import android.util.LruCache
import java.io.InputStreamReader
import java.util.zip.GZIPInputStream

/** One positioned glyph of a precomputed DigitalKhatt line (units of the Madina font, em = 1000). */
class DkGlyph(val cp: Int, val adv: Int, val xo: Int, val yo: Int, val lt: Int, val rt: Int)
class DkLine(val type: Int, val x: Int, val xscale: Float, val glyphs: List<DkGlyph>) {
    /** Natural width in glyph units including the line's own x offset and x-scale (as in precomputed/layout.ts of digitalkhatt.org, MIT). */
    val width: Float = (x + glyphs.sumOf { it.adv }) * xscale
}
class DkPage(val lines: List<DkLine>)

typealias Paths = List<List<FloatArray>>
/** Outline of one glyph plus the four tatweel masters used to interpolate it. */
class DkOutline(val lim: IntArray?, val d: Paths, val ml: Paths?, val xl: Paths?, val mr: Paths?, val xr: Paths?)

class DkData(private val ctx: Context) {
    private val outlines = HashMap<Int, DkOutline>()
    private val pathCache = object : LruCache<Long, Path>(6000) {}
    @Volatile var loaded = false

    fun loadGlyphs() {
        if (loaded) return
        ctx.assets.open("dk/glyphs.bin").use { raw ->
            JsonReader(InputStreamReader(GZIPInputStream(raw), Charsets.UTF_8)).use { r ->
                r.beginObject()
                while (r.hasNext()) {
                    val cp = r.nextName().toInt()
                    var lim: IntArray? = null; var d: Paths = emptyList()
                    var ml: Paths? = null; var xl: Paths? = null; var mr: Paths? = null; var xr: Paths? = null
                    r.beginObject()
                    while (r.hasNext()) when (r.nextName()) {
                        "lim" -> { r.beginArray(); val a = IntArray(4); var i = 0; while (r.hasNext()) a[i++] = r.nextDouble().toInt(); r.endArray(); lim = a }
                        "d" -> d = readPaths(r)
                        "ml" -> ml = readPaths(r)
                        "xl" -> xl = readPaths(r)
                        "mr" -> mr = readPaths(r)
                        "xr" -> xr = readPaths(r)
                        else -> r.skipValue()
                    }
                    r.endObject()
                    outlines[cp] = DkOutline(lim, d, ml, xl, mr, xr)
                }
                r.endObject()
            }
        }
        loaded = true
    }

    private fun readPaths(r: JsonReader): Paths {
        val out = ArrayList<List<FloatArray>>()
        r.beginArray()
        while (r.hasNext()) {
            val sub = ArrayList<FloatArray>()
            r.beginArray()
            while (r.hasNext()) {
                val el = ArrayList<Float>(6)
                r.beginArray(); while (r.hasNext()) el.add(r.nextDouble().toFloat()); r.endArray()
                sub.add(el.toFloatArray())
            }
            r.endArray(); out.add(sub)
        }
        r.endArray(); return out
    }

    fun loadPage(n: Int): DkPage {
        val lines = ArrayList<DkLine>()
        ctx.assets.open("dk/p$n.bin").use { raw ->
            JsonReader(InputStreamReader(GZIPInputStream(raw), Charsets.UTF_8)).use { r ->
                r.beginArray()
                while (r.hasNext()) {
                    r.beginArray()
                    val type = r.nextInt(); val x = r.nextInt(); val xs = r.nextDouble().toFloat()
                    val gl = ArrayList<DkGlyph>()
                    r.beginArray()
                    while (r.hasNext()) {
                        val cp = r.nextInt(); val adv = r.nextInt(); val xo = r.nextInt(); val yo = r.nextInt(); val lt = r.nextInt(); val rt = r.nextInt()
                        gl.add(DkGlyph(cp, adv, xo, yo, lt, rt))
                    }
                    r.endArray(); r.endArray()
                    lines.add(DkLine(type, x, xs, gl))
                }
                r.endArray()
            }
        }
        return DkPage(lines)
    }

    val cachedPaths: Int get() = pathCache.size()

    /** Android Path of a glyph for the given tatweel amounts, interpolated exactly as getGlyphSvg() in digitalkhatt.org layout.ts. */
    fun path(cp: Int, ltIn: Int, rtIn: Int): Path? {
        val o = outlines[cp] ?: return null
        val key = (cp.toLong() shl 32) or ((ltIn and 0xFFFF).toLong() shl 16) or (rtIn and 0xFFFF).toLong()
        pathCache.get(key)?.let { return it }
        val lim = o.lim ?: intArrayOf(0, 0, 0, 0)
        val lt = ltIn.coerceIn(minOf(lim[0], lim[1]), maxOf(lim[0], lim[1])); val rt = rtIn.coerceIn(minOf(lim[2], lim[3]), maxOf(lim[2], lim[3]))
        val ls = if (lt < 0 && lim[0] != 0) lt.toFloat() / lim[0] else if (lt > 0 && lim[1] != 0) lt.toFloat() / lim[1] else 0f
        val rs = if (rt < 0 && lim[2] != 0) rt.toFloat() / lim[2] else if (rt > 0 && lim[3] != 0) rt.toFloat() / lim[3] else 0f
        fun ip(v: Float, i: Int, j: Int, k: Int): Float {
            var x = v
            if (lt < 0) o.ml?.let { x += (it[i][j][k] - v) * ls } else if (lt > 0) o.xl?.let { x += (it[i][j][k] - v) * ls }
            if (rt < 0) o.mr?.let { x += (it[i][j][k] - v) * rs } else if (rt > 0) o.xr?.let { x += (it[i][j][k] - v) * rs }
            return x
        }
        val p = Path()
        for ((i, sub) in o.d.withIndex()) for ((j, e) in sub.withIndex()) {
            if (e.size == 2) p.moveTo(ip(e[0], i, j, 0), ip(e[1], i, j, 1))
            else if (e.size == 6) p.cubicTo(ip(e[0], i, j, 0), ip(e[1], i, j, 1), ip(e[2], i, j, 2), ip(e[3], i, j, 3), ip(e[4], i, j, 4), ip(e[5], i, j, 5))
        }
        p.close()
        pathCache.put(key, p); return p
    }
}
