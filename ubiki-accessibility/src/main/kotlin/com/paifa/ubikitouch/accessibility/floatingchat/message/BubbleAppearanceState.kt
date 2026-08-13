package com.paifa.ubikitouch.accessibility.floatingchat.message

import androidx.compose.ui.graphics.Color
import com.paifa.ubikitouch.core.model.FloatingChatMessage
import com.paifa.ubikitouch.core.model.FloatingChatMessageKind
import com.paifa.ubikitouch.core.model.FloatingChatMessagePresentation

internal enum class BubbleAppearance {
    TwoD,
    ThreeD
}

internal fun BubbleAppearance.toggle(): BubbleAppearance = when (this) {
    BubbleAppearance.TwoD -> BubbleAppearance.ThreeD
    BubbleAppearance.ThreeD -> BubbleAppearance.TwoD
}

internal fun bubbleAppearanceButtonLabel(appearance: BubbleAppearance): String = when (appearance) {
    BubbleAppearance.TwoD -> "2D气泡"
    BubbleAppearance.ThreeD -> "3D气泡"
}

internal fun messageUsesThreeDimensionalBubble(
    message: FloatingChatMessage,
    appearance: BubbleAppearance
): Boolean {
    return appearance == BubbleAppearance.ThreeD &&
        message.presentation == FloatingChatMessagePresentation.Bubble &&
        messageTypeUsesImModuleBubble(message.type) &&
        !message.isPaymentCardMessage()
}

internal fun messageThreeDimensionalAccent(message: FloatingChatMessage): Color = when {
    message.kind == FloatingChatMessageKind.AiDraft -> Color(0xFFB87514)
    message.fromMe -> Color(0xFF0D6B4F)
    else -> Color(0xFF12485E)
}
