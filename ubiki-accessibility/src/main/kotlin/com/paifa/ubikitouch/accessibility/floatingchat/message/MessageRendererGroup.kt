package com.paifa.ubikitouch.accessibility.floatingchat.message

import com.paifa.ubikitouch.core.model.FloatingChatMessageType

/** Mirrors the renderer families in iOS ChatCells so each type has a stable visual contract. */
internal enum class MessageRendererGroup {
    Text,
    OversizedText,
    Media,
    ChannelVideo,
    Voice,
    Document,
    Location,
    Profile,
    Link,
    Stacked,
    Money,
    Call,
    Quote,
    Notice
}

internal enum class LinkMessageCardKind(
    val sourceLabel: String,
    val glyph: String
) {
    Web("网页链接", "↗"),
    Article("公众号文章", "文"),
    MiniProgram("小程序", "小"),
    ChannelLive("视频号直播", "播"),
    Music("音乐分享", "♫"),
    Favorite("收藏分享", "★")
}

internal fun linkMessageCardKindFor(type: FloatingChatMessageType): LinkMessageCardKind {
    return when (type) {
        FloatingChatMessageType.WebLink -> LinkMessageCardKind.Web
        FloatingChatMessageType.Article -> LinkMessageCardKind.Article
        FloatingChatMessageType.MiniProgramLink -> LinkMessageCardKind.MiniProgram
        FloatingChatMessageType.ChannelsLive -> LinkMessageCardKind.ChannelLive
        FloatingChatMessageType.Music -> LinkMessageCardKind.Music
        FloatingChatMessageType.Favorite -> LinkMessageCardKind.Favorite
        else -> error("$type is not a link message type")
    }
}

internal fun messageRendererGroupFor(type: FloatingChatMessageType): MessageRendererGroup {
    return when (type) {
        FloatingChatMessageType.Text,
        FloatingChatMessageType.MixedText -> MessageRendererGroup.Text
        FloatingChatMessageType.Emoji -> MessageRendererGroup.OversizedText
        FloatingChatMessageType.ImageThumbnail,
        FloatingChatMessageType.CapturedPhoto,
        FloatingChatMessageType.StickerGif,
        FloatingChatMessageType.VideoPreview -> MessageRendererGroup.Media
        FloatingChatMessageType.ChannelsVideo -> MessageRendererGroup.ChannelVideo
        FloatingChatMessageType.Voice -> MessageRendererGroup.Voice
        FloatingChatMessageType.FilePreview -> MessageRendererGroup.Document
        FloatingChatMessageType.Location,
        FloatingChatMessageType.LiveLocation,
        FloatingChatMessageType.InlineLocation -> MessageRendererGroup.Location
        FloatingChatMessageType.ContactLink,
        FloatingChatMessageType.GroupInvite,
        FloatingChatMessageType.EnterpriseInvite,
        FloatingChatMessageType.InlineContact -> MessageRendererGroup.Profile
        FloatingChatMessageType.MiniProgramLink,
        FloatingChatMessageType.WebLink,
        FloatingChatMessageType.Article,
        FloatingChatMessageType.ChannelsLive,
        FloatingChatMessageType.Music,
        FloatingChatMessageType.Favorite -> MessageRendererGroup.Link
        FloatingChatMessageType.ChatHistory,
        FloatingChatMessageType.Relay -> MessageRendererGroup.Stacked
        FloatingChatMessageType.RedPacket,
        FloatingChatMessageType.Transfer,
        FloatingChatMessageType.SplitBill,
        FloatingChatMessageType.Coupon -> MessageRendererGroup.Money
        FloatingChatMessageType.VoiceCall,
        FloatingChatMessageType.VideoCall -> MessageRendererGroup.Call
        FloatingChatMessageType.Quote -> MessageRendererGroup.Quote
        FloatingChatMessageType.GroupNotice -> MessageRendererGroup.Notice
    }
}
