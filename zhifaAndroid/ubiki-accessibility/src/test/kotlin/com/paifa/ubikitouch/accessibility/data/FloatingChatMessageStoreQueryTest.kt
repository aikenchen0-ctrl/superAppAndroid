package com.paifa.ubikitouch.accessibility.data

import android.app.Application
import com.paifa.ubikitouch.core.model.FloatingChatConnectionTarget
import com.paifa.ubikitouch.core.model.FloatingChatMessage
import com.paifa.ubikitouch.core.model.FloatingChatMessagePresentation
import com.paifa.ubikitouch.core.model.FloatingChatMessageType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FloatingChatMessageStoreQueryTest {
    private lateinit var context: Application
    private val closeables = mutableListOf<AutoCloseable>()

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.deleteDatabase(FloatingChatDatabaseContract.databaseName)
    }

    @After
    fun tearDown() {
        closeables.asReversed().forEach(AutoCloseable::close)
        closeables.clear()
        context.deleteDatabase(FloatingChatDatabaseContract.databaseName)
    }

    @Test(timeout = 60_000L)
    fun recentFloatingMessagesLoadsGlobalRecentMessagesWithoutThreadEnumeration() {
        val store = FloatingChatMessageStore(context).also(closeables::add)
        store.insertFloatingMessage(message("old", "friend-old"), threadId = "private:friend-old", createdAt = 1L)
        store.insertFloatingMessage(message("new", "friend-new"), threadId = "private:friend-new", createdAt = 2L)

        val recent = store.recentFloatingMessages(limit = 1)

        assertEquals(listOf("new"), recent.map { it.id })
    }

    private fun message(id: String, contactId: String): FloatingChatMessage {
        return FloatingChatMessage(
            id = id,
            type = FloatingChatMessageType.Text,
            text = id,
            fromMe = false,
            senderName = contactId,
            time = "now",
            presentation = FloatingChatMessagePresentation.Bubble,
            connectionTarget = FloatingChatConnectionTarget.User,
            connectionTargetId = contactId,
            threadContactId = contactId
        )
    }
}
