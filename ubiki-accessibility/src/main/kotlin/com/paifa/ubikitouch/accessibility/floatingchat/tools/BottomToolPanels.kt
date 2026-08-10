package com.paifa.ubikitouch.accessibility.floatingchat.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Textsms
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
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
import com.paifa.ubikitouch.accessibility.floatingchat.components.TextLabel
import com.paifa.ubikitouch.accessibility.floatingchat.input.BottomEmojiPanelHeightDp
import com.paifa.ubikitouch.accessibility.floatingchat.shell.BottomPanelMode
import com.paifa.ubikitouch.accessibility.floatingchat.theme.OverlayTokens

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
    val tools = remember {
        listOf(
            PanelTool(Icons.Filled.Mic, "\u8bed\u97f3\u8f93\u5165", BottomPanelMode.Voice),
            PanelTool(Icons.Filled.SmartToy, "AI\u8bed\u97f3", opensAiVoice = true),
            PanelTool(Icons.Filled.Textsms, "\u5feb\u6377\u8bdd\u672f", BottomPanelMode.QuickPhrase),
            PanelTool(Icons.Filled.LocationOn, "\u4f4d\u7f6e", BottomPanelMode.Location),
            PanelTool(Icons.Filled.CardGiftcard, "\u7ea2\u5305", BottomPanelMode.RedPacket),
            PanelTool(Icons.Filled.AttachMoney, "\u8f6c\u8d26", BottomPanelMode.Transfer),
            PanelTool(Icons.Filled.CardGiftcard, "\u793c\u7269", BottomPanelMode.Gift),
            PanelTool(Icons.Filled.Star, "\u6536\u85cf", BottomPanelMode.Favorite),
            PanelTool(Icons.Filled.Star, "\u6536\u85cf\u8868\u60c5", BottomPanelMode.ScrmEmoji),
            PanelTool(Icons.Filled.CardGiftcard, "\u5c0f\u7a0b\u5e8f\u5361\u7247", BottomPanelMode.ScrmWeAppCard),
            PanelTool(Icons.Filled.Contacts, "\u5361\u7247\u6a21\u677f", BottomPanelMode.ScrmCardTemplates),
            PanelTool(Icons.Filled.Textsms, "\u6279\u91cf\u53d1\u9001", BottomPanelMode.ScrmBatchSend),
            PanelTool(Icons.Filled.Contacts, "\u540d\u7247", BottomPanelMode.Card),
            PanelTool(Icons.Filled.Collections, "\u670b\u53cb\u5708", BottomPanelMode.Moments),
            PanelTool(Icons.Filled.Collections, "\u7d20\u6750", BottomPanelMode.MomentMaterials),
            PanelTool(Icons.Filled.Textsms, "\u5e38\u7528\u8bed", BottomPanelMode.QuickPhrase)
        )
    }
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

@Composable
internal fun EmojiPanel(onInsertText: (String) -> Unit) {
    var selectedCategoryIndex by remember { mutableIntStateOf(0) }
    val selectedCategory = EmojiCategories[selectedCategoryIndex]

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

        selectedCategory.emojis.chunked(8).forEach { rowItems ->
            Row(modifier = Modifier.fillMaxWidth()) {
                rowItems.forEach { emoji ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
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

@Composable
internal fun GiftPanel(onClose: () -> Unit) {
    val gifts = remember { listOf("Coffee", "Flower", "Star", "Cake", "Badge", "Thanks") }
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        TextLabel(
            text = "\u793c\u7269\u9009\u62e9",
            size = 10.sp,
            weight = FontWeight.SemiBold,
            color = OverlayTokens.panelSecondaryText,
            maxLines = 1
        )
        gifts.chunked(3).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                rowItems.forEach { gift -> SmallChoiceButton(label = gift, onClick = onClose) }
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
