package com.paifa.univerge.accessibility.floatingchat.tools

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.paifa.univerge.accessibility.floatingchat.components.FloatingWorkspaceTopAppBar
import kotlinx.coroutines.launch

private data class SideEffectTemplate(val title: String, val subtitle: String)
private data class SideEffectSettings(val templateIndex: Int, val fillColor: Long, val strokeColor: Long)

private val sideEffectTemplates = listOf(
    SideEffectTemplate("默认抛物线", "当前边缘水珠效果"),
    SideEffectTemplate("圆润水滴", "更短更饱满"),
    SideEffectTemplate("长弧面板", "更宽的侧边延展"),
    SideEffectTemplate("轻薄丝带", "细长透明"),
    SideEffectTemplate("胶囊浮层", "圆角柱状"),
    SideEffectTemplate("弹性波纹", "更强拉伸感")
)

private class SideEffectSettingsStore(context: Context) {
    private val preferences = context.getSharedPreferences("floating_chat_side_effect", Context.MODE_PRIVATE)

    fun load(): SideEffectSettings = SideEffectSettings(
        templateIndex = preferences.getInt("template", 0).coerceIn(0, sideEffectTemplates.lastIndex),
        fillColor = preferences.getLong("fill", 0xE0FFFFFF),
        strokeColor = preferences.getLong("stroke", 0x7A527DFA)
    )

    fun save(settings: SideEffectSettings) {
        preferences.edit()
            .putInt("template", settings.templateIndex)
            .putLong("fill", settings.fillColor)
            .putLong("stroke", settings.strokeColor)
            .apply()
    }
}

/**
 * 对应 iOS SideEffectConfigurationViewController 的 Android M3 全屏配置页。
 * iOS 仅将模板和双色配置持久化到本地，因此本页同样只保存本地偏好，不伪造接口请求。
 * 测试流程：点击任一“侧边特效”，切换模板与颜色标签，返回后再次进入确认配置仍保留；
 * 确认页面从下向上进入、从上向下退出，且没有 Dialog 或额外 Window。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SideEffectFullScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember(context) { SideEffectSettingsStore(context.applicationContext) }
    var settings by remember { mutableStateOf(store.load()) }
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState { 2 }
    fun update(next: SideEffectSettings) { settings = next; store.save(next) }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        // UI：右侧边缘特效复用 UI组件 工具栏和全屏工作区坐标系。
        // 测试流程：点击边缘特效确认自下向上进入，点击左上返回确认向顶部退出。
        FloatingWorkspaceTopAppBar(title = "侧边特效", onBack = onBack)
        PrimaryTabRow(selectedTabIndex = pagerState.currentPage) {
            listOf("特效模板", "颜色配置").forEachIndexed { index, title ->
                Tab(selected = pagerState.currentPage == index, onClick = { scope.launch { pagerState.animateScrollToPage(index) } }, text = { Text(title, fontWeight = FontWeight.Normal) })
            }
        }
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f).fillMaxWidth()) { page ->
            if (page == 0) SideEffectTemplatePage(settings, ::update) else SideEffectColorPage(settings, ::update)
        }
    }
}

@Composable
private fun SideEffectTemplatePage(settings: SideEffectSettings, onSettingsChange: (SideEffectSettings) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SideEffectPreview(settings) }
        item { Text("选择模板", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Normal, modifier = Modifier.padding(top = 8.dp)) }
        items(sideEffectTemplates.indices.toList()) { index ->
            val template = sideEffectTemplates[index]
            Card(modifier = Modifier.fillMaxWidth().clickable { onSettingsChange(settings.copy(templateIndex = index)) }, colors = CardDefaults.cardColors(containerColor = if (settings.templateIndex == index) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(template.title, fontWeight = FontWeight.Normal)
                    Text(template.subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun SideEffectColorPage(settings: SideEffectSettings, onSettingsChange: (SideEffectSettings) -> Unit) {
    val palette = listOf(0xE0FFFFFFL, 0xE0E8F0FEL, 0xE0E0F2F1L, 0xE0FFF3E0L)
    val strokes = listOf(0x7A527DFAL, 0x7A00897BL, 0x7AE65100L, 0x7AC2185BL)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { SideEffectPreview(settings) }
        item { Text("填充颜色", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Normal) }
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { palette.forEachIndexed { index, color -> FilterChip(selected = settings.fillColor == color, onClick = { onSettingsChange(settings.copy(fillColor = color)) }, label = { Text("方案 ${index + 1}") }) } } }
        item { Text("描边颜色", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Normal, modifier = Modifier.padding(top = 8.dp)) }
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { strokes.forEachIndexed { index, color -> FilterChip(selected = settings.strokeColor == color, onClick = { onSettingsChange(settings.copy(strokeColor = color)) }, label = { Text("描边 ${index + 1}") }) } } }
    }
}

@Composable
private fun SideEffectPreview(settings: SideEffectSettings) {
    Card(Modifier.fillMaxWidth().border(2.dp, Color(settings.strokeColor), CardDefaults.shape), colors = CardDefaults.cardColors(containerColor = Color(settings.fillColor))) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column { Text(sideEffectTemplates[settings.templateIndex].title, fontWeight = FontWeight.Normal); Text("侧边效果实时预览", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}
