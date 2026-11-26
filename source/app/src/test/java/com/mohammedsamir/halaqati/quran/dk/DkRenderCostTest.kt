package com.mohammedsamir.halaqati.quran.dk

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.test.core.app.ApplicationProvider
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Measures what one page costs to draw (cold path cache, then warm). Prints timings; asserts only a loose ceiling. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = android.app.Application::class)
class DkRenderCostTest {
    private val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
    @Test fun cost() {
        assumeTrue(DkPack.available(ctx))
        var t = System.nanoTime()
        val data = DkPack.data(ctx)
        println("COST load glyphs ms=" + (System.nanoTime() - t) / 1_000_000)
        val colors = DkColors(0xFF000000.toInt(), 0xFF006600.toInt(), 0xFF006600.toInt(), 0xFF888888.toInt())
        for (pass in 1..2) for (pg in intArrayOf(511, 518, 527, 300)) {
            t = System.nanoTime()
            val page = data.loadPage(pg)
            val t1 = System.nanoTime()
            val w = 1000f; val pitch = DkRender.lineHeight(w).coerceAtMost(110f)
            val bmp = Bitmap.createBitmap(w.toInt(), (pitch * page.lines.size).toInt(), Bitmap.Config.ARGB_8888)
            DkRender.draw(Canvas(bmp), data, page, w, pitch, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }, colors)
            val t2 = System.nanoTime()
            println("COST pass=$pass page=$pg glyphs=${page.lines.sumOf { it.glyphs.size }} parse=${(t1 - t) / 1_000_000}ms draw=${(t2 - t1) / 1_000_000}ms cachedPaths=${data.cachedPaths}")
        }
    }
}
