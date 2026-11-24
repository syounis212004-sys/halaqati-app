package com.mohammedsamir.halaqati.ui

import android.graphics.Bitmap
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.mohammedsamir.halaqati.data.ThemeMode
import com.mohammedsamir.halaqati.data.UiPreferences
import com.mohammedsamir.halaqati.ui.theme.HalaqatiTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.mohammedsamir.halaqati.quran.MushafEngine
import com.mohammedsamir.halaqati.quran.MushafStore
import com.mohammedsamir.halaqati.ui.screens.MushafScore
import com.mohammedsamir.halaqati.ui.screens.MushafScreenContent
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Real rendering of the interactive mushaf screen (page 518 of the real QCF V2 fonts, one mistake and one prompt marked) to PNG in
 * app/build/preview, for a person to look at. Runs only with HALAQATI_PREVIEW=1 and a network (the Quran text and fonts are never in this
 * repo); without either it is skipped, never failed.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = android.app.Application::class, qualifiers = "w411dp-h900dp-xxhdpi")
class MushafPreviewTest {
    private val composeRule = createComposeRule()

    @get:Rule val chain: RuleChain = RuleChain.outerRule(TestRule { base, _ ->
        object : Statement() {
            override fun evaluate() {
                assumeTrue(System.getenv("HALAQATI_PREVIEW") == "1")
                base.evaluate()
            }
        }
    }).around(composeRule)
    private val rule get() = composeRule

    private fun render(name: String, dark: Boolean, collapse: Boolean = false) {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        // page and fonts first: no network (or a blocked CDN) skips the preview instead of failing the build
        val ready = runCatching { runBlocking { MushafStore.get(context).preparePage(518) } }.getOrNull()
        assumeTrue("mushaf page/fonts not reachable from this machine", ready?.isSuccess == true)
        val session = MushafEngine.Session(518)
        session.markWord("50:1:3", MushafEngine.MarkType.Mistake, pageNo = 518)
        session.markWord("50:2:3", MushafEngine.MarkType.Prompt, pageNo = 518)
        rule.setContent {
            // the app's own theme, exactly as Settings chooses it (light or dark), not the system's
            HalaqatiTheme(UiPreferences.defaults().copy(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT, motion = com.mohammedsamir.halaqati.data.MotionLevel.REDUCED)) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    MushafScreenContent(
                        session = session, rangeStartPage = 518,
                        counters = { 1 to 1 }, score = { MushafScore(94.2, "ممتاز", 25.0) },
                        inRange = { s, _, _ -> s == 50 }, onMark = { _, _ -> }, onDismiss = {},
                    )
                }
            }
        }
        var painted = false
        repeat(200) {
            rule.waitForIdle(); Thread.sleep(50)
            if (rule.onAllNodesWithContentDescription(" — خطأ", substring = true).fetchSemanticsNodes().isNotEmpty()) { painted = true; return@repeat }
        }
        assertTrue("$name: the page never finished loading", painted)
        if (collapse) {
            rule.onAllNodesWithContentDescription("طي شريط الأدوات").onFirst().performClick()
            rule.mainClock.advanceTimeBy(600)
        }
        rule.waitForIdle()
        val view = rule.onRoot().fetchSemanticsNode().root as android.view.View
        val bmp = Bitmap.createBitmap(view.width.coerceAtLeast(1), view.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bmp))
        val dir = File("build/preview").also { it.mkdirs() }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun mushaf_light_411() = render("mushaf_light_411", dark = false)

    @Test fun mushaf_dark_411() = render("mushaf_dark_411", dark = true)

    @Test @Config(qualifiers = "w360dp-h640dp-xhdpi") fun mushaf_light_small_360() = render("mushaf_light_small_360", dark = false)

    @Test @Config(qualifiers = "w360dp-h640dp-xhdpi") fun mushaf_dark_small_360() = render("mushaf_dark_small_360", dark = true)

    @Test @Config(qualifiers = "w411dp-h900dp-xxhdpi") fun mushaf_light_collapsed_411() = render("mushaf_light_collapsed_411", dark = false, collapse = true)

    @Test @Config(qualifiers = "w360dp-h640dp-xhdpi") fun mushaf_dark_collapsed_360() = render("mushaf_dark_collapsed_360", dark = true, collapse = true)

    @Test @Config(qualifiers = "w800dp-h1280dp-xhdpi") fun mushaf_light_tablet_800() = render("mushaf_light_tablet_800", dark = false)

    @Test @Config(qualifiers = "w800dp-h1280dp-xhdpi") fun mushaf_dark_tablet_800() = render("mushaf_dark_tablet_800", dark = true)

    @Test @Config(qualifiers = "w891dp-h411dp-land-xxhdpi") fun mushaf_light_landscape() = render("mushaf_light_landscape", dark = false)

    @Test @Config(qualifiers = "w891dp-h411dp-land-xxhdpi") fun mushaf_dark_landscape() = render("mushaf_dark_landscape", dark = true)
}
