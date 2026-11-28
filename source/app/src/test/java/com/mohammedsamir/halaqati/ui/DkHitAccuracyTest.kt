package com.mohammedsamir.halaqati.ui

import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.mohammedsamir.halaqati.quran.MushafEngine
import com.mohammedsamir.halaqati.quran.MushafEngine.MarkType
import com.mohammedsamir.halaqati.ui.screens.MushafPalette
import com.mohammedsamir.halaqati.ui.screens.Tool
import org.junit.Assert.assertEquals
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.click
import com.mohammedsamir.halaqati.quran.dk.DkPack
import com.mohammedsamir.halaqati.quran.dk.DkRender
import com.mohammedsamir.halaqati.ui.screens.DkPrepared
import com.mohammedsamir.halaqati.ui.screens.DkReadyPage
import org.junit.Assume.assumeTrue
import java.io.File

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.pinch
import com.mohammedsamir.halaqati.ui.screens.MushafScreenContent

import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A touch in the middle of a word marks exactly that word (needs the generated pack), at several widths. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = android.app.Application::class, qualifiers = "w411dp-h900dp-xxhdpi")
class DkHitAccuracyTest {
    private val composeRule = createComposeRule()
    @get:Rule val chain: RuleChain = RuleChain.outerRule(TestRule { base, _ ->
        object : Statement() {
            override fun evaluate() {
                val app = ApplicationProvider.getApplicationContext<android.app.Application>()
                shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app.packageName, ComponentActivity::class.java.name))
                base.evaluate()
            }
        }
    }).around(composeRule)
    private val rule get() = composeRule


    private fun check(page: Int, widthDp: Int) {
        assumeTrue(File("src/main/assets/dk/ids.bin").exists())
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val data = DkPack.data(ctx)
        val prepared = DkPrepared(page, data, data.loadPage(page), DkPack.idsOf(ctx, page))
        val session = MushafEngine.Session(page)
        val pal = MushafPalette(lightColorScheme(), Color(0xFF1565C0))
        rule.setContent {
            MaterialTheme(lightColorScheme()) {
                Box(Modifier.width(widthDp.dp).height(700.dp).background(pal.pageBg)) {
                    DkReadyPage(prepared, page, pal, 0, Tool.Tap, emptyMap(), emptySet(), { _, _, _ -> true }, { session.markWord(it, MarkType.Mistake, pageNo = page) }, { _, _ -> }, {}, {}, null, { _, _ -> }, {})
                }
            }
        }
        val desc = "صفحة ${com.mohammedsamir.halaqati.ui.screens.digits(page)}"
        repeat(200) { rule.waitForIdle(); Thread.sleep(40); if (rule.onAllNodesWithContentDescription(desc).fetchSemanticsNodes().isNotEmpty()) return@repeat }
        rule.waitForIdle()
        val node = rule.onNodeWithContentDescription(desc)
        val b = node.fetchSemanticsNode().boundsInRoot
        val pitch = b.height / prepared.dk.lines.size
        val rects = DkRender.rectsById(prepared.ids, DkRender.wordRects(prepared.dk, b.width, pitch))
        var checked = 0
        for ((i, e) in rects.entries.withIndex()) {
            if (i % 4 != 0) continue
            val r = e.value
            node.performTouchInput { click(androidx.compose.ui.geometry.Offset((r.left + r.right) / 2f, (r.top + r.bottom) / 2f)) }
            rule.waitForIdle()
            val marked = prepared.wordIds.filter { session.getMark(it) != null }
            assertEquals("page $page width ${widthDp}dp: touching ${e.key} must mark only it", listOf(e.key), marked)
            node.performTouchInput { click(androidx.compose.ui.geometry.Offset((r.left + r.right) / 2f, (r.top + r.bottom) / 2f)) }
            rule.waitForIdle()
            assertTrue("second tap clears ${e.key}", prepared.wordIds.none { session.getMark(it) != null })
            checked++
        }
        assertTrue("checked $checked words", checked > 25)
    }

    @Test fun page300AtPhoneWidth() = check(300, 392)
    @Test fun page300AtSmallPhoneWidth() = check(300, 330)
    @Test fun page300OnATablet() = check(300, 700)
    @Test fun page177IsServedAndHitsWords() = check(177, 392)
}
