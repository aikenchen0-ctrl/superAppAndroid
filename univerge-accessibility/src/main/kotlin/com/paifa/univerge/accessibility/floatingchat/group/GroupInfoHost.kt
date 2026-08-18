package com.paifa.univerge.accessibility.floatingchat.group

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.paifa.univerge.accessibility.data.LocalGroupProfile
import com.paifa.univerge.accessibility.floatingchat.contract.GroupInfoAction
import com.paifa.univerge.accessibility.floatingchat.contract.GroupInfoMemberUiState
import com.paifa.univerge.accessibility.floatingchat.contract.GroupInfoUiEvent
import com.paifa.univerge.accessibility.floatingchat.contract.GroupInfoUiState
import com.paifa.univerge.accessibility.floatingchat.contract.groupInfoAction
import com.paifa.univerge.accessibility.scrm.ScrmChatRoomActionRequest
import com.paifa.univerge.accessibility.scrm.ScrmChatRoomManagementApi
import com.paifa.univerge.accessibility.scrm.ScrmChatRoomMemberMutationRequest
import com.paifa.univerge.accessibility.scrm.ScrmChatRoomSwitchRequest
import com.paifa.univerge.accessibility.scrm.ScrmChatRoomTextRequest
import com.paifa.univerge.accessibility.scrm.ScrmContactTaskRunner
import com.paifa.univerge.accessibility.scrm.ScrmContactTaskOutcome
import com.paifa.univerge.accessibility.scrm.ScrmRefreshChatRoomRequest
import com.paifa.univerge.accessibility.scrm.ScrmRenameChatRoomRequest
import com.paifa.univerge.accessibility.scrm.ScrmSetChatRoomNoticeRequest
import com.paifa.univerge.accessibility.scrm.ScrmSettingsManager
import com.paifa.univerge.accessibility.scrm.ScrmTaskSubmissionResult
import com.paifa.univerge.accessibility.scrm.scrmContactsPanelRouteForSelectedAccount
import com.paifa.univerge.accessibility.scrm.scrmFloatingContactConversationId
import com.paifa.univerge.accessibility.scrm.scrmRouteCurrentDeviceMismatchMessage
import com.paifa.univerge.accessibility.scrm.toScrmContactsPanelMessage
import com.paifa.univerge.core.model.FloatingChatContact
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun GroupInfoHost(
    accountId: String,
    group: FloatingChatContact,
    profile: LocalGroupProfile,
    contacts: List<FloatingChatContact>,
    members: List<FloatingChatContact>,
    onProfileChange: (LocalGroupProfile) -> Unit,
    onMemberClick: (FloatingChatContact) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val manager = remember(context) { ScrmSettingsManager(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var groupName by remember(profile.accountId, profile.groupId, profile.groupName, group.name) {
        mutableStateOf(profile.groupName.ifBlank { group.name })
    }
    var announcement by remember(profile.accountId, profile.groupId, profile.announcement) {
        mutableStateOf(profile.announcement)
    }
    var remark by remember(profile.accountId, profile.groupId, profile.remark) {
        mutableStateOf(profile.remark)
    }
    var myNickname by remember(profile.accountId, profile.groupId, profile.myNickname) {
        mutableStateOf(profile.myNickname.ifBlank { group.initials.ifBlank { group.name.take(2) } })
    }
    var mute by remember(profile.accountId, profile.groupId, profile.mute) {
        mutableStateOf(profile.mute)
    }
    var pinned by remember(profile.accountId, profile.groupId, profile.pinned) {
        mutableStateOf(profile.pinned)
    }
    var saveToContacts by remember(profile.accountId, profile.groupId, profile.saveToContacts) {
        mutableStateOf(profile.saveToContacts)
    }
    var joinVerification by remember(group.id) { mutableStateOf(false) }
    var showMemberNicknames by remember(profile.accountId, profile.groupId, profile.showMemberNicknames) {
        mutableStateOf(profile.showMemberNicknames)
    }
    var showMemberAvatars by remember(profile.accountId, profile.groupId, profile.showMemberAvatars) {
        mutableStateOf(profile.showMemberAvatars)
    }
    var backgroundLabel by remember(profile.accountId, profile.groupId, profile.backgroundLabel) {
        mutableStateOf(profile.backgroundLabel.ifBlank { "榛樿鑳屾櫙" })
    }
    var actionLoading by remember(group.id) { mutableStateOf(false) }
    var actionStatus by remember(group.id) { mutableStateOf<String?>(null) }
    var actionError by remember(group.id) { mutableStateOf<String?>(null) }
    var qrCodeImageUrl by remember(group.id) { mutableStateOf<String?>(null) }
    var qrCodeContent by remember(group.id) { mutableStateOf<String?>(null) }
    var memberPickerMode by remember(group.id) { mutableStateOf<GroupMemberPickerMode?>(null) }
    var showScrmGroupManagement by remember(group.id) { mutableStateOf(false) }
    val route = remember(accountId) {
        scrmContactsPanelRouteForSelectedAccount(
            selectedAccountId = accountId,
            fallbackDeviceUuid = null,
            fallbackWeChatId = null
        )
    }
    val chatRoomId = remember(group.id) { scrmFloatingContactConversationId(group.id) }
    val currentMember = remember(route?.weChatId, members) {
        groupInfoCurrentMemberForRoute(route?.weChatId, members)
    }
    val canManageMembers = groupInfoCanManageMembers(currentMember)
    val memberRows = remember(members, canManageMembers) {
        groupInfoMemberGridRows(members, canManageMembers = canManageMembers)
    }

    fun submitRemoteGroupTask(
        loadingText: String,
        successPrefix: String,
        onSuccess: (() -> Unit)? = null,
        onOutcome: ((ScrmContactTaskOutcome) -> Unit)? = null,
        block: () -> ScrmTaskSubmissionResult
    ) {
        val currentRoute = route
        if (currentRoute == null || chatRoomId.isNullOrBlank()) {
            actionLoading = false
            actionStatus = null
            actionError = "当前群聊缺少 SCRM 路由，无法操作"
            return
        }
        scope.launch {
            actionLoading = true
            actionStatus = loadingText
            actionError = null
            runCatching {
                withContext(Dispatchers.IO) {
                    val session = manager.loadSelectedSessionOrBootstrap()
                    scrmRouteCurrentDeviceMismatchMessage(currentRoute, session.readApi.getDevices())?.let { message ->
                        throw IllegalStateException(message)
                    }
                    ScrmContactTaskRunner(session.taskApi).submitAndAwait(reloadContactsOnSuccess = false) {
                        block()
                    }
                }
            }.onSuccess { outcome ->
                actionLoading = false
                actionStatus = "$successPrefix：${outcome.message}"
                actionError = null
                onSuccess?.invoke()
                onOutcome?.invoke(outcome)
            }.onFailure { error ->
                actionLoading = false
                actionStatus = null
                actionError = error.toScrmContactsPanelMessage()
            }
        }
    }

    fun inviteMembers(selected: List<FloatingChatContact>) {
        val currentRoute = route ?: return
        val targetIds = selected.mapNotNull { contact -> scrmFloatingContactConversationId(contact.id) }.distinct()
        if (targetIds.isEmpty()) {
            actionError = "请选择可邀请的通讯录好友"
            return
        }
        submitRemoteGroupTask(
            loadingText = "正在邀请成员",
            successPrefix = "已提交邀请",
            onSuccess = { memberPickerMode = null }
        ) {
            val session = manager.loadSelectedSessionOrBootstrap()
            session.chatRoomApi.inviteChatRoomMembers(
                ScrmChatRoomMemberMutationRequest(
                    deviceUuid = currentRoute.deviceUuid,
                    weChatId = currentRoute.weChatId,
                    chatRoomId = requireNotNull(chatRoomId),
                    memberWxids = targetIds
                )
            )
        }
    }

    fun kickMembers(selected: List<FloatingChatContact>) {
        val currentRoute = route ?: return
        val targetIds = selected.mapNotNull { member -> scrmFloatingContactConversationId(member.id) }.distinct()
        if (targetIds.isEmpty()) {
            actionError = "请选择要移出的群成员"
            return
        }
        submitRemoteGroupTask(
            loadingText = "正在移出成员",
            successPrefix = "已提交移出",
            onSuccess = { memberPickerMode = null }
        ) {
            val session = manager.loadSelectedSessionOrBootstrap()
            session.chatRoomApi.kickChatRoomMembers(
                ScrmChatRoomMemberMutationRequest(
                    deviceUuid = currentRoute.deviceUuid,
                    weChatId = currentRoute.weChatId,
                    chatRoomId = requireNotNull(chatRoomId),
                    memberWxids = targetIds
                )
            )
        }
    }

    fun persistProfile(
        nextGroupName: String = groupName,
        nextAnnouncement: String = announcement,
        nextRemark: String = remark,
        nextMyNickname: String = myNickname,
        nextMute: Boolean = mute,
        nextPinned: Boolean = pinned,
        nextSaveToContacts: Boolean = saveToContacts,
        nextShowMemberNicknames: Boolean = showMemberNicknames,
        nextShowMemberAvatars: Boolean = showMemberAvatars,
        nextBackgroundLabel: String = backgroundLabel
    ) {
        onProfileChange(
            profile.copy(
                accountId = accountId,
                groupId = group.id,
                groupName = nextGroupName,
                announcement = nextAnnouncement,
                remark = nextRemark,
                myNickname = nextMyNickname,
                mute = nextMute,
                pinned = nextPinned,
                saveToContacts = nextSaveToContacts,
                showMemberNicknames = nextShowMemberNicknames,
                showMemberAvatars = nextShowMemberAvatars,
                backgroundLabel = nextBackgroundLabel,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    fun handleGroupInfoEvent(event: GroupInfoUiEvent) {
        when (val action = groupInfoAction(event)) {
            GroupInfoAction.Back -> onDismiss()
            GroupInfoAction.OpenScrmManagement -> showScrmGroupManagement = true
            GroupInfoAction.InviteMembers -> memberPickerMode = GroupMemberPickerMode.Invite
            GroupInfoAction.RemoveMembers -> {
                if (canManageMembers) memberPickerMode = GroupMemberPickerMode.Kick
                else {
                    actionError = "只有群主或管理员可以移出群成员"
                    actionStatus = null
                }
            }
            is GroupInfoAction.OpenMember -> members.firstOrNull { it.id == action.memberId }?.let(onMemberClick)
            is GroupInfoAction.UpdateGroupName -> {
                groupName = action.value
                persistProfile(nextGroupName = action.value)
            }
            GroupInfoAction.RenameGroup -> {
                val currentRoute = route ?: return
                submitRemoteGroupTask("正在修改群名称", "已提交群名修改") {
                    val session = manager.loadSelectedSessionOrBootstrap()
                    session.chatRoomApi.renameChatRoom(
                        ScrmRenameChatRoomRequest(currentRoute.deviceUuid, currentRoute.weChatId, requireNotNull(chatRoomId), groupName.trim())
                    )
                }
            }
            // 对应 iOS“刷新群资料”，请求手机端回写最新群资料和成员快照。
            GroupInfoAction.RefreshGroup -> {
                val currentRoute = route ?: return
                submitRemoteGroupTask("正在刷新群资料", "已提交群资料刷新") {
                    val session = manager.loadSelectedSessionOrBootstrap()
                    session.chatRoomApi.refreshChatRoom(
                        ScrmRefreshChatRoomRequest(currentRoute.deviceUuid, currentRoute.weChatId, requireNotNull(chatRoomId))
                    )
                }
            }
            GroupInfoAction.LoadQrCode -> {
                val currentRoute = route ?: return
                submitRemoteGroupTask(
                    loadingText = "正在获取群二维码",
                    successPrefix = "已提交群二维码获取",
                    onOutcome = { outcome ->
                        val payload = parseGroupQrCodePayload(outcome.data)
                        qrCodeImageUrl = payload?.imageUrl
                        qrCodeContent = payload?.content
                        if (!outcome.completed || payload == null) {
                            actionError = "群二维码接口未返回可展示内容"
                            actionStatus = null
                        }
                    }
                ) {
                    val session = manager.loadSelectedSessionOrBootstrap()
                    session.chatRoomApi.pullChatRoomQrCode(
                        ScrmChatRoomActionRequest(currentRoute.deviceUuid, currentRoute.weChatId, requireNotNull(chatRoomId))
                    )
                }
            }
            is GroupInfoAction.UpdateAnnouncement -> {
                announcement = action.value
                persistProfile(nextAnnouncement = action.value)
            }
            GroupInfoAction.PublishAnnouncement -> {
                val currentRoute = route ?: return
                submitRemoteGroupTask("正在设置群公告", "已提交群公告") {
                    val session = manager.loadSelectedSessionOrBootstrap()
                    session.chatRoomApi.setChatRoomNotice(
                        ScrmSetChatRoomNoticeRequest(currentRoute.deviceUuid, currentRoute.weChatId, requireNotNull(chatRoomId), announcement.trim())
                    )
                }
            }
            is GroupInfoAction.UpdateRemark -> {
                remark = action.value
                persistProfile(nextRemark = action.value)
            }
            // 群备注使用 /chatrooms/remark，成功后保留已编辑的本地资料镜像。
            GroupInfoAction.SaveRemark -> {
                val currentRoute = route ?: return
                submitRemoteGroupTask("正在保存群备注", "已提交群备注") {
                    val session = manager.loadSelectedSessionOrBootstrap()
                    val api = session.chatRoomApi as? ScrmChatRoomManagementApi
                        ?: error("当前 SCRM 客户端不支持群备注接口")
                    api.setChatRoomRemark(
                        ScrmChatRoomTextRequest(currentRoute.deviceUuid, currentRoute.weChatId, requireNotNull(chatRoomId), remark.trim())
                    )
                }
            }
            GroupInfoAction.SearchChatHistory -> {
                actionStatus = "查找聊天记录入口已保留"
                actionError = null
            }
            GroupInfoAction.TransferOwner,
            GroupInfoAction.ManageManagers -> {
                showScrmGroupManagement = true
            }
            is GroupInfoAction.SetMuted -> {
                val currentRoute = route ?: return
                // API 是“新消息通知”，与 UI 的“消息免打扰”语义相反。
                submitRemoteGroupTask("正在更新消息通知", "已提交消息通知设置", onSuccess = {
                    mute = action.enabled
                    persistProfile(nextMute = action.enabled)
                }) {
                    val session = manager.loadSelectedSessionOrBootstrap()
                    val api = session.chatRoomApi as? ScrmChatRoomManagementApi
                        ?: error("当前 SCRM 客户端不支持消息通知接口")
                    api.setChatRoomNewMessageNotify(
                        ScrmChatRoomSwitchRequest(currentRoute.deviceUuid, currentRoute.weChatId, requireNotNull(chatRoomId), !action.enabled)
                    )
                }
            }
            is GroupInfoAction.SetPinned -> {
                val currentRoute = route ?: return
                submitRemoteGroupTask("正在更新置顶状态", "已提交置顶设置", onSuccess = {
                    pinned = action.enabled
                    persistProfile(nextPinned = action.enabled)
                }) {
                    val session = manager.loadSelectedSessionOrBootstrap()
                    val api = session.chatRoomApi as? ScrmChatRoomManagementApi
                        ?: error("当前 SCRM 客户端不支持置顶群聊接口")
                    api.setChatRoomTop(
                        ScrmChatRoomSwitchRequest(currentRoute.deviceUuid, currentRoute.weChatId, requireNotNull(chatRoomId), action.enabled)
                    )
                }
            }
            is GroupInfoAction.SetSavedToContacts -> {
                val currentRoute = route ?: return
                submitRemoteGroupTask("正在更新通讯录", "已提交通讯录设置", onSuccess = {
                    saveToContacts = action.enabled
                    persistProfile(nextSaveToContacts = action.enabled)
                }) {
                    val session = manager.loadSelectedSessionOrBootstrap()
                    val api = session.chatRoomApi as? ScrmChatRoomManagementApi
                        ?: error("当前 SCRM 客户端不支持通讯录接口")
                    api.setChatRoomSavedToPhonebook(
                        ScrmChatRoomSwitchRequest(currentRoute.deviceUuid, currentRoute.weChatId, requireNotNull(chatRoomId), action.enabled)
                    )
                }
            }
            is GroupInfoAction.SetJoinVerification -> {
                val currentRoute = route ?: return
                submitRemoteGroupTask("正在更新入群验证", "已提交入群验证设置", onSuccess = {
                    joinVerification = action.enabled
                }) {
                    val session = manager.loadSelectedSessionOrBootstrap()
                    val api = session.chatRoomApi as? ScrmChatRoomManagementApi
                        ?: error("当前 SCRM 客户端不支持入群验证接口")
                    api.setChatRoomVerify(
                        ScrmChatRoomSwitchRequest(currentRoute.deviceUuid, currentRoute.weChatId, requireNotNull(chatRoomId), action.enabled)
                    )
                }
            }
            is GroupInfoAction.UpdateMyNickname -> {
                myNickname = action.value
                persistProfile(nextMyNickname = action.value)
            }
            // 与 iOS 的群内昵称编辑对应，Android 已有 /chatrooms/self-display-name 接口时同步到手机端。
            GroupInfoAction.SaveMyNickname -> {
                val currentRoute = route ?: return
                submitRemoteGroupTask("正在保存群内昵称", "已提交群内昵称") {
                    val session = manager.loadSelectedSessionOrBootstrap()
                    val api = session.chatRoomApi as? ScrmChatRoomManagementApi
                        ?: error("当前 SCRM 客户端不支持群内昵称接口")
                    api.setChatRoomSelfDisplayName(
                        ScrmChatRoomTextRequest(currentRoute.deviceUuid, currentRoute.weChatId, requireNotNull(chatRoomId), myNickname.trim())
                    )
                }
            }
            is GroupInfoAction.SetMemberNicknamesVisible -> {
                showMemberNicknames = action.visible
                persistProfile(nextShowMemberNicknames = action.visible)
                actionStatus = if (action.visible) "已显示群成员昵称" else "已隐藏群成员昵称"
                actionError = null
            }
            is GroupInfoAction.SetMemberAvatarsVisible -> {
                showMemberAvatars = action.visible
                persistProfile(nextShowMemberAvatars = action.visible)
                actionStatus = if (action.visible) "已显示群成员头像" else "已隐藏群成员头像"
                actionError = null
            }
            is GroupInfoAction.UpdateBackground -> {
                backgroundLabel = action.value
                persistProfile(nextBackgroundLabel = action.value)
                actionStatus = "已更新悬浮窗口聊天背景"
                actionError = null
            }
            GroupInfoAction.ClearChatHistory -> {
                actionStatus = "已清空悬浮窗口内当前聊天记录显示；微信真实聊天记录未修改"
                actionError = null
            }
            GroupInfoAction.Report -> {
                actionStatus = "举报入口已保留，真实举报流程暂未接入"
                actionError = null
            }
            GroupInfoAction.ExitGroup -> {
                val currentRoute = route
                if (currentRoute == null || chatRoomId.isNullOrBlank()) {
                    actionError = "当前群聊缺少 SCRM 路由，无法退出群聊"
                    actionStatus = null
                } else submitRemoteGroupTask("正在退出群聊", "已提交退出群聊") {
                    val session = manager.loadSelectedSessionOrBootstrap()
                    session.chatRoomApi.exitChatRoom(
                        ScrmChatRoomActionRequest(currentRoute.deviceUuid, currentRoute.weChatId, chatRoomId)
                    )
                }
            }
        }
    }

    memberPickerMode?.let { mode ->
        GroupMemberSelectionPanel(
            title = mode.title,
            contacts = when (mode) {
                GroupMemberPickerMode.Invite -> groupInviteCandidates(contacts, members)
                GroupMemberPickerMode.Kick -> groupKickCandidates(members, currentMember)
            },
            loading = actionLoading,
            status = actionStatus,
            error = actionError,
            onBack = { memberPickerMode = null },
            onDone = { selected ->
                when (mode) {
                    GroupMemberPickerMode.Invite -> inviteMembers(selected)
                    GroupMemberPickerMode.Kick -> kickMembers(selected)
                }
            }
        )
        return
    }

    if (showScrmGroupManagement) {
        ScrmGroupOperationPreviewPanel(
            accountId = accountId,
            group = group,
            members = members,
            onDismiss = { showScrmGroupManagement = false }
        )
        return
    }

    GroupInfoScreen(
        state = GroupInfoUiState(
            memberCount = groupInfoMemberCount(members),
            members = members.map { member ->
                GroupInfoMemberUiState(member.id, member.name, member.initials, member.avatarUrl, member.avatarColor.toInt())
            },
            groupAvatarUrl = group.avatarUrl,
            groupAvatarInitials = group.initials,
            groupAvatarColor = group.avatarColor.toInt(),
            canManageMembers = canManageMembers,
            groupName = groupName,
            announcement = announcement,
            remark = remark,
            myNickname = myNickname,
            muted = mute,
            pinned = pinned,
            savedToContacts = saveToContacts,
            joinVerification = joinVerification,
            memberNicknamesVisible = showMemberNicknames,
            memberAvatarsVisible = showMemberAvatars,
            backgroundLabel = backgroundLabel,
            qrCodeImageUrl = qrCodeImageUrl,
            qrCodeContent = qrCodeContent,
            loading = actionLoading,
            status = actionStatus,
            error = actionError
        ),
        onEvent = ::handleGroupInfoEvent
    )
}
