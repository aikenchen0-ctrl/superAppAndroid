package com.paifa.univerge.accessibility.floatingchat.tools

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
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.paifa.univerge.accessibility.floatingchat.components.FloatingWorkspaceTopAppBar

internal const val VoiceCallStatusBarHeightDp = 30
internal enum class VoiceCallTab(val label: String) {
    Call("通话"),
    Participants("参与者")
}

/**
 * 对应 iOS VoiceCallViewController 的 Android M3 全屏通话工作区。
 * 测试流程：从右侧“语音通话”进入，确认计时与静音/免提状态可切换；点击结束后检查当前会话新增语音通话记录，
 * 再确认返回或结束均由实体自上向下退出，且未创建额外 Window，避免 BadTokenException。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun VoiceCallFullScreen(
    targetName: String,
    participantNames: List<String>,
    isGroup: Boolean,
    onEndCall: (Int) -> Unit,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { VoiceCallTab.entries.size })
    var elapsedSeconds by remember { mutableIntStateOf(0) }
    var muted by remember { mutableStateOf(false) }
    var speakerOn by remember { mutableStateOf(true) }
    var isClosing by remember { mutableStateOf(false) }
    val visibleParticipants = remember(participantNames, targetName) {
        participantNames.filter(String::isNotBlank).ifEmpty { listOf(targetName.ifBlank { "当前好友" }) }.take(9)
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            elapsedSeconds += 1
        }
    }
    fun close(recordCall: Boolean) {
        if (isClosing) return
        isClosing = true
        if (recordCall) onEndCall(elapsedSeconds)
        onBack()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        FloatingWorkspaceTopAppBar(
            title = if (isGroup) "群语音通话" else "语音通话",
            onBack = { close(recordCall = false) }
        )
        PrimaryTabRow(selectedTabIndex = pagerState.currentPage) {
            VoiceCallTab.entries.forEachIndexed { index, tab ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                    text = { Text(tab.label, fontWeight = FontWeight.Normal) }
                )
            }
        }
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f).fillMaxWidth()) { page ->
            when (VoiceCallTab.entries[page]) {
                VoiceCallTab.Call -> VoiceCallPage(
                    targetName = targetName,
                    isGroup = isGroup,
                    elapsedSeconds = elapsedSeconds,
                    muted = muted,
                    speakerOn = speakerOn,
                    onMutedChanged = { muted = !muted },
                    onSpeakerChanged = { speakerOn = !speakerOn },
                    onEndCall = { close(recordCall = true) }
                )
                VoiceCallTab.Participants -> VoiceCallParticipantsPage(
                    names = visibleParticipants,
                    isGroup = isGroup
                )
            }
        }
    }
}

@Composable
private fun VoiceCallPage(
    targetName: String,
    isGroup: Boolean,
    elapsedSeconds: Int,
    muted: Boolean,
    speakerOn: Boolean,
    onMutedChanged: () -> Unit,
    onSpeakerChanged: () -> Unit,
    onEndCall: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Text(
                text = targetName.ifBlank { if (isGroup) "群聊成员" else "当前好友" },
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Normal
            )
        }
        item {
            Text(
                text = if (isGroup) "已接通群语音通话" else "语音通话已接通",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge
            )
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(
                    modifier = Modifier.padding(horizontal = 44.dp, vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Filled.PhoneInTalk,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(64.dp)
                    )
                    Text(
                        text = formatVoiceCallDuration(elapsedSeconds),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Normal
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.GraphicEq,
                            contentDescription = null,
                            tint = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (muted) "已静音" else "正在通话",
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Top
            ) {
                VoiceCallControl(
                    icon = if (muted) Icons.Filled.MicOff else Icons.Filled.Mic,
                    label = if (muted) "取消静音" else "静音",
                    onClick = onMutedChanged
                )
                VoiceCallControl(
                    icon = if (speakerOn) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff,
                    label = if (speakerOn) "免提" else "听筒",
                    onClick = onSpeakerChanged
                )
                VoiceCallControl(
                    icon = Icons.Filled.CallEnd,
                    label = "结束通话",
                    destructive = true,
                    onClick = onEndCall
                )
            }
        }
    }
}

@Composable
private fun VoiceCallControl(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    destructive: Boolean = false,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (destructive) {
            FilledIconButton(
                onClick = onClick,
                modifier = Modifier.size(64.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) {
                Icon(icon, contentDescription = label)
            }
        } else {
            FilledTonalIconButton(onClick = onClick, modifier = Modifier.size(64.dp)) {
                Icon(icon, contentDescription = label)
            }
        }
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun VoiceCallParticipantsPage(names: List<String>, isGroup: Boolean) {
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
                    Icon(
                        Icons.Filled.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(name, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Normal)
                        Text("语音已接通", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

internal fun formatVoiceCallDuration(seconds: Int): String {
    val safeSeconds = seconds.coerceAtLeast(0)
    return "%02d:%02d".format(safeSeconds / 60, safeSeconds % 60)
}
