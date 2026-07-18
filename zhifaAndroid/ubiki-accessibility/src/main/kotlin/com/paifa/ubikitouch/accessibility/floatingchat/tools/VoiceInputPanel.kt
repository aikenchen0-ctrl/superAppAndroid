package com.paifa.ubikitouch.accessibility.floatingchat.tools

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paifa.ubikitouch.accessibility.FloatingChatVoicePermissionBridge
import com.paifa.ubikitouch.accessibility.floatingchat.components.TextLabel
import com.paifa.ubikitouch.accessibility.floatingchat.media.formatVoiceTimecode
import com.paifa.ubikitouch.accessibility.floatingchat.theme.OverlayTokens
import java.io.File
internal fun voiceInputRecordsAudioMessage(): Boolean = true

internal fun playGeneratedAiVoice(context: Context, audioUrl: String) {
    val player = MediaPlayer()
    player.setDataSource(context, Uri.parse(audioUrl))
    player.setOnPreparedListener(MediaPlayer::start)
    player.setOnCompletionListener(MediaPlayer::release)
    player.setOnErrorListener { failedPlayer, _, _ ->
        failedPlayer.release()
        true
    }
    player.prepareAsync()
}

internal fun generatedAiVoiceDurationMs(context: Context, audioUrl: String): Int {
    val player = MediaPlayer.create(context, Uri.parse(audioUrl)) ?: return 0
    return try {
        player.duration.coerceAtLeast(0)
    } finally {
        player.release()
    }
}

internal fun voiceInputSendsRecordedAudio(): Boolean = true

internal fun voiceMessageSupportsPlayback(): Boolean = true

internal fun voiceRecorderMimeType(): String = VoiceRecorderMimeType

internal fun voiceRecorderFileExtension(): String = VoiceRecorderFileExtension

internal fun voiceInputRequiresRecordAudioPermission(): Boolean = true

internal fun floatingChatRuntimePermissions(): List<String> {
    return listOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )
}

private fun hasRecordAudioPermission(context: Context): Boolean {
    return context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
}

private fun voiceInputIdleLabel(): String = "点击开始录音，停止后会发送语音消息"

private fun createVoiceRecorderSession(context: Context): VoiceRecorderSession {
    val directory = File(context.cacheDir, "floating-chat-voice").apply {
        mkdirs()
    }
    val file = File(directory, "voice-${System.currentTimeMillis()}.$VoiceRecorderFileExtension")
    @Suppress("DEPRECATION")
    val recorder = MediaRecorder().apply {
        setAudioSource(MediaRecorder.AudioSource.MIC)
        setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        setAudioEncodingBitRate(96_000)
        setAudioSamplingRate(44_100)
        setOutputFile(file.absolutePath)
    }
    return VoiceRecorderSession(
        recorder = recorder,
        file = file,
        startedAtMs = System.currentTimeMillis()
    )
}


@Composable
internal fun RealVoiceInputPanel(
    permissionRequestToken: Int,
    onSendVoice: (String, Int) -> Unit
) {
    val context = LocalContext.current
    var state by remember { mutableStateOf(VoiceInputState.Idle) }
    var statusText by remember { mutableStateOf(voiceInputIdleLabel()) }
    var recorder by remember { mutableStateOf<VoiceRecorderSession?>(null) }
    var recordedMs by remember { mutableStateOf(0) }
    var lastFile by remember { mutableStateOf<File?>(null) }

    val stopRecording: (Boolean) -> Unit = { send ->
        val session = recorder
        if (session == null) {
            statusText = voiceInputIdleLabel()
        } else {
            recorder = null
            val elapsedMs = session.elapsedMs()
            val stopped = runCatching {
                session.recorder.stop()
            }.isSuccess
            session.recorder.release()
            state = VoiceInputState.Idle
            recordedMs = elapsedMs
            lastFile = session.file
            if (send && stopped && session.file.length() > 0L) {
                onSendVoice(Uri.fromFile(session.file).toString(), elapsedMs)
            } else if (send) {
                statusText = "没有录到声音，请再试一次"
                session.file.delete()
            } else {
                statusText = voiceInputIdleLabel()
                session.file.delete()
            }
        }
    }

    val startRecording = {
        if (!hasRecordAudioPermission(context)) {
            state = VoiceInputState.PermissionRequired
            statusText = "需要麦克风权限，正在请求授权"
            FloatingChatVoicePermissionBridge.requestRecordAudioPermission()
        } else {
            runCatching {
                createVoiceRecorderSession(context).also { session ->
                    session.recorder.prepare()
                    session.recorder.start()
                }
            }.onSuccess { session ->
                recorder = session
                lastFile = null
                recordedMs = 0
                state = VoiceInputState.Recording
                statusText = "正在录音，点击停止发送"
            }.onFailure { error ->
                state = VoiceInputState.Idle
                statusText = error.message ?: "录音启动失败"
            }
        }
    }

    LaunchedEffect(state, recorder) {
        while (state == VoiceInputState.Recording && recorder != null) {
            recordedMs = recorder?.elapsedMs() ?: recordedMs
            kotlinx.coroutines.delay(200)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            val active = recorder
            recorder = null
            if (active != null) {
                runCatching { active.recorder.stop() }
                active.recorder.release()
                active.file.delete()
            }
        }
    }

    LaunchedEffect(permissionRequestToken) {
        if (permissionRequestToken > 0 && hasRecordAudioPermission(context)) {
            startRecording()
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        TextLabel(
            text = "语音输入",
            size = 11.sp,
            weight = FontWeight.SemiBold,
            color = OverlayTokens.panelPrimaryText,
            maxLines = 1
        )
        TextLabel(
            text = statusText,
            size = 10.sp,
            color = OverlayTokens.panelSecondaryText,
            maxLines = 2
        )
        TextLabel(
            text = if (state == VoiceInputState.Recording) {
                formatVoiceTimecode(recordedMs)
            } else {
                lastFile?.name ?: "m4a · 点击开始录音"
            },
            size = 16.sp,
            weight = FontWeight.Bold,
            color = OverlayTokens.panelPrimaryText,
            maxLines = 1
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
        ) {
            if (state == VoiceInputState.Recording) {
                SmallChoiceButton(
                    label = "取消",
                    onClick = { stopRecording(false) }
                )
            }
            SmallChoiceButton(
                label = if (state == VoiceInputState.Recording) {
                    "停止发送"
                } else {
                    "开始录音"
                },
                onClick = {
                    if (state == VoiceInputState.Recording) {
                        stopRecording(true)
                    } else {
                        startRecording()
                    }
                }
            )
        }
    }
}

private enum class VoiceInputState {
    Idle,
    PermissionRequired,
    Recording
}

private data class VoiceRecorderSession(
    val recorder: MediaRecorder,
    val file: File,
    val startedAtMs: Long
) {
    fun elapsedMs(): Int {
        return (System.currentTimeMillis() - startedAtMs).toInt().coerceAtLeast(0)
    }
}

private const val VoiceRecorderMimeType = "audio/mp4"
private const val VoiceRecorderFileExtension = "m4a"
