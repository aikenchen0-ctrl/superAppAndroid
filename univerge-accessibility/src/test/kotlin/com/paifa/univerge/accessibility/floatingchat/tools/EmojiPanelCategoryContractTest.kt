package com.paifa.univerge.accessibility.floatingchat.tools

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmojiPanelCategoryContractTest {

    @Test
    fun emojiPanelKeepsOnlyTheFirstFourUsefulCategories() {
        val relativePath = "src/main/kotlin/com/paifa/univerge/accessibility/floatingchat/tools/BottomToolPanels.kt"
        val sourceFile = listOf(
            File(relativePath),
            File("univerge-accessibility", relativePath)
        ).firstOrNull(File::exists)
            ?: error("Cannot locate BottomToolPanels.kt")
        val source = sourceFile.readText()
        val categoryBlock = source.substringAfter("private val EmojiCategories = listOf(")
            .substringBefore("@Composable\ninternal fun GiftPanel")

        assertEquals(4, Regex("EmojiCategory\\(").findAll(categoryBlock).count())
        val labels = listOf("表情", "人物", "自然", "食物")
        assertTrue(labels.zipWithNext().all { (first, second) ->
            categoryBlock.indexOf(first) in 0 until categoryBlock.indexOf(second)
        })
    }

    @Test
    fun emojiPanelUsesIndependentLazyScrollingGridForEachCategory() {
        val relativePath = "src/main/kotlin/com/paifa/univerge/accessibility/floatingchat/tools/BottomToolPanels.kt"
        val sourceFile = listOf(
            File(relativePath),
            File("univerge-accessibility", relativePath)
        ).firstOrNull(File::exists)
            ?: error("Cannot locate BottomToolPanels.kt")
        val source = sourceFile.readText()

        assertTrue(source.contains("LazyVerticalGrid"))
        assertTrue(source.contains("GridCells.Fixed(8)"))
        assertTrue(source.contains("rememberLazyGridState"))
        assertTrue(source.contains("Modifier.weight(1f)"))
        assertTrue(source.contains("key = { index, _ -> \"\$selectedCategoryIndex-\$index\" }"))
    }
}
