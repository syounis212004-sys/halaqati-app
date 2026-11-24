package com.mohammedsamir.halaqati.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.mohammedsamir.halaqati.data.MotionLevel
import com.mohammedsamir.halaqati.data.ThemeMode
import com.mohammedsamir.halaqati.data.UiPreferences
import com.mohammedsamir.halaqati.ui.components.AnimatedLinearProgress
import com.mohammedsamir.halaqati.ui.components.LoadingSkeleton
import com.mohammedsamir.halaqati.ui.theme.HalaqatiTheme
import java.io.File
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

/** Real rendering (PNG in app/build/preview, HALAQATI_PREVIEW=1 only) of the shared loading skeleton and the animated progress bar in both themes. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = android.app.Application::class, qualifiers = "w411dp-h900dp-xxhdpi")
class PremiumUiPreviewTest {
    private val composeRule = createComposeRule()

    @get:Rule val chain: RuleChain = RuleChain.outerRule(TestRule { base, _ ->
        object : Statement() { override fun evaluate() { assumeTrue(System.getenv("HALAQATI_PREVIEW") == "1"); base.evaluate() } }
    }).around(composeRule)

    private fun save(name: String) {
        composeRule.waitForIdle()
        val view = composeRule.onRoot().fetchSemanticsNode().root as android.view.View
        val bmp = Bitmap.createBitmap(view.width.coerceAtLeast(1), view.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bmp))
        val sample = IntArray(64) { i -> bmp.getPixel((i * 37) % bmp.width, (i * 53) % bmp.height) }
        assertTrue("$name: nothing was painted", sample.toSet().size > 1)
        val dir = File("build/preview").also { it.mkdirs() }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Composable
    private fun Themed(dark: Boolean, content: @Composable () -> Unit) {
        HalaqatiTheme(UiPreferences.defaults().copy(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT, motion = MotionLevel.REDUCED)) {
            Surface(color = MaterialTheme.colorScheme.background) { content() }
        }
    }

    private fun render(name: String, dark: Boolean) {
        composeRule.setContent {
            Themed(dark) {
                Column(Modifier.width(411.dp).padding(12.dp)) {
                    LoadingSkeleton(rows = 2, modifier = Modifier.fillMaxWidth())
                    AnimatedLinearProgress(progress = 0.62f, modifier = Modifier.fillMaxWidth().padding(top = 16.dp).height(8.dp))
                }
            }
        }
        save(name)
    }

    @Test fun premium_skeleton_progress_light() = render("premium_skeleton_progress_light", dark = false)
    @Test fun premium_skeleton_progress_dark() = render("premium_skeleton_progress_dark", dark = true)
}
