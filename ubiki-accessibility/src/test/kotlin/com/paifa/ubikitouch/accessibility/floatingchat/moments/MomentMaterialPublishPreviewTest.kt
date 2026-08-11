package com.paifa.ubikitouch.accessibility.floatingchat.moments

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MomentMaterialPublishPreviewTest {
    @Test
    fun previewRequiresMaterialIdBeforeItCanBeGenerated() {
        val preview = MomentMaterialPublishPreviewDraft().toPreview()

        assertEquals("素材 ID：未选择", preview.materialSummary)
        assertEquals("当前微信账号", preview.targetSummary)
        assertFalse(preview.canGeneratePreview)
        assertEquals("请先填写素材 ID", preview.validationMessage)
    }

    @Test
    fun previewRetainsSelectedMaterialScopeAndSchedule() {
        val preview = MomentMaterialPublishPreviewDraft(
            materialId = "material-1001",
            contentSummary = "新品海报与活动文案",
            targetSummary = "销售一组",
            scheduledAt = "2026-08-12 10:00"
        ).toPreview()

        assertEquals("素材 ID：material-1001", preview.materialSummary)
        assertEquals("新品海报与活动文案", preview.contentSummary)
        assertEquals("销售一组", preview.targetSummary)
        assertEquals("2026-08-12 10:00", preview.scheduledAt)
        assertTrue(preview.canGeneratePreview)
        assertEquals("仅生成素材发布预览，未复制、未发布", preview.executionStatement)
    }
}
