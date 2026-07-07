package com.paifa.ubikitouch.accessibility.data

import com.paifa.ubikitouch.core.model.FloatingChatConnectionTarget
import com.paifa.ubikitouch.core.model.FloatingChatMessage
import com.paifa.ubikitouch.core.model.FloatingChatMessagePresentation
import com.paifa.ubikitouch.core.model.FloatingChatMessageType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingChatMessageStoreContractTest {
    @Test
    fun `file content key is stable for the same local media`() {
        val first = FloatingChatFileIndex.contentKeyForUri(
            uri = "file:///data/user/0/com.paifa.ubikitouch/cache/floating-chat-original-media/image-1.jpg",
            mimeType = "image/jpeg",
            sizeBytes = 2048L,
            modifiedAtMillis = 1234L
        )
        val second = FloatingChatFileIndex.contentKeyForUri(
            uri = "file:///data/user/0/com.paifa.ubikitouch/cache/floating-chat-original-media/image-1.jpg",
            mimeType = "image/jpeg",
            sizeBytes = 2048L,
            modifiedAtMillis = 1234L
        )

        assertEquals(first, second)
    }

    @Test
    fun `message media extracts one original file with preview uri instead of duplicate file rows`() {
        val message = FloatingChatMessage(
            id = "local-image-1",
            type = FloatingChatMessageType.ImageThumbnail,
            text = "",
            fromMe = true,
            senderName = "Lin",
            time = "just now",
            presentation = FloatingChatMessagePresentation.MediaStandalone,
            connectionTarget = FloatingChatConnectionTarget.Account,
            connectionTargetId = "account-main",
            resourceUrl = "file:///cache/original/image-1.jpg",
            thumbnailUrl = "file:///cache/preview/image-1.jpg",
            mediaMimeType = "image/jpeg"
        )

        val attachments = FloatingChatFileIndex.fileAttachmentsForMessage(message)

        assertEquals(1, attachments.size)
        assertEquals("file:///cache/original/image-1.jpg", attachments.single().file.uri)
        assertEquals("file:///cache/preview/image-1.jpg", attachments.single().file.previewUri)
        assertEquals("image", attachments.single().ref.role)
    }

    @Test
    fun `forwarding message reuses existing file ids`() {
        val sourceRefs = listOf(
            LocalChatMessageFileRef(
                messageId = "source-message",
                fileId = "file-image-1",
                role = "image",
                position = 0
            ),
            LocalChatMessageFileRef(
                messageId = "source-message",
                fileId = "file-preview-ignored",
                role = "preview",
                position = 1
            )
        )

        val forwardedRefs = FloatingChatFileIndex.forwardedFileRefs(
            sourceRefs = sourceRefs,
            targetMessageId = "forwarded-message"
        )

        assertEquals(listOf("file-image-1", "file-preview-ignored"), forwardedRefs.map { it.fileId })
        assertTrue(forwardedRefs.all { it.messageId == "forwarded-message" })
    }

    @Test
    fun `sqlite schema has unique file content key and message file join table`() {
        val schema = FloatingChatDatabaseContract.createStatements.joinToString("\n")

        assertTrue(schema.contains("chat_files"))
        assertTrue(schema.contains("content_key TEXT NOT NULL UNIQUE"))
        assertTrue(schema.contains("chat_message_files"))
        assertTrue(schema.contains("PRIMARY KEY(message_id, file_id, role, position)"))
    }

    @Test
    fun `sqlite schema stores moments and migrates existing databases`() {
        val schema = FloatingChatDatabaseContract.createStatements.joinToString("\n")
        val v1ToV2Migration = FloatingChatDatabaseContract.migrationStatements(
            oldVersion = 1,
            newVersion = 2
        ).joinToString("\n")

        assertEquals(4, FloatingChatDatabaseContract.databaseVersion)
        assertTrue(schema.contains("moment_posts"))
        assertTrue(schema.contains("post_id TEXT PRIMARY KEY"))
        assertTrue(schema.contains("media_uri TEXT"))
        assertTrue(schema.contains("liked_by TEXT"))
        assertTrue(schema.contains("comments_json TEXT"))
        assertTrue(v1ToV2Migration.contains("CREATE TABLE IF NOT EXISTS moment_posts"))
    }

    @Test
    fun `sqlite schema stores contact profile edits and migrates existing databases`() {
        val schema = FloatingChatDatabaseContract.createStatements.joinToString("\n")
        val v2ToV3Migration = FloatingChatDatabaseContract.migrationStatements(
            oldVersion = 2,
            newVersion = 3
        ).joinToString("\n")

        assertEquals(4, FloatingChatDatabaseContract.databaseVersion)
        assertTrue(schema.contains("contact_profiles"))
        assertTrue(schema.contains("PRIMARY KEY(account_id, contact_id)"))
        assertTrue(schema.contains("remark TEXT"))
        assertTrue(schema.contains("tags TEXT"))
        assertTrue(schema.contains("memo TEXT"))
        assertTrue(schema.contains("friend_circle_visible INTEGER NOT NULL DEFAULT 1"))
        assertTrue(schema.contains("only_chat INTEGER NOT NULL DEFAULT 0"))
        assertTrue(schema.contains("phone TEXT"))
        assertTrue(schema.contains("source TEXT"))
        assertTrue(schema.contains("added_time TEXT"))
        assertTrue(schema.contains("common_group_count INTEGER"))
        assertTrue(v2ToV3Migration.contains("CREATE TABLE IF NOT EXISTS contact_profiles"))
    }

    @Test
    fun `sqlite schema stores group profile edits and migrates existing databases`() {
        val schema = FloatingChatDatabaseContract.createStatements.joinToString("\n")
        val v3ToV4Migration = FloatingChatDatabaseContract.migrationStatements(
            oldVersion = 3,
            newVersion = 4
        ).joinToString("\n")

        assertEquals(4, FloatingChatDatabaseContract.databaseVersion)
        assertTrue(schema.contains("group_profiles"))
        assertTrue(schema.contains("PRIMARY KEY(account_id, group_id)"))
        assertTrue(schema.contains("group_name TEXT"))
        assertTrue(schema.contains("announcement TEXT"))
        assertTrue(schema.contains("my_nickname TEXT"))
        assertTrue(schema.contains("show_member_avatars INTEGER NOT NULL DEFAULT 1"))
        assertTrue(schema.contains("show_member_nicknames INTEGER NOT NULL DEFAULT 1"))
        assertTrue(v3ToV4Migration.contains("CREATE TABLE IF NOT EXISTS group_profiles"))
    }
}
