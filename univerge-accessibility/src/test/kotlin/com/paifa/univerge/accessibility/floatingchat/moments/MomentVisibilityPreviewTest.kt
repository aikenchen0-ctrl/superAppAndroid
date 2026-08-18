package com.paifa.univerge.accessibility.floatingchat.moments

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MomentVisibilityPreviewTest {
    @Test
    fun previewUsesAllFriendsDefaultWithoutInventingAffectedCount() {
        val preview = MomentVisibilityPreviewDraft().toPreview()

        assertEquals("所有朋友可见", preview.visibilityLabel)
        assertEquals("当前微信账号", preview.targetSummary)
        assertEquals("影响账号数：待接口计算", preview.affectedSummary)
        assertEquals("不置顶", preview.stickyLabel)
        assertEquals("待确认，未执行", preview.executionStatement)
    }

    @Test
    fun selectedAudienceAndStickyPreviewStateAllScopeDetails() {
        val preview = MomentVisibilityPreviewDraft(
            visibility = MomentVisibilityScope.OnlySelectedFriends,
            targetSummary = "销售客户标签",
            affectedAccountCount = 36,
            sticky = true
        ).toPreview()

        assertEquals("仅指定朋友可见", preview.visibilityLabel)
        assertEquals("销售客户标签", preview.targetSummary)
        assertEquals("影响账号数：36", preview.affectedSummary)
        assertEquals("置顶", preview.stickyLabel)
        assertTrue(preview.requiresConfirmation)
    }
}
