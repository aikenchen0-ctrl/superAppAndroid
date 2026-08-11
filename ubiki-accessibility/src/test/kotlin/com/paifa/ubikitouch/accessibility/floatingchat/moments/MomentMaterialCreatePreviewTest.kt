package com.paifa.ubikitouch.accessibility.floatingchat.moments

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MomentMaterialCreatePreviewTest {
    @Test
    fun emptyContentCannotGenerateCreatePreview() {
        assertFalse(MomentMaterialCreatePreviewDraft(content = " ").toPreview().canGeneratePreview)
    }

    @Test
    fun contentCreatesPreviewWithDefaultsForOptionalFields() {
        assertTrue(MomentMaterialCreatePreviewDraft(content = "新品文案").toPreview().canGeneratePreview)
    }
}
