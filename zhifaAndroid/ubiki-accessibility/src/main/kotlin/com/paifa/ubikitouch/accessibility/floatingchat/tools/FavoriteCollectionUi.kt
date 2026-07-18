package com.paifa.ubikitouch.accessibility.floatingchat.tools

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface as MaterialSurface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paifa.ubikitouch.accessibility.floatingchat.theme.OverlayTokens
import com.paifa.ubikitouch.accessibility.floatingchat.components.TextLabel
import com.paifa.ubikitouch.accessibility.floatingchat.message.MessageLongPressAction
import com.paifa.ubikitouch.accessibility.floatingchat.message.MessageLongPressMenu
import com.paifa.ubikitouch.accessibility.floatingchat.message.MessageLongPressMenuEstimatedHeight
import com.paifa.ubikitouch.accessibility.floatingchat.message.MessageLongPressMenuWidth
import com.paifa.ubikitouch.core.model.FloatingChatMessageType

@Composable
internal fun FavoriteCollectionPreviewOverlay(
    item: FavoriteCollectionItem,
    onDismiss: () -> Unit,
    onForward: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(OverlayTokens.centerPanelScrim)
            .pointerInput(item.messageId) {
                detectTapGestures(onTap = { onDismiss() })
            },
        contentAlignment = Alignment.Center
    ) {
        MaterialSurface(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .fillMaxWidth()
                .widthIn(max = 430.dp),
            shape = RoundedCornerShape(10.dp),
            color = OverlayTokens.panel,
            border = BorderStroke(1.dp, OverlayTokens.panelBorder)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(favoriteAccentColor(item.type)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = favoriteIconFor(item.type),
                            contentDescription = null,
                            tint = OverlayTokens.primaryText,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        TextLabel(
                            text = item.title,
                            size = 13.sp,
                            weight = FontWeight.Bold,
                            color = OverlayTokens.panelPrimaryText,
                            maxLines = 2
                        )
                        TextLabel(
                            text = item.source,
                            size = 10.sp,
                            color = OverlayTokens.momentsName,
                            maxLines = 1
                        )
                    }
                }
                TextLabel(
                    text = item.description,
                    size = 11.sp,
                    color = OverlayTokens.panelSecondaryText,
                    lineHeight = 16.sp,
                    maxLines = 8
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onForward,
                        colors = ButtonDefaults.buttonColors(containerColor = OverlayTokens.inputFocus)
                    ) {
                        TextLabel(text = favoriteCollectionPreviewActionLabels()[0], size = 10.sp, color = OverlayTokens.primaryText, maxLines = 1)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onDelete,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB65757))
                    ) {
                        TextLabel(text = favoriteCollectionPreviewActionLabels()[1], size = 10.sp, color = OverlayTokens.primaryText, maxLines = 1)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent)
                    ) {
                        TextLabel(text = favoriteCollectionPreviewActionLabels()[2], size = 10.sp, color = OverlayTokens.panelPrimaryText, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
internal fun FavoriteCollectionLongPressMenuOverlay(
    item: FavoriteCollectionItem,
    itemBounds: Rect?,
    onDismiss: () -> Unit,
    onAction: (MessageLongPressAction) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier
            .background(Color.Transparent)
            .pointerInput(item.messageId) {
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
        val anchorCenterX = itemBounds?.center?.x ?: viewportWidthPx / 2f
        val aboveTop = (itemBounds?.top ?: (viewportHeightPx / 2f)) - menuHeightPx - gapPx
        val belowTop = (itemBounds?.bottom ?: (viewportHeightPx / 2f)) + gapPx
        val placeBelow = aboveTop < marginPx && belowTop + menuHeightPx <= viewportHeightPx - marginPx
        val topPx = if (placeBelow) belowTop else aboveTop
        val clampedX = (anchorCenterX - menuWidthPx / 2f)
            .coerceIn(marginPx, (viewportWidthPx - menuWidthPx - marginPx).coerceAtLeast(marginPx))
        val clampedY = topPx.coerceIn(marginPx, (viewportHeightPx - menuHeightPx - marginPx).coerceAtLeast(marginPx))

        MessageLongPressMenu(
            actions = favoriteCollectionLongPressActions(),
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
internal fun FavoriteCollectionPanel(
    items: List<FavoriteCollectionItem>,
    multiSelectMode: Boolean,
    selectedItemIds: Map<String, Boolean>,
    onPreviewItem: (FavoriteCollectionItem) -> Unit,
    onLongPressItem: (FavoriteCollectionItem, Rect?) -> Unit,
    onToggleSelection: (FavoriteCollectionItem) -> Unit,
    onForwardSelected: () -> Unit,
    onDeleteSelected: () -> Unit,
    onCancelSelection: () -> Unit
) {
    FavoritePanel(
        items = items.map { FavoriteUiItem(it.messageId, it.title, it.description) },
        selectedIds = selectedItemIds.filterValues { it }.keys,
        onToggleSelection = { id -> items.firstOrNull { it.messageId == id }?.let(onToggleSelection) },
        onOpen = { id -> items.firstOrNull { it.messageId == id }?.let(onPreviewItem) }
    )
}

internal fun favoriteCollectionPreviewActionLabels(): List<String> {
    return listOf("转发", "删除", "关闭")
}

internal fun favoriteCollectionSelectionCountLabel(count: Int): String = "已选 $count"

internal fun favoriteCollectionLongPressActions(): List<MessageLongPressAction> {
    return listOf(
        MessageLongPressAction.Forward,
        MessageLongPressAction.Delete,
        MessageLongPressAction.MultiSelect
    )
}

private fun favoriteIconFor(type: FloatingChatMessageType): ImageVector {
    return when (type) {
        FloatingChatMessageType.ImageThumbnail -> Icons.Filled.Image
        FloatingChatMessageType.VideoPreview -> Icons.Filled.VideoLibrary
        FloatingChatMessageType.FilePreview -> Icons.Filled.Article
        FloatingChatMessageType.Location,
        FloatingChatMessageType.InlineLocation -> Icons.Filled.LocationOn
        FloatingChatMessageType.ContactLink -> Icons.Filled.CreditCard
        else -> Icons.Filled.Star
    }
}

private fun favoriteAccentColor(type: FloatingChatMessageType): Color {
    return when (type) {
        FloatingChatMessageType.ImageThumbnail -> Color(0xFF5B8EB7)
        FloatingChatMessageType.VideoPreview -> Color(0xFF855E9B)
        FloatingChatMessageType.FilePreview -> Color(0xFF6A7F90)
        FloatingChatMessageType.Location,
        FloatingChatMessageType.InlineLocation -> Color(0xFF3D8B78)
        FloatingChatMessageType.ContactLink -> Color(0xFF5674A8)
        else -> Color(0xFF8D7B55)
    }
}
