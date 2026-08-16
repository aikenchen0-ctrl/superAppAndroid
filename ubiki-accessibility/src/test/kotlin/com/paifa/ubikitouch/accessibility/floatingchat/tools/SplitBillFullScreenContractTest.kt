package com.paifa.ubikitouch.accessibility.floatingchat.tools

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SplitBillFullScreenContractTest {
    /** 测试流程：核对页面从底部进入、向顶部退出，与右侧 FloatActivity 悬浮承载器一致。 */
    @Test
    fun splitBillUsesRequestedSlideDirections() {
        assertEquals(1, splitBillEnterOffsetDirection())
        assertEquals(-1, splitBillExitOffsetDirection())
    }

    /** 测试流程：点击右侧“AA收款”，确认进入既有悬浮根内的全屏工作区。 */
    @Test
    fun splitBillEntryOpensDedicatedFullscreenWorkspace() {
        val catalog = source("floatingchat/tools/RightRailToolCatalog.kt").readText()
        val rail = source("floatingchat/tools/RightCoordinateRail.kt").readText()
        val body = source("floatingchat/chat/CoordinateChatBody.kt").readText()
        val overlay = source("FloatingChatOverlayUi.kt").readText()
        val bottomPanel = source("floatingchat/shell/FloatingBottomPanel.kt").readText()

        assertTrue(catalog.contains("opensSplitBill"))
        assertTrue(catalog.contains("item(\"AA收款\", Icons.Filled.AccountBox, opensSplitBill = true)"))
        assertTrue(rail.contains("onSplitBillClick()"))
        assertTrue(body.contains("onSplitBillClick"))
        assertTrue(overlay.contains("bottomPanelMode = BottomPanelMode.SplitBill"))
        assertTrue(overlay.contains("AA 收款只能在群聊中发起"))
        assertTrue(overlay.contains("SplitBillFullScreen("))
        assertFalse(bottomPanel.contains("SplitBillPanel("))
    }

    /** 测试流程：检查 M3 分页、高效列表、工具栏顶部安全区与实体位移动画。 */
    @Test
    fun splitBillWorkspaceUsesRequiredMaterial3BuildingBlocks() {
        val workspace = source("floatingchat/tools/SplitBillFullScreen.kt")
        val presentation = source("floatingchat/components/FloatingWorkspacePresentation.kt")

        assertTrue("AA收款全屏工作区源码必须存在", workspace.isFile)
        val text = workspace.readText()
        val presentationText = presentation.readText()
        assertTrue(text.contains("PrimaryTabRow"))
        assertTrue(text.contains("HorizontalPager"))
        assertTrue(text.contains("LazyColumn"))
        assertTrue(text.contains("FloatingWorkspaceTopAppBar("))
        assertTrue(presentationText.contains("StatusBarTopPaddingDp: Int = 30"))
        assertTrue(presentationText.contains("windowInsets = WindowInsets(top = FloatingWorkspaceTopBarDefaults.StatusBarTopPaddingDp.dp)"))
        assertFalse(text.contains("Spacer(Modifier.height(30.dp))"))
        assertTrue(text.contains(".background(MaterialTheme.colorScheme.surface)"))
        assertTrue(text.contains("containerColor = Color.Transparent"))
        assertTrue(text.contains("MaterialTheme.colorScheme.primary"))
        assertTrue(text.contains("FontWeight.Normal"))
        assertTrue(text.contains("Animatable"))
        assertTrue(text.contains("translationY"))
        assertTrue(text.contains("pageAlpha.animateTo"))
        assertTrue(text.contains("this.alpha = pageAlpha.value"))
        assertFalse(text.contains("Dialog("))
        assertFalse(text.contains("WindowManager"))
    }

    /** 测试流程：群聊选择成员并提交，确认本地 AA 消息字段与 iOS 一致，且没有虚构远端接口。 */
    @Test
    fun splitBillSubmissionCreatesLocalMessageWithoutInventingEndpoint() {
        val workspace = source("floatingchat/tools/SplitBillFullScreen.kt").readText()
        val outgoing = source("floatingchat/message/OutgoingMessageActions.kt").readText()
        val overlay = source("FloatingChatOverlayUi.kt").readText()
        val api = source("scrm/ScrmApiClient.kt").readText()
        val paymentCard = source("floatingchat/message/PaymentMessageUi.kt").readText()

        assertTrue(workspace.contains("AA 收款只能在群聊中发起"))
        assertTrue(workspace.contains("320.00"))
        assertTrue(outgoing.contains("fun addSplitBillMessage("))
        assertTrue(outgoing.contains("FloatingChatMessageType.SplitBill"))
        assertTrue(outgoing.contains("群收款总额"))
        assertTrue(outgoing.contains("收款成员ID"))
        assertTrue(outgoing.contains("未支付"))
        assertTrue(overlay.contains("addSplitBillMessage("))
        assertTrue(paymentCard.contains("SplitBill(\"群收款\")"))
        assertTrue(paymentCard.contains("splitBillAmountTextFor"))
        assertTrue(paymentCard.contains("splitBillStatusTextFor"))
        assertFalse(api.contains("messages/split-bill"))
        assertFalse(api.contains("payments/split-bill"))
    }

    private fun source(relativePath: String): File = File(
        System.getProperty("user.dir"),
        "src/main/kotlin/com/paifa/ubikitouch/accessibility/$relativePath"
    )
}
