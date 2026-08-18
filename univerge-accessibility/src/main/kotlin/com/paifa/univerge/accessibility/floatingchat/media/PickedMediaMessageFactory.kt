package com.paifa.univerge.accessibility.floatingchat.media

import com.paifa.univerge.accessibility.floatingchat.chat.ChatThreadSelection
import com.paifa.univerge.accessibility.floatingchat.chat.toPrototypeToolSelection
import com.paifa.univerge.accessibility.floatingchat.shell.FloatingChatPickedDocumentEvent
import com.paifa.univerge.accessibility.floatingchat.shell.FloatingChatPickedMediaEvent
import com.paifa.univerge.core.model.FloatingChatConversation
import com.paifa.univerge.core.model.FloatingChatMessage
import com.paifa.univerge.core.model.FloatingChatPrototype

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
