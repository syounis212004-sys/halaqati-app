package com.mohammedsamir.halaqati.ui

import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.mohammedsamir.halaqati.ui.screens.MushafOfflineDialog
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

/** The "mushaf without internet" dialog: size shown before downloading, Wi-Fi-only guard, nothing downloads until the button is pressed. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = android.app.Application::class, qualifiers = "w411dp-h900dp-xxhdpi")
class MushafOfflineDialogTest {
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

    @Test fun pickingAllShowsTheSizeAndTheWifiGuardBlocksUntilSwitchedOff() {
        composeRule.setContent { MaterialTheme(lightColorScheme()) { MushafOfflineDialog(currentPage = 3, onDismiss = {}) } }
        composeRule.onNodeWithText("المصحف بدون إنترنت").assertExists()
        // Quran Foundation Developer Terms: the fonts downloaded here need a visible credit
        composeRule.onNodeWithText("Quran fonts provided by Quran Foundation", substring = true).assertExists()
        composeRule.onNodeWithText("تنزيل").assertIsNotEnabled()            // nothing chosen yet
        composeRule.onNodeWithText("المصحف كاملًا").performClick()
        composeRule.onNodeWithText("سيُنزَّل ٦٠٤ صفحة", substring = true).assertExists()
        composeRule.onNodeWithText("أنت غير متصل بشبكة Wi-Fi", substring = true).assertExists()
        composeRule.onNodeWithText("تنزيل").assertIsNotEnabled()            // Wi-Fi only is on and the test device is on no Wi-Fi
    }
}
