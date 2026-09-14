package com.paifa.univerge.accessibility.floatingchat.message

import com.paifa.univerge.core.model.FloatingChatMessage
import com.paifa.univerge.core.model.FloatingChatMessagePresentation
import com.paifa.univerge.core.model.FloatingChatMessageType
import com.paifa.univerge.heavydrag.core.HeavyDragCoordinator
import com.paifa.univerge.heavydrag.core.HeavyDragPolicy
import com.paifa.univerge.heavydrag.core.HeavyDragSource
import com.paifa.univerge.heavydrag.core.HeavyDragTarget
import com.paifa.univerge.heavydrag.core.HeavyPoint
import com.paifa.univerge.heavydrag.core.HeavyRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HeavyTextDropTargetsTest {
    @Test
    fun exposesKnowledgeBaseAndIntentTaskTargetsInStableOrder() {
        assertEquals(
            listOf("ai_knowledge_base", "intent_task_generator"),
            heavyTextDropTargetKinds().map { it.id }
        )
        assertEquals(
            listOf("AI 知识库", "理解意图生成任务"),
            heavyTextDropTargetKinds().map { it.label }
        )
    }

    @Test
    fun targetAcceptsOnlyNonSystemPlainTextMessages() {
        val plain = message(FloatingChatMessageType.Text)
        val mixed = message(FloatingChatMessageType.MixedText)
        val system = plain.copy(presentation = FloatingChatMessagePresentation.System)

        assertTrue(heavyTextDropTargetAccepts(plain))
        assertFalse(heavyTextDropTargetAccepts(mixed))
        assertFalse(heavyTextDropTargetAccepts(system))
        assertFalse(heavyTextDropTargetAccepts("plain text"))
        assertFalse(heavyTextDropTargetAccepts(null))
    }

    @Test
    fun heavyTextMessageCommitsToKnowledgeBaseDropZone() {
        val message = message(FloatingChatMessageType.Text)
        var dropped: FloatingChatMessage? = null
        val coordinator = HeavyDragCoordinator(HeavyDragPolicy(minHeavyConfidence = 0f))
        coordinator.registerSource(
            HeavyDragSource(
                id = message.id,
                bounds = HeavyRect(0f, 0f, 40f, 40f),
                payload = message
            )
        )
        coordinator.registerTarget(
            HeavyDragTarget(
                id = HeavyTextDropTargetKind.AiKnowledgeBase.id,
                bounds = HeavyRect(100f, 100f, 180f, 180f),
                accepts = ::heavyTextDropTargetAccepts,
                onDrop = { event -> dropped = event.payload as FloatingChatMessage }
            )
        )

        val gestureId = coordinator.onPointerDown(0, HeavyPoint(10f, 10f), 0L)
        requireNotNull(gestureId)
        assertTrue(coordinator.onClassification(
            com.paifa.univerge.heavydrag.core.HeavyTouchClassification.heavyPress(
                gestureId = gestureId,
                confidence = 1f,
                eventTimeMillis = 0L
            )
        ))
        coordinator.onPointerMove(0, HeavyPoint(120f, 120f), 100L, gestureId)
        coordinator.onPointerUp(0, HeavyPoint(120f, 120f), 120L, gestureId)

        assertEquals(message, dropped)
    }

    private fun message(type: FloatingChatMessageType): FloatingChatMessage {
        return FloatingChatMessage(
            id = "message-1",
            type = type,
            text = "测试文本",
            fromMe = false,
            senderName = "联系人",
            time = "刚刚"
        )
    }
}
