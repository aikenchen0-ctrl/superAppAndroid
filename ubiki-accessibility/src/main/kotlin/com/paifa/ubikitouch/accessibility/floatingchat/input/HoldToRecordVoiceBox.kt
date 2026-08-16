package com.paifa.ubikitouch.accessibility.floatingchat.input

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaRecorder
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.paifa.ubikitouch.accessibility.FloatingChatVoicePermissionBridge
import java.io.File

internal data class PendingVoiceRecording(val file: File, val durationMs: Int)

/** 按住录音；松手后的确认层由悬浮聊天根节点负责绘制，避免被输入框布局裁剪。 */
@Composable
internal fun HoldToRecordVoiceBox(
    permissionRequestToken: Int,
    onRecordingReady: (PendingVoiceRecording) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var session by remember { mutableStateOf<VoiceRecordSession?>(null) }
    var recording by remember { mutableStateOf(false) }
    var cancelTarget by remember { mutableStateOf(false) }
    var elapsedMs by remember { mutableStateOf(0) }
    var status by remember { mutableStateOf("按住说话") }

    fun startRecording() {
        if (session != null) return
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            status = "需要麦克风权限，请允许后重试"
            FloatingChatVoicePermissionBridge.requestRecordAudioPermission()
            return
        }
        runCatching {
            val file = File(context.cacheDir, "floating-chat-voice/voice-${System.currentTimeMillis()}.m4a")
                .also { it.parentFile?.mkdirs() }
            @Suppress("DEPRECATION")
            val recorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(96_000)
                setAudioSamplingRate(44_100)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            VoiceRecordSession(recorder, file, System.currentTimeMillis())
        }.onSuccess {
            session = it
            recording = true
            cancelTarget = false
            status = "正在录音，松手确认"
        }.onFailure { status = "录音启动失败：${it.message ?: "未知错误"}" }
    }

    fun finishRecording() {
        val active = session ?: return
        session = null
        recording = false
        elapsedMs = (System.currentTimeMillis() - active.startedAtMs).toInt().coerceAtLeast(0)
        val stopped = runCatching { active.recorder.stop() }.isSuccess
        active.recorder.release()
        if (!stopped || active.file.length() <= 0L || cancelTarget) {
            active.file.delete()
            status = if (cancelTarget) "已取消发送" else "没有录到声音，请重试"
            cancelTarget = false
        } else {
            onRecordingReady(PendingVoiceRecording(active.file, elapsedMs))
            status = "录音已准备好"
        }
    }

    LaunchedEffect(recording, session) {
        while (recording && session != null) {
            elapsedMs = (System.currentTimeMillis() - (session?.startedAtMs ?: 0L)).toInt().coerceAtLeast(0)
            kotlinx.coroutines.delay(200)
        }
    }
    LaunchedEffect(permissionRequestToken) {
        if (permissionRequestToken > 0 && context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            status = "权限已允许，请按住说话"
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            session?.let { active ->
                runCatching { active.recorder.stop() }
                active.recorder.release()
                active.file.delete()
            }
        }
    }

    Box(modifier.heightIn(min = 46.dp, max = BottomInputBarMaxHeightDp.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (recording && cancelTarget) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        startRecording()
                        var up = false
                        while (!up) {
                            val change = awaitPointerEvent().changes.firstOrNull() ?: break
                            cancelTarget = recording && change.position.x < size.width * 0.32f
                            if (!change.pressed) up = true
                        }
                        finishRecording()
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
            ) {
                Icon(Icons.Filled.Mic, contentDescription = "按住说话", modifier = Modifier.size(20.dp))
                Text(
                    text = when {
                        recording && cancelTarget -> "松手取消"
                        recording -> "录音 ${elapsedMs / 1000}s"
                        else -> status
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private data class VoiceRecordSession(val recorder: MediaRecorder, val file: File, val startedAtMs: Long)

/** 根节点确认层：全屏遮罩承载确认卡片，避免确认 UI 被 BottomBar 的高度约束限制。 */
@Composable
internal fun VoiceSendConfirmationOverlay(
    recording: PendingVoiceRecording,
    onCancel: () -> Unit,
    onConfirm: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("发送语音？", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleLarge)
                Text("录音时长 ${recording.durationMs / 1000}s，是否发送到当前会话？", style = MaterialTheme.typography.bodyMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onCancel) { Text("取消") }
                    Button(onClick = onConfirm) { Text("发送") }
                }
            }
        }
    }
}
