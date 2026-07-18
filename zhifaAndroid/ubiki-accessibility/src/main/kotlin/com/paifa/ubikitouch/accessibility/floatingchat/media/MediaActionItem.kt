package com.paifa.ubikitouch.accessibility.floatingchat.media

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paifa.ubikitouch.accessibility.floatingchat.components.CompactInteractiveSize
import com.paifa.ubikitouch.accessibility.floatingchat.theme.OverlayTokens
import com.paifa.ubikitouch.accessibility.floatingchat.components.TextLabel

@Composable
internal fun FloatingChatMediaActionItem(label: String, onClick: () -> Unit, iconContent: @Composable () -> Unit) {
    Column(Modifier.width(50.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        CompactInteractiveSize {
            FilledTonalIconButton(onClick = onClick, modifier = Modifier.size(34.dp).border(1.dp, OverlayTokens.mediaSheetIconBorder, RoundedCornerShape(8.dp)), shape = RoundedCornerShape(8.dp), colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = OverlayTokens.mediaSheetIconBox, contentColor = OverlayTokens.mediaSheetIcon)) {
                iconContent()
            }
        }
        TextLabel(label, 8.sp, color = OverlayTokens.mediaSheetText, maxLines = 2, textAlign = TextAlign.Center, lineHeight = 10.sp)
    }
}
