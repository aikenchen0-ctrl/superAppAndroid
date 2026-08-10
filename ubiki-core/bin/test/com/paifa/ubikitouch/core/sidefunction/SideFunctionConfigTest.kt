package com.paifa.ubikitouch.core.sidefunction

import org.junit.Assert.assertEquals
import org.junit.Test

class SideFunctionConfigTest {
    @Test
    fun fixedActionsPrecedeCustomActions() {
        val config = SideFunctionConfig.fromCustomActionIds(listOf("home", "play_video"))

        assertEquals(
            listOf("close_all", "back", "home", "play_video"),
            config.allActionIds
        )
    }

    @Test
    fun customActionsAreLimitedToFiveAndIgnoreBlankValues() {
        val config = SideFunctionConfig.fromCustomActionIds(
            listOf(
                "home",
                "",
                "back",
                "recents",
                "notifications",
                "quick_settings",
                "screenshot"
            )
        )

        assertEquals(
            listOf("home", "back", "recents", "notifications", "quick_settings"),
            config.customActionIds
        )
    }
}
