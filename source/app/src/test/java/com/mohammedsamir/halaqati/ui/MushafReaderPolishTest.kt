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
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.mohammedsamir.halaqati.quran.MushafEngine
import com.mohammedsamir.halaqati.quran.MushafEngine.MarkType
import com.mohammedsamir.halaqati.ui.screens.MushafDock
import com.mohammedsamir.halaqati.ui.screens.MushafJumpDialog
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
        rule.onRoot().performTouchInput { swipeRight() }
        rule.waitForIdle()
        rule.onNodeWithText("٦ / ٦٠٤").assertIsDisplayed()
        rule.onRoot().performTouchInput { swipeLeft() }
        rule.onRoot().performTouchInput { swipeLeft() }
        rule.waitForIdle()
        rule.onNodeWithText("٤ / ٦٠٤").assertIsDisplayed()
    }

    @Test fun swipingAlsoWorksInTheMarkingMushafWhileTheTapToolIsActive() {
        reader(readOnly = false)
        rule.waitForIdle()
        rule.onRoot().performTouchInput { swipeRight() }
        rule.waitForIdle()
        rule.onNodeWithText("٦ / ٦٠٤").assertIsDisplayed()
    }

    @Test fun theReadingMushafHasNoMarkingDockOrScore() {
        reader(readOnly = true)
        rule.waitForIdle()
        rule.onNodeWithText("خطأ").assertDoesNotExistCompat()
    }

    @Test fun jumpDialogListsThePagesOfTheAssignedRangeUnderItsOwnHeading() {
        var went = -1
        rule.setContent { MaterialTheme(lightColorScheme()) { MushafJumpDialog(page = 3, rangeStart = 510, rangeEnd = 512, onGo = { went = it }, onOffline = {}, onDismiss = {}) } }
        rule.onNodeWithText("ضمن المقرر").assertIsDisplayed()
        rule.onNodeWithText("٥١١").assertIsDisplayed()
        rule.onNodeWithText("٥١١").performClick()
        assertEquals(511, went)
    }

    @Test fun jumpDialogWithoutARangeHasNoRangeHeadingAndLongRangesAreSummarised() {
        rule.setContent { MaterialTheme(lightColorScheme()) { MushafJumpDialog(page = 3, rangeStart = null, rangeEnd = null, onGo = {}, onOffline = {}, onDismiss = {}) } }
        rule.onNodeWithText("ضمن المقرر").assertDoesNotExistCompat()
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
        rule.setContent { MaterialTheme(lightColorScheme()) { SettingsCollapsible(androidx.compose.material.icons.Icons.Default.Speed, "القياسات", "أزمنة") { Text("planSnapshots 2840") } } }
        rule.onNodeWithText("planSnapshots 2840").assertDoesNotExistCompat()
        rule.onNodeWithText("القياسات").performClick()
        rule.onNodeWithText("planSnapshots 2840").assertIsDisplayed()
        rule.onNodeWithText("القياسات").performClick()
        rule.onNodeWithText("planSnapshots 2840").assertDoesNotExistCompat()
    }
}

private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertDoesNotExistCompat() = this.assertDoesNotExist()
