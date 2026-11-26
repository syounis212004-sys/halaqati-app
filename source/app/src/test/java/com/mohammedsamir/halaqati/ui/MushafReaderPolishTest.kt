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
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.mohammedsamir.halaqati.quran.MushafEngine
import com.mohammedsamir.halaqati.quran.MushafEngine.MarkType
import com.mohammedsamir.halaqati.ui.screens.MushafDock
import com.mohammedsamir.halaqati.ui.screens.MushafJumpBody
import com.mohammedsamir.halaqati.ui.screens.MushafPalette
import com.mohammedsamir.halaqati.ui.screens.MushafScreenContent
import com.mohammedsamir.halaqati.ui.screens.SettingsCollapsible
import com.mohammedsamir.halaqati.ui.screens.Tool
import org.junit.Assert.assertEquals
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

/** Mushaf reader polish: swipe to turn pages, jump dialog with the assigned range, count badges inside the dock, folded settings rows. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = android.app.Application::class, qualifiers = "w411dp-h900dp-xxhdpi")
class MushafReaderPolishTest {
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

    private fun reader(readOnly: Boolean, start: Int = 5) = rule.setContent {
        MaterialTheme(lightColorScheme()) {
            MushafScreenContent(
                MushafEngine.Session(start), null, { 0 to 0 }, { null }, { _, _, _ -> false }, { _, _ -> }, {}, readOnly = readOnly,
            )
        }
    }

    @Test fun swipingTheFingerRightTurnsToTheNextPageAndLeftToThePreviousOne() {
        reader(readOnly = true)
        rule.waitForIdle()
        rule.onNodeWithText("٥ / ٦٠٤").assertIsDisplayed()
        rule.onRoot().performTouchInput { swipe(androidx.compose.ui.geometry.Offset(width * 0.2f, centerY), androidx.compose.ui.geometry.Offset(width * 0.8f, centerY)) }
        rule.waitForIdle()
        val labels = rule.onAllNodesWithText("/ ٦٠٤", substring = true).fetchSemanticsNodes().map { n -> n.config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString() }
        assertEquals(listOf("٦ / ٦٠٤"), labels)
        rule.onRoot().performTouchInput { swipe(androidx.compose.ui.geometry.Offset(width * 0.8f, centerY), androidx.compose.ui.geometry.Offset(width * 0.2f, centerY)) }
        rule.onRoot().performTouchInput { swipe(androidx.compose.ui.geometry.Offset(width * 0.8f, centerY), androidx.compose.ui.geometry.Offset(width * 0.2f, centerY)) }
        rule.waitForIdle()
        rule.onNodeWithText("٤ / ٦٠٤").assertIsDisplayed()
    }

    @Test fun swipingAlsoWorksInTheMarkingMushafWhileTheTapToolIsActive() {
        reader(readOnly = false)
        rule.waitForIdle()
        rule.onRoot().performTouchInput { swipe(androidx.compose.ui.geometry.Offset(width * 0.2f, centerY), androidx.compose.ui.geometry.Offset(width * 0.8f, centerY)) }
        rule.waitForIdle()
        rule.onNodeWithText("٦ / ٦٠٤").assertIsDisplayed()
    }

    @Test fun theReadingMushafHasNoMarkingDockOrScore() {
        reader(readOnly = true)
        rule.waitForIdle()
        rule.onNodeWithText("خطأ").assertDoesNotExist()
    }

    @Test fun jumpDialogListsThePagesOfTheAssignedRangeUnderItsOwnHeading() {
        var went = -1
                rule.setContent { MaterialTheme(lightColorScheme()) { MushafJumpBody(page = 3, rangeStart = 510, rangeEnd = 512, input = "3", onInput = {}, onGo = { went = it }) } }
        rule.onNodeWithText("ضمن المقرر").assertIsDisplayed()
        rule.onNodeWithText("٥١١").assertIsDisplayed()
        rule.onNodeWithText("٥١١").performClick()
        assertEquals(511, went)
    }

    @Test fun jumpDialogWithoutARangeHasNoRangeHeadingAndLongRangesAreSummarised() {
                rule.setContent { MaterialTheme(lightColorScheme()) { MushafJumpBody(page = 3, rangeStart = null, rangeEnd = null, input = "3", onInput = {}, onGo = {}) } }
        rule.onNodeWithText("ضمن المقرر").assertDoesNotExist()
    }

    @Test fun theCountBadgesStayInsideTheDock() {
        rule.setContent {
            MaterialTheme(lightColorScheme()) {
                val pal = MushafPalette(lightColorScheme(), Color(0xFF1565C0))
                Box(Modifier.fillMaxSize()) {
                    MushafDock(false, true, {}, 2 to 1, MarkType.Mistake, Tool.Tap, canUndo = false, canRedo = false, pal = pal, onType = {}, onTool = {}, onUndo = {}, onRedo = {})
                }
            }
        }
        rule.waitForIdle()
        val root = rule.onRoot().getBoundsInRoot()
        for (n in listOf("٢", "١")) {
            val b = rule.onNodeWithText(n).getBoundsInRoot()
            assertTrue("badge $n top ${b.top} must be inside the dock (root top ${root.top})", b.top >= root.top)
            assertTrue("badge $n inside horizontally", b.left >= root.left && b.right <= root.right)
        }
    }

    @Test fun foldedSettingsRowsHideTheirContentUntilTapped() {
        rule.setContent { MaterialTheme(lightColorScheme()) { SettingsCollapsible(androidx.compose.ui.graphics.vector.ImageVector.Builder("t", 24.dp, 24.dp, 24f, 24f).build(), "القياسات", "أزمنة") { Text("planSnapshots 2840") } } }
        rule.onNodeWithText("planSnapshots 2840").assertDoesNotExist()
        rule.onNodeWithText("القياسات").performClick()
        rule.onNodeWithText("planSnapshots 2840").assertIsDisplayed()
        rule.onNodeWithText("القياسات").performClick()
        rule.onNodeWithText("planSnapshots 2840").assertDoesNotExist()
    }

    @Test fun bookmarksTogglePersistSortedAndNeverTouchMarks() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        ctx.getSharedPreferences("halaqati_mushaf_reader", 0).edit().clear().commit()
        assertEquals(emptyList<Int>(), com.mohammedsamir.halaqati.ui.screens.MushafBookmarks.all(ctx))
        com.mohammedsamir.halaqati.ui.screens.MushafBookmarks.toggle(ctx, 300); com.mohammedsamir.halaqati.ui.screens.MushafBookmarks.toggle(ctx, 12)
        assertEquals(listOf(12, 300), com.mohammedsamir.halaqati.ui.screens.MushafBookmarks.all(ctx))
        assertEquals(listOf(12), com.mohammedsamir.halaqati.ui.screens.MushafBookmarks.toggle(ctx, 300))
    }

    @Test fun theBookmarkButtonTogglesAndTheJumpDialogListsBookmarkedPages() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        ctx.getSharedPreferences("halaqati_mushaf_reader", 0).edit().clear().commit()
        reader(readOnly = true, start = 7)
        rule.waitForIdle()
        rule.onNodeWithContentDescription("إضافة علامة مرجعية").performClick()
        rule.onNodeWithContentDescription("إزالة العلامة المرجعية").assertIsDisplayed()
        assertEquals(listOf(7), com.mohammedsamir.halaqati.ui.screens.MushafBookmarks.all(ctx))
        rule.onNodeWithContentDescription("إزالة العلامة المرجعية").performClick()
        assertEquals(emptyList<Int>(), com.mohammedsamir.halaqati.ui.screens.MushafBookmarks.all(ctx))
    }

    @Test fun jumpBodyShowsBookmarksAndGoesToAnAyahsPage() {
        var went = -1
        rule.setContent { MaterialTheme(lightColorScheme()) { MushafJumpBody(page = 3, rangeStart = null, rangeEnd = null, input = "3", onInput = {}, onGo = { went = it }, bookmarks = listOf(42, 300)) } }
        rule.onNodeWithText("العلامات المرجعية").assertIsDisplayed()
        rule.onNodeWithText("ص ٣٠٠").performClick()
        assertEquals(300, went)
        rule.onNodeWithText("السور").performClick()
        rule.waitForIdle()
        rule.onNodeWithText(com.mohammedsamir.halaqati.quran.QuranMetadata.surahName(2)).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        rule.waitForIdle()
        rule.onNodeWithText("رقم الآية", substring = true).performTextInput("255")
        rule.onNodeWithText("اذهب إلى الآية").performClick()
        assertEquals(com.mohammedsamir.halaqati.quran.QuranMetadata.pageFor(2, 255), went)
    }

    @Test fun anAyahNumberBeyondTheSurahIsRejected() {
        rule.setContent { MaterialTheme(lightColorScheme()) { MushafJumpBody(page = 3, rangeStart = null, rangeEnd = null, input = "3", onInput = {}, onGo = {}) } }
        rule.onNodeWithText("السور").performClick()
        rule.onNodeWithText("ابحث عن سورة").performTextInput(com.mohammedsamir.halaqati.quran.QuranMetadata.surahName(103).removePrefix("ال"))
        rule.waitForIdle()
        rule.onNodeWithText(com.mohammedsamir.halaqati.quran.QuranMetadata.surahName(103)).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        rule.waitForIdle()
        rule.onNodeWithText("رقم الآية", substring = true).performTextInput("9")
        rule.onNodeWithText("اذهب إلى الآية").assertIsNotEnabled()
    }

    @Test fun juzTabGoesToTheFirstPageOfTheJuz() {
        var went = -1
        rule.setContent { MaterialTheme(lightColorScheme()) { MushafJumpBody(page = 3, rangeStart = null, rangeEnd = null, input = "3", onInput = {}, onGo = { went = it }) } }
        rule.onNodeWithText("الأجزاء").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("١٥").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        assertEquals(com.mohammedsamir.halaqati.quran.MushafOfflinePlan.juzPages(15).first, went)
    }

    @Test fun aLongRangeIsOfferedAsFirstAndLastPage() {
        var went = -1
        rule.setContent { MaterialTheme(lightColorScheme()) { MushafJumpBody(page = 3, rangeStart = 10, rangeEnd = 60, input = "3", onInput = {}, onGo = { went = it }) } }
        rule.onNodeWithText("آخر المقرر").performClick()
        assertEquals(60, went)
    }
}
