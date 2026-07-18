package com.paifa.ubikitouch.accessibility.floatingchat.message

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface as MaterialSurface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paifa.ubikitouch.accessibility.floatingchat.components.CompactInteractiveSize
import com.paifa.ubikitouch.accessibility.FloatingChatAiDraftAction
import com.paifa.ubikitouch.accessibility.floatingchat.theme.OverlayTokens
import com.paifa.ubikitouch.accessibility.floatingchat.components.TextLabel
import com.paifa.ubikitouch.accessibility.floatingChatAiDraftActions
import com.paifa.ubikitouch.core.model.FloatingChatMessage

@Composable
internal fun MessageSelectionToggle(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    CompactInteractiveSize {
        IconButton(
            onClick = onClick,
            modifier = modifier.size(28.dp)
        ) {
            Icon(
                imageVector = if (selected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (selected) OverlayTokens.activeControl else OverlayTokens.primaryText,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
internal fun MessageStateBadges(
    favorite: Boolean,
    reminded: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(9.dp))
            .background(Color(0xCC4A4A4A))
            .padding(horizontal = 5.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (favorite) {
            Icon(Icons.Filled.Star, contentDescription = null, tint = Color(0xFFFFD56A), modifier = Modifier.size(10.dp))
        }
        if (reminded) {
            Icon(Icons.Filled.Notifications, contentDescription = null, tint = Color(0xFFF5F8FA), modifier = Modifier.size(10.dp))
        }
    }
}

@Composable
internal fun AiDraftActionOverlay(
    message: FloatingChatMessage,
    onDismiss: () -> Unit,
    onAction: (FloatingChatAiDraftAction) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(Color(0x22000000))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
        )
        MaterialSurface(
            onClick = {},
            modifier = Modifier
                .widthIn(min = 250.dp, max = 320.dp),
            shape = RoundedCornerShape(8.dp),
            color = OverlayTokens.longPressMenu,
            shadowElevation = 0.dp
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TextLabel(
                    text = "AI 草稿",
                    size = 12.sp,
                    weight = FontWeight.SemiBold,
                    color = Color(0xFFF5F8FA),
                    maxLines = 1
                )
                TextLabel(
                    text = message.text,
                    size = 11.sp,
                    color = Color(0xEAF5F8FA),
                    maxLines = 3
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    floatingChatAiDraftActions().forEach { action ->
                        Button(
                            onClick = { onAction(action) },
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp),
                            shape = RoundedCornerShape(5.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0x22FFFFFF),
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 0.dp)
                        ) {
                            TextLabel(
                                text = action.label,
                                size = 10.sp,
                                color = Color.White,
                                maxLines = 1,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun AiDraftEditOverlay(
    message: FloatingChatMessage,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onSend: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var draftText by remember(message.id) { mutableStateOf(message.text) }
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(Color(0x22000000))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
        )
        MaterialSurface(
            onClick = {},
            modifier = Modifier
                .padding(horizontal = 22.dp)
                .widthIn(min = 260.dp, max = 360.dp),
            shape = RoundedCornerShape(8.dp),
            color = OverlayTokens.panel,
            border = androidx.compose.foundation.BorderStroke(1.dp, OverlayTokens.panelBorder),
            shadowElevation = 0.dp
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TextLabel(
                    text = "编辑 AI 草稿",
                    size = 13.sp,
                    weight = FontWeight.SemiBold,
                    color = OverlayTokens.panelPrimaryText,
                    maxLines = 1
                )
                OutlinedTextField(
                    value = draftText,
                    onValueChange = { draftText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 96.dp),
                    minLines = 4,
                    textStyle = TextStyle(fontSize = 13.sp, color = OverlayTokens.panelPrimaryText),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = OverlayTokens.panelPrimaryText,
                        unfocusedTextColor = OverlayTokens.panelPrimaryText,
                        focusedBorderColor = OverlayTokens.panelBorder,
                        unfocusedBorderColor = OverlayTokens.panelBorder,
                        cursorColor = OverlayTokens.panelPrimaryText
                    )
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = OverlayTokens.resourcePanel,
                            contentColor = OverlayTokens.panelPrimaryText
                        )
                    ) {
                        TextLabel(
                            text = "取消",
                            size = 11.sp,
                            color = OverlayTokens.panelPrimaryText,
                            maxLines = 1,
                            textAlign = TextAlign.Center
                        )
                    }
                    Button(
                        onClick = { onSave(draftText) },
                        enabled = draftText.isNotBlank(),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = OverlayTokens.resourcePanel,
                            contentColor = OverlayTokens.panelPrimaryText
                        )
                    ) {
                        TextLabel(
                            text = "保存",
                            size = 11.sp,
                            color = OverlayTokens.panelPrimaryText,
                            maxLines = 1,
                            textAlign = TextAlign.Center
                        )
                    }
                    Button(
                        onClick = { onSend(draftText) },
                        enabled = draftText.isNotBlank(),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = OverlayTokens.accent,
                            contentColor = Color.White
                        )
                    ) {
                        TextLabel(
                            text = "发送",
                            size = 11.sp,
                            color = Color.White,
                            maxLines = 1,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}
