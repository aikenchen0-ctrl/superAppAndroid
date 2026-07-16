package com.paifa.ubikitouch.accessibility.floatingchat.message

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paifa.ubikitouch.accessibility.OverlayTokens
import com.paifa.ubikitouch.accessibility.TextLabel
import com.paifa.ubikitouch.core.model.FloatingChatInlineTokenType
import com.paifa.ubikitouch.core.model.FloatingChatMessage
import com.paifa.ubikitouch.core.model.FloatingChatMessagePresentation

@Composable
internal fun SimpleTextMessageContent(message: FloatingChatMessage, index: Int) {
    val isSystem = message.presentation == FloatingChatMessagePresentation.System
    TextLabel(
        text = message.text,
        size = if (isSystem) 9.sp else 11.sp,
        weight = if (isSystem) FontWeight.Normal else FontWeight.Bold,
        color = if (isSystem) OverlayTokens.systemPromptText else OverlayTokens.bubbleText,
        lineHeight = if (isSystem) 13.sp else 15.sp,
        maxLines = if (isSystem) 2 else if (index < 2) 3 else 4,
        shadow = OverlayTokens.imModuleTextShadow
    )
    message.detail?.let { detail ->
        Spacer(modifier = Modifier.height(2.dp))
        TextLabel(
            text = detail,
            size = 9.sp,
            weight = FontWeight.SemiBold,
            color = OverlayTokens.bubbleTextMuted,
            lineHeight = 12.sp,
            maxLines = 1,
            shadow = OverlayTokens.imModuleTextShadow
        )
    }
}

@Composable
internal fun MixedTextMessageContent(message: FloatingChatMessage) {
    val text = remember(message.inlineTokens, message.text) {
        if (message.inlineTokens.isEmpty()) {
            AnnotatedString(message.text)
        } else {
            buildAnnotatedString {
                message.inlineTokens.forEach { token ->
                    val color = when (token.type) {
                        FloatingChatInlineTokenType.Plain -> OverlayTokens.bubbleText
                        FloatingChatInlineTokenType.PaidianLink,
                        FloatingChatInlineTokenType.FileLink,
                        FloatingChatInlineTokenType.Url,
                        FloatingChatInlineTokenType.Mention,
                        FloatingChatInlineTokenType.ImageName -> OverlayTokens.linkText
                        FloatingChatInlineTokenType.Ai -> OverlayTokens.aiGold
                    }
                    val weight = when (token.type) {
                        FloatingChatInlineTokenType.Ai -> FontWeight.Black
                        FloatingChatInlineTokenType.Plain -> FontWeight.SemiBold
                        else -> FontWeight.Bold
                    }
                    withStyle(SpanStyle(color = color, fontWeight = weight)) {
                        append(token.text)
                    }
                }
            }
        }
    }
    AnnotatedTextLabel(
        text = text,
        size = 11.sp,
        lineHeight = 15.sp,
        maxLines = 5,
        shadow = OverlayTokens.imModuleTextShadow
    )
}

@Composable
internal fun QuoteMessageContent(message: FloatingChatMessage) {
    QuoteBlock(message)
    TextLabel(
        text = message.text,
        size = 11.sp,
        weight = FontWeight.Bold,
        color = OverlayTokens.bubbleText,
        lineHeight = 15.sp,
        maxLines = 4,
        shadow = OverlayTokens.imModuleTextShadow
    )
}

@Composable
internal fun ChatHistoryMessageContent(message: FloatingChatMessage) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(7.dp))
            .background(OverlayTokens.specialCard)
            .border(1.dp, OverlayTokens.resourcePanelBorder, RoundedCornerShape(7.dp))
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)
    ) {
        TextLabel(
            text = message.text.ifBlank { "聊天记录" },
            size = 12.sp,
            weight = FontWeight.Bold,
            color = OverlayTokens.bubbleText,
            maxLines = 1,
            shadow = OverlayTokens.imModuleTextShadow
        )
        message.filePreviewLines.take(3).forEach { line ->
            TextLabel(
                text = line,
                size = 10.sp,
                color = OverlayTokens.secondaryText,
                maxLines = 1,
                shadow = OverlayTokens.imModuleTextShadow
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Article,
                contentDescription = null,
                tint = OverlayTokens.panelSecondaryText,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            TextLabel(
                text = "聊天记录",
                size = 9.sp,
                color = OverlayTokens.panelSecondaryText,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun QuoteBlock(message: FloatingChatMessage) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(5.dp))
            .background(OverlayTokens.quoteBackground)
            .padding(horizontal = 7.dp, vertical = 5.dp)
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .width(2.dp)
                .height(26.dp)
                .background(OverlayTokens.quoteBar)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Column {
            TextLabel(
                text = message.quoteAuthor.orEmpty(),
                size = 9.sp,
                weight = FontWeight.SemiBold,
                color = OverlayTokens.bubbleText,
                maxLines = 1,
                shadow = OverlayTokens.imModuleTextShadow
            )
            TextLabel(
                text = message.quoteText.orEmpty(),
                size = 9.sp,
                color = OverlayTokens.bubbleTextMuted,
                maxLines = 1,
                shadow = OverlayTokens.imModuleTextShadow
            )
        }
    }
}

@Composable
private fun AnnotatedTextLabel(
    text: AnnotatedString,
    size: TextUnit,
    modifier: Modifier = Modifier,
    lineHeight: TextUnit = TextUnit.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    shadow: Shadow? = null
) {
    androidx.compose.foundation.text.BasicText(
        text = text,
        modifier = modifier,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        style = TextStyle.Default.copy(
            color = OverlayTokens.bubbleText,
            fontSize = size,
            lineHeight = lineHeight,
            fontWeight = FontWeight.SemiBold,
            shadow = shadow
        )
    )
}
