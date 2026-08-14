package com.paifa.ubikitouch.accessibility.floatingchat.scrm

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.paifa.ubikitouch.accessibility.scrm.ScrmContact
import com.paifa.ubikitouch.accessibility.scrm.ScrmContactManagementApi
import com.paifa.ubikitouch.accessibility.scrm.ScrmContactQuery
import com.paifa.ubikitouch.accessibility.scrm.ScrmCustomerProfile
import com.paifa.ubikitouch.accessibility.scrm.ScrmSaveCustomerProfileRequest
import com.paifa.ubikitouch.accessibility.scrm.ScrmSettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val CustomerProfileAnimationDurationMillis = 260

/** 客户档案的高性能分页，分别承载档案、标签记录和编辑页面。 */
internal enum class CustomerProfileFullScreenTab(val label: String) {
    Profile("档案"),
    LabelsAndHistory("标签与记录"),
    Editor("编辑")
}

private data class CustomerProfileFullScreenDraft(
    val customerLevel: String = "",
    val sourceChannel: String = "",
    val sourceDetail: String = "",
    val profileKey: String = "",
    val purchaseHistory: String = "",
    val socialAccounts: String = "",
    val notes: String = "",
    val mappedLabelNames: String = ""
)

/**
 * iOS OpenAPI“客户档案”对应的 Android Material 3 全屏悬浮页。
 *
 * UI 与接口：页面读取客户列表和 `getCustomerProfile`，保存时调用
 * `saveCustomerProfile` 后立即回读，展示服务端持久化后的档案。
 * 测试流程：从右侧“客户档案”进入，选择客户，依次检查三个分页；编辑任意字段保存后，
 * 刷新或返回档案页确认服务端回读数据；返回时确认页面向下退出且未创建额外窗口。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CustomerProfileFullScreen(
    manager: ScrmSettingsManager,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var contacts by remember { mutableStateOf<List<ScrmContact>>(emptyList()) }
    var selectedContact by remember { mutableStateOf<ScrmContact?>(null) }
    var profile by remember { mutableStateOf<ScrmCustomerProfile?>(null) }
    var draft by remember { mutableStateOf(CustomerProfileFullScreenDraft()) }
    var loading by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableStateOf(0) }
    val pagerState = rememberPagerState(pageCount = { CustomerProfileFullScreenTab.entries.size })
    var pageHeightPx by remember { mutableFloatStateOf(0f) }
    var entered by remember { mutableStateOf(false) }
    val translationY = remember { Animatable(0f) }

    /** 读取联系人及当前客户档案，保持与 iOS 的档案查看流程一致。 */
    fun loadData(preferredContactId: Int? = selectedContact?.id) {
        scope.launch {
            loading = true
            error = null
            runCatching {
                withContext(Dispatchers.IO) {
                    val session = manager.loadSelectedSessionOrBootstrap()
                    val loadedContacts = session.contactApi.getContacts(
                        ScrmContactQuery(
                            weChatId = session.weChatId,
                            page = 1,
                            pageSize = 50,
                            onlyFriends = true
                        )
                    ).items
                    val contact = loadedContacts.firstOrNull { it.id == preferredContactId }
                        ?: loadedContacts.firstOrNull()
                    val loadedProfile = contact?.let {
                        session.contactApi.getCustomerProfile(it.id, session.weChatId)
                    }
                    Triple(loadedContacts, contact, loadedProfile)
                }
            }.onSuccess { (loadedContacts, contact, loadedProfile) ->
                contacts = loadedContacts
                selectedContact = contact
                profile = loadedProfile
                draft = loadedProfile?.toCustomerProfileDraft() ?: CustomerProfileFullScreenDraft()
                status = if (contact == null) "暂无已同步联系人，请先同步好友资料" else "已读取 ${contact.displayName} 的客户档案"
            }.onFailure { throwable ->
                error = throwable.message ?: "客户档案读取失败"
            }
            loading = false
        }
    }

    /** 切换客户后通过既有 GET 客户档案接口获取真实数据。 */
    fun selectContact(contact: ScrmContact) {
        scope.launch {
            loading = true
            error = null
            runCatching {
                withContext(Dispatchers.IO) {
                    val session = manager.loadSelectedSessionOrBootstrap()
                    session.contactApi.getCustomerProfile(contact.id, session.weChatId)
                }
            }.onSuccess { loadedProfile ->
                selectedContact = contact
                profile = loadedProfile
                draft = loadedProfile.toCustomerProfileDraft()
                status = "已读取 ${contact.displayName} 的客户档案"
            }.onFailure { throwable ->
                error = throwable.message ?: "客户档案读取失败"
            }
            loading = false
        }
    }

    /** 保存档案后回读，避免 UI 假定服务端已成功持久化。 */
    fun saveProfile() {
        val contact = selectedContact ?: return
        scope.launch {
            loading = true
            error = null
            status = "正在保存客户档案"
            runCatching {
                withContext(Dispatchers.IO) {
                    val session = manager.loadSelectedSessionOrBootstrap()
                    val api = session.contactApi as? ScrmContactManagementApi
                        ?: error("当前 SCRM 客户端不支持客户档案保存")
                    api.saveCustomerProfile(contact.id, draft.toSaveRequest(session.weChatId))
                    session.contactApi.getCustomerProfile(contact.id, session.weChatId)
                }
            }.onSuccess { persisted ->
                profile = persisted
                draft = persisted.toCustomerProfileDraft()
                status = "客户档案已保存并完成回读"
                scope.launch { pagerState.animateScrollToPage(CustomerProfileFullScreenTab.Profile.ordinal) }
            }.onFailure { throwable ->
                error = throwable.message ?: "客户档案保存失败"
            }
            loading = false
        }
    }

    LaunchedEffect(reloadKey) { loadData() }
    LaunchedEffect(pageHeightPx) {
        if (pageHeightPx > 0f && !entered) {
            translationY.snapTo(pageHeightPx)
            translationY.animateTo(0f, tween(CustomerProfileAnimationDurationMillis))
            entered = true
        }
    }
    fun close() = scope.launch {
        translationY.animateTo(pageHeightPx, tween(CustomerProfileAnimationDurationMillis))
        onBack()
    }

    // 复用现有 accessibility overlay 根视图，不附加 Dialog 或新 Window，规避 BadTokenException。
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .onSizeChanged { pageHeightPx = it.height.toFloat() }
            .graphicsLayer { this.translationY = translationY.value }
    ) {
        Spacer(Modifier.height(30.dp))
        TopAppBar(
            title = { Text("客户档案", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Normal) },
            navigationIcon = {
                IconButton(onClick = ::close) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
            },
            actions = {
                IconButton(onClick = { reloadKey += 1 }, enabled = !loading) {
                    Icon(Icons.Filled.Refresh, contentDescription = "刷新客户档案")
                }
            }
        )
        TabRow(selectedTabIndex = pagerState.currentPage) {
            CustomerProfileFullScreenTab.entries.forEachIndexed { index, tab ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                    text = { Text(tab.label, fontWeight = FontWeight.Normal) }
                )
            }
        }
        CustomerProfileStatus(loading, error ?: status, error != null)
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            when (CustomerProfileFullScreenTab.entries[page]) {
                CustomerProfileFullScreenTab.Profile -> CustomerProfilePage(
                    contacts = contacts,
                    selectedContact = selectedContact,
                    profile = profile,
                    loading = loading,
                    onSelectContact = ::selectContact,
                    onEdit = { scope.launch { pagerState.animateScrollToPage(CustomerProfileFullScreenTab.Editor.ordinal) } }
                )
                CustomerProfileFullScreenTab.LabelsAndHistory -> CustomerProfileHistoryPage(profile)
                CustomerProfileFullScreenTab.Editor -> CustomerProfileEditorPage(
                    draft = draft,
                    enabled = selectedContact != null && !loading,
                    onDraftChange = { draft = it },
                    onSave = ::saveProfile
                )
            }
        }
    }
}

@Composable
private fun CustomerProfileStatus(loading: Boolean, message: String?, isError: Boolean) {
    if (message == null) return
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (loading) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
            }
            Text(message, color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CustomerProfilePage(
    contacts: List<ScrmContact>,
    selectedContact: ScrmContact?,
    profile: ScrmCustomerProfile?,
    loading: Boolean,
    onSelectContact: (ScrmContact) -> Unit,
    onEdit: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { CustomerProfileSectionTitle("客户 (${contacts.size})") }
        items(contacts, key = { it.id }) { contact ->
            ListItem(
                headlineContent = { Text(contact.displayName, fontWeight = FontWeight.Normal) },
                supportingContent = { Text(contact.wxid.orEmpty().ifBlank { "未提供微信号" }) },
                trailingContent = {
                    FilledTonalButton(
                        onClick = { onSelectContact(contact) },
                        enabled = !loading && contact.id != selectedContact?.id
                    ) {
                        Text(if (contact.id == selectedContact?.id) "当前" else "查看", fontWeight = FontWeight.Normal)
                    }
                }
            )
        }
        if (selectedContact != null) {
            item { CustomerProfileSectionTitle("${selectedContact.displayName} 的档案") }
            item {
                CustomerProfileOverview(profile)
            }
            item {
                FilledTonalButton(onClick = onEdit, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Edit, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("编辑客户档案", fontWeight = FontWeight.Normal)
                }
            }
        }
    }
}

@Composable
private fun CustomerProfileOverview(profile: ScrmCustomerProfile?) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                profile?.customerLevel?.takeIf { it.isNotBlank() }?.let { "$it 级客户" } ?: "未分级客户",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Normal
            )
            CustomerProfileValue("来源渠道", profile?.sourceChannel)
            CustomerProfileValue("画像标识", profile?.profileKey)
            CustomerProfileValue("联系电话", profile?.phone)
            CustomerProfileValue("来源详情", profile?.sourceDetail)
        }
    }
}

@Composable
private fun CustomerProfileHistoryPage(profile: ScrmCustomerProfile?) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { CustomerProfileSectionTitle("画像标签映射") }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("非微信联系人标签", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Normal)
                    Text(
                        profile?.mappedLabelNames?.takeIf { it.isNotEmpty() }?.joinToString(" · ") ?: "暂无标签",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Normal
                    )
                }
            }
        }
        item { CustomerProfileSectionTitle("最近动态") }
        item { CustomerProfileValueCard("购买记录", profile?.purchaseHistory) }
        item { CustomerProfileValueCard("社交帐号", profile?.socialAccounts) }
        item { CustomerProfileValueCard("档案备注", profile?.notes) }
    }
}

@Composable
private fun CustomerProfileEditorPage(
    draft: CustomerProfileFullScreenDraft,
    enabled: Boolean,
    onDraftChange: (CustomerProfileFullScreenDraft) -> Unit,
    onSave: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { CustomerProfileSectionTitle("编辑客户档案") }
        item { CustomerProfileInput("客户等级", draft.customerLevel) { onDraftChange(draft.copy(customerLevel = it)) } }
        item { CustomerProfileInput("来源渠道", draft.sourceChannel) { onDraftChange(draft.copy(sourceChannel = it)) } }
        item { CustomerProfileInput("来源详情", draft.sourceDetail) { onDraftChange(draft.copy(sourceDetail = it)) } }
        item { CustomerProfileInput("画像标识", draft.profileKey) { onDraftChange(draft.copy(profileKey = it)) } }
        item { CustomerProfileInput("购买记录", draft.purchaseHistory) { onDraftChange(draft.copy(purchaseHistory = it)) } }
        item { CustomerProfileInput("社交帐号", draft.socialAccounts) { onDraftChange(draft.copy(socialAccounts = it)) } }
        item { CustomerProfileInput("档案备注", draft.notes, singleLine = false) { onDraftChange(draft.copy(notes = it)) } }
        item { CustomerProfileInput("画像标签（逗号分隔）", draft.mappedLabelNames) { onDraftChange(draft.copy(mappedLabelNames = it)) } }
        item {
            Button(onClick = onSave, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                Text("保存并回读确认", fontWeight = FontWeight.Normal)
            }
        }
    }
}

@Composable
private fun CustomerProfileInput(
    label: String,
    value: String,
    singleLine: Boolean = true,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label, fontWeight = FontWeight.Normal) },
        singleLine = singleLine
    )
}

@Composable
private fun CustomerProfileValueCard(label: String, value: String?) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Normal)
            Text(value.orEmpty().ifBlank { "暂无$label" }, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CustomerProfileValue(label: String, value: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Normal)
        Text(value.orEmpty().ifBlank { "未设置" }, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CustomerProfileSectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Normal,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
    )
}

private fun ScrmCustomerProfile.toCustomerProfileDraft() = CustomerProfileFullScreenDraft(
    customerLevel = customerLevel.orEmpty(),
    sourceChannel = sourceChannel.orEmpty(),
    sourceDetail = sourceDetail.orEmpty(),
    profileKey = profileKey.orEmpty(),
    purchaseHistory = purchaseHistory.orEmpty(),
    socialAccounts = socialAccounts.orEmpty(),
    notes = notes.orEmpty(),
    mappedLabelNames = mappedLabelNames.joinToString(", ")
)

private fun CustomerProfileFullScreenDraft.toSaveRequest(weChatId: String) = ScrmSaveCustomerProfileRequest(
    weChatId = weChatId,
    customerLevel = customerLevel.blankToNull(),
    sourceChannel = sourceChannel.blankToNull(),
    sourceDetail = sourceDetail.blankToNull(),
    profileKey = profileKey.blankToNull(),
    purchaseHistory = purchaseHistory.blankToNull(),
    socialAccounts = socialAccounts.blankToNull(),
    notes = notes.blankToNull(),
    mappedLabelNames = normalizeCustomerProfileLabels(mappedLabelNames)
)

/** 将逗号分隔的标签转换为 API 要求的非空、去重列表。 */
internal fun normalizeCustomerProfileLabels(value: String): List<String> = value
    .split(',')
    .map(String::trim)
    .filter(String::isNotEmpty)
    .distinct()

private fun String.blankToNull(): String? = trim().takeIf(String::isNotEmpty)
