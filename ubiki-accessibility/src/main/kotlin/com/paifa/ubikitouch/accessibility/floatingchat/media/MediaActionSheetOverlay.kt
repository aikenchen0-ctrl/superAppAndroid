package com.paifa.ubikitouch.accessibility.floatingchat.media

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paifa.ubikitouch.accessibility.floatingchat.components.CompactInteractiveSize
import com.paifa.ubikitouch.accessibility.floatingchat.theme.OverlayTokens
import com.paifa.ubikitouch.accessibility.floatingchat.components.TextLabel
import com.paifa.ubikitouch.core.model.FloatingChatMessage

@Composable
internal fun FloatingChatMediaActionSheetOverlay(
    message: FloatingChatMessage,
    onClose: () -> Unit,
    onMediaAction: (MediaActionContract) -> Unit,
    modifier: Modifier = Modifier
) {
    val clickSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .background(OverlayTokens.mediaSheetScrim)
            .clickable(
                interactionSource = clickSource,
                indication = null,
                onClick = onClose
            )
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 8.dp)
                .clipMediaActionSheet()
                .background(OverlayTokens.mediaSheet)
                .border(
                    width = 1.dp,
                    color = OverlayTokens.mediaSheetBorder,
                    shape = mediaActionSheetShape()
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FloatingChatMediaActionSheetHeader(message)
            val rows = listOf(
                listOf(
                    FloatingChatMediaAction.Share,
                    FloatingChatMediaAction.Save,
                    FloatingChatMediaAction.Favorite,
                    FloatingChatMediaAction.More
                ),
                listOf(
                    FloatingChatMediaAction.Visibility,
                    FloatingChatMediaAction.Edit,
                    FloatingChatMediaAction.Comment,
                    FloatingChatMediaAction.Grid
                )
            )
            rows.forEach { rowActions ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    rowActions.forEach { action ->
                        FloatingChatMediaActionItem(
                            label = action.label,
                            onClick = { onMediaAction(action.toContract()) },
                            iconContent = {
                                FloatingChatMediaActionIcon(
                                    action = action,
                                    color = OverlayTokens.mediaSheetIcon,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        )
                    }
                }
            }
            Button(
                onClick = onClose,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .background(OverlayTokens.mediaSheetCancel),
                shape = RoundedCornerShape(7.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = OverlayTokens.mediaSheetCancel,
                    contentColor = OverlayTokens.mediaSheetText
                ),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
            ) {
                TextLabel(
                    text = "取消",
                    size = 11.sp,
                    weight = FontWeight.Bold,
                    color = OverlayTokens.mediaSheetText,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
internal fun FloatingChatStandaloneImageQuickActions(
    onOpenActions: () -> Unit,
    onShare: () -> Unit,
    onSave: () -> Unit,
    onFavorite: () -> Unit,
    favorite: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FloatingChatMediaRoundActionButton(
            action = FloatingChatMediaAction.Share,
            onClick = onShare
        )
        FloatingChatMediaRoundActionButton(
            action = FloatingChatMediaAction.Save,
            onClick = onSave
        )
        FloatingChatMediaRoundActionButton(
            action = FloatingChatMediaAction.Favorite,
            selected = favorite,
            onClick = onFavorite
        )
        FloatingChatMediaRoundActionButton(
            action = FloatingChatMediaAction.More,
            onClick = onOpenActions
        )
    }
}

internal enum class FloatingChatMediaAction(val label: String) {
    Share("分享"),
    Save("保存"),
    Favorite("收藏"),
    More("更多"),
    Visibility("可见范围"),
    Edit("编辑"),
    Comment("定位到聊天"),
    Grid("图片管理")
}

internal fun FloatingChatMediaAction.toContract(): MediaActionContract {
    return when (this) {
        FloatingChatMediaAction.Share -> MediaActionContract.Share
        FloatingChatMediaAction.Save -> MediaActionContract.Save
        FloatingChatMediaAction.Favorite -> MediaActionContract.Favorite
        FloatingChatMediaAction.More -> MediaActionContract.More
        FloatingChatMediaAction.Visibility -> MediaActionContract.Visibility
        FloatingChatMediaAction.Edit -> MediaActionContract.Edit
        FloatingChatMediaAction.Comment -> MediaActionContract.Comment
        FloatingChatMediaAction.Grid -> MediaActionContract.Grid
    }
}

@Composable
internal fun FloatingChatMediaActionIcon(
    action: FloatingChatMediaAction,
    color: Color,
    strokeWidth: Float = 1.9f,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (action) {
            FloatingChatMediaAction.Share -> drawShareArrowIcon(color, stroke)
            FloatingChatMediaAction.Save -> drawSaveBoxIcon(color, stroke)
            FloatingChatMediaAction.Favorite -> drawHeartToolIcon(color, stroke)
            FloatingChatMediaAction.More -> drawMoreDotsIcon(color)
            FloatingChatMediaAction.Visibility -> drawEyeToolIcon(color, stroke)
            FloatingChatMediaAction.Edit -> drawEditPencilIcon(color, stroke)
            FloatingChatMediaAction.Comment -> drawCommentBubbleIcon(color, stroke)
            FloatingChatMediaAction.Grid -> drawGridIcon(color, stroke)
        }
    }
}

@Composable
private fun FloatingChatMediaRoundActionButton(
    action: FloatingChatMediaAction,
    onClick: () -> Unit,
    selected: Boolean = false,
    modifier: Modifier = Modifier
) {
    CompactInteractiveSize {
        IconButton(
            onClick = onClick,
            modifier = modifier
                .size(24.dp)
                .background(
                    color = if (selected) OverlayTokens.mediaActionButtonSelected else OverlayTokens.mediaActionButton,
                    shape = CircleShape
                )
                .border(
                    width = 1.dp,
                    color = if (selected) OverlayTokens.accent else OverlayTokens.mediaActionBorder,
                    shape = CircleShape
                ),
            colors = IconButtonDefaults.iconButtonColors(
                contentColor = if (selected) OverlayTokens.mediaActionIconSelected else OverlayTokens.mediaActionIcon
            )
        ) {
            val iconColor = if (selected) OverlayTokens.mediaActionIconSelected else OverlayTokens.mediaActionIcon
            FloatingChatMediaActionIcon(
                action = action,
                color = iconColor,
                strokeWidth = 1.7f,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

private fun Modifier.clipMediaActionSheet(): Modifier = clip(mediaActionSheetShape())

private fun mediaActionSheetShape(): RoundedCornerShape {
    return RoundedCornerShape(topStart = 13.dp, topEnd = 13.dp, bottomStart = 9.dp, bottomEnd = 9.dp)
}

private fun DrawScope.drawHeartToolIcon(color: Color, stroke: Stroke) {
    val w = size.width
    val h = size.height
    val heart = Path().apply {
        moveTo(w * 0.50f, h * 0.78f)
        cubicTo(w * 0.24f, h * 0.60f, w * 0.16f, h * 0.44f, w * 0.25f, h * 0.30f)
        cubicTo(w * 0.35f, h * 0.16f, w * 0.48f, h * 0.25f, w * 0.50f, h * 0.36f)
        cubicTo(w * 0.52f, h * 0.25f, w * 0.65f, h * 0.16f, w * 0.75f, h * 0.30f)
        cubicTo(w * 0.84f, h * 0.44f, w * 0.76f, h * 0.60f, w * 0.50f, h * 0.78f)
    }
    drawPath(heart, color, style = stroke)
}

private fun DrawScope.drawEyeToolIcon(color: Color, stroke: Stroke) {
    val w = size.width
    val h = size.height
    val eye = Path().apply {
        moveTo(w * 0.12f, h * 0.50f)
        cubicTo(w * 0.28f, h * 0.28f, w * 0.72f, h * 0.28f, w * 0.88f, h * 0.50f)
        cubicTo(w * 0.72f, h * 0.72f, w * 0.28f, h * 0.72f, w * 0.12f, h * 0.50f)
    }
    drawPath(eye, color, style = stroke)
    drawCircle(color, radius = w * 0.10f, center = Offset(w * 0.50f, h * 0.50f), style = stroke)
}

private fun DrawScope.drawShareArrowIcon(color: Color, stroke: Stroke) {
    val w = size.width
    val h = size.height
    val arrow = Path().apply {
        moveTo(w * 0.22f, h * 0.58f)
        cubicTo(w * 0.36f, h * 0.38f, w * 0.56f, h * 0.30f, w * 0.78f, h * 0.28f)
        lineTo(w * 0.66f, h * 0.17f)
        moveTo(w * 0.78f, h * 0.28f)
        lineTo(w * 0.66f, h * 0.42f)
    }
    drawPath(arrow, color, style = stroke)
    drawLine(color, Offset(w * 0.22f, h * 0.58f), Offset(w * 0.22f, h * 0.78f), strokeWidth = stroke.width, cap = StrokeCap.Round)
}

private fun DrawScope.drawSaveBoxIcon(color: Color, stroke: Stroke) {
    val w = size.width
    val h = size.height
    drawRoundRect(
        color = color,
        topLeft = Offset(w * 0.22f, h * 0.46f),
        size = Size(w * 0.56f, h * 0.34f),
        cornerRadius = CornerRadius(w * 0.05f, w * 0.05f),
        style = stroke
    )
    drawLine(color, Offset(w * 0.50f, h * 0.16f), Offset(w * 0.50f, h * 0.58f), strokeWidth = stroke.width, cap = StrokeCap.Round)
    drawLine(color, Offset(w * 0.34f, h * 0.42f), Offset(w * 0.50f, h * 0.58f), strokeWidth = stroke.width, cap = StrokeCap.Round)
    drawLine(color, Offset(w * 0.66f, h * 0.42f), Offset(w * 0.50f, h * 0.58f), strokeWidth = stroke.width, cap = StrokeCap.Round)
}

private fun DrawScope.drawMoreDotsIcon(color: Color) {
    val w = size.width
    val h = size.height
    drawCircle(color, radius = w * 0.07f, center = Offset(w * 0.30f, h * 0.50f))
    drawCircle(color, radius = w * 0.07f, center = Offset(w * 0.50f, h * 0.50f))
    drawCircle(color, radius = w * 0.07f, center = Offset(w * 0.70f, h * 0.50f))
}

private fun DrawScope.drawEditPencilIcon(color: Color, stroke: Stroke) {
    val w = size.width
    val h = size.height
    drawLine(color, Offset(w * 0.30f, h * 0.70f), Offset(w * 0.72f, h * 0.28f), strokeWidth = stroke.width, cap = StrokeCap.Round)
    drawLine(color, Offset(w * 0.64f, h * 0.20f), Offset(w * 0.80f, h * 0.36f), strokeWidth = stroke.width, cap = StrokeCap.Round)
    drawLine(color, Offset(w * 0.24f, h * 0.78f), Offset(w * 0.36f, h * 0.72f), strokeWidth = stroke.width, cap = StrokeCap.Round)
    drawRoundRect(
        color = color,
        topLeft = Offset(w * 0.18f, h * 0.18f),
        size = Size(w * 0.64f, h * 0.64f),
        cornerRadius = CornerRadius(w * 0.07f, w * 0.07f),
        style = stroke
    )
}

private fun DrawScope.drawCommentBubbleIcon(color: Color, stroke: Stroke) {
    val w = size.width
    val h = size.height
    val bubble = Path().apply {
        moveTo(w * 0.22f, h * 0.24f)
        lineTo(w * 0.78f, h * 0.24f)
        lineTo(w * 0.78f, h * 0.66f)
        lineTo(w * 0.50f, h * 0.66f)
        lineTo(w * 0.34f, h * 0.82f)
        lineTo(w * 0.36f, h * 0.66f)
        lineTo(w * 0.22f, h * 0.66f)
        close()
    }
    drawPath(bubble, color, style = stroke)
}

private fun DrawScope.drawGridIcon(color: Color, stroke: Stroke) {
    val w = size.width
    val h = size.height
    val cell = Size(w * 0.20f, h * 0.20f)
    listOf(
        Offset(w * 0.22f, h * 0.22f),
        Offset(w * 0.58f, h * 0.22f),
        Offset(w * 0.22f, h * 0.58f),
        Offset(w * 0.58f, h * 0.58f)
    ).forEach { topLeft ->
        drawRoundRect(
            color = color,
            topLeft = topLeft,
            size = cell,
            cornerRadius = CornerRadius(w * 0.04f, w * 0.04f),
            style = stroke
        )
    }
}
