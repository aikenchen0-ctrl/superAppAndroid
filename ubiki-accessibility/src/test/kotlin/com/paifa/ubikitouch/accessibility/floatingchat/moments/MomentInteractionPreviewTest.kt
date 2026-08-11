package com.paifa.ubikitouch.accessibility.floatingchat.moments

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MomentInteractionPreviewTest {
    @Test
    fun unreadPreviewDoesNotInventCountWhenNoServerDataIsAvailable() {
        val preview = MomentInteractionPreviewDraft().toPreview()

        assertEquals("读取未读状态", preview.actionLabel)
        assertEquals("未读数量：待接口读取", preview.unreadSummary)
        assertEquals("当前微信账号", preview.targetSummary)
        assertEquals("仅生成预览，未读取、未修改", preview.executionStatement)
    }

    @Test
    fun markReadPreviewNamesImpactAndRequiresConfirmation() {
        val preview = MomentInteractionPreviewDraft(
            action = MomentInteractionAction.MarkAllRead,
            targetSummary = "销售一组",
            unreadCount = 12
        ).toPreview()

        assertEquals("标记全部已读", preview.actionLabel)
        assertEquals("未读数量：12", preview.unreadSummary)
        assertEquals("销售一组", preview.targetSummary)
        assertEquals("将 12 条互动标记为已读", preview.impactSummary)
        assertTrue(preview.requiresConfirmation)
        assertEquals("待确认，未执行", preview.executionStatement)
    }
}
