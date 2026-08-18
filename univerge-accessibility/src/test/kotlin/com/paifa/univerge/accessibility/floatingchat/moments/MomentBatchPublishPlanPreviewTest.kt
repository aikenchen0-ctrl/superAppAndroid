package com.paifa.univerge.accessibility.floatingchat.moments

import org.junit.Assert.assertEquals
import org.junit.Test

class MomentBatchPublishPlanPreviewTest {
    @Test
    fun previewUsesExplicitDefaultsForBlankDraftFields() {
        val preview = MomentBatchPublishPlanDraft().toPreview()

        assertEquals("朋友圈批量发布草稿", preview.planName)
        assertEquals("未选择素材或文案", preview.contentSummary)
        assertEquals("当前微信账号", preview.targetSummary)
        assertEquals("待选择，未排期", preview.scheduledAt)
        assertEquals("草稿，待确认", preview.statusLabel)
        assertEquals("待接口计算", preview.estimatedImpact)
        assertEquals("仅生成预览，未创建、未执行", preview.executionStatement)
    }

    @Test
    fun previewKeepsOperatorEnteredPlanScopeAndPausedStatus() {
        val preview = MomentBatchPublishPlanDraft(
            planName = "七夕活动第一批",
            contentSummary = "七夕海报和活动文案",
            targetSummary = "销售一组、销售二组",
            scheduledAt = "2026-08-12 10:00",
            status = MomentBatchPlanStatus.Paused,
            estimatedImpactCount = 36
        ).toPreview()

        assertEquals("七夕活动第一批", preview.planName)
        assertEquals("七夕海报和活动文案", preview.contentSummary)
        assertEquals("销售一组、销售二组", preview.targetSummary)
        assertEquals("2026-08-12 10:00", preview.scheduledAt)
        assertEquals("暂停，尚未执行", preview.statusLabel)
        assertEquals("预计影响 36 个账号", preview.estimatedImpact)
    }
}
