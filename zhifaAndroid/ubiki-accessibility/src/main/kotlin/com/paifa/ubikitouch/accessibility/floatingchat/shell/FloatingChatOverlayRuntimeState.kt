package com.paifa.ubikitouch.accessibility.floatingchat.shell

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.paifa.ubikitouch.accessibility.floatingchat.chat.ChatThreadSelection
import com.paifa.ubikitouch.accessibility.FloatingChatMediaPreviewSession
import com.paifa.ubikitouch.accessibility.FloatingChatMediaTarget
import com.paifa.ubikitouch.accessibility.FloatingChatPickedDocument
import com.paifa.ubikitouch.core.model.FloatingChatConversation
import com.paifa.ubikitouch.core.model.FloatingChatMessage
import com.paifa.ubikitouch.core.model.FloatingChatMessageType
import com.paifa.ubikitouch.core.model.FloatingChatPrototype
import com.paifa.ubikitouch.core.model.FloatingChatThumbnailOrientation
internal class FloatingChatOverlayRuntimeState {
    var previewVisible by mutableStateOf(false)
    var mediaActionSheetVisible by mutableStateOf(false)
    var dismissSignal by mutableStateOf(0L)
    var selectedThread by mutableStateOf<ChatThreadSelection>(ChatThreadSelection.Group)
    var pickedMediaEvent by mutableStateOf<FloatingChatPickedMediaEvent?>(null)
    var pickedDocumentEvent by mutableStateOf<FloatingChatPickedDocumentEvent?>(null)
    var blinkVoiceResultEvent by mutableStateOf<FloatingChatBlinkVoiceResultEvent?>(null)
    var conversationUpdateEvent by mutableStateOf<FloatingChatConversationUpdateEvent?>(null)
    var localMessagesUpdateEvent by mutableStateOf<FloatingChatLocalMessagesUpdateEvent?>(null)
    var previewSession by mutableStateOf<FloatingChatMediaPreviewSession?>(null)
    var documentPreviewMessage by mutableStateOf<FloatingChatMessage?>(null)

    fun canHandleBack(): Boolean {
        return previewSession != null || documentPreviewMessage != null || mediaActionSheetVisible
    }

    fun requestDismiss() {
        dismissSignal += 1L
    }

    fun deliverPickedMedia(
        mediaKind: FloatingChatPrototype.PickedMediaKind,
        mediaUri: String,
        previewUri: String,
        orientation: FloatingChatThumbnailOrientation,
        aspectRatio: Float?,
        target: FloatingChatMediaTarget = FloatingChatMediaTarget.Chat
    ) {
        val nextToken = (pickedMediaEvent?.token ?: 0L) + 1L
        pickedMediaEvent = FloatingChatPickedMediaEvent(
            token = nextToken,
            mediaKind = mediaKind,
            mediaUri = mediaUri,
            previewUri = previewUri,
            orientation = orientation,
            aspectRatio = aspectRatio,
            target = target
        )
    }

    fun clearPickedMediaEvent(token: Long) {
        if (pickedMediaEvent?.token == token) {
            pickedMediaEvent = null
        }
    }

    fun deliverPickedDocument(document: FloatingChatPickedDocument) {
        val nextToken = (pickedDocumentEvent?.token ?: 0L) + 1L
        pickedDocumentEvent = FloatingChatPickedDocumentEvent(
            token = nextToken,
            document = document
        )
    }

    fun clearPickedDocumentEvent(token: Long) {
        if (pickedDocumentEvent?.token == token) {
            pickedDocumentEvent = null
        }
    }

    fun deliverBlinkVoiceResult(
        eventType: String,
        durationMs: Long,
        confidence: Float,
        headless: Boolean = false
    ) {
        val nextToken = (blinkVoiceResultEvent?.token ?: 0L) + 1L
        blinkVoiceResultEvent = FloatingChatBlinkVoiceResultEvent(
            token = nextToken,
            eventType = eventType,
            durationMs = durationMs,
            confidence = confidence,
            headless = headless
        )
    }

    fun clearBlinkVoiceResultEvent(token: Long) {
        if (blinkVoiceResultEvent?.token == token) {
            blinkVoiceResultEvent = null
        }
    }

    fun deliverConversationUpdate(
        conversation: FloatingChatConversation,
        selectedAccountId: String,
        selectedThread: ChatThreadSelection
    ) {
        val nextToken = (conversationUpdateEvent?.token ?: 0L) + 1L
        this.selectedThread = selectedThread
        conversationUpdateEvent = FloatingChatConversationUpdateEvent(
            token = nextToken,
            conversation = conversation,
            selectedAccountId = selectedAccountId,
            selectedThread = selectedThread
        )
    }

    fun clearConversationUpdate(token: Long) {
        if (conversationUpdateEvent?.token == token) {
            conversationUpdateEvent = null
        }
    }

    fun deliverLocalMessagesUpdate(
        messages: List<FloatingChatMessage>,
        messageSequence: Int
    ) {
        val nextToken = (localMessagesUpdateEvent?.token ?: 0L) + 1L
        localMessagesUpdateEvent = FloatingChatLocalMessagesUpdateEvent(
            token = nextToken,
            messages = messages,
            messageSequence = messageSequence
        )
    }

    fun clearLocalMessagesUpdate(token: Long) {
        if (localMessagesUpdateEvent?.token == token) {
            localMessagesUpdateEvent = null
        }
    }

    fun openMediaPreview(
        mediaMessages: List<FloatingChatMessage>,
        initialIndex: Int
    ) {
        if (mediaMessages.isEmpty()) return
        documentPreviewMessage = null
        previewSession = FloatingChatMediaPreviewSession(
            mediaMessages = mediaMessages,
            initialIndex = initialIndex.coerceIn(0, mediaMessages.lastIndex.coerceAtLeast(0))
        )
    }

    fun closeMediaPreview() {
        previewSession = null
        previewVisible = false
    }

    fun openDocumentPreview(message: FloatingChatMessage) {
        if (message.type != FloatingChatMessageType.FilePreview) return
        previewSession = null
        mediaActionSheetVisible = false
        documentPreviewMessage = message
    }

    fun closeDocumentPreview() {
        documentPreviewMessage = null
        previewVisible = false
    }
}

internal data class FloatingChatPickedMediaEvent(
    val token: Long,
    val mediaKind: FloatingChatPrototype.PickedMediaKind,
    val mediaUri: String,
    val previewUri: String,
    val orientation: FloatingChatThumbnailOrientation,
    val aspectRatio: Float?,
    val target: FloatingChatMediaTarget = FloatingChatMediaTarget.Chat
)

internal data class FloatingChatPickedDocumentEvent(
    val token: Long,
    val document: FloatingChatPickedDocument
)

internal data class FloatingChatBlinkVoiceResultEvent(
    val token: Long,
    val eventType: String,
    val durationMs: Long,
    val confidence: Float,
    val headless: Boolean = false
)

internal data class FloatingChatConversationUpdateEvent(
    val token: Long,
    val conversation: FloatingChatConversation,
    val selectedAccountId: String,
    val selectedThread: ChatThreadSelection
)

internal data class FloatingChatLocalMessagesUpdateEvent(
    val token: Long,
    val messages: List<FloatingChatMessage>,
    val messageSequence: Int
)
