package com.paifa.ubikitouch.accessibility.floatingchat.message

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paifa.ubikitouch.accessibility.floatingchat.account.AccountCardPreviewContent
import com.paifa.ubikitouch.accessibility.floatingchat.components.FloatingChatLocationGlyph
import com.paifa.ubikitouch.accessibility.floatingchat.theme.OverlayTokens
import com.paifa.ubikitouch.accessibility.floatingchat.media.MediaThumbnailSurface
import com.paifa.ubikitouch.accessibility.floatingchat.components.SquareAvatarChip
import com.paifa.ubikitouch.accessibility.floatingchat.components.TextLabel
import com.paifa.ubikitouch.core.model.FloatingChatContactCardKind
import com.paifa.ubikitouch.core.model.FloatingChatMessage
import com.paifa.ubikitouch.core.model.FloatingChatMessageType

@Composable
internal fun LocationMessageContent(message: FloatingChatMessage) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(7.dp))
            .background(OverlayTokens.locationMapCard)
            .border(1.dp, OverlayTokens.resourcePanelBorder, RoundedCornerShape(7.dp))
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        TextLabel(
            text = locationMessageTitle(message),
            size = 11.sp,
            weight = FontWeight.Normal,
            color = OverlayTokens.locationMapText,
            maxLines = 1
        )
        message.locationAddress?.takeIf { it.isNotBlank() }?.let { address ->
            TextLabel(
                text = address,
                size = 10.sp,
                weight = FontWeight.Normal,
                color = OverlayTokens.locationMapSubtext,
                maxLines = 1
            )
        }
        LocationMapPreviewCanvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(locationMapPreviewHeightDp().dp)
                .clip(RoundedCornerShape(5.dp))
        )
    }
}

@Composable
internal fun ContactLinkCardContent(message: FloatingChatMessage) {
    val name = contactCardDisplayName(message)
    AccountCardPreviewContent(
        name = name,
        subtitle = message.cardSubtitle.orEmpty(),
        detail = message.detail.orEmpty().ifBlank { message.resourceUrl.orEmpty() },
        avatarText = name.take(2).ifBlank { "名片" },
        avatarColor = cardColorFor(message.cardKind),
        avatarImageUri = message.thumbnailUrl
    )
}

@Composable
internal fun InlineContactContent(message: FloatingChatMessage) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 42.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(OverlayTokens.contactCard)
            .border(1.dp, OverlayTokens.contactCardBorder, RoundedCornerShape(7.dp))
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SquareAvatarChip(
            text = message.cardName?.take(2).orEmpty().ifBlank { "名片" },
            background = OverlayTokens.inlineAvatar,
            sizeDp = 26
        )
        Spacer(modifier = Modifier.width(7.dp))
        TextLabel(
            text = inlineContactDisplayText(message),
            size = 11.sp,
            weight = FontWeight.Bold,
            color = OverlayTokens.cardPrimaryText,
            maxLines = 1,
            shadow = OverlayTokens.imModuleTextShadow
        )
    }
}

@Composable
internal fun InlineLocationContent(message: FloatingChatMessage) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 42.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(OverlayTokens.locationCard)
            .border(1.dp, OverlayTokens.locationCardBorder, RoundedCornerShape(7.dp))
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FloatingChatLocationGlyph(modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(7.dp))
        Column(modifier = Modifier.weight(1f)) {
            TextLabel(
                text = locationMessageTitle(message),
                size = 10.5.sp,
                weight = FontWeight.Bold,
                color = OverlayTokens.cardPrimaryText,
                maxLines = 1,
                shadow = OverlayTokens.imModuleTextShadow
            )
            message.locationAddress?.takeIf { it.isNotBlank() }?.let { address ->
                TextLabel(
                    text = address,
                    size = 8.5.sp,
                    color = OverlayTokens.cardSecondaryText,
                    maxLines = 1,
                    shadow = OverlayTokens.imModuleTextShadow
                )
            }
        }
    }
}

/** Read-only cards for iOS message kinds that do not have send support on Android yet. */
@Composable
internal fun IosAlignedMessageContent(message: FloatingChatMessage) {
    when (message.type) {
        FloatingChatMessageType.Emoji -> EmojiMessageCard(message)
        FloatingChatMessageType.StickerGif -> StickerMessageCard(message)
        FloatingChatMessageType.LiveLocation -> LocationMessageContent(message.copy(type = FloatingChatMessageType.Location))
        FloatingChatMessageType.GroupInvite -> ContactLinkCardContent(message.copy(type = FloatingChatMessageType.ContactLink))
        FloatingChatMessageType.Article -> OfficialArticleMessageCard(message)
        FloatingChatMessageType.WebLink,
        FloatingChatMessageType.ChannelsLive,
        FloatingChatMessageType.Music,
        FloatingChatMessageType.Favorite -> LinkMessageCard(message)
        FloatingChatMessageType.RedPacket,
        FloatingChatMessageType.Transfer,
        FloatingChatMessageType.SplitBill,
        FloatingChatMessageType.Coupon -> MoneyMessageCard(message)
        FloatingChatMessageType.VoiceCall,
        FloatingChatMessageType.VideoCall -> CallMessageCard(message)
        FloatingChatMessageType.Relay -> StackedMessageCard(message)
        FloatingChatMessageType.GroupNotice -> NoticeMessageCard(message)
        else -> LinkMessageCard(message)
    }
}

@Composable
internal fun EmojiMessageCard(message: FloatingChatMessage) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        TextLabel(
            text = message.text.ifBlank { "🙂" },
            size = 30.sp,
            weight = FontWeight.Normal,
            color = OverlayTokens.bubbleText,
            maxLines = 2,
            lineHeight = 34.sp
        )
        MessageMetaLine("Emoji 表情", message.detail)
    }
}

@Composable
internal fun StickerMessageCard(message: FloatingChatMessage) {
    Column(
        modifier = Modifier
            .widthIn(max = 236.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(OverlayTokens.mediaCard)
            .border(1.dp, OverlayTokens.resourcePanelBorder, RoundedCornerShape(9.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(108.dp)
                .background(OverlayTokens.imageBlock),
            contentAlignment = Alignment.Center
        ) {
            TextLabel(text = "GIF", size = 24.sp, weight = FontWeight.Bold, color = OverlayTokens.imageWatermark, maxLines = 1)
        }
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            TextLabel(text = "动态表情", size = 11.sp, weight = FontWeight.Bold, color = OverlayTokens.cardKindText, maxLines = 1)
            TextLabel(text = message.text.ifBlank { "动态表情" }, size = 10.sp, color = OverlayTokens.bubbleTextMuted, maxLines = 1)
        }
    }
}

@Composable
internal fun LinkMessageCard(message: FloatingChatMessage) {
    val kind = linkMessageCardKindFor(message.type)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .background(OverlayTokens.resourcePanel)
            .border(1.dp, OverlayTokens.resourcePanelBorder, RoundedCornerShape(9.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (linkMessageCardUsesThumbnail(message)) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(OverlayTokens.imageBlock)
            ) {
                MediaThumbnailSurface(message, Modifier.fillMaxWidth().height(54.dp), showChrome = false)
            }
        } else {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(OverlayTokens.miniProgramIcon),
                contentAlignment = Alignment.Center
            ) {
                TextLabel(text = kind.glyph, size = 16.sp, weight = FontWeight.Bold, color = Color.White, maxLines = 1)
            }
        }
        Spacer(modifier = Modifier.width(9.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            TextLabel(text = message.appName?.ifBlank { null } ?: kind.sourceLabel, size = 9.sp, color = OverlayTokens.cardSecondaryText, maxLines = 1)
            TextLabel(text = linkMessageTitle(message), size = 12.sp, weight = FontWeight.Bold, color = OverlayTokens.cardKindText, maxLines = 2, lineHeight = 15.sp)
            MessageMetaLine(kind.sourceLabel, message.detail?.takeIf { it != message.text })
            ResourceUrlLine(message.resourceUrl)
        }
    }
}

internal fun linkMessageCardUsesThumbnail(message: FloatingChatMessage): Boolean {
    return !message.thumbnailUrl.isNullOrBlank()
}

internal fun locationMessageTitle(message: FloatingChatMessage): String {
    return listOf(message.locationTitle, message.text, message.locationAddress)
        .filterNotNull()
        .map(String::trim)
        .firstOrNull(String::isNotBlank)
        ?: "位置消息"
}

internal fun inlineContactDisplayText(message: FloatingChatMessage): String {
    return listOf(message.text, message.cardName, message.cardSubtitle)
        .filterNotNull()
        .map(String::trim)
        .firstOrNull(String::isNotBlank)
        ?: "名片"
}

internal fun linkMessageTitle(message: FloatingChatMessage): String {
    val kind = linkMessageCardKindFor(message.type)
    return message.text.trim().ifBlank {
        message.detail?.trim().orEmpty().ifBlank { kind.sourceLabel }
    }
}

internal fun contactCardDisplayName(message: FloatingChatMessage): String {
    return listOf(message.cardName, message.text, message.cardSubtitle)
        .filterNotNull()
        .map(String::trim)
        .firstOrNull(String::isNotBlank)
        ?: "名片"
}

@Composable
internal fun MoneyMessageCard(message: FloatingChatMessage) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .background(OverlayTokens.paymentCard)
            .padding(top = 11.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(OverlayTokens.paymentCardBorder), contentAlignment = Alignment.Center) {
                TextLabel(text = if (message.type == FloatingChatMessageType.Coupon) "券" else "¥", size = 18.sp, weight = FontWeight.Bold, color = OverlayTokens.paymentCardText, maxLines = 1)
            }
            Spacer(modifier = Modifier.width(9.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                TextLabel(text = message.text.ifBlank { iosAlignedTitle(message.type) }, size = 13.sp, weight = FontWeight.Bold, color = OverlayTokens.paymentCardText, maxLines = 2)
                TextLabel(text = message.detail.orEmpty().ifBlank { iosAlignedTitle(message.type) }, size = 10.sp, color = OverlayTokens.paymentCardFooterText, maxLines = 1)
            }
        }
        Spacer(modifier = Modifier.fillMaxWidth().height(1.dp).background(OverlayTokens.paymentCardBorder))
        TextLabel(
            text = paymentFooterLabel(message.type),
            size = 9.sp,
            color = OverlayTokens.paymentCardFooterText,
            maxLines = 1,
            modifier = Modifier.padding(start = 11.dp, end = 11.dp, bottom = 10.dp)
        )
    }
}

@Composable
internal fun CallMessageCard(message: FloatingChatMessage) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .background(OverlayTokens.voiceCard)
            .border(1.dp, OverlayTokens.voiceCardBorder, RoundedCornerShape(9.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(32.dp).clip(RoundedCornerShape(9.dp)).background(OverlayTokens.linkText), contentAlignment = Alignment.Center) {
                TextLabel(text = if (message.type == FloatingChatMessageType.VideoCall) "▣" else "☎", size = 16.sp, color = Color.White, maxLines = 1)
            }
            Spacer(modifier = Modifier.width(9.dp))
            TextLabel(text = iosAlignedTitle(message.type), size = 11.sp, weight = FontWeight.Bold, color = OverlayTokens.cardKindText, maxLines = 1)
        }
        TextLabel(text = message.text.ifBlank { "通话记录" }, size = 11.sp, color = OverlayTokens.bubbleText, maxLines = 2)
        TextLabel(text = message.detail.orEmpty().ifBlank { "已结束" }, size = 9.sp, color = OverlayTokens.bubbleTextMuted, maxLines = 1)
    }
}

@Composable
internal fun StackedMessageCard(message: FloatingChatMessage) {
    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp)).background(OverlayTokens.specialCard).border(1.dp, OverlayTokens.specialCardBorder, RoundedCornerShape(9.dp)).padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        TextLabel(text = "接龙", size = 9.sp, color = OverlayTokens.cardSecondaryText, maxLines = 1)
        TextLabel(text = message.text.ifBlank { "接龙消息" }, size = 12.sp, weight = FontWeight.Bold, color = OverlayTokens.cardKindText, maxLines = 2)
        message.filePreviewLines.take(3).forEachIndexed { index, line -> TextLabel(text = "${index + 1}. $line", size = 10.sp, color = OverlayTokens.bubbleTextMuted, maxLines = 1) }
        TextLabel(text = "${message.filePreviewLines.size.coerceAtLeast(1)} 条接龙内容", size = 9.sp, color = OverlayTokens.cardSecondaryText, maxLines = 1)
    }
}

@Composable
internal fun NoticeMessageCard(message: FloatingChatMessage) {
    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp)).background(OverlayTokens.locationCard).border(1.dp, OverlayTokens.locationCardBorder, RoundedCornerShape(9.dp)).padding(horizontal = 11.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        TextLabel(text = message.detail?.takeIf { it.isNotBlank() } ?: "群公告", size = 9.sp, color = OverlayTokens.locationMapSubtext, maxLines = 1)
        TextLabel(text = message.text.ifBlank { "暂无公告内容" }, size = 12.sp, weight = FontWeight.Bold, color = OverlayTokens.locationMapText, maxLines = 3, lineHeight = 16.sp)
        TextLabel(text = message.senderName.ifBlank { "群管理员" }, size = 9.sp, color = OverlayTokens.locationMapSubtext, maxLines = 1)
    }
}

@Composable
private fun MessageMetaLine(label: String?, detail: String?) {
    val value = listOfNotNull(label, detail?.takeIf { it.isNotBlank() }).joinToString(" · ")
    if (value.isNotBlank()) TextLabel(text = value, size = 9.sp, color = OverlayTokens.bubbleTextMuted, maxLines = 1)
}

private fun iosAlignedTitle(type: FloatingChatMessageType): String = when (type) {
    FloatingChatMessageType.WebLink -> "网页链接"
    FloatingChatMessageType.Article -> "公众号文章"
    FloatingChatMessageType.ChannelsLive -> "视频号直播"
    FloatingChatMessageType.Music -> "音乐分享"
    FloatingChatMessageType.Favorite -> "收藏分享"
    FloatingChatMessageType.RedPacket -> "红包"
    FloatingChatMessageType.Transfer -> "转账"
    FloatingChatMessageType.SplitBill -> "AA 收款"
    FloatingChatMessageType.Coupon -> "优惠券"
    FloatingChatMessageType.VoiceCall -> "语音通话"
    FloatingChatMessageType.VideoCall -> "视频通话"
    else -> type.label
}

private fun paymentFooterLabel(type: FloatingChatMessageType): String = when (type) {
    FloatingChatMessageType.RedPacket -> "微信红包"
    FloatingChatMessageType.Transfer -> "转账"
    FloatingChatMessageType.SplitBill -> "AA 收款"
    FloatingChatMessageType.Coupon -> "微信卡券"
    else -> type.label
}

@Composable
internal fun MiniProgramLinkContent(
    message: FloatingChatMessage,
    claimed: Boolean = false
) {
    if (message.isPaymentCardMessage()) {
        PaymentCardContent(message, claimed)
        return
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(7.dp))
            .background(OverlayTokens.resourcePanel)
            .border(1.dp, OverlayTokens.resourcePanelBorder, RoundedCornerShape(7.dp))
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(OverlayTokens.miniProgramIcon),
            contentAlignment = Alignment.Center
        ) {
            TextLabel(
                text = "小",
                size = 15.sp,
                weight = FontWeight.Bold,
                color = OverlayTokens.primaryText,
                maxLines = 1,
                textAlign = TextAlign.Center,
                shadow = OverlayTokens.imModuleTextShadow
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            TextLabel(
                text = linkMessageTitle(message),
                size = 11.sp,
                weight = FontWeight.Bold,
                color = OverlayTokens.cardPrimaryText,
                maxLines = 2,
                lineHeight = 14.sp,
                shadow = OverlayTokens.imModuleTextShadow
            )
            TextLabel(
                text = message.appName?.takeIf { it.isNotBlank() } ?: "小程序",
                size = 10.sp,
                color = OverlayTokens.cardSecondaryText,
                maxLines = 1,
                shadow = OverlayTokens.imModuleTextShadow
            )
            ResourceUrlLine(message.resourceUrl)
        }
    }
}

@Composable
private fun LocationMapPreviewCanvas(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.background(OverlayTokens.locationMapBase)) {
        drawRect(color = OverlayTokens.locationMapBase)
        drawRoundRect(
            color = OverlayTokens.locationMapPark,
            topLeft = Offset(size.width * 0.58f, size.height * 0.08f),
            size = Size(size.width * 0.34f, size.height * 0.24f),
            cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
        )
        drawRoundRect(
            color = OverlayTokens.locationMapPark,
            topLeft = Offset(size.width * 0.68f, size.height * 0.58f),
            size = Size(size.width * 0.24f, size.height * 0.30f),
            cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx())
        )
        drawRoundRect(
            color = OverlayTokens.locationMapWater,
            topLeft = Offset(size.width * 0.05f, size.height * 0.54f),
            size = Size(size.width * 0.22f, size.height * 0.24f),
            cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx())
        )
        val roadStroke = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val minorRoadStroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawLine(
            color = OverlayTokens.locationMapRoad,
            start = Offset(size.width * 0.02f, size.height * 0.24f),
            end = Offset(size.width * 0.98f, size.height * 0.76f),
            strokeWidth = roadStroke.width,
            cap = StrokeCap.Round
        )
        drawLine(
            color = OverlayTokens.locationMapRoad,
            start = Offset(size.width * 0.10f, size.height * 0.86f),
            end = Offset(size.width * 0.92f, size.height * 0.16f),
            strokeWidth = roadStroke.width,
            cap = StrokeCap.Round
        )
        listOf(0.22f, 0.42f, 0.62f, 0.82f).forEach { x ->
            drawLine(
                color = OverlayTokens.locationMapMinorRoad,
                start = Offset(size.width * x, 0f),
                end = Offset(size.width * (x - 0.18f), size.height),
                strokeWidth = minorRoadStroke.width,
                cap = StrokeCap.Round
            )
        }
        listOf(0.18f, 0.42f, 0.66f).forEach { y ->
            drawLine(
                color = OverlayTokens.locationMapMinorRoad,
                start = Offset(0f, size.height * y),
                end = Offset(size.width, size.height * (y + 0.12f)),
                strokeWidth = minorRoadStroke.width,
                cap = StrokeCap.Round
            )
        }
        val pinCenter = Offset(size.width * 0.50f, size.height * 0.43f)
        drawCircle(
            color = Color.White,
            radius = size.minDimension * 0.15f,
            center = pinCenter
        )
        drawCircle(
            color = OverlayTokens.locationMapPin,
            radius = size.minDimension * 0.12f,
            center = pinCenter
        )
        drawLine(
            color = OverlayTokens.locationMapPin,
            start = Offset(pinCenter.x, pinCenter.y + size.minDimension * 0.12f),
            end = Offset(pinCenter.x, pinCenter.y + size.minDimension * 0.29f),
            strokeWidth = 3.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

private fun cardColorFor(kind: FloatingChatContactCardKind?): Color {
    return when (kind) {
        FloatingChatContactCardKind.WeCom -> Color(0xFF357C68)
        FloatingChatContactCardKind.Personal -> Color(0xFF5674A8)
        FloatingChatContactCardKind.OfficialAccount -> Color(0xFF90643D)
        FloatingChatContactCardKind.MiniProgram -> Color(0xFF4B7F9A)
        FloatingChatContactCardKind.Channel -> Color(0xFF9A536B)
        null -> OverlayTokens.inlineAvatar
    }
}
