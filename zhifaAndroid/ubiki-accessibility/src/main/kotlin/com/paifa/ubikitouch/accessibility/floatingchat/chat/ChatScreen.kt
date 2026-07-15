package com.paifa.ubikitouch.accessibility.floatingchat.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.paifa.ubikitouch.accessibility.floatingchat.components.OverlayStatus
import com.paifa.ubikitouch.accessibility.floatingchat.components.OverlayTextField
import com.paifa.ubikitouch.accessibility.floatingchat.contract.ChatUiEvent
import com.paifa.ubikitouch.accessibility.floatingchat.contract.ChatUiState
import com.paifa.ubikitouch.accessibility.floatingchat.contract.OverlayStatusProps
import com.paifa.ubikitouch.accessibility.floatingchat.contract.OverlayTextFieldEvent
import com.paifa.ubikitouch.accessibility.floatingchat.contract.OverlayTextFieldProps
import com.paifa.ubikitouch.accessibility.floatingchat.contract.StatusTone
import com.paifa.ubikitouch.accessibility.floatingchat.theme.FloatingChatThemeValues

@Composable
internal fun ChatScreen(
    state: ChatUiState,
    onEvent: (ChatUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("floating-chat-chat-screen"),
        verticalArrangement = Arrangement.spacedBy(FloatingChatThemeValues.dimensions.itemSpacing)
    ) {
        state.errorMessage?.let { message ->
            OverlayStatus(
                OverlayStatusProps(
                    message = message,
                    tone = StatusTone.Error
                )
            )
        }
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .testTag("floating-chat-message-list")
        ) {
            items(
                items = state.conversation.messages,
                key = { message -> message.id }
            ) { message ->
                Text(
                    text = message.text,
                    color = FloatingChatThemeValues.colors.primaryText,
                    style = FloatingChatThemeValues.typography.body
                )
            }
        }
        OverlayTextField(
            props = OverlayTextFieldProps(
                value = state.inputText,
                placeholder = "输入消息"
            ),
            onEvent = { event ->
                when (event) {
                    is OverlayTextFieldEvent.ValueChanged -> onEvent(ChatUiEvent.InputChanged(event.value))
                    OverlayTextFieldEvent.Submitted -> onEvent(ChatUiEvent.SendClicked)
                }
            },
            modifier = Modifier.testTag("chat-input")
        )
    }
}
