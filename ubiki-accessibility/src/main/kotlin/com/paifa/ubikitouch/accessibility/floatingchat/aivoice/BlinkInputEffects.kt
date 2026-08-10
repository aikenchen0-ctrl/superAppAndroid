package com.paifa.ubikitouch.accessibility.floatingchat.aivoice

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import com.paifa.ubikitouch.accessibility.FloatingChatBlinkInputAiAction
import com.paifa.ubikitouch.accessibility.FloatingChatBlinkInputStatusPhase
import com.paifa.ubikitouch.accessibility.FloatingChatBlinkVoiceBridge
import com.paifa.ubikitouch.accessibility.blinkVoiceInputActionFor
import com.paifa.ubikitouch.accessibility.blinkVoiceInputStatusAutoDismissMs
import com.paifa.ubikitouch.accessibility.blinkVoiceInputStatusMessageFor
import com.paifa.ubikitouch.accessibility.floatingchat.shell.FloatingChatOverlayRuntimeState
import kotlinx.coroutines.delay

@Composable
internal fun FloatingChatBlinkInputEffects(
    runtimeState: FloatingChatOverlayRuntimeState,
    inputFocused: Boolean,
    inputText: String,
    blinkInputAiBusy: Boolean,
    aiPredicting: Boolean,
    blinkInputStatusText: String?,
    blinkInputStatusAutoDismiss: Boolean,
    blinkInputStatusVersion: Int,
    onShowBlinkInputStatus: (String, Boolean) -> Unit,
    onRunBlinkInputAiAction: (FloatingChatBlinkInputAiAction, String) -> Unit,
    onSendRecognizedText: (String) -> Unit,
    onClearBlinkInputStatus: () -> Unit
) {
    LaunchedEffect(runtimeState.blinkVoiceResultEvent) {
        val event = runtimeState.blinkVoiceResultEvent ?: return@LaunchedEffect
        if (event.headless) {
            if (inputFocused) {
                val action = blinkVoiceInputActionFor(
                    eventType = event.eventType,
                    inputText = inputText
                )
                onShowBlinkInputStatus(
                    blinkVoiceInputStatusMessageFor(
                        eventType = event.eventType,
                        action = action,
                        phase = FloatingChatBlinkInputStatusPhase.Recognized
                    ),
                    action == FloatingChatBlinkInputAiAction.None || blinkInputAiBusy || aiPredicting
                )
                onRunBlinkInputAiAction(action, event.eventType)
            }
        } else if (blinkVoiceRecognitionAutoSendsChatMessage()) {
            onSendRecognizedText(blinkVoiceResultMessageText(event.eventType, event.durationMs))
        }
        runtimeState.clearBlinkVoiceResultEvent(event.token)
    }

    LaunchedEffect(inputFocused) {
        if (inputFocused) {
            FloatingChatBlinkVoiceBridge.requestHeadlessCapture()
        } else {
            onClearBlinkInputStatus()
            FloatingChatBlinkVoiceBridge.stopHeadlessCapture()
        }
    }

    LaunchedEffect(blinkInputStatusVersion) {
        val version = blinkInputStatusVersion
        if (blinkInputStatusText != null && blinkInputStatusAutoDismiss) {
            delay(blinkVoiceInputStatusAutoDismissMs().toLong())
            if (version == blinkInputStatusVersion) {
                onClearBlinkInputStatus()
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            FloatingChatBlinkVoiceBridge.stopHeadlessCapture()
        }
    }
}
