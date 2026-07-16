package com.paifa.ubikitouch.accessibility.floatingchat.media

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import com.paifa.ubikitouch.core.model.FloatingChatMessage
import com.paifa.ubikitouch.accessibility.OverlayTokens
import com.paifa.ubikitouch.accessibility.TextLabel

@Composable
internal fun FloatingChatMediaActionSheetHeader(message: FloatingChatMessage) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        androidx.compose.foundation.layout.Box(Modifier.size(46.dp).clip(RoundedCornerShape(8.dp)).background(OverlayTokens.imageBase).border(1.dp, OverlayTokens.resourcePanelBorder, RoundedCornerShape(8.dp))) {
            ImageThumbnailSurface(message, Modifier.fillMaxSize())
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            TextLabel(message.senderName, 11.sp, color = OverlayTokens.mediaSheetText, weight = FontWeight.Bold, maxLines = 1)
            TextLabel(mediaWatermarkText(message.resourceUrl, message.thumbnailUrl).ifBlank { "本地图片" }, 8.sp, color = OverlayTokens.mediaSheetMutedText, maxLines = 1)
            VisibilityAccessStrip(message.visibility, message.accessState)
        }
    }
}
