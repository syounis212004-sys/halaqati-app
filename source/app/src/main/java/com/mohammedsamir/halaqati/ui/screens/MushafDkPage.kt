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
import androidx.compose.ui.draw.drawBehind
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
            val bitmap by produceState<Bitmap?>(null, p, wPx.toInt(), pitch.toInt(), colors) {
                value = withContext(Dispatchers.Default) {
                    val bmp = Bitmap.createBitmap(wPx.toInt().coerceAtLeast(1), pageHpx.toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink; style = Paint.Style.FILL }
                    DkRender.draw(android.graphics.Canvas(bmp), p.data, p.dk, wPx.toInt().toFloat(), pitch, paint, colors)
                    bmp
                }
            }
            val rects = remember(p, wPx, pitch) { DkRender.rectsById(p.ids, DkRender.wordRects(p.dk, wPx.toInt().toFloat(), pitch)).mapValues { Rect(it.value.left, it.value.top, it.value.right, it.value.bottom) } }
            val wDp = with(density) { wPx.toInt().toDp() }; val hDp = with(density) { pageHpx.toInt().toDp() }

            Box(
                Modifier.size(wDp, hDp)
                    .semantics { contentDescription = "صفحة ${digits(page)}" }
                    .drawBehind {
                        // the printed sura-name frame behind the sura-name line (DigitalKhatm draws the name only)
                        p.dk.lines.forEachIndexed { i, line ->
                            if (line.type == 1) {
                                val tl = Offset(size.width * 0.02f, i * pitch + pitch * 0.07f); val sz = Size(size.width * 0.96f, pitch * 0.86f); val cr = CornerRadius(pitch * 0.26f)
                                drawRoundRect(pal.rangeBg, tl, sz, cr)
                                drawRoundRect(pal.rule, tl, sz, cr, style = Stroke(max(2f, pitch * 0.045f)))
                            }
                        }
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
        }
    }
}
