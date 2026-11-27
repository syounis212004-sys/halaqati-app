package com.mohammedsamir.halaqati.ui

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import com.mohammedsamir.halaqati.data.ThemeMode
import com.mohammedsamir.halaqati.data.UiPreferences
import com.mohammedsamir.halaqati.quran.MushafEngine
import com.mohammedsamir.halaqati.quran.dk.DkPack
import com.mohammedsamir.halaqati.quran.dk.DkRender
import com.mohammedsamir.halaqati.ui.screens.MushafScreenContent
import com.mohammedsamir.halaqati.ui.theme.HalaqatiTheme
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
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h900dp-xxhdpi")
class MushafMarkRenderingTest {
    private val compose = createComposeRule()
    @get:Rule val chain: RuleChain = RuleChain.outerRule(TestRule { base, _ ->
        object : Statement() {
            override fun evaluate() {
                val app = ApplicationProvider.getApplicationContext<Application>()
                shadowOf(app.packageManager).addActivityIfNotPresent(
                    ComponentName(app.packageName, ComponentActivity::class.java.name)
                )
                base.evaluate()
            }
        }
    }).around(compose)

    @Test fun mistakeRepaintsInLight() = checkMarks(ThemeMode.LIGHT, MushafEngine.MarkType.Mistake)
    @Test fun mistakeRepaintsInDark() = checkMarks(ThemeMode.DARK, MushafEngine.MarkType.Mistake)
    @Test fun promptRepaintsInLight() = checkMarks(ThemeMode.LIGHT, MushafEngine.MarkType.Prompt)
    @Test fun promptRepaintsInDark() = checkMarks(ThemeMode.DARK, MushafEngine.MarkType.Prompt)

    private fun checkMarks(initialTheme: ThemeMode, type: MushafEngine.MarkType) {
        val page = 300
        val app = ApplicationProvider.getApplicationContext<Application>()
        val layout = DkPack.data(app).loadPage(page)
        val ids = DkPack.idsOf(app, page)
        val session = MushafEngine.Session(page)
        val counts = mutableStateOf(0 to 0)
        val theme = mutableStateOf(initialTheme)
        compose.setContent {
            HalaqatiTheme(UiPreferences.defaults().copy(themeMode = theme.value)) {
                MushafScreenContent(
                    session, page, { counts.value }, { null }, { _, _, _ -> true },
                    { errors, prompts -> counts.value = counts.value.let { it.first + errors to it.second + prompts } },
                    {},
                )
            }
        }
        val description = "صفحة ٣٠٠"
        compose.waitUntil(15_000) {
            compose.onAllNodesWithContentDescription(description).fetchSemanticsNodes().size == 1 &&
                compose.onAllNodesWithText("جارٍ رسم الصفحة…").fetchSemanticsNodes().isEmpty()
        }
        val node = compose.onNodeWithContentDescription(description)
        val bounds = node.fetchSemanticsNode().boundsInRoot
        val boxes = DkRender.rectsById(ids, DkRender.wordRects(layout, bounds.width, bounds.height / layout.lines.size))
        val (id, box) = boxes.entries
            .filter { it.value.centerY() in bounds.height * 0.3f..bounds.height * 0.7f }
            .maxBy { it.value.width() }
        val center = Offset(box.centerX(), box.centerY())
        fun pixels() = node.captureToImage().toPixelMap()
        fun assertStroke(before: PixelMap, after: PixelMap, red: Boolean) {
            var colored = 0
            val lineY = box.bottom - box.height() * 0.08f
            val margin = (box.height() * 0.04f).coerceAtLeast(2f)
            for (y in (lineY - margin).toInt().coerceAtLeast(0)..(lineY + margin).toInt().coerceAtMost(after.height - 1)) {
                for (x in box.left.toInt().coerceAtLeast(0) until box.right.toInt().coerceAtMost(after.width)) {
                    val old = before[x, y]
                    val now = after[x, y]
                    val contrast = if (red) now.red - maxOf(now.green, now.blue) else now.blue - maxOf(now.red, now.green)
                    val delta = maxOf(abs(now.red - old.red), abs(now.green - old.green), abs(now.blue - old.blue))
                    if (contrast > 0.20f && delta > 0.12f) colored++
                }
            }
            assertTrue("$initialTheme: expected visible ${if (red) "red" else "blue"} stroke pixels, found $colored", colored >= 12)
        }

        val isMistake = type == MushafEngine.MarkType.Mistake
        if (!isMistake) compose.onNodeWithContentDescription("تلقين، العدد 0").performClick()
        val unmarked = pixels()
        node.performTouchInput { click(center) }
        compose.runOnIdle {
            assertEquals(type, session.getMark(id)?.type)
            assertEquals(if (isMistake) 1 to 0 else 0 to 1, counts.value)
        }
        assertStroke(unmarked, pixels(), red = isMistake)
    }
}
