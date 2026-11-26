package com.mohammedsamir.halaqati.ui

import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onRoot
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.mohammedsamir.halaqati.reminders.ReminderPrefs
import com.mohammedsamir.halaqati.ui.screens.DateField
import com.mohammedsamir.halaqati.ui.screens.ReminderPanel
import java.time.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

/** The reminder panel (switch, days, time, permission notice) and the calendar button of the contest dates. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = android.app.Application::class, qualifiers = "w411dp-h900dp-xxhdpi")
class ReminderAndDateUiTest {
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
    private val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
    private fun scheduled() = androidx.work.WorkManager.getInstance(ctx).getWorkInfosForUniqueWork(com.mohammedsamir.halaqati.reminders.ReminderScheduler.WORK_NAME).get()
        .count { !it.state.isFinished }

    @org.junit.Before fun workManager() {
        // the app starts WorkManager itself (its automatic start is disabled in the manifest); do the same here
        runCatching { androidx.work.WorkManager.initialize(ctx, androidx.work.Configuration.Builder().build()) }
        ReminderPrefs.setRole(ctx, "TEACHER")
    }

    @Test fun panelShowsTheGuardianTextAndTheDefaultSchedule() {
        shadowOf(ctx).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        rule.setContent { MaterialTheme(lightColorScheme()) { ReminderPanel("GUARDIAN") } }
        rule.onNodeWithText("تذكير من التطبيق").assertIsDisplayed()
        rule.onNodeWithText("ساهم بكفالة أو دعم لحلقة أبنائك. افتح صفحة المساهمة.").assertIsDisplayed()
        rule.onNodeWithText("الخميس").assertIsDisplayed()
        rule.onNodeWithText("الساعة ٦:٠٠ م").assertIsDisplayed()
    }

    @Test fun switchingItOffIsSavedAndHidesTheSchedule() {
        shadowOf(ctx).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        rule.setContent { MaterialTheme(lightColorScheme()) { ReminderPanel("TEACHER") } }
        rule.onNode(isToggleable()).performClick()
        rule.waitForIdle()
        assertFalse(ReminderPrefs.load(ctx).enabled)
        assertEquals("switching off cancels the scheduled work", 0, scheduled())
        rule.onNodeWithText("التذكير مُطفأ. لن يصلك أي إشعار منه.").assertIsDisplayed()
        assertEquals(0, rule.onAllNodesWithText("أيام التذكير").fetchSemanticsNodes().size)
    }

    @Test fun pickingADayIsSaved() {
        shadowOf(ctx).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        rule.setContent { MaterialTheme(lightColorScheme()) { ReminderPanel("SUPERVISOR") } }
        rule.onNodeWithText("الأحد").performClick()
        rule.waitForIdle()
        assertTrue(DayOfWeek.SUNDAY in ReminderPrefs.load(ctx).days)
        assertTrue(DayOfWeek.THURSDAY in ReminderPrefs.load(ctx).days)
        assertEquals("one reminder is scheduled", 1, scheduled())
    }

    @Test fun withoutTheNotificationPermissionAWarningAndAButtonAreShown() {
        rule.setContent { MaterialTheme(lightColorScheme()) { ReminderPanel("GUARDIAN") } }
        rule.onNodeWithText("اسمح بالإشعارات ليصلك التذكير في وقته.").assertIsDisplayed()
        rule.onNodeWithText("السماح بالإشعارات").assertIsDisplayed()
    }

    @Test fun theCalendarButtonWritesADayFirstDate() {
        var value by mutableStateOf("")
        rule.setContent { MaterialTheme(lightColorScheme()) { DateField("تاريخ البداية (اختياري)", value, { value = it }, null, "تلميح", true) } }
        rule.onNodeWithContentDescription("اختيار تاريخ البداية (اختياري) من التقويم").performClick()
        rule.onNodeWithText("تأكيد").assertIsDisplayed()
        rule.onNodeWithText("تأكيد").performClick()
        rule.waitForIdle()
        assertTrue("got '$value'", Regex("\\d{2}-\\d{2}-\\d{4}").matches(value))
    }

    private fun shot(name: String, dark: Boolean, content: @androidx.compose.runtime.Composable () -> Unit) {
        shadowOf(ctx).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        rule.setContent {
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Rtl) {
                com.mohammedsamir.halaqati.ui.theme.HalaqatiTheme(com.mohammedsamir.halaqati.data.UiPreferences.defaults().copy(themeMode = if (dark) com.mohammedsamir.halaqati.data.ThemeMode.DARK else com.mohammedsamir.halaqati.data.ThemeMode.LIGHT, motion = com.mohammedsamir.halaqati.data.MotionLevel.REDUCED)) {
                    androidx.compose.material3.Surface(color = MaterialTheme.colorScheme.background, modifier = androidx.compose.ui.Modifier.fillMaxSize()) {
                        androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.padding(16.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) { content() }
                    }
                }
            }
        }
        rule.waitForIdle(); Thread.sleep(300); rule.waitForIdle()
        val view = rule.onRoot().fetchSemanticsNode().root as android.view.View
        val bmp = android.graphics.Bitmap.createBitmap(view.width.coerceAtLeast(1), view.height.coerceAtLeast(1), android.graphics.Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bmp))
        java.io.File("build/preview").mkdirs(); java.io.File("build/preview/$name.png").outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun previewReminderPanel() = shot("rc114_reminder_light", false) { ReminderPanel("GUARDIAN") }
    @Test fun previewReminderPanelDark() = shot("rc114_reminder_dark", true) { ReminderPanel("TEACHER") }
    @Test fun previewDateFields() = shot("rc114_datefields", false) {
        DateField("تاريخ البداية (اختياري)", "01-12-2026", {}, null, "اختر من التقويم أو اكتب 01-12-2026", true)
        DateField("تاريخ النهاية (اختياري)", "20-11-2026", {}, "تاريخ النهاية قبل تاريخ البداية", "اختر من التقويم", true)
    }
}
