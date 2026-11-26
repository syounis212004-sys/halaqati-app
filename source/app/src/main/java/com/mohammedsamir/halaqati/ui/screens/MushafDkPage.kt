package com.mohammedsamir.halaqati.ui.screens

import android.graphics.Paint
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mohammedsamir.halaqati.quran.MushafEngine
import com.mohammedsamir.halaqati.quran.MushafEngine.MarkKind
import com.mohammedsamir.halaqati.quran.MushafEngine.MarkType
import com.mohammedsamir.halaqati.quran.QuranMetadata
import com.mohammedsamir.halaqati.quran.dk.DkBitmaps
import com.mohammedsamir.halaqati.quran.dk.DkColors
import com.mohammedsamir.halaqati.quran.dk.DkData
import com.mohammedsamir.halaqati.quran.dk.DkPage
import com.mohammedsamir.halaqati.quran.dk.DkRender
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** One page of the experimental DigitalKhatt viewer: its precomputed lines and the word id of every DK word (see DkPack.idsOf). */
internal class DkPrepared(val page: Int, val data: DkData, val dk: DkPage, val ids: List<String>) {
    /** Markable word ids in reading order (ayah-end markers are not markable, merged pairs appear once). */
    val wordIds: List<String> = ids.filter { !it.endsWith(":end") }.distinct()
    fun span(a: String, b: String): List<String> {
        val i = wordIds.indexOf(a); val j = wordIds.indexOf(b)
        return if (i < 0 || j < 0) emptyList() else wordIds.subList(min(i, j), max(i, j) + 1)
    }
}

/**
 * The page drawn from DigitalKhatt outlines inside the app's own page frame, palette and marking tools. Same gestures and marks as the current viewer:
 * tap, long press (kind menu), drag to highlight or erase. The line pitch is the app's 15-row pitch (never wider than DigitalKhatt's own); glyphs are never stretched.
 */
@Composable
internal fun DkReadyPage(
    p: DkPrepared, page: Int, pal: MushafPalette, tick: Int, tool: Tool, session: MushafEngine.Session, preview: Set<String>, inRange: (Int, Int, Int) -> Boolean,
    onTapWord: (String) -> Unit, onLongWord: (String, Offset) -> Unit, onPreview: (Set<String>) -> Unit, onSpan: (List<String>) -> Unit,
    kindMenuFor: Pair<String, Offset>?, onKind: (String, MarkKind) -> Unit, onDismissKind: () -> Unit,
    isCurrent: Boolean = true, onZoomChanged: (Boolean) -> Unit = {},
) {
    val density = LocalDensity.current
    val first = p.wordIds.firstOrNull()?.let { MushafEngine.parseWordId(it) }
    val inside = remember(p, tick) { p.wordIds.count { id -> MushafEngine.parseWordId(id)?.let { inRange(it.first, it.second, page) } == true } }
    val juz = (1..30).firstOrNull { page in com.mohammedsamir.halaqati.quran.MushafOfflinePlan.juzPages(it) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(first?.let { "سورة " + QuranMetadata.surahName(it.first) } ?: "", color = pal.frame, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text(if (p.wordIds.isEmpty() || inside == 0) "" else if (inside == p.wordIds.size) "ضمن المقرر" else "جزء من المقرر", color = pal.accent, fontSize = 12.sp)
            Text(juz?.let { "الجزء ${digits(it)}" } ?: "", color = pal.frame, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            // equal breathing room inside the page frame on every side (the printed text block never touches the border)
            val areaW = constraints.maxWidth * 0.965f; val areaH = constraints.maxHeight * 0.955f
            val rowsPitch = areaH / 15f
            val wPx = min(areaW, rowsPitch / 0.11f)
            val pitch = min(rowsPitch, DkRender.lineHeight(wPx))
            val pageHpx = pitch * p.dk.lines.size
            val ink = pal.pageInk.toArgb()
            val colors = remember(pal) { DkColors(ink = ink, header = pal.frame.toArgb(), ayah = pal.frame.toArgb(), sign = pal.rule.toArgb()) }
            // the page bitmap: from the cache at once, otherwise drawn on the render pool. A draw that takes too long is retried (never a silent blank page).
            val bmpKey = DkBitmaps.key(page, wPx.toInt(), pitch.toInt(), colors)
            var bitmap by remember(bmpKey) { mutableStateOf(DkBitmaps.peek(bmpKey)) }
            var drawFailed by remember(bmpKey) { mutableStateOf(false) }
            var drawAttempt by remember(bmpKey) { androidx.compose.runtime.mutableIntStateOf(0) }
            androidx.compose.runtime.LaunchedEffect(bmpKey, drawAttempt) {
                drawFailed = false
                var tries = 0
                while (bitmap == null && tries < 3) {
                    val got = try {
                        withTimeoutOrNull(10_000L) { DkBitmaps.get(bmpKey, p.data, p.dk, wPx.toInt(), pitch, colors) }
                    } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { null }
                    if (got != null) bitmap = got else { tries++; kotlinx.coroutines.delay(250L * tries) }
                }
                if (bitmap == null) drawFailed = true
            }
            val rects = remember(p, wPx, pitch) { DkRender.rectsById(p.ids, DkRender.wordRects(p.dk, wPx.toInt().toFloat(), pitch)).mapValues { Rect(it.value.left, it.value.top, it.value.right, it.value.bottom) } }
            val wDp = with(density) { wPx.toInt().toDp() }; val hDp = with(density) { pageHpx.toInt().toDp() }

            var zoom by remember(p) { androidx.compose.runtime.mutableFloatStateOf(1f) }
            var pan by remember(p) { mutableStateOf(Offset.Zero) }
            val zoomedNow = zoom > 1.02f
            androidx.compose.runtime.LaunchedEffect(zoomedNow) { onZoomChanged(zoomedNow) }
            androidx.compose.runtime.LaunchedEffect(isCurrent) { if (!isCurrent) { zoom = 1f; pan = Offset.Zero } }
            Box(Modifier.fillMaxSize().clipToBounds().zoomPinch(enabled = isCurrent, tapToolOnly = tool == Tool.Tap, zoom = { zoom }, pan = { pan }) { z, pn -> zoom = z; pan = pn }, contentAlignment = Alignment.Center) {
            Box(
                Modifier.size(wDp, hDp)
                    .graphicsLayer { scaleX = zoom; scaleY = zoom; translationX = pan.x; translationY = pan.y }
                    .semantics { contentDescription = "صفحة ${digits(page)}" }
                    .drawBehind {
                        // the sura-name cartouche behind the sura-name line (DigitalKhatm draws the name only)
                        p.dk.lines.forEachIndexed { i, line -> if (line.type == 1) drawSuraOrnament(i * pitch + pitch * 0.06f, pitch * 0.88f, pal) }
                        for ((id, r) in rects) {
                            val mark = session.getMark(id)
                            val prev = id in preview
                            val bg = when (mark?.type) { MarkType.Mistake -> pal.mistakeBg; MarkType.Prompt -> pal.promptBg; null -> if (prev) pal.rangeBg else androidx.compose.ui.graphics.Color.Transparent }
                            val corner = CornerRadius(r.height * 0.12f)
                            if (bg != androidx.compose.ui.graphics.Color.Transparent) drawRoundRect(bg, r.topLeft, r.size, corner)
                            if (prev) drawRoundRect(pal.accent, r.topLeft, r.size, corner, style = Stroke(1.5f * density.density, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))))
                            if (mark != null) {
                                val c = if (mark.type == MarkType.Mistake) pal.mistake else pal.prompt
                                val t = max(2f, r.height * 0.05f); val y = r.bottom - r.height * 0.08f
                                drawLine(c, Offset(r.left, y), Offset(r.right, y), strokeWidth = t, pathEffect = if (mark.type == MarkType.Prompt) PathEffect.dashPathEffect(floatArrayOf(t * 2.5f, t * 1.6f)) else null)
                            }
                        }
                    }
                    .pointerInput(p, tool, rects) {
                        val slop = 10.dp.toPx()
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            fun hit(pos: Offset): String? = rects.entries.firstOrNull { it.value.contains(pos) }?.key
                            val anchor = hit(down.position) ?: return@awaitEachGesture
                            var current = anchor; var moved = false; var long = false
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
                                        else -> onSpan(p.span(anchor, current))
                                    }
                                } else {
                                    if (!moved && hypot(ch.position.x - down.position.x, ch.position.y - down.position.y) > slop) moved = true
                                    if (tool != Tool.Tap && moved) {
                                        ch.consume()
                                        hit(ch.position)?.let { w -> current = w; onPreview(p.span(anchor, w).toSet()) }
                                    }
                                }
                            }
                            if (tool != Tool.Tap && !finished) onPreview(emptySet())
                        }
                    },
            ) {
                bitmap?.let { Image(it.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize()) }
                if (bitmap == null) {
                    if (drawFailed) Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("تعذّر رسم الصفحة", color = pal.pageInk, fontWeight = FontWeight.Bold)
                        androidx.compose.material3.TextButton(onClick = { drawAttempt++ }) { Text("إعادة المحاولة") }
                    } else Text("جارٍ رسم الصفحة…", color = pal.pageInk.copy(alpha = 0.6f), modifier = Modifier.align(Alignment.Center))
                }
                kindMenuFor?.let { (id, at) ->
                    Box(Modifier.offset(with(density) { at.x.toDp() }, with(density) { at.y.toDp() - 56.dp })) {
                        DropdownMenu(expanded = true, onDismissRequest = onDismissKind, offset = DpOffset(0.dp, 0.dp)) {
                            listOf(MarkKind.Word to "كلمة", MarkKind.Harakah to "حركة", MarkKind.AyahStart to "بداية آية").forEach { (k, label) ->
                                DropdownMenuItem(text = { Text(label) }, onClick = { onKind(id, k) })
                            }
                        }
                    }
                }
            }
            if (zoomedNow) androidx.compose.material3.FilledTonalButton(
                onClick = { zoom = 1f; pan = Offset.Zero }, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
            ) { Text("إعادة الحجم") }
            }
        }
    }
}

/**
 * Two-finger pinch zoom (1x to 4x) with pan. A single finger pans only while zoomed and only in the tap tool, after it has clearly moved, so taps,
 * long presses and highlighting keep working. Runs in the Initial pass so a pinch never reaches the marking gestures underneath.
 */
internal fun Modifier.zoomPinch(
    enabled: Boolean, tapToolOnly: Boolean, zoom: () -> Float, pan: () -> Offset, onChange: (Float, Offset) -> Unit,
): Modifier = if (!enabled) this else pointerInput(tapToolOnly) {
    val slop = viewConfiguration.touchSlop
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = androidx.compose.ui.input.pointer.PointerEventPass.Initial)
        var travelled = 0f
        do {
            val ev = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
            val multi = ev.changes.count { it.pressed } >= 2
            val z0 = zoom()
            if (multi || (z0 > 1.02f && tapToolOnly)) {
                val zc = if (multi) ev.calculateZoom() else 1f
                val pc = ev.calculatePan()
                travelled += hypot(pc.x, pc.y)
                if (multi || travelled > slop) {
                    val z1 = (z0 * zc).coerceIn(1f, 4f)
                    val centre = Offset(size.width / 2f, size.height / 2f)
                    val c = if (multi) ev.calculateCentroid(useCurrent = false) else centre
                    var p1 = (c - centre) * (1f - z1 / z0) + pan() * (z1 / z0) + pc
                    val maxX = (z1 - 1f) * size.width / 2f; val maxY = (z1 - 1f) * size.height / 2f
                    p1 = Offset(p1.x.coerceIn(-maxX, maxX), p1.y.coerceIn(-maxY, maxY))
                    if (z1 <= 1.001f) p1 = Offset.Zero
                    onChange(z1, p1)
                    ev.changes.forEach { if (it.positionChanged()) it.consume() }
                }
            }
        } while (ev.changes.any { it.pressed })
    }
}
