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
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.onNodeWithContentDescription
import com.mohammedsamir.halaqati.quran.dk.DkPack
import com.mohammedsamir.halaqati.ui.screens.MushafScreenContent
import org.junit.Assume.assumeTrue
import java.io.File

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

/** Renders the DigitalKhatm page inside the app frame to PNG (needs the generated pack) and checks the printed margins. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = android.app.Application::class, qualifiers = "w411dp-h900dp-xxhdpi")
class DkPagePreviewTest {
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


    private fun render(name: String, page: Int, dark: Boolean = false, readOnly: Boolean = false) {
        assumeTrue(File("src/main/assets/dk/ids.bin").exists())
        val session = MushafEngine.Session(page)
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val ids = DkPack.idsOf(ctx, page).filter { !it.endsWith(":end") }.distinct()
        if (!readOnly) { session.markWord(ids[3], MushafEngine.MarkType.Mistake, pageNo = page); session.markWord(ids[9], MushafEngine.MarkType.Prompt, pageNo = page) }
        rule.setContent {
            com.mohammedsamir.halaqati.ui.theme.HalaqatiTheme(com.mohammedsamir.halaqati.data.UiPreferences.defaults().copy(themeMode = if (dark) com.mohammedsamir.halaqati.data.ThemeMode.DARK else com.mohammedsamir.halaqati.data.ThemeMode.LIGHT, motion = com.mohammedsamir.halaqati.data.MotionLevel.REDUCED)) {
                androidx.compose.material3.Surface(color = MaterialTheme.colorScheme.background) {
                    MushafScreenContent(session, page, { 1 to 1 }, { com.mohammedsamir.halaqati.ui.screens.MushafScore(94.0, "ممتاز", 3.3) }, { _, _, _ -> true }, { _, _ -> }, {}, rangeEndPage = page + 2, readOnly = readOnly)
                }
            }
        }
        var found = false
        repeat(200) {
            rule.waitForIdle(); Thread.sleep(50)
            if (rule.onAllNodesWithContentDescription("صفحة ${com.mohammedsamir.halaqati.ui.screens.digits(page)}").fetchSemanticsNodes().isNotEmpty()) { found = true; return@repeat }
        }
        assertTrue("$name: DigitalKhatm page never appeared", found)
        Thread.sleep(1500); rule.waitForIdle()
        val box = rule.onNodeWithContentDescription("صفحة ${com.mohammedsamir.halaqati.ui.screens.digits(page)}").getBoundsInRoot()
        assertTrue("left margin ${box.left}", box.left.value >= 4f); assertTrue("right margin", (411f - box.right.value) >= 4f)
        val view = rule.onRoot().fetchSemanticsNode().root as android.view.View
        val bmp = android.graphics.Bitmap.createBitmap(view.width.coerceAtLeast(1), view.height.coerceAtLeast(1), android.graphics.Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bmp))
        File("build/preview").mkdirs(); File("build/preview/$name.png").outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun page300() = render("dk_ui_p300", 300)
    @Test fun page511SuraStart() = render("dk_ui_p511", 511)
    @Test fun page511Dark() = render("dk_ui_p511_dark", 511, dark = true)
    @Test fun page511Reader() = render("dk_ui_p511_reader", 511, readOnly = true)
    @Test fun page1Fatiha() = render("dk_ui_p1", 1)
}
