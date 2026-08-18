package com.paifa.univerge.accessibility

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingChatToolbarLayoutContractTest {
    @Test(timeout = 60_000)
    fun activeChatToolbarUsesTwoEqualRegionsAndBoundedMarquees() {
        val header = floatingChatWorkspaceHeaderSource()

        assertTrue(header.contains("FloatingWorkspaceTopAppBar("))
        assertEquals(2, header.occurrencesOf(".weight(1f)"))
        assertEquals(2, header.occurrencesOf("basicMarquee()"))
        assertTrue(header.contains("width(36.dp)"))
        assertTrue(header.contains("widthIn(max = 110.dp)"))
        assertEquals(0, header.occurrencesOf(".size(25.dp)"))
        assertFalse(header.contains("LocalMinimumInteractiveComponentSize"))
    }

    @Test(timeout = 60_000)
    fun activeChatToolbarKeepsRequiredControlOrderAndExistingCallbacks() {
        val header = floatingChatWorkspaceHeaderSource()

        val back = header.indexOf("contentDescription = \"返回\"")
        val title = header.indexOf("text = state.title")
        val edit = header.indexOf("contentDescription = \"编辑会话备注\"")
        val qrCode = header.indexOf("contentDescription = \"扫一扫与添加朋友\"")
        val account = header.indexOf("text = accountName")
        val search = header.indexOf("contentDescription = \"搜索聊天记录\"")

        assertTrue(back >= 0)
        assertTrue(title > back)
        assertTrue(edit > title)
        assertTrue(qrCode > edit)
        assertTrue(account > qrCode)
        assertTrue(search > account)
        assertTrue(header.contains("onClick = onLeadingClick"))
        assertTrue(header.contains("onClick = onEditClick"))
        assertTrue(header.contains("onClick = onSearchClick"))
        assertTrue(header.contains("onClick = onScanClick"))
        assertTrue(header.contains("state.showUnreadDot"))
    }

    @Test(timeout = 60_000)
    fun toolbarEditOpensExistingProfileAndInTreeRemarkInputLayer() {
        val overlay = sourceFile("FloatingChatOverlayUi.kt")
        val inputLayer = File(
            System.getProperty("user.dir"),
            "src/main/kotlin/com/paifa/univerge/accessibility/" +
                "floatingchat/contacts/ContactRemarkInputDialog.kt"
        )

        assertTrue(overlay.contains("contactRemarkDialogTarget"))
        assertTrue(overlay.contains("ContactEditorTarget.User(contact)"))
        assertTrue(overlay.contains("ContactRemarkInputDialog("))
        assertTrue(inputLayer.isFile)

        val inputSource = inputLayer.readText()
        assertTrue(inputSource.contains("OutlinedTextField("))
        assertTrue(inputSource.contains("BackHandler(onBack = onDismiss)"))
        assertTrue(inputSource.contains(".pointerInput(Unit) { detectTapGestures(onTap = {}) }"))
        assertFalse(inputSource.contains("AlertDialog("))
        assertFalse(inputSource.contains("androidx.compose.ui.window.Dialog"))
    }

    private fun floatingChatWorkspaceHeaderSource(): String {
        return sourceFile("FloatingChatOverlayUi.kt")
            .substringAfter("internal fun FloatingChatWorkspaceHeader(")
            .substringBefore("/**")
    }

    private fun String.occurrencesOf(value: String): Int {
        var count = 0
        var offset = 0
        while (true) {
            val index = indexOf(value, offset)
            if (index < 0) return count
            count += 1
            offset = index + value.length
        }
    }

    private fun sourceFile(relativePath: String): String {
        return File(
            System.getProperty("user.dir"),
            "src/main/kotlin/com/paifa/univerge/accessibility/$relativePath"
        ).readText()
    }
}
