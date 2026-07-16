package com.paifa.ubikitouch.accessibility.floatingchat.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paifa.ubikitouch.accessibility.OverlayTokens
import com.paifa.ubikitouch.accessibility.TextLabel

@Composable
internal fun FloatingChatImageActionPill(label: String, onClick: (() -> Unit)? = null) {
    Button(
        onClick = { onClick?.invoke() },
        enabled = onClick != null,
        modifier = Modifier.width(32.dp).height(20.dp),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = OverlayTokens.imageAction,
            contentColor = OverlayTokens.primaryText,
            disabledContainerColor = OverlayTokens.imageAction,
            disabledContentColor = OverlayTokens.primaryText
        ),
        border = BorderStroke(1.dp, OverlayTokens.hairline),
        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp)
    ) {
        TextLabel(label, 8.sp, color = OverlayTokens.primaryText, weight = FontWeight.Bold, maxLines = 1, textAlign = TextAlign.Center)
    }
}
