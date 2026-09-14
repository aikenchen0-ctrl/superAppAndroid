package com.paifa.univerge.benchmark

import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Same-device edge input baseline. Run on a connected device with visible edge
 * triggers; retain the raw frame data with the device and build metadata.
 */
@RunWith(AndroidJUnit4::class)
class EdgeGestureLatencyBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun sideAndBottomGestureFrameTiming() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        iterations = 10,
        startupMode = StartupMode.WARM,
        setupBlock = {
            pressHome()
            startActivityAndWait()
        }
    ) {
        performSideGesture()
        performBottomGesture()
    }

    private fun MacrobenchmarkScope.startActivityAndWait() {
        startActivityAndWait(android.content.Intent().apply {
            setPackage(TARGET_PACKAGE)
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        device.wait(Until.hasObject(By.pkg(TARGET_PACKAGE).depth(0)), 5_000)
    }

    private fun MacrobenchmarkScope.performSideGesture() {
        val width = device.displayWidth
        val height = device.displayHeight
        device.swipe(2, height / 2, (width * 0.22f).toInt(), height / 2, 180)
    }

    private fun MacrobenchmarkScope.performBottomGesture() {
        val width = device.displayWidth
        val height = device.displayHeight
        device.swipe(width / 2, height - 4, width / 2, (height * 0.72f).toInt(), 180)
    }

    private companion object {
        const val TARGET_PACKAGE = "com.paifa.univerge"
    }
}
