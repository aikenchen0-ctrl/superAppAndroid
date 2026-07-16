package com.paifa.ubikitouch.accessibility.floatingchat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paifa.ubikitouch.accessibility.OverlayTokens
import com.paifa.ubikitouch.accessibility.TextLabel

@Composable
internal fun FloatingChatTinyChip(label: String, color: Color) {
    Box(Modifier.clip(RoundedCornerShape(4.dp)).background(OverlayTokens.permissionChip).border(1.dp, OverlayTokens.permissionChipBorder, RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 2.dp)) {
        TextLabel(label, 8.sp, color = color, weight = FontWeight.Bold, maxLines = 1, shadow = OverlayTokens.imModuleTextShadow)
    }
}
