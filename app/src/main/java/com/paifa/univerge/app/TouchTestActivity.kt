package com.paifa.univerge.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.paifa.univerge.app.heavydrag.AispectHeavyTouchBridge
import com.paifa.univerge.heavydrag.android.HeavyDragRuntime
import com.paifa.univerge.heavydrag.compose.HeavyDragHost
import com.paifa.univerge.heavydrag.compose.LocalHeavyDragSession
import com.paifa.univerge.heavydrag.compose.heavyDraggable
import com.paifa.univerge.heavydrag.compose.heavyDropTarget
import com.paifa.univerge.heavydrag.compose.heavyOverlapTarget
import com.paifa.univerge.heavydrag.core.HeavyDragCoordinator
import com.paifa.univerge.heavydrag.core.HeavyDragPolicy
import com.paifa.univerge.heavydrag.core.HeavyDragState
import com.zhifaios.eyes.touch.AispectTouchError
import com.zhifaios.eyes.touch.AispectTouchModelInfo
import com.zhifaios.eyes.touch.AispectTouchResult

class TouchTestActivity : ComponentActivity() {
    private lateinit var bridge: AispectHeavyTouchBridge
    private lateinit var runtime: HeavyDragRuntime
    private var latestResult by mutableStateOf<AispectTouchResult?>(null)
    private var latestError by mutableStateOf<AispectTouchError?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bridge = AispectHeavyTouchBridge(applicationContext)
        bridge.setRawResultListener { result ->
            latestResult = result
            latestError = null
        }
        bridge.setRawErrorListener { error -> latestError = error }
        val coordinator = HeavyDragCoordinator(
            policy = HeavyDragPolicy(minHeavyConfidence = 0.55f)
        )
        runtime = HeavyDragRuntime(coordinator = coordinator, classifier = bridge)
        setContent {
            MaterialTheme {
                TouchTestScreen(
                    runtime = runtime,
                    coordinator = coordinator,
                    selectedModel = bridge.selectedModelInfo(),
                    latestResult = latestResult,
                    latestError = latestError,
                    onBack = ::finish
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (::runtime.isInitialized) runtime.start()
    }

    override fun onStop() {
        if (::runtime.isInitialized) runtime.stop()
        super.onStop()
    }

    override fun onDestroy() {
        if (::runtime.isInitialized) runtime.close()
        super.onDestroy()
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun TouchTestScreen(
    runtime: HeavyDragRuntime,
    coordinator: HeavyDragCoordinator,
    selectedModel: AispectTouchModelInfo?,
    latestResult: AispectTouchResult?,
    latestError: AispectTouchError?,
    onBack: () -> Unit
) {
    var clickCount by remember { mutableStateOf(0) }
    var dropCount by remember { mutableStateOf(0) }
    var overlapCount by remember { mutableStateOf(0) }
    var targetStatus by remember { mutableStateOf("拖动源未激活") }
    val session = LocalHeavyDragSession.current
    val isDragging = session?.state == HeavyDragState.DRAGGING

    HeavyDragHost(runtime = runtime, modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("触感拖拽测试") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                        }
                    }
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "轻触源组件只点击；重触确认后，源组件才跟随手指移动。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(132.dp)
                            .heavyDraggable(
                                coordinator = coordinator,
                                sourceId = "touch-test-source",
                                payload = "touch-test-payload",
                                onClick = { clickCount += 1 },
                                onDragStart = { targetStatus = "重触拖动中" },
                                onDrag = { targetStatus = "重触拖动中" },
                                onDragEnd = { targetStatus = "拖动结束" },
                                onDragCancel = { targetStatus = "拖动已取消" }
                            )
                            .background(MaterialTheme.colorScheme.secondary)
                    ) {
                        Text(
                            "重触拖动源",
                            modifier = Modifier.align(Alignment.Center),
                            color = Color.White
                        )
                    }
                    if (isDragging) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(16.dp)
                                .size(150.dp)
                                .heavyDropTarget(
                                    coordinator = coordinator,
                                    targetId = "touch-test-drop-zone",
                                    onEnter = { targetStatus = "已进入拖放区" },
                                    onOver = { targetStatus = "拖放区内" },
                                    onExit = { targetStatus = "已离开拖放区" },
                                    onDrop = {
                                        dropCount += 1
                                        targetStatus = "拖放成功"
                                    }
                                )
                                .background(MaterialTheme.colorScheme.tertiaryContainer)
                        ) {
                            Text("拖放区域", modifier = Modifier.align(Alignment.Center))
                        }
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(16.dp)
                                .size(150.dp)
                                .heavyOverlapTarget(
                                    coordinator = coordinator,
                                    targetId = "touch-test-overlap-target",
                                    onEnter = { targetStatus = "已重叠其他 UI" },
                                    onOver = { targetStatus = "重叠中" },
                                    onExit = { targetStatus = "已离开重叠 UI" },
                                    onCommit = {
                                        overlapCount += 1
                                        targetStatus = "重叠事件已提交"
                                    }
                                )
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Text("重叠目标", modifier = Modifier.align(Alignment.Center))
                        }
                    }
                }
                TouchStatusPanel(
                    selectedModel = selectedModel,
                    latestResult = latestResult,
                    latestError = latestError,
                    targetStatus = targetStatus,
                    clickCount = clickCount,
                    dropCount = dropCount,
                    overlapCount = overlapCount
                )
            }
        }
    }
}

@Composable
private fun TouchStatusPanel(
    selectedModel: AispectTouchModelInfo?,
    latestResult: AispectTouchResult?,
    latestError: AispectTouchError?,
    targetStatus: String,
    clickCount: Int,
    dropCount: Int,
    overlapCount: Int
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("交互状态：$targetStatus", color = MaterialTheme.colorScheme.primary)
        Text("轻触点击：$clickCount  拖放提交：$dropCount  重叠提交：$overlapCount")
        Text(
            when {
                latestError != null -> "错误：${latestError.code} ${latestError.cause?.message.orEmpty()}"
                latestResult == null -> "尚未收到触摸分类"
                else -> latestResult.toDisplayText()
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        Text("当前触摸模型", style = MaterialTheme.typography.titleMedium)
        Text(selectedModel?.toDisplayText() ?: "未加载触摸模型")
    }
}

private fun AispectTouchResult.toDisplayText(): String =
    "${eventType} / ${classLabel.ifBlank { "UNKNOWN" }}  confidence=${"%.2f".format(confidence)}  gestureId=$gestureId"

private fun AispectTouchModelInfo.toDisplayText(): String =
    "${displayName.ifBlank { id }}  $id v${version.ifBlank { "-" }}\n${inputChannels}通道 / ${frameCount}帧 / $windowMode"
