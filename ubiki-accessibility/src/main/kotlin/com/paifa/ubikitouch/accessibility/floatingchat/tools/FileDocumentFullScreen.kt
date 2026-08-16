package com.paifa.ubikitouch.accessibility.floatingchat.tools

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.paifa.ubikitouch.accessibility.floatingchat.components.FloatingWorkspaceTopAppBar
import com.paifa.ubikitouch.core.model.FloatingChatFileFormat
import com.paifa.ubikitouch.core.model.FloatingChatMessage
import com.paifa.ubikitouch.core.model.FloatingChatMessageType
import kotlinx.coroutines.launch

internal const val FileDocumentStatusBarHeightDp = 30

internal enum class FileDocumentTab(val label: String) {
    Recent("最近文件"),
    Documents("文档")
}

/**
 * 文件/文档悬浮全屏工作区：对应 iOS 文件选择与文档消息流程，使用 Android M3 组件。
 * 测试流程：点击右侧“文件/文档”进入，切换 Tab 或搜索文件，点击“选择文件”调用系统 picker，
 * 点击历史文件打开既有全屏预览，最后点击左上角返回确认自上向下退出。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun FileDocumentFullScreen(
    messages: List<FloatingChatMessage>,
    onPickDocument: () -> Unit,
    onPreviewDocument: (FloatingChatMessage) -> Unit,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { FileDocumentTab.entries.size })
    var query by remember { mutableStateOf("") }
    val fileMessages = remember(messages) {
        messages.filter { it.type == FloatingChatMessageType.FilePreview }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // 顶部安全区由共享 AppBar 内部处理，避免页面再创建状态栏空白。
        FloatingWorkspaceTopAppBar(title = "文件/文档", onBack = onBack)
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            singleLine = true,
            leadingIcon = { Icon(Icons.Filled.FolderOpen, contentDescription = null) },
            label = { Text("搜索文件") }
        )
        PrimaryTabRow(selectedTabIndex = pagerState.currentPage) {
            FileDocumentTab.entries.forEachIndexed { index, tab ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                    text = { Text(tab.label, fontWeight = FontWeight.Normal) }
                )
            }
        }
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f).fillMaxWidth()) { page ->
            val tab = FileDocumentTab.entries[page]
            val visibleMessages = remember(fileMessages, query, tab) {
                fileMessages
                    .asReversed()
                    .filter { tab == FileDocumentTab.Recent || it.fileFormat != null }
                    .filter { it.matchesDocumentQuery(query) }
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = if (tab == FileDocumentTab.Recent) "最近使用的文件" else "选择要发送的文档",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Normal
                            )
                            Text(
                                text = "文件会沿用现有系统选择器、真实附件地址和文件预览接口。",
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Button(onClick = onPickDocument, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Filled.UploadFile, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("选择文件")
                            }
                        }
                    }
                }
                if (visibleMessages.isEmpty()) {
                    item {
                        Text(
                            text = "暂无文件记录",
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    items(visibleMessages, key = { it.id }) { message ->
                        FileDocumentRow(message = message, onClick = { onPreviewDocument(message) })
                    }
                }
                item { Spacer(Modifier.height(12.dp)) }
            }
        }
    }
}

private fun FloatingChatMessage.matchesDocumentQuery(query: String): Boolean {
    val value = query.trim()
    if (value.isEmpty()) return true
    return listOfNotNull(fileName, text, detail, appName, fileFormat?.label)
        .any { it.contains(value, ignoreCase = true) }
}

@Composable
private fun FileDocumentRow(message: FloatingChatMessage, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = documentIcon(message.fileFormat),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = message.fileName ?: message.text.ifBlank { "未命名文件" },
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Normal
                )
                Text(
                    text = listOfNotNull(message.fileFormat?.label, message.fileSizeLabel, message.time)
                        .joinToString(" · ")
                        .ifBlank { "文件消息" },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
                message.senderName.takeIf(String::isNotBlank)?.let { sender ->
                    Text(sender, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                }
            }
            Icon(Icons.Filled.Description, contentDescription = "预览", tint = MaterialTheme.colorScheme.primary)
        }
    }
}

private fun documentIcon(format: FloatingChatFileFormat?): ImageVector = when (format) {
    FloatingChatFileFormat.Pdf -> Icons.Filled.PictureAsPdf
    FloatingChatFileFormat.Word -> Icons.Filled.Description
    FloatingChatFileFormat.Markdown,
    FloatingChatFileFormat.Txt,
    null -> Icons.Filled.Article
}
