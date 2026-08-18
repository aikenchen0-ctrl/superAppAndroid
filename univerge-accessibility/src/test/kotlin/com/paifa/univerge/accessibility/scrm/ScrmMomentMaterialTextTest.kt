package com.paifa.univerge.accessibility.scrm

import org.junit.Assert.assertEquals
import org.junit.Test

class ScrmMomentMaterialTextTest {
    @Test
    fun materialDisplayNameRepairsLegacyMojibake() {
        val material = ScrmMomentMaterial(id = 7L, name = "鍥剧墖")

        assertEquals("图片", material.displayName)
    }

    @Test
    fun normalMaterialDisplayNameIsUnchanged() {
        val material = ScrmMomentMaterial(id = 8L, name = "夏季活动素材")

        assertEquals("夏季活动素材", material.displayName)
    }

    @Test
    fun materialDetailDisplayNameRepairsLegacyMojibake() {
        val detail = ScrmMomentMaterialDetail(id = 9L, name = "鍥剧墖")

        assertEquals("图片", detail.displayName)
    }
}
