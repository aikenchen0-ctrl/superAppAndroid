package com.paifa.ubikitouch.accessibility.floatingchat.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal fun FloatingChatRuntimeSections(
    modifier: Modifier,
    topContent: @Composable () -> Unit = {},
    mainContent: @Composable BoxScope.() -> Unit,
    bottomContent: @Composable () -> Unit = {},
    panelContent: @Composable BoxScope.() -> Unit,
    overlayContent: @Composable BoxScope.() -> Unit
) {
    Box(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            topContent()
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                mainContent()
            }
            bottomContent()
        }
        panelContent()
        overlayContent()
    }
}

internal fun floatingChatWorkspaceUsesVerticalSlots(): Boolean = true

internal fun floatingChatWorkspaceBodyUsesWeight(): Boolean = true

internal fun floatingChatWorkspaceHandlesInsetsAtBottomBoundary(): Boolean = true
