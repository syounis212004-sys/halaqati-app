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

/** What the recitation form already knows, so the mushaf can show the official V7 estimate next to the page. */
data class MushafScore(val safety: Double, val label: String, val pages: Double)

private enum class Tool { Tap, Highlight, Erase }

private class MushafPalette(dark: Boolean) {
    val pageBg = if (dark) Color(0xFF10201D) else Color(0xFFFFFCF3)
    val pageInk = if (dark) Color(0xFFEAF3F0) else Color(0xFF1F2A27)
    val frame = if (dark) Color(0xFF5EEAD4) else Color(0xFF134E4A)
    val rule = if (dark) Color(0xFFFBBF24) else Color(0xFFB45309)
    val mistake = if (dark) Color(0xFFF87171) else Color(0xFFDC2626)
    val mistakeBg = mistake.copy(alpha = if (dark) 0.20f else 0.16f)
    val prompt = if (dark) Color(0xFF60A5FA) else Color(0xFF2563EB)
    val promptBg = prompt.copy(alpha = if (dark) 0.20f else 0.14f)
    val rangeBg = (if (dark) Color(0xFF5EEAD4) else Color(0xFF0D9488)).copy(alpha = if (dark) 0.10f else 0.10f)
    val accent = if (dark) Color(0xFF5EEAD4) else Color(0xFF0F766E)
}

private fun digits(n: Any?): String = n.toString().map { if (it in '0'..'9') "٠١٢٣٤٥٦٧٨٩"[it - '0'] else it }.joinToString("")

private sealed interface PageState {
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
    val context = LocalContext.current
    val store = remember { MushafStore.get(context) }
    val dark = isSystemInDarkTheme()
    val pal = remember(dark) { MushafPalette(dark) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    var page by remember { mutableIntStateOf(session.page) }
    var state by remember { mutableStateOf<PageState>(PageState.Loading) }
    var tool by remember { mutableStateOf(Tool.Tap) }
    var type by remember { mutableStateOf(MarkType.Mistake) }
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

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.systemBarsPadding().fillMaxSize().padding(horizontal = 8.dp)) {
                    // ---- top bar: close, page navigation, jump to the assigned range
                    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.Close, contentDescription = "إغلاق المصحف") }
                        Text("المصحف", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        TextButton(onClick = { page = MushafEngine.clampPage(page - 1) }, enabled = page > 1, modifier = Modifier.heightIn(min = 48.dp)) { Text("السابقة") }
                        TextButton(onClick = { jumpDialog = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text("${digits(page)} / ٦٠٤", fontWeight = FontWeight.Bold)
                        }
                        TextButton(onClick = { page = MushafEngine.clampPage(page + 1) }, enabled = page < MushafEngine.TOTAL_PAGES, modifier = Modifier.heightIn(min = 48.dp)) { Text("التالية") }
                    }
                    if (rangeStartPage != null && rangeStartPage != page) {
                        TextButton(onClick = { page = rangeStartPage }, modifier = Modifier.align(Alignment.End).heightIn(min = 40.dp)) { Text("إلى المقرر (صفحة ${digits(rangeStartPage)})") }
                    }

                    // ---- official score preview (V7)
                    ScoreStrip(session, tick, counters(), score(), pal)

                    // ---- the page
                    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
                        val availW = maxWidth; val availH = maxHeight
                        // the printed page is 14:20; use all the room the screen gives it
                        val pageW = min(availW.value, availH.value * 14f / 20f).dp
                        val pageH = pageW * 20f / 14f
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
                    Text(
                        "خطوط المصحف: مجمع الملك فهد لطباعة المصحف الشريف · عبر Quran Foundation",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    )

                    // ---- bottom bar: type first (a count on each), then the tools
                    val c = counters()
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TypeButton("خطأ", "×", c.first, type == MarkType.Mistake, pal.mistake, pal.mistakeBg, dashed = false, Modifier.weight(1f)) { type = MarkType.Mistake }
                        TypeButton("تلقين", "ت", c.second, type == MarkType.Prompt, pal.prompt, pal.promptBg, dashed = true, Modifier.weight(1f)) { type = MarkType.Prompt }
                    }
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        ToolButton("لمس", Icons.Default.TouchApp, tool == Tool.Tap, Modifier.weight(1f)) { tool = Tool.Tap }
                        ToolButton("تظليل", Icons.Default.BorderColor, tool == Tool.Highlight, Modifier.weight(1f)) { tool = Tool.Highlight }
                        ToolButton("ممحاة", Icons.Default.AutoFixNormal, tool == Tool.Erase, Modifier.weight(1f)) { tool = Tool.Erase }
                        IconButton(onClick = { undoRedo(true) }, enabled = remember(tick) { session.canUndo }, modifier = Modifier.size(48.dp)) { Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "تراجع") }
                        IconButton(onClick = { undoRedo(false) }, enabled = remember(tick) { session.canRedo }, modifier = Modifier.size(48.dp)) { Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "إعادة") }
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
    Column(
        Modifier.fillMaxWidth().padding(vertical = 2.dp).background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        if (s == null) {
            Text("اختر المقرر أولاً ليظهر التقدير.", style = MaterialTheme.typography.bodySmall)
        } else {
            Text("التقدير: ${digits(s.safety)}٪ ${s.label}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                (if (s.pages > 0) "محسوب على ${digits((s.pages * 100).roundToInt() / 100.0)} صفحة: ${digits(c.first)} أخطاء و${digits(c.second)} تلقينات. " else "") +
                    "الحد الأعلى للصفحة: خطأ (جملة) وتلقينان",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (over.isNotEmpty()) {
                Text(
                    "تنبيه: " + over.joinToString("، ") { "صفحة ${digits(it.page)} فيها ${digits(it.mistakes)} أخطاء و${digits(it.prompts)} تلقينات" } + " (فوق الحد، فالنتيجة إعادة)",
                    style = MaterialTheme.typography.bodySmall, color = pal.mistake, fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun TypeButton(label: String, glyph: String, count: Int, selected: Boolean, color: Color, bg: Color, dashed: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick, modifier = modifier.heightIn(min = 56.dp).semantics { role = Role.RadioButton; contentDescription = "$label، العدد $count" },
        shape = RoundedCornerShape(16.dp),
        color = if (selected) bg else MaterialTheme.colorScheme.surface,
        border = BorderStroke(2.dp, if (selected) color else MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // the swatch repeats the mark's pattern (solid or dashed underline and a letter) so colour is never the only cue
            Box(
                Modifier.size(28.dp).background(bg, RoundedCornerShape(7.dp)).border(2.dp, color, RoundedCornerShape(7.dp))
                    .drawBehind {
                        val y = size.height - 5.dp.toPx()
                        drawLine(color, Offset(4.dp.toPx(), y), Offset(size.width - 4.dp.toPx(), y), strokeWidth = 2.dp.toPx(), pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(5f, 4f)) else null)
                    },
                contentAlignment = Alignment.Center,
            ) { Text(glyph, color = color, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp) }
            Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(digits(count), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = if (selected) color else MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun ToolButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick, modifier = modifier.heightIn(min = 48.dp).semantics { role = Role.RadioButton },
        shape = RoundedCornerShape(14.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer4()
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
        }
    }
}

@Composable
private fun Spacer4() = Box(Modifier.width(4.dp))

@Composable
private fun MushafPage(
    state: PageState, page: Int, pageW: androidx.compose.ui.unit.Dp, pageH: androidx.compose.ui.unit.Dp, pal: MushafPalette, tick: Int, tool: Tool,
    session: MushafEngine.Session, preview: Set<String>, inRange: (Int, Int, Int) -> Boolean,
    onTapWord: (String) -> Unit, onLongWord: (String, Offset) -> Unit, onPreview: (Set<String>) -> Unit, onSpan: (List<String>) -> Unit,
    kindMenuFor: Pair<String, Offset>?, onKind: (String, MarkKind) -> Unit, onDismissKind: () -> Unit, onRetry: () -> Unit,
) {
    Box(
        Modifier.width(pageW).height(pageH).background(pal.pageBg, RoundedCornerShape(6.dp))
            .border(3.dp, pal.frame, RoundedCornerShape(6.dp)).padding(3.dp).border(1.dp, pal.rule, RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 2.dp),
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
private fun ReadyPage(
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
        Text(digits(page), color = pal.frame, fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
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
