package com.paifa.ubikitouch.accessibility.floatingchat.chat

import com.paifa.ubikitouch.core.model.FloatingChatPrototype
import org.junit.Assert.assertSame
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountAvatarSwitchPolicyTest {
    @Test
    fun repeatedOrInvalidAccountClicksDoNotStartAnotherSwitch() {
        assertFalse(shouldHandleAccountAvatarClick("account-a", "account-a"))
        assertFalse(shouldHandleAccountAvatarClick("account-a", ""))
        assertTrue(shouldHandleAccountAvatarClick("account-a", "account-b"))
    }

    @Test
    fun scopedConversationIsReusedWhenSwitchingBackToAnAccount() {
        val source = FloatingChatPrototype.sampleConversation()
        val accountId = source.accountContacts.first().id
        val cache = AccountScopedConversationCache(source)

        assertSame(cache.conversationFor(accountId), cache.conversationFor(accountId))
    }
}
