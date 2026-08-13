package com.paifa.ubikitouch.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paifa.ubikitouch.accessibility.FloatingChatContactRelation
import com.paifa.ubikitouch.accessibility.FloatingChatContactRelationsBridge
import com.paifa.ubikitouch.accessibility.FloatingChatContactRelationsSnapshot
import com.paifa.ubikitouch.accessibility.FloatingChatContactRelationSegment
import com.paifa.ubikitouch.accessibility.floatingchat.media.rememberAsyncImageThumbnailBitmap

class ContactRelationsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val snapshot by FloatingChatContactRelationsBridge.snapshot.collectAsState()
            ContactRelationsScreen(snapshot, ::finish)
        }
    }

    override fun onDestroy() {
        if (!isChangingConfigurations) FloatingChatContactRelationsBridge.notifyClosed()
        super.onDestroy()
    }
}

private val RelationsPage = Color(0xFFF2F2F7)
private val RelationsCard = Color.White
private val RelationsPrimary = Color(0xFF171719)
private val RelationsSecondary = Color(0xFF76767C)
private val RelationsBlue = Color(0xFF087CF0)

@Composable
private fun ContactRelationsScreen(snapshot: FloatingChatContactRelationsSnapshot, onBack: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var segment by rememberSaveable { mutableStateOf(0) }
    val filtered = remember(snapshot.contacts, query) {
        snapshot.contacts.filter { contact ->
            listOf(contact.name, contact.wxid, contact.organization.orEmpty(), contact.source.orEmpty())
                .joinToString(" ").contains(query.trim(), ignoreCase = true)
        }
    }
    Column(Modifier.fillMaxSize().background(RelationsPage)) {
        Spacer(Modifier.height(30.dp))
        RelationsToolbar(onBack, FloatingChatContactRelationsBridge::refresh)
        LazyColumn(Modifier.fillMaxSize()) {
            item { RelationsHeader(snapshot) }
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Search, null, tint = RelationsSecondary) },
                    placeholder = { Text("搜索联系人、组织或关系", fontSize = 14.sp) },
                    shape = RoundedCornerShape(10.dp)
                )
            }
            item {
                RelationSegments(segment) {
                    segment = it
                    FloatingChatContactRelationsBridge.loadSegment(it.toRelationSegment(), query)
                }
            }
            if (snapshot.loading) item { RelationsStateRow("正在加载通讯录关系...") }
            snapshot.error?.let { error -> item { RelationsStateRow(error, error = true) } }
            if (!snapshot.loading && snapshot.error == null && filtered.isEmpty()) item { RelationsStateRow("当前账号暂无联系人") }
            items(filtered, key = { it.id }) { contact -> RelationRow(contact, snapshot.segment) }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun RelationsToolbar(onBack: () -> Unit, onRefresh: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(56.dp).background(RelationsCard), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "返回", tint = RelationsPrimary) }
        Text("通讯录关系", modifier = Modifier.weight(1f), color = RelationsPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        IconButton(onClick = onRefresh) { Icon(Icons.Filled.Refresh, "刷新", tint = RelationsBlue) }
    }
}

@Composable
private fun RelationsHeader(snapshot: FloatingChatContactRelationsSnapshot) {
    val account = snapshot.accounts.firstOrNull { it.id == snapshot.selectedAccountId }
    Card(Modifier.fillMaxWidth().padding(14.dp), shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = RelationsCard), elevation = CardDefaults.cardElevation(0.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${account?.name ?: "当前账号"}的通讯录", color = RelationsPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("仅展示当前账号的好友与群聊关系", color = RelationsSecondary, fontSize = 13.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RelationStat("联系人", snapshot.totalCount, Modifier.weight(1f))
                RelationStat("群聊", snapshot.groups.size, Modifier.weight(1f))
                RelationStat(
                    "客户",
                    if (snapshot.segment == FloatingChatContactRelationSegment.Customers) snapshot.totalCount else 0,
                    Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun RelationStat(title: String, value: Int, modifier: Modifier) {
    Column(modifier.background(Color(0xFFF6F6F8), RoundedCornerShape(8.dp)).padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value.toString(), color = RelationsPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Text(title, color = RelationsSecondary, fontSize = 11.sp)
    }
}

@Composable
private fun RelationSegments(selected: Int, onSelected: (Int) -> Unit) {
    val labels = listOf("全部", "组织", "标签", "共同关系", "客户")
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        labels.forEachIndexed { index, label ->
            Text(label, Modifier.weight(1f).background(if (index == selected) RelationsBlue else Color(0xFFE5E5EA), RoundedCornerShape(7.dp)).clickable { onSelected(index) }.padding(vertical = 7.dp), color = if (index == selected) Color.White else RelationsPrimary, fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

@Composable
private fun RelationsStateRow(message: String, error: Boolean = false) {
    Text(message, Modifier.fillMaxWidth().background(RelationsCard).padding(22.dp), color = if (error) Color(0xFFB3261E) else RelationsSecondary, fontSize = 13.sp)
}

@Composable
private fun RelationRow(contact: FloatingChatContactRelation, segment: FloatingChatContactRelationSegment) {
    Row(Modifier.fillMaxWidth().background(RelationsCard).padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        RelationAvatar(contact)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(contact.name, color = RelationsPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(contactRelationSummary(contact, segment), color = RelationsSecondary, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                relationTags(contact, segment).forEach { tag -> RelationTag(tag) }

            }
            Text(contact.wxid.ifBlank { "微信号暂未返回" }, color = Color(0xFF9A9AA0), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
    Spacer(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFE5E5EA)))
}

@Composable
private fun RelationTag(text: String) {
    Text(text, Modifier.background(Color(0xFFF0F3F7), RoundedCornerShape(8.dp)).padding(horizontal = 7.dp, vertical = 3.dp), color = Color(0xFF496273), fontSize = 11.sp)
}

private fun Int.toRelationSegment(): FloatingChatContactRelationSegment = when (this) {
    1 -> FloatingChatContactRelationSegment.Organization
    2 -> FloatingChatContactRelationSegment.Tags
    3 -> FloatingChatContactRelationSegment.Common
    4 -> FloatingChatContactRelationSegment.Customers
    else -> FloatingChatContactRelationSegment.All
}

private fun contactRelationSummary(contact: FloatingChatContactRelation, segment: FloatingChatContactRelationSegment): String = when (segment) {
    FloatingChatContactRelationSegment.Organization -> contact.organization.orEmpty()
    FloatingChatContactRelationSegment.Tags -> "标签 ${contact.tags.size} 个"
    FloatingChatContactRelationSegment.Common -> "共同群聊 ${contact.commonGroups.size} 个"
    FloatingChatContactRelationSegment.Customers -> listOfNotNull(contact.customerLevel?.takeIf { it.isNotBlank() }, contact.source?.takeIf { it.isNotBlank() }).joinToString(" · ").ifBlank { "客户资料" }
    FloatingChatContactRelationSegment.All -> contact.source?.takeIf { it.isNotBlank() } ?: "好友关系"
}

private fun relationTags(contact: FloatingChatContactRelation, segment: FloatingChatContactRelationSegment): List<String> = when (segment) {
    FloatingChatContactRelationSegment.Organization -> listOfNotNull(contact.organization?.takeIf { it.isNotBlank() })
    FloatingChatContactRelationSegment.Tags -> contact.tags.take(3)
    FloatingChatContactRelationSegment.Common -> contact.commonGroups.take(3)
    FloatingChatContactRelationSegment.Customers -> listOfNotNull(contact.customerLevel?.takeIf { it.isNotBlank() }, "客户")
    FloatingChatContactRelationSegment.All -> listOf("好友")
}

@Composable
private fun RelationAvatar(contact: FloatingChatContactRelation) {
    val bitmap = rememberAsyncImageThumbnailBitmap(LocalContext.current, contact.avatarUrl?.takeIf { it.isNotBlank() })
    Box(Modifier.size(44.dp).background(Color(0xFF5C8DCE), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
        if (bitmap == null) Icon(if (contact.customerLevel != null) Icons.Filled.Business else Icons.Filled.Person, null, tint = Color.White, modifier = Modifier.size(24.dp))
        else Image(bitmap.asImageBitmap(), "${contact.name}头像", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}
