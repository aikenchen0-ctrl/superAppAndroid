package com.paifa.ubikitouch.accessibility

import com.paifa.ubikitouch.accessibility.floatingchat.chat.UnrepliedDraftKey
import com.paifa.ubikitouch.accessibility.floatingchat.chat.UnrepliedOverviewState
import com.paifa.ubikitouch.accessibility.floatingchat.chat.afterDraftSend
import com.paifa.ubikitouch.accessibility.floatingchat.chat.updateDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UnrepliedDraftStateTest {
    @Test
    fun failedSendKeepsDraftAndSuccessfulSendRemovesIt() {
        val key = UnrepliedDraftKey("account-a", "private:thread-a")
        val state = UnrepliedOverviewState().updateDraft(key, "收到，我来处理")

        assertEquals("收到，我来处理", state.afterDraftSend(key, succeeded = false).drafts[key])
        assertNull(state.afterDraftSend(key, succeeded = true).drafts[key])
    }

    @Test
    fun blankDraftRemovesStoredValue() {
        val key = UnrepliedDraftKey("account-a", "private:thread-a")
        val state = UnrepliedOverviewState()
            .updateDraft(key, "待回复")
            .updateDraft(key, "")

        assertNull(state.drafts[key])
    }
}
