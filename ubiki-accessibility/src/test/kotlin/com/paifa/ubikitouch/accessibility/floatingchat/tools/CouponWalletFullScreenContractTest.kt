package com.paifa.ubikitouch.accessibility.floatingchat.tools

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CouponWalletFullScreenContractTest {
    /** 测试流程：点击右侧“微信卡券”，应留在悬浮根视图而不是启动外部 Activity。 */
    @Test
    fun couponWalletUsesDedicatedOverlayWorkspace() {
        val workspace = sourceFile("CouponWalletFullScreen.kt")
        val catalog = sourceFile("RightRailToolCatalog.kt")
        val rail = sourceFile("RightCoordinateRail.kt")

        assertTrue("卡券全屏工作区源码必须存在", workspace.isFile)
        assertFalse(catalog.readText().contains("opensCouponWallet = true"))
        assertFalse(rail.readText().contains("FloatingChatCouponWalletBridge.open"))
    }

    /** 测试流程：核对 M3 Tab/Pager、列表、状态栏与属性动画，且不允许 Dialog/Window。 */
    @Test
    fun couponWalletWorkspaceUsesRequiredMaterial3BuildingBlocks() {
        val source = sourceFile("CouponWalletFullScreen.kt").readText()

        assertTrue(source.contains("PrimaryTabRow"))
        assertTrue(source.contains("HorizontalPager"))
        assertTrue(source.contains("LazyColumn"))
        assertTrue(source.contains("height(30.dp)"))
        assertTrue(source.contains("Animatable"))
        assertTrue(source.contains("translationY"))
        assertFalse(source.contains("Dialog("))
        assertFalse(source.contains("WindowManager"))
    }

    private fun sourceFile(fileName: String): File = File(
        System.getProperty("user.dir"),
        "src/main/kotlin/com/paifa/ubikitouch/accessibility/floatingchat/tools/$fileName"
    )
}
