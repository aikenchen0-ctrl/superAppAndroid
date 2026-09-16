package com.paifa.univerge.accessibility.floatingchat.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.paifa.univerge.accessibility.BottomGestureBarGestureType
import com.paifa.univerge.accessibility.bottomGestureBarTouchHeightDp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FloatingChatBottomGestureBarTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test(timeout = 60_000)
    fun directionalMoveCommitsBeforeReleaseUsingConfiguredWidth() {
        val events = mutableListOf<BottomGestureBarGestureType>()
        composeRule.setContent {
            Box {
                FloatingChatExpandedBottomGestureBar(
                    widthDp = 200,
                    onGesture = { type, _ -> events += type },
                    modifier = Modifier.testTag("bottom-bar")
                )
            }
        }

        composeRule.onNodeWithTag("bottom-bar").assertWidthIsEqualTo(200.dp)
        composeRule.onNodeWithTag("bottom-bar").assertHeightIsEqualTo(bottomGestureBarTouchHeightDp().dp)
        composeRule.onNodeWithTag("bottom-bar").performTouchInput {
            down(center)
            moveBy(Offset(0f, -120f))
        }

        composeRule.runOnIdle {
            assertEquals(listOf(BottomGestureBarGestureType.SwipeUp), events)
        }
        composeRule.onNodeWithTag("bottom-bar").performTouchInput { up() }

        composeRule.runOnIdle {
            assertEquals(1, events.size)
            assertEquals(BottomGestureBarGestureType.SwipeUp, events.single())
        }
    }

    @Test(timeout = 60_000)
    fun upwardSwipeThatStopsBeforeReleaseCommitsHoldBeforeUp() {
        val events = mutableListOf<BottomGestureBarGestureType>()
        composeRule.setContent {
            FloatingChatExpandedBottomGestureBar(
                onGesture = { type, _ -> events += type },
                enableSwipeUpHold = true,
                modifier = Modifier.testTag("bottom-bar")
            )
        }

        composeRule.onNodeWithTag("bottom-bar").performTouchInput {
            down(center)
            moveBy(Offset(0f, -120f))
            advanceEventTime(600)
            moveBy(Offset.Zero)
            up()
        }

        composeRule.runOnIdle {
            assertEquals(listOf(BottomGestureBarGestureType.SwipeUpHold), events)
        }
    }

    @Test(timeout = 60_000)
    fun secondPointerCancelsTheBottomGestureWithoutCommitting() {
        val events = mutableListOf<BottomGestureBarGestureType>()
        composeRule.setContent {
            FloatingChatExpandedBottomGestureBar(
                onGesture = { type, _ -> events += type },
                modifier = Modifier.testTag("bottom-bar")
            )
        }

        composeRule.onNodeWithTag("bottom-bar").performTouchInput {
            down(pointerId = 0, position = center)
            down(pointerId = 1, position = center + Offset(12f, 0f))
            up(pointerId = 1)
            up(pointerId = 0)
        }

        composeRule.runOnIdle { assertTrue(events.isEmpty()) }
    }

    @Test(timeout = 60_000)
    fun completedGestureCommitsExactlyOnce() {
        val events = mutableListOf<BottomGestureBarGestureType>()
        composeRule.setContent {
            FloatingChatExpandedBottomGestureBar(
                onGesture = { type, _ -> events += type },
                modifier = Modifier.testTag("bottom-bar")
            )
        }

        composeRule.onNodeWithTag("bottom-bar").performTouchInput {
            down(center)
            moveBy(Offset(120f, 0f))
            up()
        }

        composeRule.runOnIdle { assertEquals(listOf(BottomGestureBarGestureType.SwipeHorizontal), events) }
    }

    @Test(timeout = 60_000)
    fun recompositionKeepsTheActivePointerSessionAndUsesTheLatestCallback() {
        val events = mutableListOf<Int>()
        var callbackVersion by mutableStateOf(0)
        composeRule.setContent {
            val version = callbackVersion
            FloatingChatExpandedBottomGestureBar(
                onGesture = { _, _ -> events += version },
                modifier = Modifier.testTag("bottom-bar")
            )
        }

        composeRule.onNodeWithTag("bottom-bar").performTouchInput { down(center) }
        composeRule.runOnIdle { callbackVersion = 1 }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("bottom-bar").performTouchInput { up() }

        composeRule.runOnIdle { assertEquals(listOf(1), events) }
    }
}
