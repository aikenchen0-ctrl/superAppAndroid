package com.paifa.univerge.accessibility

import com.paifa.univerge.core.model.GestureAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class GesturePreviewLabelCacheTest {
    @Test
    fun resolvesEachActionOnlyWhenTheSnapshotChanges() {
        var resolveCount = 0
        val cache = GesturePreviewLabelCache()
        val action = GestureAction.LaunchApp("com.example.app")

        cache.replace(mapOf(action to "示例应用"))
        val first = cache.label(action) { resolveCount++; "fallback" }
        val second = cache.label(action) { resolveCount++; "fallback" }

        assertEquals("示例应用", first)
        assertSame(first, second)
        assertEquals(0, resolveCount)

        cache.clear()
        assertEquals("fallback", cache.label(action) { resolveCount++; "fallback" })
        assertEquals(1, resolveCount)
    }
}
