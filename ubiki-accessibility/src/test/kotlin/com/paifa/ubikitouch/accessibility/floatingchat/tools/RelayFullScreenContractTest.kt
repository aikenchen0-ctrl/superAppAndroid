package com.paifa.ubikitouch.accessibility.floatingchat.tools

import com.paifa.ubikitouch.accessibility.floatingchat.shell.BottomPanelMode
import com.paifa.ubikitouch.core.model.FloatingChatToolAction
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 测试流程：点击右侧“接龙消息”，应进入独立全屏工作区，而不是旧的群管理预览对话框。
 */
class RelayFullScreenContractTest {
    @Test
    fun relayToolOpensDedicatedFullScreenWorkspace() {
        assertEquals(
            ToolActionDispatch.OpenBottomPanel(BottomPanelMode.Relay),
            toolActionDispatchFor(FloatingChatToolAction.Relay)
        )
    }
}
