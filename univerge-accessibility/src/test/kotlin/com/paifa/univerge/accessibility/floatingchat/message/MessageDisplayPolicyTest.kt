package com.paifa.univerge.accessibility.floatingchat.message

import com.paifa.univerge.core.model.FloatingChatMessage
import com.paifa.univerge.core.model.FloatingChatMessageType
import com.paifa.univerge.accessibility.floatingchat.media.fileDisplayName
import org.junit.Assert.assertEquals
import org.junit.Test

class MessageDisplayPolicyTest {
    @Test
    fun linkUrlIsNotRenderedAsAnImageWhenNoThumbnailWasProvided() {
        val message = FloatingChatMessage(
            id = "link-1",
            type = FloatingChatMessageType.WebLink,
            text = "",
            fromMe = false,
            senderName = "张三",
            time = "10:00",
            resourceUrl = "https://example.test/article"
        )

        assertEquals(false, linkMessageCardUsesThumbnail(message))
        assertEquals("网页链接", linkMessageTitle(message))
    }

    @Test
    fun structuredCardsAlwaysHaveAVisibleTitleWhenOptionalTextIsMissing() {
        val file = FloatingChatMessage(
            id = "file-1",
            type = FloatingChatMessageType.FilePreview,
            text = "",
            fromMe = false,
            senderName = "张三",
            time = "10:00",
            resourceUrl = "content://files/1"
        )
        val contact = FloatingChatMessage(
            id = "contact-1",
            type = FloatingChatMessageType.ContactLink,
            text = "",
            fromMe = false,
            senderName = "张三",
            time = "10:01",
            detail = "联系人"
        )

        assertEquals("文件", fileDisplayName(file))
        assertEquals("名片", contactCardDisplayName(contact))
    }

    @Test
    fun structuredLocationContentIsNotReplacedByUnavailablePlaceholder() {
        val message = FloatingChatMessage(
            id = "location-1",
            type = FloatingChatMessageType.Location,
            text = "",
            fromMe = false,
            senderName = "张三",
            time = "10:00",
            locationTitle = "上海市人民广场",
            locationAddress = "黄浦区人民大道"
        )

        assertEquals(null, messageUnavailableStateFor(message))
    }

    @Test
    fun mediaWithOnlyThumbnailUrlIsAvailableForEveryRenderedMediaType() {
        val mediaTypes = listOf(
            FloatingChatMessageType.ImageThumbnail,
            FloatingChatMessageType.CapturedPhoto,
            FloatingChatMessageType.StickerGif,
            FloatingChatMessageType.VideoPreview,
            FloatingChatMessageType.ChannelsVideo
        )

        mediaTypes.forEach { type ->
            val message = FloatingChatMessage(
                id = "media-${type.name}",
                type = type,
                text = "",
                fromMe = false,
                senderName = "张三",
                time = "10:00",
                thumbnailUrl = "https://example.test/${type.name}.jpg"
            )

            assertEquals(type.name, null, messageUnavailableStateFor(message))
        }
    }

    @Test
    fun quoteAndChatHistoryPreviewFieldsCountAsDisplayableContent() {
        val quote = FloatingChatMessage(
            id = "quote-1",
            type = FloatingChatMessageType.Quote,
            text = "",
            fromMe = false,
            senderName = "张三",
            time = "10:00",
            quoteAuthor = "李四",
            quoteText = "原始消息"
        )
        val history = FloatingChatMessage(
            id = "history-1",
            type = FloatingChatMessageType.ChatHistory,
            text = "",
            fromMe = false,
            senderName = "张三",
            time = "10:01",
            filePreviewLines = listOf("张三：你好", "李四：收到")
        )

        assertEquals(null, messageUnavailableStateFor(quote))
        assertEquals(null, messageUnavailableStateFor(history))
    }
}
