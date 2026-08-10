package com.paifa.ubikitouch.accessibility.floatingchat.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.paifa.ubikitouch.accessibility.floatingchat.chat.ChatScreen
import com.paifa.ubikitouch.accessibility.floatingchat.components.OverlayStatus
import com.paifa.ubikitouch.accessibility.floatingchat.contract.FloatingChatPanel
import com.paifa.ubikitouch.accessibility.floatingchat.contract.FloatingChatShellEvent
import com.paifa.ubikitouch.accessibility.floatingchat.contract.FloatingChatShellState
import com.paifa.ubikitouch.accessibility.floatingchat.contract.OverlayStatusProps
import com.paifa.ubikitouch.accessibility.floatingchat.theme.FloatingChatTheme

@Composable
internal fun FloatingChatShell(
    state: FloatingChatShellState,
    onEvent: (FloatingChatShellEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    FloatingChatTheme {
        Box(
            modifier = modifier
                .fillMaxSize()
                .testTag("floating-chat-root")
        ) {
            when (state.activePanel) {
                FloatingChatPanel.Chat -> ChatScreen(
                    state = state.chat,
                    onEvent = { event -> onEvent(FloatingChatShellEvent.Chat(event)) }
                )
                else -> OverlayStatus(
                    OverlayStatusProps(message = state.activePanel.name)
                )
            }
        }
    }
}
