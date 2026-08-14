package com.paifa.ubikitouch.accessibility.floatingchat.tools

import android.Manifest
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.paifa.ubikitouch.accessibility.floatingchat.components.FloatingWorkspaceTopAppBar

internal const val VideoCallStatusBarHeightDp = 30
private const val VideoCallAnimationDurationMillis = 260

internal enum class VideoCallTab(val label: String) {
    Call("通话"),
    Participants("参与者")
}

/**
 * 对应 iOS VideoCallViewController 的 Android M3 全屏通话工作区，使用 CameraX 预览当前设备摄像头。
 * 测试流程：点击右侧“视频通话”，授权相机后确认预览、开关与前后切换；切换参与者 Tab；点击结束后检查当前会话的通话记录，
 * 最后确认页面使用 translationY 自下向上进入、自上向下退出，且未附着额外 Window。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun VideoCallFullScreen(
    targetName: String,
    participantNames: List<String>,
    isGroup: Boolean,
    onEndCall: (Int) -> Unit,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { VideoCallTab.entries.size })
    var pageHeightPx by remember { mutableFloatStateOf(0f) }
    var entered by remember { mutableStateOf(false) }
    var exiting by remember { mutableStateOf(false) }
    var elapsedSeconds by remember { mutableIntStateOf(0) }
    var muted by remember { mutableStateOf(false) }
    var speakerOn by remember { mutableStateOf(true) }
    var cameraOn by remember { mutableStateOf(true) }
    var usingFrontCamera by remember { mutableStateOf(true) }
    val pageTranslationY = remember { Animatable(0f) }
    val visibleParticipants = remember(participantNames, targetName) {
        participantNames.filter(String::isNotBlank).ifEmpty { listOf(targetName.ifBlank { "当前好友" }) }.take(9)
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            elapsedSeconds += 1
        }
    }
    LaunchedEffect(pageHeightPx) {
        if (pageHeightPx > 0f && !entered) {
            pageTranslationY.snapTo(pageHeightPx)
            pageTranslationY.animateTo(0f, tween(VideoCallAnimationDurationMillis))
            entered = true
        }
    }

    fun closeWithExitAnimation(recordCall: Boolean) {
        if (exiting) return
        exiting = true
        cameraOn = false
        scope.launch {
            pageTranslationY.animateTo(-pageHeightPx, tween(VideoCallAnimationDurationMillis))
            if (recordCall) onEndCall(elapsedSeconds)
            onBack()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .onSizeChanged { pageHeightPx = it.height.toFloat() }
            .graphicsLayer { translationY = pageTranslationY.value }
    ) {
        FloatingWorkspaceTopAppBar(
            title = if (isGroup) "群视频通话" else "视频通话",
            onBack = { closeWithExitAnimation(recordCall = false) }
        )
        PrimaryTabRow(selectedTabIndex = pagerState.currentPage) {
            VideoCallTab.entries.forEachIndexed { index, tab ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                    text = { Text(tab.label, fontWeight = FontWeight.Normal) }
                )
            }
        }
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
            when (VideoCallTab.entries[page]) {
                VideoCallTab.Call -> VideoCallPage(
                    targetName = targetName,
                    isGroup = isGroup,
                    elapsedSeconds = elapsedSeconds,
                    muted = muted,
                    speakerOn = speakerOn,
                    cameraOn = cameraOn,
                    usingFrontCamera = usingFrontCamera,
                    onMutedChanged = { muted = !muted },
                    onSpeakerChanged = { speakerOn = !speakerOn },
                    onCameraChanged = { cameraOn = !cameraOn },
                    onSwitchCamera = { usingFrontCamera = !usingFrontCamera },
                    onEndCall = { closeWithExitAnimation(recordCall = true) }
                )
                VideoCallTab.Participants -> VideoCallParticipantsPage(visibleParticipants, isGroup)
            }
        }
    }
}

@Composable
private fun VideoCallPage(
    targetName: String,
    isGroup: Boolean,
    elapsedSeconds: Int,
    muted: Boolean,
    speakerOn: Boolean,
    cameraOn: Boolean,
    usingFrontCamera: Boolean,
    onMutedChanged: () -> Unit,
    onSpeakerChanged: () -> Unit,
    onCameraChanged: () -> Unit,
    onSwitchCamera: () -> Unit,
    onEndCall: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Text(
                targetName.ifBlank { if (isGroup) "群聊成员" else "当前好友" },
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Normal
            )
        }
        item {
            Text(
                text = "视频通话已接通 ${formatVoiceCallDuration(elapsedSeconds)}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge
            )
        }
        item {
            VideoCallCameraPreview(
                cameraEnabled = cameraOn,
                useFrontCamera = usingFrontCamera,
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
            )
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Top
            ) {
                VideoCallControl(
                    icon = if (muted) Icons.Filled.MicOff else Icons.Filled.Mic,
                    label = if (muted) "取消静音" else "静音",
                    onClick = onMutedChanged
                )
                VideoCallControl(
                    icon = if (speakerOn) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff,
                    label = if (speakerOn) "免提" else "听筒",
                    onClick = onSpeakerChanged
                )
                VideoCallControl(
                    icon = if (cameraOn) Icons.Filled.Videocam else Icons.Filled.VideocamOff,
                    label = if (cameraOn) "关闭视频" else "打开视频",
                    onClick = onCameraChanged
                )
            }
        }
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.Top
            ) {
                VideoCallControl(
                    icon = Icons.Filled.Cameraswitch,
                    label = "切换摄像头",
                    enabled = cameraOn,
                    onClick = onSwitchCamera
                )
                VideoCallControl(
                    icon = Icons.Filled.CallEnd,
                    label = "结束通话",
                    destructive = true,
                    onClick = onEndCall
                )
            }
        }
    }
}

/** CameraX 仅在已获授权且视频打开时绑定；离开页面或关闭视频即解绑，避免持续占用摄像头。 */
@Composable
private fun VideoCallCameraPreview(
    cameraEnabled: Boolean,
    useFrontCamera: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    val providerFuture = remember { ProcessCameraProvider.getInstance(context) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    LaunchedEffect(cameraEnabled, useFrontCamera, cameraGranted, lifecycleOwner) {
        providerFuture.addListener(
            {
                runCatching {
                    val provider = providerFuture.get()
                    cameraProvider = provider
                    provider.unbindAll()
                    if (cameraEnabled && cameraGranted) {
                        val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                        val selector = if (useFrontCamera) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                        provider.bindToLifecycle(lifecycleOwner, selector, preview)
                    }
                }
            },
            ContextCompat.getMainExecutor(context)
        )
    }
    DisposableEffect(cameraProvider) {
        onDispose { cameraProvider?.unbindAll() }
    }

    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        if (cameraEnabled && cameraGranted) {
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = if (cameraEnabled) Icons.Filled.VideocamOff else Icons.Filled.Videocam,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = if (cameraEnabled) "未获得相机权限" else "视频已关闭",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Normal
                )
                Text(
                    text = if (cameraEnabled) "请在系统设置中授予相机权限后重新进入通话。" else "打开视频后将显示本机 CameraX 预览。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun VideoCallControl(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    destructive: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (destructive) {
            FilledIconButton(
                onClick = onClick,
                modifier = Modifier.size(60.dp),
                enabled = enabled,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) {
                Icon(icon, contentDescription = label)
            }
        } else {
            FilledTonalIconButton(onClick = onClick, modifier = Modifier.size(60.dp), enabled = enabled) {
                Icon(icon, contentDescription = label)
            }
        }
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun VideoCallParticipantsPage(names: List<String>, isGroup: Boolean) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = if (isGroup) "已接通 ${names.size} 人" else "通话对象",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Normal,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
        items(names, key = { it }) { name ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(44.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(name, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Normal)
                        Text("视频已接通", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
