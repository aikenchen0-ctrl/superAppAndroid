package com.paifa.ubikitouch.accessibility.floatingchat.message

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Forward
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface as MaterialSurface
import androidx.compose.runtime.Composable
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
import com.paifa.ubikitouch.accessibility.floatingchat.theme.OverlayTokens
import com.paifa.ubikitouch.accessibility.floatingchat.components.TextLabel
import com.paifa.ubikitouch.core.model.FloatingChatMessage

internal enum class MessageLongPressAction(val label: String) {
    Copy("复制"),
    Forward("转发"),
    Favorite("收藏"),
    Delete("删除"),
    MultiSelect("多选"),
    Quote("引用"),
    Reminder("提醒")
}

internal fun messageLongPressPrimaryActions(): List<MessageLongPressAction> {
    return listOf(
        MessageLongPressAction.Copy,
        MessageLongPressAction.Forward,
        MessageLongPressAction.Favorite,
        MessageLongPressAction.Delete,
        MessageLongPressAction.MultiSelect,
        MessageLongPressAction.Quote,
        MessageLongPressAction.Reminder
    )
}

internal fun messageLongPressUsesWechatFloatingPanel(): Boolean = true

internal fun messageLongPressSupportsInternalForwarding(): Boolean = true

internal fun messageLongPressReminderUsesUiStateOnly(): Boolean = true

internal fun messageLongPressIncludesSearch(): Boolean = false

internal fun messageLongPressIncludesListenFromHere(): Boolean = false

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
            actions = messageLongPressPrimaryActions(),
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
    selectedCount: Int,
    onForward: () -> Unit,
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
            TextLabel(
                text = multiSelectSelectionCountLabel(selectedCount),
                size = 11.sp,
                weight = FontWeight.Bold,
                color = Color(0xFFF5F8FA),
                maxLines = 1
            )
            LongPressBarButton(MessageLongPressAction.Forward, onForward)
            LongPressBarButton(MessageLongPressAction.Favorite, onFavorite)
            LongPressBarButton(MessageLongPressAction.Delete, onDelete)
            Button(
                onClick = onCancel,
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
        MessageLongPressAction.Copy -> Icons.Filled.ContentCopy
        MessageLongPressAction.Forward -> Icons.AutoMirrored.Filled.Forward
        MessageLongPressAction.Favorite -> Icons.Filled.Star
        MessageLongPressAction.Delete -> Icons.Filled.Delete
        MessageLongPressAction.MultiSelect -> Icons.Filled.Checklist
        MessageLongPressAction.Quote -> Icons.Filled.FormatQuote
        MessageLongPressAction.Reminder -> Icons.Filled.Notifications
    }
}

internal val MessageLongPressMenuWidth = 300.dp
internal val MessageLongPressMenuEstimatedHeight = 122.dp
