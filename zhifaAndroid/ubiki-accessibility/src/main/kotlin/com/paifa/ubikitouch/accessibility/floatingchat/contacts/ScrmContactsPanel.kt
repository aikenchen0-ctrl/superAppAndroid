package com.paifa.ubikitouch.accessibility.floatingchat.contacts

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Textsms
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paifa.ubikitouch.accessibility.FloatingChatMediaPickerBridge
import com.paifa.ubikitouch.accessibility.floatingchat.components.TextLabel
import com.paifa.ubikitouch.accessibility.floatingchat.contract.ContactGroupSummary
import com.paifa.ubikitouch.accessibility.floatingchat.contract.ContactProfileIntroAction
import com.paifa.ubikitouch.accessibility.floatingchat.contract.ContactProfileUiState
import com.paifa.ubikitouch.accessibility.floatingchat.contract.ContactSummary
import com.paifa.ubikitouch.accessibility.floatingchat.contract.ContactsScreenAction
import com.paifa.ubikitouch.accessibility.floatingchat.contract.ContactsShortcut
import com.paifa.ubikitouch.accessibility.floatingchat.contract.ContactsUiEvent
import com.paifa.ubikitouch.accessibility.floatingchat.contract.ContactsUiState
import com.paifa.ubikitouch.accessibility.floatingchat.contract.FriendRequestSummary
import com.paifa.ubikitouch.accessibility.floatingchat.contract.FriendRequestUiState
import com.paifa.ubikitouch.accessibility.floatingchat.contract.contactProfileIntroAction
import com.paifa.ubikitouch.accessibility.floatingchat.contract.contactsScreenAction
import com.paifa.ubikitouch.accessibility.floatingchat.media.rememberAsyncImageThumbnailBitmap
import com.paifa.ubikitouch.accessibility.floatingchat.theme.OverlayTokens
import com.paifa.ubikitouch.accessibility.floatingchat.tools.PanelTextInput
import com.paifa.ubikitouch.accessibility.scrm.ScrmAddFriendRequest
import com.paifa.ubikitouch.accessibility.scrm.ScrmAddFriendsByPhoneRequest
import com.paifa.ubikitouch.accessibility.scrm.ScrmAdminBootstrapResult
import com.paifa.ubikitouch.accessibility.scrm.ScrmAuthenticationException
import com.paifa.ubikitouch.accessibility.scrm.ScrmContact
import com.paifa.ubikitouch.accessibility.scrm.ScrmContactQuery
import com.paifa.ubikitouch.accessibility.scrm.ScrmContactTaskRunner
import com.paifa.ubikitouch.accessibility.scrm.ScrmCreateChatRoomRequest
import com.paifa.ubikitouch.accessibility.scrm.ScrmFindContactRequest
import com.paifa.ubikitouch.accessibility.scrm.ScrmFloatingAccountRoute
import com.paifa.ubikitouch.accessibility.scrm.ScrmFriendRequest
import com.paifa.ubikitouch.accessibility.scrm.ScrmFriendRequestOperation
import com.paifa.ubikitouch.accessibility.scrm.ScrmHandleFriendRequestRequest
import com.paifa.ubikitouch.accessibility.scrm.ScrmInvalidResponseException
import com.paifa.ubikitouch.accessibility.scrm.ScrmSendFriendVerifyRequest
import com.paifa.ubikitouch.accessibility.scrm.ScrmSettingsManager
import com.paifa.ubikitouch.accessibility.scrm.ScrmSyncContactsRequest
import com.paifa.ubikitouch.accessibility.scrm.ScrmTaskSubmissionResult
import com.paifa.ubikitouch.accessibility.scrm.scrmFloatingAccountId
import com.paifa.ubikitouch.accessibility.scrm.scrmFloatingContactId
import com.paifa.ubikitouch.accessibility.scrm.scrmFloatingScopedThreadId
import com.paifa.ubikitouch.accessibility.scrm.scrmRouteCurrentDeviceMismatchMessage
import com.paifa.ubikitouch.accessibility.scrm.toScrmContactsPanelMessage
import com.paifa.ubikitouch.accessibility.scrmConversationPageSize
import com.paifa.ubikitouch.accessibility.shouldRequestNextScrmConversationPage
import com.paifa.ubikitouch.core.model.FloatingChatContact
import java.nio.charset.Charset
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

@Composable
internal fun ScrmContactsPanel(
    route: ScrmFloatingAccountRoute?,
    onClose: () -> Unit,
    onOpenPrivateChat: (ScrmFloatingAccountRoute, ScrmContact) -> Unit,
    onOpenFriendProfile: (ScrmFloatingAccountRoute, ScrmContact) -> Unit
) {
    val context = LocalContext.current
    val manager = remember(context) { ScrmSettingsManager(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var searchText by remember { mutableStateOf("") }
    var addWxidText by remember { mutableStateOf("") }
    var addMessageText by remember { mutableStateOf("你好，我是通过只发添加你的") }
    var state by remember { mutableStateOf(ScrmContactsPanelState()) }
    var panelScreen by remember { mutableStateOf(WechatContactsPanelScreen.Contacts) }
    var showPlusMenu by remember { mutableStateOf(false) }
    var contactsSearchVisible by remember { mutableStateOf(false) }
    val startGroupSelectedContactIds = remember { mutableStateMapOf<Int, Boolean>() }

    if (route == null) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextLabel(
                text = "当前账号缺少 SCRM 路由，无法加载联系人",
                size = 12.sp,
                color = Color(0xFFB65757),
                maxLines = 2
            )
            ScrmPanelButton(label = "关闭", onClick = onClose)
        }
        return
    }

    fun loadContacts(nextSearch: String = searchText) {
        scope.launch {
            state = state.copy(
                loading = true,
                error = null,
                status = "正在加载联系人"
            )
            runCatching {
                withContext(Dispatchers.IO) {
                    fun fetchContacts(): ScrmContactsPanelLoadResult {
                        val session = manager.loadSelectedSessionOrBootstrap()
                        val allContacts = mutableListOf<ScrmContact>()
                        var totalCount = 0
                        var pageNumber = 1
                        var returnedItemCount = 0
                        do {
                            val page = session.contactApi.getContacts(
                                scrmContactsPanelContactQuery(
                                    weChatId = route.weChatId,
                                    pageNumber = pageNumber,
                                    search = nextSearch
                                )
                            )
                            allContacts += page.items
                            totalCount = page.totalCount
                            returnedItemCount = page.items.size
                            pageNumber += 1
                        } while (shouldRequestNextScrmConversationPage(
                                returnedItemCount = returnedItemCount,
                                loadedItemCount = allContacts.size,
                                totalCount = totalCount,
                                pageSize = scrmConversationPageSize()
                            )
                        )
                        val requests = session.contactApi.getFriendRequests(
                            weChatId = route.weChatId,
                            count = 20,
                            pendingOnly = true
                        )
                        return ScrmContactsPanelLoadResult(
                            contacts = allContacts,
                            totalCount = totalCount.takeIf { it > 0 } ?: allContacts.size,
                            friendRequests = requests
                        )
                    }

                    try {
                        fetchContacts()
                    } catch (error: ScrmAuthenticationException) {
                        when (manager.bootstrapWithBundledAdminCredentials()) {
                            is ScrmAdminBootstrapResult.Success -> fetchContacts()
                            else -> throw error
                        }
                    }
                }
            }.onSuccess { result ->
                val selected = state.selectedContact?.let { selected ->
                    result.contacts.firstOrNull { contact ->
                        contact.id == selected.id || contact.wxid == selected.wxid
                    }
                }
                state = state.copy(
                    loading = false,
                    contacts = result.contacts,
                    totalCount = result.totalCount,
                    friendRequests = result.friendRequests,
                    selectedContact = selected,
                    status = "已加载 ${result.contacts.size}/${result.totalCount} 个联系人",
                    error = null
                )
            }.onFailure { error ->
                state = state.copy(
                    loading = false,
                    error = error.toScrmContactsPanelMessage(),
                    status = null
                )
            }
        }
    }

    fun submitTask(
        status: String,
        reloadContactsOnSuccess: Boolean = true,
        successStatus: ((String) -> String)? = null,
        onSuccess: (() -> Unit)? = null,
        block: suspend () -> ScrmTaskSubmissionResult
    ) {
        scope.launch {
            state = state.copy(loading = true, error = null, status = status)
            runCatching {
                withContext(Dispatchers.IO) {
                    val session = manager.loadSelectedSessionOrBootstrap()
                    scrmRouteCurrentDeviceMismatchMessage(route, session.readApi.getDevices())?.let { message ->
                        throw IllegalStateException(message)
                    }
                    val submitted = block()
                    ScrmContactTaskRunner(session.taskApi).submitAndAwait(
                        reloadContactsOnSuccess = reloadContactsOnSuccess
                    ) {
                        submitted
                    }
                }
            }
                .onSuccess { outcome ->
                    val taskId = outcome.taskId
                    state = state.copy(
                        loading = false,
                        status = successStatus?.invoke(outcome.message) ?: "已提交任务 #$taskId",
                        error = null
                    )
                    if (successStatus == null) {
                        state = state.copy(status = outcome.message)
                    }
                    if (outcome.shouldReloadContacts) {
                        loadContacts()
                    }
                    onSuccess?.invoke()
                }.onFailure { error ->
                    state = state.copy(
                        loading = false,
                        error = error.toScrmContactsPanelMessage(),
                        status = null
                    )
                }
        }
    }

    fun selectedRemoteId(contact: ScrmContact): String? {
        return scrmContactPrimaryConversationId(contact)
    }

    fun syncContacts() {
        submitTask("正在提交联系人同步") {
            val session = manager.loadSelectedSessionOrBootstrap()
            session.contactApi.syncContacts(
                ScrmSyncContactsRequest(
                    deviceUuid = route.deviceUuid,
                    weChatId = route.weChatId
                )
            )
        }
    }

    fun searchFriendForAdd() {
        val query = addWxidText.trim()
        if (query.isBlank()) {
            state = state.copy(error = "请输入微信号、手机号或搜索关键词")
            return
        }
        scope.launch {
            state = state.copy(
                loading = true,
                error = null,
                status = "正在搜索好友",
                friendSearchProfile = null
            )
            runCatching {
                withContext(Dispatchers.IO) {
                    val session = manager.loadSelectedSessionOrBootstrap()
                    val runner = ScrmContactTaskRunner(session.taskApi)
                    val outcome = runner.submitAndAwait(reloadContactsOnSuccess = false) {
                        session.contactApi.findFriend(
                            ScrmFindContactRequest(
                                deviceUuid = route.deviceUuid,
                                weChatId = route.weChatId,
                                content = query
                            )
                        )
                    }
                    val profile = scrmFriendSearchProfileFromFindContactData(outcome.data)
                    if (profile == null && !outcome.completed) {
                        return@withContext ScrmFriendSearchResult.Pending(outcome.message)
                    }
                    ScrmFriendSearchResult.Found(
                        profile ?: throw ScrmInvalidResponseException(
                            "好友搜索已完成，但服务端没有返回目标用户资料"
                        )
                    )
                }
            }.onSuccess { result ->
                state = when (result) {
                    is ScrmFriendSearchResult.Found -> state.copy(
                        loading = false,
                        friendSearchProfile = result.profile,
                        status = "已找到 ${result.profile.displayName}",
                        error = null
                    )
                    is ScrmFriendSearchResult.Pending -> state.copy(
                        loading = false,
                        friendSearchProfile = null,
                        status = result.message,
                        error = null
                    )
                }
            }.onFailure { error ->
                state = state.copy(
                    loading = false,
                    friendSearchProfile = null,
                    error = error.toScrmContactsPanelMessage(),
                    status = null
                )
            }
        }
    }

    fun sendFriendVerifyFromSearch(profile: ScrmFriendSearchProfile) {
        scope.launch {
            state = state.copy(loading = true, error = null, status = "正在发送好友申请")
            runCatching {
                withContext(Dispatchers.IO) {
                    val session = manager.loadSelectedSessionOrBootstrap()
                    ScrmContactTaskRunner(session.taskApi).submitAndAwait(
                        reloadContactsOnSuccess = true
                    ) {
                        session.contactApi.sendFriendVerify(
                            ScrmSendFriendVerifyRequest(
                                deviceUuid = route.deviceUuid,
                                weChatId = route.weChatId,
                                friendId = profile.friendId,
                                message = addMessageText.trim().takeIf { it.isNotEmpty() }
                            )
                        )
                    }
                }
            }.onSuccess { outcome ->
                state = state.copy(
                    loading = false,
                    friendSearchProfile = null,
                    status = outcome.message,
                    error = null
                )
                if (outcome.shouldReloadContacts) {
                    loadContacts()
                }
            }.onFailure { error ->
                state = state.copy(
                    loading = false,
                    error = error.toScrmContactsPanelMessage(),
                    status = null
                )
            }
        }
    }

    fun addFriend() {
        val account = addWxidText.trim()
        if (account.isBlank()) {
            state = state.copy(error = "请输入微信号、手机号或账号")
            return
        }
        submitTask(
            status = "正在发送好友申请",
            reloadContactsOnSuccess = false,
            successStatus = ::friendApplySubmittedStatus
        ) {
            val session = manager.loadSelectedSessionOrBootstrap()
            if (scrmAddFriendInputLooksLikePhone(account)) {
                session.contactApi.addFriendsByPhone(
                    ScrmAddFriendsByPhoneRequest(
                        deviceUuid = route.deviceUuid,
                        weChatId = route.weChatId,
                        phones = listOf(account),
                        message = addMessageText.trim().takeIf { it.isNotEmpty() }
                    )
                )
            } else {
                session.contactApi.addFriend(
                    scrmDirectAddFriendRequest(
                        deviceUuid = route.deviceUuid,
                        weChatId = route.weChatId,
                        friendAccount = account,
                        message = addMessageText
                    )
                )
            }
        }
    }

    fun openCameraForScan() {
        Toast.makeText(context, "正在打开相机", Toast.LENGTH_SHORT).show()
        FloatingChatMediaPickerBridge.requestCapture()
    }

    fun deleteSelectedFriend() {
        val contact = state.selectedContact ?: return
        val friendId = selectedRemoteId(contact)
        if (friendId == null) {
            state = state.copy(error = "当前联系人缺少可删除的 wxid")
            return
        }
        submitTask("正在提交删除好友") {
            val session = manager.loadSelectedSessionOrBootstrap()
            session.contactApi.deleteFriend(
                friendId = friendId,
                deviceUuid = route.deviceUuid,
                weChatId = route.weChatId
            )
        }
    }

    fun handleFriendRequest(
        request: ScrmFriendRequest,
        operation: ScrmFriendRequestOperation
    ) {
        val friendId = request.requestWxid?.takeIf { it.isNotBlank() }
            ?: request.id.toString()
        submitTask(if (operation == ScrmFriendRequestOperation.Accept) "正在通过好友申请" else "正在拒绝好友申请") {
            val session = manager.loadSelectedSessionOrBootstrap()
            session.contactApi.handleFriendRequest(
                ScrmHandleFriendRequestRequest(
                    deviceUuid = route.deviceUuid,
                    weChatId = route.weChatId,
                    friendId = friendId,
                    friendNick = request.displayName,
                    remark = request.displayName,
                    replyMsg = if (operation == ScrmFriendRequestOperation.Accept) "已通过" else "暂不添加",
                    operation = operation
                )
            )
        }
    }

    fun createChatRoomFromSelectedContacts() {
        val selectedContacts = state.contacts.filter { contact ->
            startGroupSelectedContactIds[contact.id] == true
        }
        val memberWxids = scrmCreateChatRoomMemberWxids(selectedContacts)
        if (memberWxids.isEmpty()) {
            state = state.copy(error = "请选择可拉群的通讯录好友")
            return
        }
        submitTask(
            status = "正在发起群聊",
            reloadContactsOnSuccess = false,
            successStatus = { message -> "已提交建群任务：$message" },
            onSuccess = {
                startGroupSelectedContactIds.clear()
                panelScreen = WechatContactsPanelScreen.Contacts
            }
        ) {
            val session = manager.loadSelectedSessionOrBootstrap()
            session.chatRoomApi.createChatRoom(
                ScrmCreateChatRoomRequest(
                    deviceUuid = route.deviceUuid,
                    weChatId = route.weChatId,
                    memberWxids = memberWxids
                )
            )
        }
    }

    LaunchedEffect(route) {
        startGroupSelectedContactIds.clear()
        loadContacts()
    }

    val contactGroups = remember(state.contacts, searchText) {
        scrmContactGroupSummaries(state.contacts, searchText)
    }
    val contactsById = remember(state.contacts) {
        scrmContactsBySummaryId(state.contacts)
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        when (panelScreen) {
            WechatContactsPanelScreen.Contacts -> ContactsScreen(
                state = ContactsUiState(
                    query = searchText,
                    searchVisible = contactsSearchVisible,
                    contacts = contactGroups.flatMap { group -> group.contacts },
                    groups = contactGroups,
                    friendRequests = state.friendRequests.map { request -> request.toFriendRequestSummary() },
                    loading = state.loading,
                    status = state.status,
                    error = state.error
                ),
                onEvent = { event ->
                    when (val action = contactsScreenAction(event)) {
                        is ContactsScreenAction.UpdateQuery -> searchText = action.value
                        is ContactsScreenAction.SetSearchVisible -> {
                            contactsSearchVisible = action.visible
                        }
                        ContactsScreenAction.SubmitSearch -> loadContacts(searchText)
                        ContactsScreenAction.Sync -> syncContacts()
                        ContactsScreenAction.Close -> onClose()
                        ContactsScreenAction.TogglePlusMenu -> showPlusMenu = !showPlusMenu
                        ContactsScreenAction.OpenFriendRequests -> {
                            panelScreen = WechatContactsPanelScreen.FriendRequests
                            showPlusMenu = false
                        }
                        is ContactsScreenAction.ShowPlaceholder -> {
                            Toast.makeText(
                                context,
                                "${action.shortcut.placeholderLabel()} 暂未接入",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        is ContactsScreenAction.OpenContact -> {
                            contactsById[action.contactId]?.let { contact ->
                                state = state.copy(selectedContact = contact, error = null)
                                addWxidText = contact.wxid.orEmpty()
                                panelScreen = WechatContactsPanelScreen.ContactIntro
                                showPlusMenu = false
                            }
                        }
                        ContactsScreenAction.Ignore -> Unit
                    }
                }
            )
            WechatContactsPanelScreen.AddFriend -> WechatAddFriendPanel(
                query = addWxidText,
                onQueryChange = { addWxidText = it },
                verifyMessage = addMessageText,
                onVerifyMessageChange = { addMessageText = it },
                loading = state.loading,
                status = state.status,
                error = state.error,
                onBack = {
                    panelScreen = WechatContactsPanelScreen.Contacts
                    state = state.copy(friendSearchProfile = null, error = null)
                },
                onAdd = ::addFriend,
                onScan = ::openCameraForScan,
                onPlaceholderClick = { label ->
                    Toast.makeText(context, "$label 暂未接入", Toast.LENGTH_SHORT).show()
                }
            )
            WechatContactsPanelScreen.ContactIntro -> {
                val selectedContact = state.selectedContact
                ContactProfileScreen(
                    state = selectedContact.toContactIntroUiState(loading = state.loading),
                    onEvent = { event ->
                        when (contactProfileIntroAction(event)) {
                            ContactProfileIntroAction.Back -> {
                                panelScreen = WechatContactsPanelScreen.Contacts
                            }
                            ContactProfileIntroAction.SendMessage -> {
                                selectedContact?.let { contact -> onOpenPrivateChat(route, contact) }
                            }
                            ContactProfileIntroAction.StartVoiceCall -> {
                                Toast.makeText(context, "语音通话暂未接入", Toast.LENGTH_SHORT).show()
                            }
                            ContactProfileIntroAction.StartVideoCall -> {
                                Toast.makeText(context, "视频通话暂未接入", Toast.LENGTH_SHORT).show()
                            }
                            ContactProfileIntroAction.Edit -> {
                                selectedContact?.let { contact -> onOpenFriendProfile(route, contact) }
                            }
                            ContactProfileIntroAction.ShowMoments -> {
                                Toast.makeText(context, "朋友圈暂未接入", Toast.LENGTH_SHORT).show()
                            }
                            ContactProfileIntroAction.Ignore -> Unit
                        }
                    }
                )
            }
            WechatContactsPanelScreen.FriendRequests -> {
                val requestsById = state.friendRequests.associateBy { request -> request.id.toString() }
                FriendRequestScreen(
                    state = FriendRequestUiState(
                        requests = state.friendRequests.map { request -> request.toFriendRequestSummary() },
                        enabled = !state.loading
                    ),
                    onEvent = { event ->
                        when (event) {
                            ContactsUiEvent.BackRequested -> {
                                panelScreen = WechatContactsPanelScreen.Contacts
                            }
                            is ContactsUiEvent.AcceptRequest -> {
                                requestsById[event.requestId]?.let { request ->
                                    handleFriendRequest(request, ScrmFriendRequestOperation.Accept)
                                }
                            }
                            is ContactsUiEvent.RejectRequest -> {
                                requestsById[event.requestId]?.let { request ->
                                    handleFriendRequest(request, ScrmFriendRequestOperation.Reject)
                                }
                            }
                            else -> Unit
                        }
                    }
                )
            }
            WechatContactsPanelScreen.StartGroup -> WechatStartGroupPanel(
                contacts = state.contacts,
                selectedContactIds = startGroupSelectedContactIds,
                loading = state.loading,
                status = state.status,
                error = state.error,
                onBack = {
                    startGroupSelectedContactIds.clear()
                    panelScreen = WechatContactsPanelScreen.Contacts
                    state = state.copy(error = null)
                },
                onDone = ::createChatRoomFromSelectedContacts,
                onPlaceholderClick = { label ->
                    Toast.makeText(context, "$label 暂未接入", Toast.LENGTH_SHORT).show()
                }
            )
        }
        if (showPlusMenu) {
            WechatContactsPlusMenu(
                onDismiss = { showPlusMenu = false },
                onAction = { action ->
                    showPlusMenu = false
                    when (action) {
                        WechatContactsPlusAction.StartGroup -> {
                            startGroupSelectedContactIds.clear()
                            panelScreen = WechatContactsPanelScreen.StartGroup
                            state = state.copy(error = null)
                        }
                        WechatContactsPlusAction.Payment -> {
                            Toast.makeText(context, "${action.label} 暂未接入", Toast.LENGTH_SHORT).show()
                        }
                        WechatContactsPlusAction.AddFriend -> {
                            panelScreen = WechatContactsPanelScreen.AddFriend
                            state = state.copy(friendSearchProfile = null, error = null)
                        }
                        WechatContactsPlusAction.Scan -> openCameraForScan()
                    }
                },
                modifier = Modifier.align(Alignment.TopEnd)
            )
        }
    }
}

@Composable
private fun WechatStartGroupPanel(
    contacts: List<ScrmContact>,
    selectedContactIds: MutableMap<Int, Boolean>,
    loading: Boolean,
    status: String?,
    error: String?,
    onBack: () -> Unit,
    onDone: () -> Unit,
    onPlaceholderClick: (String) -> Unit
) {
    var searchText by remember { mutableStateOf("") }
    val visibleContacts = remember(contacts, searchText) {
        filterWechatContacts(contacts, searchText)
    }
    val sections = remember(visibleContacts) { groupedWechatContactSections(visibleContacts) }
    val selectedCount = selectedContactIds.values.count { selected -> selected }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(WechatContactsPageBackground)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .background(WechatContactsHeaderBackground)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(34.dp)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    tint = WechatContactsPrimaryText,
                    modifier = Modifier.size(21.dp)
                )
            }
            TextLabel(
                text = wechatStartGroupTitle(),
                size = 16.sp,
                weight = FontWeight.Bold,
                color = WechatContactsPrimaryText,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            ScrmPanelButton(
                label = wechatStartGroupDoneLabel(selectedCount),
                enabled = selectedCount > 0 && !loading,
                accent = true,
                onClick = onDone
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(WechatContactsHeaderBackground)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            WechatSearchField(
                value = searchText,
                onValueChange = { searchText = it },
                placeholder = "鎼滅储",
                onSearch = {},
                modifier = Modifier.weight(1f)
            )
        }
        WechatContactsStatusText(status = status, error = error)
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 360.dp, max = 520.dp)
        ) {
            wechatStartGroupOptionLabels().forEachIndexed { index, label ->
                item(key = "start-group-option-$index") {
                    WechatStartGroupOptionRow(label = label, onClick = { onPlaceholderClick(label) })
                }
            }
            item(key = "contacts-header") {
                WechatContactSectionHeader("选择联系人")
            }
            if (sections.isEmpty()) {
                item(key = "empty") {
                    TextLabel(
                        text = if (loading) "正在加载..." else "暂无可选择联系人",
                        size = 12.sp,
                        color = WechatContactsSecondaryText,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                        maxLines = 1
                    )
                }
            }
            sections.forEach { section ->
                item(key = "start-section-${section.title}") {
                    WechatContactSectionHeader(section.title)
                }
                itemsIndexed(
                    items = section.contacts,
                    key = { _, contact -> "start-contact-${contact.id}" }
                ) { _, contact ->
                    WechatStartGroupContactRow(
                        contact = contact,
                        selected = selectedContactIds[contact.id] == true,
                        enabled = !loading,
                        onToggle = {
                            if (selectedContactIds[contact.id] == true) {
                                selectedContactIds.remove(contact.id)
                            } else {
                                selectedContactIds[contact.id] = true
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun WechatContactsStatusText(
    status: String?,
    error: String?
) {
    val message = error?.takeIf { it.isNotBlank() } ?: status?.takeIf { it.isNotBlank() }
    if (message == null) return
    TextLabel(
        text = message,
        size = 10.sp,
        color = if (error != null) Color(0xFFE45858) else WechatContactsSecondaryText,
        maxLines = 2,
        lineHeight = 13.sp,
        modifier = Modifier
            .fillMaxWidth()
            .background(WechatContactsPageBackground)
            .padding(horizontal = 18.dp, vertical = 6.dp)
    )
}

@Composable
private fun WechatStartGroupOptionRow(
    label: String,
    onClick: () -> Unit
) {
    val icon = when (label) {
        "选择一个群" -> Icons.Filled.Groups
        "面对面建群" -> Icons.Filled.Radar
        else -> Icons.Filled.Contacts
    }
    WechatContactShortcutRow(
        icon = icon,
        iconColor = Color(0xFF2F93E8),
        title = label,
        onClick = onClick
    )
}

@Composable
private fun WechatStartGroupContactRow(
    contact: ScrmContact,
    selected: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(WechatContactsRowBackground)
            .clickable(enabled = enabled, onClick = onToggle)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = if (selected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (selected) Color(0xFF1AAD19) else WechatContactsSecondaryText,
            modifier = Modifier.size(22.dp)
        )
        WechatContactAvatar(contact = contact)
        TextLabel(
            text = contact.displayName,
            size = 15.sp,
            color = WechatContactsPrimaryText,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun WechatContactsTopBar(
    title: String,
    showBack: Boolean,
    onBack: () -> Unit,
    onSearchClick: (() -> Unit)? = null,
    onPlusClick: (() -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(WechatContactsHeaderBackground)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        if (showBack) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(34.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    tint = WechatContactsPrimaryText,
                    modifier = Modifier.size(21.dp)
                )
            }
        }
        TextLabel(
            text = title,
            size = 16.sp,
            weight = FontWeight.Bold,
            color = WechatContactsPrimaryText,
            maxLines = 1
        )
        Row(
            modifier = Modifier.align(Alignment.CenterEnd),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            onSearchClick?.let { click ->
                IconButton(onClick = click, modifier = Modifier.size(34.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        tint = WechatContactsPrimaryText,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            onPlusClick?.let { click ->
                IconButton(onClick = click, modifier = Modifier.size(34.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        tint = WechatContactsPrimaryText,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun WechatContactsStatusLine(
    loading: Boolean,
    status: String?,
    error: String?,
    onSync: () -> Unit
) {
    val message = wechatContactsStatusText(loading = loading, status = status, error = error)
    if (message == null && !loading) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(WechatContactsPageBackground)
            .padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TextLabel(
            text = message.orEmpty(),
            size = 10.sp,
            color = if (error != null) Color(0xFFE45858) else WechatContactsSecondaryText,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        ScrmPanelButton(label = "鍚屾", enabled = !loading, onClick = onSync)
    }
}

@Composable
private fun WechatSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .height(36.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(WechatContactsSearchBackground)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = null,
            tint = WechatContactsHintText,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            textStyle = TextStyle.Default.copy(
                color = WechatContactsPrimaryText,
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal
            ),
            modifier = Modifier.weight(1f)
        ) { innerTextField ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.isBlank()) {
                    TextLabel(
                        text = placeholder,
                        size = 14.sp,
                        color = WechatContactsHintText,
                        maxLines = 1
                    )
                }
                innerTextField()
            }
        }
    }
}

@Composable
private fun WechatContactsPlusMenu(
    onDismiss: () -> Unit,
    onAction: (WechatContactsPlusAction) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onDismiss() })
            },
        contentAlignment = Alignment.TopEnd
    ) {
        Column(
            modifier = Modifier
                .padding(top = 40.dp, end = 6.dp)
                .width(188.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xEE3E3E3E))
                .pointerInput(Unit) {
                    detectTapGestures(onTap = {})
                }
                .padding(vertical = 6.dp)
        ) {
            WechatContactsPlusAction.values().forEach { action ->
                WechatPlusMenuRow(action = action, onClick = { onAction(action) })
            }
        }
    }
}

@Composable
private fun WechatPlusMenuRow(
    action: WechatContactsPlusAction,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = when (action) {
                WechatContactsPlusAction.StartGroup -> Icons.Filled.Textsms
                WechatContactsPlusAction.AddFriend -> Icons.Filled.PersonAdd
                WechatContactsPlusAction.Scan -> Icons.Filled.CropFree
                WechatContactsPlusAction.Payment -> Icons.Filled.CreditCard
            },
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(22.dp)
        )
        TextLabel(
            text = action.label,
            size = 15.sp,
            color = Color.White,
            maxLines = 1
        )
    }
}

@Composable
private fun WechatContactShortcutRow(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(WechatContactsRowBackground)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(iconColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 40.dp),
            verticalArrangement = Arrangement.Center
        ) {
            TextLabel(
                text = title,
                size = 15.sp,
                color = WechatContactsPrimaryText,
                maxLines = 1
            )
            subtitle?.takeIf { it.isNotBlank() }?.let { value ->
                TextLabel(
                    text = value,
                    size = 10.sp,
                    color = WechatContactsSecondaryText,
                    maxLines = 1
                )
            }
        }
        Box(
            modifier = Modifier
                .height(1.dp)
                .width(0.dp)
        )
    }
}

@Composable
private fun WechatContactSectionHeader(title: String) {
    TextLabel(
        text = title,
        size = 11.sp,
        color = WechatContactsSecondaryText,
        modifier = Modifier
            .fillMaxWidth()
            .background(WechatContactsPageBackground)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        maxLines = 1
    )
}

@Composable
private fun WechatContactAvatar(contact: ScrmContact, size: Dp = 42.dp) {
    val context = LocalContext.current
    val bitmap = rememberAsyncImageThumbnailBitmap(
        context = context,
        uriText = contact.avatar?.takeIf { it.isNotBlank() }
    )
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(4.dp))
            .background(wechatContactAvatarColor(contact)),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            TextLabel(
                text = contact.displayName.take(2),
                size = 12.sp,
                weight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1
            )
        }
    }
}

private fun ScrmContact?.toContactIntroUiState(loading: Boolean): ContactProfileUiState {
    val contact = this
    return ContactProfileUiState(
        editing = false,
        contactId = contact?.id?.toString(),
        displayName = contact?.displayName.orEmpty(),
        originalName = contact?.nickname.orEmpty(),
        initials = contact?.displayName.orEmpty().take(2),
        avatarUrl = contact?.avatar?.takeIf { it.isNotBlank() },
        avatarColor = contact?.let(::wechatContactAvatarColor)?.toArgb() ?: 0xFF5B7CFA.toInt(),
        wechatId = contact?.wxid.orEmpty().ifBlank { contact?.friendNo.orEmpty() },
        region = contact?.sourceExt.orEmpty().ifBlank { contact?.source.orEmpty() },
        loading = loading
    )
}
@Composable
private fun WechatAddFriendPanel(
    query: String,
    onQueryChange: (String) -> Unit,
    verifyMessage: String,
    onVerifyMessageChange: (String) -> Unit,
    loading: Boolean,
    status: String?,
    error: String?,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onScan: () -> Unit,
    onPlaceholderClick: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(WechatContactsRowBackground)
    ) {
        WechatContactsTopBar(title = "添加朋友", showBack = true, onBack = onBack)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            WechatSearchField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = "账号/手机号",
                onSearch = onAdd,
                modifier = Modifier.weight(1f)
            )
            ScrmPanelButton(label = "添加", enabled = !loading, accent = true, onClick = onAdd)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PanelTextInput(
                value = verifyMessage,
                onValueChange = onVerifyMessageChange,
                placeholder = "验证消息",
                modifier = Modifier.weight(1f)
            )
        }
        WechatContactsStatusLine(loading = loading, status = status, error = error, onSync = {})
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 320.dp, max = 450.dp)
        ) {
            item {
                WechatAddFriendEntryRow(
                    icon = Icons.Filled.CropFree,
                    iconColor = Color(0xFF1688FF),
                    title = "扫一扫",
                    subtitle = "扫描二维码名片",
                    onClick = onScan
                )
            }
            item {
                WechatAddFriendEntryRow(
                    icon = Icons.Filled.PhoneAndroid,
                    iconColor = Color(0xFF08C160),
                    title = "手机联系人",
                    subtitle = "添加通讯录中的好友",
                    onClick = { onPlaceholderClick("手机联系人") }
                )
            }
            item {
                WechatAddFriendEntryRow(
                    icon = Icons.Filled.Radar,
                    iconColor = Color(0xFF6A72FF),
                    title = "闆疯揪",
                    subtitle = "添加身边的朋友",
                    onClick = { onPlaceholderClick("闆疯揪") }
                )
            }
            item {
                WechatAddFriendEntryRow(
                    icon = Icons.Filled.Contacts,
                    iconColor = Color(0xFF1688FF),
                    title = "企业微信联系人",
                    subtitle = "通过手机号搜索企业微信用户",
                    onClick = { onPlaceholderClick("企业微信联系人") }
                )
            }
            item {
                WechatAddFriendEntryRow(
                    icon = Icons.Filled.Groups,
                    iconColor = Color(0xFF08C160),
                    title = "面对面建群",
                    subtitle = "与身边的朋友进入同一个群聊",
                    onClick = { onPlaceholderClick("面对面建群") }
                )
            }
            item {
                WechatAddFriendEntryRow(
                    icon = Icons.Filled.MenuBook,
                    iconColor = Color(0xFF1688FF),
                    title = "公众号",
                    subtitle = "鑾峰彇鏇村璧勮",
                    onClick = { onPlaceholderClick("公众号") }
                )
            }
            item {
                WechatAddFriendEntryRow(
                    icon = Icons.Filled.Article,
                    iconColor = Color(0xFFFF5A48),
                    title = "服务号",
                    subtitle = "获取更多购物信息和服务",
                    onClick = { onPlaceholderClick("服务号") }
                )
            }
        }
    }
}

@Composable
private fun WechatAddFriendEntryRow(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(WechatContactsRowBackground)
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(25.dp)
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            TextLabel(
                text = title,
                size = 15.sp,
                color = WechatContactsPrimaryText,
                maxLines = 1
            )
            TextLabel(
                text = subtitle,
                size = 11.sp,
                color = WechatContactsSecondaryText,
                maxLines = 1
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = WechatContactsChevronText,
            modifier = Modifier.size(20.dp)
        )
    }
}

private fun wechatContactAvatarColor(contact: ScrmContact): Color {
    val palette = listOf(
        Color(0xFF5B8EB7),
        Color(0xFF58A36D),
        Color(0xFFB97A56),
        Color(0xFF7B73B7),
        Color(0xFFB75B76)
    )
    return palette[(contact.id and Int.MAX_VALUE) % palette.size]
}

@Composable
private fun ScrmFriendSearchProfilePanel(
    profile: ScrmFriendSearchProfile,
    message: String,
    onMessageChange: (String) -> Unit,
    enabled: Boolean,
    onApply: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val avatarBitmap = rememberAsyncImageThumbnailBitmap(
        context = context,
        uriText = profile.avatarUrl?.takeIf { it.isNotBlank() }
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(OverlayTokens.resourcePanel)
            .border(1.dp, OverlayTokens.resourcePanelBorder, RoundedCornerShape(10.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF5674A8)),
                contentAlignment = Alignment.Center
            ) {
                if (avatarBitmap != null) {
                    Image(
                        bitmap = avatarBitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    TextLabel(
                        text = profile.displayName.take(2),
                        size = 12.sp,
                        weight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1
                    )
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                TextLabel(
                    text = profile.displayName,
                    size = 13.sp,
                    weight = FontWeight.Bold,
                    color = OverlayTokens.panelPrimaryText,
                    maxLines = 1
                )
                TextLabel(
                    text = profile.wechatId ?: profile.friendId,
                    size = 10.sp,
                    color = OverlayTokens.panelSecondaryText,
                    maxLines = 1
                )
            }
            ScrmPanelButton(label = "取消", enabled = enabled, onClick = onClose)
        }
        listOfNotNull(
            profile.phone?.takeIf { it.isNotBlank() }?.let { "电话 $it" },
            profile.region?.takeIf { it.isNotBlank() }?.let { "鍦板尯 $it" },
            profile.signature?.takeIf { it.isNotBlank() }?.let { "绛惧悕 $it" },
            profile.source?.takeIf { it.isNotBlank() }?.let { "鏉ユ簮 $it" }
        ).forEach { line ->
            TextLabel(
                text = line,
                size = 10.sp,
                color = OverlayTokens.panelSecondaryText,
                maxLines = 1
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            PanelTextInput(
                value = message,
                onValueChange = onMessageChange,
                placeholder = "验证消息",
                modifier = Modifier.weight(1f)
            )
            ScrmPanelButton(label = "发送申请", enabled = enabled, accent = true, onClick = onApply)
        }
    }
}

@Composable
private fun ScrmSelectedContactPanel(
    contact: ScrmContact?,
    sendText: String,
    onSendTextChange: (String) -> Unit,
    onSendText: () -> Unit,
    onDelete: () -> Unit,
    enabled: Boolean
) {
    if (contact == null) {
        TextLabel(
            text = "选择一个联系人后可发送消息或删除好友",
            size = 10.sp,
            color = OverlayTokens.panelSecondaryText,
            maxLines = 1
        )
        return
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .background(OverlayTokens.resourcePanel)
            .border(1.dp, OverlayTokens.resourcePanelBorder, RoundedCornerShape(9.dp))
            .padding(9.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                TextLabel(
                    text = contact.displayName,
                    size = 12.sp,
                    weight = FontWeight.Bold,
                    color = OverlayTokens.panelPrimaryText,
                    maxLines = 1
                )
                TextLabel(
                    text = contact.wxid.orEmpty().ifBlank { contact.friendNo.orEmpty().ifBlank { "无 wxid" } },
                    size = 9.sp,
                    color = OverlayTokens.panelSecondaryText,
                    maxLines = 1
                )
            }
            ScrmPanelButton(label = "鍒犻櫎", enabled = enabled, danger = true, onClick = onDelete)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            PanelTextInput(
                value = sendText,
                onValueChange = onSendTextChange,
                placeholder = "给该联系人发送文本",
                modifier = Modifier.weight(1f)
            )
            ScrmPanelButton(label = "发送", enabled = enabled, accent = true, onClick = onSendText)
        }
    }
}

@Composable
private fun ScrmContactList(
    contacts: List<ScrmContact>,
    selectedContact: ScrmContact?,
    loading: Boolean,
    modifier: Modifier,
    onSelectContact: (ScrmContact) -> Unit
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        TextLabel(
            text = "联系人",
            size = 11.sp,
            weight = FontWeight.Bold,
            color = OverlayTokens.panelPrimaryText,
            maxLines = 1
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 128.dp, max = 210.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            if (contacts.isEmpty()) {
                item {
                    TextLabel(
                        text = if (loading) "正在加载..." else "暂无联系人",
                        size = 10.sp,
                        color = OverlayTokens.panelSecondaryText,
                        modifier = Modifier.padding(vertical = 10.dp),
                        maxLines = 1
                    )
                }
            }
            itemsIndexed(
                items = contacts,
                key = { _, contact -> contact.id }
            ) { _, contact ->
                val selected = selectedContact?.id == contact.id
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selected) OverlayTokens.inputFocus.copy(alpha = 0.22f) else Color.Transparent)
                        .border(
                            1.dp,
                            if (selected) OverlayTokens.accent else OverlayTokens.resourcePanelBorder,
                            RoundedCornerShape(8.dp)
                        )
                        .clickable { onSelectContact(contact) }
                        .padding(horizontal = 8.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(Color(0xFF5674A8)),
                        contentAlignment = Alignment.Center
                    ) {
                        TextLabel(
                            text = contact.displayName.take(2),
                            size = 9.sp,
                            weight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1
                        )
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        TextLabel(
                            text = contact.displayName,
                            size = 11.sp,
                            weight = FontWeight.SemiBold,
                            color = OverlayTokens.panelPrimaryText,
                            maxLines = 1
                        )
                        TextLabel(
                            text = contact.wxid.orEmpty().ifBlank { contact.friendNo.orEmpty() },
                            size = 8.sp,
                            color = OverlayTokens.panelSecondaryText,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun ScrmPanelButton(
    label: String,
    enabled: Boolean = true,
    accent: Boolean = false,
    danger: Boolean = false,
    onClick: () -> Unit
) {
    val container = when {
        danger -> Color(0xFFB65757)
        accent -> OverlayTokens.accent
        else -> OverlayTokens.resourcePanel
    }
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.height(34.dp),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = if (accent || danger) Color.White else OverlayTokens.panelPrimaryText,
            disabledContainerColor = OverlayTokens.resourcePanel.copy(alpha = 0.55f),
            disabledContentColor = OverlayTokens.panelSecondaryText
        ),
        contentPadding = PaddingValues(horizontal = 9.dp, vertical = 0.dp)
    ) {
        TextLabel(
            text = label,
            size = 10.sp,
            weight = FontWeight.SemiBold,
            color = if (enabled && (accent || danger)) Color.White else OverlayTokens.panelPrimaryText,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}

private data class ScrmContactsPanelState(
    val loading: Boolean = false,
    val contacts: List<ScrmContact> = emptyList(),
    val totalCount: Int = 0,
    val friendRequests: List<ScrmFriendRequest> = emptyList(),
    val selectedContact: ScrmContact? = null,
    val friendSearchProfile: ScrmFriendSearchProfile? = null,
    val status: String? = null,
    val error: String? = null
)

private data class ScrmContactsPanelLoadResult(
    val contacts: List<ScrmContact>,
    val totalCount: Int,
    val friendRequests: List<ScrmFriendRequest>
)

private fun ScrmFriendRequest.toFriendRequestSummary(): FriendRequestSummary {
    return FriendRequestSummary(
        id = id.toString(),
        displayName = displayName,
        message = requestMessage?.takeIf { it.isNotBlank() }
            ?: requestWxid?.takeIf { it.isNotBlank() },
        avatarUrl = avatar?.takeIf { it.isNotBlank() }
    )
}

private fun ScrmContact.toContactSummary(): ContactSummary {
    return ContactSummary(
        id = id.toString(),
        displayName = displayName,
        secondaryText = listOfNotNull(
            nickname,
            remarks,
            wxid,
            friendNo,
            source
        ).joinToString(separator = "\n").takeIf { it.isNotBlank() },
        avatarUrl = avatar?.takeIf { it.isNotBlank() },
        avatarColor = wechatContactAvatarColor(this).toArgb()
    )
}

internal fun scrmContactGroupSummaries(
    contacts: List<ScrmContact>,
    query: String
): List<ContactGroupSummary> {
    return groupedWechatContactSections(filterWechatContacts(contacts, query)).map { section ->
        ContactGroupSummary(
            title = section.title,
            contacts = section.contacts.map { contact -> contact.toContactSummary() }
        )
    }
}

internal fun scrmContactsBySummaryId(contacts: List<ScrmContact>): Map<String, ScrmContact> {
    return contacts.associateBy { contact -> contact.id.toString() }
}

private fun ContactsShortcut.placeholderLabel(): String {
    return when (this) {
        ContactsShortcut.NewFriends -> "新的朋友"
        ContactsShortcut.Groups -> "缇よ亰"
        ContactsShortcut.Tags -> "鏍囩"
        ContactsShortcut.OfficialAccounts -> "公众号"
        ContactsShortcut.WeComContacts -> "企业微信联系人"
        ContactsShortcut.Assistant -> "智能助手"
    }
}

internal data class ScrmFriendSearchProfile(
    val friendId: String,
    val displayName: String,
    val avatarUrl: String? = null,
    val wechatId: String? = null,
    val phone: String? = null,
    val region: String? = null,
    val signature: String? = null,
    val source: String? = null
)

private sealed interface ScrmFriendSearchResult {
    data class Found(val profile: ScrmFriendSearchProfile) : ScrmFriendSearchResult
    data class Pending(val message: String) : ScrmFriendSearchResult
}

internal fun contactAddFriendUsesSearchBeforeApply(): Boolean = false

internal fun contactAddFriendShowsIndependentProfileBeforeApply(): Boolean = false

internal fun wechatContactsStatusText(
    loading: Boolean,
    status: String?,
    error: String?
): String? {
    error?.takeIf { it.isNotBlank() }?.let { return it }
    val cleanStatus = status?.takeIf { it.isNotBlank() }
    return when {
        loading -> cleanStatus ?: "正在处理"
        else -> cleanStatus
    }
}

internal fun friendApplySubmittedStatus(message: String): String {
    val detail = message.trim().ifBlank { "等待对方确认" }
    return "好友申请已发送：$detail"
}

internal fun scrmDirectAddFriendRequest(
    deviceUuid: String,
    weChatId: String,
    friendAccount: String,
    message: String
): ScrmAddFriendRequest {
    return ScrmAddFriendRequest(
        deviceUuid = deviceUuid,
        weChatId = weChatId,
        friendWxid = friendAccount.trim(),
        message = message.trim().takeIf { it.isNotEmpty() }
    )
}

internal fun scrmAddFriendInputLooksLikePhone(input: String): Boolean {
    val normalized = input.trim().replace(" ", "").replace("-", "")
    return normalized.length >= 7 && normalized.all { char -> char.isDigit() || char == '+' }
}

internal fun scrmContactPrimaryConversationId(contact: ScrmContact): String? {
    return contact.wxid?.takeIf { it.isNotBlank() }
        ?: contact.friendNo?.takeIf { it.isNotBlank() }
}

internal fun scrmContactsPanelContactQuery(
    weChatId: String,
    pageNumber: Int,
    search: String?
): ScrmContactQuery {
    return ScrmContactQuery(
        weChatId = weChatId,
        page = pageNumber,
        pageSize = scrmConversationPageSize(),
        search = search?.trim()?.takeIf { it.isNotEmpty() },
        onlyFriends = true,
        includeProfile = false
    )
}

internal fun scrmPrivateChatThreadIdForContact(
    route: ScrmFloatingAccountRoute,
    contact: ScrmContact
): String? {
    val conversationId = scrmContactPrimaryConversationId(contact) ?: return null
    return scrmFloatingScopedThreadId(
        accountId = scrmFloatingAccountId(route.deviceUuid, route.weChatId),
        threadId = scrmFloatingContactId(conversationId)
    )
}

internal fun scrmFloatingContactForProfile(
    route: ScrmFloatingAccountRoute,
    contact: ScrmContact
): FloatingChatContact? {
    val conversationId = scrmContactPrimaryConversationId(contact) ?: return null
    val displayName = contact.displayName.trim().ifBlank { conversationId }
    return FloatingChatContact(
        id = scrmFloatingScopedThreadId(
            accountId = scrmFloatingAccountId(route.deviceUuid, route.weChatId),
            threadId = scrmFloatingContactId(conversationId)
        ),
        name = displayName,
        initials = displayName.take(2).ifBlank { "WX" },
        description = "WeChat friend / $conversationId",
        avatarColor = wechatContactAvatarColor(contact).toArgb().toLong(),
        avatarUrl = contact.avatar?.takeIf { it.isNotBlank() },
        online = contact.isBlocked == 0
    )
}

internal data class ScrmWechatContactSection(
    val title: String,
    val contacts: List<ScrmContact>
)

private enum class WechatContactsPanelScreen {
    Contacts,
    StartGroup,
    AddFriend,
    ContactIntro,
    FriendRequests
}

private enum class WechatContactsPlusAction(val label: String) {
    StartGroup("发起群聊"),
    AddFriend("添加朋友"),
    Scan("扫一扫"),
    Payment("收付款")
}

internal fun wechatContactsTitle(): String = "通讯录"

internal fun wechatContactsPlusMenuLabels(): List<String> {
    return WechatContactsPlusAction.values().map { action -> action.label }
}

internal fun wechatContactsPendingMenuLabels(): List<String> {
    return listOf(WechatContactsPlusAction.StartGroup.label, WechatContactsPlusAction.Payment.label)
}

internal fun wechatContactsStartGroupUsesContactPicker(): Boolean = true

internal fun wechatStartGroupTitle(): String = "发起群聊"

internal fun wechatStartGroupDoneLabel(selectedCount: Int): String {
    return if (selectedCount > 0) "完成($selectedCount)" else "完成"
}

internal fun wechatStartGroupOptionLabels(): List<String> {
    return listOf("选择一个群", "面对面建群", "企业微信联系人")
}

internal fun scrmCreateChatRoomMemberWxids(contacts: List<ScrmContact>): List<String> {
    return contacts.mapNotNull { contact ->
        scrmContactPrimaryConversationId(contact)?.trim()?.takeIf { it.isNotEmpty() }
    }.distinct()
}

internal fun wechatAddFriendPageEntryLabels(): List<String> {
    return listOf("扫一扫", "手机联系人", "雷达", "企业微信联系人", "面对面建群", "公众号", "服务号")
}

internal fun wechatContactIntroInfoRowLabels(): List<String> {
    return listOf("朋友资料", "朋友圈")
}

internal fun wechatContactIntroActionLabels(): List<String> {
    return listOf("发消息", "音视频通话")
}

internal fun filterWechatContacts(
    contacts: List<ScrmContact>,
    query: String
): List<ScrmContact> {
    val keyword = query.trim()
    if (keyword.isBlank()) return contacts
    return contacts.filter { contact ->
        listOfNotNull(
            contact.displayName,
            contact.nickname,
            contact.remarks,
            contact.wxid,
            contact.friendNo,
            contact.source
        ).any { value -> value.contains(keyword, ignoreCase = true) }
    }
}

internal fun groupedWechatContactSections(contacts: List<ScrmContact>): List<ScrmWechatContactSection> {
    return contacts
        .sortedWith(
            compareBy<ScrmContact>(
                { wechatContactSectionTitle(it) },
                { it.displayName.lowercase(Locale.ROOT) }
            )
        )
        .groupBy(::wechatContactSectionTitle)
        .toSortedMap(compareBy(::wechatContactSectionSortOrder))
        .map { (title, groupContacts) ->
            ScrmWechatContactSection(title = title, contacts = groupContacts)
        }
}

private fun wechatContactSectionTitle(contact: ScrmContact): String {
    return wechatContactInitial(contact.displayName)?.toString() ?: "#"
}

private fun wechatContactInitial(value: String): Char? {
    val first = value.trim().firstOrNull() ?: return null
    return when {
        first.isLetter() && first.code < 128 -> first.uppercaseChar()
        first.isDigit() -> "#".first()
        else -> chinesePinyinInitial(first)
    }?.takeIf { it in 'A'..'Z' }
}

private fun chinesePinyinInitial(char: Char): Char? {
    val bytes = runCatching { char.toString().toByteArray(Charset.forName("GBK")) }.getOrNull()
        ?: return null
    if (bytes.size < 2) return null
    val code = ((bytes[0].toInt() and 0xFF) * 256 + (bytes[1].toInt() and 0xFF)) - 65536
    val ranges = listOf(
        -20319 to 'A',
        -20283 to 'B',
        -19775 to 'C',
        -19218 to 'D',
        -18710 to 'E',
        -18526 to 'F',
        -18239 to 'G',
        -17922 to 'H',
        -17417 to 'J',
        -16474 to 'K',
        -16212 to 'L',
        -15640 to 'M',
        -15165 to 'N',
        -14922 to 'O',
        -14914 to 'P',
        -14630 to 'Q',
        -14149 to 'R',
        -14090 to 'S',
        -13318 to 'T',
        -12838 to 'W',
        -12556 to 'X',
        -11847 to 'Y',
        -11055 to 'Z'
    )
    return ranges
        .zipWithNext()
        .firstOrNull { (current, next) -> code in current.first until next.first }
        ?.first
        ?.second
        ?: ranges.lastOrNull { (start, _) -> code >= start }?.second
}

private fun wechatContactSectionSortOrder(title: String): Int {
    return when {
        title == "★" -> 0
        title.length == 1 && title.first() in 'A'..'Z' -> title.first() - 'A' + 1
        else -> 27
    }
}

private enum class ScrmFriendAddInputKind {
    Wxid,
    Phone,
    SearchContent
}

private fun scrmFriendAddInputKind(value: String): ScrmFriendAddInputKind {
    val input = value.trim()
    val phoneDigits = input.filter(Char::isDigit)
    val phoneLike = phoneDigits.length >= 7 &&
        input.all { char -> char.isDigit() || char in setOf('+', '-', ' ', '(', ')') }
    return when {
        input.startsWith("wxid_", ignoreCase = true) ||
            input.startsWith("gh_", ignoreCase = true) ||
            input.contains("@chatroom", ignoreCase = true) -> ScrmFriendAddInputKind.Wxid
        phoneLike -> ScrmFriendAddInputKind.Phone
        else -> ScrmFriendAddInputKind.SearchContent
    }
}

private fun scrmFriendIdFromFindContactData(data: JsonElement?): String? {
    return collectScrmFindContactObjects(data)
        .firstNotNullOfOrNull { item ->
            listOf(
                "friendId",
                "friendWxid",
                "wxid",
                "userName",
                "username",
                "encryptUsername",
                "v1"
            ).firstNotNullOfOrNull { key ->
                item[key]?.let { element ->
                    runCatching { element.jsonPrimitive.contentOrNull }.getOrNull()
                }?.takeIf { it.isNotBlank() }
            }
        }
}

internal fun scrmFriendSearchProfileFromFindContactData(data: JsonElement?): ScrmFriendSearchProfile? {
    return collectScrmFindContactObjects(data)
        .firstNotNullOfOrNull { item ->
            val friendId = item.stringValue(
                "friendId",
                "friendWxid",
                "wxid",
                "userName",
                "username",
                "encryptUsername",
                "v1"
            ) ?: return@firstNotNullOfOrNull null
            val wechatId = item.stringValue(
                "wxid",
                "friendWxid",
                "wechatId",
                "weChatId",
                "userName",
                "username"
            ) ?: friendId.takeIf { it.startsWith("wxid_", ignoreCase = true) }
            val displayName = item.stringValue(
                "remarks",
                "remark",
                "nickname",
                "nickName",
                "displayName",
                "alias",
                "friendNo",
                "name"
            ) ?: wechatId ?: friendId
            ScrmFriendSearchProfile(
                friendId = friendId,
                displayName = displayName,
                avatarUrl = item.stringValue(
                    "avatar",
                    "avatarUrl",
                    "headImg",
                    "headImgUrl",
                    "bigHeadImgUrl",
                    "smallHeadImgUrl"
                ),
                wechatId = wechatId,
                phone = item.stringValue("phone", "mobile", "phoneNumber"),
                region = item.stringValue("region", "area", "city", "province"),
                signature = item.stringValue("signature", "sign", "description", "desc"),
                source = item.stringValue("source", "from", "scene")
            )
        }
}

private fun JsonObject.stringValue(vararg keys: String): String? {
    return keys.firstNotNullOfOrNull { key ->
        this[key]?.let { element ->
            runCatching { element.jsonPrimitive.contentOrNull }.getOrNull()
        }?.takeIf { value -> value.isNotBlank() }
    }
}

private fun collectScrmFindContactObjects(data: JsonElement?): List<JsonObject> {
    return when (data) {
        is JsonArray -> data.flatMap(::collectScrmFindContactObjects)
        is JsonObject -> {
            val children = listOf(
                "data",
                "contact",
                "friend",
                "result",
                "item",
                "items",
                "list",
                "contacts"
            ).flatMap { key -> collectScrmFindContactObjects(data[key]) }
            listOf(data) + children
        }
        else -> emptyList()
    }
}

private val WechatContactsHeaderBackground = Color(0xFFF1F1F1)
private val WechatContactsPageBackground = Color(0xFFEDEDED)
private val WechatContactsRowBackground = Color(0xFFFCFCFC)
private val WechatContactsSearchBackground = Color(0xFFE9E9E9)
private val WechatContactsPrimaryText = Color(0xFF202020)
private val WechatContactsSecondaryText = Color(0xFF8B8B8B)
private val WechatContactsHintText = Color(0xFFAAAAAA)
private val WechatContactsChevronText = Color(0xFFC0C0C0)
