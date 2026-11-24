package com.mohammedsamir.halaqati.quran

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class MushafOfflineTest {
    @get:Rule val tmp = TemporaryFolder()

    /** Fake server: an API page n holds one verse whose single word sits on page n; every font is 2 KB. */
    private class Fake(var online: Boolean = true, val failApiPages: Set<Int> = emptySet()) {
        val calls = ArrayList<String>()
        fun fetch(url: String, @Suppress("UNUSED_PARAMETER") timeout: Int): ByteArray {
            calls += url
            if (!online) throw IOException("offline")
            val n = Regex("by_page/(\\d+)").find(url)?.groupValues?.get(1)?.toInt()
            if (n != null) {
                if (n in failApiPages) throw IOException("HTTP 503")
                return ("""{"verses":[{"verse_key":"2:$n","juz_number":1,"words":[{"char_type_name":"word","position":1,"page_number":$n,"v2_page":$n,"line_number":3,"code_v2":"x","text_uthmani":"y"}]}]}""").toByteArray()
            }
            return ByteArray(2048) { 1 }
        }
    }

    private fun store(dir: File, fake: Fake) = MushafStore(dir) { u, t -> fake.fetch(u, t) }

    @Test fun downloadedPagesAreOnTheDeviceAndNotFetchedTwice() = runBlocking {
        val fake = Fake(); val s = store(tmp.newFolder(), fake)
        val out = s.downloadPages(listOf(10, 11))
        assertEquals(2, out.ok); assertTrue(out.failed.isEmpty())
        assertTrue(s.isPageDownloaded(10) && s.isPageDownloaded(11)); assertFalse(s.isPageDownloaded(12))
        val first = fake.calls.size
        s.downloadPages(listOf(10, 11))
        assertEquals("a finished page costs no network", first, fake.calls.size)
        assertEquals(2, s.packStatus().pages)
    }

    @Test fun aDownloadedPageOpensWithoutNetworkEvenWhenItsDataIsOlderThanAWeek() = runBlocking {
        val dir = tmp.newFolder(); val fake = Fake()
        store(dir, fake).downloadPages(listOf(20))
        File(dir, "pages-v2").listFiles()!!.forEach { f -> f.writeText(f.readText().replace(Regex("\"at\":\\d+"), "\"at\":0")) }
        val offline = store(dir, Fake(online = false))
        val page = offline.loadPage(20)
        assertTrue("stale copy is the fallback when there is no network", page.isSuccess)
        assertTrue(page.getOrThrow().items.isNotEmpty())
    }

    @Test fun aPageNeverDownloadedFailsCleanlyOffline() = runBlocking {
        val s = store(tmp.newFolder(), Fake(online = false))
        assertTrue(s.loadPage(30).isFailure)
        assertFalse(s.isPageDownloaded(30))
    }

    @Test fun aFailedPageIsReportedAndTheRestStillDownload() = runBlocking {
        val s = store(tmp.newFolder(), Fake(failApiPages = setOf(41)))
        // a page is built from its own API page and both neighbours, so 40 and 42 fail with 41 (never a half-drawn page); 38 and 44 do not
        val out = s.downloadPages(listOf(38, 41, 44), onProgress = { _, _ -> })
        assertEquals(listOf(41), out.failed)
        assertEquals(2, out.ok)
        assertTrue(s.isPageDownloaded(38) && s.isPageDownloaded(44) && !s.isPageDownloaded(41))
    }

    @Test fun deletingThePackRemovesEveryPageAndFont() = runBlocking {
        val dir = tmp.newFolder(); val s = store(dir, Fake())
        s.downloadPages(listOf(5, 6))
        s.deletePack()
        assertEquals(0, s.packStatus().pages); assertEquals(0L, s.packStatus().bytes)
        assertTrue(store(dir, Fake(online = false)).loadPage(5).isFailure)
    }

    @Test fun juzPagesAndSizeEstimate() {
        assertEquals(1, MushafOfflinePlan.juzPages(1).first)
        assertEquals(604, MushafOfflinePlan.juzPages(30).last)
        assertEquals(604, MushafOfflinePlan.pagesFor((1..30).toSet()).size)
        assertEquals(3 * MushafOfflinePlan.APPROX_BYTES_PER_PAGE, MushafOfflinePlan.estimateBytes(3))
    }
}
