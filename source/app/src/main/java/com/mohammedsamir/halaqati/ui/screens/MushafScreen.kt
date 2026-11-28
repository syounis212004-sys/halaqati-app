package com.mohammedsamir.halaqati.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.graphics.graphicsLayer
import com.mohammedsamir.halaqati.ui.theme.LocalReducedMotion
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoFixNormal
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.BorderColor
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.graphics.luminance
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mohammedsamir.halaqati.quran.MushafEngine
import com.mohammedsamir.halaqati.quran.TilawahCategory
import com.mohammedsamir.halaqati.quran.TilawahMark
import com.mohammedsamir.halaqati.quran.TilawahMarks
import com.mohammedsamir.halaqati.quran.MushafEngine.MarkKind
import com.mohammedsamir.halaqati.quran.MushafEngine.MarkType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** The printed page is 14 wide by 20 high; the page box always keeps this shape (never stretched). */
internal const val MUSHAF_ASPECT_W = 14f
internal const val MUSHAF_ASPECT_H = 20f

/** What the recitation form already knows, so the mushaf can show the official V7 estimate next to the page. */
data class MushafScore(val safety: Double, val label: String, val pages: Double)

internal enum class Tool { Tap, Highlight, Erase }

/**
 * Every colour comes from the app theme the user chose in Settings (light, dark or system), so the mushaf matches all other screens:
 * paper = warm paper in the light theme and the theme's surface in the dark one; ink, frame and rule follow the scheme; a mistake is the
 * scheme's error colour and a prompt a blue tuned for contrast against the page.
 */
internal class MushafPalette(scheme: ColorScheme, info: Color) {
    val dark = scheme.background.luminance() < 0.5f
    // paper
    val pageBg = if (dark) scheme.surface else Color(0xFFFFFCF3)
    val pageInk = scheme.onSurface
    val frame = scheme.primary
    val rule = scheme.tertiary
    val mistake = scheme.error
    val mistakeBg = mistake.copy(alpha = if (dark) 0.22f else 0.16f)
    val prompt = if (dark) Color(0xFF64B5F6) else Color(0xFF1565C0)
    val promptBg = prompt.copy(alpha = if (dark) 0.24f else 0.14f)
    val rangeBg = scheme.primary.copy(alpha = 0.10f)
    val accent = scheme.primary
    /** Highlighter: the app's identity green (the theme primary). */
    val highlight = scheme.primary
    val dockBg = scheme.surfaceContainerHigh
    // chrome around the page
    val chromeBg = scheme.surface
    val chromeInk = scheme.onSurface
    val chromeLine = scheme.outlineVariant
    val chromeAccent = scheme.primary
    val chromeMistake = scheme.error
    val chromePrompt = info
}

@Composable
private fun rememberMushafPalette(): MushafPalette {
    val scheme = MaterialTheme.colorScheme
    val info = com.mohammedsamir.halaqati.ui.theme.statusColors("info").first
    return remember(scheme, info) { MushafPalette(scheme, info) }
}

internal fun digits(n: Any?): String = n.toString().map { if (it in '0'..'9') "٠١٢٣٤٥٦٧٨٩"[it - '0'] else it }.joinToString("")

internal sealed interface PageState {
    data object Loading : PageState
    data class Error(val message: String) : PageState
    data class Dk(val page: DkPrepared) : PageState
}

/**
 * Full-screen interactive mushaf: a printed 15-line page (QCF V2 glyphs) the teacher marks word by word.
 * Marks live in [session] (kept by the caller while the recitation form is open); counter changes are reported through [onMark].
 */
@Composable
fun MushafMarkingDialog(
    session: MushafEngine.Session,
    rangeStartPage: Int?,
    counters: () -> Pair<Int, Int>,
    score: () -> MushafScore?,
    inRange: (surah: Int, ayah: Int, page: Int) -> Boolean,
    onMark: (mist: Int, prompt: Int) -> Unit,
    onDismiss: () -> Unit,
    rangeEndPage: Int? = null,
    tilawahMarks: List<TilawahMark>? = null,
    onTilawahMarksChange: suspend (List<TilawahMark>) -> Unit = {},
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        MushafScreenContent(session, rangeStartPage, counters, score, inRange, onMark, onDismiss, rangeEndPage = rangeEndPage,
            tilawahMarks = tilawahMarks, onTilawahMarksChange = onTilawahMarksChange)
    }
}

/** Last page read in the stand-alone reader (kept on this device only). */
object MushafReaderPrefs {
    private const val PREFS = "halaqati_mushaf_reader"
    fun lastPage(ctx: android.content.Context): Int = ctx.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).getInt("last_page", 1).coerceIn(1, 604)
    fun save(ctx: android.content.Context, page: Int) = ctx.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).edit().putInt("last_page", page.coerceIn(1, 604)).apply()
}

/** Bookmarked pages (kept on this device only); the same list serves the reader and the marking mushaf. */
object MushafBookmarks {
    private const val PREFS = "halaqati_mushaf_reader"
    private const val KEY = "bookmarks"
    fun all(ctx: android.content.Context): List<Int> =
        ctx.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).getString(KEY, "").orEmpty().split(',').mapNotNull { it.toIntOrNull() }.filter { it in 1..604 }.distinct().sorted()
    /** Adds the page when absent, removes it when present; returns the new list. */
    fun toggle(ctx: android.content.Context, page: Int): List<Int> {
        val next = if (page in all(ctx)) all(ctx) - page else (all(ctx) + page).sorted()
        ctx.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).edit().putString(KEY, next.joinToString(",")).apply()
        return next
    }
}

/** The mushaf for reading only (no marking tools, no score), opened from the main menu; remembers the last page. */
@Composable
fun MushafReaderDialog(startPage: Int, onDismiss: () -> Unit) {
    val session = remember { MushafEngine.Session(startPage) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        MushafScreenContent(session, null, { 0 to 0 }, { null }, { _, _, _ -> false }, { _, _ -> }, onDismiss, readOnly = true)
    }
}

@Composable
internal fun MushafScreenContent(
    session: MushafEngine.Session,
    rangeStartPage: Int?,
    counters: () -> Pair<Int, Int>,
    score: () -> MushafScore?,
    inRange: (surah: Int, ayah: Int, page: Int) -> Boolean,
    onMark: (mist: Int, prompt: Int) -> Unit,
    onDismiss: () -> Unit,
    rangeEndPage: Int? = null,
    readOnly: Boolean = false,
    tilawahMarks: List<TilawahMark>? = null,
    onTilawahMarksChange: suspend (List<TilawahMark>) -> Unit = {},
) {
    val context = LocalContext.current
    val pal = rememberMushafPalette()
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    var page by remember { mutableIntStateOf(session.page) }
    var zoomed by remember { mutableStateOf(false) }
    var bookmarks by remember { mutableStateOf(MushafBookmarks.all(context)) }
    val tilawah = tilawahMarks != null
    var tool by rememberSaveable(tilawah) { mutableStateOf(Tool.Tap) }
    var type by rememberSaveable { mutableStateOf(MarkType.Mistake) }
    var dockOpen by rememberSaveable { mutableStateOf(true) }
    var tick by remember { mutableIntStateOf(0) }            // marks live outside Compose state: bump to repaint
    val marksById = remember(session, tick) { session.marksSnapshot().associateBy { it.id } }
    var preview by remember { mutableStateOf<Set<String>>(emptySet()) }
    var kindMenu by remember { mutableStateOf<Pair<String, Offset>?>(null) }
    var jumpDialog by remember { mutableStateOf(false) }
    var announce by remember { mutableStateOf("") }
    val tilawahById = remember(tilawahMarks) { tilawahMarks.orEmpty().groupBy { it.wordId } }
    var tilawahSelection by remember { mutableStateOf<Pair<List<String>, Int>?>(null) }
    var tilawahPast by remember { mutableStateOf<List<List<TilawahMark>>>(emptyList()) }
    var tilawahFuture by remember { mutableStateOf<List<List<TilawahMark>>>(emptyList()) }
    var tilawahBusy by remember { mutableStateOf(false) }
    var tilawahError by remember { mutableStateOf<String?>(null) }

    fun selectTilawah(ids: List<String>, pg: Int): List<String> {
        val allowed = ids.distinct().filter { id -> MushafEngine.parseWordId(id)?.let { inRange(it.first, it.second, pg) } == true }
        if (allowed.isEmpty()) tilawahError = "اختر كلمة ضمن نطاق القراءة"
        return allowed
    }
    fun changeTilawah(next: List<TilawahMark>, undo: Boolean = false, redo: Boolean = false) {
        if (tilawahBusy || next == tilawahMarks.orEmpty()) return
        val current = tilawahMarks.orEmpty()
        tilawahBusy = true
        tilawahError = null
        scope.launch {
            try {
                onTilawahMarksChange(next)
                when {
                    undo -> { tilawahPast = tilawahPast.dropLast(1); tilawahFuture = (tilawahFuture + listOf(current)).takeLast(50) }
                    redo -> { tilawahFuture = tilawahFuture.dropLast(1); tilawahPast = (tilawahPast + listOf(current)).takeLast(50) }
                    else -> { tilawahPast = (tilawahPast + listOf(current)).takeLast(50); tilawahFuture = emptyList() }
                }
                tick++
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                announce = "تم حفظ علامات التلاوة محليًا"
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { tilawahError = e.message ?: "تعذر حفظ العلامات محليًا" }
            finally { tilawahBusy = false }
        }
    }

    val pagerState = rememberPagerState(initialPage = page - 1) { MushafEngine.TOTAL_PAGES }
    val reducedMotion = LocalReducedMotion.current
    LaunchedEffect(page) {
        session.setPage(page)
        if (readOnly) MushafReaderPrefs.save(context, page)
        kindMenu = null; zoomed = false
        // buttons and jumps move the pager: a neighbour animates (the book direction), a far jump swaps at once
        if (pagerState.settledPage != page - 1) {
            if (!reducedMotion && abs(pagerState.settledPage - (page - 1)) == 1) pagerState.animateScrollToPage(page - 1) else pagerState.scrollToPage(page - 1)
        }
    }
    LaunchedEffect(pagerState) { androidx.compose.runtime.snapshotFlow { pagerState.settledPage }.collect { if (it + 1 != page) page = it + 1 } }

    fun report(applied: MushafEngine.Applied, what: String) {
        if (applied.delta.mist != 0 || applied.delta.prompt != 0) onMark(applied.delta.mist, applied.delta.prompt)
        if (applied.changed > 0) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        tick++
        if (applied.changed > 0) {
            val c = counters()
            announce = "$what. أخطاء ${c.first}، تلقين ${c.second}"
        }
    }
    fun actOnWord(id: String, pg: Int) {
        if (readOnly) return
        if (tilawah) {
            val ids = selectTilawah(listOf(id), pg)
            if (ids.isNotEmpty()) tilawahSelection = ids to pg
            return
        }
        val name = if (type == MarkType.Mistake) "خطأ" else "تلقين"
        when (tool) {
            Tool.Erase -> report(session.eraseWords(listOf(id)), "مُسحت العلامة")
            Tool.Highlight -> report(session.markSpan(listOf(id), type, pageNo = pg), "تم وضع علامة $name")
            Tool.Tap -> report(session.markWord(id, type, pageNo = pg), "تم تبديل علامة $name")
        }
    }
    fun undoRedo(undo: Boolean) {
        val r = (if (undo) session.undo() else session.redo()) ?: return
        report(r, if (undo) "تراجع" else "إعادة")
        r.page?.takeIf { it != page }?.let { page = it }
    }

    Box(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Surface(Modifier.fillMaxSize(), color = pal.chromeBg, contentColor = pal.chromeInk) {
                Column(Modifier.systemBarsPadding().fillMaxSize()) {
                    // ---- one slim bar: close, previous / page / next, jump to the assigned range
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onDismiss, enabled = !tilawahBusy, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.Close, contentDescription = "إغلاق المصحف") }
                        val marked = page in bookmarks
                        IconButton(onClick = { bookmarks = MushafBookmarks.toggle(context, page) }, modifier = Modifier.size(48.dp)) {
                            Icon(
                                if (marked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                contentDescription = if (marked) "إزالة العلامة المرجعية" else "إضافة علامة مرجعية", tint = if (marked) pal.chromeAccent else pal.chromeInk,
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { page = MushafEngine.clampPage(page - 1) }, enabled = page > 1, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "الصفحة السابقة", tint = if (page > 1) pal.chromeAccent else pal.chromeInk.copy(alpha = 0.3f))
                        }
                        TextButton(onClick = { jumpDialog = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text("${digits(page)} / ٦٠٤", fontWeight = FontWeight.Bold, color = pal.chromeInk, maxLines = 1, softWrap = false)
                        }
                        IconButton(onClick = { page = MushafEngine.clampPage(page + 1) }, enabled = page < MushafEngine.TOTAL_PAGES, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "الصفحة التالية", tint = if (page < MushafEngine.TOTAL_PAGES) pal.chromeAccent else pal.chromeInk.copy(alpha = 0.3f))
                        }
                        Spacer(Modifier.weight(1f))
                        if (!readOnly && rangeStartPage != null && rangeStartPage != page) {
                            TextButton(onClick = { page = rangeStartPage }, modifier = Modifier.heightIn(min = 48.dp)) { Text("إلى المقرر", color = pal.chromeAccent, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false) }
                        } else Spacer(Modifier.width(48.dp))
                    }
                    // ---- the official V7 estimate on one line (more only when a page is over its limit)
                    if (tilawah) {
                        val currentScore = score()
                        Text("جلي: ${counters().first} · خفي: ${tilawahMarks.orEmpty().filterNot { it.category.major }.sumOf { it.occurrences }} · ${currentScore?.label.orEmpty()}",
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 3.dp), style = MaterialTheme.typography.bodySmall)
                        tilawahError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 8.dp)) }
                    } else if (!readOnly) ScoreStrip(session, tick, counters(), score(), pal)

                    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                        val landscape = maxWidth > maxHeight
                        val pageArea: @Composable (Modifier) -> Unit = { m ->
                            // the page keeps its printed 14:20 shape; spare height (tall screens) is shared above and below it
                            BoxWithConstraints(m, contentAlignment = Alignment.Center) {
                                val pageW = min(maxWidth.value, maxHeight.value * MUSHAF_ASPECT_W / MUSHAF_ASPECT_H).dp
                                val pageH = (pageW.value * MUSHAF_ASPECT_H / MUSHAF_ASPECT_W).dp
                                val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
                                // turning a page: the incoming page fades in while the outgoing one fades out and both travel only part of the swipe,
                                // so two pages are never seen side by side. Reduced motion swaps the page at the half way point.
                                HorizontalPager(
                                    state = pagerState, modifier = Modifier.fillMaxSize(), key = { it },
                                    // the neighbours are prepared ahead: the pages come from the pack inside the app, so this costs no network
                                    beyondViewportPageCount = 1,
                                    userScrollEnabled = (readOnly || tool == Tool.Tap) && !zoomed,
                                ) { idx ->
                                    Box(
                                        Modifier.fillMaxSize().graphicsLayer {
                                            val off = pagerState.getOffsetDistanceInPages(idx)
                                            val a = abs(off).coerceIn(0f, 1f)
                                            val travel = (if (rtl) -1f else 1f) * off * size.width        // where the pager puts this page
                                            // a page that is a whole page away stays where the pager put it (off screen): pulling it back would let an invisible
                                            // neighbour sit on top of the current page and steal its touches
                                            val moving = a < 0.999f
                                            if (reducedMotion) { alpha = if (a < 0.5f) 1f else 0f; translationX = if (moving) -travel else 0f }
                                            else { alpha = (1.4f - 1.8f * a).coerceIn(0f, 1f); translationX = if (moving) -travel * 0.6f else 0f; val sc = 0.96f + 0.04f * (1f - a); scaleX = sc; scaleY = sc }
                                        },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        MushafPageSlot(
                                            p = idx + 1, current = idx + 1 == page, pageW = pageW, pageH = pageH, pal = pal, tick = tick, tool = tool,
                                            marksById = marksById, preview = preview, inRange = inRange,
                                            onTapWord = { id, pg -> actOnWord(id, pg) },
                                            onLongWord = { id, at -> if (tilawah) actOnWord(id, idx + 1) else if (!readOnly) kindMenu = id to at },
                                            onPreview = { preview = it },
                                            onSpan = { ids, pg ->
                                                preview = emptySet()
                                                if (tilawah) {
                                                    val allowed = selectTilawah(ids, pg)
                                                    if (allowed.isNotEmpty()) {
                                                        if (tool == Tool.Erase) changeTilawah(TilawahMarks.eraseWords(tilawahMarks.orEmpty(), allowed))
                                                        else tilawahSelection = allowed to pg
                                                    }
                                                } else if (ids.isNotEmpty()) {
                                                    if (tool == Tool.Erase) report(session.eraseWords(ids), "مُسحت العلامات")
                                                    else report(session.markSpan(ids, type, pageNo = pg), "تم تظليل ${ids.size} كلمة")
                                                }
                                            },
                                            kindMenuFor = kindMenu,
                                            onKind = { id, kind, pg -> kindMenu = null; report(session.markWord(id, type, kind, pg), "تم وضع علامة ${if (type == MarkType.Mistake) "خطأ" else "تلقين"}") },
                                            onDismissKind = { kindMenu = null },
                                            onZoomChanged = { z -> if (idx + 1 == page) zoomed = z },
                                            tilawahById = tilawahById,
                                        )
                                    }
                                }
                            }
                        }
                        val dock: @Composable (Boolean, Modifier) -> Unit = { side, m ->
                            if (tilawah) TilawahMushafTools(side, tool, tilawahBusy, tilawahPast.isNotEmpty(), tilawahFuture.isNotEmpty(),
                                { tool = it }, { tilawahPast.lastOrNull()?.let { changeTilawah(it, undo = true) } },
                                { tilawahFuture.lastOrNull()?.let { changeTilawah(it, redo = true) } })
                            else MushafDock(
                                side = side, expanded = dockOpen, onExpanded = { dockOpen = it }, counts = counters(), type = type, tool = tool,
                                canUndo = remember(tick) { session.canUndo }, canRedo = remember(tick) { session.canRedo }, pal = pal,
                                onType = { type = it }, onTool = { tool = it }, onUndo = { undoRedo(true) }, onRedo = { undoRedo(false) }, modifier = m,
                            )
                        }
                        if (landscape) {
                            // landscape: a vertical rail on the start edge, the page takes the rest of the width
                            Row(Modifier.fillMaxSize().padding(horizontal = 2.dp, vertical = 1.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (!readOnly) dock(true, Modifier)
                                pageArea(Modifier.weight(1f).fillMaxHeight())
                            }
                        } else {
                            Column(Modifier.fillMaxSize().padding(horizontal = 1.dp).padding(bottom = 2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                pageArea(Modifier.weight(1f).fillMaxWidth())
                                if (!readOnly) dock(false, Modifier)
                            }
                        }
                    }
                    Text(announce, modifier = Modifier.size(0.dp).semantics { contentDescription = announce })
                }
            }
        }
    }

    tilawahSelection?.let { (ids, pg) ->
        TilawahWordEditor(ids, tilawahMarks.orEmpty(), tilawahBusy, tilawahError,
            onAdd = { category, note, subtype ->
                try { changeTilawah(TilawahMarks.add(tilawahMarks.orEmpty(), ids, pg, category, note, subtype)) }
                catch (e: IllegalArgumentException) { tilawahError = e.message ?: "تعذر إضافة العلامة" }
            },
            onCorrect = { changeTilawah(TilawahMarks.correct(tilawahMarks.orEmpty(), it)) },
            onRemove = { changeTilawah(TilawahMarks.remove(tilawahMarks.orEmpty(), it)) },
            onDismiss = { if (!tilawahBusy) tilawahSelection = null })
    }
    if (jumpDialog) MushafJumpDialog(
        bookmarks = bookmarks, page = page, rangeStart = if (readOnly) null else rangeStartPage, rangeEnd = rangeEndPage,
        onGo = { page = MushafEngine.clampPage(it); jumpDialog = false }, onDismiss = { jumpDialog = false },
    )
}

@Composable
private fun ScoreStrip(session: MushafEngine.Session, tick: Int, c: Pair<Int, Int>, s: MushafScore?, pal: MushafPalette) {
    val over = remember(tick) { session.overLimitPages() }
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp).background(pal.chromeLine.copy(alpha = 0.28f), RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 4.dp)) {
        if (s == null) {
            Text("اختر المقرر أولاً ليظهر التقدير.", style = MaterialTheme.typography.bodySmall)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${digits(s.safety)}٪ ${s.label}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1)
                Spacer(Modifier.weight(1f))
                // the full rule is one tap away in the accessibility text; on screen only what changes the score
                Text("${digits(c.first)} أخطاء · ${digits(c.second)} تلقين · ${digits((s.pages * 100).roundToInt() / 100.0)} ص", style = MaterialTheme.typography.labelMedium, maxLines = 1)
            }
            if (over.isNotEmpty()) {
                Text(
                    "صفحة " + over.joinToString("، ") { "${digits(it.page)} (${digits(it.mistakes)} أخطاء، ${digits(it.prompts)} تلقين)" } + " فوق الحد: النتيجة إعادة",
                    style = MaterialTheme.typography.labelMedium, color = pal.chromeMistake, fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

/**
 * The mark identity, drawn (not an icon font): a mistake is a SOLID red badge with a cross, a prompt is a DASHED blue ring with the letter ت.
 * They repeat the underline each mark leaves on the page (solid ×, dashed ت), so colour is never the only cue.
 */
@Composable
internal fun MarkBadge(mistake: Boolean, color: Color, size: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier.size(size).drawBehind {
            val w = this.size.minDimension
            if (mistake) {
                drawCircle(color)
            } else {
                drawCircle(color.copy(alpha = 0.14f))
                drawCircle(color, style = Stroke(width = w * 0.09f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(w * 0.17f, w * 0.12f))))
            }
        },
        contentAlignment = Alignment.Center,
    ) {
        if (mistake) {
            Box(Modifier.size(size * 0.46f).drawBehind {
                val t = this.size.minDimension * 0.2f
                drawLine(Color.White, Offset(0f, 0f), Offset(this.size.width, this.size.height), strokeWidth = t, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                drawLine(Color.White, Offset(this.size.width, 0f), Offset(0f, this.size.height), strokeWidth = t, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            })
        } else Text("ت", color = color, fontWeight = FontWeight.ExtraBold, fontSize = with(LocalDensity.current) { (size * 0.52f).toSp() })
    }
}

/** One page of the pager: loads its own data from the pack inside the app and draws it in the page frame. */
@Composable
private fun MushafPageSlot(
    p: Int, current: Boolean, pageW: androidx.compose.ui.unit.Dp, pageH: androidx.compose.ui.unit.Dp, pal: MushafPalette, tick: Int, tool: Tool,
    marksById: Map<String, MushafEngine.Mark>, preview: Set<String>, inRange: (Int, Int, Int) -> Boolean,
    onTapWord: (String, Int) -> Unit, onLongWord: (String, Offset) -> Unit, onPreview: (Set<String>) -> Unit, onSpan: (List<String>, Int) -> Unit,
    kindMenuFor: Pair<String, Offset>?, onKind: (String, MarkKind, Int) -> Unit, onDismissKind: () -> Unit, onZoomChanged: (Boolean) -> Unit,
    tilawahById: Map<String, List<TilawahMark>> = emptyMap(),
) {
    val context = LocalContext.current
    var attempt by remember { mutableIntStateOf(0) }
    var state by remember(p) { mutableStateOf<PageState>(PageState.Loading) }
    LaunchedEffect(p, attempt) {
        state = runCatching {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val data = com.mohammedsamir.halaqati.quran.dk.DkPack.data(context)
                PageState.Dk(DkPrepared(p, data, data.loadPage(p), com.mohammedsamir.halaqati.quran.dk.DkPack.idsOf(context, p))) as PageState
            }
        }.getOrElse { PageState.Error(it.message ?: "تعذر تحميل الصفحة") }
    }
    MushafPage(
        state, p, pageW, pageH, pal, tick, tool, marksById, preview, inRange,
        onTapWord = { onTapWord(it, p) }, onLongWord = onLongWord, onPreview = onPreview, onSpan = { onSpan(it, p) },
        kindMenuFor = if (current) kindMenuFor else null, onKind = { id, k -> onKind(id, k, p) }, onDismissKind = onDismissKind,
        onRetry = { attempt++ }, isCurrent = current, onZoomChanged = onZoomChanged, tilawahById = tilawahById,
    )
}

@Composable
private fun MushafPage(
    state: PageState, page: Int, pageW: androidx.compose.ui.unit.Dp, pageH: androidx.compose.ui.unit.Dp, pal: MushafPalette, tick: Int, tool: Tool,
    marksById: Map<String, MushafEngine.Mark>, preview: Set<String>, inRange: (Int, Int, Int) -> Boolean,
    onTapWord: (String) -> Unit, onLongWord: (String, Offset) -> Unit, onPreview: (Set<String>) -> Unit, onSpan: (List<String>) -> Unit,
    kindMenuFor: Pair<String, Offset>?, onKind: (String, MarkKind) -> Unit, onDismissKind: () -> Unit, onRetry: () -> Unit,
    isCurrent: Boolean = true, onZoomChanged: (Boolean) -> Unit = {},
    tilawahById: Map<String, List<TilawahMark>> = emptyMap(),
) {
    Box(
        Modifier.width(pageW).height(pageH).background(pal.pageBg, RoundedCornerShape(6.dp))
            .border(1.dp, pal.frame, RoundedCornerShape(5.dp)).padding(1.dp).border(1.dp, pal.rule, RoundedCornerShape(4.dp)).padding(1.dp),
    ) {
        when (state) {
            is PageState.Loading -> Text("جارٍ تحميل صفحة ${digits(page)}…", color = pal.pageInk, modifier = Modifier.align(Alignment.Center).semantics { role = Role.Image })
            is PageState.Error -> Column(Modifier.align(Alignment.Center).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("تعذّر عرض صفحة ${digits(page)}.", color = pal.pageInk, fontWeight = FontWeight.Bold)
                Text(state.message + " يمكنك متابعة التسميع بالعدّادات اليدوية دون المصحف.", color = pal.pageInk, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                TextButton(onClick = onRetry) { Text("إعادة المحاولة") }
            }
            is PageState.Dk -> DkReadyPage(state.page, page, pal, tick, tool, marksById, preview, inRange, onTapWord, onLongWord, onPreview, onSpan, kindMenuFor, onKind, onDismissKind, isCurrent, onZoomChanged, tilawahById)
        }
    }
}
