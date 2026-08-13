package com.paifa.ubikitouch.app

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paifa.ubikitouch.accessibility.FloatingChatFriendManagementBridge
import com.paifa.ubikitouch.accessibility.FloatingChatFriendManagementSnapshot
import com.paifa.ubikitouch.core.model.FloatingChatContact
import com.paifa.ubikitouch.accessibility.floatingchat.media.rememberAsyncImageThumbnailBitmap

class FriendManagementActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { FriendManagementScreen(FloatingChatFriendManagementBridge.snapshot, ::finish) }
    }

    override fun onDestroy() {
        if (!isChangingConfigurations) FloatingChatFriendManagementBridge.notifyClosed()
        super.onDestroy()
    }
}

private val PageBackground = Color(0xFFF2F2F7)
private val CardBackground = Color(0xFFFFFFFF)
private val PrimaryText = Color(0xFF171719)
private val SecondaryText = Color(0xFF76767C)
private val Hairline = Color(0xFFE2E2E7)
private val SystemBlue = Color(0xFF087CF0)
private val SystemGreen = Color(0xFF28A745)
private val SystemOrange = Color(0xFFF29A22)

@Composable
private fun FriendManagementScreen(snapshot: FloatingChatFriendManagementSnapshot, onBack: () -> Unit) {
    val context = LocalContext.current
    var selectedAccountId by rememberSaveable { mutableStateOf(snapshot.selectedAccountId ?: snapshot.accounts.firstOrNull()?.id) }
    var formExpanded by rememberSaveable { mutableStateOf(false) }
    var target by rememberSaveable { mutableStateOf("") }
    val selectedAccount = snapshot.accounts.firstOrNull { it.id == selectedAccountId } ?: snapshot.accounts.firstOrNull()
    val contacts = remember(snapshot.contacts, selectedAccountId) { snapshot.contacts.forAccount(selectedAccountId) }
    val groups = remember(snapshot.groups, selectedAccountId) { snapshot.groups.forAccount(selectedAccountId) }
    val unavailable: (String) -> Unit = { action -> Toast.makeText(context, "${action}接口暂未接入此页面", Toast.LENGTH_SHORT).show() }

    Column(Modifier.fillMaxSize().background(PageBackground)) {
        Spacer(Modifier.height(30.dp))
        FriendManagementToolbar(
            onBack = onBack,
            onEnvironment = { unavailable("环境") },
            onPullRequests = FloatingChatFriendManagementBridge::pullFriendRequests
        )
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                ManagementHeader(
                    accounts = snapshot.accounts,
                    selectedAccount = selectedAccount,
                    contactCount = contacts.size,
                    groupCount = groups.size,
                    expanded = formExpanded,
                    target = target,
                    onAccountSelected = { selectedAccountId = it.id },
                    onToggle = { formExpanded = !formExpanded },
                    onTargetChanged = { target = it },
                    onAction = unavailable,
                    onRefresh = FloatingChatFriendManagementBridge::refresh
                )
            }
            item { SectionHeader("好友申请") }
            item { EmptySectionRow("暂无好友申请，点击右上角“拉取申请”从手机端同步。") }
            item { SectionHeader("当前账号群聊") }
            if (groups.isEmpty()) item { EmptySectionRow("当前账号暂未同步到群聊。") }
            items(groups, key = { it.id }) { ContactManagementRow(it, true, null) }
            item { SectionHeader("当前账号好友") }
            if (contacts.isEmpty()) item { EmptySectionRow("当前账号暂未同步到好友，点击刷新重新拉取。") }
            items(contacts, key = { it.id }) { ContactManagementRow(it, false) { unavailable("删除好友") } }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun FriendManagementToolbar(onBack: () -> Unit, onEnvironment: () -> Unit, onPullRequests: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(56.dp).background(CardBackground), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "返回", tint = PrimaryText) }
        Text("好友管理", modifier = Modifier.weight(1f), color = PrimaryText, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        TextButton(onClick = onEnvironment) { Text("环境", color = SystemBlue, fontSize = 14.sp) }
        TextButton(onClick = onPullRequests) { Text("拉取申请", color = SystemBlue, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
    }
}

@Composable
private fun ManagementHeader(
    accounts: List<FloatingChatContact>, selectedAccount: FloatingChatContact?, contactCount: Int, groupCount: Int,
    expanded: Boolean, target: String, onAccountSelected: (FloatingChatContact) -> Unit,
    onToggle: () -> Unit, onTargetChanged: (String) -> Unit, onAction: (String) -> Unit,
    onRefresh: () -> Unit
) {
    var accountMenu by remember { mutableStateOf(false) }
    var message by rememberSaveable { mutableStateOf("你好，我是安卓联调号。") }
    var remark by rememberSaveable { mutableStateOf("") }
    var label by rememberSaveable { mutableStateOf("") }
    var source by rememberSaveable { mutableStateOf("android-app") }
    var level by rememberSaveable { mutableStateOf("A") }
    var profileKey by rememberSaveable { mutableStateOf("android_openapi_demo") }
    var verifyImage by rememberSaveable { mutableStateOf("") }
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("真实好友操作", modifier = Modifier.weight(1f), color = PrimaryText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Box {
                    Row(
                        Modifier.background(Color(0x1A28A745), RoundedCornerShape(13.dp)).clickable { accountMenu = true }.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(if (selectedAccount?.online == true) Icons.Filled.CheckCircle else Icons.Filled.Person, null, tint = SystemGreen, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(selectedAccount?.name ?: "未同步账号", color = SystemGreen, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                    DropdownMenu(expanded = accountMenu, onDismissRequest = { accountMenu = false }) {
                        accounts.forEach { account ->
                            DropdownMenuItem(
                                text = { Text("${account.name} · ${if (account.online) "在线" else "离线"}") },
                                onClick = { onAccountSelected(account); accountMenu = false }
                            )
                        }
                    }
                }
            }
            Text("当前账号：${selectedAccount?.id ?: "未同步"}\n好友 $contactCount 位 · 群聊 $groupCount 个 · 待处理申请 0 条", color = SecondaryText, fontSize = 12.5.sp, lineHeight = 18.sp)
            Row(
                Modifier.fillMaxWidth().height(40.dp).background(Color(0xFFF0F0F2), RoundedCornerShape(8.dp)).clickable(onClick = onToggle).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(if (expanded) "收起添加好友" else "添加或查询好友", modifier = Modifier.weight(1f), color = PrimaryText, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, tint = SecondaryText)
            }
            if (expanded) {
                ManagementField(target, onTargetChanged, "微信号 / wxid / 手机号")
                ManagementField(message, { message = it }, "验证语，例如：你好，我是...")
                ManagementField(remark, { remark = it }, "备注，可为空")
                ManagementField(label, { label = it }, "标签，可为空")
                ManagementField(source, { source = it }, "来源，例如 android-app")
                ManagementField(level, { level = it }, "客户等级，例如 A / B / C")
                ManagementField(profileKey, { profileKey = it }, "画像 key，例如 android_openapi_demo")
                ManagementField(verifyImage, { verifyImage = it }, "验证图片 URL，可为空")
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    ManagementAction("查询联系人", Icons.Filled.Search, SystemBlue, Modifier.weight(1f)) { onAction("查询联系人") }
                    ManagementAction("添加好友", Icons.Filled.PersonAdd, SystemGreen, Modifier.weight(1f)) { onAction("添加好友") }
                    ManagementAction("刷新", Icons.Filled.Refresh, SystemOrange, Modifier.weight(1f), onRefresh)
                }
            }
        }
    }
}

@Composable
private fun ManagementField(value: String, onValueChange: (String) -> Unit, placeholder: String) {
    OutlinedTextField(value = value, onValueChange = onValueChange, modifier = Modifier.fillMaxWidth().height(52.dp), singleLine = true, placeholder = { Text(placeholder, fontSize = 13.sp) }, shape = RoundedCornerShape(12.dp))
}

@Composable
private fun ManagementAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = modifier.height(40.dp), shape = RoundedCornerShape(8.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 5.dp), colors = ButtonDefaults.buttonColors(containerColor = color)) {
        Icon(icon, null, Modifier.size(16.dp)); Spacer(Modifier.width(3.dp)); Text(label, fontSize = 11.sp, maxLines = 1)
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(title.uppercase(), modifier = Modifier.fillMaxWidth().padding(start = 30.dp, top = 14.dp, bottom = 6.dp), color = SecondaryText, fontSize = 13.sp)
}

@Composable
private fun EmptySectionRow(text: String) {
    Text(text, modifier = Modifier.fillMaxWidth().background(CardBackground).padding(horizontal = 22.dp, vertical = 18.dp), color = SecondaryText, fontSize = 13.sp, lineHeight = 18.sp)
}

@Composable
private fun ContactManagementRow(contact: FloatingChatContact, group: Boolean, onDelete: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().background(CardBackground).padding(start = 20.dp, end = 14.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        FriendContactAvatar(contact, group)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(contact.name, color = PrimaryText, fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (group) "${contact.groupMemberContacts.size} 人 · ${contact.id}" else listOf(contact.id, contact.description).filter { it.isNotBlank() }.joinToString(" · "), color = SecondaryText, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (onDelete != null) TextButton(onClick = onDelete, colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFD83A3A))) { Icon(Icons.Filled.Delete, null, Modifier.size(16.dp)); Spacer(Modifier.width(2.dp)); Text("删除", fontSize = 13.sp) }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).padding(start = 76.dp).background(Hairline))
}

@Composable
private fun FriendContactAvatar(contact: FloatingChatContact, group: Boolean) {
    val bitmap = rememberAsyncImageThumbnailBitmap(
        context = LocalContext.current,
        uriText = contact.avatarUrl?.takeIf { it.isNotBlank() }
    )
    Box(
        Modifier
            .size(44.dp)
            .background(Color(contact.avatarColor), RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "${contact.name}头像",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Icon(if (group) Icons.Filled.Group else Icons.Filled.Contacts, null, tint = Color.White, modifier = Modifier.size(25.dp))
        }
    }
}

private fun List<FloatingChatContact>.forAccount(accountId: String?): List<FloatingChatContact> {
    if (accountId.isNullOrBlank()) return this
    return filter { it.id.startsWith("${accountId}__") }
}
