package com.mohammedsamir.halaqati.ui

import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipe
import androidx.test.core.app.ApplicationProvider
import com.mohammedsamir.halaqati.ui.screens.zoomPinch
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

/** The pinch-zoom gesture on its own and inside a pager: a two-finger pinch must zoom and must not turn the page. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = android.app.Application::class, qualifiers = "w411dp-h900dp-xxhdpi")
class ZoomPinchTest {
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
    private fun pinchOut() = rule.onRoot().performTouchInput {
        pinch(Offset(centerX - 40f, centerY), Offset(centerX - 160f, centerY), Offset(centerX + 40f, centerY), Offset(centerX + 160f, centerY))
    }

    @Test fun pinchAloneZooms() {
        var zoom by mutableFloatStateOf(1f); var pan by mutableStateOf(Offset.Zero)
        rule.setContent { Box(Modifier.fillMaxSize().zoomPinch(true, true, { zoom }, { pan }) { z, p -> zoom = z; pan = p }) { Text("x") } }
        pinchOut(); rule.waitForIdle()
        assertTrue("zoom=$zoom", zoom > 1.2f)
    }

    @Test fun pinchInsideAPagerZoomsAndDoesNotTurnThePage() {
        var zoom by mutableFloatStateOf(1f); var pan by mutableStateOf(Offset.Zero)
        var settled = -1
        rule.setContent {
            val st = rememberPagerState(initialPage = 5) { 10 }
            settled = st.settledPage
            HorizontalPager(st, Modifier.fillMaxSize(), userScrollEnabled = zoom < 1.02f) { _ ->
                Box(Modifier.fillMaxSize().zoomPinch(true, true, { zoom }, { pan }) { z, p -> zoom = z; pan = p }) { Text("page") }
            }
        }
        pinchOut(); rule.waitForIdle()
        assertTrue("zoom=$zoom", zoom > 1.2f)
        assertEquals(5, settled)
        // zoomed: a drag pans and the pager stays put
        rule.onRoot().performTouchInput { swipe(Offset(width * 0.2f, centerY), Offset(width * 0.8f, centerY)) }
        rule.waitForIdle()
        assertEquals(5, settled)
    }
}
