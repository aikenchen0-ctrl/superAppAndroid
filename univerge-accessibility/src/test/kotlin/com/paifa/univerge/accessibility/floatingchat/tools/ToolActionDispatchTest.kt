package com.paifa.univerge.accessibility.floatingchat.tools

import com.paifa.univerge.accessibility.floatingchat.shell.BottomPanelMode
import com.paifa.univerge.core.model.FloatingChatToolAction
import org.junit.Assert.assertEquals
import org.junit.Test

class ToolActionDispatchTest {
    @Test
    fun commandActionOpensTheOpenApiWorkbench() {
        assertEquals(
            ToolActionDispatch.OpenApiWorkbench,
            toolActionDispatchFor(FloatingChatToolAction.Command)
        )
    }

    @Test
    fun finderActionOpensTheFinderPublishActivity() {
        assertEquals(
            ToolActionDispatch.OpenFinderPublish,
            toolActionDispatchFor(FloatingChatToolAction.Finder)
        )
    }

    @Test
    fun favoriteActionOpensTheFavoriteLibrary() {
        assertEquals(
            ToolActionDispatch.OpenFavoriteLibrary,
            toolActionDispatchFor(FloatingChatToolAction.Favorite)
        )
    }

    @Test
    fun uiComponentsActionOpensTheUiComponentsWorkspace() {
        assertEquals(
            ToolActionDispatch.OpenBottomPanel(BottomPanelMode.UiComponents),
            toolActionDispatchFor(FloatingChatToolAction.UiComponents)
        )
    }

    @Test
    fun miniProgramActionOpensTheMiniProgramWorkspace() {
        assertEquals(
            ToolActionDispatch.OpenBottomPanel(BottomPanelMode.MiniProgram),
            toolActionDispatchFor(FloatingChatToolAction.MiniProgram)
        )
    }

    @Test
    fun reviewRequestsActionOpensTheReviewRequestsWorkspace() {
        assertEquals(
            ToolActionDispatch.OpenBottomPanel(BottomPanelMode.ReviewRequests),
            toolActionDispatchFor(FloatingChatToolAction.ReviewRequests)
        )
    }
}
