package com.paifa.ubikitouch.accessibility.floatingchat.input

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Surface as MaterialSurface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paifa.ubikitouch.accessibility.floatingchat.components.CompactInteractiveSize
import com.paifa.ubikitouch.accessibility.floatingchat.components.TextLabel
import com.paifa.ubikitouch.accessibility.floatingchat.message.longPressCopyText
import com.paifa.ubikitouch.accessibility.floatingchat.shell.BottomPanelMode
import com.paifa.ubikitouch.accessibility.floatingchat.theme.OverlayTokens
import com.paifa.ubikitouch.core.model.FloatingChatMessage

internal enum class BottomInputAction {
    Home,
    Emoji,
    Voice,
    Text,
    Gift,
    Assistant,
    Send
}

internal const val BottomInputBarMinHeightDp = 46
internal const val BottomInputBarMaxHeightDp = 128
internal const val BottomInputBarBottomPaddingDp = 10
internal const val BottomEmojiPanelHeightDp = 300

private const val BottomInputIconButtonSizeDp = 32
private const val BottomInputIconSizeDp = 18
private const val BottomInputFieldMinHeightDp = 36
private const val BottomInputFieldMaxHeightDp = 118
private const val BottomInputTextSizeSp = 11
private const val BottomInputPlaceholderTextSizeSp = 11
private const val BottomInputMinLines = 1
private const val BottomInputMaxLines = 4

internal fun bottomInputBarHeightDp(): Int = BottomInputBarMinHeightDp

internal fun bottomInputBarMinHeightDp(): Int = BottomInputBarMinHeightDp

internal fun bottomInputBarMaxHeightDp(): Int = BottomInputBarMaxHeightDp

internal fun bottomInputBarBottomPaddingDp(): Int = BottomInputBarBottomPaddingDp

internal fun bottomInputBarUsesKeyboardInsets(): Boolean = true

internal fun bottomInputBarVisibleForCenteredToolPanel(
    centeredToolFeaturePanelVisible: Boolean
): Boolean = !centeredToolFeaturePanelVisible

internal fun bottomInputControlsUseCenterAlignment(): Boolean = true

internal fun bottomInputUsesCustomBasicTextField(): Boolean = true

internal fun bottomInputTextSizeSp(): Int = BottomInputTextSizeSp

internal fun bottomInputPlaceholderTextSizeSp(): Int = BottomInputPlaceholderTextSizeSp

internal fun bottomInputIconButtonSizeDp(): Int = BottomInputIconButtonSizeDp

internal fun bottomInputIconSizeDp(): Int = BottomInputIconSizeDp

internal fun bottomInputMinLines(): Int = BottomInputMinLines

internal fun bottomInputMaxLines(): Int = BottomInputMaxLines

internal fun bottomInputActionOrder(): List<BottomInputAction> {
    return listOf(
        BottomInputAction.Home,
        BottomInputAction.Voice,
        BottomInputAction.Text,
        BottomInputAction.Gift,
        BottomInputAction.Assistant
    )
}

internal fun bottomInputLeadingAction(inputFocused: Boolean): BottomInputAction {
    return if (inputFocused) BottomInputAction.Emoji else BottomInputAction.Home
}

internal fun bottomHomeButtonShowsUnreadOverview(): Boolean = true

internal fun bottomHomeButtonShowsUnrepliedOverview(): Boolean = true

internal fun bottomHomeButtonSwapsToEmojiWhenInputFocused(): Boolean = true

internal fun bottomInputAssistantActionSendsPredictedMessage(): Boolean = true

internal fun bottomInputAssistantUsesAiDraftPrediction(): Boolean = true

internal fun assistantPredictionRequiresAiConfiguration(): Boolean = true

internal fun bottomEmojiPanelUsesAndroidXEmojiPicker(): Boolean = true

internal fun bottomEmojiPickerDependencyCoordinate(): String = "androidx.emoji2:emoji2-emojipicker:1.6.0"

internal fun bottomEmojiPanelKeepsPickerOpenAfterSelection(): Boolean = true

internal fun bottomEmojiPanelHeightDp(): Int = BottomEmojiPanelHeightDp

@Composable
internal fun BottomInputBar(
    inputText: String,
    onInputTextChange: (String) -> Unit,
    quotedMessage: FloatingChatMessage?,
    onClearQuote: () -> Unit,
    aiGeneratedClearable: Boolean,
    onClearAiGeneratedInput: () -> Unit,
    inputFocused: Boolean,
    onInputFocusedChange: (Boolean) -> Unit,
    panelMode: BottomPanelMode,
    onPanelModeChange: (BottomPanelMode) -> Unit,
    onSend: () -> Unit,
    onHome: () -> Unit,
    onAssistantPredict: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sendMode = inputText.isNotBlank()
    val leadingAction = bottomInputLeadingAction(inputFocused)
    val shape = RoundedCornerShape(23.dp)
    MaterialSurface(
        onClick = {},
        modifier = modifier
            .fillMaxWidth()
            .heightIn(
                min = BottomInputBarMinHeightDp.dp,
                max = BottomInputBarMaxHeightDp.dp
            ),
        shape = shape,
        color = OverlayTokens.bar,
        border = BorderStroke(1.dp, OverlayTokens.bottomBarStroke),
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 5.dp)
        ) {
            quotedMessage?.let { message ->
                QuotedComposerPreview(
                    message = message,
                    onClear = onClearQuote,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 5.dp)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                BottomIcon(
                    action = leadingAction,
                    active = leadingAction == BottomInputAction.Emoji && panelMode == BottomPanelMode.Emoji,
                    onClick = {
                        if (leadingAction == BottomInputAction.Home) {
                            onHome()
                        } else {
                            onPanelModeChange(
                                if (panelMode == BottomPanelMode.Emoji) {
                                    BottomPanelMode.None
                                } else {
                                    BottomPanelMode.Emoji
                                }
                            )
                        }
                    }
                )
                BottomIcon(
                    action = BottomInputAction.Voice,
                    active = panelMode == BottomPanelMode.Voice,
                    onClick = {
                        onPanelModeChange(
                            if (panelMode == BottomPanelMode.Voice) {
                                BottomPanelMode.None
                            } else {
                                BottomPanelMode.Voice
                            }
                        )
                    }
                )
                AlignedMessageInputField(
                    value = inputText,
                    onValueChange = onInputTextChange,
                    focused = inputFocused,
                    onFocusedChange = onInputFocusedChange,
                    onSend = onSend,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(
                            min = BottomInputFieldMinHeightDp.dp,
                            max = BottomInputFieldMaxHeightDp.dp
                        )
                )
                if (aiGeneratedClearable) {
                    CompactInteractiveSize {
                        IconButton(
                            onClick = onClearAiGeneratedInput,
                            modifier = Modifier
                                .size(BottomInputIconButtonSizeDp.dp)
                                .clip(CircleShape)
                                .background(OverlayTokens.bottomIconButton)
                                .border(1.dp, OverlayTokens.accent.copy(alpha = 0.72f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "\u6e05\u9664 AI \u751f\u6210\u5185\u5bb9",
                                tint = OverlayTokens.accent,
                                modifier = Modifier.size(BottomInputIconSizeDp.dp)
                            )
                        }
                    }
                }
                BottomIcon(
                    action = BottomInputAction.Gift,
                    active = panelMode == BottomPanelMode.Gift,
                    onClick = {
                        onPanelModeChange(
                            if (panelMode == BottomPanelMode.Gift) {
                                BottomPanelMode.None
                            } else {
                                BottomPanelMode.Gift
                            }
                        )
                    }
                )
                BottomIcon(
                    action = if (sendMode) BottomInputAction.Send else BottomInputAction.Assistant,
                    active = sendMode || panelMode == BottomPanelMode.Assistant,
                    onClick = {
                        if (sendMode) {
                            onSend()
                        } else {
                            onAssistantPredict()
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun QuotedComposerPreview(
    message: FloatingChatMessage,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(OverlayTokens.resourcePanel)
            .border(1.dp, OverlayTokens.resourcePanelBorder, RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        TextLabel(
            text = "\u5f15\u7528",
            size = 10.sp,
            weight = FontWeight.Bold,
            color = OverlayTokens.panelPrimaryText,
            maxLines = 1
        )
        TextLabel(
            text = message.longPressCopyText(),
            size = 10.sp,
            color = OverlayTokens.panelSecondaryText,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        CompactInteractiveSize {
            IconButton(
                onClick = onClear,
                modifier = Modifier.size(22.dp)
            ) {
                TextLabel(
                    text = "x",
                    size = 12.sp,
                    weight = FontWeight.Bold,
                    color = OverlayTokens.panelPrimaryText,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun AlignedMessageInputField(
    value: String,
    onValueChange: (String) -> Unit,
    focused: Boolean,
    onFocusedChange: (Boolean) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(17.dp)
    val borderColor = if (focused) OverlayTokens.inputFocus else OverlayTokens.inputStroke
    CompactInteractiveSize {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            minLines = BottomInputMinLines,
            maxLines = BottomInputMaxLines,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onSend() }),
            textStyle = TextStyle.Default.copy(
                color = OverlayTokens.inputText,
                fontSize = BottomInputTextSizeSp.sp,
                fontWeight = FontWeight.SemiBold
            ),
            cursorBrush = SolidColor(OverlayTokens.accent),
            modifier = modifier
                .clip(shape)
                .background(OverlayTokens.input)
                .border(1.dp, borderColor, shape)
                .onFocusChanged { onFocusedChange(it.isFocused) }
                .padding(horizontal = 11.dp, vertical = 8.dp),
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (value.isBlank()) {
                        TextLabel(
                            text = "\u8f93\u5165\u6d88\u606f....",
                            size = BottomInputPlaceholderTextSizeSp.sp,
                            weight = FontWeight.SemiBold,
                            color = OverlayTokens.inputPlaceholder,
                            maxLines = 1
                        )
                    }
                    innerTextField()
                }
            }
        )
    }
}

@Composable
private fun BottomIcon(
    action: BottomInputAction,
    active: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val iconTint = when {
        action == BottomInputAction.Assistant -> OverlayTokens.accent
        action == BottomInputAction.Send -> OverlayTokens.accent
        else -> OverlayTokens.bottomIcon
    }
    CompactInteractiveSize {
        IconButton(
            onClick = { onClick?.invoke() },
            enabled = onClick != null,
            modifier = Modifier
                .size(BottomInputIconButtonSizeDp.dp)
                .clip(CircleShape)
                .background(OverlayTokens.bottomIconButton)
                .border(1.dp, OverlayTokens.bottomIconStroke, CircleShape),
            colors = IconButtonDefaults.iconButtonColors(
                contentColor = iconTint,
                disabledContentColor = iconTint.copy(alpha = 0.65f)
            )
        ) {
            Icon(
                imageVector = bottomInputActionIcon(action),
                contentDescription = bottomInputActionDescription(action),
                tint = iconTint,
                modifier = Modifier.size(
                    if (action == BottomInputAction.Assistant) {
                        (BottomInputIconSizeDp + 1).dp
                    } else {
                        BottomInputIconSizeDp.dp
                    }
                )
            )
        }
    }
}

private fun bottomInputActionIcon(action: BottomInputAction): ImageVector {
    return when (action) {
        BottomInputAction.Home -> Icons.Filled.Home
        BottomInputAction.Emoji -> Icons.Filled.EmojiEmotions
        BottomInputAction.Voice -> Icons.Filled.Mic
        BottomInputAction.Text -> Icons.AutoMirrored.Filled.Send
        BottomInputAction.Gift -> Icons.Filled.CardGiftcard
        BottomInputAction.Assistant -> Icons.Filled.SmartToy
        BottomInputAction.Send -> Icons.AutoMirrored.Filled.Send
    }
}

private fun bottomInputActionDescription(action: BottomInputAction): String {
    return when (action) {
        BottomInputAction.Home -> "\u4e3b\u9875"
        BottomInputAction.Emoji -> "\u8868\u60c5\u5305"
        BottomInputAction.Voice -> "\u8bed\u97f3\u8f93\u5165"
        BottomInputAction.Text -> "\u8f93\u5165\u6d88\u606f"
        BottomInputAction.Gift -> "\u793c\u7269\u9009\u62e9"
        BottomInputAction.Assistant -> "\u673a\u5668\u4eba\u9884\u6d4b\u6d88\u606f"
        BottomInputAction.Send -> "\u53d1\u9001"
    }
}
