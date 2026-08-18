package com.paifa.univerge.accessibility.floatingchat.media

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.paifa.univerge.core.model.FloatingChatAccessState
import com.paifa.univerge.core.model.FloatingChatVisibilityScope
import com.paifa.univerge.accessibility.floatingchat.theme.OverlayTokens
import com.paifa.univerge.accessibility.floatingchat.components.FloatingChatTinyChip
import com.paifa.univerge.accessibility.floatingchat.message.accessStateColor

@Composable
internal fun VisibilityAccessStrip(visibility: FloatingChatVisibilityScope?, accessState: FloatingChatAccessState?, modifier: Modifier = Modifier) {
    if (visibility == null && accessState == null) return
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        visibility?.let { FloatingChatTinyChip(it.label, OverlayTokens.cardSecondaryText) }
        accessState?.let { FloatingChatTinyChip(it.label, accessStateColor(it)) }
    }
}
