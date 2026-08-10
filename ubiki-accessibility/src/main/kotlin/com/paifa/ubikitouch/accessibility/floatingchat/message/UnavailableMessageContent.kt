package com.paifa.ubikitouch.accessibility.floatingchat.message

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paifa.ubikitouch.accessibility.floatingchat.components.TextLabel
import com.paifa.ubikitouch.accessibility.floatingchat.theme.OverlayTokens

@Composable
internal fun UnavailableMessageContent(state: MessageUnavailableState) {
    Row(
        modifier = Modifier
            .widthIn(min = 164.dp, max = 248.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(OverlayTokens.unavailableMessageCard)
            .border(1.dp, OverlayTokens.unavailableMessageCardBorder, RoundedCornerShape(7.dp))
            .padding(horizontal = 9.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(OverlayTokens.unavailableMessageIconBackground),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = null,
                tint = OverlayTokens.unavailableMessageIcon,
                modifier = Modifier.size(15.dp)
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            TextLabel(
                text = unavailableMessageTitle(state),
                size = 11.sp,
                color = OverlayTokens.unavailableMessageTitle,
                maxLines = 1
            )
            TextLabel(
                text = unavailableMessageDescription(state),
                size = 9.sp,
                color = OverlayTokens.unavailableMessageDescription,
                maxLines = 1
            )
        }
    }
}

internal fun unavailableMessageTitle(state: MessageUnavailableState): String {
    return when (state) {
        MessageUnavailableState.ContentUnavailable -> "消息暂不可查看"
        MessageUnavailableState.AccessPending -> "等待访问授权"
        MessageUnavailableState.MediaExpired -> "媒体已失效"
    }
}

internal fun unavailableMessageDescription(state: MessageUnavailableState): String {
    return when (state) {
        MessageUnavailableState.ContentUnavailable -> "内容暂未同步到当前设备"
        MessageUnavailableState.AccessPending -> "获得授权后可查看内容"
        MessageUnavailableState.MediaExpired -> "原始文件已无法加载"
    }
}

internal fun unavailableMessageCanPreview(state: MessageUnavailableState): Boolean = false

internal fun unavailableMessageUsesCompactCard(): Boolean = true
