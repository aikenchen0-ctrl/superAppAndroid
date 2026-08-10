package com.paifa.ubikitouch.accessibility.floatingchat.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Shared close affordance for transient floating-chat dialogs and panels. */
@Composable
internal fun FloatingDialogCloseButton(
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onClose,
        modifier = modifier.size(40.dp),
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = Color(0xFFFFE8E6),
            contentColor = Color(0xFFC93632)
        )
    ) {
        Icon(
            imageVector = Icons.Filled.Close,
            contentDescription = "关闭对话框",
            modifier = Modifier.size(22.dp)
        )
    }
}
