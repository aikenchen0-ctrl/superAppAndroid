package com.paifa.ubikitouch.accessibility.floatingchat.tools

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.emoji2.emojipicker.EmojiPickerView
import com.paifa.ubikitouch.accessibility.floatingchat.components.TextLabel
import com.paifa.ubikitouch.accessibility.floatingchat.input.BottomEmojiPanelHeightDp
import com.paifa.ubikitouch.accessibility.floatingchat.theme.OverlayTokens
private data class PanelTool(
    val icon: String,
    val label: String,
    val action: PanelToolAction = PanelToolAction.Close
)

private enum class PanelToolAction {
    Close,
    AiVoice
}


@Composable
internal fun MoreToolPanel(onClose: () -> Unit, onAiVoiceClick: () -> Unit) {
    val tools = remember {
        listOf(
            PanelTool("Camera", "鐩稿唽"),
            PanelTool("Video", "视频通话"),
            PanelTool("Call", "语音通话"),
            PanelTool("AI", "AI语音", PanelToolAction.AiVoice),
            PanelTool("Pin", "浣嶇疆"),
            PanelTool("Red", "红包"),
            PanelTool("Gift", "绀肩墿"),
            PanelTool("Pay", "杞处"),
            PanelTool("Star", "鏀惰棌"),
            PanelTool("Sign", "绛剧害"),
            PanelTool("Card", "鍚嶇墖"),
            PanelTool("File", "鏂囦欢"),
            PanelTool("Mat", "素材")
        )
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        tools.chunked(4).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                rowItems.forEach { tool ->
                    PanelToolButton(
                        tool = tool,
                        onClick = {
                            when (tool.action) {
                                PanelToolAction.Close -> onClose()
                                PanelToolAction.AiVoice -> onAiVoiceClick()
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
internal fun EmojiPanel(onInsertText: (String) -> Unit) {
    AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .height(BottomEmojiPanelHeightDp.dp),
        factory = { context ->
            EmojiPickerView(context).apply {
                setOnEmojiPickedListener { item ->
                    onInsertText(item.emoji)
                }
            }
        }
    )
}

@Composable
internal fun GiftPanel(onClose: () -> Unit) {
    val gifts = remember { listOf("Coffee", "Flower", "Star", "Cake", "Badge", "Thanks") }
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        TextLabel(
            text = "礼物选择",
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
                rowItems.forEach { gift ->
                    SmallChoiceButton(label = gift, onClick = onClose)
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
        TextLabel(
            text = title,
            size = 11.sp,
            weight = FontWeight.SemiBold,
            color = OverlayTokens.primaryText,
            maxLines = 1
        )
        TextLabel(
            text = message,
            size = 10.sp,
            color = OverlayTokens.panelSecondaryText,
            maxLines = 2
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            SmallChoiceButton(label = "Close", onClick = onClose)
        }
    }
}

@Composable
private fun PanelToolButton(tool: PanelTool, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .widthIn(min = 42.dp, max = 54.dp)
            .height(58.dp),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = OverlayTokens.primaryText
        ),
        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 3.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(31.dp)
                    .clip(CircleShape)
                    .background(OverlayTokens.panelIcon)
                    .border(1.dp, OverlayTokens.hairline, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                TextLabel(
                    text = tool.icon,
                    size = 8.sp,
                    weight = FontWeight.Bold,
                    color = OverlayTokens.panelPrimaryText,
                    maxLines = 1,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            TextLabel(
                text = tool.label,
                size = 9.sp,
                color = OverlayTokens.panelSecondaryText,
                maxLines = 1,
                textAlign = TextAlign.Center
            )
        }
    }
}
