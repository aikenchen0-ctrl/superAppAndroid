package com.paifa.ubikitouch.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingChatDataDiagnosticsTest {
    @Test
    fun diagnosticIdDoesNotExposeOriginalIdentifier() {
        val original = "wxid_sensitive_user_123"

        val diagnostic = floatingChatDiagnosticId(original)

        assertFalse(diagnostic.contains(original))
        assertEquals(8, diagnostic.length)
    }

    @Test
    fun conversationSourceDistinguishesPrototypeAndBackendData() {
        assertEquals(
            FloatingChatDataSource.Prototype,
            floatingChatDataSource(
                peerName = "星河产品小组",
                contactIds = listOf("li-si"),
                accountIds = listOf("account-main")
            )
        )
        assertEquals(
            FloatingChatDataSource.Backend,
            floatingChatDataSource(
                peerName = "SCRM Contacts",
                contactIds = listOf("scrm-contact:wxid_friend"),
                accountIds = listOf("scrm-account:device\nwxid_account")
            )
        )
        assertTrue(
            floatingChatDataSource(
                peerName = "SCRM Contacts",
                contactIds = emptyList(),
                accountIds = emptyList()
            ) == FloatingChatDataSource.BackendEmpty
        )
    }
}
