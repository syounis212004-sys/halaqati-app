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
class MushafStore private constructor(context: Context) {
    private val root = File(context.applicationContext.filesDir, "mushaf").apply { mkdirs() }
    private val pagesDir = File(root, "pages-v2").apply { mkdirs() }
    private val fontsDir = File(root, "fonts-v1").apply { mkdirs() }
    private val pages = ConcurrentHashMap<Int, MushafEngine.PageData>()
    private val fonts = ConcurrentHashMap<String, FontFamily>()
    private val fontLock = Mutex()
    private var basmala: List<String>? = null

    suspend fun loadPage(page: Int): Result<MushafEngine.PageData> = withContext(Dispatchers.IO) {
        val p = MushafEngine.clampPage(page)
        pages[p]?.let { return@withContext Result.success(it) }
        runCatching {
            // a verse that runs from one printed page to the next is listed by only one of the two API pages, so a printed page is built from
            // its own API page plus both neighbours; a neighbour that cannot be read fails the page (never a half-drawn page)
            val wanted = listOf(p - 1, p, p + 1).filter { it in 1..MushafEngine.TOTAL_PAGES }
            val verses = coroutineScope { wanted.map { n -> async { rawPage(n) } }.map { it.await() } }.flatten()
            val data = MushafEngine.normalizePage(p, verses)
            require(data.items.isNotEmpty()) { "EMPTY_PAGE_DATA" }
            pages[p] = data
            data
        }.recoverCatching { throw MushafLoadException(if (it is java.net.SocketTimeoutException) "انتهت مهلة تحميل الصفحة" else "تعذر تحميل بيانات الصفحة", it) }
    }

    private fun rawPage(n: Int): List<MushafEngine.ApiVerse> {
        val file = File(pagesDir, "$n.json")
        val now = System.currentTimeMillis()
        if (file.exists()) {
            runCatching {
                val saved = JSONObject(file.readText())
                if (now - saved.optLong("at", 0L) <= DATA_MAX_AGE_MS) {
                    val v = parseVerses(saved.getJSONArray("verses"))
                    if (v.isNotEmpty()) return v
                }
            }
            file.delete()
        }
        val conn = URL(apiUrl(n)).openConnection() as HttpURLConnection
        conn.connectTimeout = 12_000; conn.readTimeout = 12_000
        conn.setRequestProperty("Accept", "application/json")
        try {
            if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val arr = JSONObject(body).getJSONArray("verses")
            val parsed = parseVerses(arr)
            require(parsed.isNotEmpty()) { "EMPTY_PAGE_DATA" }
            runCatching {
                val tmp = File(pagesDir, "$n.tmp")
                tmp.writeText(JSONObject().put("at", now).put("verses", arr).toString())
                tmp.renameTo(file)
            }
            return parsed
        } finally { conn.disconnect() }
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
            runCatching {
                val file = File(fontsDir, "$key.ttf")
                if (!file.exists() || file.length() < 1024) {
                    val conn = URL(url).openConnection() as HttpURLConnection
                    conn.connectTimeout = 15_000; conn.readTimeout = 30_000
                    try {
                        if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
                        val tmp = File(fontsDir, "$key.tmp")
                        conn.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
                        require(tmp.length() >= 1024 && tmp.renameTo(file)) { "bad font download" }
                    } finally { conn.disconnect() }
                }
                FontFamily(Typeface.createFromFile(file)).also { fonts[key] = it }
            }.getOrNull()
        }
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
        val pagesNeeded = data.items.map { it.fontPage }.toMutableSet()
        val hasBasmala = lines.any { it.kind == MushafEngine.LineKind.Basmala }
        if (hasBasmala) pagesNeeded += 1
        for (n in pagesNeeded) {
            families[n] = pageFont(n) ?: return Result.failure(MushafLoadException("تعذر تحميل خط الصفحة. تأكد من الاتصال بالإنترنت (تُحفظ الخطوط بعد أول تحميل).", null))
        }
        val names = if (lines.any { it.kind == MushafEngine.LineKind.Banner }) surahNamesFont() else null
        val basmalaGlyphs = if (hasBasmala) basmalaGlyphs() else emptyList()
        return Result.success(PreparedPage(data, lines, families, names, basmalaGlyphs))
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

        @Volatile private var instance: MushafStore? = null
        fun get(context: Context): MushafStore = instance ?: synchronized(this) { instance ?: MushafStore(context).also { instance = it } }
    }
}
