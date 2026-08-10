# Compose Back Wave Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an SDK-owned inward back gesture with a Compose wave visual affordance on the existing UbikiTouch edge overlay system.

**Architecture:** Keep the existing transparent Android overlay views as the touch interception layer. Add a pure Kotlin back drag progress model, extend overlay gesture callbacks, and let `UbikiAccessibilityService` manage a separate non-touchable Compose wave overlay that mirrors drag progress and dismisses on release/cancel.

**Tech Stack:** Kotlin, Android AccessibilityService, WindowManager `TYPE_ACCESSIBILITY_OVERLAY`, Jetpack Compose `ComposeView` and `Canvas`, JUnit 4.

---

## File Structure

- Create `ubiki-core/src/main/kotlin/com/paifa/ubikitouch/core/gesture/BackGestureProgress.kt`
  - Pure Kotlin threshold/progress model for inward back drag.
- Create `ubiki-core/src/test/kotlin/com/paifa/ubikitouch/core/gesture/BackGestureProgressTest.kt`
  - JVM tests for progress clamping and commit/cancel decisions.
- Modify `ubiki-overlay/src/main/kotlin/com/paifa/ubikitouch/overlay/EdgeGestureDetector.kt`
  - Emit back drag progress, release, and cancel callbacks without changing existing gesture output.
- Modify `ubiki-overlay/src/main/kotlin/com/paifa/ubikitouch/overlay/EdgeOverlayView.kt`
  - Accept and forward back drag callbacks.
- Modify `ubiki-accessibility/build.gradle.kts`
  - Enable Compose in the accessibility runtime module and add Compose dependencies.
- Create `ubiki-accessibility/src/main/kotlin/com/paifa/ubikitouch/accessibility/BackWaveOverlayController.kt`
  - Owns the Compose visual overlay lifecycle and rendering state.
- Modify `ubiki-accessibility/src/main/kotlin/com/paifa/ubikitouch/accessibility/UbikiAccessibilityService.kt`
  - Wire back drag callbacks from edge overlays to the wave controller and existing Back action execution path.

---

### Task 1: Core Back Drag Progress Model

**Files:**
- Create: `ubiki-core/src/main/kotlin/com/paifa/ubikitouch/core/gesture/BackGestureProgress.kt`
- Test: `ubiki-core/src/test/kotlin/com/paifa/ubikitouch/core/gesture/BackGestureProgressTest.kt`

- [ ] **Step 1: Write the failing tests**

Create `ubiki-core/src/test/kotlin/com/paifa/ubikitouch/core/gesture/BackGestureProgressTest.kt`:

```kotlin
package com.paifa.ubikitouch.core.gesture

import com.paifa.ubikitouch.core.model.EdgeSide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackGestureProgressTest {
    @Test
    fun progressClampsBetweenZeroAndOne() {
        val halfway = BackGestureProgress.fromDelta(
            side = EdgeSide.LEFT,
            dx = 24f,
            dy = 0f,
            thresholdPx = 48f
        )
        val beyond = BackGestureProgress.fromDelta(
            side = EdgeSide.LEFT,
            dx = 96f,
            dy = 0f,
            thresholdPx = 48f
        )

        assertNotNull(halfway)
        assertEquals(0.5f, halfway!!.progress, 0.001f)
        assertNotNull(beyond)
        assertEquals(1f, beyond!!.progress, 0.001f)
    }

    @Test
    fun releaseBelowThresholdCancels() {
        val progress = BackGestureProgress.fromDelta(
            side = EdgeSide.LEFT,
            dx = 32f,
            dy = 0f,
            thresholdPx = 48f
        )

        assertNotNull(progress)
        assertFalse(progress!!.committed)
    }

    @Test
    fun releaseAtThresholdCommits() {
        val progress = BackGestureProgress.fromDelta(
            side = EdgeSide.RIGHT,
            dx = -48f,
            dy = 0f,
            thresholdPx = 48f
        )

        assertNotNull(progress)
        assertTrue(progress!!.committed)
    }

    @Test
    fun rejectsOutwardAndMostlyVerticalMovement() {
        assertNull(
            BackGestureProgress.fromDelta(
                side = EdgeSide.LEFT,
                dx = -48f,
                dy = 0f,
                thresholdPx = 48f
            )
        )
        assertNull(
            BackGestureProgress.fromDelta(
                side = EdgeSide.LEFT,
                dx = 8f,
                dy = 80f,
                thresholdPx = 48f
            )
        )
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```powershell
.\gradlew.bat :ubiki-core:test --no-daemon
```

Expected: compilation fails because `BackGestureProgress` does not exist.

- [ ] **Step 3: Add the minimal implementation**

Create `ubiki-core/src/main/kotlin/com/paifa/ubikitouch/core/gesture/BackGestureProgress.kt`:

```kotlin
package com.paifa.ubikitouch.core.gesture

import com.paifa.ubikitouch.core.model.EdgeSide
import kotlin.math.abs

data class BackGestureProgress(
    val side: EdgeSide,
    val dragDistancePx: Float,
    val thresholdPx: Float
) {
    val progress: Float =
        if (thresholdPx <= 0f) 1f else (dragDistancePx / thresholdPx).coerceIn(0f, 1f)

    val committed: Boolean = dragDistancePx >= thresholdPx

    companion object {
        fun fromDelta(
            side: EdgeSide,
            dx: Float,
            dy: Float,
            thresholdPx: Float,
            verticalSwipeRatio: Float = DEFAULT_VERTICAL_SWIPE_RATIO
        ): BackGestureProgress? {
            if (!isInward(side, dx)) return null
            val absX = abs(dx)
            val absY = abs(dy)
            if (absX == 0f || absY > absX * verticalSwipeRatio) return null
            return BackGestureProgress(
                side = side,
                dragDistancePx = absX,
                thresholdPx = thresholdPx.coerceAtLeast(1f)
            )
        }

        private fun isInward(side: EdgeSide, dx: Float): Boolean {
            return when (side) {
                EdgeSide.LEFT -> dx > 0f
                EdgeSide.RIGHT -> dx < 0f
            }
        }

        private const val DEFAULT_VERTICAL_SWIPE_RATIO = 2.75f
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run:

```powershell
.\gradlew.bat :ubiki-core:test --no-daemon
```

Expected: `BackGestureProgressTest`, `SwipeClassifierTest`, `GestureActionTest`, and `GestureTypeTest` pass. If the local Gradle worker environment fails before tests execute, record the environment failure and continue with code review.

---

### Task 2: Overlay Back Drag Callbacks

**Files:**
- Modify: `ubiki-overlay/src/main/kotlin/com/paifa/ubikitouch/overlay/EdgeGestureDetector.kt`
- Modify: `ubiki-overlay/src/main/kotlin/com/paifa/ubikitouch/overlay/EdgeOverlayView.kt`

- [ ] **Step 1: Extend detector constructor and state**

Modify `EdgeGestureDetector` imports and constructor:

```kotlin
import com.paifa.ubikitouch.core.gesture.BackGestureProgress
```

```kotlin
class EdgeGestureDetector(
    private val side: EdgeSide,
    private val minSwipeDistancePx: Float,
    private val onGesture: (GestureType, GestureData) -> Unit,
    private val onBackGestureProgress: (BackGestureProgress) -> Unit = {},
    private val onBackGestureEnd: (BackGestureProgress) -> Unit = {},
    private val onBackGestureCancel: () -> Unit = {}
) {
```

Add state:

```kotlin
private var backProgressActive = false
```

- [ ] **Step 2: Emit progress while moving**

In `handleMove`, call `updateBackGestureProgress(event)` before returning from both `TOUCH_DOWN` and `SWIPING` paths:

```kotlin
private fun handleMove(event: MotionEvent) {
    updateBackGestureProgress(event)
    if (state == State.SWIPING) {
        updatePullHoldCandidate(event)
        return
    }
    if (state != State.TOUCH_DOWN) return
    val distance = hypot(event.x - startX, event.y - startY)
    if (distance > minSwipeDistancePx) {
        handler.removeCallbacks(longPressRunnable)
        state = State.SWIPING
        updatePullHoldCandidate(event)
    }
}
```

Add helper:

```kotlin
private fun updateBackGestureProgress(event: MotionEvent): BackGestureProgress? {
    val progress = BackGestureProgress.fromDelta(
        side = side,
        dx = event.x - startX,
        dy = event.y - startY,
        thresholdPx = minSwipeDistancePx
    )
    if (progress == null) {
        cancelBackProgress()
        return null
    }
    backProgressActive = true
    onBackGestureProgress(progress)
    return progress
}
```

- [ ] **Step 3: Emit release or cancel**

In `handleUp`, compute end progress before the `when`:

```kotlin
val backProgress = updateBackGestureProgress(event)
if (backProgress != null) {
    onBackGestureEnd(backProgress)
    backProgressActive = false
} else {
    cancelBackProgress()
}
```

Add helper:

```kotlin
private fun cancelBackProgress() {
    if (!backProgressActive) return
    backProgressActive = false
    onBackGestureCancel()
}
```

Update `reset()` to call `cancelBackProgress()` before removing callbacks:

```kotlin
private fun reset() {
    state = State.IDLE
    tapCount = 0
    lastTapTimeMs = 0L
    pullHoldArmed = false
    cancelBackProgress()
    handler.removeCallbacks(longPressRunnable)
    handler.removeCallbacks(tapTimeoutRunnable)
    handler.removeCallbacks(pullHoldRunnable)
}
```

- [ ] **Step 4: Forward callbacks from EdgeOverlayView**

Update `EdgeOverlayView` imports:

```kotlin
import com.paifa.ubikitouch.core.gesture.BackGestureProgress
```

Update constructor:

```kotlin
class EdgeOverlayView(
    context: Context,
    private val side: EdgeSide,
    private val showIndicator: Boolean,
    private val opacityPercent: Int,
    swipeThresholdDp: Int,
    private val onGesture: (GestureType, GestureData) -> Unit,
    private val onBackGestureProgress: (BackGestureProgress) -> Unit = {},
    private val onBackGestureEnd: (BackGestureProgress) -> Unit = {},
    private val onBackGestureCancel: () -> Unit = {}
) : View(context) {
```

Update detector construction:

```kotlin
private val detector = EdgeGestureDetector(
    side = side,
    minSwipeDistancePx = swipeThresholdDp.coerceIn(8, 120) * resources.displayMetrics.density,
    onGesture = onGesture,
    onBackGestureProgress = onBackGestureProgress,
    onBackGestureEnd = onBackGestureEnd,
    onBackGestureCancel = onBackGestureCancel
)
```

- [ ] **Step 5: Run compile check**

Run:

```powershell
.\gradlew.bat :ubiki-overlay:compileDebugKotlin --no-daemon
```

Expected: overlay module compiles.

---

### Task 3: Compose Wave Overlay Runtime

**Files:**
- Modify: `ubiki-accessibility/build.gradle.kts`
- Create: `ubiki-accessibility/src/main/kotlin/com/paifa/ubikitouch/accessibility/BackWaveOverlayController.kt`

- [ ] **Step 1: Enable Compose in accessibility module**

Modify `ubiki-accessibility/build.gradle.kts` plugins:

```kotlin
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("maven-publish")
}
```

Add build features inside `android`:

```kotlin
buildFeatures {
    compose = true
}
```

Add dependencies:

```kotlin
implementation(platform("androidx.compose:compose-bom:2024.12.01"))
implementation("androidx.compose.foundation:foundation")
implementation("androidx.compose.ui:ui")
```

- [ ] **Step 2: Create controller and Compose wave**

Create `ubiki-accessibility/src/main/kotlin/com/paifa/ubikitouch/accessibility/BackWaveOverlayController.kt`:

```kotlin
package com.paifa.ubikitouch.accessibility

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.ComposeView
import com.paifa.ubikitouch.core.gesture.BackGestureProgress
import com.paifa.ubikitouch.core.model.EdgeSide

internal class BackWaveOverlayController(
    private val context: Context,
    private val windowManager: WindowManager
) {
    private var composeView: ComposeView? = null
    private var activeSide: EdgeSide? = null
    private val waveState: MutableState<BackWaveState> = mutableStateOf(BackWaveState.Hidden)

    fun update(progress: BackGestureProgress) {
        ensureView(progress.side)
        waveState.value = BackWaveState.Visible(
            side = progress.side,
            progress = progress.progress,
            committed = progress.committed
        )
    }

    fun finish(progress: BackGestureProgress) {
        waveState.value = BackWaveState.Visible(
            side = progress.side,
            progress = progress.progress,
            committed = progress.committed
        )
        dismiss()
    }

    fun dismiss() {
        val view = composeView ?: return
        runCatching { windowManager.removeView(view) }
        composeView = null
        activeSide = null
        waveState.value = BackWaveState.Hidden
    }

    private fun ensureView(side: EdgeSide) {
        if (composeView != null && activeSide == side) return
        dismiss()
        activeSide = side
        val view = ComposeView(context).apply {
            setContent {
                BackWaveOverlay(waveState.value)
            }
        }
        composeView = view
        runCatching {
            windowManager.addView(view, layoutParams(side))
        }.onFailure {
            composeView = null
            activeSide = null
        }
    }

    private fun layoutParams(side: EdgeSide): WindowManager.LayoutParams {
        val density = context.resources.displayMetrics.density
        val width = (WAVE_WIDTH_DP * density).toInt().coerceAtLeast(1)
        return WindowManager.LayoutParams(
            width,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = if (side == EdgeSide.LEFT) 0 else context.resources.displayMetrics.widthPixels - width
            y = 0
        }
    }

    private companion object {
        const val WAVE_WIDTH_DP = 148
    }
}

private sealed class BackWaveState {
    data object Hidden : BackWaveState()
    data class Visible(
        val side: EdgeSide,
        val progress: Float,
        val committed: Boolean
    ) : BackWaveState()
}

@Composable
private fun BackWaveOverlay(state: BackWaveState) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        if (state !is BackWaveState.Visible) return@Canvas
        val progress = state.progress.coerceIn(0f, 1f)
        val waveWidth = size.width * (0.25f + 0.75f * progress)
        val centerY = size.height / 2f
        val color = if (state.committed) {
            Color(0xCC2E7DFF)
        } else {
            Color(0x883C8DFF)
        }
        val path = Path()
        if (state.side == EdgeSide.LEFT) {
            path.moveTo(0f, 0f)
            path.lineTo(waveWidth * 0.48f, 0f)
            path.cubicTo(waveWidth, centerY * 0.45f, waveWidth, centerY * 1.55f, waveWidth * 0.48f, size.height)
            path.lineTo(0f, size.height)
            path.close()
        } else {
            path.moveTo(size.width, 0f)
            path.lineTo(size.width - waveWidth * 0.48f, 0f)
            path.cubicTo(
                size.width - waveWidth,
                centerY * 0.45f,
                size.width - waveWidth,
                centerY * 1.55f,
                size.width - waveWidth * 0.48f,
                size.height
            )
            path.lineTo(size.width, size.height)
            path.close()
        }
        drawPath(path, color)

        val arrowCenterX = if (state.side == EdgeSide.LEFT) {
            waveWidth * 0.38f
        } else {
            size.width - waveWidth * 0.38f
        }
        val arrowSize = 16f + 18f * progress
        val arrowStroke = Stroke(
            width = 5f + 2f * progress,
            cap = StrokeCap.Round
        )
        val arrow = Path()
        if (state.side == EdgeSide.LEFT) {
            arrow.moveTo(arrowCenterX + arrowSize * 0.45f, centerY - arrowSize)
            arrow.lineTo(arrowCenterX - arrowSize * 0.35f, centerY)
            arrow.lineTo(arrowCenterX + arrowSize * 0.45f, centerY + arrowSize)
        } else {
            arrow.moveTo(arrowCenterX - arrowSize * 0.45f, centerY - arrowSize)
            arrow.lineTo(arrowCenterX + arrowSize * 0.35f, centerY)
            arrow.lineTo(arrowCenterX - arrowSize * 0.45f, centerY + arrowSize)
        }
        drawPath(arrow, Color.White.copy(alpha = 0.55f + 0.45f * progress), style = arrowStroke)
        drawCircle(
            color = Color.White.copy(alpha = 0.16f * progress),
            radius = arrowSize * 1.6f,
            center = Offset(arrowCenterX, centerY)
        )
    }
}
```

- [ ] **Step 3: Run compile check**

Run:

```powershell
.\gradlew.bat :ubiki-accessibility:compileDebugKotlin --no-daemon
```

Expected: accessibility module compiles. If Compose dependency resolution is blocked by local cache or network, record the dependency failure and retry only after approval for dependency download if needed.

---

### Task 4: Service Wiring

**Files:**
- Modify: `ubiki-accessibility/src/main/kotlin/com/paifa/ubikitouch/accessibility/UbikiAccessibilityService.kt`

- [ ] **Step 1: Add controller field and initialize it**

Add import:

```kotlin
import com.paifa.ubikitouch.core.gesture.BackGestureProgress
import com.paifa.ubikitouch.core.model.GestureAction
import com.paifa.ubikitouch.core.model.GestureType
```

Add field:

```kotlin
private lateinit var backWaveOverlayController: BackWaveOverlayController
```

Initialize in `onServiceConnected()` after `windowManager`:

```kotlin
backWaveOverlayController = BackWaveOverlayController(this, windowManager)
```

- [ ] **Step 2: Wire overlay callbacks in createOverlays**

Update `EdgeOverlayView` construction:

```kotlin
val overlay = EdgeOverlayView(
    context = this,
    side = side,
    showIndicator = preferences.showIndicators,
    opacityPercent = preferences.overlayOpacity,
    swipeThresholdDp = preferences.swipeThresholdDp,
    onGesture = { type, data ->
        Log.d(TAG, "gesture side=${side.id} type=${type.id}")
        actionExecutor.execute(preferences.actionFor(side, type), data)
    },
    onBackGestureProgress = { progress ->
        handleBackGestureProgress(side, progress)
    },
    onBackGestureEnd = { progress ->
        handleBackGestureEnd(side, progress)
    },
    onBackGestureCancel = {
        if (::backWaveOverlayController.isInitialized) {
            backWaveOverlayController.dismiss()
        }
    }
)
```

Add helpers:

```kotlin
private fun handleBackGestureProgress(side: EdgeSide, progress: BackGestureProgress) {
    if (!shouldShowBackWave(side)) return
    backWaveOverlayController.update(progress)
}

private fun handleBackGestureEnd(side: EdgeSide, progress: BackGestureProgress) {
    if (!shouldShowBackWave(side)) {
        if (::backWaveOverlayController.isInitialized) {
            backWaveOverlayController.dismiss()
        }
        return
    }
    backWaveOverlayController.finish(progress)
}

private fun shouldShowBackWave(side: EdgeSide): Boolean {
    if (!::preferences.isInitialized) return false
    return preferences.actionFor(side, GestureType.PULL_INWARD) == GestureAction.Back
}
```

- [ ] **Step 3: Dismiss wave on overlay removal and destruction**

At the start of `removeAllOverlays()`:

```kotlin
if (::backWaveOverlayController.isInitialized) {
    backWaveOverlayController.dismiss()
}
```

This ensures preference refresh, screen off, and service destroy remove pending visual state.

- [ ] **Step 4: Run compile check**

Run:

```powershell
.\gradlew.bat :ubiki-accessibility:compileDebugKotlin --no-daemon
```

Expected: accessibility module compiles.

---

### Task 5: Verification

**Files:**
- No new source files.

- [ ] **Step 1: Run focused JVM tests**

Run:

```powershell
.\gradlew.bat :ubiki-core:test --no-daemon
```

Expected: core tests pass. If the local Gradle Test Executor still fails with `GradleWorkerMain`, record it as an environment issue.

- [ ] **Step 2: Run debug compile**

Run:

```powershell
.\gradlew.bat :ubiki-overlay:compileDebugKotlin :ubiki-accessibility:compileDebugKotlin :app:compileDebugKotlin --no-daemon
```

Expected: all Kotlin compilation tasks pass.

- [ ] **Step 3: Run broader build if local SDK is complete**

Run:

```powershell
.\gradlew.bat lintDebug assembleDebug --no-daemon
```

Expected: lint and debug APK build pass. If Android SDK platform 36 is missing, install or configure `compileSdk` tooling before treating failures as code failures.

- [ ] **Step 4: Manual behavior check on device**

Run the existing install and accessibility setup scripts:

```powershell
.\scripts\adb-install-debug.ps1
.\scripts\adb-enable-accessibility-dev.ps1
```

Then verify:

- left inward drag shows a left-side wave
- right inward drag shows a right-side wave
- releasing before threshold dismisses the wave and does not go back
- releasing past threshold dismisses the wave and performs Back
- vertical edge swipes still map to vertical gesture actions
- long press, tap, double tap, and pull-hold still work

---

## Plan Self-Review

Spec coverage:

- SDK-owned back gesture: covered by Tasks 1, 2, and 4.
- Compose wave overlay: covered by Task 3.
- Existing overlay remains the gesture layer: covered by Tasks 2 and 4.
- Existing disable/block/pause constraints: preserved by wiring through existing overlay creation path in Task 4.
- Error handling and lifecycle cleanup: covered by Tasks 3 and 4.
- JVM model tests: covered by Task 1 and Task 5.

Placeholder scan:

- The plan contains no unresolved placeholder language or undefined future task.

Type consistency:

- `BackGestureProgress` is created in Task 1 and reused by Tasks 2, 3, and 4.
- Callback names are consistent across `EdgeGestureDetector`, `EdgeOverlayView`, and `UbikiAccessibilityService`.
