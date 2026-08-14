package com.paifa.ubikitouch.accessibility.floatingchat.components

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 测试流程：依次点击右侧群信息、群邀请卡、语音助手和边缘特效，
 * 确认各页面的状态区由统一 M3 工具栏承载，关闭时沿统一全屏工作区方向退出。
 */
class FloatingWorkspaceAdoptionContractTest {
    @Test
    fun rightRailWorkspacesReuseTheSharedToolbarInsteadOfStandaloneStatusSpacers() {
        val sourceFiles = listOf(
            "src/main/kotlin/com/paifa/ubikitouch/accessibility/floatingchat/group/GroupInfoScreen.kt",
            "src/main/kotlin/com/paifa/ubikitouch/accessibility/floatingchat/group/GroupInvitationFullScreen.kt",
            "src/main/kotlin/com/paifa/ubikitouch/accessibility/floatingchat/aivoice/AiVoicePanel.kt",
            "src/main/kotlin/com/paifa/ubikitouch/accessibility/floatingchat/tools/SideEffectFullScreen.kt",
            "src/main/kotlin/com/paifa/ubikitouch/accessibility/floatingchat/scrm/OpenApiWorkbenchPanel.kt",
            "src/main/kotlin/com/paifa/ubikitouch/accessibility/floatingchat/tools/ToolbarWorkspaceFullScreen.kt",
            "src/main/kotlin/com/paifa/ubikitouch/accessibility/floatingchat/tools/VoiceCallFullScreen.kt",
            "src/main/kotlin/com/paifa/ubikitouch/accessibility/floatingchat/tools/VideoCallFullScreen.kt",
            "src/main/kotlin/com/paifa/ubikitouch/accessibility/AiAutoReplyOverlayController.kt",
            "src/main/kotlin/com/paifa/ubikitouch/accessibility/ContactRelationsOverlayController.kt",
            "src/main/kotlin/com/paifa/ubikitouch/accessibility/LeftSidebarOverlayController.kt"
        )

        sourceFiles.forEach { relativePath ->
            val source = File(System.getProperty("user.dir"), relativePath).readText()
            assertTrue("$relativePath must use the shared toolbar", source.contains("FloatingWorkspaceTopAppBar("))
            assertTrue("$relativePath must keep the floating workspace background transparent", source.contains("Color.Transparent"))
            assertFalse("$relativePath must not reserve a standalone 30dp status spacer", source.contains("Spacer(Modifier.height(30.dp))"))
        }
    }

    @Test
    fun groupWorkspacesDelegateEntranceAndExitMotionToTheSharedOverlay() {
        val sourceFiles = listOf(
            "src/main/kotlin/com/paifa/ubikitouch/accessibility/floatingchat/group/GroupInfoScreen.kt",
            "src/main/kotlin/com/paifa/ubikitouch/accessibility/floatingchat/group/GroupInvitationFullScreen.kt",
            "src/main/kotlin/com/paifa/ubikitouch/accessibility/floatingchat/tools/SideEffectFullScreen.kt"
        )

        sourceFiles.forEach { relativePath ->
            val source = File(System.getProperty("user.dir"), relativePath).readText()
            assertFalse("$relativePath must not start a second page animation", source.contains("Animatable"))
            assertFalse("$relativePath must not apply a page translation layer", source.contains("graphicsLayer"))
            assertFalse("$relativePath must not measure itself only for page motion", source.contains("onSizeChanged"))
        }
    }

    /**
     * 测试流程：分别展开全部未回消息、单账号未回消息，再点击顶部搜索、扫一扫和添加朋友。
     * 确认主聊天与三个工具栏工作区都复用 UI组件 的工具栏和根级入出场动画。
     */
    @Test
    fun unreadChatAndToolbarWorkspacesUseTheUiComponentsPresentationContract() {
        val overlayUi = File(
            System.getProperty("user.dir"),
            "src/main/kotlin/com/paifa/ubikitouch/accessibility/FloatingChatOverlayUi.kt"
        ).readText()
        val toolbarWorkspace = File(
            System.getProperty("user.dir"),
            "src/main/kotlin/com/paifa/ubikitouch/accessibility/floatingchat/tools/ToolbarWorkspaceFullScreen.kt"
        ).readText()

        assertTrue(overlayUi.contains("FloatingWorkspaceTopAppBar("))
        assertTrue(toolbarWorkspace.contains("FloatingWorkspaceTopAppBar("))
        assertTrue(toolbarWorkspace.contains("Color.Transparent"))
        assertFalse(toolbarWorkspace.contains("Animatable"))
        assertFalse(toolbarWorkspace.contains("graphicsLayer"))
        assertFalse(toolbarWorkspace.contains("onSizeChanged"))
    }

    /**
     * 测试流程：打开全部未回消息、具体账号未回消息、搜索和扫码，确认状态区属于 M3 AppBar，
     * 未回消息页根保持透明，只有普通会话继续保留用户可配置的磨砂背景。
     */
    @Test
    fun unreadAndToolbarWorkspacesKeepTheUiComponentsInsetAndTransparentRoot() {
        val presentation = File(
            System.getProperty("user.dir"),
            "src/main/kotlin/com/paifa/ubikitouch/accessibility/floatingchat/components/FloatingWorkspacePresentation.kt"
        ).readText()
        val overlayUi = File(
            System.getProperty("user.dir"),
            "src/main/kotlin/com/paifa/ubikitouch/accessibility/FloatingChatOverlayUi.kt"
        ).readText()

        assertTrue(presentation.contains("windowInsets = WindowInsets(top = FloatingWorkspaceTopBarDefaults.StatusBarTopPaddingDp.dp)"))
        assertFalse(presentation.contains("modifier = modifier.padding(top = FloatingWorkspaceTopBarDefaults.StatusBarTopPaddingDp.dp)"))
        assertTrue(overlayUi.contains("floatingChatRouteUsesTransparentWorkspaceRoot"))
        assertTrue(overlayUi.contains("enabled = frostedBackgroundEnabled &&"))
        assertTrue(
            overlayUi.contains(
                "!floatingChatRouteUsesTransparentWorkspaceRoot(chatNavigationState.route)"
            )
        )
    }

    /**
     * 测试流程：依次打开右侧任意全屏功能页，确认进出场只使用 UI组件 的统一运动规格，
     * 不允许聊天根按照具体功能页叠加或选择另一套位移动画。
     */
    @Test
    fun fullscreenWorkspacesUseOneSharedRootMotionContract() {
        val overlayUi = File(
            System.getProperty("user.dir"),
            "src/main/kotlin/com/paifa/ubikitouch/accessibility/FloatingChatOverlayUi.kt"
        ).readText()

        assertTrue(overlayUi.contains("FloatingWorkspaceMotion.EnterOffsetDirection"))
        assertTrue(overlayUi.contains("FloatingWorkspaceMotion.ExitOffsetDirection"))
        assertFalse(overlayUi.contains("aiAssistantEnterOffsetDirection()"))
        assertFalse(overlayUi.contains("aiVoiceEnterOffsetDirection()"))
        assertFalse(overlayUi.contains("uiComponentsEnterOffsetDirection()"))
        assertFalse(overlayUi.contains("miniProgramEnterOffsetDirection()"))
        assertFalse(overlayUi.contains("reviewRequestsEnterOffsetDirection()"))
        assertFalse(overlayUi.contains("quickPhraseEnterOffsetDirection()"))
        assertFalse(overlayUi.contains("galleryEnterOffsetDirection()"))
        assertFalse(overlayUi.contains("voiceMessageEnterOffsetDirection()"))
    }

    /**
     * 测试流程：从右侧依次打开 UI组件 与智能抠图，确认全屏工作区不由父级铺设白底，
     * 智能抠图启动前不隐藏聊天悬浮根节点，因此可在原界面之上连续完成位移动画。
     */
    @Test
    fun fullscreenWorkspacesKeepTheSharedChatRootVisibleBehindTheirTransparentSurface() {
        val bottomPanel = File(
            System.getProperty("user.dir"),
            "src/main/kotlin/com/paifa/ubikitouch/accessibility/floatingchat/shell/FloatingBottomPanel.kt"
        ).readText()
        val service = File(
            System.getProperty("user.dir"),
            "src/main/kotlin/com/paifa/ubikitouch/accessibility/UbikiAccessibilityService.kt"
        ).readText()

        assertTrue(bottomPanel.contains("color = if (isFullscreenWorkspace) Color.Transparent"))
        assertFalse(service.contains("hideFloatingChatForExternalActivity(\"background removal\")"))
    }

    /**
     * 测试流程：从右侧分别打开 OpenAPI 与智能抠图，确认请求写入聊天根状态，
     * 并由 UI组件同一条 BottomPanelMode 与 AnimatedVisibility 链路承载，禁止 Activity 跳板。
     */
    @Test
    fun activityBridgeEntriesRouteThroughTheExistingFloatingChatWorkspace() {
        val runtimeState = File(
            System.getProperty("user.dir"),
            "src/main/kotlin/com/paifa/ubikitouch/accessibility/floatingchat/shell/FloatingChatOverlayRuntimeState.kt"
        ).readText()
        val controller = File(
            System.getProperty("user.dir"),
            "src/main/kotlin/com/paifa/ubikitouch/accessibility/FloatingChatOverlayController.kt"
        ).readText()
        val service = File(
            System.getProperty("user.dir"),
            "src/main/kotlin/com/paifa/ubikitouch/accessibility/UbikiAccessibilityService.kt"
        ).readText()
        val panelMode = File(
            System.getProperty("user.dir"),
            "src/main/kotlin/com/paifa/ubikitouch/accessibility/floatingchat/shell/BottomPanelMode.kt"
        ).readText()

        assertTrue(runtimeState.contains("requestWorkspace"))
        assertTrue(controller.contains("openWorkspace"))
        assertTrue(service.contains("openWorkspace(BottomPanelMode.OpenApiWorkbench)"))
        assertTrue(service.contains("openWorkspace(BottomPanelMode.BackgroundRemoval)"))
        assertTrue(panelMode.contains("OpenApiWorkbench"))
        assertTrue(panelMode.contains("BackgroundRemoval"))
    }
}
