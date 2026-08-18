package com.paifa.univerge.accessibility.floatingchat.input

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.paifa.univerge.accessibility.floatingchat.components.CompactInteractiveSize
import com.paifa.univerge.accessibility.floatingchat.shell.BottomPanelMode

internal fun moreInputButtonNextPanelMode(currentMode: BottomPanelMode): BottomPanelMode {
    return if (currentMode == BottomPanelMode.More) BottomPanelMode.None else BottomPanelMode.More
}

@Composable
internal fun MoreInputButton(
    active: Boolean,
    onClick: () -> Unit
) {
    val iconTint = MaterialTheme.colorScheme.onSurface
    CompactInteractiveSize {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(BottomInputIconButtonSizeDp.dp),
            colors = IconButtonDefaults.iconButtonColors(contentColor = iconTint)
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = "\u66f4\u591a\u5de5\u5177",
                tint = iconTint,
                modifier = Modifier.size(BottomInputIconSizeDp.dp)
            )
        }
    }
}
