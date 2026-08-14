package com.paifa.ubikitouch.accessibility.floatingchat.tools

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolbarWorkspaceContractTest {
    @Test
    fun toolbarWorkspaceProvidesSearchScanAndAddFriendFlows() {
        assertEquals(
            listOf(
                ToolbarWorkspaceMode.Search,
                ToolbarWorkspaceMode.Scan,
                ToolbarWorkspaceMode.AddFriend
            ),
            ToolbarWorkspaceMode.entries
        )
    }

    @Test
    fun toolbarWorkspaceUsesFullscreenMaterial3AndExistingFriendApi() {
        val workspaceSource = File(
            System.getProperty("user.dir"),
            "src/main/kotlin/com/paifa/ubikitouch/accessibility/floatingchat/tools/ToolbarWorkspaceFullScreen.kt"
        ).readText()
        val overlaySource = File(
            System.getProperty("user.dir"),
            "src/main/kotlin/com/paifa/ubikitouch/accessibility/FloatingChatOverlayUi.kt"
        ).readText()

        assertTrue(workspaceSource.contains("Spacer(Modifier.height(30.dp))"))
        assertTrue(workspaceSource.contains("TopAppBar("))
        assertTrue(workspaceSource.contains("translationY"))
        assertTrue(workspaceSource.contains("LazyColumn("))
        assertTrue(overlaySource.contains("FilledTonalIconButton("))
        assertTrue(overlaySource.contains("contactApi.addFriendsByPhone("))
        assertTrue(overlaySource.contains("contactApi.addFriend("))
    }
}
