package com.paifa.univerge.accessibility.floatingchat.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.DynamicFeed
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.paifa.univerge.accessibility.floatingchat.components.FloatingWorkspaceTopAppBar
import com.paifa.univerge.accessibility.floatingchat.components.FloatingWorkspaceTopBarDefaults
import kotlinx.coroutines.launch

internal const val UiComponentsStatusBarHeightDp = FloatingWorkspaceTopBarDefaults.StatusBarTopPaddingDp

internal enum class UiComponentsFullScreenTab(val label: String) {
    Operation("操作组件"),
    Modules("已接入模块")
}

internal data class IosAppKitModule(
    val title: String,
    val directory: String,
    val responsibility: String,
    val status: String,
    val owner: String,
    val icon: ImageVector
)

/** iOS AppKitRegistry 的六个模块边界，安卓仅展示已接入职责，不伪造网络接口。 */
internal val iosAppKitModuleManifest = listOf(
    IosAppKitModule("交互 SDK", "Modules/Interaction", "眨眼、侧边拉起、灵动岛与手势拖拽等跨页面交互能力。", "已接入悬浮交互适配入口。", "Interaction", Icons.Filled.Gesture),
    IosAppKitModule("悬浮 IM Kit", "Modules/FloatingIM", "账号轨道、会话列表、连接线、拖拽转发和悬浮窗口。", "已接入悬浮聊天工作区。", "IM", Icons.Filled.Forum),
    IosAppKitModule("消息渲染 Kit", "Modules/MessageRender", "文本、媒体、红包、转账、文件与卡片消息渲染。", "已接入消息类型与渲染契约。", "Render", Icons.Filled.DynamicFeed),
    IosAppKitModule("AI 输入 Kit", "Modules/AIInput", "草稿、润色、续写、自动回复、意图与日历写入。", "已接入 AI 配置与自动回复接口。", "AI", Icons.Filled.SmartToy),
    IosAppKitModule("数据层", "Modules/Data", "SQLite 消息、附件索引、AB 配置、OpenAPI 缓存与本地数据。", "已接入本地会话与配置存储。", "Data", Icons.Filled.DataObject),
    IosAppKitModule("业务页面模块", "Modules/Business", "朋友圈、素材库、客户档案、视频号发布和 OpenAPI 页面。", "已接入现有业务路由。", "Business", Icons.Filled.Extension)
)

/**
 * 对应 iOS OperationLab 与 AppKitRegistry 的 M3 全屏工作区。
 * 测试流程：点击右侧 UI组件，切换两个 Tab，填写表单后执行，再通过左上返回关闭。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun UiComponentsFullScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { UiComponentsFullScreenTab.entries.size })

    Column(modifier = Modifier.fillMaxSize()) {
        // 将悬浮状态区内嵌到工具栏顶部 padding，避免独立空白区域造成页面跳变。
        FloatingWorkspaceTopAppBar(title = "UI组件", onBack = onBack)
        PrimaryTabRow(selectedTabIndex = pagerState.currentPage) {
            UiComponentsFullScreenTab.entries.forEachIndexed { index, tab ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                    text = { Text(tab.label, fontWeight = FontWeight.Normal) }
                )
            }
        }
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
            when (UiComponentsFullScreenTab.entries[page]) {
                UiComponentsFullScreenTab.Operation -> OperationComponentsPage()
                UiComponentsFullScreenTab.Modules -> AppKitModulesPage()
            }
        }
    }
}

/** iOS OperationLab 的表单、选择、范围和进度能力，状态仅保留在当前全屏工作区。 */
@Composable
private fun OperationComponentsPage() {
    var taskName by remember { mutableStateOf("客户转化日报") }
    var budget by remember { mutableStateOf("12800") }
    var selectedMode by remember { mutableStateOf("销售") }
    var selectedScenario by remember { mutableStateOf("客户增长分析") }
    var selectedRange by remember { mutableStateOf(22f..74f) }
    var rulerValue by remember { mutableFloatStateOf(64f) }
    var progress by remember { mutableFloatStateOf(0.64f) }
    var result by remember { mutableStateOf("等待执行操作") }
    var droppedItem by remember { mutableStateOf<String?>(null) }
    val modes = listOf("销售", "服务", "运营")
    val scenarios = listOf("客户增长分析", "朋友圈素材投放", "视频号转化", "群运营复盘")

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SectionHeader("操作组件工作区", "表单、选择、范围、进度和结果在同一工作区内测试。", Icons.Filled.AutoAwesome)
        }
        item {
            ComponentCard(title = "表单") {
                OutlinedTextField(value = taskName, onValueChange = { taskName = it }, modifier = Modifier.fillMaxWidth(), label = { Text("任务名称") }, singleLine = true)
                OutlinedTextField(value = budget, onValueChange = { budget = it.filter(Char::isDigit) }, modifier = Modifier.fillMaxWidth(), label = { Text("预算金额") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    modes.forEach { mode ->
                        FilterChip(selected = selectedMode == mode, onClick = { selectedMode = mode }, label = { Text(mode) })
                    }
                }
                Button(
                    onClick = {
                        progress = (selectedRange.endInclusive / 100f).coerceIn(0f, 1f)
                        result = "已执行：$selectedMode · $taskName · 预算 $budget"
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("执行表单操作")
                }
            }
        }
        item {
            ComponentCard(title = "选择器") {
                scenarios.forEach { scenario ->
                    FilterChip(selected = selectedScenario == scenario, onClick = { selectedScenario = scenario }, label = { Text(scenario) })
                }
                Text("当前场景：$selectedScenario", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            ComponentCard(title = "范围与进度") {
                Text("双向范围：${selectedRange.start.toInt()} - ${selectedRange.endInclusive.toInt()}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                RangeSlider(value = selectedRange, onValueChange = { selectedRange = it }, valueRange = 0f..100f)
                Text("刻度值：${rulerValue.toInt()}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Slider(value = rulerValue, onValueChange = { rulerValue = it }, valueRange = 0f..100f, steps = 9)
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(progress = { progress }, modifier = Modifier.height(36.dp).width(36.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("执行进度：${(progress * 100).toInt()}%", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            ComponentCard(title = "图表") {
                OperationMetric("成交", 0.72f)
                OperationMetric("触达趋势", 0.58f)
                OperationMetric("复购意向", 0.46f)
            }
        }
        item {
            ComponentCard(title = "放入区") {
                Text(droppedItem ?: "选择下方项目放入当前操作区", color = MaterialTheme.colorScheme.onSurfaceVariant)
                FilterChip(
                    selected = droppedItem != null,
                    onClick = {
                        droppedItem = "已放入：高价值客户"
                        progress = (progress + 0.08f).coerceAtMost(1f)
                        result = "已接收放入项：高价值客户"
                    },
                    label = { Text("放入高价值客户") }
                )
            }
        }
        item {
            ComponentCard(title = "执行结果") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text(result, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

/** 以 M3 进度条映射 iOS OperationLab 的柱状和趋势图数据。 */
@Composable
private fun OperationMetric(label: String, value: Float) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        LinearProgressIndicator(progress = { value }, modifier = Modifier.fillMaxWidth())
    }
}

/** iOS AppKitRegistry 模块清单使用 LazyColumn 呈现，保留目录、职责和当前接入状态。 */
@Composable
private fun AppKitModulesPage() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SectionHeader("已接入模块", "对应 iOS AppKitRegistry 的可复用 Kit 边界。", Icons.Filled.BarChart)
        }
        items(iosAppKitModuleManifest, key = { it.directory }) { module ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(module.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Text(module.title, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Normal, style = MaterialTheme.typography.titleMedium)
                    }
                    Text(module.directory, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                    Text(module.responsibility, style = MaterialTheme.typography.bodyMedium)
                    HorizontalDivider()
                    Text("${module.owner} · ${module.status}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String, icon: ImageVector) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Normal)
                Text(subtitle, color = MaterialTheme.colorScheme.onSecondaryContainer, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun ComponentCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Normal)
            content()
        }
    }
}
