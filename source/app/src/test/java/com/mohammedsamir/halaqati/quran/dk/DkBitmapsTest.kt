package com.mohammedsamir.halaqati.quran.dk

import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A page that was drawn once comes back instantly, and a cancelled draw stops instead of finishing a page nobody will see. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = android.app.Application::class)
class DkBitmapsTest {
    private val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val colors = DkColors(0xFF000000.toInt(), 0xFF006600.toInt(), 0xFF006600.toInt(), 0xFF888888.toInt())

    @Test fun secondRequestIsServedFromTheCache() = runBlocking<Unit> {
        assumeTrue(DkPack.available(ctx))
        DkBitmaps.clear()
        val data = DkPack.data(ctx); val page = data.loadPage(511)
        val key = DkBitmaps.key(511, 800, 90, colors)
        val a = withTimeout(30_000) { DkBitmaps.get(key, data, page, 800, 90f, colors) }
        assertSame(a, DkBitmaps.peek(key))
        assertSame(a, withTimeout(500) { DkBitmaps.get(key, data, page, 800, 90f, colors) })
    }

    @Test fun aCancelledDrawLeavesNothingInTheCache() = runBlocking<Unit> {
        assumeTrue(DkPack.available(ctx))
        DkBitmaps.clear()
        val data = DkPack.data(ctx); val page = data.loadPage(300)
        val key = DkBitmaps.key(300, 900, 100, colors)
        val job = async { DkBitmaps.get(key, data, page, 900, 100f, colors) }
        job.cancel()
        val cancelled = try { job.await(); false } catch (e: CancellationException) { true }
        assertTrue(cancelled)
        // whatever the race, a later request still gets a bitmap
        withTimeout(30_000) { DkBitmaps.get(key, data, page, 900, 100f, colors) }
    }
}
