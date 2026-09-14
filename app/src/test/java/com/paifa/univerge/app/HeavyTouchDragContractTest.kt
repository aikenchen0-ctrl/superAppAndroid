package com.paifa.univerge.app

import java.nio.file.Files
import java.nio.file.Paths
import org.junit.Assert.assertTrue
import org.junit.Test

class HeavyTouchDragContractTest {
    @Test
    fun touchTestActivityUsesSharedHeavyDragHostAndDeclarativeTargets() {
        val source = String(
            Files.readAllBytes(Paths.get("src/main/java/com/paifa/univerge/app/TouchTestActivity.kt")),
            Charsets.UTF_8
        )
        assertTrue(source.contains("HeavyDragHost"))
        assertTrue(source.contains("heavyDraggable"))
        assertTrue(source.contains("heavyDropTarget"))
        assertTrue(source.contains("heavyOverlapTarget"))
        assertTrue(source.contains("AispectHeavyTouchBridge"))
    }
}
