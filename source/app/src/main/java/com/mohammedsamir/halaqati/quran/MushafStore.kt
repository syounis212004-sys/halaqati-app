package com.mohammedsamir.halaqati.quran

import android.content.Context
import android.graphics.Typeface
import androidx.compose.ui.text.font.FontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * Page data and glyph fonts of the interactive mushaf.
 * - The Quran text only ever comes from the Quran.com API (never typed in this repo). Its data is cached on the device for at most
 *   one week (Quran Foundation terms); the page fonts are not text and are kept without a limit so a page read once works offline.
 * - Fonts are the King Fahd Complex QCF V2 page fonts, served by the Quran Foundation CDN (TrueType, because Android cannot read WOFF2).
 */
class MushafStore internal constructor(
    rootDir: File,
    /** Network seam (url, timeout in ms) -> body; replaced by a fake in the tests. */
    private val fetch: (String, Int) -> ByteArray = { url, timeout -> httpGet(url, timeout) },
) {
    private constructor(context: Context) : this(File(context.applicationContext.filesDir, "mushaf"))
    private val root = rootDir.apply { mkdirs() }
    private val pagesDir = File(root, "pages-v2").apply { mkdirs() }
    private val fontsDir = File(root, "fonts-v1").apply { mkdirs() }
    private val pages = ConcurrentHashMap<Int, MushafEngine.PageData>()
    private val fonts = ConcurrentHashMap<String, FontFamily>()
    private val fontLock = Mutex()
    private var basmala: List<String>? = null

    suspend fun loadPage(page: Int): Result<MushafEngine.PageData> = withContext(Dispatchers.IO) {
        val p = MushafEngine.clampPage(page)
        pages[p]?.let { return@withContext Result.success(it) }
        runCatching { buildPage(p).also { pages[p] = it } }
            .recoverCatching { throw MushafLoadException(if (it is java.net.SocketTimeoutException) "انتهت مهلة تحميل الصفحة" else "تعذر تحميل بيانات الصفحة", it) }
    }

    private suspend fun buildPage(p: Int): MushafEngine.PageData = coroutineScope {
        run {
            // a verse that runs from one printed page to the next is listed by only one of the two API pages, so a printed page is built from
            // its own API page plus both neighbours; a neighbour that cannot be read fails the page (never a half-drawn page)
            val wanted = listOf(p - 1, p, p + 1).filter { it in 1..MushafEngine.TOTAL_PAGES }
            val verses = coroutineScope { wanted.map { n -> async { rawPage(n) } }.map { it.await() } }.flatten()
            val data = MushafEngine.normalizePage(p, verses)
            require(data.items.isNotEmpty()) { "EMPTY_PAGE_DATA" }
            data
        }
    }

    private fun rawPage(n: Int): List<MushafEngine.ApiVerse> {
        val file = File(pagesDir, "$n.json")
        val now = System.currentTimeMillis()
        var stale: List<MushafEngine.ApiVerse>? = null
        if (file.exists()) {
            val saved = runCatching { JSONObject(file.readText()) }.getOrNull()
            val cached = runCatching { parseVerses(saved!!.getJSONArray("verses")) }.getOrNull().orEmpty()
            if (cached.isEmpty()) file.delete()
            else if (now - saved!!.optLong("at", 0L) <= DATA_MAX_AGE_MS) return cached
            else stale = cached
        }
        try {
            val arr = JSONObject(fetch(apiUrl(n), 12_000).toString(Charsets.UTF_8)).getJSONArray("verses")
            val parsed = parseVerses(arr)
            require(parsed.isNotEmpty()) { "EMPTY_PAGE_DATA" }
            runCatching {
                val tmp = File(pagesDir, "$n.tmp")
                tmp.writeText(JSONObject().put("at", now).put("verses", arr).toString())
                tmp.renameTo(file)
            }
            return parsed
        } catch (e: java.io.IOException) {
            // offline: a copy older than a week is still the page the user already downloaded - it is only ever used when no fresh one can be had
            return stale ?: throw e
        }
    }

    private fun parseVerses(arr: JSONArray): List<MushafEngine.ApiVerse> = (0 until arr.length()).map { i ->
        val v = arr.getJSONObject(i)
        val ws = v.optJSONArray("words") ?: JSONArray()
        MushafEngine.ApiVerse(
            key = v.optString("verse_key"),
            juz = v.optInt("juz_number", 0).takeIf { it > 0 },
            words = (0 until ws.length()).map { j ->
                val w = ws.getJSONObject(j)
                MushafEngine.ApiWord(
                    isEnd = w.optString("char_type_name") == "end",
                    position = w.optInt("position", 0),
                    pageNumber = w.optInt("page_number", 0),
                    v2Page = w.optInt("v2_page", 0),
                    lineNumber = w.optInt("line_number", 0),
                    code = w.optString("code_v2"),
                    text = w.optString("text_uthmani"),
                )
            },
        )
    }

    /** QCF V2 font of one printed page (null when it cannot be had). */
    suspend fun pageFont(fontPage: Int): FontFamily? = font("p$fontPage", "$PAGE_FONT_BASE/p$fontPage.ttf")

    /** The surah-name banner glyphs ("surah" followed by the three-digit surah number). */
    suspend fun surahNamesFont(): FontFamily? = font("sura_names", SURAH_NAMES_URL)

    private suspend fun font(key: String, url: String): FontFamily? = withContext(Dispatchers.IO) {
        fonts[key]?.let { return@withContext it }
        fontLock.withLock {
            fonts[key]?.let { return@withLock it }
            runCatching { FontFamily(Typeface.createFromFile(ensureFontFile(key, url))).also { fonts[key] = it } }.getOrNull()
        }
    }

    /** The font file on disk, downloaded first when missing (fonts are not text and stay without a limit). */
    private fun ensureFontFile(key: String, url: String): File {
        val file = File(fontsDir, "$key.ttf")
        if (!file.exists() || file.length() < 1024) {
            val bytes = fetch(url, 30_000)
            require(bytes.size >= 1024) { "bad font download" }
            val tmp = File(fontsDir, "$key.tmp")
            tmp.writeBytes(bytes)
            require(tmp.renameTo(file)) { "bad font download" }
        }
        return file
    }

    /** The four glyphs of the basmala (from the first ayah of Al-Fatiha, drawn with page 1's font). */
    suspend fun basmalaGlyphs(): List<String> {
        basmala?.let { return it }
        val d = loadPage(1).getOrNull() ?: return emptyList()
        return d.words.filter { it.ayahKey == "1:1" }.take(4).map { it.glyph }.also { basmala = it }
    }

    /** Loads the page and every font it needs; a missing font fails the whole page rather than drawing wrong glyphs. */
    suspend fun preparePage(page: Int): Result<PreparedPage> {
        val data = loadPage(page).getOrElse { return Result.failure(it) }
        val lines = MushafEngine.buildPageLines(data)
        val families = HashMap<Int, FontFamily>()
        val hasBasmala = lines.any { it.kind == MushafEngine.LineKind.Basmala }
        for (n in fontPagesNeeded(data, lines)) {
            families[n] = pageFont(n) ?: return Result.failure(MushafLoadException("تعذر تحميل خط الصفحة. اتصل بالإنترنت مرة واحدة، أو نزّل المصحف للاستخدام بدون إنترنت.", null))
        }
        val names = if (lines.any { it.kind == MushafEngine.LineKind.Banner }) surahNamesFont() else null
        val basmalaGlyphs = if (hasBasmala) basmalaGlyphs() else emptyList()
        return Result.success(PreparedPage(data, lines, families, names, basmalaGlyphs))
    }

    private fun fontPagesNeeded(data: MushafEngine.PageData, lines: List<MushafEngine.Line>): Set<Int> {
        val needed = data.items.map { it.fontPage }.toMutableSet()
        if (lines.any { it.kind == MushafEngine.LineKind.Basmala }) needed += 1
        return needed
    }

    // ---------------------------------------------------------------- offline pack

    /** [ok] pages are fully on the device; [failed] could not be fetched (no network, server refused). */
    data class DownloadOutcome(val ok: Int, val failed: List<Int>)
    data class PackStatus(val pages: Int, val bytes: Long)

    /** True when the data and font of this printed page are on the device, so it opens without a network. */
    fun isPageDownloaded(page: Int): Boolean =
        File(pagesDir, "$page.json").exists() && File(fontsDir, "p$page.ttf").length() >= 1024

    fun packStatus(): PackStatus {
        val ready = (1..MushafEngine.TOTAL_PAGES).count { isPageDownloaded(it) }
        val bytes = listOf(pagesDir, fontsDir).sumOf { d -> d.listFiles()?.sumOf { it.length() } ?: 0L }
        return PackStatus(ready, bytes)
    }

    /**
     * Downloads data and fonts of the given printed pages so they open offline. Three pages at a time; a page that fails is reported
     * and the rest carry on. Cancelling the calling coroutine stops it, and what was finished stays.
     */
    suspend fun downloadPages(pageList: List<Int>, onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }): DownloadOutcome = withContext(Dispatchers.IO) {
        val todo = pageList.map { MushafEngine.clampPage(it) }.distinct()
        val failed = java.util.concurrent.CopyOnWriteArrayList<Int>()
        val done = java.util.concurrent.atomic.AtomicInteger(0)
        onProgress(0, todo.size)
        for (batch in todo.chunked(3)) {
            coroutineScope {
                batch.map { p ->
                    async {
                        runCatching {
                            val data = buildPage(p)
                            val lines = MushafEngine.buildPageLines(data)
                            for (n in fontPagesNeeded(data, lines)) ensureFontFile("p$n", "$PAGE_FONT_BASE/p$n.ttf")
                            if (lines.any { it.kind == MushafEngine.LineKind.Banner }) ensureFontFile("sura_names", SURAH_NAMES_URL)
                        }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it; failed += p }
                        onProgress(done.incrementAndGet(), todo.size)
                    }
                }.forEach { it.await() }
            }
        }
        DownloadOutcome(todo.size - failed.size, failed.sorted())
    }

    /** Removes every downloaded page and font; pages are fetched again the next time they are opened with a network. */
    suspend fun deletePack() = withContext(Dispatchers.IO) {
        fontLock.withLock {
            pagesDir.listFiles()?.forEach { it.delete() }
            fontsDir.listFiles()?.forEach { it.delete() }
            pages.clear(); fonts.clear(); basmala = null
        }
    }

    class PreparedPage(
        val data: MushafEngine.PageData,
        val lines: List<MushafEngine.Line>,
        val families: Map<Int, FontFamily>,
        val surahNames: FontFamily?,
        val basmala: List<String>,
    )

    class MushafLoadException(message: String, cause: Throwable?) : Exception(message, cause)

    companion object {
        private const val DATA_MAX_AGE_MS = 7L * 24 * 3600 * 1000
        private const val PAGE_FONT_BASE = "https://verses.quran.foundation/fonts/quran/hafs/v2/ttf"
        private const val SURAH_NAMES_URL = "https://verses.quran.foundation/fonts/quran/surah-names/v1/sura_names.ttf"
        private fun apiUrl(n: Int) = "https://api.quran.com/api/v4/verses/by_page/$n?words=true&word_fields=code_v2,text_uthmani,page_number,line_number&per_page=50"

        internal fun httpGet(url: String, timeoutMs: Int): ByteArray {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 12_000; conn.readTimeout = timeoutMs
            conn.setRequestProperty("Accept", "application/json")
            try {
                if (conn.responseCode !in 200..299) throw java.io.IOException("HTTP ${conn.responseCode}")
                return conn.inputStream.use { it.readBytes() }
            } finally { conn.disconnect() }
        }

        @Volatile private var instance: MushafStore? = null
        fun get(context: Context): MushafStore = instance ?: synchronized(this) { instance ?: MushafStore(context).also { instance = it } }
    }
}
