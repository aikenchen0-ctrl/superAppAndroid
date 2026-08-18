package com.paifa.univerge.accessibility.floatingchat.message

import com.paifa.univerge.accessibility.floatingchat.chat.ChatThreadSelection
import com.paifa.univerge.core.model.FloatingChatMessagePresentation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DetailedBubbleStyleTest {
    @Test
    fun detailedBubbleIsUsedForUnreadOverviewAndGroupChatOnly() {
        assertTrue(usesDetailedMessageBubble(homeOverviewVisible = true, ChatThreadSelection.Private("friend")))
        assertTrue(usesDetailedMessageBubble(homeOverviewVisible = false, ChatThreadSelection.GroupChat("group")))
        assertFalse(usesDetailedMessageBubble(homeOverviewVisible = false, ChatThreadSelection.Private("friend")))
    }

    @Test
    fun senderNameFontIsExactlyHalfOfMessageContentFont() {
        assertEquals(7f, detailedBubbleSenderNameSizeSp(14f), 0f)
        assertEquals(5.5f, detailedBubbleSenderNameSizeSp(11f), 0f)
    }

    @Test
    fun senderNameCenterMatchesBubbleTopBorder() {
        val badgeHeight = detailedBubbleSenderNameBadgeHeightDp()
        val badgeTop = detailedBubbleSenderNameTopOffsetDp()

        assertEquals(0f, badgeTop + badgeHeight / 2f, 0f)
    }

    @Test
    fun simpleAndSystemBubblesDoNotShowSenderName() {
        assertFalse(shouldShowDetailedBubbleSenderName(false, FloatingChatMessagePresentation.Bubble))
        assertFalse(shouldShowDetailedBubbleSenderName(true, FloatingChatMessagePresentation.System))
        assertFalse(shouldShowDetailedBubbleSenderName(true, FloatingChatMessagePresentation.MediaStandalone))
        assertTrue(shouldShowDetailedBubbleSenderName(true, FloatingChatMessagePresentation.Bubble))
    }

    @Test
    fun detailedBubbleUsesStrongBlurAndTextShadow() {
        assertTrue(detailedBubbleSenderNameBlurRadiusPx() >= 16f)
        assertTrue(detailedBubbleTextUsesShadow())
    }

    @Test
    fun renderPathUsesExplicitStyleParameterAndroidBlurAndShadowContext() {
        val projectRoot = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
            .first { File(it, "univerge-accessibility").isDirectory }
        val rowSource = File(
            projectRoot,
            "univerge-accessibility/src/main/kotlin/com/paifa/univerge/accessibility/floatingchat/message/MessageRow.kt"
        ).readText()
        val styleSource = File(
            projectRoot,
            "univerge-accessibility/src/main/kotlin/com/paifa/univerge/accessibility/floatingchat/message/MessageBubbleStyle.kt"
        ).readText()

        assertTrue(rowSource.contains("detailedBubble: Boolean = usesDetailedMessageBubble"))
        assertTrue(rowSource.contains("LocalOverlayTextShadow provides OverlayTokens.imModuleTextShadow"))
        assertTrue(styleSource.contains("RenderEffect.createBlurEffect"))
        assertTrue(styleSource.contains(".asComposeRenderEffect()"))
    }
}
