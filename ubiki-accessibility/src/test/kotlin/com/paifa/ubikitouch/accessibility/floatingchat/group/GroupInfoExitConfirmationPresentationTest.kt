package com.paifa.ubikitouch.accessibility.floatingchat.group

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 退出群聊必须先在同一个悬浮根内确认，不能直接下发退出任务或创建第二个 Window。 */
class GroupInfoExitConfirmationPresentationTest {
    @Test
    fun exitGroupRequiresInPageConfirmationBeforeDispatchingEvent() {
        val source = File(
            System.getProperty("user.dir"),
            "src/main/kotlin/com/paifa/ubikitouch/accessibility/floatingchat/group/GroupInfoScreen.kt"
        ).readText()

        assertTrue(source.contains("var showExitConfirmation by remember"))
        assertTrue(source.contains("GroupInfoExitConfirmation("))
        assertTrue(source.contains("onConfirm = {"))
        assertTrue(source.contains("onEvent(GroupInfoUiEvent.ExitGroupRequested)"))
        assertTrue(source.contains("onExitRequested = { showExitConfirmation = true }"))
        assertFalse(source.contains("AlertDialog("))
    }
}
