package com.mohammedsamir.halaqati.quran.dk

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.core.app.ApplicationProvider
import com.mohammedsamir.halaqati.quran.MushafEngine
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Experimental DigitalKhatm viewer. Geometry tests are pure; the pack tests run only where scripts/dk/make_dk_assets.py has generated the (uncommitted) pack. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = android.app.Application::class)
class DkPackTest {
    private val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
    private fun g(cp: Int, adv: Float = 100f) = DkGlyph(cp, adv, 0f, 0f, 0f, 0f)

    @Test fun wordRectsFollowSpacesAndSkipHeaderLines() {
        val text = DkLine(0, 0f, 1f, listOf(g(1), g(2), g(32), g(3), g(32), g(4)))
        val header = DkLine(1, 0f, 1f, listOf(g(9), g(32), g(9)))
        val page = DkPage(listOf(header, text))
        val r = DkRender.wordRects(page, 600f, 40f)
        assertEquals(3, r.size)
        assertTrue("right-to-left: first word is rightmost", r[0].left > r[1].left && r[1].left > r[2].left)
        assertTrue("words sit on the text line, not the header", r.all { it.top == 40f && it.bottom == 80f })
    }

    @Test fun idsSharedByAMergedPairJoinAndEndMarkersAreNotMarkable() {
        val ids = listOf("2:181:3", "2:181:3", "2:181:4", "2:181:end")
        val rects = listOf(android.graphics.RectF(50f, 0f, 90f, 10f), android.graphics.RectF(10f, 0f, 45f, 10f), android.graphics.RectF(0f, 20f, 8f, 30f), android.graphics.RectF(0f, 40f, 8f, 50f))
        val m = DkRender.rectsById(ids, rects)
        assertEquals(listOf("2:181:3", "2:181:4"), m.keys.toList())
        assertEquals(10f, m.getValue("2:181:3").left, 0f); assertEquals(90f, m.getValue("2:181:3").right, 0f)
    }

    @Test fun packAvailabilityFollowsTheAssets() {
        val has = File("src/main/assets/dk/ids.bin").exists()
        assertEquals(has, DkPack.available(ctx))
        assertEquals(has, DkPack.supports(ctx, 1))
        assertFalse(DkPack.supports(ctx, 0)); assertFalse(DkPack.supports(ctx, 605))
    }

    @Test fun everyPageIncludingPage177IsServedByThePack() {
        assumeTrue(File("src/main/assets/dk/ids.bin").exists())
        assertTrue((1..604).all { DkPack.supports(ctx, it) })
    }

    @Test fun everyPageHasOneIdPerDkWordAndEveryIdParses() {
        assumeTrue(File("src/main/assets/dk/ids.bin").exists())
        val data = DkPack.data(ctx)
        for (n in 1..604) {
            val ids = DkPack.idsOf(ctx, n); val page = data.loadPage(n)
            assertEquals("page $n words vs ids", ids.size, DkRender.wordRects(page, 1000f, 100f).size)
            ids.filter { !it.endsWith(":end") }.forEach { assertNotNull("$n $it", MushafEngine.parseWordId(it)) }
            assertTrue(ids.any { it.endsWith(":end") })
        }
    }

    @Test fun page300DrawsInkInsideItsFrameAndWordRectsStayInsideToo() {
        assumeTrue(File("src/main/assets/dk/ids.bin").exists())
        val data = DkPack.data(ctx); val page = data.loadPage(300)
        val w = 900; val pitch = 99f; val h = (pitch * page.lines.size).toInt()
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888); Canvas(bmp).also { it.drawColor(Color.WHITE) }
            .let { DkRender.draw(it, data, page, w.toFloat(), pitch, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }) }
        var ink = 0; for (y in 0 until h step 3) for (x in 0 until w step 3) if (bmp.getPixel(x, y) != Color.WHITE) ink++
        assertTrue("ink=$ink", ink > 2000)
        File("build/preview").mkdirs(); File("build/preview/dk_app_p300.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        DkRender.wordRects(page, w.toFloat(), pitch).forEach { assertTrue(it.left >= -1 && it.right <= w + 1) }
    }
}
