package com.mohammedsamir.halaqati.quran.dk

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.util.LruCache
import java.util.concurrent.Executors
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Page bitmaps for the mushaf viewer. Three things keep a page from staying blank:
 *  - a small cache, so going back to a page you just left is instant (the pager drops pages that are two or more away);
 *  - a render pool of its own, so drawing is never queued behind unrelated background work;
 *  - cancellable drawing (checked once per line), so pages the reader already swiped past stop costing time.
 */
object DkBitmaps {
    private val pool = Executors.newFixedThreadPool(2) { r -> Thread(r, "dk-render").apply { isDaemon = true; priority = Thread.NORM_PRIORITY - 1 } }.asCoroutineDispatcher()
    private val cache = object : LruCache<String, Bitmap>(((Runtime.getRuntime().maxMemory() / 8).coerceIn(24L shl 20, 64L shl 20)).toInt()) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    fun key(page: Int, wPx: Int, pitchPx: Int, c: DkColors) = "$page|$wPx|$pitchPx|${c.ink}|${c.header}|${c.ayah}|${c.sign}"

    fun peek(key: String): Bitmap? = cache.get(key)

    suspend fun get(key: String, data: DkData, page: DkPage, wPx: Int, pitch: Float, colors: DkColors): Bitmap {
        cache.get(key)?.let { return it }
        return withContext(pool) {
            cache.get(key) ?: run {
                val ctx = coroutineContext
                val bmp = Bitmap.createBitmap(wPx.coerceAtLeast(1), (pitch * page.lines.size).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colors.ink; style = Paint.Style.FILL }
                DkRender.draw(Canvas(bmp), data, page, wPx.toFloat(), pitch, paint, colors) { ctx.ensureActive() }
                cache.put(key, bmp)
                bmp
            }
        }
    }

    fun clear() = cache.evictAll()
}
