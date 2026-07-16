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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import com.paifa.ubikitouch.accessibility.AccountCardPreviewContent
import com.paifa.ubikitouch.accessibility.LocationGlyph
import com.paifa.ubikitouch.accessibility.OverlayTokens
import com.paifa.ubikitouch.accessibility.PaymentCardContent
import com.paifa.ubikitouch.accessibility.SquareAvatarChip
import com.paifa.ubikitouch.accessibility.TextLabel
import com.paifa.ubikitouch.accessibility.isPaymentCardMessage
import com.paifa.ubikitouch.accessibility.locationMapPreviewHeightDp
import com.paifa.ubikitouch.core.model.FloatingChatContactCardKind
import com.paifa.ubikitouch.core.model.FloatingChatMessage

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
            text = message.locationTitle ?: message.text,
            size = 11.sp,
            weight = FontWeight.Normal,
            color = OverlayTokens.locationMapText,
            maxLines = 1
        )
        TextLabel(
            text = message.locationAddress.orEmpty(),
            size = 8.sp,
            weight = FontWeight.Normal,
            color = OverlayTokens.locationMapSubtext,
            maxLines = 1
        )
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
    val name = message.cardName ?: message.text
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
            .height(28.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SquareAvatarChip(
            text = message.cardName?.take(2).orEmpty().ifBlank { "名片" },
            background = OverlayTokens.inlineAvatar,
            sizeDp = 26
        )
        Spacer(modifier = Modifier.width(7.dp))
        TextLabel(
            text = message.text,
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
            .height(28.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LocationGlyph(modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(7.dp))
        Column(modifier = Modifier.weight(1f)) {
            TextLabel(
                text = message.locationTitle ?: message.text,
                size = 10.5.sp,
                weight = FontWeight.Bold,
                color = OverlayTokens.cardPrimaryText,
                maxLines = 1,
                shadow = OverlayTokens.imModuleTextShadow
            )
            TextLabel(
                text = message.locationAddress.orEmpty(),
                size = 8.5.sp,
                color = OverlayTokens.cardSecondaryText,
                maxLines = 1,
                shadow = OverlayTokens.imModuleTextShadow
            )
        }
    }
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
                text = message.text,
                size = 11.sp,
                weight = FontWeight.Bold,
                color = OverlayTokens.cardPrimaryText,
                maxLines = 2,
                lineHeight = 14.sp,
                shadow = OverlayTokens.imModuleTextShadow
            )
            TextLabel(
                text = message.appName.orEmpty(),
                size = 9.sp,
                color = OverlayTokens.cardSecondaryText,
                maxLines = 1,
                shadow = OverlayTokens.imModuleTextShadow
            )
            ResourceUrlLine(message.resourceUrl)
        }
    }
}

@Composable
private fun ResourceUrlLine(url: String?) {
    if (url.isNullOrBlank()) return
    TextLabel(
        text = url,
        size = 8.sp,
        color = OverlayTokens.linkText,
        maxLines = 1,
        shadow = OverlayTokens.imModuleTextShadow
    )
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
