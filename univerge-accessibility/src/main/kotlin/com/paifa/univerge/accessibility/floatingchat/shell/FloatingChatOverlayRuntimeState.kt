package com.paifa.univerge.accessibility.floatingchat.shell

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.paifa.univerge.accessibility.floatingchat.chat.ChatThreadSelection
import com.paifa.univerge.accessibility.floatingchat.chat.ChatNavigationState
import com.paifa.univerge.accessibility.floatingchat.chat.syncChatNavigationState
import com.paifa.univerge.accessibility.FloatingChatMediaPreviewSession
import com.paifa.univerge.accessibility.FloatingChatMediaTarget
import com.paifa.univerge.accessibility.FloatingChatPickedDocument
import com.paifa.univerge.core.model.FloatingChatConversation
import com.paifa.univerge.core.model.FloatingChatMessage
import com.paifa.univerge.core.model.FloatingChatMessageType
import com.paifa.univerge.core.model.FloatingChatPrototype
import com.paifa.univerge.core.model.FloatingChatThumbnailOrientation
import java.util.concurrent.ConcurrentHashMap

internal class FloatingChatOverlayRuntimeState {
    private val voiceTranscriptionTaskIds =
        ConcurrentHashMap<VoiceTranscriptionTaskKey, Long>()
    private val messageRevokeTaskIds =
        ConcurrentHashMap<MessageRevokeTaskKey, Long>()

    var previewVisible by mutableStateOf(false)
    var mediaActionSheetVisible by mutableStateOf(false)
    var dismissSignal by mutableStateOf(0L)
    var workspaceRequest by mutableStateOf<FloatingChatWorkspaceRequest?>(null)
    var chatNavigationState by mutableStateOf(ChatNavigationState())
    var selectedThread: ChatThreadSelection
        get() = chatNavigationState.selectedThread
        set(value) {
            chatNavigationState = chatNavigationState.copy(selectedThread = value)
        }
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

    /**
     * 将外部入口切换请求交回已挂载的聊天根视图，避免通过 Activity 或第二个 Window 附加页面。
     * 测试流程：从 OpenAPI、智能抠图等入口触发后，确认同一悬浮聊天根显示对应全屏工作区。
     */
    fun requestWorkspace(mode: BottomPanelMode) {
        val nextToken = (workspaceRequest?.token ?: 0L) + 1L
        workspaceRequest = FloatingChatWorkspaceRequest(token = nextToken, mode = mode)
    }

    fun clearWorkspaceRequest(token: Long) {
        if (workspaceRequest?.token == token) {
            workspaceRequest = null
        }
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
        chatNavigationState = syncChatNavigationState(
            current = chatNavigationState,
            conversation = conversation,
            controllerAccountId = selectedAccountId,
            controllerThread = selectedThread
        )
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

    /**
     * 保存已受理的语音转写 taskId，使全屏浮层重建后只续查任务而不会重复提交写操作。
     * 测试流程：发起转写后收起并重新展开聊天，再次点击同一语音消息并选择“转文字”。
     */
    fun rememberVoiceTranscriptionTask(accountId: String, remoteMessageId: Long, taskId: Long) {
        require(accountId.isNotBlank()) { "accountId 不能为空" }
        require(remoteMessageId > 0L) { "remoteMessageId 必须大于 0" }
        require(taskId > 0L) { "taskId 必须大于 0" }
        voiceTranscriptionTaskIds[VoiceTranscriptionTaskKey(accountId, remoteMessageId)] = taskId
    }

    fun voiceTranscriptionTaskId(accountId: String, remoteMessageId: Long): Long? {
        require(accountId.isNotBlank()) { "accountId 不能为空" }
        require(remoteMessageId > 0L) { "remoteMessageId 必须大于 0" }
        return voiceTranscriptionTaskIds[VoiceTranscriptionTaskKey(accountId, remoteMessageId)]
    }

    fun clearVoiceTranscriptionTask(accountId: String, remoteMessageId: Long) {
        require(accountId.isNotBlank()) { "accountId 不能为空" }
        require(remoteMessageId > 0L) { "remoteMessageId 必须大于 0" }
        voiceTranscriptionTaskIds.remove(VoiceTranscriptionTaskKey(accountId, remoteMessageId))
    }

    fun rememberMessageRevokeTask(accountId: String, remoteMessageId: Long, taskId: Long) {
        require(accountId.isNotBlank()) { "accountId 不能为空" }
        require(remoteMessageId > 0L) { "remoteMessageId 必须大于 0" }
        require(taskId > 0L) { "taskId 必须大于 0" }
        messageRevokeTaskIds[MessageRevokeTaskKey(accountId, remoteMessageId)] = taskId
    }

    fun messageRevokeTaskId(accountId: String, remoteMessageId: Long): Long? {
        require(accountId.isNotBlank()) { "accountId 不能为空" }
        require(remoteMessageId > 0L) { "remoteMessageId 必须大于 0" }
        return messageRevokeTaskIds[MessageRevokeTaskKey(accountId, remoteMessageId)]
    }

    fun clearMessageRevokeTask(accountId: String, remoteMessageId: Long) {
        require(accountId.isNotBlank()) { "accountId 不能为空" }
        require(remoteMessageId > 0L) { "remoteMessageId 必须大于 0" }
        messageRevokeTaskIds.remove(MessageRevokeTaskKey(accountId, remoteMessageId))
    }

    fun openMediaPreview(
        mediaMessages: List<FloatingChatMessage>,
        initialIndex: Int,
        accountId: String = ""
    ) {
        if (mediaMessages.isEmpty()) return
        documentPreviewMessage = null
        previewSession = FloatingChatMediaPreviewSession(
            mediaMessages = mediaMessages,
            initialIndex = initialIndex.coerceIn(0, mediaMessages.lastIndex.coerceAtLeast(0)),
            accountId = accountId
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

private data class VoiceTranscriptionTaskKey(
    val accountId: String,
    val remoteMessageId: Long
)

private data class MessageRevokeTaskKey(
    val accountId: String,
    val remoteMessageId: Long
)

/** 单次工作区请求使用 token，避免 Compose 重组重复打开已消费的页面。 */
internal data class FloatingChatWorkspaceRequest(
    val token: Long,
    val mode: BottomPanelMode
)

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
