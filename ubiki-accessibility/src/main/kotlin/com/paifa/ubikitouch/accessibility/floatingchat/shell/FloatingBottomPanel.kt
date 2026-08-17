package com.paifa.ubikitouch.accessibility.floatingchat.shell

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface as MaterialSurface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.paifa.ubikitouch.accessibility.AppLocationOption
import com.paifa.ubikitouch.accessibility.AppMomentMedia
import com.paifa.ubikitouch.accessibility.AppMomentPost
import com.paifa.ubikitouch.accessibility.AiAutoReplyFullScreen
import com.paifa.ubikitouch.accessibility.ContactRelationsFullScreen
import com.paifa.ubikitouch.accessibility.FavoriteLibraryScreen
import com.paifa.ubikitouch.accessibility.FinderPublishScreen
import com.paifa.ubikitouch.accessibility.FloatingChatAiConfig
import com.paifa.ubikitouch.accessibility.FriendManagementFullscreenScreen
import com.paifa.ubikitouch.accessibility.LeftSidebarFullScreen
import com.paifa.ubikitouch.accessibility.floatingchat.account.GroupInvitePickerPanel
import com.paifa.ubikitouch.accessibility.floatingchat.account.FloatingChatAccountProfile
import com.paifa.ubikitouch.accessibility.floatingchat.aivoice.AiVoiceCapabilityConfigEvent
import com.paifa.ubikitouch.accessibility.floatingchat.aivoice.AiVoiceCapabilityConfigState
import com.paifa.ubikitouch.accessibility.floatingchat.aivoice.AiVoiceEvent
import com.paifa.ubikitouch.accessibility.floatingchat.aivoice.AiVoicePanel
import com.paifa.ubikitouch.accessibility.floatingchat.aivoice.AiVoiceState
import com.paifa.ubikitouch.accessibility.floatingchat.aivoice.aiVoiceUsesFullscreenWorkspace
import com.paifa.ubikitouch.accessibility.floatingchat.contacts.ScrmContactsPanel
import com.paifa.ubikitouch.accessibility.floatingchat.components.FloatingWorkspaceMotion
import com.paifa.ubikitouch.accessibility.floatingchat.finder.FinderApi
import com.paifa.ubikitouch.accessibility.floatingchat.finder.FinderSession
import com.paifa.ubikitouch.accessibility.floatingchat.finder.FinderWorkspaceView
import com.paifa.ubikitouch.accessibility.floatingchat.input.BottomGestureTouchClearanceDp
import com.paifa.ubikitouch.accessibility.floatingchat.input.BottomEmojiPanelHeightDp
import com.paifa.ubikitouch.accessibility.floatingchat.input.BottomInputBarMaxHeightDp
import com.paifa.ubikitouch.accessibility.floatingchat.moments.MomentMaterialsPanel
import com.paifa.ubikitouch.accessibility.floatingchat.moments.launchMomentExternalLink
import com.paifa.ubikitouch.accessibility.floatingchat.moments.MomentsWorkspace
import com.paifa.ubikitouch.accessibility.floatingchat.moments.MaterialLibraryActivityContent
import com.paifa.ubikitouch.accessibility.floatingchat.message.ScrmComposerKind
import com.paifa.ubikitouch.accessibility.floatingchat.message.ScrmMessageComposerPanel
import com.paifa.ubikitouch.accessibility.floatingchat.scrm.ScrmOperationsHubPanel
import com.paifa.ubikitouch.accessibility.floatingchat.scrm.AccountDevicePanel
import com.paifa.ubikitouch.accessibility.floatingchat.scrm.CustomerProfilePanel
import com.paifa.ubikitouch.accessibility.scrm.ScrmSettingsManager
import com.paifa.ubikitouch.accessibility.floatingchat.theme.OverlayTokens
import com.paifa.ubikitouch.accessibility.floatingchat.tools.AiConfigPanel
import com.paifa.ubikitouch.accessibility.floatingchat.tools.BackgroundRemovalWorkspace
import com.paifa.ubikitouch.accessibility.floatingchat.tools.CompactNoticePanel
import com.paifa.ubikitouch.accessibility.floatingchat.tools.EmojiPanel
import com.paifa.ubikitouch.accessibility.floatingchat.tools.FavoriteCollectionItem
import com.paifa.ubikitouch.accessibility.floatingchat.tools.FavoriteShareFullScreen
import com.paifa.ubikitouch.accessibility.floatingchat.tools.FileDocumentFullScreen
import com.paifa.ubikitouch.accessibility.floatingchat.tools.VoiceCallFullScreen
import com.paifa.ubikitouch.accessibility.floatingchat.tools.VideoCallFullScreen
import com.paifa.ubikitouch.accessibility.floatingchat.tools.GiftPanel
import com.paifa.ubikitouch.accessibility.floatingchat.tools.MoreToolPanel
import com.paifa.ubikitouch.accessibility.floatingchat.tools.PaymentComposerPanel
import com.paifa.ubikitouch.accessibility.floatingchat.tools.TransferFullScreen
import com.paifa.ubikitouch.accessibility.floatingchat.tools.QuickPhrasePanel
import com.paifa.ubikitouch.accessibility.floatingchat.tools.RealVoiceInputPanel
import com.paifa.ubikitouch.accessibility.floatingchat.tools.UiComponentsFullScreen
import com.paifa.ubikitouch.accessibility.floatingchat.scrm.OpenApiWorkbenchActivityContent
import com.paifa.ubikitouch.accessibility.floatingchat.tools.MiniProgramFullScreen
import com.paifa.ubikitouch.accessibility.floatingchat.tools.ReviewRequestsFullScreen
import com.paifa.ubikitouch.core.model.FloatingChatContact
import com.paifa.ubikitouch.accessibility.scrm.ScrmContact
import com.paifa.ubikitouch.accessibility.scrm.ScrmFloatingAccountRoute
internal fun bottomFloatingPanelUsesDarkText(): Boolean = true

internal fun bottomComposerDrawersUseOpaqueInputBarSurface(): Boolean {
    return OverlayTokens.bottomComposerSurface.alpha == 1f
}

internal fun aiAssistantUsesFullscreenWorkspace(): Boolean = true

internal fun aiAssistantEnterOffsetDirection(): Int = 1

internal fun aiAssistantExitOffsetDirection(): Int = -1

/** UI组件复用当前悬浮根视图，避免新建窗口附着时产生 BadTokenException。 */
internal fun uiComponentsUsesFullscreenWorkspace(): Boolean = true

/** 测试流程：打开右侧 UI组件，确认全屏实体从底部进入。 */
internal fun uiComponentsEnterOffsetDirection(): Int = FloatingWorkspaceMotion.EnterOffsetDirection

/** 测试流程：点击左上返回，确认全屏实体向顶部退出。 */
internal fun uiComponentsExitOffsetDirection(): Int = FloatingWorkspaceMotion.ExitOffsetDirection

/** 微信小程序复用当前悬浮根视图，避免新增窗口附着时发生 BadTokenException。 */
internal fun miniProgramUsesFullscreenWorkspace(): Boolean = true

/** 测试流程：点击右侧微信小程序，确认全屏实体从底部进入。 */
internal fun miniProgramEnterOffsetDirection(): Int = 1

/** 测试流程：点击左上返回，确认全屏实体向顶部退出。 */
internal fun miniProgramExitOffsetDirection(): Int = -1

/** 申请审核复用现有悬浮根视图，不单独附着 Window，避免 BadTokenException。 */
internal fun reviewRequestsUsesFullscreenWorkspace(): Boolean = true

/** 测试流程：点击申请审核，确认实体全屏工作区从底部进入。 */
internal fun reviewRequestsEnterOffsetDirection(): Int = 1

/** 测试流程：点击左上返回，确认实体全屏工作区向顶部退出。 */
internal fun reviewRequestsExitOffsetDirection(): Int = -1

/** 收藏分享复用当前悬浮根视图，避免独立 Activity/Window 附着时触发 BadTokenException。 */
internal fun favoriteShareUsesFullscreenWorkspace(): Boolean = true

/** 测试流程：点击右侧收藏分享，确认全屏实体从底部向上进入。 */
internal fun favoriteShareEnterOffsetDirection(): Int = 1

/** 测试流程：点击左上角返回，确认收藏全屏实体自上向下退出。 */
internal fun favoriteShareExitOffsetDirection(): Int = -1

/** 文件/文档复用当前悬浮根视图，避免新建 Activity 或 Window 导致 BadTokenException。 */
internal fun fileDocumentUsesFullscreenWorkspace(): Boolean = true

/** 测试流程：点击右侧文件/文档，确认全屏实体自底向上进入。 */
internal fun fileDocumentEnterOffsetDirection(): Int = 1

/** 测试流程：点击左上角返回，确认全屏实体自上向下退出。 */
internal fun fileDocumentExitOffsetDirection(): Int = -1

/** 语音通话在现有悬浮根内呈现，避免独立 Window 附着失败导致 BadTokenException。 */
internal fun voiceCallUsesFullscreenWorkspace(): Boolean = true

/** 测试流程：点击右侧语音通话，确认全屏实体自底向上进入。 */
internal fun voiceCallEnterOffsetDirection(): Int = 1

/** 测试流程：点击左上角返回或结束通话，确认全屏实体自上向下退出。 */
internal fun voiceCallExitOffsetDirection(): Int = -1

/** 视频通话在现有悬浮根内呈现，避免独立 Window 附着失败导致 BadTokenException。 */
internal fun videoCallUsesFullscreenWorkspace(): Boolean = true

/** 测试流程：点击右侧视频通话，确认全屏实体自底向上进入。 */
internal fun videoCallEnterOffsetDirection(): Int = 1

/** 测试流程：点击左上角返回或结束通话，确认全屏实体自上向下退出。 */
internal fun videoCallExitOffsetDirection(): Int = -1

/** 转账复用当前悬浮根视图承载全屏工作区，避免新增 Window 导致 BadTokenException。 */
internal fun transferUsesFullscreenWorkspace(): Boolean = true

/** 测试流程：打开转账，确认工作区实体从底部向上进入。 */
internal fun transferEnterOffsetDirection(): Int = 1

/** 测试流程：点击返回，确认工作区实体向下退出完成后关闭。 */
internal fun transferExitOffsetDirection(): Int = FloatingWorkspaceMotion.ExitOffsetDirection

@Composable
internal fun FloatingBottomPanel(
    mode: BottomPanelMode,
    scrmContactsRoute: ScrmFloatingAccountRoute?,
    contactsOpenAddFriend: Boolean,
    contactsOpenStartGroup: Boolean,
    scrmMomentsRoute: ScrmFloatingAccountRoute?,
    scrmMessageRoute: ScrmFloatingAccountRoute?,
    scrmMessageConversationId: String?,
    finderSession: FinderSession?,
    finderApi: FinderApi?,
    finderConfigurationError: String?,
    finderInitialSphUserName: String?,
    finderUserPageRequestKey: Int,
    voicePermissionRequestToken: Int,
    locationPermissionRequestToken: Int,
    onClose: () -> Unit,
    aiVoiceState: AiVoiceState,
    aiVoiceCapabilityConfigState: AiVoiceCapabilityConfigState,
    onAiVoiceEvent: (AiVoiceEvent) -> Unit,
    onAiVoiceCapabilityConfigEvent: (AiVoiceCapabilityConfigEvent) -> Unit,
    onOpenAiVoice: () -> Unit,
    onOpenToolPanel: (BottomPanelMode) -> Unit,
    onOpenPrivateChat: (ScrmFloatingAccountRoute, ScrmContact) -> Unit,
    onOpenFriendProfile: (ScrmFloatingAccountRoute, ScrmContact) -> Unit,
    onInsertText: (String) -> Unit,
    onSendVoice: (String, Int) -> Unit,
    quickPhrases: List<String>,
    momentPosts: List<AppMomentPost>,
    pendingMomentMedia: AppMomentMedia?,
    favoriteItems: List<FavoriteCollectionItem>,
    documentMessages: List<com.paifa.ubikitouch.core.model.FloatingChatMessage>,
    voiceCallTargetName: String,
    voiceCallParticipantNames: List<String>,
    voiceCallIsGroup: Boolean,
    videoCallTargetName: String,
    videoCallParticipantNames: List<String>,
    videoCallIsGroup: Boolean,
    accounts: List<FloatingChatContact>,
    accountProfiles: Map<String, FloatingChatAccountProfile>,
    aiConfig: FloatingChatAiConfig,
    aiConfigStatus: String?,
    aiPredicting: Boolean,
    aiConfigTesting: Boolean,
    aiModels: List<String>,
    aiModelsLoading: Boolean,
    transferRecipients: List<FloatingChatContact>,
    paymentOperationStatus: String?,
    paymentOperationInProgress: Boolean,
    onSaveAiConfig: (FloatingChatAiConfig) -> Unit,
    onTestAiConfig: (FloatingChatAiConfig) -> Unit,
    onFetchAiModels: (FloatingChatAiConfig) -> Unit,
    onSendQuickPhrase: (String) -> Unit,
    onAddQuickPhrase: (String) -> Unit,
    onUpdateQuickPhrase: (Int, String) -> Unit,
    onDeleteQuickPhrase: (Int) -> Unit,
    onPickMomentMedia: () -> Unit,
    onClearMomentMedia: () -> Unit,
    onPreviewMomentMedia: (AppMomentPost) -> Unit,
    onUpdateMomentPost: (AppMomentPost) -> Unit,
    favoriteMultiSelectMode: Boolean,
    selectedFavoriteItemIds: Map<String, Boolean>,
    onPreviewFavoriteItem: (FavoriteCollectionItem) -> Unit,
    onFavoriteItemLongPress: (FavoriteCollectionItem, Rect?) -> Unit,
    onToggleFavoriteSelection: (FavoriteCollectionItem) -> Unit,
    onForwardSelectedFavorites: () -> Unit,
    onDeleteSelectedFavorites: () -> Unit,
    onCancelFavoriteSelection: () -> Unit,
    onPickDocument: () -> Unit,
    onPreviewDocument: (com.paifa.ubikitouch.core.model.FloatingChatMessage) -> Unit,
    onEndVoiceCall: (Int) -> Unit,
    onEndVideoCall: (Int) -> Unit,
    onSendRedPacket: (String, String, String, Int) -> Unit,
    onSendTransfer: (String, String, FloatingChatContact?, String) -> Unit,
    onSendLocation: (AppLocationOption) -> Unit,
    onSendAccountCard: (String) -> Unit,
    onSendGroupInvite: (String) -> Unit,
    modifier: Modifier = Modifier,
    composerHeader: (@Composable () -> Unit)? = null
) {
    val context = LocalContext.current
    val isBottomDrawer = mode == BottomPanelMode.Emoji || mode == BottomPanelMode.More
    val isFullscreenWorkspace = mode.isFullscreenWorkspace()
    val shape = if (isBottomDrawer) {
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    } else {
        RoundedCornerShape(10.dp)
    }
    val widthFraction = when (mode) {
        BottomPanelMode.Emoji,
        BottomPanelMode.More -> 1f
        BottomPanelMode.QuickPhrase -> 0.78f
        BottomPanelMode.Card -> 0.82f
        BottomPanelMode.Moments -> 0.92f
        BottomPanelMode.Finder -> 0.92f
        BottomPanelMode.MomentMaterials -> 0.92f
        BottomPanelMode.Contacts -> 0.92f
        BottomPanelMode.AccountDevice -> 0.92f
        BottomPanelMode.CustomerProfile -> 0.92f
        BottomPanelMode.Favorite -> 0.86f
        BottomPanelMode.FileDocument -> 0.92f
        BottomPanelMode.VoiceCall -> 0.92f
        BottomPanelMode.VideoCall -> 0.92f
        BottomPanelMode.Assistant,
        BottomPanelMode.AiVoice -> 0.86f
        BottomPanelMode.RedPacket,
        BottomPanelMode.Location -> 0.76f
        BottomPanelMode.ScrmEmoji,
        BottomPanelMode.ScrmWeAppCard,
        BottomPanelMode.ScrmCardTemplates,
        BottomPanelMode.ScrmBatchSend,
        BottomPanelMode.ScrmOperations -> 0.92f
        else -> 0.64f
    }
    val maxHeight = when (mode) {
        BottomPanelMode.Emoji -> (BottomEmojiPanelHeightDp + 20 + BottomInputBarMaxHeightDp + BottomGestureTouchClearanceDp).dp
        BottomPanelMode.More -> (286 + BottomInputBarMaxHeightDp).dp
        BottomPanelMode.QuickPhrase -> 310.dp
        BottomPanelMode.Card -> 360.dp
        BottomPanelMode.Moments -> 520.dp
        BottomPanelMode.Finder -> 520.dp
        BottomPanelMode.MomentMaterials -> 520.dp
        BottomPanelMode.Contacts -> 520.dp
        BottomPanelMode.AccountDevice -> 520.dp
        BottomPanelMode.CustomerProfile -> 520.dp
        BottomPanelMode.Favorite -> 380.dp
        BottomPanelMode.FileDocument -> 520.dp
        BottomPanelMode.VoiceCall -> 520.dp
        BottomPanelMode.VideoCall -> 520.dp
        BottomPanelMode.Transfer -> 520.dp
        BottomPanelMode.Assistant,
        BottomPanelMode.AiVoice -> 430.dp
        BottomPanelMode.RedPacket,
        BottomPanelMode.Location -> 300.dp
        BottomPanelMode.ScrmEmoji,
        BottomPanelMode.ScrmWeAppCard,
        BottomPanelMode.ScrmCardTemplates,
        BottomPanelMode.ScrmBatchSend,
        BottomPanelMode.ScrmOperations -> 520.dp
        else -> 230.dp
    }
    MaterialSurface(
        onClick = {},
        modifier = modifier
            .then(
                if (isFullscreenWorkspace) {
                    Modifier.fillMaxSize()
                } else {
                    Modifier
                        .fillMaxWidth(widthFraction)
                        .heightIn(min = 86.dp, max = maxHeight)
                }
            ),
        shape = if (isFullscreenWorkspace) RoundedCornerShape(0.dp) else shape,
        color = if (isFullscreenWorkspace) MaterialTheme.colorScheme.surface else if (isBottomDrawer) {
            OverlayTokens.bottomComposerSurface
        } else {
            OverlayTokens.panel
        },
        border = if (isFullscreenWorkspace) null else BorderStroke(1.dp, OverlayTokens.panelBorder)
    ) {
        val panelContent: @Composable () -> Unit = {
            Box(
                modifier = if (isFullscreenWorkspace) Modifier.fillMaxSize() else Modifier.padding(
                    start = 10.dp,
                    end = 10.dp,
                    top = 10.dp,
                    bottom = if (isBottomDrawer) BottomGestureTouchClearanceDp.dp else 10.dp
                )
            ) {
            when (mode) {
                BottomPanelMode.More -> MoreToolPanel(
                    onOpenPanel = onOpenToolPanel,
                    onAiVoiceClick = onOpenAiVoice
                )
                BottomPanelMode.Emoji -> EmojiPanel(onInsertText = onInsertText)
                BottomPanelMode.Gift -> GiftPanel(onClose = onClose)
                BottomPanelMode.Voice -> RealVoiceInputPanel(
                    permissionRequestToken = voicePermissionRequestToken,
                    onSendVoice = onSendVoice
                )
                BottomPanelMode.QuickPhrase -> QuickPhrasePanel(
                    phrases = quickPhrases,
                    onSendPhrase = onSendQuickPhrase,
                    onAddPhrase = onAddQuickPhrase,
                    onUpdatePhrase = onUpdateQuickPhrase,
                    onDeletePhrase = onDeleteQuickPhrase
                )
                // Card is rendered by FloatingChatOverlayUi inside the existing overlay root.
                BottomPanelMode.Card -> Unit
                // GroupInfo is rendered by FloatingChatOverlayUi because it owns the active group profile state.
                BottomPanelMode.GroupInfo -> Unit
                BottomPanelMode.GroupInvite -> GroupInvitePickerPanel(
                    groups = accounts.filter { it.groupMemberContacts.isNotEmpty() },
                    onBack = { onOpenToolPanel(BottomPanelMode.More) },
                    onSend = { onSendGroupInvite(it.id) }
                )
                BottomPanelMode.Moments -> MomentsWorkspace(
                    route = scrmMomentsRoute,
                    posts = momentPosts,
                    pendingMedia = pendingMomentMedia,
                    onPickMedia = onPickMomentMedia,
                    onClearMedia = onClearMomentMedia,
                    onPreviewMedia = onPreviewMomentMedia,
                    onOpenLink = { link -> launchMomentExternalLink(context, link) },
                    onUpdatePost = onUpdateMomentPost,
                    onRemotePostsLoaded = { remotePosts ->
                        remotePosts.forEach(onUpdateMomentPost)
                    },
                    onClose = onClose
                )
                BottomPanelMode.Finder -> FinderWorkspaceView(
                    session = finderSession,
                    api = finderApi,
                    initialSphUserName = finderInitialSphUserName,
                    userPageRequestKey = finderUserPageRequestKey,
                    configurationError = finderConfigurationError
                )
                BottomPanelMode.MomentMaterials -> MomentMaterialsPanel(
                    route = scrmMomentsRoute,
                    onClose = onClose
                )
                BottomPanelMode.Contacts -> ScrmContactsPanel(
                    route = scrmContactsRoute,
                    openAddFriend = contactsOpenAddFriend,
                    openStartGroup = contactsOpenStartGroup,
                    onClose = onClose,
                    onOpenPrivateChat = onOpenPrivateChat,
                    onOpenFriendProfile = onOpenFriendProfile
                )
                BottomPanelMode.AccountDevice -> AccountDevicePanel(
                    manager = remember(context) { ScrmSettingsManager(context.applicationContext) },
                    onClose = onClose
                )
                BottomPanelMode.CustomerProfile -> CustomerProfilePanel(
                    manager = remember(context) { ScrmSettingsManager(context.applicationContext) },
                    onClose = onClose
                )
                BottomPanelMode.Favorite -> FavoriteShareFullScreen(
                    items = favoriteItems,
                    multiSelectMode = favoriteMultiSelectMode,
                    selectedItemIds = selectedFavoriteItemIds,
                    onPreviewItem = onPreviewFavoriteItem,
                    onLongPressItem = onFavoriteItemLongPress,
                    onToggleSelection = onToggleFavoriteSelection,
                    onForwardSelected = onForwardSelectedFavorites,
                    onDeleteSelected = onDeleteSelectedFavorites,
                    onCancelSelection = onCancelFavoriteSelection,
                    onBack = onClose
                )
                BottomPanelMode.FileDocument -> FileDocumentFullScreen(
                    messages = documentMessages,
                    onPickDocument = onPickDocument,
                    onPreviewDocument = onPreviewDocument,
                    onBack = onClose
                )
                BottomPanelMode.VoiceCall -> VoiceCallFullScreen(
                    targetName = voiceCallTargetName,
                    participantNames = voiceCallParticipantNames,
                    isGroup = voiceCallIsGroup,
                    onEndCall = onEndVoiceCall,
                    onBack = onClose
                )
                BottomPanelMode.VideoCall -> VideoCallFullScreen(
                    targetName = videoCallTargetName,
                    participantNames = videoCallParticipantNames,
                    isGroup = videoCallIsGroup,
                    onEndCall = onEndVideoCall,
                    onBack = onClose
                )
                BottomPanelMode.RedPacket -> PaymentComposerPanel(
                    title = "发红包",
                    amountLabel = "红包金额",
                    noteLabel = "祝福语",
                    defaultNote = "恭喜发财，大吉大利",
                    confirmLabel = "塞钱进红包",
                    recipients = emptyList(),
                    scrmRoute = scrmMessageRoute,
                    operationStatus = paymentOperationStatus,
                    operationInProgress = paymentOperationInProgress,
                    onConfirm = { amount, note, _, paymentPassword, packetCount ->
                        onSendRedPacket(amount, note, paymentPassword, packetCount)
                    }
                )
                BottomPanelMode.Transfer -> TransferFullScreen(
                    recipients = transferRecipients,
                    operationStatus = paymentOperationStatus,
                    operationInProgress = paymentOperationInProgress,
                    onSubmit = onSendTransfer,
                    onBack = onClose
                )
                BottomPanelMode.SplitBill -> error("AA 收款必须由全屏悬浮工作区承载")
                // Location is rendered by FloatingChatOverlayUi inside the existing overlay root.
                BottomPanelMode.Location -> Unit
                BottomPanelMode.ScrmEmoji -> ScrmMessageComposerPanel(
                    kind = ScrmComposerKind.Emoji,
                    route = scrmMessageRoute,
                    conversationId = scrmMessageConversationId,
                    onBack = { onOpenToolPanel(BottomPanelMode.More) }
                )
                BottomPanelMode.ScrmWeAppCard -> ScrmMessageComposerPanel(
                    kind = ScrmComposerKind.WeAppCard,
                    route = scrmMessageRoute,
                    conversationId = scrmMessageConversationId,
                    onBack = { onOpenToolPanel(BottomPanelMode.More) }
                )
                BottomPanelMode.ScrmCardTemplates -> ScrmMessageComposerPanel(
                    kind = ScrmComposerKind.CardTemplates,
                    route = scrmMessageRoute,
                    conversationId = scrmMessageConversationId,
                    onBack = { onOpenToolPanel(BottomPanelMode.More) }
                )
                BottomPanelMode.ScrmBatchSend -> ScrmMessageComposerPanel(
                    kind = ScrmComposerKind.BatchText,
                    route = scrmMessageRoute,
                    conversationId = scrmMessageConversationId,
                    onBack = { onOpenToolPanel(BottomPanelMode.More) }
                )
        BottomPanelMode.ScrmOperations -> ScrmOperationsHubPanel(
                    route = scrmMessageRoute,
                    conversationId = scrmMessageConversationId,
                    onOpenPanel = onOpenToolPanel,
                    onClose = onClose
                )
                BottomPanelMode.Home -> CompactNoticePanel(
                    title = "杩斿洖涓婚〉",
                    message = "已记录当前会话入口，可从悬浮按钮继续打开。",
                    onClose = onClose
                )
                BottomPanelMode.Assistant -> AiConfigPanel(
                    config = aiConfig,
                    status = aiConfigStatus,
                    predicting = aiPredicting,
                    testing = aiConfigTesting,
                    models = aiModels,
                    modelsLoading = aiModelsLoading,
                    onSave = onSaveAiConfig,
                    onTest = onTestAiConfig,
                    onFetchModels = onFetchAiModels,
                    onClose = onClose
                )
                BottomPanelMode.AiVoice -> AiVoicePanel(
                    state = aiVoiceState,
                    onEvent = onAiVoiceEvent,
                    onClose = onClose,
                    capabilityConfigState = aiVoiceCapabilityConfigState,
                    onCapabilityConfigEvent = onAiVoiceCapabilityConfigEvent
                )
                BottomPanelMode.UiComponents -> UiComponentsFullScreen(onBack = onClose)
                BottomPanelMode.AiAutoReply -> AiAutoReplyFullScreen(
                    context = context,
                    onBack = onClose
                )
                BottomPanelMode.ContactRelations -> ContactRelationsFullScreen(onBack = onClose)
                BottomPanelMode.LeftSidebar -> LeftSidebarFullScreen(onBack = onClose)
                BottomPanelMode.FriendManagement -> FriendManagementFullscreenScreen(onBack = onClose)
                BottomPanelMode.FavoriteLibrary -> FavoriteLibraryScreen(
                    context = context,
                    onBack = onClose
                )
                BottomPanelMode.FinderPublish -> FinderPublishScreen(
                    context = context,
                    onBack = onClose
                )
                BottomPanelMode.MaterialLibrary -> MaterialLibraryActivityContent(
                    context = context,
                    onClose = onClose
                )
                BottomPanelMode.OpenApiWorkbench -> OpenApiWorkbenchActivityContent(
                    context = context.applicationContext,
                    onClose = onClose
                )
                BottomPanelMode.BackgroundRemoval -> BackgroundRemovalWorkspace(onBack = onClose)
                BottomPanelMode.MiniProgram -> MiniProgramFullScreen(
                    route = scrmMessageRoute,
                    conversationId = scrmMessageConversationId,
                    onBack = onClose
                )
                BottomPanelMode.ReviewRequests -> ReviewRequestsFullScreen(
                    route = scrmContactsRoute,
                    onBack = onClose
                )
                // HiddenUsers is rendered by FloatingChatOverlayUi because it owns the participant state.
                BottomPanelMode.HiddenUsers -> Unit
                // SendName is rendered by FloatingChatOverlayUi because it owns the active conversation context.
                BottomPanelMode.SendName -> Unit
                // VideoShort is rendered by FloatingChatOverlayUi to launch the existing video-only picker.
                BottomPanelMode.VideoShort -> Unit
                // ChannelsVideo is rendered by FloatingChatOverlayUi inside the existing overlay root.
                BottomPanelMode.ChannelsVideo -> Unit
                // ChannelsLive is rendered by FloatingChatOverlayUi inside the existing overlay root.
                BottomPanelMode.ChannelsLive -> Unit
                // WebLink is rendered by FloatingChatOverlayUi inside the existing overlay root.
                BottomPanelMode.WebLink -> Unit
                // Article is rendered by FloatingChatOverlayUi inside the existing overlay root.
                BottomPanelMode.Article -> Unit
                // Music is rendered by FloatingChatOverlayUi inside the existing overlay root.
                BottomPanelMode.Music -> Unit
                // Gallery is rendered by FloatingChatOverlayUi so it can remain a full-screen root view.
                BottomPanelMode.Gallery -> Unit
                // Toolbar workspaces are rendered by FloatingChatOverlayUi to reuse the overlay root.
                BottomPanelMode.ToolbarSearch,
                BottomPanelMode.ToolbarScan,
                BottomPanelMode.ToolbarAddFriend -> Unit
                // Relay is rendered by FloatingChatOverlayUi to keep its full-screen activity surface in one root.
                BottomPanelMode.Relay -> Unit
                // CouponWallet is rendered by FloatingChatOverlayUi to keep its full-screen activity surface in one root.
                BottomPanelMode.CouponWallet -> Unit
                // SideEffect is rendered by FloatingChatOverlayUi to keep its full-screen activity surface in one root.
                BottomPanelMode.SideEffect -> Unit
                BottomPanelMode.None -> Unit
            }
            }
        }
        if (isBottomDrawer && composerHeader != null) {
            Column(modifier = Modifier.fillMaxWidth()) {
                composerHeader()
                panelContent()
            }
        } else {
            panelContent()
        }
    }
}
