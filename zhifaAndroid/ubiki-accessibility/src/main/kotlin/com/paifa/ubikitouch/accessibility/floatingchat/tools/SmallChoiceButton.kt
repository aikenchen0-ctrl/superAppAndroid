package com.paifa.ubikitouch.accessibility.floatingchat.tools

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paifa.ubikitouch.accessibility.floatingchat.components.TextLabel
import com.paifa.ubikitouch.accessibility.floatingchat.theme.OverlayTokens

@Composable
internal fun SmallChoiceButton(label: String, onClick: () -> Unit) {
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
