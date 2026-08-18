package com.paifa.univerge.accessibility.floatingchat.components

import java.io.File
import com.paifa.univerge.accessibility.floatingchat.shell.BottomPanelMode
import com.paifa.univerge.accessibility.floatingchat.shell.isFullscreenWorkspace
import org.junit.Assert.assertEquals
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
            "src/main/kotlin/com/paifa/univerge/accessibility/floatingchat/group/GroupInfoScreen.kt",
            "src/main/kotlin/com/paifa/univerge/accessibility/floatingchat/group/GroupInvitationFullScreen.kt",
            "src/main/kotlin/com/paifa/univerge/accessibility/floatingchat/aivoice/AiVoicePanel.kt",
            "src/main/kotlin/com/paifa/univerge/accessibility/floatingchat/tools/SideEffectFullScreen.kt",
            "src/main/kotlin/com/paifa/univerge/accessibility/floatingchat/scrm/OpenApiWorkbenchPanel.kt",
            "src/main/kotlin/com/paifa/univerge/accessibility/floatingchat/tools/ToolbarWorkspaceFullScreen.kt",
            "src/main/kotlin/com/paifa/univerge/accessibility/floatingchat/tools/VoiceCallFullScreen.kt",
            "src/main/kotlin/com/paifa/univerge/accessibility/floatingchat/tools/VideoCallFullScreen.kt",
            "src/main/kotlin/com/paifa/univerge/accessibility/AiAutoReplyOverlayController.kt",
            "src/main/kotlin/com/paifa/univerge/accessibility/ContactRelationsOverlayController.kt",
            "src/main/kotlin/com/paifa/univerge/accessibility/LeftSidebarOverlayController.kt",
            "src/main/kotlin/com/paifa/univerge/accessibility/FriendManagementOverlayController.kt",
            "src/main/kotlin/com/paifa/univerge/accessibility/FavoriteLibraryActivity.kt",
            "src/main/kotlin/com/paifa/univerge/accessibility/FinderPublishActivity.kt",
            "src/main/kotlin/com/paifa/univerge/accessibility/floatingchat/moments/MaterialLibraryActivityContent.kt"
        )

        sourceFiles.forEach { relativePath ->
            val source = File(System.getProperty("user.dir"), relativePath).readText()
            assertTrue("$relativePath must use the shared toolbar", source.contains("FloatingWorkspaceTopAppBar("))
            assertTrue(
                "$relativePath must use the Material 3 surface as its floating workspace background",
                source.contains("background(MaterialTheme.colorScheme.surface)")
            )
            assertFalse("$relativePath must not reserve a standalone 30dp status spacer", source.contains("Spacer(Modifier.height(30.dp))"))
        }
    }

    @Test
    fun groupWorkspacesDelegateEntranceAndExitMotionToTheSharedOverlay() {
        val sourceFiles = listOf(
            "src/main/kotlin/com/paifa/univerge/accessibility/floatingchat/group/GroupInfoScreen.kt",
            "src/main/kotlin/com/paifa/univerge/accessibility/floatingchat/group/GroupInvitationFullScreen.kt",
            "src/main/kotlin/com/paifa/univerge/accessibility/floatingchat/tools/SideEffectFullScreen.kt"
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
            "src/main/kotlin/com/paifa/univerge/accessibility/FloatingChatOverlayUi.kt"
        ).readText()
        val toolbarWorkspace = File(
            System.getProperty("user.dir"),
            "src/main/kotlin/com/paifa/univerge/accessibility/floatingchat/tools/ToolbarWorkspaceFullScreen.kt"
        ).readText()

        assertTrue(overlayUi.contains("FloatingWorkspaceTopAppBar("))
        assertTrue(toolbarWorkspace.contains("FloatingWorkspaceTopAppBar("))
        assertTrue(toolbarWorkspace.contains("background(MaterialTheme.colorScheme.surface)"))
        assertFalse(toolbarWorkspace.contains("Animatable"))
        assertFalse(toolbarWorkspace.contains("graphicsLayer"))
        assertFalse(toolbarWorkspace.contains("onSizeChanged"))
    }

    /**
     * 测试流程：打开全部未回消息、具体账号未回消息、搜索和扫码，确认状态区属于 M3 AppBar，
     * 右侧工作区的工具栏使用 surface，普通会话继续保留用户可配置的磨砂背景。
     */
    @Test
    fun unreadAndToolbarWorkspacesKeepTheUiComponentsInsetAndSurfaceRoot() {
        val presentation = File(
            System.getProperty("user.dir"),
            "src/main/kotlin/com/paifa/univerge/accessibility/floatingchat/components/FloatingWorkspacePresentation.kt"
        ).readText()
        val overlayUi = File(
            System.getProperty("user.dir"),
            "src/main/kotlin/com/paifa/univerge/accessibility/FloatingChatOverlayUi.kt"
        ).readText()

        assertTrue(presentation.contains("windowInsets = WindowInsets(top = FloatingWorkspaceTopBarDefaults.StatusBarTopPaddingDp.dp)"))
        assertFalse(presentation.contains("modifier = modifier.padding(top = FloatingWorkspaceTopBarDefaults.StatusBarTopPaddingDp.dp)"))
        assertTrue(presentation.contains("containerColor = MaterialTheme.colorScheme.surface"))
        assertTrue(presentation.contains("scrolledContainerColor = MaterialTheme.colorScheme.surface"))
        assertTrue(
            overlayUi.contains(
                "floatingChatRouteUsesSurfaceWorkspaceRoot(chatNavigationState.route)"
            )
        )
        assertTrue(overlayUi.contains("Modifier.background(MaterialTheme.colorScheme.surface)"))
        assertFalse(overlayUi.contains("floatingChatRouteUsesTransparentWorkspaceRoot"))
    }

    /**
     * 测试流程：依次打开智能抠图、边缘特效、卡包、收藏、素材库、群接龙和 OpenAPI，
     * 确认工具栏与 Tab 之后的主列表只占用剩余高度，避免使用 fillMaxSize 导致内容越界或被裁剪。
     */
    @Test
    fun fullscreenWorkspacesAllocateScrollableContentToTheRemainingHeight() {
        val sourceFiles = listOf(
            "src/main/kotlin/com/paifa/univerge/accessibility/floatingchat/tools/BackgroundRemovalWorkspace.kt",
            "src/main/kotlin/com/paifa/univerge/accessibility/floatingchat/tools/SideEffectFullScreen.kt",
            "src/main/kotlin/com/paifa/univerge/accessibility/floatingchat/tools/CouponWalletFullScreen.kt",
            "src/main/kotlin/com/paifa/univerge/accessibility/FavoriteLibraryActivity.kt",
            "src/main/kotlin/com/paifa/univerge/accessibility/floatingchat/moments/MaterialLibraryActivityContent.kt",
            "src/main/kotlin/com/paifa/univerge/accessibility/floatingchat/tools/RelayFullScreen.kt",
            "src/main/kotlin/com/paifa/univerge/accessibility/floatingchat/scrm/OpenApiWorkbenchPanel.kt",
            "src/main/kotlin/com/paifa/univerge/accessibility/floatingchat/account/AccountCardFullScreen.kt",
            "src/main/kotlin/com/paifa/univerge/accessibility/floatingchat/scrm/AccountDeviceFullScreen.kt",
            "src/main/kotlin/com/paifa/univerge/accessibility/floatingchat/scrm/CustomerProfileFullScreen.kt",
            "src/main/kotlin/com/paifa/univerge/accessibility/floatingchat/group/GroupInfoScreen.kt",
            "src/main/kotlin/com/paifa/univerge/accessibility/floatingchat/group/GroupInvitationFullScreen.kt",
            "src/main/kotlin/com/paifa/univerge/accessibility/floatingchat/tools/ChannelsVideoFullScreen.kt",
            "src/main/kotlin/com/paifa/univerge/accessibility/floatingchat/tools/HiddenUsersFullScreen.kt",
            "src/main/kotlin/com/paifa/univerge/accessibility/floatingchat/tools/LocationFullScreen.kt",
            "src/main/kotlin/com/paifa/univerge/accessibility/floatingchat/tools/RedPacketFullScreen.kt",
            "src/main/kotlin/com/paifa/univerge/accessibility/floatingchat/tools/SplitBillFullScreen.kt",
            "src/main/kotlin/com/paifa/univerge/accessibility/floatingchat/tools/VideoShortFullScreen.kt"
        )

        sourceFiles.forEach { relativePath ->
            val source = File(System.getProperty("user.dir"), relativePath).readText()
            assertTrue(
                "$relativePath must allocate its scrollable content to the remaining full-screen height",
                source.contains("Modifier.weight(1f).fillMaxWidth()")
            )
        }
    }

    /**
     * 测试流程：在素材库进入详情和编辑页，再分别打开工具栏搜索、扫一扫和添加好友，
     * 确认内部页面同样使用 surface 根，并将内容限制在共享工具栏后的剩余高度。
     */
    @Test
    fun nestedFullscreenPagesKeepSurfaceRootsAndRemainingHeight() {
        val materialLibrary = File(
            System.getProperty("user.dir"),
            "src/main/kotlin/com/paifa/univerge/accessibility/floatingchat/moments/MaterialLibraryActivityContent.kt"
        ).readText()
        val toolbarWorkspace = File(
            System.getProperty("user.dir"),
            "src/main/kotlin/com/paifa/univerge/accessibility/floatingchat/tools/ToolbarWorkspaceFullScreen.kt"
        ).readText()

        assertEquals(
            3,
            Regex("Column\\(Modifier\\.fillMaxSize\\(\\)\\.background\\(MaterialTheme\\.colorScheme\\.surface\\)\\)")
                .findAll(materialLibrary)
                .count()
        )
        assertEquals(
            3,
            Regex("Modifier\\.weight\\(1f\\)\\.fillMaxWidth\\(\\)")
                .findAll(materialLibrary)
                .count()
        )
        assertTrue(toolbarWorkspace.contains("Column(Modifier.weight(1f).fillMaxWidth())"))
    }

    /**
     * 测试流程：依次打开右侧任意全屏功能页，确认进出场只使用 UI组件 的统一运动规格，
     * 不允许聊天根按照具体功能页叠加或选择另一套位移动画。
     */
    @Test
    fun fullscreenWorkspacesUseOneSharedRootMotionContract() {
        val overlayUi = File(
            System.getProperty("user.dir"),
            "src/main/kotlin/com/paifa/univerge/accessibility/FloatingChatOverlayUi.kt"
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
     * 测试流程：从右侧依次打开联系人、朋友圈、视频号、素材库、AI 配置、语音助手、通话、收藏和文件，
     * 确认每个工作区都占满悬浮根，
     * 并使用 Material 3 surface，而非透明或受限的居中面板。
     */
    @Test
    fun fullscreenWorkspacesUseSurfaceAndAvoidCenteredInsetsForEveryFullPageTool() {
        val bottomPanel = File(
            System.getProperty("user.dir"),
            "src/main/kotlin/com/paifa/univerge/accessibility/floatingchat/shell/FloatingBottomPanel.kt"
        ).readText()
        val service = File(
            System.getProperty("user.dir"),
            "src/main/kotlin/com/paifa/univerge/accessibility/UbikiAccessibilityService.kt"
        ).readText()
        listOf(
            BottomPanelMode.Moments,
            BottomPanelMode.Finder,
            BottomPanelMode.MomentMaterials,
            BottomPanelMode.Contacts,
            BottomPanelMode.Assistant,
            BottomPanelMode.AiVoice,
            BottomPanelMode.VoiceCall,
            BottomPanelMode.VideoCall,
            BottomPanelMode.Favorite,
            BottomPanelMode.FileDocument
        ).forEach { mode ->
            assertTrue("$mode must use the full-screen workspace", mode.isFullscreenWorkspace())
        }
        assertTrue(bottomPanel.contains("val isFullscreenWorkspace = mode.isFullscreenWorkspace()"))
        assertTrue(bottomPanel.contains("color = if (isFullscreenWorkspace) MaterialTheme.colorScheme.surface"))
        assertTrue(bottomPanel.contains("modifier = if (isFullscreenWorkspace) Modifier.fillMaxSize()"))
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
            "src/main/kotlin/com/paifa/univerge/accessibility/floatingchat/shell/FloatingChatOverlayRuntimeState.kt"
        ).readText()
        val controller = File(
            System.getProperty("user.dir"),
            "src/main/kotlin/com/paifa/univerge/accessibility/FloatingChatOverlayController.kt"
        ).readText()
        val service = File(
            System.getProperty("user.dir"),
            "src/main/kotlin/com/paifa/univerge/accessibility/UbikiAccessibilityService.kt"
        ).readText()
        val panelMode = File(
            System.getProperty("user.dir"),
            "src/main/kotlin/com/paifa/univerge/accessibility/floatingchat/shell/BottomPanelMode.kt"
        ).readText()

        assertTrue(runtimeState.contains("requestWorkspace"))
        assertTrue(controller.contains("openWorkspace"))
        assertTrue(service.contains("openWorkspace(BottomPanelMode.OpenApiWorkbench)"))
        assertTrue(service.contains("openWorkspace(BottomPanelMode.BackgroundRemoval)"))
        assertTrue(panelMode.contains("OpenApiWorkbench"))
        assertTrue(panelMode.contains("BackgroundRemoval"))
    }

    /**
     * 测试流程：从右侧素材库进入，确认素材列表页面使用 UI组件 的 surface 根和 M3 工具栏，
     * 顶部 30dp 仅由共享 AppBar 处理，不能再保留页面级状态栏占位。
     */
    @Test
    fun materialLibraryUsesTheSharedSurfaceWorkspacePresentation() {
        val source = File(
            System.getProperty("user.dir"),
            "src/main/kotlin/com/paifa/univerge/accessibility/floatingchat/moments/MaterialLibraryActivityContent.kt"
        ).readText()

        assertTrue(source.contains("FloatingWorkspaceTopAppBar("))
        assertTrue(source.contains("background(MaterialTheme.colorScheme.surface)"))
        assertFalse(source.contains("Spacer(Modifier.height(30.dp))"))
    }

    /**
     * 测试流程：依次从右侧功能栏和工具栏打开全屏页面，确认仅聊天根负责进出场位移，
     * 子页面不能再次创建整页 Animatable、graphicsLayer 或 onSizeChanged 动画。
     */
    @Test
    fun fullscreenRoutesDelegatePageMotionToTheOverlayRoot() {
        val sourceFiles = listOf(
            "src/main/kotlin/com/paifa/univerge/accessibility/floatingchat/account/AccountCardFullScreen.kt",
            "src/main/kotlin/com/paifa/univerge/accessibility/floatingchat/scrm/AccountDeviceFullScreen.kt",
            "src/main/kotlin/com/paifa/univerge/accessibility/floatingchat/scrm/CustomerProfileFullScreen.kt",
            "src/main/kotlin/com/paifa/univerge/accessibility/floatingchat/tools/ChannelsLiveFullScreen.kt",
            "src/main/kotlin/com/paifa/univerge/accessibility/floatingchat/tools/ChannelsVideoFullScreen.kt",
            "src/main/kotlin/com/paifa/univerge/accessibility/floatingchat/tools/CouponWalletFullScreen.kt",
            "src/main/kotlin/com/paifa/univerge/accessibility/floatingchat/tools/FavoriteShareFullScreen.kt",
            "src/main/kotlin/com/paifa/univerge/accessibility/floatingchat/tools/FileDocumentFullScreen.kt",
            "src/main/kotlin/com/paifa/univerge/accessibility/floatingchat/tools/GalleryFullScreen.kt",
            "src/main/kotlin/com/paifa/univerge/accessibility/floatingchat/tools/HiddenUsersFullScreen.kt",
            "src/main/kotlin/com/paifa/univerge/accessibility/floatingchat/tools/LocationFullScreen.kt",
            "src/main/kotlin/com/paifa/univerge/accessibility/floatingchat/tools/MiniProgramFullScreen.kt",
            "src/main/kotlin/com/paifa/univerge/accessibility/floatingchat/tools/MusicFullScreen.kt",
            "src/main/kotlin/com/paifa/univerge/accessibility/floatingchat/tools/OfficialArticleFullScreen.kt",
            "src/main/kotlin/com/paifa/univerge/accessibility/floatingchat/tools/QuickPhraseFullScreen.kt",
            "src/main/kotlin/com/paifa/univerge/accessibility/floatingchat/tools/RedPacketFullScreen.kt",
            "src/main/kotlin/com/paifa/univerge/accessibility/floatingchat/tools/RelayFullScreen.kt",
            "src/main/kotlin/com/paifa/univerge/accessibility/floatingchat/tools/ReviewRequestsFullScreen.kt",
            "src/main/kotlin/com/paifa/univerge/accessibility/floatingchat/tools/SendNameFullScreen.kt",
            "src/main/kotlin/com/paifa/univerge/accessibility/floatingchat/tools/SplitBillFullScreen.kt",
            "src/main/kotlin/com/paifa/univerge/accessibility/floatingchat/tools/TransferFullScreen.kt",
            "src/main/kotlin/com/paifa/univerge/accessibility/floatingchat/tools/VideoCallFullScreen.kt",
            "src/main/kotlin/com/paifa/univerge/accessibility/floatingchat/tools/VideoShortFullScreen.kt",
            "src/main/kotlin/com/paifa/univerge/accessibility/floatingchat/tools/VoiceCallFullScreen.kt",
            "src/main/kotlin/com/paifa/univerge/accessibility/floatingchat/tools/VoiceMessageFullScreen.kt",
            "src/main/kotlin/com/paifa/univerge/accessibility/floatingchat/tools/WebLinkFullScreen.kt"
        )

        sourceFiles.forEach { relativePath ->
            val source = File(System.getProperty("user.dir"), relativePath).readText()
            assertTrue(
                "$relativePath must keep a Material 3 surface root",
                source.contains("background(MaterialTheme.colorScheme.surface)")
            )
            assertTrue(
                "$relativePath must reuse the shared floating workspace toolbar",
                source.contains("FloatingWorkspaceTopAppBar(")
            )
            assertFalse(
                "$relativePath must not reserve an independent 30dp status spacer",
                source.contains("Spacer(Modifier.height(30.dp))")
            )
            assertFalse("$relativePath must not create a second page Animatable", source.contains("Animatable"))
            assertFalse("$relativePath must not add a second page graphicsLayer", source.contains("graphicsLayer"))
            assertFalse("$relativePath must not measure itself for page motion", source.contains("onSizeChanged"))
        }
    }
}
