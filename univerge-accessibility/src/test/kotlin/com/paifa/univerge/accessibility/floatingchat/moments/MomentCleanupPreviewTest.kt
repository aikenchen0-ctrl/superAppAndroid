package com.paifa.univerge.accessibility.floatingchat.moments

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MomentCleanupPreviewTest {
    @Test
    fun deletePreviewRequiresMomentIdAndExplicitConfirmationPhrase() {
        val preview = MomentCleanupPreviewDraft(
            action = MomentCleanupAction.DeleteMoment,
            targetSummary = "当前微信账号",
            confirmationText = "删除"
        ).toPreview()

        assertEquals("删除朋友圈动态", preview.actionLabel)
        assertEquals("动态 ID：未填写", preview.targetItemSummary)
        assertFalse(preview.canGeneratePreview)
        assertEquals("请输入“删除动态”确认", preview.confirmationHint)
    }

    @Test
    fun confirmedDeletePreviewNamesTheTargetAndImpact() {
        val preview = MomentCleanupPreviewDraft(
            action = MomentCleanupAction.DeleteMoment,
            momentId = "circle-1001",
            targetSummary = "销售一组",
            affectedCount = 1,
            confirmationText = "删除动态"
        ).toPreview()

        assertEquals("动态 ID：circle-1001", preview.targetItemSummary)
        assertEquals("影响动态数：1", preview.impactSummary)
        assertTrue(preview.canGeneratePreview)
        assertEquals("仅生成删除预览，未删除", preview.executionStatement)
    }
}
