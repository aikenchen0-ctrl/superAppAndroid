package com.paifa.ubikitouch.benchmark

import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.benchmark.macro.measureRepeated
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test

class FloatingChatBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun coldStart() = benchmarkRule.measureRepeated(
        packageName = "com.paifa.ubikitouch",
        metrics = listOf(StartupTimingMetric()),
        iterations = 20,
        startupMode = StartupMode.COLD,
        setupBlock = {
            pressHome()
        }
    ) {
        device.wait(Until.hasObject(By.pkg("com.paifa.ubikitouch").depth(0)), 5_000)
    }
}
