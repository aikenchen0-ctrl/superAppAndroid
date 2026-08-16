package com.paifa.ubikitouch.accessibility.floatingchat.tools

import com.paifa.ubikitouch.accessibility.floatingchat.shell.BottomPanelMode
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class MoreToolPanelContractTest {
    @Test
    fun moreToolPanelDoesNotRepeatTheQuickPhraseDestination() {
        assertTrue(moreToolPanelUsesUniqueDestinations())
    }

    @Test
    fun moreToolPanelContainsOnlyTheChatAttachmentActions() {
        assertTrue(
            morePanelToolLabels() == listOf(
                "相册",
                "视频通话",
                "语音通话",
                "定位",
                "红包",
                "礼物",
                "转账",
                "收藏",
                "签约",
                "名片",
                "文件",
                "素材"
            )
        )
    }

    @Test
    fun moreToolPanelUsesDistinctDestinationsInVisualOrder() {
        assertEquals(12, morePanelToolModes().size)
        assertEquals(12, morePanelToolModes().toSet().size)
    }

    @Test
    fun signingEntryReusesTheExistingScrmWorkspaceDestination() {
        val labels = morePanelToolLabels()
        assertEquals(
            BottomPanelMode.ScrmOperations,
            morePanelToolModes()[labels.indexOf("\u7b7e\u7ea6")]
        )
    }

    @Test
    fun morePanelDoesNotExposeRemovedUtilityEntries() {
        val removedLabels = setOf(
            "\u8bed\u97f3\u8f93\u5165",
            "AI\u8bed\u97f3",
            "\u5feb\u6377\u8bdd\u672f",
            "AA \u6536\u6b3e",
            "\u670b\u53cb\u5708",
            "\u5c0f\u7a0b\u5e8f\u5361\u7247"
        )
        assertFalse(morePanelToolLabels().any(removedLabels::contains))
    }
}
