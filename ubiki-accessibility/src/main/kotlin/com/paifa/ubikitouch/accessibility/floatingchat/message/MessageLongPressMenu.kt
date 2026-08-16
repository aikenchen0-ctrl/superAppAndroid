package com.paifa.ubikitouch.accessibility.floatingchat.message

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Forward
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface as MaterialSurface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import com.paifa.ubikitouch.accessibility.floatingchat.aivoice.MessageAsideAnalysisState
import com.paifa.ubikitouch.accessibility.floatingchat.theme.OverlayTokens
import com.paifa.ubikitouch.accessibility.floatingchat.components.TextLabel
import com.paifa.ubikitouch.accessibility.floatingchat.popup.BalloonCoordinateState
import com.paifa.ubikitouch.accessibility.floatingchat.popup.BalloonPopupState
import com.paifa.ubikitouch.accessibility.floatingchat.popup.IrregularBalloonPopup
import com.paifa.ubikitouch.core.model.FloatingChatMessage
import kotlin.math.roundToInt

internal enum class MessageLongPressAction(val label: String) {
    Transcribe("转文字"),
    Listen("话外音"),
    Copy("复制"),
    Forward("转发"),
    Favorite("收藏"),
    MultiSelect("多选"),
    Quote("引用"),
    Zoom("放大"),
    Delete("删除"),
    ScrmOperations("更多")
}

internal fun messageLongPressPrimaryActions(): List<MessageLongPressAction> {
    return listOf(
        MessageLongPressAction.Listen,
        MessageLongPressAction.Copy,
        MessageLongPressAction.Forward,
        MessageLongPressAction.Favorite,
        MessageLongPressAction.MultiSelect,
        MessageLongPressAction.Quote,
        MessageLongPressAction.Zoom,
        MessageLongPressAction.Delete
    )
}

/**
 * UI：点击已同步语音消息时，将“转文字”放在首层操作菜单；本地录音和其他消息不展示。
 * 测试流程：依次点击已同步语音、本地语音和文本消息，仅第一种应在首位看到“转文字”。
 */
internal fun messageLongPressActionsFor(message: FloatingChatMessage): List<MessageLongPressAction> {
    val canTranscribe = message.type == com.paifa.ubikitouch.core.model.FloatingChatMessageType.Voice &&
        message.remoteMessageId?.let { it > 0L } == true
    return if (canTranscribe) {
        listOf(MessageLongPressAction.Transcribe) + messageLongPressPrimaryActions()
    } else {
        messageLongPressPrimaryActions()
    }
}

internal fun messageLongPressUsesWechatFloatingPanel(): Boolean = true

internal fun messageLongPressSupportsInternalForwarding(): Boolean = true

internal fun messageLongPressIncludesSearch(): Boolean = false

internal fun messageLongPressIncludesAsideAnalysis(): Boolean = true

internal fun messageLongPressSupportsMultiSelectMode(): Boolean = true

internal fun messageLongPressQuoteShowsComposerPreview(): Boolean = true

internal fun messageLongPressMenuAnchorsToMessageBounds(): Boolean = true

internal fun messageLongPressMenuUsesFixedSidePosition(): Boolean = false

internal enum class MultiForwardMode(val label: String) {
    Separate("逐条转发"),
    Combined("合并转发")
}

internal fun multiForwardModeLabels(): List<String> {
    return MultiForwardMode.values().map { mode -> mode.label }
}

internal fun multiSelectSelectionCountLabel(count: Int): String = "已选 $count"

@Composable
internal fun MessageLongPressMenuOverlay(
    message: FloatingChatMessage,
    messageBounds: Rect?,
    onDismiss: () -> Unit,
    onAction: (MessageLongPressAction) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier
            .background(Color.Transparent)
            .pointerInput(message.id) {
                detectTapGestures(onTap = { onDismiss() })
            }
    ) {
        val density = LocalDensity.current
        val menuWidth = MessageLongPressMenuWidth
        val menuHeight = MessageLongPressMenuEstimatedHeight
        val viewportWidthPx = with(density) { maxWidth.toPx() }
        val viewportHeightPx = with(density) { maxHeight.toPx() }
        val menuWidthPx = with(density) { menuWidth.toPx() }
        val menuHeightPx = with(density) { menuHeight.toPx() }
        val marginPx = with(density) { 10.dp.toPx() }
        val gapPx = with(density) { 8.dp.toPx() }
        val fallbackX = if (message.fromMe) viewportWidthPx - menuWidthPx - marginPx else marginPx
        val anchorCenterX = messageBounds?.center?.x ?: (fallbackX + menuWidthPx / 2f)
        val aboveTop = (messageBounds?.top ?: (viewportHeightPx / 2f)) - menuHeightPx - gapPx
        val belowTop = (messageBounds?.bottom ?: (viewportHeightPx / 2f)) + gapPx
        val placeBelow = aboveTop < marginPx && belowTop + menuHeightPx <= viewportHeightPx - marginPx
        val topPx = if (placeBelow) belowTop else aboveTop
        val clampedX = (anchorCenterX - menuWidthPx / 2f)
            .coerceIn(marginPx, (viewportWidthPx - menuWidthPx - marginPx).coerceAtLeast(marginPx))
        val clampedY = topPx.coerceIn(marginPx, (viewportHeightPx - menuHeightPx - marginPx).coerceAtLeast(marginPx))

        MessageLongPressMenu(
            actions = messageLongPressActionsFor(message),
            pointerOnTop = placeBelow,
            onAction = onAction,
            modifier = Modifier
                .offset(
                    x = with(density) { clampedX.toDp() },
                    y = with(density) { clampedY.toDp() }
                )
        )
    }
}

@Composable
internal fun MessageAsideAnalysisOverlay(
    state: MessageAsideAnalysisState,
    messageBounds: Rect?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val fallbackSize = with(density) { 48.dp.toPx() }
        val anchorWidthPx = messageBounds?.width?.coerceAtLeast(1f) ?: fallbackSize
        val anchorHeightPx = messageBounds?.height?.coerceAtLeast(1f) ?: fallbackSize
        val anchorX = messageBounds?.left ?: ((with(density) { maxWidth.toPx() } - anchorWidthPx) / 2f)
        val anchorY = messageBounds?.top ?: ((with(density) { maxHeight.toPx() } - anchorHeightPx) / 2f)
        val popupState = remember(state.message.id) { BalloonPopupState(initiallyVisible = true) }
        val coordinateState = remember(state.message.id) { BalloonCoordinateState() }

        IrregularBalloonPopup(
            state = popupState,
            coordinateState = coordinateState,
            onDismiss = onDismiss,
            containerColor = Color(0xF2F4FAFC),
            modifier = Modifier.fillMaxSize(),
            trigger = { triggerModifier ->
                Box(
                    Modifier
                        .offset { IntOffset(anchorX.roundToInt(), anchorY.roundToInt()) }
                        .size(
                            width = with(density) { anchorWidthPx.toDp() },
                            height = with(density) { anchorHeightPx.toDp() }
                        )
                        .then(triggerModifier)
                )
            }
        ) {
            MessageAsideAnalysisContent(
                state = state,
                modifier = Modifier
                    .width(280.dp)
                    .heightIn(min = 150.dp, max = 330.dp)
                    .padding(horizontal = 22.dp, vertical = 20.dp)
            )
        }
    }
}

@Composable
private fun MessageAsideAnalysisContent(
    state: MessageAsideAnalysisState,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TextLabel(
            text = "话外音",
            size = 16.sp,
            color = OverlayTokens.panelPrimaryText,
            weight = FontWeight.SemiBold,
            maxLines = 1
        )
        when (state) {
            is MessageAsideAnalysisState.Loading -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = OverlayTokens.panelPrimaryText
                    )
                    TextLabel(
                        text = "AI 正在分析当前消息...",
                        size = 12.sp,
                        color = OverlayTokens.panelSecondaryText
                    )
                }
            }
            is MessageAsideAnalysisState.Ready -> {
                MessageAsideAnalysisField("情绪", state.analysis.emotion)
                MessageAsideAnalysisField("立场", state.analysis.stance)
                MessageAsideAnalysisField("话外音", state.analysis.subtext)
            }
            is MessageAsideAnalysisState.Failed -> {
                TextLabel(
                    text = state.reason,
                    size = 12.sp,
                    lineHeight = 18.sp,
                    color = Color(0xFFB3261E)
                )
            }
        }
    }
}

@Composable
private fun MessageAsideAnalysisField(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        TextLabel(
            text = label,
            size = 10.sp,
            color = OverlayTokens.panelSecondaryText,
            weight = FontWeight.SemiBold,
            maxLines = 1
        )
        TextLabel(
            text = value,
            size = 13.sp,
            lineHeight = 19.sp,
            color = OverlayTokens.panelPrimaryText
        )
    }
}

@Composable
internal fun MessageTextZoomOverlay(
    message: FloatingChatMessage,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(Color(0xF2181D20))
            .pointerInput(message.id) { detectTapGestures { onDismiss() } }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 36.dp)
                .verticalScroll(rememberScrollState())
                .pointerInput(message.id) { detectTapGestures(onTap = {}) },
            verticalArrangement = Arrangement.Center
        ) {
            TextLabel(
                text = message.senderName.ifBlank { "消息" },
                size = 13.sp,
                color = Color(0xFFB7C6CC),
                weight = FontWeight.SemiBold,
                maxLines = 1
            )
            TextLabel(
                text = message.longPressCopyText(),
                size = 28.sp,
                lineHeight = 39.sp,
                color = Color(0xFFF4F7F8),
                modifier = Modifier.padding(top = 14.dp)
            )
            if (message.time.isNotBlank()) {
                TextLabel(
                    text = message.time,
                    size = 11.sp,
                    color = Color(0xFF91A3AA),
                    maxLines = 1,
                    modifier = Modifier.padding(top = 18.dp)
                )
            }
        }
        IconButton(
            onClick = onDismiss,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
                .size(40.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "关闭放大查看",
                tint = Color(0xFFF4F7F8)
            )
        }
    }
}

@Composable
internal fun MessageLongPressMenu(
    actions: List<MessageLongPressAction>,
    pointerOnTop: Boolean,
    onAction: (MessageLongPressAction) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (pointerOnTop) {
            MessageLongPressPointer(up = true)
        }
        MaterialSurface(
            modifier = Modifier.width(MessageLongPressMenuWidth),
            shape = RoundedCornerShape(5.dp),
            color = OverlayTokens.longPressMenu,
            shadowElevation = 0.dp
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                actions.chunked(5).forEach { rowActions ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        rowActions.forEach { action ->
                            MessageLongPressActionButton(
                                action = action,
                                onClick = { onAction(action) }
                            )
                        }
                    }
                }
            }
        }
        if (!pointerOnTop) {
            MessageLongPressPointer(up = false)
        }
    }
}

@Composable
internal fun MultiSelectActionBar(
    onForward: () -> Unit,
    onCombinedForward: () -> Unit,
    onFavorite: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    MaterialSurface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = OverlayTokens.longPressMenu,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            LongPressBarButton(MessageLongPressAction.Forward, onForward)
            LongPressBarButton(MessageLongPressAction.Forward, onCombinedForward)
            LongPressBarButton(MessageLongPressAction.Favorite, onFavorite)
            LongPressBarButton(MessageLongPressAction.Delete, onDelete)
            IconButton(onClick = onCancel, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "关闭多选", tint = Color(0xFFF5F8FA))
            }
            Button(
                onClick = onCancel,
                modifier = Modifier.size(0.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = Color(0xFFF5F8FA)),
                contentPadding = PaddingValues(horizontal = 5.dp, vertical = 0.dp)
            ) {
                TextLabel(text = "取消", size = 10.sp, color = Color(0xFFF5F8FA), maxLines = 1)
            }
        }
    }
}

@Composable
private fun MessageLongPressPointer(up: Boolean) {
    Canvas(modifier = Modifier.size(width = 18.dp, height = 9.dp)) {
        val path = Path().apply {
            if (up) {
                moveTo(size.width / 2f, 0f)
                lineTo(0f, size.height)
                lineTo(size.width, size.height)
            } else {
                moveTo(size.width / 2f, size.height)
                lineTo(0f, 0f)
                lineTo(size.width, 0f)
            }
            close()
        }
        drawPath(path, OverlayTokens.longPressMenu)
    }
}

@Composable
private fun MessageLongPressActionButton(
    action: MessageLongPressAction,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .width(45.dp)
            .height(50.dp),
        shape = RoundedCornerShape(4.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = Color(0xFFF5F8FA)
        ),
        contentPadding = PaddingValues(0.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = action.icon(),
                contentDescription = null,
                tint = Color(0xFFF5F8FA),
                modifier = Modifier.size(18.dp)
            )
            TextLabel(
                text = action.label,
                size = 10.sp,
                color = Color(0xFFF5F8FA),
                maxLines = 1,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun LongPressBarButton(
    action: MessageLongPressAction,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick, modifier = Modifier.size(30.dp)) {
        Icon(
            imageVector = action.icon(),
            contentDescription = null,
            tint = Color(0xFFF5F8FA),
            modifier = Modifier.size(17.dp)
        )
    }
}

private fun MessageLongPressAction.icon(): ImageVector {
    return when (this) {
        MessageLongPressAction.Transcribe -> Icons.Filled.TextFields
        MessageLongPressAction.Listen -> Icons.Filled.VolumeUp
        MessageLongPressAction.Zoom -> Icons.Filled.ZoomIn
        MessageLongPressAction.Copy -> Icons.Filled.ContentCopy
        MessageLongPressAction.Forward -> Icons.AutoMirrored.Filled.Forward
        MessageLongPressAction.Favorite -> Icons.Filled.Star
        MessageLongPressAction.Delete -> Icons.Filled.Delete
        MessageLongPressAction.MultiSelect -> Icons.Filled.Checklist
        MessageLongPressAction.Quote -> Icons.Filled.FormatQuote
        MessageLongPressAction.ScrmOperations -> Icons.Filled.MoreHoriz
    }
}

internal val MessageLongPressMenuWidth = 300.dp
internal val MessageLongPressMenuEstimatedHeight = 182.dp
