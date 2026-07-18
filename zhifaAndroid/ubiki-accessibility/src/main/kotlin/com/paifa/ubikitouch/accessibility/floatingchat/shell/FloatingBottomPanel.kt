package com.paifa.ubikitouch.accessibility.floatingchat.shell

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface as MaterialSurface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.dp
import com.paifa.ubikitouch.accessibility.AppLocationOption
import com.paifa.ubikitouch.accessibility.AppMomentMedia
import com.paifa.ubikitouch.accessibility.AppMomentPost
import com.paifa.ubikitouch.accessibility.FloatingChatAiConfig
import com.paifa.ubikitouch.accessibility.floatingchat.account.AccountCardPickerPanel
import com.paifa.ubikitouch.accessibility.floatingchat.account.FloatingChatAccountProfile
import com.paifa.ubikitouch.accessibility.floatingchat.aivoice.AiVoiceCapabilityConfigEvent
import com.paifa.ubikitouch.accessibility.floatingchat.aivoice.AiVoiceCapabilityConfigState
import com.paifa.ubikitouch.accessibility.floatingchat.aivoice.AiVoiceEvent
import com.paifa.ubikitouch.accessibility.floatingchat.aivoice.AiVoicePanel
import com.paifa.ubikitouch.accessibility.floatingchat.aivoice.AiVoiceState
import com.paifa.ubikitouch.accessibility.floatingchat.contacts.ScrmContactsPanel
import com.paifa.ubikitouch.accessibility.floatingchat.input.BottomEmojiPanelHeightDp
import com.paifa.ubikitouch.accessibility.floatingchat.moments.MomentMaterialsPanel
import com.paifa.ubikitouch.accessibility.floatingchat.moments.MomentsTimelinePanel
import com.paifa.ubikitouch.accessibility.floatingchat.theme.OverlayTokens
import com.paifa.ubikitouch.accessibility.floatingchat.tools.AiConfigPanel
import com.paifa.ubikitouch.accessibility.floatingchat.tools.CompactNoticePanel
import com.paifa.ubikitouch.accessibility.floatingchat.tools.EmojiPanel
import com.paifa.ubikitouch.accessibility.floatingchat.tools.FavoriteCollectionItem
import com.paifa.ubikitouch.accessibility.floatingchat.tools.FavoriteCollectionPanel
import com.paifa.ubikitouch.accessibility.floatingchat.tools.GiftPanel
import com.paifa.ubikitouch.accessibility.floatingchat.tools.LocationPickerPanel
import com.paifa.ubikitouch.accessibility.floatingchat.tools.MoreToolPanel
import com.paifa.ubikitouch.accessibility.floatingchat.tools.PaymentComposerPanel
import com.paifa.ubikitouch.accessibility.floatingchat.tools.QuickPhrasePanel
import com.paifa.ubikitouch.accessibility.floatingchat.tools.RealVoiceInputPanel
import com.paifa.ubikitouch.core.model.FloatingChatContact
import com.paifa.ubikitouch.accessibility.scrm.ScrmContact
import com.paifa.ubikitouch.accessibility.scrm.ScrmFloatingAccountRoute
internal fun bottomFloatingPanelUsesDarkText(): Boolean = true

internal fun toolFeaturePanelsUseCenteredFloatingSheet(): Boolean = true

internal fun toolFeaturePanelMinWidthDp(): Int = ToolFeaturePanelMinWidthDp

internal fun toolFeaturePanelMaxWidthDp(): Int = ToolFeaturePanelMaxWidthDp

internal fun toolFeaturePanelMaxHeightDp(): Int = ToolFeaturePanelMaxHeightDp

@Composable
internal fun FloatingBottomPanel(
    mode: BottomPanelMode,
    scrmContactsRoute: ScrmFloatingAccountRoute?,
    scrmMomentsRoute: ScrmFloatingAccountRoute?,
    voicePermissionRequestToken: Int,
    locationPermissionRequestToken: Int,
    onClose: () -> Unit,
    aiVoiceState: AiVoiceState,
    aiVoiceCapabilityConfigState: AiVoiceCapabilityConfigState,
    onAiVoiceEvent: (AiVoiceEvent) -> Unit,
    onAiVoiceCapabilityConfigEvent: (AiVoiceCapabilityConfigEvent) -> Unit,
    onOpenAiVoice: () -> Unit,
    onOpenPrivateChat: (ScrmFloatingAccountRoute, ScrmContact) -> Unit,
    onOpenFriendProfile: (ScrmFloatingAccountRoute, ScrmContact) -> Unit,
    onInsertText: (String) -> Unit,
    onSendVoice: (String, Int) -> Unit,
    quickPhrases: List<String>,
    momentPosts: List<AppMomentPost>,
    pendingMomentMedia: AppMomentMedia?,
    favoriteItems: List<FavoriteCollectionItem>,
    accounts: List<FloatingChatContact>,
    accountProfiles: Map<String, FloatingChatAccountProfile>,
    aiConfig: FloatingChatAiConfig,
    aiConfigStatus: String?,
    aiPredicting: Boolean,
    aiConfigTesting: Boolean,
    transferRecipients: List<FloatingChatContact>,
    onSaveAiConfig: (FloatingChatAiConfig) -> Unit,
    onTestAiConfig: (FloatingChatAiConfig) -> Unit,
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
    onSendRedPacket: (String, String) -> Unit,
    onSendTransfer: (String, String, FloatingChatContact?) -> Unit,
    onSendLocation: (AppLocationOption) -> Unit,
    onSendAccountCard: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(10.dp)
    val widthFraction = when (mode) {
        BottomPanelMode.Emoji -> 0.82f
        BottomPanelMode.QuickPhrase -> 0.78f
        BottomPanelMode.Card -> 0.82f
        BottomPanelMode.Moments -> 0.92f
        BottomPanelMode.MomentMaterials -> 0.92f
        BottomPanelMode.Contacts -> 0.92f
        BottomPanelMode.Favorite -> 0.86f
        BottomPanelMode.Assistant,
        BottomPanelMode.AiVoice -> 0.86f
        BottomPanelMode.RedPacket,
        BottomPanelMode.Transfer,
        BottomPanelMode.Location -> 0.76f
        else -> 0.64f
    }
    val maxHeight = when (mode) {
        BottomPanelMode.Emoji -> (BottomEmojiPanelHeightDp + 20).dp
        BottomPanelMode.QuickPhrase -> 310.dp
        BottomPanelMode.Card -> 360.dp
        BottomPanelMode.Moments -> 520.dp
        BottomPanelMode.MomentMaterials -> 520.dp
        BottomPanelMode.Contacts -> 520.dp
        BottomPanelMode.Favorite -> 380.dp
        BottomPanelMode.Assistant,
        BottomPanelMode.AiVoice -> 430.dp
        BottomPanelMode.RedPacket,
        BottomPanelMode.Transfer,
        BottomPanelMode.Location -> 300.dp
        else -> 230.dp
    }
    MaterialSurface(
        onClick = {},
        modifier = modifier
            .then(
                if (mode.isCenteredToolFeaturePanel()) {
                    Modifier
                        .widthIn(
                            min = ToolFeaturePanelMinWidthDp.dp,
                            max = ToolFeaturePanelMaxWidthDp.dp
                        )
                        .heightIn(min = 120.dp, max = ToolFeaturePanelMaxHeightDp.dp)
                } else {
                    Modifier
                        .fillMaxWidth(widthFraction)
                        .heightIn(min = 86.dp, max = maxHeight)
                }
            ),
        shape = shape,
        color = OverlayTokens.panel,
        border = BorderStroke(1.dp, OverlayTokens.panelBorder)
    ) {
        Box(modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp)) {
            when (mode) {
                BottomPanelMode.More -> MoreToolPanel(
                    onClose = onClose,
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
                BottomPanelMode.Card -> AccountCardPickerPanel(
                    accounts = accounts,
                    accountProfiles = accountProfiles,
                    onSendAccountCard = onSendAccountCard
                )
                BottomPanelMode.Moments -> MomentsTimelinePanel(
                    route = scrmMomentsRoute,
                    posts = momentPosts,
                    pendingMedia = pendingMomentMedia,
                    onPickMedia = onPickMomentMedia,
                    onClearMedia = onClearMomentMedia,
                    onPreviewMedia = onPreviewMomentMedia,
                    onUpdatePost = onUpdateMomentPost,
                    onRemotePostsLoaded = { remotePosts ->
                        remotePosts.forEach(onUpdateMomentPost)
                    }
                )
                BottomPanelMode.MomentMaterials -> MomentMaterialsPanel(
                    route = scrmMomentsRoute,
                    onClose = onClose
                )
                BottomPanelMode.Contacts -> ScrmContactsPanel(
                    route = scrmContactsRoute,
                    onClose = onClose,
                    onOpenPrivateChat = onOpenPrivateChat,
                    onOpenFriendProfile = onOpenFriendProfile
                )
                BottomPanelMode.Favorite -> FavoriteCollectionPanel(
                    items = favoriteItems,
                    multiSelectMode = favoriteMultiSelectMode,
                    selectedItemIds = selectedFavoriteItemIds,
                    onPreviewItem = onPreviewFavoriteItem,
                    onLongPressItem = onFavoriteItemLongPress,
                    onToggleSelection = onToggleFavoriteSelection,
                    onForwardSelected = onForwardSelectedFavorites,
                    onDeleteSelected = onDeleteSelectedFavorites,
                    onCancelSelection = onCancelFavoriteSelection
                )
                BottomPanelMode.RedPacket -> PaymentComposerPanel(
                    title = "发红包",
                    amountLabel = "红包金额",
                    noteLabel = "祝福语",
                    defaultNote = "恭喜发财，大吉大利",
                    confirmLabel = "塞钱进红包",
                    recipients = emptyList(),
                    onConfirm = { amount, note, _ -> onSendRedPacket(amount, note) }
                )
                BottomPanelMode.Transfer -> PaymentComposerPanel(
                    title = "转账",
                    amountLabel = "转账金额",
                    noteLabel = "转账说明",
                    defaultNote = "转账给你，请查收",
                    confirmLabel = "确认转账",
                    recipients = transferRecipients,
                    onConfirm = onSendTransfer
                )
                BottomPanelMode.Location -> LocationPickerPanel(
                    permissionRequestToken = locationPermissionRequestToken,
                    onSendLocation = onSendLocation
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
                    onSave = onSaveAiConfig,
                    onTest = onTestAiConfig,
                    onClose = onClose
                )
                BottomPanelMode.AiVoice -> AiVoicePanel(
                    state = aiVoiceState,
                    onEvent = onAiVoiceEvent,
                    onClose = onClose,
                    capabilityConfigState = aiVoiceCapabilityConfigState,
                    onCapabilityConfigEvent = onAiVoiceCapabilityConfigEvent
                )
                BottomPanelMode.None -> Unit
            }
        }
    }
}

private const val ToolFeaturePanelMinWidthDp = 330
private const val ToolFeaturePanelMaxWidthDp = 430
private const val ToolFeaturePanelMaxHeightDp = 560
