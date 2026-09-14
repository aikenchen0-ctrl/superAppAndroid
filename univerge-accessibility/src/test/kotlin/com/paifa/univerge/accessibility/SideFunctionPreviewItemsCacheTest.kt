package com.paifa.univerge.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Test

class SideFunctionPreviewItemsCacheTest {
    @Test
    fun replacesTheSnapshotOnlyWhenConfigurationIsRefreshed() {
        val cache = SideFunctionPreviewItemsCache()
        val source = mutableListOf(SideFunctionPanelItem("返回"))

        cache.replace(source)
        val first = cache.snapshot()
        source += SideFunctionPanelItem("主页")

        assertEquals(listOf(SideFunctionPanelItem("返回")), first)
        assertEquals(first, cache.snapshot())

        cache.replace(source)
        assertNotSame(first, cache.snapshot())
        assertEquals(source, cache.snapshot())
    }
}
