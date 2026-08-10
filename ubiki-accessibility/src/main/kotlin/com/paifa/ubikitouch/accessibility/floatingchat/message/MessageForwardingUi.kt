package com.paifa.ubikitouch.accessibility.floatingchat.message

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Forward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface as MaterialSurface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paifa.ubikitouch.accessibility.floatingchat.chat.ChatThreadSelection
import com.paifa.ubikitouch.accessibility.floatingchat.theme.OverlayTokens
import com.paifa.ubikitouch.accessibility.floatingchat.components.TextLabel
import com.paifa.ubikitouch.core.model.FloatingChatConversation
import com.paifa.ubikitouch.core.model.FloatingChatMessage

@Composable
internal fun MessageForwardTargetOverlay(
    conversation: FloatingChatConversation,
    onDismiss: () -> Unit,
    onTargetSelected: (ChatThreadSelection) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(Color(0x33000000))
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onDismiss() })
            },
        contentAlignment = Alignment.Center
    ) {
        MaterialSurface(
            modifier = Modifier
                .widthIn(min = 240.dp, max = 300.dp)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = {})
                },
            shape = RoundedCornerShape(12.dp),
            color = OverlayTokens.panel,
            border = BorderStroke(1.dp, OverlayTokens.panelBorder)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextLabel(
                    text = "转发给",
                    size = 13.sp,
                    weight = FontWeight.Bold,
                    color = OverlayTokens.panelPrimaryText,
                    maxLines = 1
                )
                conversation.groupContacts.forEach { group ->
                    ForwardTargetRow(
                        label = group.name,
                        subtitle = group.description,
                        onClick = { onTargetSelected(ChatThreadSelection.GroupChat(group.id)) }
                    )
                }
                conversation.contacts.forEach { contact ->
                    ForwardTargetRow(
                        label = contact.name,
                        subtitle = contact.description,
                        onClick = { onTargetSelected(ChatThreadSelection.Private(contact.id)) }
                    )
                }
            }
        }
    }
}

@Composable
internal fun MultiForwardModeOverlay(
    selectedCount: Int,
    onDismiss: () -> Unit,
    onModeSelected: (MultiForwardMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(Color(0x33000000))
            .pointerInput(selectedCount) {
                detectTapGestures(onTap = { onDismiss() })
            },
        contentAlignment = Alignment.Center
    ) {
        MaterialSurface(
            modifier = Modifier
                .widthIn(min = 240.dp, max = 300.dp)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = {})
                },
            shape = RoundedCornerShape(12.dp),
            color = OverlayTokens.panel,
            border = BorderStroke(1.dp, OverlayTokens.panelBorder)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                TextLabel(
                    text = "转发 $selectedCount 条消息",
                    size = 13.sp,
                    weight = FontWeight.Bold,
                    color = OverlayTokens.panelPrimaryText,
                    maxLines = 1
                )
                ForwardModeRow(
                    label = MultiForwardMode.Separate.label,
                    subtitle = "逐条发送到目标聊天，保留每条消息的独立气泡。",
                    onClick = { onModeSelected(MultiForwardMode.Separate) }
                )
                ForwardModeRow(
                    label = MultiForwardMode.Combined.label,
                    subtitle = "合并为一条聊天记录消息，适合一次性预览。",
                    onClick = { onModeSelected(MultiForwardMode.Combined) }
                )
                ForwardChoiceButton(label = "取消", onClick = onDismiss)
            }
        }
    }
}

@Composable
internal fun ChatHistoryDetailOverlay(
    message: FloatingChatMessage,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(OverlayTokens.centerPanelScrim)
            .pointerInput(message.id) {
                detectTapGestures(onTap = { onDismiss() })
            },
        contentAlignment = Alignment.Center
    ) {
        MaterialSurface(
            modifier = Modifier
                .widthIn(min = 320.dp, max = 390.dp)
                .heightIn(max = 620.dp)
                .pointerInput(message.id) {
                    detectTapGestures(onTap = {})
                },
            shape = RoundedCornerShape(14.dp),
            color = OverlayTokens.panel,
            border = BorderStroke(1.dp, OverlayTokens.panelBorder),
            shadowElevation = 10.dp
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss, modifier = Modifier.size(30.dp)) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = null,
                            tint = OverlayTokens.panelPrimaryText,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                    TextLabel(
                        text = message.text.ifBlank { "聊天记录" },
                        size = 14.sp,
                        weight = FontWeight.Bold,
                        color = OverlayTokens.panelPrimaryText,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    itemsIndexed(message.filePreviewLines) { _, line ->
                        ChatHistoryDetailRow(line)
                    }
                }
            }
        }
    }
}

@Composable
private fun ForwardTargetRow(
    label: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(OverlayTokens.accountFill),
            contentAlignment = Alignment.Center
        ) {
            TextLabel(
                text = label.take(1),
                size = 12.sp,
                weight = FontWeight.Bold,
                color = OverlayTokens.primaryText,
                maxLines = 1
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            TextLabel(
                text = label,
                size = 11.sp,
                weight = FontWeight.Bold,
                color = OverlayTokens.panelPrimaryText,
                maxLines = 1
            )
            TextLabel(
                text = subtitle,
                size = 9.sp,
                color = OverlayTokens.panelSecondaryText,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun ForwardModeRow(
    label: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(OverlayTokens.control),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Forward,
                contentDescription = null,
                tint = OverlayTokens.primaryText,
                modifier = Modifier.size(15.dp)
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            TextLabel(
                text = label,
                size = 11.sp,
                weight = FontWeight.Bold,
                color = OverlayTokens.panelPrimaryText,
                maxLines = 1
            )
            TextLabel(
                text = subtitle,
                size = 9.sp,
                color = OverlayTokens.panelSecondaryText,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun ChatHistoryDetailRow(line: String) {
    val separatorIndex = line.indexOf('：').takeIf { index -> index >= 0 } ?: line.indexOf(':')
    val author = separatorIndex
        .takeIf { index -> index >= 0 }
        ?.let { index -> line.take(index).ifBlank { "未知" } }
        ?: "未知"
    val body = separatorIndex
        .takeIf { index -> index >= 0 && index + 1 < line.length }
        ?.let { index -> line.substring(index + 1).trim() }
        ?: line
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(OverlayTokens.accountFill),
            contentAlignment = Alignment.Center
        ) {
            TextLabel(
                text = author.take(1),
                size = 12.sp,
                weight = FontWeight.Bold,
                color = OverlayTokens.primaryText,
                maxLines = 1
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            TextLabel(
                text = author,
                size = 10.sp,
                weight = FontWeight.Bold,
                color = OverlayTokens.panelSecondaryText,
                maxLines = 1
            )
            TextLabel(
                text = body,
                size = 12.sp,
                color = OverlayTokens.panelPrimaryText,
                lineHeight = 16.sp,
                maxLines = 4
            )
        }
    }
}

@Composable
private fun ForwardChoiceButton(label: String, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = {
            TextLabel(
                text = label,
                size = 9.sp,
                weight = FontWeight.SemiBold,
                color = OverlayTokens.panelPrimaryText,
                maxLines = 1,
                textAlign = TextAlign.Center
            )
        },
        modifier = Modifier
            .height(28.dp)
            .widthIn(min = 42.dp, max = 74.dp),
        shape = RoundedCornerShape(14.dp),
        colors = AssistChipDefaults.assistChipColors(
            containerColor = OverlayTokens.control,
            labelColor = OverlayTokens.panelPrimaryText
        ),
        border = BorderStroke(1.dp, OverlayTokens.hairline)
    )
}
