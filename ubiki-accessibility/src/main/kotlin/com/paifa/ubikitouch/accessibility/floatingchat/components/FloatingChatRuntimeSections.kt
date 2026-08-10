package com.paifa.ubikitouch.accessibility.floatingchat.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal fun FloatingChatRuntimeSections(
    modifier: Modifier,
    mainContent: @Composable BoxScope.() -> Unit,
    panelContent: @Composable BoxScope.() -> Unit,
    overlayContent: @Composable BoxScope.() -> Unit
) {
    Box(modifier = modifier) {
        mainContent()
        panelContent()
        overlayContent()
    }
}
