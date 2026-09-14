package com.paifa.univerge.accessibility.floatingchat.message

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.paifa.univerge.accessibility.floatingchat.theme.OverlayTokens
import com.paifa.univerge.core.model.FloatingChatMessage
import com.paifa.univerge.heavydrag.compose.LocalHeavyDragCoordinator
import com.paifa.univerge.heavydrag.compose.LocalHeavyDragSession
import com.paifa.univerge.heavydrag.compose.heavyDropTarget
import com.paifa.univerge.heavydrag.core.HeavyDragState
import com.paifa.univerge.heavydrag.core.HeavyTargetMode

/** The two test drop destinations for a plain text message. */
internal enum class HeavyTextDropTargetKind(
    val id: String,
    val label: String
) {
    AiKnowledgeBase("ai_knowledge_base", "AI 知识库"),
    IntentTaskGenerator("intent_task_generator", "理解意图生成任务")
}

/** A UI-independent drop event for the host application or a future API adapter. */
internal data class HeavyTextDropEvent(
    val target: HeavyTextDropTargetKind,
    val message: FloatingChatMessage
)

internal fun heavyTextDropTargetKinds(): List<HeavyTextDropTargetKind> {
    return HeavyTextDropTargetKind.entries
}

internal fun heavyTextDropTargetAccepts(payload: Any?): Boolean {
    return (payload as? FloatingChatMessage)?.let(::messageUsesTestHeavyDrag) == true
}

/**
 * Keeps both target bounds registered while hidden, so a drag can be classified
 * and committed without depending on a recomposition race at drag start.
 */
@Composable
internal fun HeavyTextDropTargetsOverlay(
    onDropToAiKnowledgeBase: (FloatingChatMessage) -> Unit = {},
    onDropToIntentTaskGenerator: (FloatingChatMessage) -> Unit = {},
    feedback: String? = null,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val coordinator = LocalHeavyDragCoordinator.current ?: return
    val session = LocalHeavyDragSession.current
    val dragging = session?.state == HeavyDragState.DRAGGING
    val visible = enabled && (dragging || feedback != null)
    val visibilityAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        label = "heavy-text-drop-target-alpha"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            // Alpha is intentionally visual-only; the targets remain laid out
            // and registered while hidden so the coordinator has stable bounds.
            .alpha(visibilityAlpha)
            .padding(horizontal = 14.dp, vertical = 18.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            feedback?.let { message ->
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xE61B2428)
                ) {
                    Text(
                        text = message,
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                HeavyTextDropTargetCard(
                    kind = HeavyTextDropTargetKind.AiKnowledgeBase,
                    coordinator = coordinator,
                    enabled = enabled,
                    onDrop = onDropToAiKnowledgeBase,
                    modifier = Modifier.weight(1f)
                )
                HeavyTextDropTargetCard(
                    kind = HeavyTextDropTargetKind.IntentTaskGenerator,
                    coordinator = coordinator,
                    enabled = enabled,
                    onDrop = onDropToIntentTaskGenerator,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun HeavyTextDropTargetCard(
    kind: HeavyTextDropTargetKind,
    coordinator: com.paifa.univerge.heavydrag.core.HeavyDragCoordinator,
    enabled: Boolean,
    onDrop: (FloatingChatMessage) -> Unit,
    modifier: Modifier = Modifier
) {
    var hovered by remember(kind) { mutableStateOf(false) }
    val icon = when (kind) {
        HeavyTextDropTargetKind.AiKnowledgeBase -> Icons.AutoMirrored.Filled.MenuBook
        HeavyTextDropTargetKind.IntentTaskGenerator -> Icons.Filled.Checklist
    }
    Surface(
        modifier = modifier
            .height(72.dp)
            .semantics { contentDescription = kind.label }
            .heavyDropTarget(
                coordinator = coordinator,
                targetId = kind.id,
                mode = HeavyTargetMode.DROP_ZONE,
                enabled = enabled,
                accepts = ::heavyTextDropTargetAccepts,
                onEnter = { hovered = true },
                onOver = { hovered = true },
                onExit = { hovered = false },
                onDrop = { event ->
                    hovered = false
                    (event.payload as? FloatingChatMessage)?.let(onDrop)
                }
            ),
        shape = RoundedCornerShape(8.dp),
        color = if (hovered) {
            OverlayTokens.aiBorder.copy(alpha = 0.92f)
        } else {
            Color(0xE62A353A)
        },
        contentColor = Color.White,
        shadowElevation = if (hovered) 5.dp else 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = kind.label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 2
            )
        }
    }
}
