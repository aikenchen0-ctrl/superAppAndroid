package com.paifa.ubikitouch.accessibility.floatingchat.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.paifa.ubikitouch.accessibility.FloatingChatMediaPickerBridge
import com.paifa.ubikitouch.accessibility.floatingchat.components.FloatingWorkspaceTopAppBar
import com.paifa.ubikitouch.core.model.FloatingChatPrototype
import kotlinx.coroutines.launch

internal const val GalleryStatusBarHeightDp = 30

/** 测试流程：从右侧“图片”打开后确认页面自下向上进入。 */
internal fun galleryEnterOffsetDirection(): Int = 1

/** 测试流程：点击左上返回后确认全屏页面自上向下退出。 */
internal fun galleryExitOffsetDirection(): Int = -1

internal enum class GalleryFullScreenTab(val label: String) {
    Images("图片"),
    Recent("选择说明")
}

/**
 * 对应 iOS PHPicker 图片入口的 Android Material 3 全屏工作区。
 * 页面只负责呈现真实选择入口，选中结果仍由 FloatingChatMediaPickerBridge 交付聊天层，
 * 不生成缩略图、不伪造上传成功状态，也不创建 Dialog 或额外 Window。
 * 测试流程：点击“选择图片”调用系统选择器，完成选择后由桥接回传真实 URI；点击左上返回关闭页面。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GalleryFullScreen(
    onPickImage: () -> Unit = {
        FloatingChatMediaPickerBridge.requestPick(FloatingChatPrototype.PickedMediaKind.Image)
    },
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { GalleryFullScreenTab.entries.size })
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // 共享工具栏负责状态栏 inset 和返回入口，页面本身只承载图库内容。
        FloatingWorkspaceTopAppBar(title = "图片", onBack = onBack)
        PrimaryTabRow(selectedTabIndex = pagerState.currentPage) {
            GalleryFullScreenTab.entries.forEachIndexed { index, tab ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                    text = { Text(tab.label, fontWeight = FontWeight.Normal) }
                )
            }
        }
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f).fillMaxWidth()) { page ->
            when (GalleryFullScreenTab.entries[page]) {
                GalleryFullScreenTab.Images -> GallerySelectionPage(onPickImage)
                GalleryFullScreenTab.Recent -> GalleryInformationPage()
            }
        }
    }
}

@Composable
private fun GallerySelectionPage(onPickImage: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = "选择图片",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Normal,
                modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
            )
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                ListItem(
                    leadingContent = { Icon(Icons.Filled.Image, contentDescription = null) },
                    headlineContent = { Text("从设备选择图片", fontWeight = FontWeight.Normal) },
                    supportingContent = { Text("选择完成后会作为真实图片消息返回当前聊天", fontWeight = FontWeight.Normal) },
                    trailingContent = {
                        FilledTonalButton(onClick = onPickImage) {
                            Text("选择图片", fontWeight = FontWeight.Normal)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun GalleryInformationPage() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = "选择说明",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Normal,
                modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
            )
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                ListItem(
                    leadingContent = { Icon(Icons.Filled.Collections, contentDescription = null) },
                    headlineContent = { Text("系统图库", fontWeight = FontWeight.Normal) },
                    supportingContent = {
                        Text("Android 选择器负责权限和媒体读取，取消或读取失败会原样返回聊天层。", fontWeight = FontWeight.Normal)
                    }
                )
            }
        }
    }
}
