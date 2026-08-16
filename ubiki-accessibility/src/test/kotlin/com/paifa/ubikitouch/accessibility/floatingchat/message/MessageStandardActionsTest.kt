package com.paifa.ubikitouch.accessibility.floatingchat.message

import com.paifa.ubikitouch.accessibility.floatingchat.aivoice.MessageAsideRequestTracker
import com.paifa.ubikitouch.core.model.FloatingChatMessage
import com.paifa.ubikitouch.core.model.FloatingChatMessageType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageStandardActionsTest {
    @Test
    fun stickerCopyDoesNotExposePayloadOrDownloadUrl() {
        val message = FloatingChatMessage(
            id = "sticker-1",
            type = FloatingChatMessageType.StickerGif,
            text = "{\"Md5\":\"1c3c326f0d065a84dd2c7ac9638910bb\",\"Thumb\":\"http://vweixinf.tc.qq.com/sticker.png\",\"Size\":23770}",
            fromMe = false,
            senderName = "张三",
            time = "10:00",
            detail = "GIF 贴纸",
            resourceUrl = "http://vweixinf.tc.qq.com/sticker.png",
            thumbnailUrl = "http://vweixinf.tc.qq.com/sticker.png"
        )

        assertEquals("[表情]", message.longPressCopyText())
    }

    @Test
    fun primaryActionsFollowTheEightActionProductOrder() {
        assertEquals(
            listOf(
                MessageLongPressAction.Listen,
                MessageLongPressAction.Copy,
                MessageLongPressAction.Forward,
                MessageLongPressAction.Favorite,
                MessageLongPressAction.MultiSelect,
                MessageLongPressAction.Quote,
                MessageLongPressAction.Zoom,
                MessageLongPressAction.Delete
            ),
            messageLongPressPrimaryActions()
        )
    }

    @Test
    fun onlySyncedVoiceMessagesExposeTranscribeInTheClickMenu() {
        val syncedVoice = FloatingChatMessage(
            id = "voice-1",
            type = FloatingChatMessageType.Voice,
            text = "[语音]",
            fromMe = false,
            senderName = "张三",
            time = "10:00",
            remoteMessageId = 71L
        )

        assertEquals(
            MessageLongPressAction.Transcribe,
            messageLongPressActionsFor(syncedVoice).first()
        )
        assertFalse(
            messageLongPressActionsFor(syncedVoice.copy(remoteMessageId = null))
                .contains(MessageLongPressAction.Transcribe)
        )
        assertFalse(
            messageLongPressActionsFor(syncedVoice.copy(remoteMessageId = 0L))
                .contains(MessageLongPressAction.Transcribe)
        )
        assertFalse(
            messageLongPressActionsFor(syncedVoice.copy(remoteMessageId = -1L))
                .contains(MessageLongPressAction.Transcribe)
        )
        assertFalse(
            messageLongPressActionsFor(syncedVoice.copy(type = FloatingChatMessageType.Text))
                .contains(MessageLongPressAction.Transcribe)
        )
    }

    @Test
    fun transcribeActionForwardsTheSelectedMessageAndClosesTheMenu() {
        val message = FloatingChatMessage(
            id = "voice-71",
            type = FloatingChatMessageType.Voice,
            text = "[语音]",
            fromMe = false,
            senderName = "张三",
            time = "10:00",
            remoteMessageId = 71L
        )
        var transcribeTarget: FloatingChatMessage? = null
        var menuClosed = false
        val actions = MessageLongPressActions(
            favoriteMessageIds = mutableMapOf(),
            hiddenMessageIds = mutableMapOf(),
            selectedMessageIds = mutableMapOf(),
            onCopyText = {},
            onShowToast = {},
            onBeginForward = {},
            onFavoriteChanged = { _, _ -> },
            onMultiSelectModeChanged = {},
            onQuoteMessage = {},
            onListenMessage = {},
            onZoomMessage = {},
            onTranscribeMessage = { transcribeTarget = it },
            onScrmOperationRequested = {},
            onCloseLongPressMenu = { menuClosed = true }
        )

        actions.performLongPressAction(message, MessageLongPressAction.Transcribe)

        assertEquals(message, transcribeTarget)
        assertTrue(menuClosed)
    }

    /**
     * 测试流程：在“全部未回消息”中点击其他微信账号的语音消息，再选择“转文字”。
     * 转写必须使用消息所属账号，不能误用当前工具栏选中的账号。
     */
    @Test
    fun voiceTranscriptionUsesMessageScopedAccountBeforeSelectedAccount() {
        val message = FloatingChatMessage(
            id = "voice-other-account",
            type = FloatingChatMessageType.Voice,
            text = "[语音]",
            fromMe = false,
            senderName = "张三",
            time = "10:00",
            threadContactId = "account-from-message__scrm-contact:peer",
            remoteMessageId = 72L
        )

        assertEquals(
            "account-from-message",
            voiceTranscriptionAccountId(message, fallbackAccountId = "account-selected")
        )
        assertEquals(
            "account-selected",
            voiceTranscriptionAccountId(
                message.copy(threadContactId = "legacy-unscoped-thread"),
                fallbackAccountId = "account-selected"
            )
        )
    }

    @Test
    fun asideAnalysisRequiresEmotionStanceAndSubtext() {
        assertEquals(
            MessageAsideAnalysis(
                emotion = "克制但不耐烦",
                stance = "暂不承诺，等待对方给出条件",
                subtext = "对方愿意继续谈，但不接受当前方案"
            ),
            parseMessageAsideAnalysis(
                """
                情绪：克制但不耐烦
                立场：暂不承诺，等待对方给出条件
                话外音：对方愿意继续谈，但不接受当前方案
                """.trimIndent()
            )
        )

        assertThrows(IllegalStateException::class.java) {
            parseMessageAsideAnalysis("情绪：平静\n话外音：还在考虑")
        }
    }

    @Test
    fun zoomRoutesTextMediaAndUnsupportedMessageTypes() {
        assertEquals(MessageZoomMode.Text, messageZoomMode(com.paifa.ubikitouch.core.model.FloatingChatMessageType.Text))
        assertEquals(MessageZoomMode.Text, messageZoomMode(com.paifa.ubikitouch.core.model.FloatingChatMessageType.MixedText))
        assertEquals(MessageZoomMode.Media, messageZoomMode(com.paifa.ubikitouch.core.model.FloatingChatMessageType.ImageThumbnail))
        assertEquals(MessageZoomMode.Media, messageZoomMode(com.paifa.ubikitouch.core.model.FloatingChatMessageType.CapturedPhoto))
        assertEquals(MessageZoomMode.Unsupported, messageZoomMode(com.paifa.ubikitouch.core.model.FloatingChatMessageType.Voice))
    }

    @Test
    fun dismissInvalidatesAnInFlightAsideAnalysisRequest() {
        val tracker = MessageAsideRequestTracker()
        val request = tracker.begin()

        assertTrue(tracker.isActive(request))

        tracker.cancel()

        assertEquals(false, tracker.isActive(request))
    }

    @Test
    fun multiForwardModesExposeSeparateAndCombinedForwarding() {
        assertEquals(2, multiForwardModeLabels().size)
        assertTrue(multiForwardModeLabels().contains(MultiForwardMode.Combined.label))
    }
}
