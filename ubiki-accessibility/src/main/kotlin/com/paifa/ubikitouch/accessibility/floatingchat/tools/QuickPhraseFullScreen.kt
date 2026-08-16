package com.paifa.ubikitouch.accessibility.floatingchat.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.paifa.ubikitouch.accessibility.floatingchat.components.FloatingWorkspaceTopAppBar
import kotlinx.coroutines.launch

internal const val QuickPhraseStatusBarHeightDp = 30

/** 测试流程：从右侧快捷语打开，确认全屏实体从下方进入。 */
internal fun quickPhraseEnterOffsetDirection(): Int = 1

/** 测试流程：点击左上返回，确认全屏实体按要求向下方退出。 */
internal fun quickPhraseExitOffsetDirection(): Int = -1

internal enum class QuickPhraseFullScreenTab(val label: String) {
    Recent("常用"),
    Manage("管理")
}

/**
 * 对应 iOS 快捷语管理：本地持久化短语，可填入输入框、置顶、新增、编辑和删除。
 * 测试流程：从右侧打开，切换常用/管理，保存短语后点击常用项填入聊天输入框，再经左上返回关闭。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QuickPhraseFullScreen(
    phrases: List<String>,
    onSendPhrase: (String) -> Unit,
    onAddPhrase: (String) -> Unit,
    onUpdatePhrase: (Int, String) -> Unit,
    onDeletePhrase: (Int) -> Unit,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { QuickPhraseFullScreenTab.entries.size })
    var editorIndex by remember { mutableIntStateOf(-1) }
    var editorVisible by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }

    fun finishWorkspace(afterClose: () -> Unit = onBack) {
        afterClose()
    }

    fun beginCreate() {
        editorIndex = -1
        draft = ""
        editorVisible = true
    }

    fun beginEdit(index: Int, phrase: String) {
        editorIndex = index
        draft = phrase
        editorVisible = true
    }

    fun saveDraft() {
        val normalized = draft.trim()
        if (normalized.isEmpty()) return
        if (editorIndex >= 0) onUpdatePhrase(editorIndex, normalized) else onAddPhrase(normalized)
        editorIndex = -1
        draft = ""
        editorVisible = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // 共享工具栏负责安全区和返回，快捷语选择仍沿用既有发送回调。
        FloatingWorkspaceTopAppBar(title = "快捷语", onBack = onBack)
        PrimaryTabRow(selectedTabIndex = pagerState.currentPage) {
            QuickPhraseFullScreenTab.entries.forEachIndexed { index, tab ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                    text = { Text(tab.label, fontWeight = FontWeight.Normal) }
                )
            }
        }
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f).fillMaxWidth()) { page ->
            when (QuickPhraseFullScreenTab.entries[page]) {
                QuickPhraseFullScreenTab.Recent -> QuickPhraseList(
                    phrases = phrases,
                    emptyMessage = "暂无常用快捷语",
                    onPhraseClick = { phrase -> finishWorkspace { onSendPhrase(phrase) } }
                )
                QuickPhraseFullScreenTab.Manage -> Box(Modifier.fillMaxSize()) {
                    QuickPhraseManageList(
                        phrases = phrases,
                        editorIndex = editorIndex,
                        editorVisible = editorVisible,
                        draft = draft,
                        onDraftChanged = { draft = it.take(120) },
                        onSave = ::saveDraft,
                        onEdit = ::beginEdit,
                        onDelete = onDeletePhrase
                    )
                    FloatingActionButton(
                        onClick = ::beginCreate,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp)
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "新增快捷语")
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickPhraseList(
    phrases: List<String>,
    emptyMessage: String,
    onPhraseClick: (String) -> Unit
) {
    if (phrases.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(emptyMessage, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        itemsIndexed(phrases, key = { index, phrase -> "$index-$phrase" }) { _, phrase ->
            Card(onClick = { onPhraseClick(phrase) }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Text(phrase, modifier = Modifier.fillMaxWidth().padding(16.dp), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun QuickPhraseManageList(
    phrases: List<String>,
    editorIndex: Int,
    editorVisible: Boolean,
    draft: String,
    onDraftChanged: (String) -> Unit,
    onSave: () -> Unit,
    onEdit: (Int, String) -> Unit,
    onDelete: (Int) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (editorVisible) {
            item(key = "editor") {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(if (editorIndex >= 0) "编辑快捷语" else "新增快捷语", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Normal)
                        OutlinedTextField(value = draft, onValueChange = onDraftChanged, modifier = Modifier.fillMaxWidth(), minLines = 3, label = { Text("内容") })
                        Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) { Text("保存") }
                    }
                }
            }
        }
        itemsIndexed(phrases, key = { index, phrase -> "$index-$phrase" }) { index, phrase ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 10.dp, end = 8.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(phrase, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    IconButton(onClick = { onEdit(index, phrase) }) { Icon(Icons.Filled.Edit, contentDescription = "编辑") }
                    IconButton(onClick = { onDelete(index) }) { Icon(Icons.Filled.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }
}
