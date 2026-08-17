package com.paifa.ubikitouch.accessibility.floatingchat.message

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MessageRevokeConfirmationUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test(timeout = 60_000)
    fun cancelDismissesWithoutConfirming() {
        var dismissCalls = 0
        var confirmCalls = 0
        composeRule.setContent {
            MessageRevokeConfirmationDialog(
                onDismiss = { dismissCalls += 1 },
                onConfirm = { confirmCalls += 1 }
            )
        }

        composeRule.onNodeWithText("确定撤销").assertIsDisplayed()
        composeRule.onNodeWithText("撤销后，此消息将从当前会话中撤回。").assertIsDisplayed()
        composeRule.onNodeWithText("取消").performClick()

        composeRule.runOnIdle {
            assertEquals(1, dismissCalls)
            assertEquals(0, confirmCalls)
        }
    }

    @Test(timeout = 60_000)
    fun confirmInvokesTheWriteIntentOnlyOnce() {
        var dismissCalls = 0
        var confirmCalls = 0
        composeRule.setContent {
            MessageRevokeConfirmationDialog(
                onDismiss = { dismissCalls += 1 },
                onConfirm = { confirmCalls += 1 }
            )
        }

        composeRule.onNodeWithText("确定").performClick()

        composeRule.runOnIdle {
            assertEquals(0, dismissCalls)
            assertEquals(1, confirmCalls)
        }
    }

    @Test
    fun confirmationUsesMaterial3CardInsideTheExistingOverlayWindow() {
        val source = java.io.File(
            "src/main/kotlin/com/paifa/ubikitouch/accessibility/floatingchat/message/" +
                "MessageInteractionOverlayHost.kt"
        ).readText()

        assertFalse(Regex("(?m)^\\s*(AlertDialog|Dialog)\\(").containsMatchIn(source))
        assertTrue(source.contains("MaterialTheme.colorScheme.scrim"))
        assertTrue(source.contains("RoundedCornerShape(28.dp)"))
        assertTrue(source.contains("shadowElevation = 6.dp"))
    }
}
