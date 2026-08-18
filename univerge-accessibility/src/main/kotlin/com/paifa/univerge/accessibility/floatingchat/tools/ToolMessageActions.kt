package com.paifa.univerge.accessibility.floatingchat.tools

import com.paifa.univerge.accessibility.FloatingChatAiAutoReplyBridge
import com.paifa.univerge.accessibility.FloatingChatBlinkVoiceBridge
import com.paifa.univerge.accessibility.FloatingChatMediaPickerBridge
import com.paifa.univerge.accessibility.FloatingChatOpenApiBridge
import com.paifa.univerge.accessibility.FloatingChatFinderPublishBridge
import com.paifa.univerge.accessibility.FloatingChatMaterialLibraryBridge
import com.paifa.univerge.accessibility.FloatingChatMediaTarget
import com.paifa.univerge.accessibility.floatingchat.account.FloatingChatAccountProfile
import com.paifa.univerge.accessibility.floatingchat.account.accountProfileCardMessage
import com.paifa.univerge.accessibility.floatingchat.account.accountProfileMessageForToolAction
import com.paifa.univerge.accessibility.floatingchat.message.OutgoingMessageActions
import com.paifa.univerge.accessibility.floatingchat.shell.BottomPanelMode
import com.paifa.univerge.core.model.FloatingChatContact
import com.paifa.univerge.core.model.FloatingChatConversation
import com.paifa.univerge.core.model.FloatingChatMessage
import com.paifa.univerge.core.model.FloatingChatPrototype
import com.paifa.univerge.core.model.FloatingChatToolAction

internal class ToolMessageActions(
    private val conversation: () -> FloatingChatConversation,
    private val fallbackConversation: () -> FloatingChatConversation,
    private val selectedAccount: () -> FloatingChatContact,
    private val accountProfile: (FloatingChatContact) -> FloatingChatAccountProfile,
    private val outgoingMessageActions: OutgoingMessageActions,
    private val onPendingAvatarAccountIdChanged: (String?) -> Unit,
    private val onBottomPanelModeChanged: (BottomPanelMode) -> Unit,
    private val onAssistantPanelOpened: () -> Unit,
    private val onAiVoicePanelOpened: () -> Unit,
    private val onTransferRequested: () -> Unit
) {
    fun pickAccountAvatar(accountId: String) {
        onPendingAvatarAccountIdChanged(accountId)
        FloatingChatMediaPickerBridge.requestPick(
            mediaKind = FloatingChatPrototype.PickedMediaKind.Image,
            target = FloatingChatMediaTarget.AccountAvatar
        )
    }

    fun addToolMessage(
        action: FloatingChatToolAction,
        customize: (FloatingChatMessage) -> FloatingChatMessage = { it }
    ) {
        val account = selectedAccount()
        outgoingMessageActions.addToolMessage(action) { baseMessage ->
            customize(
                accountProfileMessageForToolAction(
                    action = action,
                    profile = accountProfile(account),
                    baseMessage = baseMessage
                )
            )
        }
        onBottomPanelModeChanged(BottomPanelMode.None)
    }

    /** 音乐页点击“分享”后写入本地音乐消息，不调用不存在的远端 music-card 接口。 */
    fun sendMusicMessage(
        title: String,
        artist: String,
        audioUrl: String?,
        durationLabel: String
    ) {
        outgoingMessageActions.addMusicMessage(title, artist, audioUrl, durationLabel)
        onBottomPanelModeChanged(BottomPanelMode.None)
    }

    /**
     * 创建本地 AA 收款消息并交回当前悬浮会话。该动作没有远端接口，因此不提前关闭面板，
     * 由 `SplitBillFullScreen` 在回调后完成实体退出动画。
     * 测试流程：提交 AA 收款后检查当前会话出现 `FloatingChatMessageType.SplitBill`。
     */
    fun addSplitBillMessage(
        groupName: String,
        totalAmount: String,
        members: List<FloatingChatContact>
    ) {
        outgoingMessageActions.addSplitBillMessage(
            groupName = groupName,
            totalAmount = totalAmount,
            members = members
        )
    }

    fun sendAccountCard(accountId: String) {
        val account = conversation().accountContacts.firstOrNull { it.id == accountId }
            ?: fallbackConversation().accountContacts.firstOrNull { it.id == accountId }
            ?: return
        outgoingMessageActions.addAccountCardMessage(account) { baseMessage ->
            accountProfileCardMessage(profile = accountProfile(account), baseMessage = baseMessage)
        }
        onBottomPanelModeChanged(BottomPanelMode.None)
    }

    fun sendGroupInvite(groupId: String) {
        val group = conversation().contacts.firstOrNull { it.id == groupId }
            ?: fallbackConversation().contacts.firstOrNull { it.id == groupId }
            ?: return
        outgoingMessageActions.addToolMessage(FloatingChatToolAction.GroupInvite) { base ->
            base.copy(cardName = group.name, cardSubtitle = "${group.groupMemberContacts.size} 位成员", text = "邀请加入「${group.name}」")
        }
        onBottomPanelModeChanged(BottomPanelMode.None)
    }

    fun sendToolMessage(action: FloatingChatToolAction) {
        if (action == FloatingChatToolAction.Transfer) {
            // 转账在当前悬浮根内打开 M3 全屏工作区，提交仍由 FloatingChatOverlayUi 的真实 SCRM 回调处理。
            onBottomPanelModeChanged(BottomPanelMode.Transfer)
            return
        }
        when (val dispatch = toolActionDispatchFor(action)) {
            ToolActionDispatch.PickGalleryMedia -> {
                // 图片先进入悬浮全屏工作区，页面内再调用真实系统选择器。
                onBottomPanelModeChanged(BottomPanelMode.Gallery)
            }
            ToolActionDispatch.CaptureBlinkVoice -> {
                onBottomPanelModeChanged(BottomPanelMode.None)
                FloatingChatBlinkVoiceBridge.requestCapture()
            }
            ToolActionDispatch.CaptureCameraMedia -> {
                onBottomPanelModeChanged(BottomPanelMode.None)
                FloatingChatMediaPickerBridge.requestCapture()
            }
            ToolActionDispatch.PickDocument -> {
                // 先进入悬浮根内的 M3 文件工作区，再由页面按钮调用真实系统文档选择器。
                onBottomPanelModeChanged(BottomPanelMode.FileDocument)
            }
            ToolActionDispatch.OpenAssistantPanel -> {
                onBottomPanelModeChanged(BottomPanelMode.None)
                FloatingChatAiAutoReplyBridge.open()
            }
            ToolActionDispatch.OpenAiVoicePanel -> onAiVoicePanelOpened()
            ToolActionDispatch.OpenApiWorkbench -> {
                onBottomPanelModeChanged(BottomPanelMode.None)
                FloatingChatOpenApiBridge.open()
            }
            ToolActionDispatch.OpenFinderPublish -> {
                onBottomPanelModeChanged(BottomPanelMode.None)
                FloatingChatFinderPublishBridge.open()
            }
            ToolActionDispatch.OpenMaterialLibrary -> {
                onBottomPanelModeChanged(BottomPanelMode.None)
                FloatingChatMaterialLibraryBridge.open()
            }
            ToolActionDispatch.OpenFavoriteLibrary -> {
                // 收藏分享使用当前悬浮根视图的全屏工作区，不创建额外 Activity/Window，避免 BadTokenException。
                onBottomPanelModeChanged(BottomPanelMode.Favorite)
            }
            is ToolActionDispatch.OpenBottomPanel -> onBottomPanelModeChanged(dispatch.mode)
            ToolActionDispatch.AddSimulatedMessage -> addToolMessage(action)
            ToolActionDispatch.None -> Unit
        }
    }
}
