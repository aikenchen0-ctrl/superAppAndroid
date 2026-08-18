package com.paifa.univerge.accessibility.floatingchat.moments

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paifa.univerge.accessibility.scrm.ScrmMomentMaterial
import com.paifa.univerge.accessibility.scrm.ScrmMomentMaterialControlRequest
import com.paifa.univerge.accessibility.scrm.ScrmMomentMaterialCopyRequest
import com.paifa.univerge.accessibility.scrm.ScrmMomentMaterialCreateRequest
import com.paifa.univerge.accessibility.scrm.ScrmMomentMaterialDetail
import com.paifa.univerge.accessibility.scrm.ScrmMomentMaterialQuery
import com.paifa.univerge.accessibility.scrm.ScrmMomentMaterialUpdateRequest
import com.paifa.univerge.accessibility.scrm.ScrmMomentPostPayload
import com.paifa.univerge.accessibility.scrm.ScrmSettingsManager
import com.paifa.univerge.accessibility.floatingchat.components.FloatingWorkspaceTopAppBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val LibraryCard = Color.White
private val LibraryPrimary = Color(0xFF171719)
private val LibrarySecondary = Color(0xFF76767C)
private val LibraryGreen = Color(0xFF1E785A)

private enum class MaterialEditorMode { Create, Edit }
private enum class MaterialLibraryScreen { List, Detail, Editor }
private data class MaterialDraft(val name: String = "", val category: String = "", val content: String = "")

@Composable
fun MaterialLibraryActivityContent(context: Context, onClose: () -> Unit) {
    val manager = remember(context) { ScrmSettingsManager(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var materials by remember { mutableStateOf<List<ScrmMomentMaterial>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<ScrmMomentMaterial?>(null) }
    var detail by remember { mutableStateOf<ScrmMomentMaterialDetail?>(null) }
    var editorMode by remember { mutableStateOf<MaterialEditorMode?>(null) }
    var draft by remember { mutableStateOf(MaterialDraft()) }
    var archiveTarget by remember { mutableStateOf<ScrmMomentMaterial?>(null) }
    var screen by remember { mutableStateOf(MaterialLibraryScreen.List) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    fun refresh() = scope.launch {
        loading = true; message = null
        runCatching { withContext(Dispatchers.IO) { manager.loadSelectedSessionOrBootstrap().momentApi.getMomentMaterials(ScrmMomentMaterialQuery(skip = 0, take = 80)) } }
            .onSuccess { materials = it; selected = selected?.let { current -> it.firstOrNull { item -> item.id == current.id } } }
            .onFailure { message = it.message ?: "素材库加载失败" }
        loading = false
    }
    fun openDetail(material: ScrmMomentMaterial) = scope.launch {
        loading = true; selected = material; message = null
        runCatching { withContext(Dispatchers.IO) { manager.loadSelectedSessionOrBootstrap().momentApi.getMomentMaterialDetail(material.id, material.tenantId) } }
            .onSuccess { detail = it; screen = MaterialLibraryScreen.Detail }
            .onFailure { message = it.message ?: "素材详情加载失败" }
        loading = false
    }
    fun save() = scope.launch {
        loading = true; message = null
        runCatching<ScrmMomentMaterial> {
            withContext(Dispatchers.IO) {
                val session = manager.loadSelectedSessionOrBootstrap()
                val payload = ScrmMomentPostPayload(weChatId = session.weChatId, content = draft.content.trim())
                when (editorMode) {
                    MaterialEditorMode.Create -> session.momentApi.createMomentMaterial(ScrmMomentMaterialCreateRequest(payload = payload, content = draft.content.trim(), name = draft.name.trim().ifBlank { null }, category = draft.category.trim().ifBlank { null }, clientRequestId = "material-${System.currentTimeMillis()}", enableImmediately = true))
                    MaterialEditorMode.Edit -> {
                        val current = requireNotNull(selected)
                        session.momentApi.updateMomentMaterial(
                            current.id,
                            ScrmMomentMaterialUpdateRequest(
                                payload = payload,
                                content = draft.content.trim(),
                                name = draft.name.trim().ifBlank { null },
                                category = draft.category.trim().ifBlank { null },
                                enableImmediately = true
                            )
                        )
                        session.momentApi.getMomentMaterial(current.id, current.tenantId)
                    }
                    null -> error("素材编辑状态缺失")
                }
            }
        }.onSuccess { saved ->
            materials = if (editorMode == MaterialEditorMode.Create) listOf(saved) + materials else materials.map { if (it.id == saved.id) saved else it }
            selected = saved; detail = null; editorMode = null; screen = MaterialLibraryScreen.List; message = "素材已保存到朋友圈素材库"
        }.onFailure { message = it.message ?: "素材保存失败" }
        loading = false
    }
    fun copy(material: ScrmMomentMaterial) = scope.launch {
        loading = true
        runCatching { withContext(Dispatchers.IO) { manager.loadSelectedSessionOrBootstrap().momentApi.copyMomentMaterial(material.id, ScrmMomentMaterialCopyRequest("${material.displayName} 副本", true)) } }
            .onSuccess { copied -> materials = listOf(copied) + materials; message = "已复制素材" }
            .onFailure { message = it.message ?: "复制素材失败" }
        loading = false
    }
    fun archive(material: ScrmMomentMaterial) = scope.launch {
        loading = true
        runCatching { withContext(Dispatchers.IO) { manager.loadSelectedSessionOrBootstrap().momentApi.archiveMomentMaterial(material.id, ScrmMomentMaterialControlRequest("用户从素材库归档")) } }
            .onSuccess { archived -> materials = materials.map { if (it.id == archived.id) archived else it }; selected = archived; detail = null; message = "素材已归档" }
            .onFailure { message = it.message ?: "归档素材失败" }
        loading = false
    }
    LaunchedEffect(Unit) { refresh() }

    val categories = materials.mapNotNull { it.category?.trim()?.takeIf(String::isNotEmpty) }.distinct().sorted()
    val visible = materials.filter { item ->
        val searchableText = listOf(
            item.displayName,
            item.category.orEmpty(),
            item.createdBy.orEmpty(),
            item.updatedBy.orEmpty(),
            item.statusName.orEmpty()
        ).joinToString(" ")
        (category == null || item.category == category) && searchableText.contains(query.trim(), ignoreCase = true)
    }
    if (screen == MaterialLibraryScreen.List) Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        // UI：素材库首页复用 UI组件 的 surface M3 工具栏，30dp 顶部安全区只由共享组件处理。
        // 测试流程：从右侧素材库打开，创建或刷新后点击左上返回，确认页面由聊天根统一退出。
        FloatingWorkspaceTopAppBar(
            title = "朋友圈素材库",
            onBack = onClose,
            actions = {
                IconButton(
                    onClick = {
                        editorMode = MaterialEditorMode.Create
                        draft = MaterialDraft()
                        screen = MaterialLibraryScreen.Editor
                    },
                    enabled = !loading
                ) {
                    Icon(Icons.Filled.Add, "新建素材")
                }
                IconButton(onClick = ::refresh, enabled = !loading) {
                    Icon(Icons.Filled.Refresh, "刷新")
                }
            }
        )
        MaterialLibrarySummary(materials)
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 9.dp), singleLine = true, leadingIcon = { Icon(Icons.Filled.Search, null) }, placeholder = { Text("搜索名称、正文、附件 URL、创建人") })
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            LibraryFilter("全部", category == null) { category = null }
            categories.forEach { value -> LibraryFilter(value, category == value) { category = value } }
        }
        message?.let { Text(it, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), color = if (it.endsWith("失败")) Color(0xFFB3261E) else LibrarySecondary, fontSize = 12.sp) }
        archiveTarget?.let { material ->
            MaterialArchiveConfirmation(
                material = material,
                onCancel = { archiveTarget = null },
                onConfirm = {
                    archiveTarget = null
                    archive(material)
                }
            )
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (visible.isEmpty()) item { Text(if (loading) "正在加载素材..." else "暂无匹配的素材", Modifier.fillMaxWidth().padding(32.dp), color = LibrarySecondary) }
            items(visible, key = { it.id }) { material -> MaterialCard(material, onOpen = { openDetail(material) }, onCopy = { copy(material) }, onArchive = { archiveTarget = material }) }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }
    detail?.takeIf { screen == MaterialLibraryScreen.Detail }?.let { loaded ->
        MaterialDetailPage(
            detail = loaded,
            onBack = { screen = MaterialLibraryScreen.List; detail = null },
            onEdit = {
                editorMode = MaterialEditorMode.Edit
                draft = MaterialDraft(loaded.name.orEmpty(), loaded.category.orEmpty(), loaded.template?.content.orEmpty())
                screen = MaterialLibraryScreen.Editor
            }
        )
    }
    editorMode?.takeIf { screen == MaterialLibraryScreen.Editor }?.let { mode ->
        MaterialEditorPage(mode, draft, loading, { draft = it }, ::save) {
            editorMode = null
            screen = if (detail == null) MaterialLibraryScreen.List else MaterialLibraryScreen.Detail
        }
    }
}

@Composable
private fun MaterialLibrarySummary(materials: List<ScrmMomentMaterial>) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 12.dp),
        shape = RoundedCornerShape(18.dp),
        color = LibraryCard
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("朋友圈素材库", color = LibraryPrimary, fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryStat("素材", materials.size.toString(), Modifier.weight(1f))
                SummaryStat("启用", materials.count { it.status == 1 }.toString(), Modifier.weight(1f))
                SummaryStat("附件", materials.sumOf { it.attachmentCount }.toString(), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SummaryStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.background(Color(0xFFF0F3F2), RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 8.dp)) {
        Text(value, color = LibraryPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Text(label, color = LibrarySecondary, fontSize = 11.sp)
    }
}
@Composable
private fun LibraryFilter(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(Modifier.clickable(onClick = onClick), RoundedCornerShape(7.dp), color = if (selected) LibraryGreen else Color(0xFFE4E6E9)) {
        Text(text, Modifier.padding(horizontal = 10.dp, vertical = 7.dp), color = if (selected) Color.White else LibraryPrimary, fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
private fun MaterialCard(material: ScrmMomentMaterial, onOpen: () -> Unit, onCopy: () -> Unit, onArchive: () -> Unit) {
    Surface(Modifier.fillMaxWidth().clickable(onClick = onOpen), RoundedCornerShape(10.dp), color = LibraryCard) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(42.dp).background(Color(0xFFE7F2ED), RoundedCornerShape(9.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Filled.Image, null, tint = LibraryGreen) }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(material.displayName, color = LibraryPrimary, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(listOf(material.category.orEmpty().ifBlank { "未分类" }, material.statusName.orEmpty().ifBlank { "状态 ${material.status}" }).joinToString(" · "), color = LibrarySecondary, fontSize = 12.sp, maxLines = 1)
                }
                Text("${material.attachmentCount} 附件", color = LibrarySecondary, fontSize = 11.sp)
            }
            Text("附件 ${material.attachmentCount} · 评论 ${material.extCommentCount} · ${material.updatedAt.orEmpty().ifBlank { "暂无更新时间" }}", color = LibrarySecondary, fontSize = 11.sp, maxLines = 1)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LibraryIconButton(Icons.Filled.Edit, "详情", onOpen)
                LibraryIconButton(Icons.Filled.ContentCopy, "复制", onCopy)
                LibraryIconButton(Icons.Filled.Archive, "归档", onArchive)
            }
        }
    }
}

@Composable
private fun LibraryIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Surface(Modifier.clickable(onClick = onClick), RoundedCornerShape(6.dp), color = Color(0xFFE7F2ED)) {
        Row(Modifier.padding(horizontal = 9.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = LibraryGreen, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(4.dp))
            Text(label, color = LibraryGreen, fontSize = 11.sp)
        }
    }
}

/**
 * 页面内联归档确认，避免在悬浮工作区创建 Dialog Window。
 * 测试流程：列表点击“归档”后确认条出现在列表上方，点击“取消”保持素材不变，
 * 点击“归档”执行既有 SCRM 请求并在原页面显示结果。
 */
@Composable
private fun MaterialArchiveConfirmation(
    material: ScrmMomentMaterial,
    onCancel: () -> Unit,
    onConfirm: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        tonalElevation = 1.dp
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("归档素材", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Normal)
            Text(
                "确认归档“${material.displayName}”？归档后将从默认可用素材中移除。",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onCancel) { Text("取消") }
                Button(onClick = onConfirm) { Text("归档") }
            }
        }
    }
}

@Composable
private fun MaterialDetailPage(
    detail: ScrmMomentMaterialDetail,
    onBack: () -> Unit,
    onEdit: () -> Unit
) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        // UI：详情页沿用 UI组件 的共享 M3 toolbar，30dp 顶部安全区由 AppBar windowInsets 统一承担。
        // 测试流程：进入素材详情后点击左上返回回到列表，点击右上编辑进入编辑工作区。
        FloatingWorkspaceTopAppBar(
            title = "素材详情",
            onBack = onBack,
            actions = {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Filled.Edit, contentDescription = "编辑")
                }
            }
        )
        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Surface(Modifier.fillMaxWidth(), RoundedCornerShape(12.dp), color = LibraryCard) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(48.dp).background(Color(0xFFE7F2ED), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Filled.Image, null, tint = LibraryGreen) }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(detail.displayName, color = LibraryPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                Text("附件 ${detail.attachmentCount} · 评论 ${detail.extCommentCount}", color = LibrarySecondary, fontSize = 12.sp)
                            }
                        }
                        Text(detail.template?.content.orEmpty().ifBlank { "接口未返回素材文案" }, color = LibraryPrimary, fontSize = 15.sp)
                    }
                }
            }
            item { MaterialDetailSection("素材信息", listOf("分类" to detail.category.orEmpty().ifBlank { "未分类" }, "状态" to detail.statusName.orEmpty().ifBlank { "状态 ${detail.status}" }, "创建时间" to detail.createdAt.orEmpty().ifBlank { "未返回" }, "更新时间" to detail.updatedAt.orEmpty().ifBlank { "未返回" })) }
            item { MaterialDetailSection("发布设置", listOf("延迟发送" to if (detail.sendSlow) "已开启" else "未开启", "变量模板" to if (detail.variableSchemaJson.isNullOrBlank()) "未配置" else "已配置", "防折叠策略" to if (detail.antiFoldStrategyJson.isNullOrBlank()) "未配置" else "已配置")) }
        }
    }
}

@Composable
private fun MaterialDetailSection(title: String, rows: List<Pair<String, String>>) {
    Column(Modifier.fillMaxWidth()) {
        Text(title, Modifier.padding(start = 4.dp, bottom = 6.dp), color = LibrarySecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Surface(Modifier.fillMaxWidth(), RoundedCornerShape(10.dp), color = LibraryCard) {
            Column(Modifier.padding(horizontal = 15.dp)) {
                rows.forEachIndexed { index, (label, value) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(label, Modifier.weight(0.38f), color = LibrarySecondary, fontSize = 13.sp)
                        Text(value, Modifier.weight(0.62f), color = LibraryPrimary, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    if (index < rows.lastIndex) Spacer(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFE7E7EA)))
                }
            }
        }
    }
}

@Composable
private fun MaterialEditorPage(
    mode: MaterialEditorMode,
    draft: MaterialDraft,
    saving: Boolean,
    onChange: (MaterialDraft) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        // UI：编辑页与详情页共享同一 M3 toolbar，避免二级页面再次创建独立状态栏占位。
        // 测试流程：输入正文后点击右上保存，保存失败时保持编辑页并显示错误状态。
        FloatingWorkspaceTopAppBar(
            title = if (mode == MaterialEditorMode.Create) "新建素材" else "编辑素材",
            onBack = {
                if (!saving) onDismiss()
            },
            actions = {
                Button(onClick = onSave, enabled = !saving && draft.content.isNotBlank()) {
                    Text(if (saving) "保存中" else "保存")
                }
            }
        )
        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Surface(Modifier.fillMaxWidth(), RoundedCornerShape(10.dp), color = LibraryCard) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Text("基本信息", color = LibrarySecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = { onChange(draft.copy(name = it)) },
                    label = { Text("素材名称") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = draft.category,
                    onValueChange = { onChange(draft.copy(category = it)) },
                    label = { Text("分类") },
                    singleLine = true
                )
                    }
                }
            }
            item {
                Surface(Modifier.fillMaxWidth(), RoundedCornerShape(10.dp), color = LibraryCard) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Text("素材内容", color = LibrarySecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = draft.content,
                    onValueChange = { onChange(draft.copy(content = it)) },
                    label = { Text("素材文案") },
                    minLines = 4
                )
                    }
                }
            }
        }
    }
}
