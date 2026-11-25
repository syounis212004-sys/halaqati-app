package com.mohammedsamir.halaqati.quran.dk

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Build
import android.os.Debug
import android.util.Log
import android.util.LruCache
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Performance harness for the experimental DigitalKhatt viewer. It runs on a real ART runtime (an emulator in CI) and measures; it only asserts
 * gross failures (a page that cannot load/render, a blank render, a leak larger than the generous bounds below). Every number goes to
 * files/dk-perf-results.json in the app's private storage (pulled by the workflow with `run-as`) and to logcat tag DkPerf.
 *
 * The pack (assets/dk) is generated at build time and never committed; without it the test is skipped via assumption, not failed silently as green data.
 * One @Test method on purpose: JUnit orders methods nondeterministically and "cold" numbers must come first in a fresh process.
 */
class DkPerfTest {
    private val ctx: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val widthPx = 1080
    private val pages = 604
    private val passes = 5

    // ---- leak bounds (generous on purpose), measured on a forced-GC'd heap between the end of pass 2 and the end of pass 5 ----
    // A real leak here is a per-page object kept alive: a page bitmap is ~11 MB (1080 x ~2600 ARGB_8888), a parsed DkPage ~0.2-0.5 MB. Three extra passes
    // (1812 pages) would then grow by gigabytes / hundreds of MB. The path LruCache is bounded by entry count (6000) and is full after pass 1, so steady state
    // is flat. Allocator fragmentation and GC timing noise on a 5-pass run is a few MB, so 32 MB (Java) and 96 MB (native) leave a wide margin for noise
    // while still catching any leak above ~18 KB (Java) / ~53 KB (native) per page. Smaller leaks are reported in the JSON, not asserted.
    private val javaGrowthLimitBytes = 32L * 1024 * 1024
    private val nativeGrowthLimitBytes = 96L * 1024 * 1024

    private class PageTimes(val loadMs: DoubleArray, val allocMs: DoubleArray, val drawMs: DoubleArray, val rectsMs: DoubleArray, val totalMs: DoubleArray, val words: IntArray)

    @Test
    fun measureEverything() {
        val ok = try { ctx.assets.open("dk/ids.bin").close(); true } catch (_: Exception) { false }
        org.junit.Assume.assumeTrue("DK pack missing from the installed APK (assets/dk)", ok)

        val root = JSONObject()
        root.put("schema", 1)
        root.put("device", deviceInfo())
        root.put("limits", limits())
        root.put("install", installInfo())
        val failures = JSONArray()
        root.put("failures", failures)

        // ---- (a) glyph load: first call in the process (cold: JIT interpreter, cold page cache for the asset) then fresh instances (warm) ----
        gc()
        val memBeforeGlyphs = mem()
        val first0 = DkData(ctx)
        val coldMs = timeMs { first0.loadGlyphs() }
        val glyphJson = JSONObject().put("cold_ms", r(coldMs))
        val warm = JSONArray()
        var data = first0
        for (i in 1..3) {
            val d = DkData(ctx)
            warm.put(r(timeMs { d.loadGlyphs() }))
            data = d
        }
        gc()
        val memAfterGlyphs = mem()
        glyphJson.put("warm_ms_each", warm)
        glyphJson.put("heap_after_vs_before", memDelta(memBeforeGlyphs, memAfterGlyphs))
        glyphJson.put("note", "cold = first DkData.loadGlyphs() in this process; warm = new DkData instances afterwards (asset in page cache, code JIT-warmed). Memory delta is the retained outline set of one instance (older instances were released before the second reading).")
        root.put("glyph_load", glyphJson)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF000000.toInt(); style = Paint.Style.FILL }
        val w = widthPx.toFloat()
        val ids = try { DkPack.idsOf(ctx, 1); true } catch (_: Exception) { false }

        // ---- (b)(c)(d) passes over all pages ----
        val passJson = JSONArray()
        val memAfterPass = ArrayList<Mem>()
        val allPass = ArrayList<PageTimes>()
        val cacheAfterPass = JSONArray()
        gc()
        val memBeforePasses = mem()
        var sawInk = 0; var inkChecks = 0
        val cacheStart = cacheStats(data)
        for (pass in 1..passes) {
            val t = PageTimes(DoubleArray(pages), DoubleArray(pages), DoubleArray(pages), DoubleArray(pages), DoubleArray(pages), IntArray(pages))
            val passStart = System.nanoTime()
            val cBefore = cacheStats(data)
            for (n in 1..pages) {
                try {
                    val t0 = System.nanoTime()
                    val dk = data.loadPage(n)
                    val t1 = System.nanoTime()
                    val pitch = DkRender.lineHeight(w)
                    val hPx = (pitch * dk.lines.size).toInt().coerceAtLeast(1)
                    val bmp = Bitmap.createBitmap(widthPx, hPx, Bitmap.Config.ARGB_8888)
                    val t2 = System.nanoTime()
                    DkRender.draw(Canvas(bmp), data, dk, w, pitch, paint)
                    val t3 = System.nanoTime()
                    val rects = DkRender.wordRects(dk, w, pitch)
                    val t4 = System.nanoTime()
                    if (pass == 1 && n % 50 == 1) { inkChecks++; if (hasInk(bmp)) sawInk++ else failures.put("page $n rendered blank") }
                    bmp.recycle()
                    if (rects.isEmpty()) failures.put("page $n has no word rects")
                    t.loadMs[n - 1] = ms(t0, t1); t.allocMs[n - 1] = ms(t1, t2); t.drawMs[n - 1] = ms(t2, t3); t.rectsMs[n - 1] = ms(t3, t4)
                    t.totalMs[n - 1] = ms(t0, t4); t.words[n - 1] = rects.size
                } catch (e: Throwable) {
                    failures.put("pass $pass page $n: ${e.javaClass.simpleName}: ${e.message}")
                }
            }
            val wallS = (System.nanoTime() - passStart) / 1e9
            allPass.add(t)
            gc()
            val m = mem(); memAfterPass.add(m)
            val cNow = cacheStats(data)
            val pj = JSONObject().put("pass", pass).put("wall_s", r(wallS))
                .put("load_ms", stats(t.loadMs)).put("alloc_bitmap_ms", stats(t.allocMs)).put("draw_ms", stats(t.drawMs))
                .put("word_rects_ms", stats(t.rectsMs)).put("total_ms", stats(t.totalMs))
                .put("sum_total_ms", r(t.totalMs.sum())).put("sum_draw_ms", r(t.drawMs.sum())).put("sum_rects_ms", r(t.rectsMs.sum()))
                .put("memory_after_gc", m.toJson())
                .put("path_cache_delta", JSONObject().put("hits", cNow.hits - cBefore.hits).put("misses", cNow.misses - cBefore.misses).put("evictions", cNow.evictions - cBefore.evictions))
            passJson.put(pj)
            cacheAfterPass.put(JSONObject().put("pass", pass).put("entries", cNow.entries).put("hits", cNow.hits).put("misses", cNow.misses).put("evictions", cNow.evictions))
            Log.i(TAG, "pass $pass done in ${r(wallS)} s, draw median ${stats(t.drawMs).getDouble("median")} ms")
        }
        root.put("passes", passJson)
        val first = allPass[0]
        // pages 1..604 detail for pass 1 (cold path cache) as arrays: slowest 10 pages by total time, so regressions can be located
        root.put("pass1_cold_cache", JSONObject()
            .put("load_ms", stats(first.loadMs)).put("alloc_bitmap_ms", stats(first.allocMs)).put("draw_ms", stats(first.drawMs))
            .put("word_rects_ms", stats(first.rectsMs)).put("total_ms", stats(first.totalMs))
            .put("slowest_pages_by_total", slowest(first.totalMs, 10))
            .put("note", "pass 1 starts with an empty path cache (glyph paths are built on first use); later passes mostly hit the cache"))
        val warmAgg = DoubleArray((passes - 1) * pages); val warmDraw = DoubleArray(warmAgg.size); val warmLoad = DoubleArray(warmAgg.size); val warmRects = DoubleArray(warmAgg.size); val warmAlloc = DoubleArray(warmAgg.size)
        for (p in 1 until passes) for (i in 0 until pages) {
            val k = (p - 1) * pages + i
            warmAgg[k] = allPass[p].totalMs[i]; warmDraw[k] = allPass[p].drawMs[i]; warmLoad[k] = allPass[p].loadMs[i]; warmRects[k] = allPass[p].rectsMs[i]; warmAlloc[k] = allPass[p].allocMs[i]
        }
        root.put("passes_2_to_5_warm", JSONObject()
            .put("load_ms", stats(warmLoad)).put("alloc_bitmap_ms", stats(warmAlloc)).put("draw_ms", stats(warmDraw))
            .put("word_rects_ms", stats(warmRects)).put("total_ms", stats(warmAgg)))
        root.put("word_rect_counts", JSONObject().put("min", first.words.min()).put("max", first.words.max()).put("sum", first.words.sum()))

        // words vs ids (informational; the app joins them in rectsById)
        if (ids) {
            val mism = JSONArray()
            for (n in 1..pages) {
                try { val dk = data.loadPage(n); val rc = DkRender.wordRects(dk, w, DkRender.lineHeight(w)).size; val ic = DkPack.idsOf(ctx, n).size; if (rc != ic) mism.put(JSONObject().put("page", n).put("rects", rc).put("ids", ic)) } catch (_: Throwable) {}
            }
            root.put("rect_vs_id_count_mismatch_pages", mism)
        }

        // ---- (d) memory / leak ----
        val base = memAfterPass[1]; val end = memAfterPass[passes - 1]
        val javaGrowth = end.javaUsed - base.javaUsed
        val nativeGrowth = end.nativeAlloc - base.nativeAlloc
        val leak = JSONObject()
            .put("before_all_passes_after_gc", memBeforePasses.toJson())
            .put("after_each_pass_after_gc", JSONArray().also { a -> memAfterPass.forEachIndexed { i, m -> a.put(JSONObject().put("pass", i + 1).put("mem", m.toJson())) } })
            .put("growth_pass2_to_pass5_java_bytes", javaGrowth).put("growth_pass2_to_pass5_native_bytes", nativeGrowth)
            .put("growth_pass1_to_pass5_java_bytes", end.javaUsed - memAfterPass[0].javaUsed).put("growth_pass1_to_pass5_native_bytes", end.nativeAlloc - memAfterPass[0].nativeAlloc)
            .put("growth_pass2_to_pass5_pss_kb", end.pssKb - base.pssKb)
            .put("java_limit_bytes", javaGrowthLimitBytes).put("native_limit_bytes", nativeGrowthLimitBytes)
            .put("whole_run_java_delta_vs_start", memDelta(memBeforePasses, end))
            .put("method", "System.gc() x3 + runFinalization + settle sleep before every sample; Java = Runtime total-free, native = Debug.getNativeHeapAllocatedSize, PSS = Debug.getMemoryInfo. Baseline is the end of pass 2 so the glyph-path LruCache fill (pass 1) is excluded.")
        root.put("memory", leak)
        root.put("path_cache", JSONObject().put("at_start", cacheStart.toJson()).put("after_each_pass", cacheAfterPass).put("capacity_entries", 6000)
            .put("note", "LruCache hit/miss/eviction counters read by reflection from DkData.pathCache; entries = DkData.cachedPaths. Unavailable fields are reported as -1."))
        root.put("path_cache_probe", pathProbe(data))

        root.put("ink_checks", JSONObject().put("checked", inkChecks).put("with_ink", sawInk))
        root.put("passed_assertions", JSONObject().put("all_pages_rendered", failures.length() == 0).put("java_growth_ok", javaGrowth < javaGrowthLimitBytes).put("native_growth_ok", nativeGrowth < nativeGrowthLimitBytes))

        val out = File(ctx.filesDir, "dk-perf-results.json")
        out.writeText(root.toString(2))
        Log.i(TAG, "RESULT_FILE ${out.absolutePath}")
        root.toString().chunked(3000).forEachIndexed { i, s -> Log.i(TAG, "JSON[$i] $s") }

        assertEquals("pages that failed to load or render: $failures", 0, failures.length())
        assertTrue("every sampled page must contain ink", inkChecks > 0 && sawInk == inkChecks)
        assertTrue("Java heap grew by $javaGrowth B over passes 2..5 (limit $javaGrowthLimitBytes)", javaGrowth < javaGrowthLimitBytes)
        assertTrue("native heap grew by $nativeGrowth B over passes 2..5 (limit $nativeGrowthLimitBytes)", nativeGrowth < nativeGrowthLimitBytes)
    }

    // ---------------- helpers ----------------
    private fun ms(a: Long, b: Long) = (b - a) / 1e6
    private fun r(v: Double) = Math.round(v * 1000.0) / 1000.0
    private inline fun timeMs(f: () -> Unit): Double { val t = System.nanoTime(); f(); return (System.nanoTime() - t) / 1e6 }

    private fun stats(a: DoubleArray): JSONObject {
        val s = a.copyOf(); s.sort()
        fun pct(p: Double): Double = s[(Math.ceil(p * s.size).toInt() - 1).coerceIn(0, s.size - 1)]
        return JSONObject().put("n", s.size).put("min", r(s.first())).put("median", r(pct(0.5))).put("p95", r(pct(0.95))).put("max", r(s.last())).put("mean", r(s.average())).put("sum", r(s.sum()))
    }

    private fun slowest(a: DoubleArray, k: Int): JSONArray {
        val idx = a.indices.sortedByDescending { a[it] }.take(k)
        return JSONArray().also { j -> idx.forEach { j.put(JSONObject().put("page", it + 1).put("total_ms", r(a[it]))) } }
    }

    private fun hasInk(b: Bitmap): Boolean {
        val px = IntArray(b.width)
        var y = 0
        while (y < b.height) { b.getPixels(px, 0, b.width, 0, y, b.width, 1); if (px.any { it ushr 24 != 0 }) return true; y += 4 }
        return false
    }

    private class Mem(val javaUsed: Long, val javaTotal: Long, val nativeAlloc: Long, val nativeSize: Long, val pssKb: Int, val graphicsKb: Int, val nativePssKb: Int) {
        fun toJson() = JSONObject().put("java_used_bytes", javaUsed).put("java_total_bytes", javaTotal).put("native_allocated_bytes", nativeAlloc).put("native_heap_size_bytes", nativeSize)
            .put("pss_total_kb", pssKb).put("pss_graphics_kb", graphicsKb).put("pss_native_kb", nativePssKb)
    }

    private fun gc() {
        repeat(3) { System.gc(); Runtime.getRuntime().runFinalization(); Thread.sleep(150) }
    }

    private fun mem(): Mem {
        val rt = Runtime.getRuntime()
        val mi = Debug.MemoryInfo(); Debug.getMemoryInfo(mi)
        val s = mi.memoryStats
        return Mem(rt.totalMemory() - rt.freeMemory(), rt.totalMemory(), Debug.getNativeHeapAllocatedSize(), Debug.getNativeHeapSize(),
            mi.totalPss, s["summary.graphics"]?.toIntOrNull() ?: -1, s["summary.native-heap"]?.toIntOrNull() ?: -1)
    }

    private fun memDelta(a: Mem, b: Mem) = JSONObject().put("java_bytes", b.javaUsed - a.javaUsed).put("native_bytes", b.nativeAlloc - a.nativeAlloc).put("pss_kb", b.pssKb - a.pssKb)

    private class CacheStats(val entries: Int, val hits: Long, val misses: Long, val evictions: Long) {
        fun toJson() = JSONObject().put("entries", entries).put("hits", hits).put("misses", misses).put("evictions", evictions)
    }

    private fun cacheStats(d: DkData): CacheStats {
        return try {
            val f = DkData::class.java.getDeclaredField("pathCache"); f.isAccessible = true
            val c = f.get(d) as LruCache<*, *>
            CacheStats(d.cachedPaths, c.hitCount().toLong(), c.missCount().toLong(), c.evictionCount().toLong())
        } catch (_: Throwable) { CacheStats(d.cachedPaths, -1, -1, -1) }
    }

    /** Direct micro-probe of the path cache: first call of one glyph (miss: builds the Path) against repeated calls (hits), on a fresh instance. */
    private fun pathProbe(shared: DkData): JSONObject {
        val d = DkData(ctx); d.loadGlyphs()
        val dk = d.loadPage(2)
        val gl = dk.lines.flatMap { it.glyphs }.filter { it.cp != 32 }.take(300)
        val miss = DoubleArray(gl.size); val hit = DoubleArray(gl.size)
        for ((i, g) in gl.withIndex()) { val t = System.nanoTime(); d.path(g.cp, g.lt, g.rt); miss[i] = (System.nanoTime() - t) / 1e3 }
        val s1 = cacheStats(d)
        for ((i, g) in gl.withIndex()) { val t = System.nanoTime(); d.path(g.cp, g.lt, g.rt); hit[i] = (System.nanoTime() - t) / 1e3 }
        val s2 = cacheStats(d)
        return JSONObject().put("glyph_calls", gl.size)
            .put("first_call_us", stats(miss)).put("repeat_call_us", stats(hit))
            .put("entries_after_first", s1.entries).put("hits_added_by_repeat", s2.hits - s1.hits).put("misses_added_by_repeat", s2.misses - s1.misses)
            .put("note", "first_call includes both distinct glyphs (path built) and repeats within the sample; repeat_call is all hits. Units are microseconds.")
    }

    private fun deviceInfo() = JSONObject()
        .put("manufacturer", Build.MANUFACTURER).put("model", Build.MODEL).put("device", Build.DEVICE).put("hardware", Build.HARDWARE)
        .put("fingerprint", Build.FINGERPRINT).put("sdk_int", Build.VERSION.SDK_INT).put("release", Build.VERSION.RELEASE)
        .put("abis", JSONArray(Build.SUPPORTED_ABIS.toList())).put("cpu_count", Runtime.getRuntime().availableProcessors())
        .put("java_max_heap_bytes", Runtime.getRuntime().maxMemory())
        .put("is_emulator_guess", Build.FINGERPRINT.startsWith("generic") || Build.FINGERPRINT.contains("emulator") || Build.HARDWARE.contains("ranchu") || Build.HARDWARE.contains("goldfish") || Build.MODEL.contains("Emulator") || Build.MODEL.contains("sdk_gphone"))

    private fun limits() = JSONArray(listOf(
        "Measured on an Android emulator (x86_64 system image, CPU virtualised with KVM on a shared CI VM), not on a phone: absolute milliseconds are NOT predictions for a user device.",
        "The ARM phone CPU, its big.LITTLE scheduling, thermal throttling and memory bandwidth are not modelled; x86_64 ART code generation differs from arm64.",
        "Canvas drawing here is software (Skia CPU raster into a Bitmap) and the emulator has no real GPU; the app also draws into a Bitmap on the CPU, but on screen the final bitmap upload and composition are not measured.",
        "Shared CI runners are noisy (other jobs, variable CPU frequency): compare medians and p95, treat max as an outlier, and only trust relative changes between runs of the same workflow.",
        "No UI is involved: Compose recomposition, scrolling, touch hit-testing and the first frame after a page swipe are not measured; this covers data load, geometry and rasterisation only.",
        "Memory numbers come from Debug/Runtime counters inside one process after forced GC; the emulator's lowmemorykiller and a phone's memory pressure behaviour are not reproduced.",
        "Dev build: the debug APK is debuggable, unoptimised by R8 and has no baseline profile; release builds are typically faster. Installed-size figures for the release APK come from the workflow, not from this test.",
        "Bitmap size is 1080 px wide with the layout's own line pitch (lineHeight(w)); the app may use a smaller pitch (never larger), so real bitmaps can be a bit shorter."
    ))

    private fun installInfo(): JSONObject {
        val ai = ctx.applicationInfo
        val j = JSONObject().put("package", ctx.packageName).put("apk_path", ai.sourceDir).put("apk_bytes", File(ai.sourceDir).length())
        var packBytes = 0L; var files = 0
        try {
            for (name in ctx.assets.list("dk") ?: emptyArray()) {
                ctx.assets.open("dk/$name").use { s -> val buf = ByteArray(64 * 1024); while (true) { val n = s.read(buf); if (n <= 0) break; packBytes += n } }
                files++
            }
        } catch (_: Throwable) {}
        j.put("dk_pack_files", files).put("dk_pack_uncompressed_bytes_as_read_from_assets", packBytes)
        j.put("data_dir_bytes", dirSize(File(ai.dataDir)))
        return j
    }

    private fun dirSize(f: File): Long = if (f.isFile) f.length() else (f.listFiles()?.sumOf { dirSize(it) } ?: 0L)

    companion object { const val TAG = "DkPerf" }
}
