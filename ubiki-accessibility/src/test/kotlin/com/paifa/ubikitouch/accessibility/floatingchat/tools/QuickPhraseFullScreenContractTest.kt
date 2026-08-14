package com.paifa.ubikitouch.accessibility.floatingchat.tools

import org.junit.Assert.assertEquals
import org.junit.Test

class QuickPhraseFullScreenContractTest {
    /** 测试流程：从右侧快捷语进入，确认全屏工作区保留 30dp 状态栏安全区。 */
    @Test
    fun quickPhraseWorkspaceUsesTheRequiredStatusBarSpace() {
        assertEquals(30, QuickPhraseStatusBarHeightDp)
    }

    /** 测试流程：打开并返回快捷语，确认实体全屏视图从下方进入并向下方退出。 */
    @Test
    fun quickPhraseWorkspaceUsesTheRequestedSlideDirections() {
        assertEquals(1, quickPhraseEnterOffsetDirection())
        assertEquals(1, quickPhraseExitOffsetDirection())
    }

    @Test
    fun quickPhraseTabsExposeRecentAndManagePages() {
        assertEquals(
            listOf(QuickPhraseFullScreenTab.Recent, QuickPhraseFullScreenTab.Manage),
            QuickPhraseFullScreenTab.entries
        )
    }
}
