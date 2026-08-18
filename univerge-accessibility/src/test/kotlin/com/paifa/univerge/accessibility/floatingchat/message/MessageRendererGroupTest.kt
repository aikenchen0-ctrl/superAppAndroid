package com.paifa.univerge.accessibility.floatingchat.message

import com.paifa.univerge.core.model.FloatingChatMessageType
import org.junit.Assert.assertEquals
import org.junit.Test

class MessageRendererGroupTest {
    @Test
    fun mediaAndResourceTypesFollowTheIosRendererGroups() {
        assertEquals(MessageRendererGroup.Media, messageRendererGroupFor(FloatingChatMessageType.ImageThumbnail))
        assertEquals(MessageRendererGroup.Media, messageRendererGroupFor(FloatingChatMessageType.CapturedPhoto))
        assertEquals(MessageRendererGroup.Media, messageRendererGroupFor(FloatingChatMessageType.StickerGif))
        assertEquals(MessageRendererGroup.Voice, messageRendererGroupFor(FloatingChatMessageType.Voice))
        assertEquals(MessageRendererGroup.Document, messageRendererGroupFor(FloatingChatMessageType.FilePreview))
        assertEquals(MessageRendererGroup.Location, messageRendererGroupFor(FloatingChatMessageType.Location))
        assertEquals(MessageRendererGroup.Location, messageRendererGroupFor(FloatingChatMessageType.LiveLocation))
        assertEquals(MessageRendererGroup.Profile, messageRendererGroupFor(FloatingChatMessageType.ContactLink))
        assertEquals(MessageRendererGroup.Profile, messageRendererGroupFor(FloatingChatMessageType.GroupInvite))
    }

    @Test
    fun linkTypesKeepTheirOwnSourceAndPreviewSemantics() {
        assertEquals(LinkMessageCardKind.Web, linkMessageCardKindFor(FloatingChatMessageType.WebLink))
        assertEquals(LinkMessageCardKind.Article, linkMessageCardKindFor(FloatingChatMessageType.Article))
        assertEquals(LinkMessageCardKind.MiniProgram, linkMessageCardKindFor(FloatingChatMessageType.MiniProgramLink))
        assertEquals(LinkMessageCardKind.ChannelLive, linkMessageCardKindFor(FloatingChatMessageType.ChannelsLive))
        assertEquals(LinkMessageCardKind.Music, linkMessageCardKindFor(FloatingChatMessageType.Music))
        assertEquals(LinkMessageCardKind.Favorite, linkMessageCardKindFor(FloatingChatMessageType.Favorite))
    }

    @Test
    fun specialCardsKeepTheirOwnIosRendererGroups() {
        assertEquals(MessageRendererGroup.Money, messageRendererGroupFor(FloatingChatMessageType.RedPacket))
        assertEquals(MessageRendererGroup.Money, messageRendererGroupFor(FloatingChatMessageType.Transfer))
        assertEquals(MessageRendererGroup.Money, messageRendererGroupFor(FloatingChatMessageType.SplitBill))
        assertEquals(MessageRendererGroup.Money, messageRendererGroupFor(FloatingChatMessageType.Coupon))
        assertEquals(MessageRendererGroup.Call, messageRendererGroupFor(FloatingChatMessageType.VoiceCall))
        assertEquals(MessageRendererGroup.Call, messageRendererGroupFor(FloatingChatMessageType.VideoCall))
        assertEquals(MessageRendererGroup.Stacked, messageRendererGroupFor(FloatingChatMessageType.Relay))
        assertEquals(MessageRendererGroup.Notice, messageRendererGroupFor(FloatingChatMessageType.GroupNotice))
    }
}
