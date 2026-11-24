package com.mohammedsamir.halaqati.quran

import com.mohammedsamir.halaqati.quran.MushafEngine.MarkKind
import com.mohammedsamir.halaqati.quran.MushafEngine.MarkType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MushafEngineTest {
    private fun s() = MushafEngine.Session(2)

    @Test fun oneTapIsOneMistakeAndSecondTapRemovesIt() {
        val x = s()
        assertEquals(1, x.markWord("2:5:3", MarkType.Mistake).delta.mist)
        assertEquals(-1, x.markWord("2:5:3", MarkType.Mistake).delta.mist)
        assertTrue(x.isEmpty)
    }

    @Test fun adjacentWordsOfAnAyahAreOnePhrase() {
        val x = s()
        x.markWord("2:5:3", MarkType.Mistake); x.markWord("2:5:4", MarkType.Mistake)
        assertEquals(1, x.totals().mistakes)
        x.markWord("2:5:9", MarkType.Mistake)
        assertEquals(2, x.totals().mistakes)
    }

    @Test fun aHighlightedSpanIsOnePhraseAndErasingTheMiddleSplitsIt() {
        val x = s()
        val ids = listOf("2:5:1", "2:5:2", "2:5:3", "2:5:4", "2:5:5")
        assertEquals(1, x.markSpan(ids, MarkType.Mistake).delta.mist)
        x.eraseWords(listOf("2:5:3"))
        assertEquals(2, x.totals().mistakes)
    }

    @Test fun everyWordIsAPromptAndTwoHarakatAreOne() {
        val x = s()
        x.markWord("2:5:1", MarkType.Prompt)
        x.markWord("2:5:6", MarkType.Prompt, MarkKind.Harakah)
        assertEquals(1, x.totals().prompts)
        x.markWord("2:5:8", MarkType.Prompt, MarkKind.Harakah)
        assertEquals(2, x.totals().prompts)
        assertEquals(2.0, x.totals().promptsExact, 0.0)
    }

    @Test fun undoAndRedoReportTheCounterDelta() {
        val x = s()
        x.markWord("2:5:1", MarkType.Prompt)
        val u = x.undo()!!
        assertEquals(-1, u.delta.prompt)
        assertFalse(x.canUndo)
        assertEquals(1, x.redo()!!.delta.prompt)
        assertEquals(null, MushafEngine.Session().undo())
    }

    @Test fun aPageOverTheLimitIsReportedWhateverTheTotalsAre() {
        val x = s()
        x.markWord("2:5:1", MarkType.Mistake, pageNo = 2); x.markWord("2:9:1", MarkType.Mistake, pageNo = 2)
        val over = x.overLimitPages()
        assertEquals(listOf(2), over.map { it.page })
        assertEquals(2, over[0].mistakes)
        val y = s()
        y.markWord("2:5:1", MarkType.Prompt, pageNo = 2); y.markWord("2:5:3", MarkType.Prompt, pageNo = 2)
        assertTrue(y.overLimitPages().isEmpty())
        y.markWord("2:5:5", MarkType.Prompt, pageNo = 2)
        assertEquals(1, y.overLimitPages().size)
        // the same totals spread over two pages are within the limit on each
        val z = s()
        z.markWord("2:5:1", MarkType.Mistake, pageNo = 2); z.markWord("2:50:1", MarkType.Mistake, pageNo = 3)
        assertTrue(z.overLimitPages().isEmpty())
    }

    @Test fun spanWorksInEitherDirectionAndIgnoresUnknownIds() {
        val item = { id: String -> MushafEngine.Item(id, "2:1", 2, 1, 1, "", "", 2, 1, false) }
        val lines = listOf(MushafEngine.Line(1, MushafEngine.LineKind.Text, listOf(item("2:1:1"), item("2:1:2"), item("2:1:3"))))
        assertEquals(listOf("2:1:1", "2:1:2", "2:1:3"), MushafEngine.spanBetween(lines, "2:1:3", "2:1:1"))
        assertTrue(MushafEngine.spanBetween(lines, "2:1:1", "9:9:9").isEmpty())
    }

    @Test fun onlyAnOverwideLineIsScaledDown() {
        assertEquals(1.0, MushafEngine.overflowScale(90.0, 100.0), 0.0)
        assertEquals(1.0, MushafEngine.overflowScale(100.0, 100.0), 0.0)
        assertEquals(0.985, MushafEngine.overflowScale(100.5, 100.0), 0.0)
        assertEquals(0.892, MushafEngine.overflowScale(111.0, 100.0), 0.0)
    }

    @Test fun centeredLinesFollowTheMadinahPrint() {
        assertTrue(MushafEngine.isCenteredLine(1, 5)); assertTrue(MushafEngine.isCenteredLine(2, 9))
        assertTrue(MushafEngine.isCenteredLine(604, 4)); assertFalse(MushafEngine.isCenteredLine(604, 5))
    }
}
