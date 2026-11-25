package lab.dkmushaf

import android.graphics.Paint
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.Canvas
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.runtime.CompositionLocalProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) { LabApp(DkData(applicationContext)) } }
    }
}

private const val PAGES = 604

/** A tappable word box in canvas pixels. */
private class WordBox(val rect: Rect)

@Composable
fun LabApp(data: DkData) {
    var ready by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { withContext(Dispatchers.Default) { data.loadGlyphs() }; ready = true }
    var night by remember { mutableStateOf(false) }
    val bg = if (night) Color(0xFF101418) else Color(0xFFFFFCF3)
    val fg = if (night) Color(0xFFE8E0CC) else Color.Black
    val pager = rememberPagerState(initialPage = 299) { PAGES }
    val scope = rememberCoroutineScope()
    var jump by remember { mutableStateOf("") }
    var zoomed by remember { mutableStateOf(setOf<Int>()) }
    val lastMs = remember { LongArray(2) }
    Column(Modifier.fillMaxSize().background(bg).padding(top = 28.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("صفحة ${pager.currentPage + 1} / $PAGES", color = fg, fontSize = 14.sp)
            OutlinedTextField(jump, { jump = it.filter { c -> c.isDigit() }.take(3) }, Modifier.width(84.dp), singleLine = true, label = { Text("انتقال", fontSize = 10.sp) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            Button({ jump.toIntOrNull()?.let { n -> scope.launch { pager.scrollToPage((n - 1).coerceIn(0, PAGES - 1)) } } }) { Text("اذهب") }
            Button({ night = !night }) { Text(if (night) "نهاري" else "ليلي") }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (!ready) Text("تحميل المنحنيات…", color = fg, modifier = Modifier.align(Alignment.Center))
            else HorizontalPager(pager, Modifier.fillMaxSize(), reverseLayout = false, beyondViewportPageCount = 1,
                userScrollEnabled = pager.currentPage !in zoomed, key = { it }) { idx ->
                DkPageView(data, idx + 1, fg, night, lastMs) { z -> zoomed = if (z) zoomed + idx else zoomed - idx }
            }
        }
        val rt = Runtime.getRuntime()
        Text("رسم ${lastMs[0]} مللي ثانية · تحميل ${lastMs[1]} مللي ثانية · مسارات مخزنة ${data.cachedPaths} · ذاكرة ${(rt.totalMemory() - rt.freeMemory()) shr 20} MB",
            color = fg.copy(alpha = 0.7f), fontSize = 10.sp, modifier = Modifier.padding(horizontal = 8.dp))
        Text("نموذج تجريبي خاص. المنحنيات والتخطيط: DigitalKhatt (خط Madina بترخيص OFL 1.1، شيفرة المشروع MIT). لا يُنشر للمستخدمين.",
            color = fg.copy(alpha = 0.7f), fontSize = 10.sp, modifier = Modifier.padding(8.dp))
    }
}

@Composable
private fun DkPageView(data: DkData, n: Int, ink: Color, night: Boolean, lastMs: LongArray, onZoom: (Boolean) -> Unit) {
    var page by remember(n) { mutableStateOf<DkPage?>(null) }
    LaunchedEffect(n) { val t = System.nanoTime(); page = withContext(Dispatchers.Default) { data.loadPage(n) }; lastMs[1] = (System.nanoTime() - t) / 1_000_000 }
    var scale by remember(n) { mutableStateOf(1f) }
    var off by remember(n) { mutableStateOf(Offset.Zero) }
    var picked by remember(n) { mutableIntStateOf(-1) }
    val p = page ?: return
    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds()) {
        val wPx = with(LocalDensity.current) { maxWidth.toPx() }
        val lh = DkRender.lineHeight(wPx)
        val boxes = remember(p, wPx) { DkRender.wordRects(p, wPx).map { WordBox(Rect(it.second.left, it.second.top, it.second.right, it.second.bottom)) } }
        val paint = remember(ink) { Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink.hashCode(); style = Paint.Style.FILL } }
        paint.color = android.graphics.Color.argb((ink.alpha * 255).toInt(), (ink.red * 255).toInt(), (ink.green * 255).toInt(), (ink.blue * 255).toInt())
        Box(Modifier.fillMaxSize()
            .pointerInput(n) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val ev = awaitPointerEvent()
                        val pressed = ev.changes.count { it.pressed }
                        if (pressed >= 2 || scale > 1f) {
                            val z = ev.calculateZoom(); val pan = ev.calculatePan()
                            val ns = (scale * z).coerceIn(1f, 6f)
                            val w = size.width.toFloat(); val h = size.height.toFloat()
                            var no = off + pan
                            val mx = (ns - 1f) * w / 2f; val my = (ns - 1f) * h / 2f
                            no = Offset(no.x.coerceIn(-mx, mx), no.y.coerceIn(-my, my))
                            scale = ns; off = if (ns == 1f) Offset.Zero else no; onZoom(ns > 1f)
                            ev.changes.forEach { ch: PointerInputChange -> if (ch.positionChanged()) ch.consume() }
                        }
                    } while (ev.changes.any { it.pressed })
                }
            }
            .pointerInput(n) { detectTapGestures(onDoubleTap = { if (scale > 1f) { scale = 1f; off = Offset.Zero; onZoom(false) } else { scale = 2.5f; onZoom(true) } }) }
            .graphicsLayer { scaleX = scale; scaleY = scale; translationX = off.x; translationY = off.y }
            .pointerInput(n, boxes) { detectTapGestures { pt -> picked = boxes.indexOfFirst { it.rect.contains(pt) } } }
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val t0 = System.nanoTime()
                if (picked in boxes.indices) drawRect(Color(0x55FFC107), boxes[picked].rect.topLeft, boxes[picked].rect.size)
                drawIntoCanvas { c -> DkRender.draw(c.nativeCanvas, data, p, wPx, paint) }
                lastMs[0] = (System.nanoTime() - t0) / 1_000_000
            }
        }
    }
}
