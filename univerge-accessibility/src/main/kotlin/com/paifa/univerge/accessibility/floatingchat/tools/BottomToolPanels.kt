package com.paifa.univerge.accessibility.floatingchat.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VideoCall
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paifa.univerge.accessibility.floatingchat.components.FloatingWorkspaceTopAppBar
import com.paifa.univerge.accessibility.floatingchat.components.TextLabel
import com.paifa.univerge.accessibility.floatingchat.input.BottomEmojiPanelHeightDp
import com.paifa.univerge.accessibility.floatingchat.shell.BottomPanelMode
import com.paifa.univerge.accessibility.floatingchat.theme.OverlayTokens

private data class PanelTool(
    val icon: ImageVector,
    val label: String,
    val panelMode: BottomPanelMode? = null,
    val opensAiVoice: Boolean = false
)

@Composable
internal fun MoreToolPanel(
    onOpenPanel: (BottomPanelMode) -> Unit,
    onAiVoiceClick: () -> Unit
) {
    val tools = remember { morePanelTools() }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        tools.chunked(4).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                rowItems.forEach { tool ->
                    PanelToolButton(tool = tool) {
                        if (tool.opensAiVoice) onAiVoiceClick()
                        else tool.panelMode?.let(onOpenPanel)
                    }
                }
            }
        }
    }
}

/**
 * BottomNav 的“+”菜单只保留聊天发送场景需要的 12 个入口。
 * 每项均由 [PanelToolButton] 统一渲染为 M3 图标 + 下方文字，点击后复用现有
 * BottomPanelMode 路由；“签约”暂由现有 SCRM 运营工作区承载，避免新增悬浮窗口和
 * BadToken 风险，后续接入正式签约接口时只需替换该目的地。
 */
private fun morePanelTools(): List<PanelTool> = listOf(
    PanelTool(Icons.Filled.Image, "相册", BottomPanelMode.Gallery),
    PanelTool(Icons.Filled.VideoCall, "\u89c6\u9891\u901a\u8bdd", BottomPanelMode.VideoCall),
    PanelTool(Icons.Filled.Call, "\u8bed\u97f3\u901a\u8bdd", BottomPanelMode.VoiceCall),
    PanelTool(Icons.Filled.LocationOn, "\u5b9a\u4f4d", BottomPanelMode.Location),
    PanelTool(Icons.Filled.CardGiftcard, "\u7ea2\u5305", BottomPanelMode.RedPacket),
    PanelTool(Icons.Filled.CardGiftcard, "\u793c\u7269", BottomPanelMode.Gift),
    PanelTool(Icons.Filled.AttachMoney, "\u8f6c\u8d26", BottomPanelMode.Transfer),
    PanelTool(Icons.Filled.Star, "\u6536\u85cf", BottomPanelMode.Favorite),
    PanelTool(Icons.Filled.ManageAccounts, "\u7b7e\u7ea6", BottomPanelMode.ScrmOperations),
    PanelTool(Icons.Filled.Contacts, "\u540d\u7247", BottomPanelMode.Card),
    PanelTool(Icons.Filled.Description, "\u6587\u4ef6", BottomPanelMode.FileDocument),
    PanelTool(Icons.Filled.Collections, "\u7d20\u6750", BottomPanelMode.MomentMaterials)
)

/** Visible labels in the BottomNav “+” menu, kept as a small UI contract for tests. */
internal fun morePanelToolLabels(): List<String> = morePanelTools().map { it.label }

/** Destination modes used by the BottomNav “+” menu, in visual order. */
internal fun morePanelToolModes(): List<BottomPanelMode> = morePanelTools().mapNotNull { it.panelMode }

internal fun moreToolPanelUsesUniqueDestinations(): Boolean {
    val destinations = morePanelTools().mapNotNull { it.panelMode }
    return destinations.size == destinations.toSet().size
}

@Composable
internal fun EmojiPanel(onInsertText: (String) -> Unit) {
    var selectedCategoryIndex by remember { mutableIntStateOf(0) }
    val selectedCategory = EmojiCategories[selectedCategoryIndex]
    val categoryGridStates = listOf(
        rememberLazyGridState(),
        rememberLazyGridState(),
        rememberLazyGridState(),
        rememberLazyGridState()
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(BottomEmojiPanelHeightDp.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            EmojiCategories.forEachIndexed { index, category ->
                val selected = index == selectedCategoryIndex
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(32.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (selected) OverlayTokens.panelIcon else Color.Transparent
                        )
                        .selectable(
                            selected = selected,
                            role = Role.Tab,
                            onClick = { selectedCategoryIndex = index }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    TextLabel(
                        text = category.label,
                        size = 11.sp,
                        weight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) {
                            OverlayTokens.primaryText
                        } else {
                            OverlayTokens.panelSecondaryText
                        },
                        maxLines = 1
                    )
                }
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(8),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            state = categoryGridStates[selectedCategoryIndex]
        ) {
            itemsIndexed(
                items = selectedCategory.emojis,
                key = { index, _ -> "$selectedCategoryIndex-$index" }
            ) { _, emoji ->
                Box(
                    modifier = Modifier
                        .height(40.dp)
                        .clickable { onInsertText(emoji) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = emoji, fontSize = 22.sp)
                }
            }
        }
    }
}

private data class EmojiCategory(
    val label: String,
    val emojis: List<String>
)

private val EmojiCategories = listOf(
    EmojiCategory(
        label = "表情",
        emojis = listOf(
            "😀", "😄", "😁", "😆", "😅", "😂", "🤣", "😊",
            "😇", "🙂", "🙃", "😉", "😌", "😍", "🥰", "😘",
            "😋", "😜", "🤪", "🤨", "🧐", "🤓", "😎", "🤩",
            "🥳", "😏", "😒", "😞", "😔", "😟", "😕", "🙁",
            "😣", "😖", "😫", "😩", "🥺", "😢", "😭", "😤",
            "😠", "😡", "🤬", "🤯", "😳", "🥵", "🥶", "😱"
        )
    ),
    EmojiCategory(
        label = "人物",
        emojis = listOf(
            "👋", "🤚", "🖐️", "✋", "🖖", "👌", "🤌", "🤏",
            "✌️", "🤞", "🤟", "🤘", "🤙", "👈", "👉", "👆",
            "👇", "☝️", "👍", "👎", "✊", "👊", "🤛", "🤜",
            "👏", "🙌", "👐", "🤲", "🤝", "🙏", "✍️", "💅",
            "🤳", "💪", "🦾", "🦵", "🦶", "👂", "👃", "🧠",
            "👀", "👁️", "👄", "🧑", "👩", "👨", "👧", "👦"
        )
    ),
    EmojiCategory(
        label = "自然",
        emojis = listOf(
            "🐶", "🐱", "🐭", "🐹", "🐰", "🦊", "🐻", "🐼",
            "🐨", "🐯", "🦁", "🐮", "🐷", "🐸", "🐵", "🐔",
            "🐧", "🐦", "🐤", "🦆", "🦅", "🦉", "🦋", "🐝",
            "🐞", "🐢", "🐍", "🦎", "🐙", "🦀", "🐬", "🐳",
            "🌵", "🎄", "🌲", "🌳", "🌴", "🌱", "🌿", "🍀",
            "🌹", "🌸", "🌻", "🌞", "🌙", "⭐", "🌈", "🔥"
        )
    ),
    EmojiCategory(
        label = "食物",
        emojis = listOf(
            "🍏", "🍎", "🍐", "🍊", "🍋", "🍌", "🍉", "🍇",
            "🍓", "🫐", "🍈", "🍒", "🍑", "🥭", "🍍", "🥥",
            "🥝", "🍅", "🥑", "🍆", "🥕", "🌽", "🌶️", "🥒",
            "🥬", "🥦", "🧄", "🧅", "🍄", "🥜", "🌰", "🍞",
            "🥐", "🥖", "🥨", "🧀", "🥚", "🍳", "🥞", "🥓",
            "🍔", "🍟", "🍕", "🌭", "🥪", "🌮", "🍜", "🍰"
        )
    )
)

/**
 * UI：More 菜单的礼物页使用共享的全屏悬浮工作区和 M3 列表，避免取消式小面板。
 * 回调：保留原有礼物选择后关闭面板的行为，不新增接口或改变会话状态。
 * 测试流程：从 More 打开礼物，确认顶部安全区归属工具栏；点选任一礼物或左上返回，确认回到原会话。
 */
@Composable
internal fun GiftPanel(onClose: () -> Unit) {
    val gifts = remember { listOf("Coffee", "Flower", "Star", "Cake", "Badge", "Thanks") }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        FloatingWorkspaceTopAppBar(title = "\u793c\u7269", onBack = onClose)
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = "\u9009\u62e9\u793c\u7269",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleMedium
                )
            }
            items(gifts, key = { it }) { gift ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onClose)
                ) {
                    ListItem(
                        headlineContent = { Text(gift) },
                        supportingContent = { Text("\u9009\u62e9\u540e\u8fd4\u56de\u5f53\u524d\u4f1a\u8bdd") },
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Filled.CardGiftcard,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
internal fun CompactNoticePanel(title: String, message: String, onClose: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TextLabel(text = title, size = 11.sp, weight = FontWeight.SemiBold, color = OverlayTokens.primaryText, maxLines = 1)
        TextLabel(text = message, size = 10.sp, color = OverlayTokens.panelSecondaryText, maxLines = 2)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            SmallChoiceButton(label = "Close", onClick = onClose)
        }
    }
}

@Composable
private fun PanelToolButton(tool: PanelTool, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.widthIn(min = 54.dp, max = 70.dp).height(66.dp),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = OverlayTokens.primaryText),
        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 3.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(OverlayTokens.panelIcon)
                    .border(1.dp, OverlayTokens.hairline, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = tool.icon,
                    contentDescription = tool.label,
                    tint = OverlayTokens.panelPrimaryText,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            TextLabel(
                text = tool.label,
                size = 10.sp,
                color = OverlayTokens.panelSecondaryText,
                maxLines = 1,
                textAlign = TextAlign.Center
            )
        }
    }
}
