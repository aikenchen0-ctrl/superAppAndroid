package com.paifa.ubikitouch.accessibility.floatingchat.media

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.paifa.ubikitouch.core.model.FloatingChatAccessState
import com.paifa.ubikitouch.core.model.FloatingChatVisibilityScope
import com.paifa.ubikitouch.accessibility.OverlayTokens
import com.paifa.ubikitouch.accessibility.accessStateColor
import com.paifa.ubikitouch.accessibility.floatingchat.components.FloatingChatTinyChip

@Composable
internal fun VisibilityAccessStrip(visibility: FloatingChatVisibilityScope?, accessState: FloatingChatAccessState?, modifier: Modifier = Modifier) {
    if (visibility == null && accessState == null) return
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        visibility?.let { FloatingChatTinyChip(it.label, OverlayTokens.cardSecondaryText) }
        accessState?.let { FloatingChatTinyChip(it.label, accessStateColor(it)) }
    }
}
