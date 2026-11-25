package lab.dkmushaf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Renders real pages with the app's own renderer to PNG (app/build/preview) and checks geometry. Needs the generated assets (tools/make_assets.py). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = android.app.Application::class)
class DkRenderTest {
    private val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val data = DkData(ctx).also { it.loadGlyphs() }

    private fun render(n: Int, w: Int = 1000): Pair<Bitmap, DkPage> {
        val page = data.loadPage(n)
        val h = (DkRender.lineHeight(w.toFloat()) * page.lines.size + 20).toInt()
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp); c.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; style = Paint.Style.FILL }
        c.translate(0f, 10f)
        DkRender.draw(c, data, page, w.toFloat(), paint)
        File("build/preview").mkdirs()
        File("build/preview/dk_kotlin_p$n.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return bmp to page
    }

    @Test fun page300RendersInsideItsWidthWithInk() {
        val (bmp, page) = render(300)
        var ink = 0; var minX = bmp.width; var maxX = 0
        val px = IntArray(bmp.width * bmp.height); bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        for (i in px.indices) if (Color.red(px[i]) < 128) { ink++; val x = i % bmp.width; if (x < minX) minX = x; if (x > maxX) maxX = x }
        assertTrue("page must have ink, got $ink", ink > 50_000)
        assertTrue("ink must reach the right edge region, maxX=$maxX", maxX > bmp.width * 0.95)
        assertTrue("ink must not start left of the page, minX=$minX", minX >= 0)
        assertEquals(15, page.lines.size)
    }

    @Test fun wordRectsCoverEveryWordOnEveryLine() {
        val page = data.loadPage(300)
        val rects = DkRender.wordRects(page, 1000f)
        val expected = page.lines.sumOf { l -> var n = 0; var inW = false; for (g in l.glyphs) { if (g.cp == 32) inW = false else if (g.adv > 0 && !inW) { n++; inW = true } }; n }
        assertEquals(expected, rects.size)
        assertTrue(rects.all { it.second.width() > 0 && it.second.right <= 1001f })
    }

    @Test fun diversePagesRender() {
        for (n in intArrayOf(1, 2, 50, 77, 255, 589, 604)) { val (bmp, page) = render(n); assertTrue("page $n lines", page.lines.isNotEmpty()); assertTrue(bmp.width == 1000) }
    }
}
