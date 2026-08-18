package com.paifa.univerge.accessibility.floatingchat.moments

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MomentMaterialOperationPreviewTest {
    @Test
    fun archivePreviewRequiresExplicitConfirmation() {
        val preview = MomentMaterialOperationPreviewDraft(
            action = MomentMaterialOperation.Archive,
            materialId = "material-1001",
            materialName = "新品海报"
        ).toPreview()

        assertFalse(preview.canGeneratePreview)
    }

    @Test
    fun copyPreviewCanBeGeneratedWithMaterialIdentity() {
        val preview = MomentMaterialOperationPreviewDraft(
            action = MomentMaterialOperation.Copy,
            materialId = "material-1001",
            materialName = "新品海报"
        ).toPreview()

        assertTrue(preview.canGeneratePreview)
    }
}
