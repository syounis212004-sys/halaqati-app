package com.mohammedsamir.halaqati.ui

import android.app.Application
import android.graphics.Bitmap
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import com.mohammedsamir.halaqati.data.MotionLevel
import com.mohammedsamir.halaqati.data.ThemeMode
import com.mohammedsamir.halaqati.data.UiPreferences
import com.mohammedsamir.halaqati.quran.MushafEngine
import com.mohammedsamir.halaqati.quran.dk.DkPack
import com.mohammedsamir.halaqati.quran.dk.DkRender
import com.mohammedsamir.halaqati.ui.screens.MushafScreenContent
import com.mohammedsamir.halaqati.ui.screens.digits
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
import java.io.File
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

    @Test fun marksFollowEditingAndPageLifecycle() =
        checkMarks(ThemeMode.LIGHT, MushafEngine.MarkType.Mistake, exerciseEditing = true)

    private fun checkMarks(initialTheme: ThemeMode, type: MushafEngine.MarkType, exerciseEditing: Boolean = false) {
        val page = 300
        val app = ApplicationProvider.getApplicationContext<Application>()
        val layout = DkPack.data(app).loadPage(page)
        val ids = DkPack.idsOf(app, page)
        val session = MushafEngine.Session(page)
        val counts = mutableStateOf(0 to 0)
        val theme = mutableStateOf(initialTheme)
        val open = mutableStateOf(true)
        compose.setContent {
            HalaqatiTheme(UiPreferences.defaults().copy(themeMode = theme.value, motion = MotionLevel.REDUCED)) {
                if (open.value) MushafScreenContent(
                    session, page, { counts.value }, { null }, { _, _, _ -> true },
                    { errors, prompts -> counts.value = counts.value.let { it.first + errors to it.second + prompts } },
                    { open.value = false },
                )
            }
        }
        fun waitForPage(number: Int) {
            compose.waitUntil(20_000) {
                compose.onAllNodesWithContentDescription("صفحة ${digits(number)}").fetchSemanticsNodes().size == 1 &&
                    compose.onAllNodesWithText("جارٍ رسم الصفحة…").fetchSemanticsNodes().isEmpty()
            }
        }
        waitForPage(page)
        val node = compose.onNodeWithContentDescription("صفحة ${digits(page)}")
        val bounds = node.fetchSemanticsNode().boundsInRoot
        val boxes = DkRender.rectsById(ids, DkRender.wordRects(layout, bounds.width, bounds.height / layout.lines.size))
        val (id, box) = boxes.entries
            .filter { it.value.centerY() in bounds.height * 0.3f..bounds.height * 0.7f }
            .maxBy { it.value.width() }
        val center = Offset(box.centerX(), box.centerY())
        fun pixels(label: String? = null): PixelMap {
            val rect = node.fetchSemanticsNode().boundsInRoot
            val view = compose.onRoot().fetchSemanticsNode().root as android.view.View
            val bitmap = compose.runOnIdle {
                val output = Bitmap.createBitmap(rect.width.toInt(), rect.height.toInt(), Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(output)
                canvas.translate(-rect.left, -rect.top)
                view.draw(canvas)
                output
            }
            if (label != null) {
                val dir = File("build/mushaf-mark-regression").apply { mkdirs() }
                File(dir, "$initialTheme-$type-$label.png").outputStream().use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
            }
            return bitmap.asImageBitmap().toPixelMap()
        }
        fun strokePixels(before: PixelMap?, after: PixelMap, red: Boolean): Int {
            var colored = 0
            val lineY = box.bottom - box.height() * 0.08f
            val margin = (box.height() * 0.04f).coerceAtLeast(2f)
            for (y in (lineY - margin).toInt().coerceAtLeast(0)..(lineY + margin).toInt().coerceAtMost(after.height - 1)) {
                for (x in box.left.toInt().coerceAtLeast(0) until box.right.toInt().coerceAtMost(after.width)) {
                    val old = before?.get(x, y)
                    val now = after[x, y]
                    val contrast = if (red) now.red - maxOf(now.green, now.blue) else now.blue - maxOf(now.red, now.green)
                    val delta = if (old == null) 1f else maxOf(abs(now.red - old.red), abs(now.green - old.green), abs(now.blue - old.blue))
                    if (contrast > 0.20f && delta > 0.12f) colored++
                }
            }
            return colored
        }
        fun assertStroke(before: PixelMap?, after: PixelMap, red: Boolean) {
            val colored = strokePixels(before, after, red)
            assertTrue("$initialTheme: expected visible ${if (red) "red" else "blue"} stroke pixels, found $colored", colored >= 12)
        }

        val isMistake = type == MushafEngine.MarkType.Mistake
        if (!isMistake) compose.onNodeWithContentDescription("تلقين، العدد 0").performClick()
        val unmarked = pixels(if (exerciseEditing) null else "before")
        node.performTouchInput { click(center) }
        compose.runOnIdle {
            assertEquals(type, session.getMark(id)?.type)
            assertEquals(if (isMistake) 1 to 0 else 0 to 1, counts.value)
        }
        assertStroke(unmarked, pixels(if (exerciseEditing) null else "after"), red = isMistake)
        if (!exerciseEditing) return

        fun expect(expected: MushafEngine.MarkType?, mistakes: Int, prompts: Int) {
            compose.runOnIdle {
                assertEquals(expected, session.getMark(id)?.type)
                assertEquals(mistakes to prompts, counts.value)
            }
        }
        compose.onNodeWithContentDescription("تلقين، العدد 0").performClick()
        node.performTouchInput { click(center) }
        expect(MushafEngine.MarkType.Prompt, 0, 1)
        assertStroke(unmarked, pixels(), red = false)
        assertEquals(0, strokePixels(unmarked, pixels(), red = true))

        compose.onNodeWithContentDescription("تراجع").performClick()
        expect(MushafEngine.MarkType.Mistake, 1, 0)
        assertStroke(unmarked, pixels(), red = true)
        compose.onNodeWithContentDescription("إعادة").performClick()
        expect(MushafEngine.MarkType.Prompt, 0, 1)
        assertStroke(unmarked, pixels(), red = false)

        compose.runOnIdle { theme.value = ThemeMode.DARK }
        waitForPage(page)
        assertStroke(null, pixels(), red = false)
        compose.onNodeWithContentDescription("الصفحة التالية").performClick()
        waitForPage(page + 1)
        compose.onNodeWithContentDescription("الصفحة السابقة").performClick()
        waitForPage(page)
        assertStroke(null, pixels(), red = false)
        compose.onNodeWithContentDescription("إغلاق المصحف").performClick()
        compose.runOnIdle { open.value = true }
        waitForPage(page)
        expect(MushafEngine.MarkType.Prompt, 0, 1)
        assertStroke(null, pixels(), red = false)

        compose.onNodeWithContentDescription("ممحاة").performClick()
        node.performTouchInput { click(center) }
        expect(null, 0, 0)
        assertEquals(0, strokePixels(null, pixels(), red = false))

        compose.onNodeWithContentDescription("تلقين، العدد 0").performClick()
        compose.onNodeWithContentDescription("لمس").performClick()
        node.performTouchInput { longClick(center) }
        compose.onNodeWithText("حركة").performClick()
        expect(MushafEngine.MarkType.Prompt, 0, 0)
        compose.runOnIdle { assertEquals(MushafEngine.MarkKind.Harakah, session.getMark(id)?.kind) }
        assertStroke(null, pixels(), red = false)
        node.performTouchInput { longClick(center) }
        compose.onNodeWithText("بداية آية").performClick()
        expect(MushafEngine.MarkType.Prompt, 0, 1)
        compose.runOnIdle { assertEquals(MushafEngine.MarkKind.AyahStart, session.getMark(id)?.kind) }
        assertStroke(null, pixels(), red = false)

        val neighbour = boxes.entries.first { it.key != id && it.value.top == box.top }
        compose.onNodeWithContentDescription("تظليل").performClick()
        node.performTouchInput { swipe(center, Offset(neighbour.value.centerX(), neighbour.value.centerY())) }
        compose.runOnIdle {
            assertEquals(MushafEngine.MarkType.Prompt, session.getMark(neighbour.key)?.type)
            assertTrue(counts.value.second >= 2)
        }
        assertStroke(null, pixels(), red = false)
    }
}
