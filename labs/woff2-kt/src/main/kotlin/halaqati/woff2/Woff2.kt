// Original implementation written from the W3C WOFF2 Recommendation
// (https://www.w3.org/TR/WOFF2/). No code copied from google/woff2.
// Runtime dependency: org.brotli:dec (MIT) for the Brotli stream.
package halaqati.woff2

import org.brotli.dec.BrotliInputStream
import java.io.ByteArrayInputStream

class Woff2Exception(msg: String) : Exception(msg)

object Woff2 {
    private val KNOWN = arrayOf(
        "cmap", "head", "hhea", "hmtx", "maxp", "name", "OS/2", "post", "cvt ", "fpgm", "glyf", "loca", "prep",
        "CFF ", "VORG", "EBDT", "EBLC", "gasp", "hdmx", "kern", "LTSH", "PCLT", "VDMX", "vhea", "vmtx", "BASE",
        "GDEF", "GPOS", "GSUB", "EBSC", "JSTF", "MATH", "CBDT", "CBLC", "COLR", "CPAL", "SVG ", "sbix", "acnt",
        "avar", "bdat", "bloc", "bsln", "cvar", "fdsc", "feat", "fmtx", "fvar", "gvar", "hsty", "just", "lcar",
        "mort", "morx", "opbd", "prop", "trak", "Zapf", "Silf", "Glat", "Gloc", "Feat", "Sill"
    ).map { tagOf(it) }.toIntArray()

    private fun tagOf(s: String) = (s[0].code shl 24) or (s[1].code shl 16) or (s[2].code shl 8) or s[3].code
    private val GLYF = tagOf("glyf"); private val LOCA = tagOf("loca")
    private val HMTX = tagOf("hmtx"); private val HHEA = tagOf("hhea"); private val HEAD = tagOf("head")

    private class Entry(val tag: Int, val transformed: Boolean, val origLen: Int, val srcLen: Int, val srcOff: Int) {
        var data: ByteArray? = null; var dOff = 0; var dLen = 0   // final table bytes
    }

    class Cur(val d: ByteArray, var p: Int, val end: Int) {
        fun u8(): Int { if (p >= end) throw Woff2Exception("stream underrun"); return d[p++].toInt() and 255 }
        fun u16(): Int = (u8() shl 8) or u8()
        fun u32(): Int = (u16() shl 16) or u16()
        fun skip(n: Int) { if (n < 0 || n > end - p) throw Woff2Exception("stream underrun"); p += n }
        fun base128(): Int {
            var acc = 0L
            for (i in 0 until 5) {
                val b = u8()
                if (i == 0 && b == 0x80) throw Woff2Exception("bad UIntBase128")
                if (acc and 0xFE000000L != 0L) throw Woff2Exception("UIntBase128 overflow")
                acc = (acc shl 7) or (b and 0x7F).toLong()
                if (b and 0x80 == 0) { if (acc > Int.MAX_VALUE) throw Woff2Exception("too large"); return acc.toInt() }
            }
            throw Woff2Exception("UIntBase128 too long")
        }
        fun u255(): Int = when (val c = u8()) {
            253 -> u16(); 255 -> 253 + u8(); 254 -> 506 + u8(); else -> c
        }
    }

    /** Growable byte sink. */
    private class Out(cap: Int) {
        var a = ByteArray(maxOf(cap, 64)); var n = 0
        fun ensure(k: Int) { if (n + k > a.size) a = a.copyOf(maxOf(a.size * 2, n + k)) }
        fun u8(v: Int) { ensure(1); a[n++] = v.toByte() }
        fun u16(v: Int) { ensure(2); a[n++] = (v shr 8).toByte(); a[n++] = v.toByte() }
        fun bytes(s: ByteArray, o: Int, l: Int) { ensure(l); System.arraycopy(s, o, a, n, l); n += l }
        fun pad4() { while (n and 3 != 0) u8(0) }
    }

    fun isWoff2(b: ByteArray) = b.size >= 4 && b[0] == 'w'.code.toByte() && b[1] == 'O'.code.toByte() &&
        b[2] == 'F'.code.toByte() && b[3] == '2'.code.toByte()

    /** Decode a WOFF2 file (single font, not a collection) to an sfnt (TrueType) byte array. */
    @JvmStatic
    fun decode(src: ByteArray): ByteArray = try { decodeImpl(src) }
        catch (e: Woff2Exception) { throw e }
        catch (e: java.io.IOException) { throw Woff2Exception("corrupt brotli stream: ${e.message}") }
        catch (e: IndexOutOfBoundsException) { throw Woff2Exception("corrupt data: ${e.message}") }
        catch (e: NegativeArraySizeException) { throw Woff2Exception("corrupt data") }
        catch (e: NullPointerException) { throw Woff2Exception("corrupt data") }

    private fun decodeImpl(src: ByteArray): ByteArray {
        if (!isWoff2(src) || src.size < 48) throw Woff2Exception("not WOFF2")
        val c = Cur(src, 4, src.size)
        val flavor = c.u32()
        if (flavor == tagOf("ttcf")) throw Woff2Exception("font collections not supported")
        val length = c.u32(); val numTables = c.u16(); c.u16()
        c.u32() /* totalSfntSize (hint only) */
        val compSize = c.u32(); c.skip(2 + 2 + 4 * 5)
        if (length != src.size) throw Woff2Exception("length mismatch")
        if (numTables == 0) throw Woff2Exception("no tables")
        val entries = ArrayList<Entry>(numTables)
        var total = 0L
        for (i in 0 until numTables) {
            val fl = c.u8(); val idx = fl and 63; val ver = fl ushr 6
            val tag = if (idx == 63) c.u32() else KNOWN[idx]
            val orig = c.base128()
            val isGlyfLoca = tag == GLYF || tag == LOCA
            val transformed = if (isGlyfLoca) ver == 0 else ver != 0
            if (isGlyfLoca && ver > 3 || !isGlyfLoca && ver > 1 || (tag == HMTX && ver > 1)) throw Woff2Exception("bad transform")
            if (!isGlyfLoca && ver != 0 && tag != HMTX) throw Woff2Exception("unsupported transform")
            val sl = if (transformed) { val t = c.base128(); if (tag == LOCA && t != 0) throw Woff2Exception("loca transformLength != 0"); t } else orig
            entries.add(Entry(tag, transformed, orig, sl, total.toInt()))
            total += sl; if (total > Int.MAX_VALUE) throw Woff2Exception("too large")
        }
        val compStart = c.p
        if (compSize > src.size - compStart) throw Woff2Exception("truncated")
        // Brotli: one stream for every table, in directory order.
        val ub = ByteArray(total.toInt())
        BrotliInputStream(ByteArrayInputStream(src, compStart, compSize)).use { s ->
            var o = 0
            while (o < ub.size) { val r = s.read(ub, o, ub.size - o); if (r < 0) throw Woff2Exception("brotli short"); o += r }
        }
        fun find(tag: Int) = entries.firstOrNull { it.tag == tag }
        for (e in entries) if (!e.transformed) { e.data = ub; e.dOff = e.srcOff; e.dLen = e.srcLen }

        val g = find(GLYF); val l = find(LOCA)
        var numGlyphs = 0
        var xMins: IntArray? = null
        if (g != null && g.transformed) {
            if (l == null || !l.transformed) throw Woff2Exception("glyf/loca transform mismatch")
            val r = rebuildGlyf(ub, g.srcOff, g.srcLen, g.origLen)
            numGlyphs = r.numGlyphs; xMins = r.xMin
            g.data = r.glyf; g.dOff = 0; g.dLen = r.glyfLen
            l.data = r.loca; l.dOff = 0; l.dLen = r.loca.size
        } else if (g != null && l != null) {
            val locaD = l.data!!; val shortFmt = headLocaFormat(find(HEAD))
            numGlyphs = l.dLen / (if (shortFmt == 0) 2 else 4) - 1
            xMins = IntArray(numGlyphs) { i ->
                fun off(k: Int) = if (shortFmt == 0) 2 * rd16(locaD, l.dOff + 2 * k) else rd32(locaD, l.dOff + 4 * k)
                val a = off(i); val b = off(i + 1)
                if (b > a) rd16s(g.data!!, g.dOff + a + 2) else 0
            }
        }
        val h = find(HMTX)
        if (h != null && h.transformed) {
            val hh = find(HHEA) ?: throw Woff2Exception("hmtx without hhea")
            val xm = xMins ?: throw Woff2Exception("hmtx transform without glyf")
            val numH = rd16(hh.data!!, hh.dOff + 34)
            val nd = rebuildHmtx(ub, h.srcOff, h.srcLen, numH, numGlyphs, xm)
            h.data = nd; h.dOff = 0; h.dLen = nd.size
        }

        // Assemble sfnt, tables sorted by tag.
        val sorted = entries.sortedBy { it.tag.toLong() and 0xFFFFFFFFL }
        var size = 12L + 16L * sorted.size
        for (e in sorted) size += (e.dLen.toLong() + 3) and 3L.inv()
        if (size > Int.MAX_VALUE) throw Woff2Exception("too large")
        val out = ByteArray(size.toInt())
        wr32(out, 0, flavor); wr16(out, 4, sorted.size)
        var pow = 1; var log = 0
        while (pow * 2 <= sorted.size) { pow *= 2; log++ }
        wr16(out, 6, pow * 16); wr16(out, 8, log); wr16(out, 10, sorted.size * 16 - pow * 16)
        // Directory records are sorted by tag; table data is laid out in tag order too, except that a
        // rebuilt loca is placed right after glyf (this mirrors the reference decoder's layout).
        val layout = ArrayList<Entry>(sorted)
        if (g != null && g.transformed && l != null) { layout.remove(l); layout.add(layout.indexOf(g) + 1, l) }
        var pos = 12 + 16 * sorted.size
        var headPos = -1
        for (e in layout) {
            val d = 12 + 16 * sorted.indexOf(e)
            System.arraycopy(e.data!!, e.dOff, out, pos, e.dLen)
            if (e.tag == HEAD) { if (e.dLen < 12) throw Woff2Exception("short head"); headPos = pos; wr32(out, pos + 8, 0) }
            wr32(out, d, e.tag); wr32(out, d + 4, checksum(out, pos, e.dLen)); wr32(out, d + 8, pos); wr32(out, d + 12, e.dLen)
            pos += (e.dLen + 3) and 3.inv()
        }
        if (headPos >= 0) wr32(out, headPos + 8, (0xB1B0AFBAL - (checksum(out, 0, out.size).toLong() and 0xFFFFFFFFL)).toInt())
        return out
    }

    private fun headLocaFormat(h: Entry?): Int { if (h == null) throw Woff2Exception("no head"); return rd16s(h.data!!, h.dOff + 50) }
    private fun rd16(b: ByteArray, o: Int) = ((b[o].toInt() and 255) shl 8) or (b[o + 1].toInt() and 255)
    private fun rd16s(b: ByteArray, o: Int) = (rd16(b, o) shl 16) shr 16
    private fun rd32(b: ByteArray, o: Int) = (rd16(b, o) shl 16) or rd16(b, o + 2)
    private fun wr16(b: ByteArray, o: Int, v: Int) { b[o] = (v shr 8).toByte(); b[o + 1] = v.toByte() }
    private fun wr32(b: ByteArray, o: Int, v: Int) { wr16(b, o, v ushr 16); wr16(b, o + 2, v) }
    private fun checksum(b: ByteArray, o: Int, len: Int): Int {
        var s = 0; var i = 0
        while (i + 4 <= len) { s += rd32(b, o + i); i += 4 }
        if (i < len) { var v = 0; var sh = 24; while (i < len) { v = v or ((b[o + i].toInt() and 255) shl sh); sh -= 8; i++ }; s += v }
        return s
    }

    private class GlyfResult(val glyf: ByteArray, val glyfLen: Int, val loca: ByteArray, val numGlyphs: Int, val xMin: IntArray)

    private fun rebuildGlyf(ub: ByteArray, off: Int, len: Int, origLen: Int): GlyfResult {
        val h = Cur(ub, off, off + len)
        h.u16(); val opt = h.u16(); val n = h.u16(); val idxFmt = h.u16()
        val sz = IntArray(7) { h.u32() }
        var p = h.p
        fun cur(s: Int): Cur { if (s < 0 || s > off + len - p) throw Woff2Exception("glyf stream overflow"); val r = Cur(ub, p, p + s); p += s; return r }
        val nContourC = cur(sz[0]); val nPointsC = cur(sz[1]); val flagC = cur(sz[2]); val glyphC = cur(sz[3])
        val compC = cur(sz[4]); val bboxC = cur(sz[5]); val instrC = cur(sz[6])
        val overlapOff = p
        if (opt and 1 != 0 && (n + 7) / 8 > off + len - p) throw Woff2Exception("overlap bitmap overflow")
        val bmLen = 4 * ((n + 31) / 32)
        val bboxBitmapOff = bboxC.p; bboxC.skip(bmLen)

        val out = Out(origLen + 4 * n)
        val locaOff = IntArray(n + 1); val xMin = IntArray(n)
        var xs = IntArray(256); var ys = IntArray(256); var fl = ByteArray(256)
        var tf = ByteArray(512); var tx = ByteArray(512); var ty = ByteArray(512)
        var endPts = IntArray(32)
        for (i in 0 until n) {
            locaOff[i] = out.n
            val nc = (nContourC.u16() shl 16) shr 16
            val hasBbox = (ub[bboxBitmapOff + (i shr 3)].toInt() shr (7 - (i and 7))) and 1 != 0
            if (nc == 0) { if (hasBbox) throw Woff2Exception("bbox on empty glyph"); continue }
            if (nc == -1) {
                if (!hasBbox) throw Woff2Exception("composite without bbox")
                out.u16(0xFFFF)
                out.bytes(ub, bboxC.p, 8); xMin[i] = rd16s(ub, bboxC.p); bboxC.skip(8)
                var more = true; var instr = false
                while (more) {
                    val st = compC.p
                    val f = compC.u16(); compC.u16()
                    compC.skip(if (f and 1 != 0) 4 else 2)
                    if (f and 0x8 != 0) compC.skip(2) else if (f and 0x40 != 0) compC.skip(4) else if (f and 0x80 != 0) compC.skip(8)
                    out.bytes(ub, st, compC.p - st)
                    more = f and 0x20 != 0; if (f and 0x100 != 0) instr = true
                }
                if (instr) {
                    val il = glyphC.u255(); out.u16(il)
                    if (il > instrC.end - instrC.p) throw Woff2Exception("instr underrun")
                    out.bytes(ub, instrC.p, il); instrC.skip(il)
                }
            } else if (nc > 0) {
                if (endPts.size < nc) endPts = IntArray(nc * 2)
                var tot = 0
                for (k in 0 until nc) { tot += nPointsC.u255(); endPts[k] = tot - 1 }
                if (tot > 65535 + 1) throw Woff2Exception("too many points")
                if (xs.size < tot) { xs = IntArray(tot * 2); ys = IntArray(tot * 2); fl = ByteArray(tot * 2) }
                var x = 0; var y = 0
                for (k in 0 until tot) {
                    val f = flagC.u8()
                    val on = f ushr 7 == 0; val fv = f and 0x7F
                    var dx: Int; var dy: Int
                    fun sg(fb: Int, v: Int) = if (fb and 1 != 0) v else -v
                    when {
                        fv < 10 -> { val b0 = glyphC.u8(); dx = 0; dy = sg(fv, ((fv and 14) shl 7) + b0) }
                        fv < 20 -> { val b0 = glyphC.u8(); dx = sg(fv, (((fv - 10) and 14) shl 7) + b0); dy = 0 }
                        fv < 84 -> { val b0 = fv - 20; val b1 = glyphC.u8()
                            dx = sg(fv, 1 + (b0 and 0x30) + (b1 shr 4)); dy = sg(fv shr 1, 1 + ((b0 and 0xC) shl 2) + (b1 and 15)) }
                        fv < 120 -> { val b0 = fv - 84; val b1 = glyphC.u8(); val b2 = glyphC.u8()
                            dx = sg(fv, 1 + ((b0 / 12) shl 8) + b1); dy = sg(fv shr 1, 1 + (((b0 % 12) shr 2) shl 8) + b2) }
                        fv < 124 -> { val b1 = glyphC.u8(); val b2 = glyphC.u8(); val b3 = glyphC.u8()
                            dx = sg(fv, (b1 shl 4) + (b2 shr 4)); dy = sg(fv shr 1, ((b2 and 15) shl 8) + b3) }
                        else -> { val b1 = glyphC.u8(); val b2 = glyphC.u8(); val b3 = glyphC.u8(); val b4 = glyphC.u8()
                            dx = sg(fv, (b1 shl 8) + b2); dy = sg(fv shr 1, (b3 shl 8) + b4) }
                    }
                    x += dx; y += dy; xs[k] = x; ys[k] = y; fl[k] = if (on) 1 else 0
                }
                val il = glyphC.u255()
                if (il > instrC.end - instrC.p) throw Woff2Exception("instr underrun")
                var bx0: Int; var by0: Int; var bx1: Int; var by1: Int
                if (hasBbox) { bx0 = rd16s(ub, bboxC.p); by0 = rd16s(ub, bboxC.p + 2); bx1 = rd16s(ub, bboxC.p + 4); by1 = rd16s(ub, bboxC.p + 6); bboxC.skip(8) }
                else { bx0 = xs[0]; bx1 = xs[0]; by0 = ys[0]; by1 = ys[0]
                    for (k in 1 until tot) { if (xs[k] < bx0) bx0 = xs[k]; if (xs[k] > bx1) bx1 = xs[k]; if (ys[k] < by0) by0 = ys[k]; if (ys[k] > by1) by1 = ys[k] } }
                xMin[i] = bx0
                out.u16(nc); out.u16(bx0); out.u16(by0); out.u16(bx1); out.u16(by1)
                for (k in 0 until nc) out.u16(endPts[k])
                out.u16(il); out.bytes(ub, instrC.p, il); instrC.skip(il)
                if (tf.size < tot * 2) { tf = ByteArray(tot * 2); tx = ByteArray(tot * 2); ty = ByteArray(tot * 2) }
                val overlap = opt and 1 != 0 && (ub[overlapOff + (i shr 3)].toInt() shr (7 - (i and 7))) and 1 != 0
                var nf = 0; var nx = 0; var ny = 0; var last = -1; var rep = 0; var flagPos = 0; var px = 0; var py = 0
                for (k in 0 until tot) {
                    var f = fl[k].toInt(); if (k == 0 && overlap) f = f or 0x40
                    val dx = xs[k] - px; val dy = ys[k] - py; px = xs[k]; py = ys[k]
                    if (dx == 0) f = f or 0x10
                    else if (dx > -256 && dx < 256) { f = f or 0x02 or (if (dx > 0) 0x10 else 0); tx[nx++] = Math.abs(dx).toByte() }
                    else { tx[nx++] = (dx shr 8).toByte(); tx[nx++] = dx.toByte() }
                    if (dy == 0) f = f or 0x20
                    else if (dy > -256 && dy < 256) { f = f or 0x04 or (if (dy > 0) 0x20 else 0); ty[ny++] = Math.abs(dy).toByte() }
                    else { ty[ny++] = (dy shr 8).toByte(); ty[ny++] = dy.toByte() }
                    if (f == last && rep != 255) {
                        tf[flagPos] = (tf[flagPos].toInt() or 8).toByte()
                        if (rep == 0) tf[nf++] = 1 else tf[nf - 1] = (rep + 1).toByte()
                        rep++
                    } else { flagPos = nf; tf[nf++] = f.toByte(); rep = 0 }
                    last = f
                }
                out.bytes(tf, 0, nf); out.bytes(tx, 0, nx); out.bytes(ty, 0, ny)
            } else throw Woff2Exception("bad numberOfContours")
            out.pad4()
        }
        locaOff[n] = out.n
        val loca: ByteArray
        if (idxFmt == 0) { loca = ByteArray(2 * (n + 1)); for (i in 0..n) wr16(loca, 2 * i, locaOff[i] / 2) }
        else { loca = ByteArray(4 * (n + 1)); for (i in 0..n) wr32(loca, 4 * i, locaOff[i]) }
        return GlyfResult(out.a, out.n, loca, n, xMin)
    }

    private fun rebuildHmtx(ub: ByteArray, off: Int, len: Int, numH: Int, numGlyphs: Int, xMin: IntArray): ByteArray {
        if (numH > numGlyphs || numH < 1) throw Woff2Exception("bad numberOfHMetrics")
        val c = Cur(ub, off, off + len)
        val f = c.u8()
        val noProp = f and 1 != 0; val noMono = f and 2 != 0
        val out = ByteArray(4 * numH + 2 * (numGlyphs - numH))
        val adv = Cur(ub, c.p, c.end); c.skip(2 * numH)
        for (i in 0 until numH) wr16(out, 4 * i, adv.u16())
        for (i in 0 until numH) wr16(out, 4 * i + 2, if (noProp) xMin[i] else c.u16())
        for (i in numH until numGlyphs) wr16(out, 4 * numH + 2 * (i - numH), if (noMono) xMin[i] else c.u16())
        return out
    }
}
