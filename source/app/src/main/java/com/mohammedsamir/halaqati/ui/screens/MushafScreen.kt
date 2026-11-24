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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoFixNormal
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
import com.mohammedsamir.halaqati.quran.MushafEngine.MarkKind
import com.mohammedsamir.halaqati.quran.MushafEngine.MarkType
import com.mohammedsamir.halaqati.quran.MushafStore
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
 * scheme's error colour and a prompt its info blue (the same status colours the rest of the app uses).
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
    val prompt = info
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
    data class Ready(val page: MushafStore.PreparedPage) : PageState
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
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        MushafScreenContent(session, rangeStartPage, counters, score, inRange, onMark, onDismiss)
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
) {
    val context = LocalContext.current
    val store = remember { MushafStore.get(context) }
    val pal = rememberMushafPalette()
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    var page by remember { mutableIntStateOf(session.page) }
    var state by remember { mutableStateOf<PageState>(PageState.Loading) }
    var tool by rememberSaveable { mutableStateOf(Tool.Tap) }
    var type by rememberSaveable { mutableStateOf(MarkType.Mistake) }
    var dockOpen by rememberSaveable { mutableStateOf(true) }
    var tick by remember { mutableIntStateOf(0) }            // marks live outside Compose state: bump to repaint
    var preview by remember { mutableStateOf<Set<String>>(emptySet()) }
    var kindMenu by remember { mutableStateOf<Pair<String, Offset>?>(null) }
    var jumpDialog by remember { mutableStateOf(false) }
    var announce by remember { mutableStateOf("") }

    LaunchedEffect(page) {
        session.setPage(page)
        state = PageState.Loading
        kindMenu = null
        state = store.preparePage(page).fold(
            onSuccess = { PageState.Ready(it) },
            onFailure = { PageState.Error(it.message ?: "تعذر تحميل بيانات الصفحة") },
        )
    }

    fun report(applied: MushafEngine.Applied, what: String) {
        if (applied.delta.mist != 0 || applied.delta.prompt != 0) onMark(applied.delta.mist, applied.delta.prompt)
        if (applied.changed > 0) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        tick++
        if (applied.changed > 0) {
            val c = counters()
            announce = "$what. أخطاء ${c.first}، تلقين ${c.second}"
        }
    }
    fun actOnWord(id: String) {
        val name = if (type == MarkType.Mistake) "خطأ" else "تلقين"
        when (tool) {
            Tool.Erase -> report(session.eraseWords(listOf(id)), "مُسحت العلامة")
            Tool.Highlight -> report(session.markSpan(listOf(id), type, pageNo = page), "تم وضع علامة $name")
            Tool.Tap -> report(session.markWord(id, type, pageNo = page), "تم تبديل علامة $name")
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
                        IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.Close, contentDescription = "إغلاق المصحف") }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { page = MushafEngine.clampPage(page - 1) }, enabled = page > 1, modifier = Modifier.heightIn(min = 48.dp)) { Text("السابقة", color = pal.chromeAccent) }
                        TextButton(onClick = { jumpDialog = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text("${digits(page)} / ٦٠٤", fontWeight = FontWeight.Bold, color = pal.chromeInk)
                        }
                        TextButton(onClick = { page = MushafEngine.clampPage(page + 1) }, enabled = page < MushafEngine.TOTAL_PAGES, modifier = Modifier.heightIn(min = 48.dp)) { Text("التالية", color = pal.chromeAccent) }
                        Spacer(Modifier.weight(1f))
                        if (rangeStartPage != null && rangeStartPage != page) {
                            TextButton(onClick = { page = rangeStartPage }, modifier = Modifier.heightIn(min = 48.dp)) { Text("إلى المقرر", color = pal.chromeAccent, fontWeight = FontWeight.Bold) }
                        } else Spacer(Modifier.width(48.dp))
                    }
                    // ---- the official V7 estimate on one line (more only when a page is over its limit)
                    ScoreStrip(session, tick, counters(), score(), pal)

                    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                        val landscape = maxWidth > maxHeight
                        val pageArea: @Composable (Modifier) -> Unit = { m ->
                            // the page keeps its printed 14:20 shape; spare height (tall screens) is shared above and below it
                            BoxWithConstraints(m, contentAlignment = Alignment.Center) {
                                val pageW = min(maxWidth.value, maxHeight.value * MUSHAF_ASPECT_W / MUSHAF_ASPECT_H).dp
                                val pageH = (pageW.value * MUSHAF_ASPECT_H / MUSHAF_ASPECT_W).dp
                                MushafPage(
                                    state = state, page = page, pageW = pageW, pageH = pageH, pal = pal, tick = tick, tool = tool,
                                    session = session, preview = preview, inRange = inRange,
                                    onTapWord = { actOnWord(it) },
                                    onLongWord = { id, at -> kindMenu = id to at },
                                    onPreview = { preview = it },
                                    onSpan = { ids ->
                                        preview = emptySet()
                                        if (ids.isNotEmpty()) {
                                            if (tool == Tool.Erase) report(session.eraseWords(ids), "مُسحت العلامات")
                                            else report(session.markSpan(ids, type, pageNo = page), "تم تظليل ${ids.size} كلمة")
                                        }
                                    },
                                    kindMenuFor = kindMenu,
                                    onKind = { id, kind -> kindMenu = null; report(session.markWord(id, type, kind, page), "تم وضع علامة ${if (type == MarkType.Mistake) "خطأ" else "تلقين"}") },
                                    onDismissKind = { kindMenu = null },
                                    onRetry = { val p = page; page = 0; page = p },
                                )
                            }
                        }
                        val dock: @Composable (Boolean, Modifier) -> Unit = { side, m ->
                            MushafDock(
                                side = side, expanded = dockOpen, onExpanded = { dockOpen = it }, counts = counters(), type = type, tool = tool,
                                canUndo = remember(tick) { session.canUndo }, canRedo = remember(tick) { session.canRedo }, pal = pal,
                                onType = { type = it }, onTool = { tool = it }, onUndo = { undoRedo(true) }, onRedo = { undoRedo(false) }, modifier = m,
                            )
                        }
                        if (landscape) {
                            // landscape: a vertical rail on the start edge, the page takes the rest of the width
                            Row(Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                dock(true, Modifier)
                                pageArea(Modifier.weight(1f).fillMaxHeight())
                            }
                        } else {
                            Column(Modifier.fillMaxSize().padding(horizontal = 4.dp).padding(bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                pageArea(Modifier.weight(1f).fillMaxWidth())
                                dock(false, Modifier)
                            }
                        }
                    }
                    Text(announce, modifier = Modifier.size(0.dp).semantics { contentDescription = announce })
                }
            }
        }
    }

    if (jumpDialog) {
        var input by remember { mutableStateOf(page.toString()) }
        AlertDialog(
            onDismissRequest = { jumpDialog = false },
            title = { Text("الانتقال إلى صفحة") },
            text = {
                OutlinedTextField(
                    value = input, onValueChange = { input = it.filter(Char::isDigit).take(3) }, singleLine = true,
                    label = { Text("رقم الصفحة (١–٦٠٤)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            },
            confirmButton = { TextButton(onClick = { input.toIntOrNull()?.let { page = MushafEngine.clampPage(it) }; jumpDialog = false }) { Text("انتقال") } },
            dismissButton = { TextButton(onClick = { jumpDialog = false }) { Text("إلغاء") } },
        )
    }
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

@Composable
private fun MushafPage(
    state: PageState, page: Int, pageW: androidx.compose.ui.unit.Dp, pageH: androidx.compose.ui.unit.Dp, pal: MushafPalette, tick: Int, tool: Tool,
    session: MushafEngine.Session, preview: Set<String>, inRange: (Int, Int, Int) -> Boolean,
    onTapWord: (String) -> Unit, onLongWord: (String, Offset) -> Unit, onPreview: (Set<String>) -> Unit, onSpan: (List<String>) -> Unit,
    kindMenuFor: Pair<String, Offset>?, onKind: (String, MarkKind) -> Unit, onDismissKind: () -> Unit, onRetry: () -> Unit,
) {
    Box(
        Modifier.width(pageW).height(pageH).background(pal.pageBg, RoundedCornerShape(6.dp))
            .border(2.dp, pal.frame, RoundedCornerShape(5.dp)).padding(2.dp).border(1.dp, pal.rule, RoundedCornerShape(4.dp)).padding(horizontal = 2.dp, vertical = 1.dp),
    ) {
        when (state) {
            is PageState.Loading -> Text("جارٍ تحميل صفحة ${digits(page)}…", color = pal.pageInk, modifier = Modifier.align(Alignment.Center).semantics { role = Role.Image })
            is PageState.Error -> Column(Modifier.align(Alignment.Center).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("تعذّر عرض صفحة ${digits(page)}.", color = pal.pageInk, fontWeight = FontWeight.Bold)
                Text(state.message + " يمكنك متابعة التسميع بالعدّادات اليدوية دون المصحف.", color = pal.pageInk, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                TextButton(onClick = onRetry) { Text("إعادة المحاولة") }
            }
            is PageState.Ready -> ReadyPage(state.page, page, pageW, pageH, pal, tick, tool, session, preview, inRange, onTapWord, onLongWord, onPreview, onSpan, kindMenuFor, onKind, onDismissKind)
        }
    }
}

@Composable
internal fun ReadyPage(
    p: MushafStore.PreparedPage, page: Int, pageW: androidx.compose.ui.unit.Dp, pageH: androidx.compose.ui.unit.Dp, pal: MushafPalette, tick: Int, tool: Tool,
    session: MushafEngine.Session, preview: Set<String>, inRange: (Int, Int, Int) -> Boolean,
    onTapWord: (String) -> Unit, onLongWord: (String, Offset) -> Unit, onPreview: (Set<String>) -> Unit, onSpan: (List<String>) -> Unit,
    kindMenuFor: Pair<String, Offset>?, onKind: (String, MarkKind) -> Unit, onDismissKind: () -> Unit,
) {
    val density = LocalDensity.current
    val opening = MushafEngine.isOpeningPage(page)
    val shown = if (opening) p.lines.filter { it.kind != MushafEngine.LineKind.Empty } else p.lines
    val words = remember(p) { p.lines.flatMap { it.words } }
    val surahInfo = words.firstOrNull()?.surah
    val inside = remember(p, tick) { words.count { inRange(it.surah, it.ayah, page) } }
    val rects = remember(p) { HashMap<String, Rect>() }
    var origin by remember(p) { mutableStateOf(Offset.Zero) }
    val menuHeightPad = 56.dp

    Column(Modifier.fillMaxSize()) {
        // header: surah, range chip, juz
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(surahInfo?.let { "سورة " + com.mohammedsamir.halaqati.quran.QuranMetadata.surahName(it) } ?: "", color = pal.frame, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text(if (words.isEmpty() || inside == 0) "" else if (inside == words.size) "ضمن المقرر" else "جزء من المقرر", color = pal.accent, fontSize = 12.sp)
            Text(p.data.juz?.let { "الجزء ${digits(it)}" } ?: "", color = pal.frame, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }

        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val areaW = constraints.maxWidth.toFloat(); val areaH = constraints.maxHeight.toFloat()
            // one size for the whole page: a full printed line is ~15.9em wide and 15 lines need ~1.8em each
            val qcfPx = min(areaW / 15.9f, areaH / 27f) * if (opening) 1.0f else 1f
            val openingScale = if (opening) 1.3f else 1f
            val sizePx = qcfPx * openingScale
            val linesW = min(areaW, qcfPx * 15.9f)
            val measurer = rememberTextMeasurer()
            val linesWdp = with(density) { linesW.toDp() }
            val rowH = if (opening) null else with(density) { (areaH / 15f).toDp() }

            Box(
                Modifier.width(linesWdp).fillMaxSize()
                    .onGloballyPositioned { origin = it.positionInRoot() }
                    .pointerInput(p, tool) {
                        val slop = 10.dp.toPx()
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            fun hit(pos: Offset): String? {
                                val g = pos + origin
                                return rects.entries.firstOrNull { it.value.contains(g) }?.key
                            }
                            val anchor = hit(down.position) ?: return@awaitEachGesture
                            var current = anchor
                            var moved = false; var long = false
                            val t0 = down.uptimeMillis
                            if (tool != Tool.Tap) onPreview(setOf(anchor))
                            var finished = false
                            while (!finished) {
                                val waitForLong = tool == Tool.Tap && !moved && !long
                                val ev = if (waitForLong) withTimeoutOrNull(450L) { awaitPointerEvent() } else awaitPointerEvent()
                                if (ev == null) { long = true; onLongWord(anchor, down.position); continue }
                                val ch = ev.changes.firstOrNull() ?: break
                                if (!ch.pressed) {
                                    finished = true
                                    val dur = ch.uptimeMillis - t0
                                    when {
                                        long -> Unit
                                        tool == Tool.Tap -> if (!moved && dur <= 400L) onTapWord(anchor)
                                        else -> { val ids = MushafEngine.spanBetween(p.lines, anchor, current); onSpan(ids) }
                                    }
                                } else {
                                    if (!moved && hypot(ch.position.x - down.position.x, ch.position.y - down.position.y) > slop) { moved = true }
                                    if (tool != Tool.Tap && moved) {
                                        ch.consume()
                                        hit(ch.position)?.let { w -> current = w; onPreview(MushafEngine.spanBetween(p.lines, anchor, w).toSet()) }
                                    }
                                }
                            }
                            if (tool != Tool.Tap && !finished) onPreview(emptySet())
                        }
                    },
            ) {
                Column(Modifier.fillMaxSize(), verticalArrangement = if (opening) Arrangement.spacedBy(with(density) { (sizePx * 0.4f).toDp() }, Alignment.CenterVertically) else Arrangement.Top) {
                    shown.forEach { line ->
                        val rowMod = if (rowH != null) Modifier.fillMaxWidth().height(rowH) else Modifier.fillMaxWidth()
                        when (line.kind) {
                            MushafEngine.LineKind.Empty -> Box(rowMod)
                            MushafEngine.LineKind.Banner -> BannerLine(line.surah, p.surahNames, sizePx, density, pal, rowMod)
                            MushafEngine.LineKind.Basmala -> BasmalaLine(p, sizePx, density, pal, rowMod)
                            MushafEngine.LineKind.Text -> TextLine(
                                line, page, p, sizePx, linesW, density, pal, rowMod, measurer, session, tick, preview, inRange, rects, origin,
                                centered = MushafEngine.isCenteredLine(page, line.n),
                            )
                        }
                    }
                }

                kindMenuFor?.let { (id, at) ->
                    val localAt = at
                    Box(Modifier.offset(with(density) { localAt.x.toDp() }, with(density) { (localAt.y.toDp() - menuHeightPad) })) {
                        DropdownMenu(expanded = true, onDismissRequest = onDismissKind, offset = DpOffset(0.dp, 0.dp)) {
                            listOf(MarkKind.Word to "كلمة", MarkKind.Harakah to "حركة", MarkKind.AyahStart to "بداية آية").forEach { (k, label) ->
                                DropdownMenuItem(text = { Text(label) }, onClick = { onKind(id, k) })
                            }
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ lines

private fun glyphStyle(family: FontFamily, sizePx: Float, density: Density, color: Color) =
    TextStyle(fontFamily = family, fontSize = with(density) { (sizePx).toSp() }, color = color, textDirection = TextDirection.Rtl, lineHeight = with(density) { ((sizePx * 1.5f)).toSp() })

@Composable
private fun TextLine(
    line: MushafEngine.Line, page: Int, p: MushafStore.PreparedPage, sizePx: Float, linesW: Float, density: Density, pal: MushafPalette, rowMod: Modifier,
    measurer: androidx.compose.ui.text.TextMeasurer, session: MushafEngine.Session, tick: Int, preview: Set<String>, inRange: (Int, Int, Int) -> Boolean,
    rects: HashMap<String, Rect>, origin: Offset, centered: Boolean,
) {
    // lines are drawn edge to edge at one size; only a line wider than the text block is made smaller, by one ratio (the print never stretches words)
    val items = line.items
    val natural = remember(line, sizePx) {
        items.sumOf { measurer.measure(it.glyph, glyphStyle(p.families[it.fontPage] ?: FontFamily.Default, sizePx, density, pal.pageInk), softWrap = false, maxLines = 1).size.width.toDouble() } +
            (if (centered) sizePx * 0.25 * max(0, items.size - 1) else 0.0)
    }
    val k = MushafEngine.overflowScale(natural, linesW.toDouble()).toFloat()
    val lineSize = sizePx * k
    Row(
        rowMod,
        horizontalArrangement = if (centered) Arrangement.spacedBy(with(density) { (lineSize * 0.25f).toDp() }, Alignment.CenterHorizontally) else Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { w ->
            val family = p.families[w.fontPage] ?: FontFamily.Default
            if (w.isEnd) {
                Text(w.glyph, style = glyphStyle(family, lineSize, density, pal.pageInk), softWrap = false, modifier = Modifier.clearSemantics2())
            } else {
                MarkedWord(w, family, lineSize, density, pal, session.getMark(w.id), w.id in preview, inRange(w.surah, w.ayah, page), tick, rects)
            }
        }
    }
}

private fun Modifier.clearSemantics2(): Modifier = this.semantics(mergeDescendants = false) { contentDescription = "" }

@Composable
private fun MarkedWord(
    w: MushafEngine.Item, family: FontFamily, sizePx: Float, density: Density, pal: MushafPalette, mark: MushafEngine.Mark?, previewed: Boolean, inRange: Boolean,
    tick: Int, rects: HashMap<String, Rect>,
) {
    val markColor = when (mark?.type) { MarkType.Mistake -> pal.mistake; MarkType.Prompt -> pal.prompt; null -> Color.Unspecified }
    val markBg = when (mark?.type) { MarkType.Mistake -> pal.mistakeBg; MarkType.Prompt -> pal.promptBg; null -> if (previewed) pal.rangeBg else Color.Transparent }
    val desc = w.text + (mark?.let { " — " + (if (it.type == MarkType.Mistake) "خطأ" else "تلقين") + (if (it.kind == MarkKind.Harakah) " في الحركة" else if (it.kind == MarkKind.AyahStart) " في بداية الآية" else "") } ?: "")
    Box(
        Modifier
            .onGloballyPositioned { rects[w.id] = it.boundsInRoot() }
            .drawBehind {
                val r = CornerRadius(size.height * 0.12f)
                if (markBg != Color.Transparent) drawRoundRect(markBg, size = Size(size.width, size.height), cornerRadius = r)
                if (previewed) drawRoundRect(pal.accent, size = Size(size.width, size.height), cornerRadius = r, style = Stroke(1.5f * density.density, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))))
                if (mark != null) {
                    val y = size.height * 0.88f; val t = max(2f, sizePx * 0.06f)
                    drawLine(markColor, Offset(0f, y), Offset(size.width, y), strokeWidth = t, pathEffect = if (mark.type == MarkType.Prompt) PathEffect.dashPathEffect(floatArrayOf(t * 2.5f, t * 1.6f)) else null)
                }
            }
            .semantics { role = Role.Button; contentDescription = desc },
    ) {
        Text(w.glyph, style = glyphStyle(family, sizePx, density, if (mark != null) pal.pageInk else pal.pageInk), softWrap = false)
        if (mark != null) {
            // a letter beside the underline so the mark never relies on colour alone: × mistake, ت prompt
            Text(
                if (mark.type == MarkType.Mistake) "×" else "ت", color = markColor, fontWeight = FontWeight.ExtraBold,
                fontSize = with(density) { ((sizePx * 0.42f)).toSp() }, modifier = Modifier.align(Alignment.TopEnd).offset(x = 2.dp, y = (-2).dp),
            )
            if (mark.kind == MarkKind.Harakah) Text("ـَ", color = pal.frame, fontWeight = FontWeight.Bold, fontSize = with(density) { ((sizePx * 0.4f)).toSp() }, modifier = Modifier.align(Alignment.BottomStart))
        }
    }
}

@Composable
private fun BannerLine(surah: Int, surahNames: FontFamily?, sizePx: Float, density: Density, pal: MushafPalette, rowMod: Modifier) {
    val h = with(density) { (sizePx * 1.75f).toDp() }
    val name = com.mohammedsamir.halaqati.quran.QuranMetadata.surahName(surah)
    Box(rowMod, contentAlignment = Alignment.Center) {
        Row(
            Modifier.fillMaxWidth().height(h).background(pal.rangeBg, RoundedCornerShape(with(density) { (sizePx * 0.3f).toDp() }))
                .border(with(density) { (sizePx * 0.07f).toDp() }, pal.rule, RoundedCornerShape(with(density) { (sizePx * 0.3f).toDp() }))
                .semantics { contentDescription = "سورة $name"; role = Role.Image },
            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
        ) {
            if (surahNames != null) {
                val st = TextStyle(fontFamily = surahNames, fontSize = with(density) { ((sizePx * 1.15f)).toSp() }, color = pal.frame)
                Text("surah", style = st, softWrap = false)
                Box(Modifier.width(with(density) { (sizePx * 0.45f).toDp() }))
                Text(surah.toString().padStart(3, '0'), style = st, softWrap = false)
            } else {
                Text("سورة $name", color = pal.frame, fontWeight = FontWeight.Bold, fontSize = with(density) { ((sizePx * 0.8f)).toSp() })
            }
        }
    }
}

@Composable
private fun BasmalaLine(p: MushafStore.PreparedPage, sizePx: Float, density: Density, pal: MushafPalette, rowMod: Modifier) {
    val family = p.families[1]
    Row(
        rowMod.semantics { contentDescription = "بسم الله الرحمن الرحيم"; role = Role.Image },
        horizontalArrangement = Arrangement.spacedBy(with(density) { (sizePx * 0.2f).toDp() }, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically,
    ) {
        if (family != null) p.basmala.forEach { Text(it, style = glyphStyle(family, sizePx, density, pal.pageInk), softWrap = false) }
    }
}
