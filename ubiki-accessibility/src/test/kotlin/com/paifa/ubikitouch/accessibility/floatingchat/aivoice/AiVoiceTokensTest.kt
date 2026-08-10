package com.paifa.ubikitouch.accessibility.floatingchat.aivoice

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Test

class AiVoiceTokensTest {
    @Test
    fun allAiVoicePanelsShareCentralizedSpacingAndTypography() {
        val tokens = AiVoiceTokens()

        assertEquals(12.dp, tokens.panelPadding)
        assertEquals(10.dp, tokens.itemSpacing)
        assertEquals(14.sp, tokens.titleStyle.fontSize)
        assertEquals(12.sp, tokens.itemTitleStyle.fontSize)
        assertEquals(10.sp, tokens.descriptionStyle.fontSize)
    }
}
