package com.paifa.ubikitouch.accessibility

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import com.paifa.ubikitouch.core.model.FloatingChatMessage
import com.paifa.ubikitouch.core.model.FloatingChatMessageType
import com.paifa.ubikitouch.core.model.FloatingChatSendState
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.paifa.ubikitouch.core.gesture.BackGestureProgress
import com.paifa.ubikitouch.core.model.EdgeSide
import com.paifa.ubikitouch.core.model.FloatingChatFileFormat
import com.paifa.ubikitouch.core.model.FloatingChatPrototype
import com.paifa.ubikitouch.core.model.FloatingChatThumbnailOrientation
import com.paifa.ubikitouch.core.model.GestureData
import com.paifa.ubikitouch.core.model.GestureType
import com.paifa.ubikitouch.accessibility.data.FloatingChatMessageStore
import com.paifa.ubikitouch.accessibility.data.LocalContactProfile
import com.paifa.ubikitouch.accessibility.data.LocalGroupProfile
import com.paifa.ubikitouch.accessibility.data.ScrmOperationStore
import com.paifa.ubikitouch.accessibility.data.localThreadIdForSelection
import com.paifa.ubikitouch.accessibility.data.toLocalChatMessage
import com.paifa.ubikitouch.accessibility.scrm.ScrmAdminBootstrapResult
import com.paifa.ubikitouch.accessibility.scrm.ScrmAuthenticationException
import com.paifa.ubikitouch.accessibility.scrm.ScrmChatRoomQuery
import com.paifa.ubikitouch.accessibility.scrm.ScrmChatRoomMemberQuery
import com.paifa.ubikitouch.accessibility.scrm.ScrmContactQuery
import com.paifa.ubikitouch.accessibility.scrm.ScrmOperationProcessorResult
import com.paifa.ubikitouch.accessibility.scrm.ScrmOperationRunner
import com.paifa.ubikitouch.accessibility.scrm.ScrmSelectedSession
import com.paifa.ubikitouch.accessibility.scrm.ScrmSettingsManager
import com.paifa.ubikitouch.accessibility.scrm.ScrmTextMessageRoute
import com.paifa.ubikitouch.accessibility.scrm.ScrmFloatingAccountConversation
import com.paifa.ubikitouch.accessibility.scrm.ScrmFloatingAccountRoute
import com.paifa.ubikitouch.accessibility.scrm.ScrmContact
import com.paifa.ubikitouch.accessibility.scrm.ScrmChatRoom
import com.paifa.ubikitouch.accessibility.scrm.ScrmChatRoomMember
import com.paifa.ubikitouch.accessibility.scrm.ScrmDevice
import com.paifa.ubikitouch.accessibility.scrm.ScrmMessagePreflightFailure
import com.paifa.ubikitouch.accessibility.scrm.ScrmWechatAccount
import com.paifa.ubikitouch.accessibility.scrm.scrmFloatingChatConversation
import com.paifa.ubikitouch.accessibility.scrm.scrmFloatingAccountRouteForSelection
import com.paifa.ubikitouch.accessibility.scrm.scrmMessagePreflightFailure
import com.paifa.ubikitouch.accessibility.scrm.scrmMessageOperationType
import com.paifa.ubikitouch.accessibility.scrm.scrmOutboxItemForMessage
import com.paifa.ubikitouch.accessibility.scrm.scrmTextRouteForMessageThread
import com.paifa.ubikitouch.accessibility.scrm.withScrmFailureState
import com.paifa.ubikitouch.accessibility.scrm.withScrmQueueState
import java.util.concurrent.Executors
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

internal class FloatingChatOverlayController(
    private val context: Context,
    private val windowManager: WindowManager,
    private val onEdgeGesture: (EdgeSide, GestureType, GestureData) -> Unit = { _, _, _ -> },
    private val onBottomGesture: (BottomGestureBarGestureType, GestureData) -> Unit = { _, _ -> },
    private val onBackGestureProgress: (EdgeSide, BackGestureProgress) -> Unit = { _, _ -> },
    private val onBackGestureCommit: (EdgeSide, BackGestureProgress, GestureData) -> Boolean = { _, _, _ -> false },
    private val onBackGestureEnd: (EdgeSide, BackGestureProgress) -> Unit = { _, _ -> },
    private val onBackGestureCancel: () -> Unit = {},
    private val onExpandedChanged: (Boolean) -> Unit = {},
    private val onOverlayRecreated: () -> Unit = {}
) {
    private var composeView: ComposeView? = null
    private var composeOwner: AccessibilityOverlayComposeOwner? = null
    private val preferences = UbikiPreferences(context)
    private var state: FloatingChatOverlayState = FloatingChatOverlayState.Collapsed
    private var conversation = FloatingChatPrototype.sampleConversation()
    private val messageStore = FloatingChatMessageStore(context.applicationContext)
    private val scrmOperationStore = ScrmOperationStore(context.applicationContext)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scrmOperationRunner = ScrmOperationRunner(
        context = context.applicationContext,
        operationStore = scrmOperationStore,
        onProcessed = ::handleScrmOperationProcessed
    )
    private val scrmSettingsManager = ScrmSettingsManager(context.applicationContext)
    private val scrmConversationRefreshInFlight = AtomicBoolean(false)
    private val scrmConversationRefreshPending = AtomicBoolean(false)
    private val cachedScrmAccountConversations = ConcurrentHashMap<String, ScrmFloatingAccountConversation>()
    private var scheduledScrmConversationRefresh: Runnable? = null
    private var scheduledScrmBackgroundPrefetch: Runnable? = null
    private val runtimeState = FloatingChatOverlayRuntimeState()
    private val pendingScrmTextRoutes = ConcurrentHashMap<String, ScrmTextMessageRoute>()
    private var selectedThread: FloatingChatPrototype.ToolThreadSelection = defaultToolThreadSelection(conversation)
    private var selectedAccountId: String = FloatingChatPrototype.pairedAccountFor(
        conversation = conversation,
        contactId = defaultToolThreadContactId(conversation)
    ).id
    private val localMessages = mutableListOf<FloatingChatMessage>().apply {
        addAll(loadPersistedLocalMessages())
    }
    private val momentPosts = mutableListOf<AppMomentPost>().apply {
        addAll(loadPersistedMomentPosts())
    }
    private val contactProfiles = mutableListOf<LocalContactProfile>().apply {
        addAll(loadPersistedContactProfiles())
    }
    private val groupProfiles = mutableListOf<LocalGroupProfile>().apply {
        addAll(loadPersistedGroupProfiles())
    }
    private var localMessageSequence = nextLocalMessageSequence(localMessages)
    private var previewChromeVisible = false
    private var voicePermissionRequestToken = 0
    private var locationPermissionRequestToken = 0
    private val messageDatabaseExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "FloatingChatMessageDb").apply {
            isDaemon = true
        }
    }
    private val scrmConversationExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "FloatingChatScrmConversation").apply {
            isDaemon = true
        }
    }

    init {
        scrmOperationRunner.requestRun()
        refreshScrmConversationFromApi()
    }

    fun show() {
        showState(state)
    }

    fun refreshAppearance() {
        showState(state, force = true)
    }

    fun expand() {
        if (
            shouldRestoreHiddenExpandedChatView(
                floatingChatExpanded = state == FloatingChatOverlayState.Expanded,
                chatViewHidden = composeView?.visibility == View.GONE
            )
        ) {
            restoreAfterMediaPicker()
            return
        }
        refreshScrmConversationFromApi()
        showState(FloatingChatOverlayState.Expanded)
    }

    fun collapse() {
        if (!hideExpandedViewForCollapse()) {
            dismissView()
        }
        updateOverlayState(FloatingChatOverlayState.Collapsed)
    }

    fun dismissPreviewOrSheet(): Boolean {
        if (!runtimeState.canHandleBack()) return false
        runtimeState.requestDismiss()
        return true
    }

    fun toggle() {
        if (state == FloatingChatOverlayState.Expanded) {
            collapse()
        } else {
            expand()
        }
    }

    fun hideForMediaPreview() {
        composeView?.visibility = View.GONE
    }

    fun restoreAfterMediaPreview() {
        if (state == FloatingChatOverlayState.Expanded) {
            composeView?.visibility = View.VISIBLE
        }
    }

    fun restoreAfterExternalDocument() {
        restoreAfterMediaPicker()
    }

    private fun openExternalDocument(message: FloatingChatMessage): Boolean {
        val documentUri = message.resourceUrl?.takeIf { it.isNotBlank() } ?: return false
        hideForMediaPicker()
        val intent = Intent()
            .setClassName(
                context.packageName,
                "com.paifa.ubikitouch.app.FloatingChatDocumentViewerActivity"
            )
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
            .putExtra(EXTRA_EXTERNAL_DOCUMENT_URI, documentUri)
            .putExtra(EXTRA_EXTERNAL_DOCUMENT_MIME_TYPE, externalDocumentMimeType(message))
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrElse { error ->
            Log.w(TAG, "failed to open external document viewer bridge", error)
            restoreAfterExternalDocument()
            false
        }
    }

    fun hideForMediaPicker() {
        val view = composeView ?: return
        view.visibility = View.GONE
        val params = view.layoutParams as? WindowManager.LayoutParams ?: return
        params.flags = params.flags or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        runCatching {
            windowManager.updateViewLayout(view, params)
        }.onFailure {
            Log.w(TAG, "failed to hide floating chat overlay for media picker", it)
        }
    }

    fun restoreAfterMediaPicker() {
        if (state == FloatingChatOverlayState.Expanded) {
            val view = composeView ?: return
            view.visibility = View.VISIBLE
            val params = view.layoutParams as? WindowManager.LayoutParams ?: return
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
            applyExpandedChrome(
                view = view,
                params = params,
                previewVisible = previewChromeVisible
            )
            runCatching {
                windowManager.updateViewLayout(view, params)
            }.onFailure {
                Log.w(TAG, "failed to restore floating chat overlay after media picker", it)
            }
        }
    }

    fun hideForPermissionPrompt() {
        dismissView()
    }

    fun restoreAfterPermissionPrompt() {
        if (state == FloatingChatOverlayState.Expanded) {
            showState(FloatingChatOverlayState.Expanded, force = true)
        }
    }

    private fun defaultToolThreadSelection(
        conversation: com.paifa.ubikitouch.core.model.FloatingChatConversation
    ): FloatingChatPrototype.ToolThreadSelection {
        return conversation.groupContacts.firstOrNull()?.let { group ->
            FloatingChatPrototype.ToolThreadSelection.GroupChat(group.id)
        } ?: conversation.contacts.firstOrNull()?.let { contact ->
            FloatingChatPrototype.ToolThreadSelection.Private(contact.id)
        } ?: FloatingChatPrototype.ToolThreadSelection.Group
    }

    private fun defaultToolThreadContactId(
        conversation: com.paifa.ubikitouch.core.model.FloatingChatConversation
    ): String {
        return conversation.groupContacts.firstOrNull()?.id
            ?: conversation.contacts.firstOrNull()?.id
            ?: ""
    }

    fun addPickedMediaMessage(
        mediaKind: FloatingChatPrototype.PickedMediaKind,
        mediaUri: String,
        previewUri: String,
        orientation: FloatingChatThumbnailOrientation,
        aspectRatio: Float?,
        target: FloatingChatMediaTarget = FloatingChatMediaTarget.Chat
    ) {
        if (target == FloatingChatMediaTarget.Moment || target == FloatingChatMediaTarget.AccountAvatar) {
            if (state != FloatingChatOverlayState.Expanded) {
                showState(FloatingChatOverlayState.Expanded, force = true)
            }
            runtimeState.deliverPickedMedia(
                mediaKind = mediaKind,
                mediaUri = mediaUri,
                previewUri = previewUri,
                orientation = orientation,
                aspectRatio = aspectRatio,
                target = target
            )
            return
        }
        if (state == FloatingChatOverlayState.Expanded) {
            runtimeState.deliverPickedMedia(
                mediaKind = mediaKind,
                mediaUri = mediaUri,
                previewUri = previewUri,
                orientation = orientation,
                aspectRatio = aspectRatio,
                target = target
            )
            return
        }
        localMessageSequence += 1
        val message = FloatingChatPrototype.simulatedPickedMediaMessage(
            conversation = conversation,
            mediaKind = mediaKind,
            mediaUri = mediaUri,
            previewUri = previewUri,
            orientation = orientation,
            aspectRatio = aspectRatio,
            selection = selectedThread,
            accountId = selectedAccountId,
            sequence = localMessageSequence
        )
        localMessages += message
        persistLocalMessage(message, selectedThread.toLocalThreadId())
        showState(FloatingChatOverlayState.Expanded, force = true)
    }

    fun addPickedDocumentMessage(document: FloatingChatPickedDocument) {
        if (state == FloatingChatOverlayState.Expanded) {
            runtimeState.deliverPickedDocument(document)
            return
        }
        localMessageSequence += 1
        val baseMessage = FloatingChatPrototype.pickedDocumentMessage(
            conversation = conversation,
            documentUri = document.uri,
            displayName = document.displayName,
            fileFormat = document.fileFormat,
            fileSizeLabel = document.fileSizeLabel,
            previewLines = document.previewLines,
            mimeType = document.mimeType,
            selection = selectedThread,
            accountId = selectedAccountId,
            sequence = localMessageSequence
        )
        val threadId = selectedThread.toLocalThreadId()
        val message = prepareOutgoingMessageForScrm(baseMessage, threadId)
        localMessages += message
        persistLocalMessage(message, threadId)
        showState(FloatingChatOverlayState.Expanded, force = true)
    }

    fun addBlinkVoiceResult(
        eventType: String,
        durationMs: Long,
        confidence: Float,
        headless: Boolean = false
    ) {
        if (state != FloatingChatOverlayState.Expanded) {
            showState(FloatingChatOverlayState.Expanded, force = true)
        }
        runtimeState.deliverBlinkVoiceResult(
            eventType = eventType,
            durationMs = durationMs,
            confidence = confidence,
            headless = headless
        )
    }

    fun onVoicePermissionResult(granted: Boolean) {
        if (granted) {
            voicePermissionRequestToken += 1
        }
        if (state == FloatingChatOverlayState.Expanded) {
            showState(FloatingChatOverlayState.Expanded, force = true)
        }
    }

    fun onLocationPermissionResult(granted: Boolean) {
        if (granted) {
            locationPermissionRequestToken += 1
        }
        if (state == FloatingChatOverlayState.Expanded) {
            showState(FloatingChatOverlayState.Expanded, force = true)
        }
    }

    private fun showState(nextState: FloatingChatOverlayState, force: Boolean = false) {
        if (!force && composeView != null && state == nextState) {
            if (
                floatingChatShouldRefreshExpandedWindowZOrder(
                    force = force,
                    hasComposeView = true,
                    currentExpanded = state == FloatingChatOverlayState.Expanded,
                    nextExpanded = nextState == FloatingChatOverlayState.Expanded
                )
            ) {
                refreshExpandedWindowZOrder()
            }
            return
        }
        if (
            !force &&
            nextState == FloatingChatOverlayState.Expanded &&
            state == FloatingChatOverlayState.Collapsed &&
            composeView != null
        ) {
            if (restoreRetainedExpandedView()) {
                updateOverlayState(FloatingChatOverlayState.Expanded)
                return
            }
            dismissView()
        }
        dismissView()
        updateOverlayState(nextState)
        if (nextState == FloatingChatOverlayState.Collapsed) return
        val owner = AccessibilityOverlayComposeOwner()
        val view = ComposeView(context).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnLifecycleDestroyed(owner.lifecycle))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            if (mediaPreviewCoversSystemBars()) {
                systemUiVisibility = expandedSystemUiVisibility(previewVisible = false)
            }
            setContent {
                when (nextState) {
                    FloatingChatOverlayState.Collapsed -> Unit
                    FloatingChatOverlayState.Expanded -> FloatingChatOverlay(
                        conversation = conversation,
                        frostedBackgroundEnabled = preferences.floatingChatFrostedBackgroundEnabled,
                        backgroundOpacityPercent = preferences.floatingChatBackgroundOpacityPercent,
                        blurRadiusDp = preferences.floatingChatBlurRadiusDp,
                        backgroundColorRgb = preferences.floatingChatBackgroundColorRgb,
                        initialLocalMessages = localMessages,
                        initialMessageSequence = localMessageSequence,
                        initialMomentPosts = momentPosts,
                        initialContactProfiles = contactProfiles,
                        initialGroupProfiles = groupProfiles,
                        initialSelectedAccountId = selectedAccountId,
                        onLocalMessagesChanged = { messages, sequence ->
                            localMessages.clear()
                            localMessages.addAll(messages)
                            localMessageSequence = sequence
                        },
                        onPrepareOutgoingMessage = { message, threadId ->
                            prepareOutgoingMessageForScrm(message, threadId)
                        },
                        onPersistLocalMessage = { message, threadId ->
                            persistLocalMessage(message, threadId)
                        },
                        onPersistForwardedMessage = { source, forwarded, targetThreadId, senderAccountId ->
                            persistForwardedMessage(
                                source = source,
                                forwarded = forwarded,
                                targetThreadId = targetThreadId,
                                senderAccountId = senderAccountId
                            )
                        },
                        onPersistMomentPost = { post ->
                            persistMomentPost(post)
                        },
                        onPersistContactProfile = { profile ->
                            persistContactProfile(profile)
                        },
                        onPersistGroupProfile = { profile ->
                            persistGroupProfile(profile)
                        },
                        onThreadContextChanged = { threadSelection, accountId ->
                            val previousAccountId = selectedAccountId
                            val previousThread = selectedThread
                            selectedThread = threadSelection.toPrototypeToolSelection()
                            selectedAccountId = accountId
                            if (previousAccountId != accountId) {
                                scheduleScrmConversationRefreshAfterAccountSwitch()
                            } else if (previousThread != selectedThread) {
                                refreshLocalMessagesFromStore()
                            }
                        },
                        onOpenExternalDocument = ::openExternalDocument,
                        onPreviewChromeChanged = ::setPreviewChromeVisible,
                        edgeGestureShortThresholdDp = preferences.shortPullThresholdDp,
                        edgeGestureLongThresholdDp = preferences.longPullThresholdDp,
                        onEdgeGesture = onEdgeGesture,
                        onBottomGesture = onBottomGesture,
                        onBackGestureProgress = onBackGestureProgress,
                        onBackGestureCommit = onBackGestureCommit,
                        onBackGestureEnd = onBackGestureEnd,
                        onBackGestureCancel = onBackGestureCancel,
                        voicePermissionRequestToken = voicePermissionRequestToken,
                        locationPermissionRequestToken = locationPermissionRequestToken,
                        runtimeState = runtimeState,
                        onCollapse = ::collapse
                    )
                }
            }
        }
        composeOwner = owner
        composeView = view
        runCatching {
            windowManager.addView(view, layoutParams(nextState))
            onOverlayRecreated()
        }.onFailure {
            Log.w(TAG, "failed to add floating chat overlay", it)
            composeView = null
            composeOwner?.destroy()
            composeOwner = null
        }
    }

    private fun refreshExpandedWindowZOrder(): Boolean {
        val view = composeView ?: return false
        val params = view.layoutParams as? WindowManager.LayoutParams ?: return false
        if (state != FloatingChatOverlayState.Expanded) return false
        applyExpandedChrome(
            view = view,
            params = params,
            previewVisible = previewChromeVisible
        )
        return runCatching {
            windowManager.removeViewImmediate(view)
            windowManager.addView(view, params)
            onOverlayRecreated()
        }.onFailure {
            Log.w(TAG, "failed to refresh floating chat z-order", it)
        }.isSuccess
    }

    private fun loadPersistedLocalMessages(): List<FloatingChatMessage> {
        val messagesById = linkedMapOf<String, FloatingChatMessage>()
        runCatching {
            messageStore.recentFloatingMessages(limit = 500)
        }.onSuccess { messages ->
            messages.forEach { message -> messagesById[message.id] = message }
        }.onFailure { error ->
            Log.e(TAG, "failed to load recent floating chat messages", error)
        }
        floatingChatPersistedMessageThreadIdsForSelection(selectedThread).forEach { threadId ->
            runCatching {
                messageStore.messagesForThread(threadId = threadId, limit = 500)
            }.onSuccess { messages ->
                messages.forEach { message ->
                    messagesById[message.id] = message
                }
            }.onFailure { error ->
                Log.e(TAG, "failed to load floating chat messages for $threadId", error)
            }
        }
        return messagesById.values.toList()
    }

    private fun loadPersistedMomentPosts(): List<AppMomentPost> {
        return runCatching {
            messageStore.momentPosts(limit = 200).map { post -> post.toAppMomentPost() }
        }.onFailure { error ->
            Log.e(TAG, "failed to load floating chat moments", error)
        }.getOrDefault(emptyList())
    }

    private fun loadPersistedContactProfiles(): List<LocalContactProfile> {
        return runCatching {
            messageStore.contactProfiles()
        }.onFailure { error ->
            Log.e(TAG, "failed to load floating chat contact profiles", error)
        }.getOrDefault(emptyList())
    }

    private fun loadPersistedGroupProfiles(): List<LocalGroupProfile> {
        return runCatching {
            messageStore.groupProfiles()
        }.onFailure { error ->
            Log.e(TAG, "failed to load floating chat group profiles", error)
        }.getOrDefault(emptyList())
    }

    private fun prepareOutgoingMessageForScrm(
        message: FloatingChatMessage,
        threadId: String
    ): FloatingChatMessage {
        if (!message.fromMe) return message
        val supported = scrmMessageOperationType(message) != null
        if (!supported) return message
        scrmMessagePreflightFailure(message)?.let { failure ->
            return message.withScrmFailureState(failure)
        }
        val summary = runCatching { scrmSettingsManager.loadSummary() }
            .onFailure { error ->
                Log.w(TAG, "failed to load SCRM routing context", error)
            }
            .getOrNull()
            ?: return message.withScrmFailureState(
                ScrmMessagePreflightFailure(
                    code = "ScrmRoutingUnavailable",
                    message = "无法读取 SCRM 配置，未调用真实发送接口"
                )
            )
        if (!summary.isConfigured) return message
        val route = scrmTextRouteForMessageThread(
            summary = summary,
            message = message,
            threadId = threadId
        ) ?: return message.withScrmFailureState(
            ScrmMessagePreflightFailure(
                code = "MissingScrmRoute",
                message = "当前会话缺少 SCRM 账号或 conversationId，未调用真实发送接口"
            )
        )
        val clientRequestId = UUID.randomUUID().toString()
        pendingScrmTextRoutes[clientRequestId] = route
        return message.withScrmQueueState(clientRequestId)
    }

    private fun persistLocalMessage(message: FloatingChatMessage, threadId: String) {
        messageDatabaseExecutor.execute {
            runCatching {
                if (!enqueueScrmMessageIfNeeded(message, threadId)) {
                    messageStore.insertFloatingMessage(message = message, threadId = threadId)
                }
            }.onFailure { error ->
                Log.e(TAG, "failed to persist floating chat message ${message.id}", error)
                persistFailedOutgoingMessage(message, threadId, error)
            }
        }
    }

    private fun persistFailedOutgoingMessage(
        message: FloatingChatMessage,
        threadId: String,
        error: Throwable
    ) {
        val failedMessage = message.copy(
            sendState = FloatingChatSendState.FailedFinal,
            sendErrorCode = error::class.simpleName ?: "ScrmEnqueueFailed",
            sendErrorMessage = error.message ?: "SCRM 消息入队失败，未调用真实发送接口",
            clientRequestId = null
        )
        runCatching {
            messageStore.insertFloatingMessage(message = failedMessage, threadId = threadId)
            refreshLocalMessagesFromStore()
        }.onFailure { persistError ->
            Log.e(TAG, "failed to persist failed floating chat message ${message.id}", persistError)
        }
    }

    private fun enqueueScrmMessageIfNeeded(
        message: FloatingChatMessage,
        threadId: String
    ): Boolean {
        if (message.sendState != FloatingChatSendState.Queued && message.clientRequestId == null) {
            return false
        }
        require(message.fromMe && scrmMessageOperationType(message) != null) {
            "只有我方文本消息可以进入 SCRM Outbox"
        }
        require(message.sendState == FloatingChatSendState.Queued) {
            "SCRM 文本消息必须处于 QUEUED 状态"
        }
        val clientRequestId = requireNotNull(message.clientRequestId) {
            "SCRM 文本消息缺少 clientRequestId"
        }
        val route = pendingScrmTextRoutes.remove(clientRequestId)
            ?: currentScrmTextRoute(message, threadId)
            ?: error("SCRM 文本消息缺少远端路由")
        val now = System.currentTimeMillis()
        scrmOperationStore.enqueueMessage(
            message = message.toLocalChatMessage(threadId = threadId, createdAt = now),
            item = scrmOutboxItemForMessage(
                message = message,
                route = route,
                now = now
            )
        )
        scrmOperationRunner.requestRun()
        return true
    }

    private fun handleScrmOperationProcessed(result: ScrmOperationProcessorResult) {
        if (result.dispatchedCount == 0 && result.polledCount == 0) return
        refreshLocalMessagesFromStore()
    }

    private fun refreshLocalMessagesFromStore() {
        messageDatabaseExecutor.execute {
            val messages = loadPersistedLocalMessages()
            val sequence = nextLocalMessageSequence(messages)
            mainHandler.post {
                localMessages.clear()
                localMessages.addAll(messages)
                localMessageSequence = sequence
                runtimeState.deliverLocalMessagesUpdate(
                    messages = messages,
                    messageSequence = sequence
                )
            }
        }
    }

    private fun currentScrmTextRoute(
        message: FloatingChatMessage,
        threadId: String
    ): ScrmTextMessageRoute? {
        return runCatching {
            scrmTextRouteForMessageThread(
                summary = scrmSettingsManager.loadSummary(),
                message = message,
                threadId = threadId
            )
        }.onFailure { error ->
            Log.w(TAG, "failed to load SCRM routing context", error)
        }.getOrNull()
    }

    private fun refreshScrmConversationFromApi() {
        clearScheduledScrmConversationRefresh()
        clearScheduledScrmBackgroundPrefetch()
        if (
            scrmConversationRefreshGateDecision(
                inFlight = !scrmConversationRefreshInFlight.compareAndSet(false, true)
            ) == ScrmConversationRefreshGate.QueuePending
        ) {
            scrmConversationRefreshPending.set(true)
            return
        }
        scrmConversationExecutor.execute {
            val result = runCatching { loadScrmConversationFromApi() }
            mainHandler.post {
                result.onSuccess { nextConversation ->
                    applyScrmConversation(nextConversation)
                    scheduleScrmConversationBackgroundPrefetch()
                }.onFailure { error ->
                    Log.w(TAG, "failed to refresh SCRM floating chat conversation", error)
                }
                scrmConversationRefreshInFlight.set(false)
                if (scrmConversationRefreshPending.getAndSet(false)) {
                    refreshScrmConversationFromApi()
                }
            }
        }
    }

    private fun scheduleScrmConversationRefreshAfterAccountSwitch() {
        clearScheduledScrmConversationRefresh()
        val refresh = Runnable {
            scheduledScrmConversationRefresh = null
            refreshScrmConversationFromApi()
        }
        scheduledScrmConversationRefresh = refresh
        mainHandler.postDelayed(refresh, scrmAccountSwitchRefreshDebounceMillis().toLong())
    }

    private fun clearScheduledScrmConversationRefresh() {
        scheduledScrmConversationRefresh?.let { pendingRefresh ->
            mainHandler.removeCallbacks(pendingRefresh)
        }
        scheduledScrmConversationRefresh = null
    }

    private fun scheduleScrmConversationBackgroundPrefetch() {
        clearScheduledScrmBackgroundPrefetch()
        val prefetch = Runnable {
            scheduledScrmBackgroundPrefetch = null
            prefetchNextScrmAccountConversationFromApi()
        }
        scheduledScrmBackgroundPrefetch = prefetch
        mainHandler.postDelayed(prefetch, scrmBackgroundPrefetchDelayMillis().toLong())
    }

    private fun clearScheduledScrmBackgroundPrefetch() {
        scheduledScrmBackgroundPrefetch?.let { pendingPrefetch ->
            mainHandler.removeCallbacks(pendingPrefetch)
        }
        scheduledScrmBackgroundPrefetch = null
    }

    private fun prefetchNextScrmAccountConversationFromApi() {
        if (!scrmConversationRefreshInFlight.compareAndSet(false, true)) return
        scrmConversationExecutor.execute {
            val result = runCatching { loadNextScrmPrefetchConversationFromApi() }
            mainHandler.post {
                result.onSuccess { nextConversation ->
                    if (nextConversation != null) {
                        applyScrmConversation(nextConversation)
                        scheduleScrmConversationBackgroundPrefetch()
                    }
                }.onFailure { error ->
                    Log.w(TAG, "failed to prefetch SCRM floating chat conversation", error)
                }
                scrmConversationRefreshInFlight.set(false)
                if (scrmConversationRefreshPending.getAndSet(false)) {
                    refreshScrmConversationFromApi()
                }
            }
        }
    }

    private fun loadScrmConversationFromApi(): com.paifa.ubikitouch.core.model.FloatingChatConversation {
        return try {
            loadScrmConversationForSession(scrmSettingsManager.loadSelectedSessionOrBootstrap())
        } catch (error: ScrmAuthenticationException) {
            when (scrmSettingsManager.bootstrapWithBundledAdminCredentials()) {
                is ScrmAdminBootstrapResult.Success -> {
                    loadScrmConversationForSession(scrmSettingsManager.loadSelectedSessionOrBootstrap())
                }
                is ScrmAdminBootstrapResult.Failure,
                null -> throw error
            }
        }
    }

    private fun loadNextScrmPrefetchConversationFromApi(): com.paifa.ubikitouch.core.model.FloatingChatConversation? {
        val session = scrmSettingsManager.loadSelectedSessionOrBootstrap()
        val devices = session.readApi.getDevices()
        val accounts = session.readApi.getWechatAccounts()
        val selectedRoute = scrmFloatingAccountRouteForSelection(
            selectedAccountId = selectedAccountId,
            fallbackDeviceUuid = session.deviceUuid,
            fallbackWeChatId = session.weChatId
        )
        val route = scrmBackgroundPrefetchRoutesToLoad(
            accounts = accounts,
            devices = devices,
            selectedRoute = selectedRoute,
            cachedRouteKeys = cachedScrmAccountConversations.keys,
            maxRoutes = scrmBackgroundPrefetchMaxRoutesPerPass()
        ).firstOrNull() ?: return null
        val accountConversation = loadScrmAccountConversation(
            session = session,
            route = route
        )
        cachedScrmAccountConversations[scrmAccountConversationCacheKey(accountConversation)] = accountConversation
        val accountConversations = mergeScrmAccountConversationCache(
            cachedConversations = cachedScrmAccountConversations.values,
            loadedConversations = listOf(accountConversation)
        )
        return scrmFloatingChatConversation(
            base = FloatingChatPrototype.sampleConversation(),
            contacts = emptyList(),
            accountConversations = accountConversations,
            accounts = accounts,
            devices = devices,
            selectedDeviceUuid = selectedRoute.deviceUuid,
            selectedWeChatId = selectedRoute.weChatId
        )
    }

    private fun loadScrmConversationForSession(
        session: ScrmSelectedSession
    ): com.paifa.ubikitouch.core.model.FloatingChatConversation {
        val devices = session.readApi.getDevices()
        val accounts = session.readApi.getWechatAccounts()
        val selectedRoute = scrmFloatingAccountRouteForSelection(
            selectedAccountId = selectedAccountId,
            fallbackDeviceUuid = session.deviceUuid,
            fallbackWeChatId = session.weChatId
        )
        val loadedAccountConversations = scrmInitialConversationRoutesToLoad(
            accounts = accounts,
            devices = devices,
            selectedRoute = selectedRoute,
            cachedRouteKeys = cachedScrmAccountConversations.keys
        ).map { route ->
            loadScrmAccountConversation(
                session = session,
                route = route
            )
        }
        loadedAccountConversations.forEach { accountConversation ->
            cachedScrmAccountConversations[scrmAccountConversationCacheKey(accountConversation)] = accountConversation
        }
        val accountConversations = mergeScrmAccountConversationCache(
            cachedConversations = cachedScrmAccountConversations.values,
            loadedConversations = loadedAccountConversations
        )
        return scrmFloatingChatConversation(
            base = FloatingChatPrototype.sampleConversation(),
            contacts = emptyList(),
            accountConversations = accountConversations,
            accounts = accounts,
            devices = devices,
            selectedDeviceUuid = selectedRoute.deviceUuid,
            selectedWeChatId = selectedRoute.weChatId
        )
    }

    private fun loadScrmAccountConversation(
        session: ScrmSelectedSession,
        route: ScrmFloatingAccountRoute
    ): ScrmFloatingAccountConversation {
        val chatRooms = loadAllScrmChatRooms(session, route.weChatId)
        return ScrmFloatingAccountConversation(
            deviceUuid = route.deviceUuid,
            weChatId = route.weChatId,
            contacts = loadAllScrmContacts(session, route.weChatId),
            chatRooms = chatRooms,
            chatRoomMembers = loadScrmChatRoomMembersForAvatar(
                session = session,
                weChatId = route.weChatId,
                chatRooms = chatRooms
            )
        )
    }

    private fun loadScrmChatRoomMembersForAvatar(
        session: ScrmSelectedSession,
        weChatId: String,
        chatRooms: List<ScrmChatRoom>
    ): Map<String, List<ScrmChatRoomMember>> {
        val membersByRoomId = linkedMapOf<String, List<ScrmChatRoomMember>>()
        chatRooms.forEach { chatRoom ->
            val chatRoomId = chatRoom.chatRoomId?.takeIf { it.isNotBlank() } ?: return@forEach
            val page = session.chatRoomApi.getChatRoomMembers(
                chatRoomId = chatRoomId,
                query = ScrmChatRoomMemberQuery(
                    weChatId = weChatId,
                    page = 1,
                    pageSize = ScrmGroupAvatarMemberPageSize,
                    includeDeleted = false
                )
            )
            membersByRoomId[chatRoomId] = page.items
        }
        return membersByRoomId
    }

    private fun loadAllScrmContacts(
        session: ScrmSelectedSession,
        weChatId: String
    ): List<ScrmContact> {
        val allContacts = mutableListOf<ScrmContact>()
        var pageNumber = 1
        do {
            val page = session.contactApi.getContacts(
                scrmConversationContactQuery(
                    weChatId = weChatId,
                    pageNumber = pageNumber
                )
            )
            allContacts += page.items
            pageNumber += 1
        } while (shouldRequestNextScrmConversationPage(
                returnedItemCount = page.items.size,
                loadedItemCount = allContacts.size,
                totalCount = page.totalCount,
                pageSize = ScrmConversationPageSize
            )
        )
        return allContacts
    }

    private fun loadAllScrmChatRooms(
        session: ScrmSelectedSession,
        weChatId: String
    ): List<ScrmChatRoom> {
        val allChatRooms = mutableListOf<ScrmChatRoom>()
        var pageNumber = 1
        do {
            val page = session.chatRoomApi.getChatRooms(
                ScrmChatRoomQuery(
                    weChatId = weChatId,
                    page = pageNumber,
                    pageSize = ScrmConversationPageSize,
                    includeDeleted = false
                )
            )
            allChatRooms += page.items
            pageNumber += 1
        } while (shouldRequestNextScrmConversationPage(
                returnedItemCount = page.items.size,
                loadedItemCount = allChatRooms.size,
                totalCount = page.totalCount,
                pageSize = ScrmConversationPageSize
            )
        )
        return allChatRooms
    }

    private fun applyScrmConversation(
        nextConversation: com.paifa.ubikitouch.core.model.FloatingChatConversation
    ) {
        conversation = nextConversation
        selectedAccountId = nextConversation.accountContacts.firstOrNull { account -> account.selected }?.id
            ?: nextConversation.accountContacts.firstOrNull()?.id
            ?: selectedAccountId
        if (!toolThreadSelectionExists(nextConversation, selectedThread)) {
            selectedThread = defaultToolThreadSelection(nextConversation)
        }
        val nextThreadSelection = selectedThread.toChatThreadSelection()
        runtimeState.deliverConversationUpdate(
            conversation = nextConversation,
            selectedAccountId = selectedAccountId,
            selectedThread = nextThreadSelection
        )
        refreshLocalMessagesFromStore()
        if (
            state == FloatingChatOverlayState.Expanded &&
            scrmConversationRefreshRecreatesExpandedFloatingChatOverlay()
        ) {
            showState(FloatingChatOverlayState.Expanded, force = true)
        }
    }

    private fun toolThreadSelectionExists(
        conversation: com.paifa.ubikitouch.core.model.FloatingChatConversation,
        selection: FloatingChatPrototype.ToolThreadSelection
    ): Boolean {
        return when (selection) {
            FloatingChatPrototype.ToolThreadSelection.Group -> {
                conversation.groupContacts.isEmpty() && conversation.contacts.isEmpty()
            }
            is FloatingChatPrototype.ToolThreadSelection.GroupChat -> {
                conversation.groupContacts.any { group -> group.id == selection.groupId }
            }
            is FloatingChatPrototype.ToolThreadSelection.Private -> {
                conversation.contacts.any { contact -> contact.id == selection.contactId }
            }
        }
    }

    private fun persistMomentPost(post: AppMomentPost) {
        val existingIndex = momentPosts.indexOfFirst { existing -> existing.id == post.id }
        if (existingIndex >= 0) {
            momentPosts[existingIndex] = post
        } else {
            momentPosts.add(0, post)
        }
        messageDatabaseExecutor.execute {
            runCatching {
                messageStore.upsertMomentPost(post.toLocalMomentPost())
            }.onFailure { error ->
                Log.e(TAG, "failed to persist floating chat moment ${post.id}", error)
            }
        }
    }

    private fun persistContactProfile(profile: LocalContactProfile) {
        val existingIndex = contactProfiles.indexOfFirst { existing ->
            existing.accountId == profile.accountId && existing.contactId == profile.contactId
        }
        if (existingIndex >= 0) {
            contactProfiles[existingIndex] = profile
        } else {
            contactProfiles.add(profile)
        }
        messageDatabaseExecutor.execute {
            runCatching {
                messageStore.upsertContactProfile(profile)
            }.onFailure { error ->
                Log.e(
                    TAG,
                    "failed to persist floating chat contact profile ${profile.accountId}/${profile.contactId}",
                    error
                )
            }
        }
    }

    private fun persistGroupProfile(profile: LocalGroupProfile) {
        val existingIndex = groupProfiles.indexOfFirst { existing ->
            existing.accountId == profile.accountId && existing.groupId == profile.groupId
        }
        if (existingIndex >= 0) {
            groupProfiles[existingIndex] = profile
        } else {
            groupProfiles.add(profile)
        }
        messageDatabaseExecutor.execute {
            runCatching {
                messageStore.upsertGroupProfile(profile)
            }.onFailure { error ->
                Log.e(
                    TAG,
                    "failed to persist floating chat group profile ${profile.accountId}/${profile.groupId}",
                    error
                )
            }
        }
    }

    private fun persistForwardedMessage(
        source: FloatingChatMessage,
        forwarded: FloatingChatMessage,
        targetThreadId: String,
        senderAccountId: String?
    ) {
        messageDatabaseExecutor.execute {
            runCatching {
                val existingSourceFileRefs = messageStore.messageFileRefs(source.id)
                if (existingSourceFileRefs.isNotEmpty()) {
                    messageStore.forwardMessageWithFiles(
                        sourceMessageId = source.id,
                        targetThreadId = targetThreadId,
                        newMessageId = forwarded.id,
                        senderId = senderAccountId,
                        senderName = forwarded.senderName
                    )
                } else {
                    messageStore.insertFloatingMessage(
                        message = forwarded,
                        threadId = targetThreadId
                    )
                }
            }.onFailure { error ->
                Log.e(TAG, "failed to persist forwarded floating chat message ${forwarded.id}", error)
            }
        }
    }

    private fun nextLocalMessageSequence(messages: List<FloatingChatMessage>): Int {
        return messages.maxOfOrNull { message ->
            message.id.substringAfterLast('-', "").toIntOrNull() ?: 0
        } ?: 0
    }

    private fun FloatingChatPrototype.ToolThreadSelection.toLocalThreadId(): String {
        return when (this) {
            FloatingChatPrototype.ToolThreadSelection.Group -> localThreadIdForSelection()
            is FloatingChatPrototype.ToolThreadSelection.GroupChat -> localThreadIdForSelection(groupId = groupId)
            is FloatingChatPrototype.ToolThreadSelection.Private -> localThreadIdForSelection(privateContactId = contactId)
        }
    }

    private fun externalDocumentMimeType(message: FloatingChatMessage): String {
        message.mediaMimeType?.takeIf { it.isNotBlank() }?.let { return it }
        val extension = (message.fileName ?: message.text)
            .substringBefore('?')
            .substringAfterLast('.', missingDelimiterValue = "")
            .lowercase()
        return when {
            message.fileFormat == FloatingChatFileFormat.Pdf || extension == "pdf" -> "application/pdf"
            message.fileFormat == FloatingChatFileFormat.Word || extension == "docx" ->
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            extension == "doc" -> "application/msword"
            message.fileFormat == FloatingChatFileFormat.Txt || extension == "txt" -> "text/plain"
            message.fileFormat == FloatingChatFileFormat.Markdown || extension == "md" || extension == "markdown" ->
                "text/markdown"
            extension == "zip" -> "application/zip"
            extension == "rar" -> "application/vnd.rar"
            else -> "*/*"
        }
    }

    fun dismiss() {
        dismissView()
        updateOverlayState(FloatingChatOverlayState.Collapsed)
    }

    private fun updateOverlayState(nextState: FloatingChatOverlayState) {
        if (state == nextState) return
        state = nextState
        onExpandedChanged(nextState == FloatingChatOverlayState.Expanded)
    }

    private fun dismissView() {
        val view = composeView ?: return
        runCatching {
            windowManager.removeView(view)
        }.onFailure {
            Log.w(TAG, "failed to remove floating chat overlay", it)
        }
        composeView = null
        composeOwner?.destroy()
        composeOwner = null
        previewChromeVisible = false
    }

    private fun hideExpandedViewForCollapse(): Boolean {
        val view = composeView ?: return false
        val params = view.layoutParams as? WindowManager.LayoutParams ?: return false
        val presentation = floatingChatWindowPresentation(expanded = false)
        previewChromeVisible = false
        view.visibility = View.VISIBLE
        applyWindowPresentation(params, presentation)
        params.flags = params.flags and WindowManager.LayoutParams.FLAG_FULLSCREEN.inv()
        params.flags = params.flags and WindowManager.LayoutParams.FLAG_LAYOUT_IN_OVERSCAN.inv()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_BLUR_BEHIND.inv()
            params.setBlurBehindRadius(0)
        }
        view.systemUiVisibility = 0
        return runCatching {
            windowManager.updateViewLayout(view, params)
        }.onFailure {
            Log.w(TAG, "failed to hide retained floating chat overlay", it)
        }.isSuccess
    }

    private fun restoreRetainedExpandedView(): Boolean {
        val view = composeView ?: return false
        val params = view.layoutParams as? WindowManager.LayoutParams ?: return false
        applyWindowPresentation(
            params = params,
            presentation = floatingChatWindowPresentation(expanded = true)
        )
        params.flags = params.flags or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        applyExpandedChrome(
            view = view,
            params = params,
            previewVisible = previewChromeVisible
        )
        view.visibility = View.VISIBLE
        return runCatching {
            windowManager.updateViewLayout(view, params)
        }.onFailure {
            Log.w(TAG, "failed to restore retained floating chat overlay", it)
        }.isSuccess
    }

    private fun applyWindowPresentation(
        params: WindowManager.LayoutParams,
        presentation: FloatingChatWindowPresentation
    ) {
        params.width = presentation.width
        params.height = presentation.height
        params.alpha = presentation.alpha
        params.flags = if (presentation.touchable) {
            params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        } else {
            params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
        params.flags = if (presentation.focusable) {
            params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        } else {
            params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }
    }

    private fun setPreviewChromeVisible(visible: Boolean) {
        if (previewChromeVisible == visible) return
        previewChromeVisible = visible
        val view = composeView ?: return
        val params = view.layoutParams as? WindowManager.LayoutParams ?: return
        applyExpandedChrome(
            view = view,
            params = params,
            previewVisible = visible
        )
        runCatching {
            windowManager.updateViewLayout(view, params)
        }.onFailure {
            Log.w(TAG, "failed to update floating chat preview chrome", it)
        }
    }

    private fun layoutParams(state: FloatingChatOverlayState): WindowManager.LayoutParams {
        val width = when (state) {
            FloatingChatOverlayState.Collapsed -> 0
            FloatingChatOverlayState.Expanded -> WindowManager.LayoutParams.MATCH_PARENT
        }
        val height = when (state) {
            FloatingChatOverlayState.Collapsed -> 0
            FloatingChatOverlayState.Expanded -> WindowManager.LayoutParams.MATCH_PARENT
        }
        return WindowManager.LayoutParams(
            width,
            height,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            focusFlagFor(state) or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            if (state == FloatingChatOverlayState.Expanded) {
                applyExpandedWindowFlags(previewVisible = false, preferences = preferences)
            }
        }
    }

    private fun focusFlagFor(state: FloatingChatOverlayState): Int {
        return if (state == FloatingChatOverlayState.Collapsed) {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        } else {
            0
        }
    }

    private fun applyExpandedChrome(
        view: View,
        params: WindowManager.LayoutParams,
        previewVisible: Boolean
    ) {
        params.applyExpandedWindowFlags(previewVisible, preferences)
        view.systemUiVisibility = expandedSystemUiVisibility(previewVisible)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.windowInsetsController?.let { controller ->
                if (previewVisible) {
                    controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                    controller.systemBarsBehavior =
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                } else {
                    controller.show(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                }
            }
        }
    }

    private fun WindowManager.LayoutParams.applyExpandedWindowFlags(
        previewVisible: Boolean,
        preferences: UbikiPreferences
    ) {
        if (previewVisible) {
            flags = flags or
                WindowManager.LayoutParams.FLAG_FULLSCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_OVERSCAN
        } else {
            flags = flags and WindowManager.LayoutParams.FLAG_FULLSCREEN.inv()
            flags = flags and WindowManager.LayoutParams.FLAG_LAYOUT_IN_OVERSCAN.inv()
        }
        val blurRadiusPx = if (
            !previewVisible &&
            preferences.floatingChatFrostedBackgroundEnabled &&
            preferences.floatingChatBlurRadiusDp > 0 &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        ) {
            (preferences.floatingChatBlurRadiusDp * context.resources.displayMetrics.density).roundToInt()
        } else {
            0
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && blurRadiusPx > 0) {
            flags = flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
            setBlurBehindRadius(blurRadiusPx)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags = flags and WindowManager.LayoutParams.FLAG_BLUR_BEHIND.inv()
            setBlurBehindRadius(0)
        }
    }

    private fun expandedSystemUiVisibility(previewVisible: Boolean): Int {
        val layoutFlags = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        return if (previewVisible) {
            layoutFlags or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        } else {
            layoutFlags
        }
    }

    private companion object {
        const val TAG = "UbikiTouch"
        const val EXTRA_EXTERNAL_DOCUMENT_URI = "floating_chat_external_document_uri"
        const val EXTRA_EXTERNAL_DOCUMENT_MIME_TYPE = "floating_chat_external_document_mime_type"
        const val ScrmConversationPageSize = 200
        const val ScrmGroupAvatarMemberPageSize = 9
    }
}

private enum class FloatingChatOverlayState {
    Collapsed,
    Expanded
}

internal fun shouldRestoreHiddenExpandedChatView(
    floatingChatExpanded: Boolean,
    chatViewHidden: Boolean
): Boolean {
    return floatingChatExpanded && chatViewHidden
}

internal data class FloatingChatWindowPresentation(
    val width: Int,
    val height: Int,
    val alpha: Float,
    val touchable: Boolean,
    val focusable: Boolean
)

internal fun floatingChatWindowPresentation(expanded: Boolean): FloatingChatWindowPresentation {
    return FloatingChatWindowPresentation(
        width = WindowManager.LayoutParams.MATCH_PARENT,
        height = WindowManager.LayoutParams.MATCH_PARENT,
        alpha = if (expanded) 1f else 0f,
        touchable = expanded,
        focusable = expanded
    )
}

internal fun floatingChatShouldRefreshExpandedWindowZOrder(
    force: Boolean,
    hasComposeView: Boolean,
    currentExpanded: Boolean,
    nextExpanded: Boolean
): Boolean {
    return false
}

internal fun scrmConversationRefreshRecreatesExpandedFloatingChatOverlay(): Boolean = false

internal fun floatingChatOverlayHidesSemanticsFromItsOwningAccessibilityService(): Boolean = true

internal fun scrmInitialConversationRoutesToLoad(
    accounts: List<ScrmWechatAccount>,
    devices: List<ScrmDevice>,
    selectedRoute: ScrmFloatingAccountRoute,
    cachedRouteKeys: Set<String> = emptySet()
): List<ScrmFloatingAccountRoute> {
    require(selectedRoute.deviceUuid.isNotBlank()) { "selected deviceUuid cannot be blank" }
    require(selectedRoute.weChatId.isNotBlank()) { "selected weChatId cannot be blank" }
    if (scrmAccountRouteCacheKey(selectedRoute) in cachedRouteKeys) return emptyList()
    return listOf(selectedRoute)
}

internal fun floatingChatPersistedMessageThreadIdsForSelection(
    selection: FloatingChatPrototype.ToolThreadSelection
): Set<String> {
    return when (selection) {
        FloatingChatPrototype.ToolThreadSelection.Group -> setOf(localThreadIdForSelection())
        is FloatingChatPrototype.ToolThreadSelection.GroupChat -> {
            setOf(localThreadIdForSelection(groupId = selection.groupId))
        }
        is FloatingChatPrototype.ToolThreadSelection.Private -> {
            setOf(localThreadIdForSelection(privateContactId = selection.contactId))
        }
    }
}

internal fun shouldRequestNextScrmConversationPage(
    returnedItemCount: Int,
    loadedItemCount: Int,
    totalCount: Int,
    pageSize: Int
): Boolean {
    if (returnedItemCount <= 0) return false
    if (totalCount > 0) return loadedItemCount < totalCount
    return returnedItemCount >= pageSize.coerceAtLeast(1)
}

internal fun scrmConversationContactQuery(
    weChatId: String,
    pageNumber: Int
): ScrmContactQuery {
    return ScrmContactQuery(
        weChatId = weChatId,
        page = pageNumber,
        pageSize = scrmConversationPageSize(),
        onlyFriends = true,
        includeProfile = true
    )
}

internal fun scrmConversationPageSize(): Int = 200

internal enum class ScrmConversationRefreshGate {
    StartNow,
    QueuePending
}

internal fun scrmConversationRefreshGateDecision(inFlight: Boolean): ScrmConversationRefreshGate {
    return if (inFlight) {
        ScrmConversationRefreshGate.QueuePending
    } else {
        ScrmConversationRefreshGate.StartNow
    }
}

internal fun scrmAccountSwitchRefreshDebounceMillis(): Int = 260

internal fun scrmBackgroundPrefetchDelayMillis(): Int = 700

internal fun scrmBackgroundPrefetchMaxRoutesPerPass(): Int = 1

internal fun scrmBackgroundPrefetchRoutesToLoad(
    accounts: List<ScrmWechatAccount>,
    devices: List<ScrmDevice>,
    selectedRoute: ScrmFloatingAccountRoute,
    cachedRouteKeys: Set<String>,
    maxRoutes: Int
): List<ScrmFloatingAccountRoute> {
    if (maxRoutes <= 0) return emptyList()
    val devicesByWechatId = devices
        .mapNotNull { device ->
            val weChatId = device.weChatId?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            weChatId to device
        }
        .toMap()
    val routesByKey = linkedMapOf<String, ScrmFloatingAccountRoute>()
    accounts.forEach { account ->
        val weChatId = account.wxid?.takeIf { it.isNotBlank() } ?: return@forEach
        val deviceUuid = account.clientUuid?.takeIf { it.isNotBlank() }
            ?: devicesByWechatId[weChatId]?.uuid?.takeIf { it.isNotBlank() }
            ?: return@forEach
        val route = ScrmFloatingAccountRoute(deviceUuid = deviceUuid, weChatId = weChatId)
        routesByKey.putIfAbsent(scrmAccountRouteCacheKey(route), route)
    }
    devices.forEach { device ->
        val deviceUuid = device.uuid?.takeIf { it.isNotBlank() } ?: return@forEach
        val weChatId = device.weChatId?.takeIf { it.isNotBlank() } ?: return@forEach
        val route = ScrmFloatingAccountRoute(deviceUuid = deviceUuid, weChatId = weChatId)
        routesByKey.putIfAbsent(scrmAccountRouteCacheKey(route), route)
    }
    val selectedKey = scrmAccountRouteCacheKey(selectedRoute)
    return routesByKey
        .filterKeys { routeKey -> routeKey != selectedKey && routeKey !in cachedRouteKeys }
        .values
        .take(maxRoutes)
}

internal fun mergeScrmAccountConversationCache(
    cachedConversations: Collection<ScrmFloatingAccountConversation>,
    loadedConversations: Collection<ScrmFloatingAccountConversation>
): List<ScrmFloatingAccountConversation> {
    val conversationsByRoute = linkedMapOf<String, ScrmFloatingAccountConversation>()
    cachedConversations.forEach { conversation ->
        conversationsByRoute[scrmAccountConversationCacheKey(conversation)] = conversation
    }
    loadedConversations.forEach { conversation ->
        conversationsByRoute[scrmAccountConversationCacheKey(conversation)] = conversation
    }
    return conversationsByRoute.values.toList()
}

private fun scrmAccountConversationCacheKey(
    conversation: ScrmFloatingAccountConversation
): String {
    return scrmAccountRouteCacheKey(
        ScrmFloatingAccountRoute(
            deviceUuid = conversation.deviceUuid,
            weChatId = conversation.weChatId
        )
    )
}

internal fun scrmAccountRouteCacheKey(route: ScrmFloatingAccountRoute): String {
    return "${route.deviceUuid}\n${route.weChatId}"
}
