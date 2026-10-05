package com.mrturingdev.ssclassify.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Scroll jank on the home screenshot grid. Each iteration restarts the app so the thumbnail
 * cache is cold, then flings down the grid: the worst case, with every tile decoding on the way.
 *
 * Grants photo access itself and runs the first scan when the app has none, so it works on a
 * fresh install. Needs screenshots on the device.
 */
@RunWith(AndroidJUnit4::class)
class ScrollJankBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    @Before
    fun grantPhotoAccess() {
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            .executeShellCommand("pm grant $TARGET_PACKAGE android.permission.READ_MEDIA_IMAGES")
    }

    @Test
    fun scrollGridColdThumbnails() = rule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        // Full AOT: measures steady-state code, not JIT warm-up.
        compilationMode = CompilationMode.Full(),
        iterations = 5,
        setupBlock = {
            killProcess()
            startActivityAndWait()
            if (!device.wait(Until.hasObject(By.res(GRID_TAG)), 10_000)) {
                // Fresh install: scan once. The results persist across iterations.
                device.findObject(By.text("Start Scanning"))?.click()
                check(device.wait(Until.hasObject(By.res(GRID_TAG)), 600_000)) {
                    "Screenshot grid not shown after scanning. Are there screenshots on the device?"
                }
            }
        },
    ) {
        val grid = device.findObject(By.res(GRID_TAG))
        // Keep the gesture clear of the system gesture areas at the screen edges.
        grid.setGestureMargin(device.displayWidth / 5)
        repeat(FLINGS) {
            grid.fling(Direction.DOWN)
        }
        device.waitForIdle()
    }

    private companion object {
        const val TARGET_PACKAGE = "com.mrturingdev.ssclassify.android"
        const val GRID_TAG = "screenshot_grid"
        const val FLINGS = 6
    }
}
