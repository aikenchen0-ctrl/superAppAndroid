package com.paifa.ubikitouch.accessibility.floatingchat.input

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.HorizontalDivider
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
    More,
    Assistant,
    Send
}

internal const val BottomInputBarMinHeightDp = 84
internal const val BottomInputBarMaxHeightDp = 174
internal const val BottomInputBarBottomPaddingDp = 30
internal const val BottomInputBarHorizontalClearanceDp = 12
internal const val BottomGestureTouchClearanceDp = 30
internal const val BottomEmojiPanelHeightDp = 300

internal const val BottomInputIconButtonSizeDp = 44
internal const val BottomInputIconSizeDp = 24
private const val BottomInputFieldMinHeightDp = 46
private const val BottomInputFieldMaxHeightDp = 128
private const val BottomInputTextSizeSp = 13
private const val BottomInputPlaceholderTextSizeSp = 13
private const val BottomInputMinLines = 1
private const val BottomInputMaxLines = 4

internal fun bottomInputBarHeightDp(): Int = BottomInputBarMinHeightDp

internal fun bottomInputBarMinHeightDp(): Int = BottomInputBarMinHeightDp

internal fun bottomInputBarMaxHeightDp(): Int = BottomInputBarMaxHeightDp

internal fun bottomInputBarBottomPaddingDp(): Int = BottomInputBarBottomPaddingDp

internal fun bottomInputBarHorizontalClearanceDp(): Int = BottomInputBarHorizontalClearanceDp

internal fun bottomInputBarUsesKeyboardInsets(): Boolean = true

/** 全屏功能工作区打开时隐藏聊天输入栏，避免输入栏叠加在 surface 页面之上。 */
internal fun bottomInputBarVisibleForFullscreenWorkspace(
    fullscreenWorkspaceVisible: Boolean
): Boolean = !fullscreenWorkspaceVisible

internal fun bottomInputControlsUseCenterAlignment(): Boolean = true

internal fun bottomInputBarUsesWechatStyle(): Boolean = true

internal fun bottomInputBarReservesBottomGestureBarSpace(): Boolean = true

internal fun bottomInputUsesCustomBasicTextField(): Boolean = true

internal fun bottomInputTextSizeSp(): Int = BottomInputTextSizeSp

internal fun bottomInputPlaceholderTextSizeSp(): Int = BottomInputPlaceholderTextSizeSp

internal fun bottomInputIconButtonSizeDp(): Int = BottomInputIconButtonSizeDp

internal fun bottomInputIconSizeDp(): Int = BottomInputIconSizeDp

internal fun bottomInputMinLines(): Int = BottomInputMinLines

internal fun bottomInputMaxLines(): Int = BottomInputMaxLines

internal fun bottomInputActionOrder(): List<BottomInputAction> {
    return listOf(
        BottomInputAction.Emoji,
        BottomInputAction.Text,
        BottomInputAction.Voice,
        BottomInputAction.More,
        BottomInputAction.Assistant
    )
}

internal fun bottomInputLeadingAction(inputFocused: Boolean): BottomInputAction {
    return if (inputFocused) BottomInputAction.Emoji else BottomInputAction.Home
}

internal fun bottomInputTrailingAction(): BottomInputAction = BottomInputAction.Assistant

internal fun bottomHomeButtonShowsUnreadOverview(): Boolean = true

internal fun bottomHomeButtonShowsUnrepliedOverview(): Boolean = true

internal fun bottomHomeButtonSwapsToEmojiWhenInputFocused(): Boolean = true

internal fun bottomInputAssistantActionSendsPredictedMessage(): Boolean = true

internal fun bottomInputAssistantUsesAiDraftPrediction(): Boolean = true

internal fun assistantPredictionRequiresAiConfiguration(): Boolean = true

internal fun bottomEmojiPanelUsesLightweightGrid(): Boolean = true

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
    voiceInputMode: Boolean,
    onVoiceInputModeChange: (Boolean) -> Unit,
    voicePermissionRequestToken: Int,
    onRecordingReady: (PendingVoiceRecording) -> Unit,
    panelMode: BottomPanelMode,
    onPanelModeChange: (BottomPanelMode) -> Unit,
    onSend: () -> Unit,
    onAssistantPredict: () -> Unit,
    modifier: Modifier = Modifier
) {
    val trailingAction = bottomInputTrailingAction()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(
                min = BottomInputBarMinHeightDp.dp,
                max = BottomInputBarMaxHeightDp.dp
            )
            .background(WechatInputBarBackground)
    ) {
        HorizontalDivider(color = WechatInputBarDivider, thickness = 1.dp)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = BottomInputBarHorizontalClearanceDp.dp,
                    end = BottomInputBarHorizontalClearanceDp.dp,
                    top = 7.dp,
                    bottom = BottomInputBarBottomPaddingDp.dp
                )
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
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                BottomIcon(
                    action = BottomInputAction.Emoji,
                    active = panelMode == BottomPanelMode.Emoji,
                    onClick = {
                        onPanelModeChange(
                            if (panelMode == BottomPanelMode.Emoji) BottomPanelMode.None
                            else BottomPanelMode.Emoji
                        )
                    }
                )
                BottomIcon(
                    action = BottomInputAction.Voice,
                    active = voiceInputMode,
                    onClick = {
                        onVoiceInputModeChange(!voiceInputMode)
                        if (voiceInputMode) onInputFocusedChange(false)
                        onPanelModeChange(BottomPanelMode.None)
                    }
                )
                if (voiceInputMode) {
                    HoldToRecordVoiceBox(
                        permissionRequestToken = voicePermissionRequestToken,
                        onRecordingReady = onRecordingReady,
                        modifier = Modifier.weight(1f)
                    )
                } else {
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
                }
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
                MoreInputButton(
                    active = panelMode == BottomPanelMode.More,
                    onClick = {
                        onPanelModeChange(moreInputButtonNextPanelMode(panelMode))
                    }
                )
                BottomIcon(
                    action = trailingAction,
                    active = panelMode == BottomPanelMode.Assistant,
                    onClick = {
                        onAssistantPredict()
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
    val shape = RoundedCornerShape(6.dp)
    val borderColor = if (focused) WechatInputFocus else WechatInputBackground
    CompactInteractiveSize {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            minLines = BottomInputMinLines,
            maxLines = BottomInputMaxLines,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onSend() }),
            textStyle = TextStyle.Default.copy(
                color = WechatInputText,
                fontSize = BottomInputTextSizeSp.sp,
                fontWeight = FontWeight.SemiBold
            ),
            cursorBrush = SolidColor(WechatInputFocus),
            modifier = modifier
                .clip(shape)
                .background(WechatInputBackground)
                .border(1.dp, borderColor, shape)
                .onFocusChanged { onFocusedChange(it.isFocused) }
                .padding(horizontal = 13.dp, vertical = 9.dp),
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
                            color = WechatInputPlaceholder,
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
        else -> WechatInputIcon
    }
    CompactInteractiveSize {
        IconButton(
            onClick = { onClick?.invoke() },
            enabled = onClick != null,
            modifier = Modifier
                .size(BottomInputIconButtonSizeDp.dp)
                .clip(CircleShape),
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

private val WechatInputBarBackground = OverlayTokens.bottomComposerSurface
private val WechatInputBarDivider = androidx.compose.ui.graphics.Color(0xFFD8D8D8)
private val WechatInputBackground = androidx.compose.ui.graphics.Color(0xFFFFFFFF)
private val WechatInputFocus = androidx.compose.ui.graphics.Color(0xFF07C160)
private val WechatInputText = androidx.compose.ui.graphics.Color(0xFF1F1F1F)
private val WechatInputPlaceholder = androidx.compose.ui.graphics.Color(0xFFB2B2B2)
private val WechatInputIcon = androidx.compose.ui.graphics.Color(0xFF303030)

private fun bottomInputActionIcon(action: BottomInputAction): ImageVector {
    return when (action) {
        BottomInputAction.Home -> Icons.Filled.Home
        BottomInputAction.Emoji -> Icons.Filled.EmojiEmotions
        BottomInputAction.Voice -> Icons.Filled.Mic
        BottomInputAction.Text -> Icons.AutoMirrored.Filled.Send
        BottomInputAction.Gift -> Icons.Filled.CardGiftcard
        BottomInputAction.More -> Icons.Filled.Add
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
        BottomInputAction.More -> "\u66f4\u591a\u5de5\u5177"
        BottomInputAction.Assistant -> "\u673a\u5668\u4eba\u9884\u6d4b\u6d88\u606f"
        BottomInputAction.Send -> "\u53d1\u9001"
    }
}
