package com.paifa.ubikitouch.accessibility.floatingchat.tools

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.paifa.ubikitouch.accessibility.FloatingChatMediaPickerBridge
import com.paifa.ubikitouch.core.model.FloatingChatPrototype
import kotlinx.coroutines.launch

internal const val GalleryStatusBarHeightDp = 30
private const val GalleryAnimationDurationMillis = 240

/** 测试流程：从右侧“图片”打开后确认页面自下向上进入。 */
internal fun galleryEnterOffsetDirection(): Int = 1

/** 测试流程：点击左上返回后确认全屏页面自上向下退出。 */
internal fun galleryExitOffsetDirection(): Int = 1

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
    var pageHeightPx by remember { mutableFloatStateOf(0f) }
    var entered by remember { mutableStateOf(false) }
    val pageTranslationY = remember { Animatable(0f) }

    LaunchedEffect(pageHeightPx) {
        if (pageHeightPx > 0f && !entered) {
            pageTranslationY.snapTo(pageHeightPx)
            pageTranslationY.animateTo(0f, tween(GalleryAnimationDurationMillis))
            entered = true
        }
    }

    fun closeWithExitAnimation() {
        scope.launch {
            pageTranslationY.animateTo(pageHeightPx, tween(GalleryAnimationDurationMillis))
            onBack()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .onSizeChanged { pageHeightPx = it.height.toFloat() }
            .graphicsLayer { translationY = pageTranslationY.value }
    ) {
        // 根悬浮层已经处理系统窗口 Insets，这里保留产品要求的 30dp 状态区。
        androidx.compose.foundation.layout.Spacer(Modifier.height(GalleryStatusBarHeightDp.dp))
        androidx.compose.foundation.layout.Spacer(Modifier.height(GalleryStatusBarHeightDp.dp))
        TopAppBar(
            title = {
                Text(
                    text = "图片",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Normal
                )
            },
            navigationIcon = {
                IconButton(onClick = ::closeWithExitAnimation) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
            }
        )
        PrimaryTabRow(selectedTabIndex = pagerState.currentPage) {
            GalleryFullScreenTab.entries.forEachIndexed { index, tab ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                    text = { Text(tab.label, fontWeight = FontWeight.Normal) }
                )
            }
        }
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
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
