package com.paifa.ubikitouch.accessibility.floatingchat.message

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.unit.dp
import com.paifa.ubikitouch.accessibility.floatingchat.theme.OverlayTokens
import com.paifa.ubikitouch.core.model.FloatingChatAccessState
import com.paifa.ubikitouch.core.model.FloatingChatMessage
import com.paifa.ubikitouch.core.model.FloatingChatMessageKind
import com.paifa.ubikitouch.core.model.FloatingChatMessagePresentation
import com.paifa.ubikitouch.core.model.FloatingChatMessageType

internal fun locationMessageUsesMapPreview(): Boolean = true

internal fun locationMapPreviewHeightDp(): Int = 86

internal fun locationMapBubbleTextUsesShadow(): Boolean = false

internal fun locationMapPinColorArgb(): Int = OverlayTokens.locationMapPin.toArgb()

internal fun messageBubbleColor(message: FloatingChatMessage, claimed: Boolean = false): Color {
    if (message.isPaymentCardMessage()) {
        return if (claimed) OverlayTokens.paymentCardClaimed else OverlayTokens.paymentCard
    }
    if (!messageTypeUsesImModuleBubble(message.type)) {
        return cardMessageColor(message)
    }
    return when {
        message.presentation == FloatingChatMessagePresentation.System -> OverlayTokens.systemBubble
        message.fromMe -> OverlayTokens.selfBubble
        else -> OverlayTokens.otherBubble
    }
}

internal fun messageBubbleBorderColor(message: FloatingChatMessage, claimed: Boolean = false): Color {
    if (aiDraftMessageUsesGreenDashedBubble(message)) {
        return OverlayTokens.aiDashedBorder
    }
    if (message.isPaymentCardMessage()) {
        return if (claimed) OverlayTokens.paymentCardClaimedBorder else OverlayTokens.paymentCardBorder
    }
    if (!messageTypeUsesImModuleBubble(message.type)) {
        return when {
            message.kind == FloatingChatMessageKind.AiDraft -> OverlayTokens.aiBorder
            message.type == FloatingChatMessageType.Location ||
                message.type == FloatingChatMessageType.InlineLocation -> OverlayTokens.locationCardBorder
            message.type == FloatingChatMessageType.ContactLink ||
                message.type == FloatingChatMessageType.InlineContact -> OverlayTokens.contactCardBorder
            message.type == FloatingChatMessageType.FilePreview -> OverlayTokens.fileCardBorder
            message.type == FloatingChatMessageType.Voice -> OverlayTokens.voiceCardBorder
            else -> OverlayTokens.legacyBubbleBorder
        }
    }
    return when {
        message.presentation == FloatingChatMessagePresentation.System -> OverlayTokens.bubbleBorder
        message.fromMe -> OverlayTokens.selfBubbleBorder
        else -> OverlayTokens.otherBubbleBorder
    }
}

private fun cardMessageColor(message: FloatingChatMessage): Color {
    if (message.isPaymentCardMessage()) {
        return OverlayTokens.paymentCard
    }
    return cardMessageColorFor(message.type)
}

private fun cardMessageColorFor(type: FloatingChatMessageType): Color {
    return when (type) {
        FloatingChatMessageType.Location,
        FloatingChatMessageType.InlineLocation -> OverlayTokens.locationCard
        FloatingChatMessageType.ContactLink,
        FloatingChatMessageType.InlineContact -> OverlayTokens.contactCard
        FloatingChatMessageType.MiniProgramLink -> OverlayTokens.miniProgramCard
        FloatingChatMessageType.FilePreview -> OverlayTokens.fileCard
        FloatingChatMessageType.Voice -> OverlayTokens.voiceCard
        FloatingChatMessageType.ImageThumbnail,
        FloatingChatMessageType.VideoPreview -> OverlayTokens.mediaCard
        FloatingChatMessageType.Text,
        FloatingChatMessageType.MixedText,
        FloatingChatMessageType.Quote,
        FloatingChatMessageType.ChatHistory -> OverlayTokens.specialCard
    }
}

internal fun Modifier.aiDraftDashedBorder(shape: RoundedCornerShape): Modifier {
    return drawWithContent {
        drawContent()
        val strokePx = 1.4.dp.toPx()
        val inset = strokePx / 2f
        val pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
        drawRoundRect(
            color = OverlayTokens.aiDashedBorder,
            topLeft = Offset(inset, inset),
            size = size.copy(
                width = (size.width - strokePx).coerceAtLeast(0f),
                height = (size.height - strokePx).coerceAtLeast(0f)
            ),
            cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx()),
            style = Stroke(
                width = strokePx,
                pathEffect = pathEffect,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
}

internal fun accessStateColor(state: FloatingChatAccessState): Color {
    return when (state) {
        FloatingChatAccessState.Visible -> OverlayTokens.aiText
        FloatingChatAccessState.NeedsApply -> OverlayTokens.linkText
        FloatingChatAccessState.Applied -> OverlayTokens.bubbleTextMuted
        FloatingChatAccessState.Approved -> OverlayTokens.aiText
    }
}

internal fun imModuleSelfBubbleColorArgb(): Int = OverlayTokens.selfBubble.toArgb()

internal fun imModuleOtherBubbleColorArgb(): Int = OverlayTokens.otherBubble.toArgb()

internal fun imModuleSelfBubbleBorderColorArgb(): Int = OverlayTokens.selfBubbleBorder.toArgb()

internal fun imModuleOtherBubbleBorderColorArgb(): Int = OverlayTokens.otherBubbleBorder.toArgb()

internal fun imModuleBubbleTextColorArgb(): Int = OverlayTokens.bubbleText.toArgb()

internal fun imModuleBubbleShadowColorArgb(): Int = OverlayTokens.imModuleTextShadow.color.toArgb()

internal fun imModuleBubbleUsesDemoGlassEffect(): Boolean = true

internal fun imModuleSelfBubbleBackdropBlurDp(): Int = 20

internal fun imModuleSelfBubbleShadowOffsetYDp(): Int = 8

internal fun imModuleSelfBubbleShadowBlurDp(): Int = 32

internal fun imModuleOtherBubbleIsTransparentWithHalfBorder(): Boolean {
    return OverlayTokens.otherBubble == Color.Transparent &&
        OverlayTokens.otherBubbleBorder.alpha in 0.45f..0.55f
}

internal fun cardMessageTextUsesImModuleShadow(): Boolean = true

internal fun resourceUrlTextUsesImModuleShadow(): Boolean = true

internal fun chipTextUsesImModuleShadow(): Boolean = true

internal fun inlineCardTextUsesImModuleShadow(): Boolean = true

internal fun systemPromptMessageUsesTextOnly(): Boolean {
    return !messageUsesBubbleChrome(FloatingChatMessagePresentation.System)
}

internal fun systemPromptTextUsesShadow(): Boolean {
    return OverlayTokens.imModuleTextShadow.color.toArgb() == 0xE6000000.toInt()
}

internal fun cardMessageColorArgbFor(type: FloatingChatMessageType): Int {
    return cardMessageColorFor(type).toArgb()
}

internal fun paymentCardMessageColorArgb(): Int = OverlayTokens.paymentCard.toArgb()

internal fun paymentCardClaimedMessageColorArgb(): Int = OverlayTokens.paymentCardClaimed.toArgb()

internal fun paymentCardClaimedBorderColorArgb(): Int = OverlayTokens.paymentCardClaimedBorder.toArgb()

internal fun cardMessagePrimaryTextColorArgb(): Int = OverlayTokens.cardPrimaryText.toArgb()

internal fun cardMessageSecondaryTextColorArgb(): Int = OverlayTokens.cardSecondaryText.toArgb()

internal fun standaloneMessageTypeUsesCleanMediaSurface(type: FloatingChatMessageType): Boolean {
    return type == FloatingChatMessageType.ImageThumbnail || type == FloatingChatMessageType.VideoPreview
}

internal fun messageTypeUsesImModuleBubble(type: FloatingChatMessageType): Boolean {
    return when (type) {
        FloatingChatMessageType.Text,
        FloatingChatMessageType.MixedText,
        FloatingChatMessageType.Quote,
        FloatingChatMessageType.Location,
        FloatingChatMessageType.ContactLink,
        FloatingChatMessageType.MiniProgramLink,
        FloatingChatMessageType.FilePreview,
        FloatingChatMessageType.Voice,
        FloatingChatMessageType.InlineContact,
        FloatingChatMessageType.InlineLocation,
        FloatingChatMessageType.ChatHistory -> true
        FloatingChatMessageType.ImageThumbnail,
        FloatingChatMessageType.VideoPreview -> false
    }
}

internal fun aiDraftMessageUsesGreenDashedBubble(message: FloatingChatMessage): Boolean {
    return message.kind == FloatingChatMessageKind.AiDraft &&
        message.presentation == FloatingChatMessagePresentation.Bubble
}

internal fun aiDraftMessageUsesSolidBubbleBorder(message: FloatingChatMessage): Boolean {
    return !aiDraftMessageUsesGreenDashedBubble(message)
}

internal fun aiDraftBubbleDashedBorderColorArgb(): Int = OverlayTokens.aiDashedBorder.toArgb()

internal fun messageBlockUsesNegativePadding(): Boolean = false
