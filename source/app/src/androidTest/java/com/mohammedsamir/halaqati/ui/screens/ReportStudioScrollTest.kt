package com.mohammedsamir.halaqati.ui.screens

import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test

/** RC13 regression gate: the same production scroll surface used by Report Studio must
 * allow a touch user to reach the final option instead of trapping the lower controls. */
class ReportStudioScrollTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun reportStudio_swipesToLastOption() {
        compose.setContent {
            MaterialTheme {
                ReportStudioScrollableSurface(Modifier.height(420.dp)) {
                    items((1..36).toList()) { index ->
                        Text("خيار تقرير $index")
                    }
                    item {
                        Text("آخر خيار", Modifier.testTag(REPORT_STUDIO_LAST_OPTION_TAG))
                    }
                }
            }
        }

        repeat(14) {
            compose.onNodeWithTag(REPORT_STUDIO_SCROLL_TAG).performTouchInput { swipeUp(durationMillis = 80) }
            compose.waitForIdle()
        }
        compose.onNodeWithTag(REPORT_STUDIO_LAST_OPTION_TAG).assertIsDisplayed()
    }
}
