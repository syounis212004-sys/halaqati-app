package com.mohammedsamir.halaqati.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mohammedsamir.halaqati.quran.MushafEngine
import com.mohammedsamir.halaqati.quran.MushafEngine.MarkType
import com.mohammedsamir.halaqati.quran.MushafStore
import com.mohammedsamir.halaqati.ui.screens.MUSHAF_ASPECT_H
import com.mohammedsamir.halaqati.ui.screens.MUSHAF_ASPECT_W
import com.mohammedsamir.halaqati.ui.screens.MushafDock
import com.mohammedsamir.halaqati.ui.screens.MushafPalette
import com.mohammedsamir.halaqati.ui.screens.ReadyPage
import com.mohammedsamir.halaqati.ui.screens.Tool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runners.model.Statement
import org.robolectric.Shadows.shadowOf
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Word hit accuracy of the interactive mushaf after the page box changes size (dock collapsed or expanded, rotation, other phones and
 * tablets, page navigation). Uses a synthetic page (no Quran text, no network, default font): the point is the geometry, i.e. that a real
 * touch in the middle of a word marks exactly that word, because hit testing reads the measured bounds of every word.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = android.app.Application::class, qualifiers = "w411dp-h900dp-xxhdpi")
class MushafHitAccuracyTest {
    private val composeRule = createComposeRule()

    // The release unit-test classpath has no ui-test-manifest, so ComponentActivity is registered by hand before the compose rule starts it.
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

    private fun page(no: Int, ayahBase: Int): MushafStore.PreparedPage {
        val items = (1..15).flatMap { n ->
            (1..5).map { p ->
                val text = "w%02d%d".format(n + ayahBase % 50, p)
                MushafEngine.Item("2:${ayahBase + n}:$p", "2:${ayahBase + n}", 2, ayahBase + n, p, text, text, no, n, false)
            }
        }
        val data = MushafEngine.PageData(no, 1, items)
        return MushafStore.PreparedPage(data, MushafEngine.buildPageLines(data), emptyMap<Int, FontFamily>(), null, emptyList())
    }

    private fun ids(p: MushafStore.PreparedPage) = p.lines.flatMap { it.words }.map { it.id }
    private fun descOf(p: MushafStore.PreparedPage, id: String) = p.lines.flatMap { it.words }.first { it.id == id }.text

    private class Rig(val session: MushafEngine.Session) {
        var tick by mutableIntStateOf(0)
        var w by mutableStateOf(392.dp)
        var prepared: MushafStore.PreparedPage? by mutableStateOf(null)
        var pageNo by mutableIntStateOf(3)
        val h: Dp get() = (w.value * MUSHAF_ASPECT_H / MUSHAF_ASPECT_W).dp
    }

    private fun show(rig: Rig, first: MushafStore.PreparedPage) {
        rig.prepared = first
        rule.setContent {
            val pal = MushafPalette(lightColorScheme(), Color(0xFF1565C0))
            MaterialTheme(lightColorScheme()) {
                Box(Modifier.fillMaxSize()) {
                    val p = rig.prepared!!
                    ReadyPage(
                        p, rig.pageNo, rig.w, rig.h, pal, rig.tick, Tool.Tap, rig.session, emptySet(), { _, _, _ -> true },
                        onTapWord = { rig.session.markWord(it, MarkType.Mistake, pageNo = rig.pageNo); rig.tick++ },
                        onLongWord = { _, _ -> }, onPreview = {}, onSpan = {}, kindMenuFor = null, onKind = { _, _ -> }, onDismissKind = {},
                    )
                }
            }
        }
        rule.waitForIdle()
    }

    /** Tap every word of the page in turn: exactly that word gets the mark, and a second tap removes it. */
    private fun tapEveryWord(rig: Rig, p: MushafStore.PreparedPage, label: String) {
        val all = ids(p)
        assertEquals(75, all.size)
        for (id in all) {
            val d = descOf(p, id)
            rule.onNodeWithContentDescription(d, substring = true).performClick()
            rule.waitForIdle()
            val marked = all.filter { rig.session.getMark(it) != null }
            assertEquals("$label: tapping $d must mark only $id", listOf(id), marked)
            rule.onNodeWithContentDescription(d, substring = true).performClick()
            rule.waitForIdle()
            assertTrue("$label: second tap on $d must clear it", all.none { rig.session.getMark(it) != null })
        }
    }

    @Test fun every_word_is_hit_after_the_page_changes_size() {
        val rig = Rig(MushafEngine.Session(3))
        val p = page(3, 0)
        show(rig, p)
        // portrait phone, then the dock collapses (page grows), expands (page shrinks), a small phone, a tablet
        for (w in listOf(392, 392, 330, 300, 460)) {
            rig.w = w.dp; rule.waitForIdle()
            tapEveryWord(rig, p, "width ${w}dp")
        }
    }

    @Test fun marks_survive_rotation_style_resize_and_stay_attached_to_their_words() {
        val rig = Rig(MushafEngine.Session(3))
        val p = page(3, 0)
        show(rig, p)
        val target = ids(p)[37]
        rule.onNodeWithContentDescription(descOf(p, target), substring = true).performClick()
        rule.waitForIdle()
        for (w in listOf(200, 480, 392)) {   // landscape-like small page, a big one, back
            rig.w = w.dp; rule.waitForIdle()
            assertEquals(MarkType.Mistake, rig.session.getMark(target)?.type)
            rule.onAllNodesWithContentDescription(" — خطأ", substring = true).assertCountEquals(1)
        }
    }

    @Test fun page_navigation_rebinds_hit_testing_to_the_new_words() {
        val rig = Rig(MushafEngine.Session(3))
        val a = page(3, 0)
        val b = page(4, 100)
        show(rig, a)
        tapEveryWordSample(rig, a)
        rig.pageNo = 4; rig.prepared = b; rule.waitForIdle()
        rig.session.setPage(4)
        tapEveryWord(rig, b, "page 4")
    }

    private fun tapEveryWordSample(rig: Rig, p: MushafStore.PreparedPage) {
        val id = ids(p)[10]
        rule.onNodeWithContentDescription(descOf(p, id), substring = true).performClick(); rule.waitForIdle()
        assertTrue(rig.session.getMark(id) != null)
        rule.onNodeWithContentDescription(descOf(p, id), substring = true).performClick(); rule.waitForIdle()
    }

    // ------------------------------------------------------------------ the dock

    private class DockRig {
        var expanded by mutableStateOf(true)
        var tool by mutableStateOf(Tool.Tap)
        var type by mutableStateOf(MarkType.Mistake)
        var undos by mutableIntStateOf(0)
    }

    private fun showDock(r: DockRig, widthDp: Int, side: Boolean = false) {
        rule.setContent {
            val pal = MushafPalette(lightColorScheme(), Color(0xFF1565C0))
            MaterialTheme(lightColorScheme()) {
                Box(Modifier.width(widthDp.dp)) {
                    MushafDock(
                        side = side, expanded = r.expanded, onExpanded = { r.expanded = it }, counts = 3 to 4, type = r.type, tool = r.tool,
                        canUndo = true, canRedo = true, pal = pal, onType = { r.type = it }, onTool = { r.tool = it }, onUndo = { r.undos++ }, onRedo = {},
                    )
                }
            }
        }
        rule.waitForIdle()
    }

    private fun dockKeepsEveryTool(w: Int) {
        val r = DockRig()
        showDock(r, w)
        for (name in listOf("لمس", "تظليل", "ممحاة", "تراجع", "إعادة")) rule.onNodeWithContentDescription(name).assertExists()
        rule.onNodeWithContentDescription("خطأ، العدد 3").assertExists()
        rule.onNodeWithContentDescription("تلقين، العدد 4").assertExists()
        rule.onNodeWithContentDescription("تظليل").performClick(); rule.waitForIdle()
        assertEquals("width $w", Tool.Highlight, r.tool)
        rule.onNodeWithContentDescription("تلقين، العدد 4").performClick(); rule.waitForIdle()
        assertEquals(MarkType.Prompt, r.type)
        rule.onNodeWithContentDescription("تراجع").performClick(); rule.waitForIdle()
        assertEquals(1, r.undos)
        // collapse: tools fold into the compact bar, which keeps type, current tool, undo and redo
        rule.onNodeWithContentDescription("طي شريط الأدوات").performClick(); rule.waitForIdle()
        assertEquals(false, r.expanded)
        rule.onAllNodesWithContentDescription("ممحاة").assertCountEquals(0)
        rule.onNodeWithContentDescription("تراجع").assertExists()
        rule.onNodeWithContentDescription("إعادة").assertExists()
        // expand again: every tool is back
        rule.onNodeWithContentDescription("توسيع شريط الأدوات").performClick(); rule.waitForIdle()
        assertEquals(true, r.expanded)
        rule.onNodeWithContentDescription("ممحاة").assertExists()
    }

    @Test fun dock_keeps_every_tool_360() = dockKeepsEveryTool(360)
    @Test fun dock_keeps_every_tool_411() = dockKeepsEveryTool(411)
    @Test fun dock_keeps_every_tool_800() = dockKeepsEveryTool(800)

    private fun dockTouchTargets(w: Int, side: Boolean) {
        val r = DockRig()
        showDock(r, w, side)
        for (name in listOf("لمس", "تظليل", "ممحاة", "تراجع", "إعادة", "خطأ، العدد 3", "تلقين، العدد 4")) {
            rule.onNodeWithContentDescription(name).assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        }
    }

    @Test fun dock_touch_targets_360() = dockTouchTargets(360, false)
    @Test fun dock_touch_targets_411() = dockTouchTargets(411, false)
    @Test fun dock_touch_targets_800() = dockTouchTargets(800, false)
    @Test fun rail_touch_targets() = dockTouchTargets(200, true)

    @Test fun side_rail_collapses_to_icons_and_keeps_undo() {
        val r = DockRig()
        showDock(r, 160, side = true)
        rule.onNodeWithContentDescription("ممحاة").assertExists()
        rule.onNodeWithContentDescription("طي شريط الأدوات").performClick(); rule.waitForIdle()
        rule.onAllNodesWithContentDescription("ممحاة").assertCountEquals(0)
        rule.onNodeWithContentDescription("تراجع").performClick(); rule.waitForIdle()
        assertEquals(1, r.undos)
    }
}
