package com.paifa.ubikitouch.accessibility.floatingchat.media

import com.paifa.ubikitouch.accessibility.floatingchat.chat.ChatThreadSelection
import com.paifa.ubikitouch.accessibility.floatingchat.chat.toPrototypeToolSelection
import com.paifa.ubikitouch.accessibility.floatingchat.shell.FloatingChatPickedDocumentEvent
import com.paifa.ubikitouch.accessibility.floatingchat.shell.FloatingChatPickedMediaEvent
import com.paifa.ubikitouch.core.model.FloatingChatConversation
import com.paifa.ubikitouch.core.model.FloatingChatMessage
import com.paifa.ubikitouch.core.model.FloatingChatPrototype

internal fun pickedMediaMessageForEvent(
    event: FloatingChatPickedMediaEvent,
    conversation: FloatingChatConversation,
    selection: ChatThreadSelection,
    accountId: String,
    sequence: Int
): FloatingChatMessage {
    return FloatingChatPrototype.simulatedPickedMediaMessage(
        conversation = conversation,
        mediaKind = event.mediaKind,
        mediaUri = event.mediaUri,
        previewUri = event.previewUri,
        orientation = event.orientation,
        aspectRatio = event.aspectRatio,
        selection = selection.toPrototypeToolSelection(),
        accountId = accountId,
        sequence = sequence
    )
}

internal fun pickedDocumentMessageForEvent(
    event: FloatingChatPickedDocumentEvent,
    conversation: FloatingChatConversation,
    selection: ChatThreadSelection,
    accountId: String,
    sequence: Int
): FloatingChatMessage {
    return FloatingChatPrototype.pickedDocumentMessage(
        conversation = conversation,
        documentUri = event.document.uri,
        displayName = event.document.displayName,
        fileFormat = event.document.fileFormat,
        fileSizeLabel = event.document.fileSizeLabel,
        previewLines = event.document.previewLines,
        mimeType = event.document.mimeType,
        selection = selection.toPrototypeToolSelection(),
        accountId = accountId,
        sequence = sequence
    )
}
