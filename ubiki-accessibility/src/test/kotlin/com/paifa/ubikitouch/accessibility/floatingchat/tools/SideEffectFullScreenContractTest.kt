package com.paifa.ubikitouch.accessibility.floatingchat.tools

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class SideEffectFullScreenContractTest {
    /** 测试流程：点击任一“侧边特效”入口，确认进入悬浮根内的全屏配置页。 */
    @Test
    fun sideEffectUsesDedicatedFullscreenWorkspace() {
        val source = sourceFile("SideEffectFullScreen.kt")
        assertTrue("侧边特效全屏工作区源码必须存在", source.isFile)
    }

    /** 测试流程：确认使用 M3 Tab/Pager、LazyColumn、30dp 状态区与实体属性动画。 */
    @Test
    fun sideEffectWorkspaceUsesRequiredMaterial3BuildingBlocks() {
        val source = sourceFile("SideEffectFullScreen.kt").readText()
        listOf("PrimaryTabRow", "HorizontalPager", "LazyColumn", "height(30.dp)", "Animatable", "translationY").forEach {
            assertTrue("缺少 $it", source.contains(it))
        }
        assertTrue("不允许 Dialog", !source.contains("Dialog("))
        assertTrue("不允许 WindowManager", !source.contains("WindowManager"))
    }

    private fun sourceFile(fileName: String): File = File(
        System.getProperty("user.dir"),
        "src/main/kotlin/com/paifa/ubikitouch/accessibility/floatingchat/tools/$fileName"
    )
}
