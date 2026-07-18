package com.paifa.ubikitouch.accessibility.floatingchat.aivoice

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun AiVoicePanel(
    state: AiVoiceState,
    onEvent: (AiVoiceEvent) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    tokens: AiVoiceTokens = AiVoiceTokens(),
    capabilityConfigState: AiVoiceCapabilityConfigState = AiVoiceCapabilityConfigState(),
    onCapabilityConfigEvent: (AiVoiceCapabilityConfigEvent) -> Unit = {}
) {
    val scrollState = rememberScrollState()
    var configExpanded by remember { mutableStateOf(false) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(tokens.panelPadding),
        verticalArrangement = Arrangement.spacedBy(tokens.itemSpacing)
    ) {
        AiVoiceHeader(
            root = state == AiVoiceState.Menu,
            onBack = { if (state == AiVoiceState.Menu) onClose() else onEvent(AiVoiceEvent.BackRequested) },
            tokens = tokens
        )
        if (state is AiVoiceState.RealtimeCall) {
            RealtimeCallPanel(state, onEvent, tokens, Modifier.weight(1f))
        } else Column(
            modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(tokens.itemSpacing)
        ) { when (state) {
            AiVoiceState.Menu -> {
                if (configExpanded) {
                    AiVoiceCapabilityConfigPanel(
                        state = capabilityConfigState,
                        onEvent = onCapabilityConfigEvent,
                        onSaved = { configExpanded = false },
                        tokens = tokens
                    )
                } else {
                    OutlinedButton(
                        onClick = { configExpanded = true },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("语音配置") }
                }
                HorizontalDivider()
                AiVoiceMenu(onEvent, tokens)
            }
            is AiVoiceState.CreatingVoiceProfile -> VoiceProfilePanel(state, onEvent, tokens)
            is AiVoiceState.GeneratingMessage -> VoiceMessagePanel(state, onEvent, tokens)
            is AiVoiceState.RealtimeCall -> Unit
            AiVoiceState.VoiceAgents -> VoiceAgentsPanel(onEvent, tokens)
            is AiVoiceState.Failed -> FailurePanel(state.message, onEvent, tokens)
        } }
    }
}

@Composable
private fun AiVoiceCapabilityConfigPanel(
    state: AiVoiceCapabilityConfigState,
    onEvent: (AiVoiceCapabilityConfigEvent) -> Unit,
    onSaved: () -> Unit,
    tokens: AiVoiceTokens
) {
    Column(verticalArrangement = Arrangement.spacedBy(tokens.itemSpacing)) {
        Text("豆包语音配置", style = tokens.itemTitleStyle)
        OutlinedTextField(
            value = state.apiKeyInput,
            onValueChange = { onEvent(AiVoiceCapabilityConfigEvent.ApiKeyChanged(it)) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("豆包语音 API Key") },
            singleLine = true,
            enabled = !state.loading,
            visualTransformation = PasswordVisualTransformation()
        )
        OutlinedTextField(
            value = state.appIdInput,
            onValueChange = { onEvent(AiVoiceCapabilityConfigEvent.AppIdChanged(it)) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("火山引擎 APP ID") },
            singleLine = true,
            enabled = !state.loading
        )
        OutlinedTextField(
            value = state.accessTokenInput,
            onValueChange = { onEvent(AiVoiceCapabilityConfigEvent.AccessTokenChanged(it)) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("实时语音 Access Token") },
            singleLine = true,
            enabled = !state.loading,
            visualTransformation = PasswordVisualTransformation()
        )
        Button(
            onClick = { onEvent(AiVoiceCapabilityConfigEvent.VerifyCapabilitiesRequested) },
            enabled = state.apiKeyInput.isNotBlank() &&
                state.appIdInput.isNotBlank() &&
                state.accessTokenInput.isNotBlank() &&
                !state.loading,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (state.loading) CircularProgressIndicator() else Text("一键测试全部能力")
        }
        Button(
            onClick = onSaved,
            enabled = !state.loading,
            modifier = Modifier.fillMaxWidth()
        ) { Text("保存") }
        state.statusMessage?.let { Text(it, style = tokens.descriptionStyle) }
        state.errorMessage?.let { Text(it, style = tokens.descriptionStyle) }
        AiVoiceFeature.entries.forEach { feature ->
            CapabilityStatusRow(feature, state.statusFor(feature), tokens)
        }
    }
}

@Composable
private fun CapabilityStatusRow(
    feature: AiVoiceFeature,
    status: AiVoiceCapabilityStatus,
    tokens: AiVoiceTokens
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(feature.title, style = tokens.itemTitleStyle)
            Text(feature.description, style = tokens.descriptionStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(status.label, style = tokens.descriptionStyle)
    }
}

@Composable
private fun AiVoiceModelConfigPanel(
    state: AiVoiceModelConfigState,
    onEvent: (AiVoiceModelConfigEvent) -> Unit,
    tokens: AiVoiceTokens
) {
    Column(verticalArrangement = Arrangement.spacedBy(tokens.itemSpacing)) {
        Text("模型配置", style = tokens.itemTitleStyle)
        OutlinedTextField(
            value = state.apiKeyInput,
            onValueChange = { onEvent(AiVoiceModelConfigEvent.ApiKeyChanged(it)) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Ark API Key") },
            singleLine = true,
            enabled = !state.loading,
            visualTransformation = PasswordVisualTransformation()
        )
        Button(
            onClick = { onEvent(AiVoiceModelConfigEvent.FetchModelsRequested) },
            enabled = state.apiKeyInput.isNotBlank() && !state.loading,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (state.loading) CircularProgressIndicator() else Text("获取模型")
        }
        state.statusMessage?.let { message ->
            Text(message, style = tokens.descriptionStyle)
        }
        state.errorMessage?.let { message ->
            Text(message, style = tokens.descriptionStyle)
        }
        val missingLabels = state.missingFeatureLabels()
        if (state.models.isNotEmpty() && missingLabels.isNotEmpty()) {
            Text("未匹配：" + missingLabels.joinToString("、"), style = tokens.descriptionStyle)
        }
        if (state.models.isNotEmpty()) {
            AiVoiceFeature.entries.forEach { feature ->
                ModelSelectionRow(feature, state, onEvent, tokens)
            }
        }
    }
}

@Composable
private fun ModelSelectionRow(
    feature: AiVoiceFeature,
    state: AiVoiceModelConfigState,
    onEvent: (AiVoiceModelConfigEvent) -> Unit,
    tokens: AiVoiceTokens
) {
    val candidates = state.compatibleModels(feature)
    val selected = state.selectedModel(feature)
    var expanded by remember(feature, candidates) { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(feature.title, style = tokens.itemTitleStyle)
            Text(
                selected?.id ?: "未发现匹配模型",
                style = tokens.descriptionStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                enabled = candidates.isNotEmpty()
            ) {
                Text(
                    selected?.displayName ?: "选择模型",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                candidates.forEach { model ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(model.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(model.displayStatus, style = tokens.descriptionStyle)
                            }
                        },
                        onClick = {
                            onEvent(AiVoiceModelConfigEvent.ModelSelected(feature, model.id))
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun AiVoiceHeader(root: Boolean, onBack: () -> Unit, tokens: AiVoiceTokens) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = onBack) { Text(if (root) "关闭" else "返回") }
        Column(modifier = Modifier.padding(start = tokens.itemSpacing)) {
            Text("AI语音", style = tokens.titleStyle)
            Text("选择并使用语音能力", style = tokens.descriptionStyle)
        }
    }
}

@Composable
private fun AiVoiceMenu(onEvent: (AiVoiceEvent) -> Unit, tokens: AiVoiceTokens) {
    AiVoiceFeature.entries.forEachIndexed { index, feature ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onEvent(AiVoiceEvent.FeatureSelected(feature)) }
                .padding(vertical = tokens.itemSpacing),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(feature.title, style = tokens.itemTitleStyle)
                Text(
                    feature.description,
                    style = tokens.descriptionStyle,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text("进入", style = tokens.descriptionStyle)
        }
        if (index < AiVoiceFeature.entries.lastIndex) HorizontalDivider()
    }
}

@Composable
private fun VoiceProfilePanel(
    profile: AiVoiceState.CreatingVoiceProfile,
    onEvent: (AiVoiceEvent) -> Unit,
    tokens: AiVoiceTokens
) {
    val step = profile.step
    val title = when (step) {
        VoiceProfileStep.Consent -> "创建我的音色"
        VoiceProfileStep.Recording -> "录制声音样本"
        VoiceProfileStep.Creating -> "正在创建音色"
        VoiceProfileStep.Completed -> "音色已创建"
    }
    Text(title, style = tokens.itemTitleStyle)
    when (step) {
        VoiceProfileStep.Consent -> {
            Text("请在安静环境使用自然语速录制。样本只用于创建专属音色。", style = tokens.descriptionStyle)
            Button(onClick = { onEvent(AiVoiceEvent.VoiceProfileConsentAccepted) }) { Text("开始创建") }
        }
        VoiceProfileStep.Recording -> {
            Text("请自然朗读下面这句话", style = tokens.descriptionStyle)
            Surface(shape = RoundedCornerShape(tokens.cornerRadius), color = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant) {
                Text(VoiceCloneReferenceText, modifier = Modifier.padding(tokens.panelPadding), style = tokens.itemTitleStyle)
            }
            OutlinedTextField(
                value = profile.speakerId,
                onValueChange = { onEvent(AiVoiceEvent.VoiceProfileSpeakerIdChanged(it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("火山控制台音色 ID") },
                enabled = !profile.isRecording
            )
            Text(if (profile.isRecording) "正在录制，请读完整句话…" else (profile.statusMessage ?: "建议录制 8 至 15 秒"), style = tokens.descriptionStyle)
            Row(horizontalArrangement = Arrangement.spacedBy(tokens.itemSpacing)) {
                OutlinedButton(
                    onClick = { onEvent(AiVoiceEvent.VoiceProfileRecordingStarted) },
                    enabled = !profile.isRecording
                ) { Text(if (profile.recordedAudioPath == null) "开始录制" else "重新录制") }
                Button(
                    onClick = { onEvent(AiVoiceEvent.VoiceProfileRecordingStopped) },
                    enabled = profile.isRecording
                ) { Text("停止录制") }
            }
            Button(
                onClick = { onEvent(AiVoiceEvent.VoiceProfileSubmitRequested) },
                enabled = profile.recordedAudioPath != null && profile.speakerId.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) { Text("提交训练") }
        }
        VoiceProfileStep.Creating -> {
            CircularProgressIndicator()
            Text("正在上传并创建音色，请保持网络连接。", style = tokens.descriptionStyle)
        }
        VoiceProfileStep.Completed -> Button(onClick = { onEvent(AiVoiceEvent.VoiceProfileCreated) }) {
            Text("使用此音色")
        }
    }
}

@Composable
private fun VoiceMessagePanel(
    state: AiVoiceState.GeneratingMessage,
    onEvent: (AiVoiceEvent) -> Unit,
    tokens: AiVoiceTokens
) {
    var draft by remember(state.feature) { mutableStateOf(state.text) }
    Text(state.feature.title, style = tokens.itemTitleStyle)
    OutlinedTextField(
        value = draft,
        onValueChange = { draft = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("语音内容") },
        enabled = !state.generating,
        minLines = 3,
        maxLines = 5
    )
    Button(
        onClick = {
            onEvent(AiVoiceEvent.MessageTextChanged(draft))
            onEvent(AiVoiceEvent.GenerateMessageRequested)
        },
        enabled = draft.isNotBlank() && !state.generating,
        modifier = Modifier.fillMaxWidth()
    ) {
        if (state.generating) CircularProgressIndicator() else Text("生成语音")
    }
    state.generatedAudioUrl?.takeIf { draft == state.text }?.let {
        Text("语音已生成，可试听后发送。", style = tokens.descriptionStyle)
        Row(horizontalArrangement = Arrangement.spacedBy(tokens.itemSpacing)) {
            OutlinedButton(onClick = { onEvent(AiVoiceEvent.PreviewGeneratedVoice) }) {
                Text("试听")
            }
            Button(onClick = { onEvent(AiVoiceEvent.SendGeneratedVoice) }) {
                Text("发送")
            }
        }
    }
}

@Composable
private fun RealtimeCallPanel(
    call: AiVoiceState.RealtimeCall,
    onEvent: (AiVoiceEvent) -> Unit,
    tokens: AiVoiceTokens,
    modifier: Modifier = Modifier
) {
    val state = call.state
    val label = when (state) {
        RealtimeCallState.Connecting -> "正在连接"
        RealtimeCallState.Listening -> "正在聆听"
        RealtimeCallState.UserSpeaking -> "你正在说话"
        RealtimeCallState.AiResponding -> "AI正在回应"
        RealtimeCallState.Interrupted -> "已打断，继续聆听"
        RealtimeCallState.Reconnecting -> "正在重新连接"
    }
    var elapsedSeconds by remember { mutableStateOf(0L) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1_000)
            elapsedSeconds++
        }
    }
    val duration = "%02d:%02d".format(elapsedSeconds / 60, elapsedSeconds % 60)
    val messageScrollState = rememberScrollState()
    androidx.compose.runtime.LaunchedEffect(messageScrollState) {
        snapshotFlow { messageScrollState.maxValue }.collect { bottom ->
            messageScrollState.animateScrollTo(bottom)
        }
    }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(tokens.itemSpacing)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = androidx.compose.foundation.shape.CircleShape,
                color = androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(40.dp)
            ) { Box(contentAlignment = Alignment.Center) { Text("AI", style = tokens.itemTitleStyle) } }
            Column(modifier = Modifier.padding(start = tokens.itemSpacing).weight(1f)) {
                Text("豆包语音助手", style = tokens.itemTitleStyle)
                Text("$label · $duration", style = tokens.descriptionStyle)
            }
            when (state) {
                RealtimeCallState.UserSpeaking -> VoiceActivityIndicator(true)
                RealtimeCallState.AiResponding -> VoiceActivityIndicator(false)
                else -> Unit
            }
        }
        HorizontalDivider()
        Column(
            modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(messageScrollState),
            verticalArrangement = Arrangement.spacedBy(tokens.itemSpacing)
        ) {
            if (call.messages.isEmpty()) {
                Text("可以直接说话，AI 回答时也能随时打断", style = tokens.descriptionStyle)
            }
            call.messages.forEachIndexed { index, message ->
                TranscriptBubble(
                    message = message,
                    streamAssistantText = message.speaker == RealtimeSpeaker.Assistant &&
                        index == call.messages.lastIndex && state == RealtimeCallState.AiResponding,
                    tokens = tokens
                )
            }
        }
        Button(onClick = { onEvent(AiVoiceEvent.CallEnded) }, modifier = Modifier.fillMaxWidth()) {
            Text("结束对话")
        }
    }
}

@Composable
private fun TranscriptBubble(
    message: RealtimeTranscriptMessage,
    streamAssistantText: Boolean,
    tokens: AiVoiceTokens
) {
    val user = message.speaker == RealtimeSpeaker.User
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (user) Alignment.End else Alignment.Start
    ) {
        Text(if (user) "我" else "AI", style = tokens.descriptionStyle)
        Surface(
            shape = RoundedCornerShape(tokens.cornerRadius),
            color = if (user) androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer
            else androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            if (user) {
                Text(message.text, modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp), style = tokens.itemTitleStyle)
            } else {
                StreamingAssistantText(message.text, streamAssistantText, tokens)
            }
        }
    }
}

@Composable
private fun StreamingAssistantText(target: String, active: Boolean, tokens: AiVoiceTokens) {
    var displayed by remember { mutableStateOf("") }
    val latestTarget by rememberUpdatedState(target)
    androidx.compose.runtime.LaunchedEffect(active) {
        while (active) {
            if (displayed != latestTarget) {
                displayed = nextStreamingSubtitle(displayed, latestTarget)
            }
            kotlinx.coroutines.delay(AssistantSubtitleCharacterDelayMs)
        }
    }
    Text(displayed, modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp), style = tokens.itemTitleStyle)
}

@Composable
private fun VoiceActivityIndicator(userSpeaking: Boolean) {
    val transition = rememberInfiniteTransition(label = "voice activity")
    val color = if (userSpeaking) androidx.compose.material3.MaterialTheme.colorScheme.primary
    else androidx.compose.material3.MaterialTheme.colorScheme.tertiary
    Row(
        modifier = Modifier.width(32.dp).height(24.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { index ->
            val scale by transition.animateFloat(
                initialValue = 0.35f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(360 + index * 90),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "voice bar $index"
            )
            Box(
                Modifier.width(6.dp).height(18.dp)
                    .graphicsLayer { scaleY = scale }
                    .background(color, RoundedCornerShape(3.dp))
            )
        }
    }
}

@Composable
private fun VoiceAgentsPanel(onEvent: (AiVoiceEvent) -> Unit, tokens: AiVoiceTokens) {
    Text("声音智能体", style = tokens.itemTitleStyle)
    Text("配置服务后，可在此创建角色、知识库、音色和好友访问范围。", style = tokens.descriptionStyle)
    OutlinedButton(onClick = { onEvent(AiVoiceEvent.BackRequested) }) { Text("返回") }
}

@Composable
private fun FailurePanel(message: String, onEvent: (AiVoiceEvent) -> Unit, tokens: AiVoiceTokens) {
    Surface(
        color = tokens.failureContainer,
        shape = RoundedCornerShape(tokens.cornerRadius),
        border = BorderStroke(AiVoiceFailureBorderWidth, tokens.failureBorder)
    ) {
        Column(Modifier.padding(tokens.panelPadding), verticalArrangement = Arrangement.spacedBy(tokens.itemSpacing)) {
            Text("AI语音不可用", style = tokens.itemTitleStyle)
            Text(message, style = tokens.descriptionStyle)
            Button(onClick = { onEvent(AiVoiceEvent.RetryRequested) }) { Text("返回重试") }
        }
    }
}

private val AiVoiceFailureBorderWidth = AiVoiceTokens().cornerRadius / 8
private const val AssistantSubtitleCharacterDelayMs = 180L
