package com.paifa.ubikitouch.accessibility

import android.content.Context
import android.graphics.PixelFormat
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.paifa.ubikitouch.accessibility.floatingchat.components.FloatingWorkspaceTopAppBar
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class AiAutoReplyOverlayPresentation(
    val width: Int,
    val height: Int,
    val type: Int,
    val focusable: Boolean
)

internal fun aiAutoReplyOverlayWindowPresentation() = AiAutoReplyOverlayPresentation(
    width = WindowManager.LayoutParams.MATCH_PARENT,
    height = WindowManager.LayoutParams.MATCH_PARENT,
    type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
    focusable = true
)

internal fun aiAutoReplyStatusBarHeightDp(): Int = 30

internal fun aiAutoReplyEntryTranslationY(heightPx: Int): Float = heightPx.coerceAtLeast(0).toFloat()

internal fun aiAutoReplyExitTranslationY(heightPx: Int): Float {
    val height = heightPx.coerceAtLeast(0)
    return if (height == 0) 0f else -height.toFloat()
}

/**
 * AI 自动回复全屏窗口宿主。
 * 测试流程：点击右侧“AI自动回复”，编辑配置并保存，切换“连接测试”执行真实请求，再点击左上角返回。
 */
internal class AiAutoReplyOverlayController(
    private val context: Context,
    private val windowManager: WindowManager
) {
    private var view: ComposeView? = null
    private var owner: AccessibilityOverlayComposeOwner? = null

    /** 使用受保护的 WindowManager 添加流程，窗口令牌失效时销毁 Compose 生命周期以避免 BadTokenException 泄漏。 */
    fun show() {
        if (view != null) return
        val composeOwner = AccessibilityOverlayComposeOwner()
        val composeView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(composeOwner)
            setViewTreeSavedStateRegistryOwner(composeOwner)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnLifecycleDestroyed(composeOwner.lifecycle))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            translationY = aiAutoReplyEntryTranslationY(context.resources.displayMetrics.heightPixels)
            alpha = 0f
            setContent { AiAutoReplyFullScreen(context = context, onBack = ::dismiss) }
        }
        runCatching {
            windowManager.addView(composeView, layoutParams())
            view = composeView
            owner = composeOwner
            composeView.post {
                if (view !== composeView) return@post
                composeView.animate()
                    .translationY(0f)
                    .alpha(1f)
                    .setDuration(SHOW_DURATION_MILLIS)
                    .setInterpolator(DecelerateInterpolator(2f))
                    .start()
            }
        }.onFailure { error ->
            composeOwner.destroy()
            Log.w(TAG, "failed to add AI auto reply overlay", error)
        }
    }

    /** 关闭时通过实体 View 的 translationY 属性动画上移，动画结束后立即释放窗口和生命周期。 */
    fun dismiss() {
        val currentView = view ?: return
        currentView.animate().cancel()
        val height = currentView.height.takeIf { it > 0 } ?: context.resources.displayMetrics.heightPixels
        currentView.animate()
            .translationY(aiAutoReplyExitTranslationY(height))
            .alpha(0f)
            .setDuration(HIDE_DURATION_MILLIS)
            .setInterpolator(AccelerateInterpolator(1.5f))
            .withEndAction { if (view === currentView) dismissImmediately() }
            .start()
    }

    fun dismissImmediately() {
        val currentView = view ?: return
        view = null
        currentView.animate().cancel()
        owner?.destroy()
        owner = null
        runCatching { windowManager.removeViewImmediate(currentView) }
            .onFailure { error -> Log.w(TAG, "failed to remove AI auto reply overlay", error) }
    }

    private fun layoutParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.START
    }

    private companion object {
        const val TAG = "AiAutoReplyOverlay"
        const val SHOW_DURATION_MILLIS = 260L
        const val HIDE_DURATION_MILLIS = 190L
    }
}

private enum class AiAutoReplyTab(val label: String) {
    Configuration("配置"),
    ConnectionTest("连接测试"),
    Status("运行状态")
}

private data class AiAutoReplyTestRecord(val title: String, val detail: String)

/** 复用 FloatingChatAiClient 的真实网关，UI 不制造本地成功结果。 */
private class AiAutoReplyGateway(private val client: FloatingChatAiClient = FloatingChatAiClient()) {
    fun testConnection(config: FloatingChatAiConfig): String = client.testConfig(config)

    fun fetchModels(config: FloatingChatAiConfig): List<String> = client.listModels(config)
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun AiAutoReplyFullScreen(context: Context, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val gateway = remember { AiAutoReplyGateway() }
    val pager = rememberPagerState { AiAutoReplyTab.entries.size }
    var candidate by remember { mutableStateOf(loadFloatingChatAiConfig(context)) }
    var temperatureText by remember(candidate.temperature) { mutableStateOf(candidate.temperature.toString()) }
    var maxTokensText by remember(candidate.maxTokens) { mutableStateOf(candidate.maxTokens.toString()) }
    var models by remember { mutableStateOf(emptyList<String>()) }
    var status by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }
    var loadingModels by remember { mutableStateOf(false) }
    var modelMenuExpanded by remember { mutableStateOf(false) }
    val records = remember { mutableStateListOf<AiAutoReplyTestRecord>() }
    val busy = testing || loadingModels

    /** 保存自动回复配置，沿用悬浮聊天的 SharedPreferences 契约。 */
    fun saveConfiguration() {
        saveFloatingChatAiConfig(context, candidate)
        candidate = loadFloatingChatAiConfig(context)
        status = "配置已保存"
    }

    /** 真实测试调用：不完整配置和网关错误都会保留为可见状态。 */
    fun testConnection() {
        if (busy) return
        if (!candidate.isConfigured) {
            status = "请填写 API 地址、API Key 和模型"
            return
        }
        scope.launch {
            testing = true
            status = "正在测试 AI 连接"
            runCatching { withContext(Dispatchers.IO) { gateway.testConnection(candidate) } }
                .onSuccess { reply ->
                    val detail = reply.trim().take(120)
                    status = "AI 连接测试成功：$detail"
                    records.add(0, AiAutoReplyTestRecord("连接测试成功", detail))
                }
                .onFailure { error ->
                    val httpError = error as? FloatingChatAiHttpException
                    val detail = floatingChatAiFailureMessage(
                        statusCode = httpError?.statusCode,
                        detail = httpError?.message ?: error.message.orEmpty()
                    )
                    status = detail
                    records.add(0, AiAutoReplyTestRecord("连接测试失败", detail))
                }
            testing = false
        }
    }

    /** 真实模型列表调用：仅将网关返回的 model id 提供给下拉选择组件。 */
    fun fetchModels() {
        if (busy) return
        if (!candidate.isConfigured) {
            status = "请先填写 API 地址、API Key 和模型"
            return
        }
        scope.launch {
            loadingModels = true
            status = "正在读取模型列表"
            runCatching { withContext(Dispatchers.IO) { gateway.fetchModels(candidate) } }
                .onSuccess { result ->
                    models = result
                    modelMenuExpanded = true
                    status = "已读取 ${result.size} 个模型"
                }
                .onFailure { error ->
                    val httpError = error as? FloatingChatAiHttpException
                    status = floatingChatAiFailureMessage(
                        statusCode = httpError?.statusCode,
                        detail = httpError?.message ?: error.message.orEmpty()
                    )
                }
            loadingModels = false
        }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        // UI：AI自动回复复用 UI组件 的 toolbar，状态区由共享组件内嵌处理。
        // 测试流程：从右侧打开 AI自动回复，确认自下向上进入并通过左上返回向顶部退出。
        FloatingWorkspaceTopAppBar(title = "AI自动回复", onBack = onBack)
        PrimaryTabRow(selectedTabIndex = pager.currentPage) {
            AiAutoReplyTab.entries.forEachIndexed { index, tab ->
                Tab(
                    selected = pager.currentPage == index,
                    onClick = { scope.launch { pager.animateScrollToPage(index) } },
                    text = { Text(tab.label, fontWeight = FontWeight.Normal) }
                )
            }
        }
        HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { page ->
            when (AiAutoReplyTab.entries[page]) {
                AiAutoReplyTab.Configuration -> AiAutoReplyConfigurationPage(
                    candidate = candidate,
                    temperatureText = temperatureText,
                    maxTokensText = maxTokensText,
                    models = models,
                    modelMenuExpanded = modelMenuExpanded,
                    busy = busy,
                    status = status,
                    onAutoReplyEnabledChanged = { candidate = candidate.copy(autoReplyEnabled = it) },
                    onBaseUrlChanged = { candidate = candidate.copy(baseUrl = it) },
                    onApiKeyChanged = { candidate = candidate.copy(apiKey = it) },
                    onModelChanged = { candidate = candidate.copy(model = it) },
                    onSystemPromptChanged = { candidate = candidate.copy(systemPrompt = it) },
                    onTemperatureChanged = { value ->
                        temperatureText = value
                        value.toFloatOrNull()?.let { candidate = candidate.copy(temperature = it) }
                    },
                    onMaxTokensChanged = { value ->
                        maxTokensText = value.filter(Char::isDigit)
                        maxTokensText.toIntOrNull()?.let { candidate = candidate.copy(maxTokens = it) }
                    },
                    onModelMenuExpandedChanged = { modelMenuExpanded = it },
                    onFetchModels = ::fetchModels,
                    onSave = ::saveConfiguration
                )
                AiAutoReplyTab.ConnectionTest -> AiAutoReplyConnectionTestPage(
                    configured = candidate.isConfigured,
                    busy = busy,
                    status = status,
                    onTest = ::testConnection
                )
                AiAutoReplyTab.Status -> AiAutoReplyStatusPage(
                    enabled = candidate.autoReplyEnabled,
                    status = status,
                    records = records
                )
            }
        }
    }
}

@Composable
private fun AiAutoReplyConfigurationPage(
    candidate: FloatingChatAiConfig,
    temperatureText: String,
    maxTokensText: String,
    models: List<String>,
    modelMenuExpanded: Boolean,
    busy: Boolean,
    status: String?,
    onAutoReplyEnabledChanged: (Boolean) -> Unit,
    onBaseUrlChanged: (String) -> Unit,
    onApiKeyChanged: (String) -> Unit,
    onModelChanged: (String) -> Unit,
    onSystemPromptChanged: (String) -> Unit,
    onTemperatureChanged: (String) -> Unit,
    onMaxTokensChanged: (String) -> Unit,
    onModelMenuExpandedChanged: (Boolean) -> Unit,
    onFetchModels: () -> Unit,
    onSave: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().imePadding(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                "启用后，新消息将生成待确认的回复草稿，不会自动发送。",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("自动回复", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Normal)
                    Box(Modifier.fillMaxWidth().padding(top = 10.dp)) {
                        Text(
                            if (candidate.autoReplyEnabled) "已启用，等待新消息" else "未启用",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.CenterStart)
                        )
                        Switch(
                            checked = candidate.autoReplyEnabled,
                            onCheckedChange = onAutoReplyEnabledChanged,
                            enabled = !busy,
                            modifier = Modifier.align(Alignment.CenterEnd)
                        )
                    }
                }
            }
        }
        item {
            Text("服务配置", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Normal)
        }
        item {
            OutlinedTextField(
                value = candidate.baseUrl,
                onValueChange = onBaseUrlChanged,
                label = { Text("API 地址") },
                supportingText = { Text("例如 https://cc2.cx/v1") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !busy
            )
        }
        item {
            OutlinedTextField(
                value = candidate.apiKey,
                onValueChange = onApiKeyChanged,
                label = { Text("API Key") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !busy,
                visualTransformation = PasswordVisualTransformation()
            )
        }
        item {
            Box(Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = {
                        if (models.isEmpty()) onFetchModels() else onModelMenuExpandedChanged(true)
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (busy) CircularProgressIndicator() else Icon(Icons.Filled.Refresh, contentDescription = null)
                    Text(if (models.isEmpty()) "读取模型列表" else "选择模型", modifier = Modifier.padding(start = 8.dp))
                }
                DropdownMenu(expanded = modelMenuExpanded && models.isNotEmpty(), onDismissRequest = { onModelMenuExpandedChanged(false) }) {
                    models.forEach { model ->
                        DropdownMenuItem(text = { Text(model) }, onClick = {
                            onModelChanged(model)
                            onModelMenuExpandedChanged(false)
                        })
                    }
                }
            }
        }
        item {
            OutlinedTextField(
                value = candidate.model,
                onValueChange = onModelChanged,
                label = { Text("模型") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !busy
            )
        }
        item {
            OutlinedTextField(
                value = candidate.systemPrompt,
                onValueChange = onSystemPromptChanged,
                label = { Text("自动回复提示词") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 5,
                enabled = !busy
            )
        }
        item {
            OutlinedTextField(
                value = temperatureText,
                onValueChange = onTemperatureChanged,
                label = { Text("温度") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !busy,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
        }
        item {
            OutlinedTextField(
                value = maxTokensText,
                onValueChange = onMaxTokensChanged,
                label = { Text("最大 Token") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !busy,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        }
        status?.takeIf(String::isNotBlank)?.let { message ->
            item { Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        item {
            Button(onClick = onSave, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text("保存配置")
            }
        }
    }
}

@Composable
private fun AiAutoReplyConnectionTestPage(
    configured: Boolean,
    busy: Boolean,
    status: String?,
    onTest: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("连接测试", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Normal)
        }
        item {
            Text(
                if (configured) "将按当前填写的地址、密钥和模型发送真实测试请求。" else "请先在“配置”页填写 API 地址、API Key 和模型。",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        status?.takeIf(String::isNotBlank)?.let { message ->
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Text(message, modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Button(onClick = onTest, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                if (busy) CircularProgressIndicator() else Text("测试连接")
            }
        }
    }
}

@Composable
private fun AiAutoReplyStatusPage(
    enabled: Boolean,
    status: String?,
    records: List<AiAutoReplyTestRecord>
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("运行状态", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Normal)
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(if (enabled) "自动回复已启用" else "自动回复未启用", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Normal)
                    Text("生成的回复草稿仍需用户确认后发送。", modifier = Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        status?.takeIf(String::isNotBlank)?.let { message ->
            item { Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        if (records.isEmpty()) {
            item { Text("当前会话尚未执行连接测试。", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            items(records) { record ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(record.title, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Normal)
                        Text(record.detail, modifier = Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
