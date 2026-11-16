package com.mohammedsamir.halaqati.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val PACKAGE = "com.mohammedsamir.halaqati"

/** Taps a bottom-bar / button label if present (works for every role; absent labels are skipped). */
private fun MacrobenchmarkScope.tapText(text: String) {
    device.wait(Until.findObject(By.text(text)), 3_000)?.click()
    device.waitForIdle()
}

private fun MacrobenchmarkScope.scrollMain() {
    val list = device.findObject(By.scrollable(true)) ?: return
    list.setGestureMargin(device.displayWidth / 5)
    repeat(2) { list.fling(Direction.DOWN); device.waitForIdle() }
    list.fling(Direction.UP)
}

/**
 * RC17 journey benchmarks (FrameTimingMetric = jank). Run on a logged-in device/emulator:
 *   ./gradlew :benchmark:connectedBenchmarkAndroidTest   (after enabling include(":benchmark"))
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class JourneyBenchmarks {
    @get:Rule val rule = MacrobenchmarkRule()

    private fun journey(iterations: Int = 5, block: MacrobenchmarkScope.() -> Unit) = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        startupMode = StartupMode.WARM,
        iterations = iterations,
        setupBlock = { pressHome(); startActivityAndWait() },
        measureBlock = block,
    )

    @Test fun homeScroll() = journey { scrollMain() }
    @Test fun homeToStudentsScroll() = journey { tapText("الطلاب"); scrollMain(); tapText("الرئيسية") }
    @Test fun sessionsTab() = journey { tapText("الجلسات"); scrollMain(); tapText("الرئيسية") }
    @Test fun guardianChildren() = journey { tapText("أبنائي"); scrollMain(); tapText("الإنجازات"); tapText("الرئيسية") }
    @Test fun reportsScroll() = journey { device.pressMenu(); tapText("التقارير"); scrollMain() }
}

/** Generates a real baseline profile from the critical journeys (replaces the hand-written class list). */
@RunWith(AndroidJUnit4::class)
@LargeTest
class BaselineProfileGenerator {
    @get:Rule val rule = BaselineProfileRule()

    @Test fun generate() = rule.collect(packageName = PACKAGE) {
        pressHome(); startActivityAndWait()
        scrollMain()
        listOf("الطلاب", "الجلسات", "الخطط", "أبنائي", "الإنجازات", "الرئيسية").forEach { tapText(it); scrollMain() }
    }
}
