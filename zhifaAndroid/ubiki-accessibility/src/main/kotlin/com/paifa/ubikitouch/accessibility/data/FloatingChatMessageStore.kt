package com.paifa.ubikitouch.accessibility.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.paifa.ubikitouch.core.model.FloatingChatAccessState
import com.paifa.ubikitouch.core.model.FloatingChatConnectionTarget
import com.paifa.ubikitouch.core.model.FloatingChatContactCardKind
import com.paifa.ubikitouch.core.model.FloatingChatFileFormat
import com.paifa.ubikitouch.core.model.FloatingChatInlineToken
import com.paifa.ubikitouch.core.model.FloatingChatInlineTokenType
import com.paifa.ubikitouch.core.model.FloatingChatMessage
import com.paifa.ubikitouch.core.model.FloatingChatMessageKind
import com.paifa.ubikitouch.core.model.FloatingChatMessagePresentation
import com.paifa.ubikitouch.core.model.FloatingChatMessageType
import com.paifa.ubikitouch.core.model.FloatingChatSendState
import com.paifa.ubikitouch.core.model.FloatingChatThumbnailOrientation
import com.paifa.ubikitouch.core.model.FloatingChatVisibilityScope
import java.util.concurrent.atomic.AtomicLong

internal class FloatingChatMessageStore(
    context: Context,
    private val clockMillis: () -> Long = System::currentTimeMillis
) : AutoCloseable {
    private val database = FloatingChatDatabase(context)
    private val writeCounter = AtomicLong(0L)

    override fun close() {
        database.close()
    }

    fun upsertThread(thread: LocalChatThread) {
        database.writableDatabase.insertWithOnConflict(
            FloatingChatDatabaseContract.tableThreads,
            null,
            thread.toContentValues(),
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun insertMessage(
        message: LocalChatMessage,
        attachments: List<LocalChatMessageAttachment> = emptyList()
    ) {
        database.writableDatabase.transaction {
            ensureThreadFor(message)
            insertWithOnConflict(
                FloatingChatDatabaseContract.tableMessages,
                null,
                message.toContentValues(),
                SQLiteDatabase.CONFLICT_REPLACE
            )
            attachments.forEach { attachment ->
                val fileId = upsertFileInTransaction(attachment.file)
                insertWithOnConflict(
                    FloatingChatDatabaseContract.tableMessageFiles,
                    null,
                    attachment.ref.copy(fileId = fileId).toContentValues(),
                    SQLiteDatabase.CONFLICT_IGNORE
                )
            }
        }
    }

    fun insertFloatingMessage(
        message: FloatingChatMessage,
        threadId: String = threadIdForMessage(message),
        createdAt: Long = nextCreatedAt()
    ) {
        val localMessage = message.toLocalChatMessage(threadId = threadId, createdAt = createdAt)
        val attachments = FloatingChatFileIndex.fileAttachmentsForMessage(message, createdAt)
        insertMessage(localMessage, attachments)
    }

    fun upsertFile(file: LocalChatFile): String {
        return database.writableDatabase.transactionResult {
            upsertFileInTransaction(file)
        }
    }

    fun forwardMessageWithFiles(
        sourceMessageId: String,
        targetThreadId: String,
        newMessageId: String,
        senderId: String?,
        senderName: String,
        createdAt: Long = nextCreatedAt()
    ) {
        database.writableDatabase.transaction {
            val source = messageByIdInTransaction(sourceMessageId)
                ?: error("Cannot forward missing message $sourceMessageId")
            ensureThreadFor(targetThreadId, kind = if (targetThreadId.startsWith("private:")) "private" else "group")
            val forwarded = source.copy(
                messageId = newMessageId,
                threadId = targetThreadId,
                senderId = senderId,
                senderName = senderName,
                createdAt = createdAt,
                displayTime = "刚刚",
                isFromMe = true,
                connectionTarget = FloatingChatConnectionTarget.Account.name,
                connectionTargetId = senderId,
                remoteMessageServerId = null,
                remoteTaskId = null,
                sendState = FloatingChatSendState.LocalOnly.toStorageValue(),
                sendErrorCode = null,
                sendErrorMessage = null,
                clientRequestId = null
            )
            insertWithOnConflict(
                FloatingChatDatabaseContract.tableMessages,
                null,
                forwarded.toContentValues(),
                SQLiteDatabase.CONFLICT_REPLACE
            )
            FloatingChatFileIndex.forwardedFileRefs(
                sourceRefs = messageFileRefsInTransaction(sourceMessageId),
                targetMessageId = newMessageId
            ).forEach { ref ->
                insertWithOnConflict(
                    FloatingChatDatabaseContract.tableMessageFiles,
                    null,
                    ref.toContentValues(),
                    SQLiteDatabase.CONFLICT_IGNORE
                )
            }
        }
    }

    fun messagesForThread(
        threadId: String,
        limit: Int = 80,
        beforeCreatedAt: Long? = null
    ): List<FloatingChatMessage> {
        val safeLimit = limit.coerceIn(1, 500)
        val where = if (beforeCreatedAt == null) {
            "thread_id = ?"
        } else {
            "thread_id = ? AND created_at < ?"
        }
        val args = if (beforeCreatedAt == null) {
            arrayOf(threadId)
        } else {
            arrayOf(threadId, beforeCreatedAt.toString())
        }
        return database.readableDatabase.query(
            FloatingChatDatabaseContract.tableMessages,
            null,
            where,
            args,
            null,
            null,
            "created_at DESC",
            safeLimit.toString()
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.toLocalChatMessage().toFloatingChatMessage())
                }
            }.asReversed()
        }
    }

    fun recentFloatingMessages(limit: Int = 500): List<FloatingChatMessage> {
        val safeLimit = limit.coerceIn(1, 500)
        return database.readableDatabase.query(
            FloatingChatDatabaseContract.tableMessages,
            null,
            null,
            null,
            null,
            null,
            "created_at DESC",
            safeLimit.toString()
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.toLocalChatMessage().toFloatingChatMessage())
                }
            }.asReversed()
        }
    }

    fun messageFileRefs(messageId: String): List<LocalChatMessageFileRef> {
        return database.readableDatabase.query(
            FloatingChatDatabaseContract.tableMessageFiles,
            null,
            "message_id = ?",
            arrayOf(messageId),
            null,
            null,
            "position ASC"
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.toLocalChatMessageFileRef())
                }
            }
        }
    }

    fun fileCount(): Int {
        return database.readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM ${FloatingChatDatabaseContract.tableFiles}",
            emptyArray()
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
    }

    fun upsertMomentPost(post: LocalMomentPost) {
        database.writableDatabase.insertWithOnConflict(
            FloatingChatDatabaseContract.tableMomentPosts,
            null,
            post.toContentValues(),
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun momentPosts(limit: Int = 120): List<LocalMomentPost> {
        val safeLimit = limit.coerceIn(1, 500)
        return database.readableDatabase.query(
            FloatingChatDatabaseContract.tableMomentPosts,
            null,
            null,
            null,
            null,
            null,
            "created_at DESC",
            safeLimit.toString()
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.toLocalMomentPost())
                }
            }
        }
    }

    fun upsertContactProfile(profile: LocalContactProfile) {
        database.writableDatabase.insertWithOnConflict(
            FloatingChatDatabaseContract.tableContactProfiles,
            null,
            profile.toContentValues(),
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun contactProfile(accountId: String, contactId: String): LocalContactProfile? {
        return database.readableDatabase.query(
            FloatingChatDatabaseContract.tableContactProfiles,
            null,
            "account_id = ? AND contact_id = ?",
            arrayOf(accountId, contactId),
            null,
            null,
            null,
            "1"
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.toLocalContactProfile() else null
        }
    }

    fun contactProfiles(): List<LocalContactProfile> {
        return database.readableDatabase.query(
            FloatingChatDatabaseContract.tableContactProfiles,
            null,
            null,
            null,
            null,
            null,
            "updated_at DESC"
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.toLocalContactProfile())
                }
            }
        }
    }

    fun upsertGroupProfile(profile: LocalGroupProfile) {
        database.writableDatabase.insertWithOnConflict(
            FloatingChatDatabaseContract.tableGroupProfiles,
            null,
            profile.toContentValues(),
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun groupProfiles(): List<LocalGroupProfile> {
        return database.readableDatabase.query(
            FloatingChatDatabaseContract.tableGroupProfiles,
            null,
            null,
            null,
            null,
            null,
            "updated_at DESC"
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.toLocalGroupProfile())
                }
            }
        }
    }

    private fun SQLiteDatabase.ensureThreadFor(message: LocalChatMessage) {
        ensureThreadFor(
            threadId = message.threadId,
            kind = if (message.threadId.startsWith("private:")) "private" else "group"
        )
    }

    private fun SQLiteDatabase.ensureThreadFor(threadId: String, kind: String) {
        val now = clockMillis()
        insertWithOnConflict(
            FloatingChatDatabaseContract.tableThreads,
            null,
            LocalChatThread(
                threadId = threadId,
                kind = kind,
                createdAt = now,
                updatedAt = now
            ).toContentValues(),
            SQLiteDatabase.CONFLICT_IGNORE
        )
    }

    private fun SQLiteDatabase.upsertFileInTransaction(file: LocalChatFile): String {
        insertWithOnConflict(
            FloatingChatDatabaseContract.tableFiles,
            null,
            file.toContentValues(),
            SQLiteDatabase.CONFLICT_IGNORE
        )
        return rawQuery(
            "SELECT file_id FROM ${FloatingChatDatabaseContract.tableFiles} WHERE content_key = ? LIMIT 1",
            arrayOf(file.contentKey)
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else file.fileId
        }
    }

    private fun SQLiteDatabase.messageByIdInTransaction(messageId: String): LocalChatMessage? {
        return query(
            FloatingChatDatabaseContract.tableMessages,
            null,
            "message_id = ?",
            arrayOf(messageId),
            null,
            null,
            null,
            "1"
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.toLocalChatMessage() else null
        }
    }

    private fun SQLiteDatabase.messageFileRefsInTransaction(
        messageId: String
    ): List<LocalChatMessageFileRef> {
        return query(
            FloatingChatDatabaseContract.tableMessageFiles,
            null,
            "message_id = ?",
            arrayOf(messageId),
            null,
            null,
            "position ASC"
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.toLocalChatMessageFileRef())
                }
            }
        }
    }

    private fun nextCreatedAt(): Long {
        return clockMillis() + writeCounter.incrementAndGet()
    }
}

internal fun threadIdForMessage(message: FloatingChatMessage): String {
    val threadContactId = message.threadContactId
    return when {
        threadContactId != null && !threadContactId.startsWith("group-") -> {
            "private:$threadContactId"
        }
        threadContactId != null -> "group:$threadContactId"
        message.connectionTarget == FloatingChatConnectionTarget.User && message.connectionTargetId != null -> {
            "private:${message.connectionTargetId}"
        }
        else -> "group:default"
    }
}

internal fun localThreadIdForSelection(
    groupId: String? = null,
    privateContactId: String? = null
): String {
    return when {
        privateContactId != null -> "private:$privateContactId"
        groupId != null -> "group:$groupId"
        else -> "group:default"
    }
}

internal fun FloatingChatMessage.toLocalChatMessage(
    threadId: String = threadIdForMessage(this),
    createdAt: Long
): LocalChatMessage {
    return LocalChatMessage(
        messageId = id,
        threadId = threadId,
        senderId = connectionTargetId,
        senderName = senderName,
        messageType = type.name,
        body = text,
        createdAt = createdAt,
        displayTime = time,
        isFromMe = fromMe,
        kind = kind.name,
        presentation = presentation.name,
        connectionTarget = connectionTarget.name,
        connectionTargetId = connectionTargetId,
        detail = detail,
        quoteAuthor = quoteAuthor,
        quoteText = quoteText,
        cardKind = cardKind?.name,
        cardName = cardName,
        cardSubtitle = cardSubtitle,
        appName = appName,
        locationTitle = locationTitle,
        locationAddress = locationAddress,
        resourceUrl = resourceUrl,
        fileName = fileName,
        fileFormat = fileFormat?.name,
        fileSizeLabel = fileSizeLabel,
        filePreviewLines = filePreviewLines.joinToString("\n"),
        visibility = visibility?.name,
        accessState = accessState?.name,
        thumbnailOrientation = thumbnailOrientation?.name,
        mediaAspectRatio = mediaAspectRatio,
        thumbnailUrl = thumbnailUrl,
        mediaDurationMs = mediaDurationMs,
        mediaMimeType = mediaMimeType,
        inlineTokens = inlineTokens.joinToString("\n") { token -> "${token.type.name}\t${token.text}" },
        remoteMessageServerId = remoteMessageServerId,
        remoteTaskId = remoteTaskId,
        sendState = sendState.toStorageValue(),
        sendErrorCode = sendErrorCode,
        sendErrorMessage = sendErrorMessage,
        clientRequestId = clientRequestId
    )
}

internal fun LocalChatMessage.toFloatingChatMessage(): FloatingChatMessage {
    return FloatingChatMessage(
        id = messageId,
        type = enumValueOrDefault(messageType, FloatingChatMessageType.Text),
        text = body,
        fromMe = isFromMe,
        senderName = senderName,
        time = displayTime ?: "",
        kind = enumValueOrDefault(kind, FloatingChatMessageKind.Normal),
        presentation = enumValueOrDefault(presentation, FloatingChatMessagePresentation.Bubble),
        connectionTarget = enumValueOrDefault(connectionTarget, FloatingChatConnectionTarget.None),
        connectionTargetId = connectionTargetId,
        threadContactId = threadId.removePrefix("private:").removePrefix("group:").takeIf {
            it.isNotBlank() && it != "default"
        },
        detail = detail,
        quoteAuthor = quoteAuthor,
        quoteText = quoteText,
        cardKind = cardKind?.let { enumValueOrNull<FloatingChatContactCardKind>(it) },
        cardName = cardName,
        cardSubtitle = cardSubtitle,
        appName = appName,
        locationTitle = locationTitle,
        locationAddress = locationAddress,
        resourceUrl = resourceUrl,
        fileName = fileName,
        fileFormat = fileFormat?.let { enumValueOrNull<FloatingChatFileFormat>(it) },
        fileSizeLabel = fileSizeLabel,
        filePreviewLines = filePreviewLines?.lines().orEmpty().filter { it.isNotBlank() },
        visibility = visibility?.let { enumValueOrNull<FloatingChatVisibilityScope>(it) },
        accessState = accessState?.let { enumValueOrNull<FloatingChatAccessState>(it) },
        thumbnailOrientation = thumbnailOrientation?.let { enumValueOrNull<FloatingChatThumbnailOrientation>(it) },
        mediaAspectRatio = mediaAspectRatio,
        thumbnailUrl = thumbnailUrl,
        mediaDurationMs = mediaDurationMs,
        mediaMimeType = mediaMimeType,
        inlineTokens = inlineTokens.toInlineTokens(),
        remoteMessageServerId = remoteMessageServerId,
        remoteTaskId = remoteTaskId,
        sendState = floatingChatSendStateFromStorage(sendState),
        sendErrorCode = sendErrorCode,
        sendErrorMessage = sendErrorMessage,
        clientRequestId = clientRequestId
    )
}

private fun String?.toInlineTokens(): List<FloatingChatInlineToken> {
    if (isNullOrBlank()) return emptyList()
    return lines().mapNotNull { line ->
        val parts = line.split('\t', limit = 2)
        val type = parts.getOrNull(0)?.let { enumValueOrNull<FloatingChatInlineTokenType>(it) }
        val text = parts.getOrNull(1)
        if (type != null && text != null) FloatingChatInlineToken(type, text) else null
    }
}

private inline fun <reified T : Enum<T>> enumValueOrNull(value: String): T? {
    return runCatching { enumValueOf<T>(value) }.getOrNull()
}

private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String, default: T): T {
    return enumValueOrNull<T>(value) ?: default
}

private fun FloatingChatSendState.toStorageValue(): String {
    return when (this) {
        FloatingChatSendState.LocalOnly -> "LOCAL_ONLY"
        FloatingChatSendState.Queued -> "QUEUED"
        FloatingChatSendState.Uploading -> "UPLOADING"
        FloatingChatSendState.Submitted -> "SUBMITTED"
        FloatingChatSendState.Processing -> "PROCESSING"
        FloatingChatSendState.Succeeded -> "SUCCEEDED"
        FloatingChatSendState.FailedRetryable -> "FAILED_RETRYABLE"
        FloatingChatSendState.FailedFinal -> "FAILED_FINAL"
        FloatingChatSendState.Unknown -> "UNKNOWN"
        FloatingChatSendState.Cancelled -> "CANCELLED"
    }
}

private fun floatingChatSendStateFromStorage(value: String): FloatingChatSendState {
    return when (value) {
        "LOCAL_ONLY", FloatingChatSendState.LocalOnly.name -> FloatingChatSendState.LocalOnly
        "QUEUED", FloatingChatSendState.Queued.name -> FloatingChatSendState.Queued
        "UPLOADING", FloatingChatSendState.Uploading.name -> FloatingChatSendState.Uploading
        "SUBMITTED", FloatingChatSendState.Submitted.name -> FloatingChatSendState.Submitted
        "PROCESSING", FloatingChatSendState.Processing.name -> FloatingChatSendState.Processing
        "SUCCEEDED", FloatingChatSendState.Succeeded.name -> FloatingChatSendState.Succeeded
        "FAILED_RETRYABLE", FloatingChatSendState.FailedRetryable.name -> {
            FloatingChatSendState.FailedRetryable
        }
        "FAILED_FINAL", FloatingChatSendState.FailedFinal.name -> FloatingChatSendState.FailedFinal
        "UNKNOWN", FloatingChatSendState.Unknown.name -> FloatingChatSendState.Unknown
        "CANCELLED", FloatingChatSendState.Cancelled.name -> FloatingChatSendState.Cancelled
        else -> FloatingChatSendState.LocalOnly
    }
}

internal fun LocalChatThread.toContentValues(): ContentValues {
    return ContentValues().apply {
        put("thread_id", threadId)
        put("kind", kind)
        put("title", title)
        put("remote_conversation_id", remoteConversationId)
        put("account_wechat_id", accountWeChatId)
        put("created_at", createdAt)
        put("updated_at", updatedAt)
    }
}

internal fun LocalChatMessage.toContentValues(): ContentValues {
    return ContentValues().apply {
        put("message_id", messageId)
        put("thread_id", threadId)
        put("sender_id", senderId)
        put("sender_name", senderName)
        put("message_type", messageType)
        put("body", body)
        put("created_at", createdAt)
        put("display_time", displayTime)
        put("is_from_me", if (isFromMe) 1 else 0)
        put("kind", kind)
        put("presentation", presentation)
        put("connection_target", connectionTarget)
        put("connection_target_id", connectionTargetId)
        put("detail", detail)
        put("quote_author", quoteAuthor)
        put("quote_text", quoteText)
        put("card_kind", cardKind)
        put("card_name", cardName)
        put("card_subtitle", cardSubtitle)
        put("app_name", appName)
        put("location_title", locationTitle)
        put("location_address", locationAddress)
        put("resource_url", resourceUrl)
        put("file_name", fileName)
        put("file_format", fileFormat)
        put("file_size_label", fileSizeLabel)
        put("file_preview_lines", filePreviewLines)
        put("visibility", visibility)
        put("access_state", accessState)
        put("thumbnail_orientation", thumbnailOrientation)
        mediaAspectRatio?.let { put("media_aspect_ratio", it) } ?: putNull("media_aspect_ratio")
        mediaDurationMs?.let { put("media_duration_ms", it) } ?: putNull("media_duration_ms")
        put("thumbnail_url", thumbnailUrl)
        put("media_mime_type", mediaMimeType)
        put("inline_tokens", inlineTokens)
        put("metadata_json", metadataJson)
        put("remote_msg_svr_id", remoteMessageServerId)
        remoteTaskId?.let { put("remote_task_id", it) } ?: putNull("remote_task_id")
        put("send_state", sendState)
        put("send_error_code", sendErrorCode)
        put("send_error_message", sendErrorMessage)
        put("client_request_id", clientRequestId)
    }
}

private fun LocalChatFile.toContentValues(): ContentValues {
    return ContentValues().apply {
        put("file_id", fileId)
        put("content_key", contentKey)
        put("uri", uri)
        put("preview_uri", previewUri)
        put("mime_type", mimeType)
        put("display_name", displayName)
        sizeBytes?.let { put("size_bytes", it) } ?: putNull("size_bytes")
        durationMs?.let { put("duration_ms", it) } ?: putNull("duration_ms")
        width?.let { put("width", it) } ?: putNull("width")
        height?.let { put("height", it) } ?: putNull("height")
        put("created_at", createdAt)
        put("metadata_json", metadataJson)
    }
}

private fun LocalChatMessageFileRef.toContentValues(): ContentValues {
    return ContentValues().apply {
        put("message_id", messageId)
        put("file_id", fileId)
        put("role", role)
        put("position", position)
    }
}

private fun LocalMomentPost.toContentValues(): ContentValues {
    return ContentValues().apply {
        put("post_id", postId)
        put("author", author)
        put("content", content)
        put("display_time", displayTime)
        put("avatar_text", avatarText)
        put("avatar_color", avatarColor)
        put("media_kind", mediaKind)
        put("media_uri", mediaUri)
        put("media_preview_uri", mediaPreviewUri)
        put("media_orientation", mediaOrientation)
        mediaAspectRatio?.let { put("media_aspect_ratio", it) } ?: putNull("media_aspect_ratio")
        mediaWidthDp?.let { put("media_width_dp", it) } ?: putNull("media_width_dp")
        mediaHeightDp?.let { put("media_height_dp", it) } ?: putNull("media_height_dp")
        mediaColor?.let { put("media_color", it) } ?: putNull("media_color")
        put("media_label", mediaLabel)
        put("link_title", linkTitle)
        put("source_label", sourceLabel)
        put("liked_by", encodeTextList(likedBy))
        put("comments_json", encodeMomentComments(comments))
        put("created_at", createdAt)
    }
}

private fun LocalContactProfile.toContentValues(): ContentValues {
    return ContentValues().apply {
        put("account_id", accountId)
        put("contact_id", contactId)
        put("remark", remark)
        put("tags", tags)
        put("memo", memo)
        put("friend_circle_visible", if (friendCircleVisible) 1 else 0)
        put("only_chat", if (onlyChat) 1 else 0)
        put("phone", phone)
        put("source", source)
        put("added_time", addedTime)
        commonGroupCount?.let { put("common_group_count", it) } ?: putNull("common_group_count")
        put("updated_at", updatedAt)
    }
}

private fun LocalGroupProfile.toContentValues(): ContentValues {
    return ContentValues().apply {
        put("account_id", accountId)
        put("group_id", groupId)
        put("group_name", groupName)
        put("remark", remark)
        put("announcement", announcement)
        put("my_nickname", myNickname)
        put("mute", if (mute) 1 else 0)
        put("pinned", if (pinned) 1 else 0)
        put("save_to_contacts", if (saveToContacts) 1 else 0)
        put("show_member_nicknames", if (showMemberNicknames) 1 else 0)
        put("show_member_avatars", if (showMemberAvatars) 1 else 0)
        put("background_label", backgroundLabel)
        put("updated_at", updatedAt)
    }
}

private fun Cursor.toLocalChatMessage(): LocalChatMessage {
    return LocalChatMessage(
        messageId = getString(columnIndex("message_id")),
        threadId = getString(columnIndex("thread_id")),
        senderId = getNullableString("sender_id"),
        senderName = getString(columnIndex("sender_name")),
        messageType = getString(columnIndex("message_type")),
        body = getString(columnIndex("body")),
        createdAt = getLong(columnIndex("created_at")),
        displayTime = getNullableString("display_time"),
        isFromMe = getInt(columnIndex("is_from_me")) == 1,
        kind = getString(columnIndex("kind")),
        presentation = getString(columnIndex("presentation")),
        connectionTarget = getString(columnIndex("connection_target")),
        connectionTargetId = getNullableString("connection_target_id"),
        detail = getNullableString("detail"),
        quoteAuthor = getNullableString("quote_author"),
        quoteText = getNullableString("quote_text"),
        cardKind = getNullableString("card_kind"),
        cardName = getNullableString("card_name"),
        cardSubtitle = getNullableString("card_subtitle"),
        appName = getNullableString("app_name"),
        locationTitle = getNullableString("location_title"),
        locationAddress = getNullableString("location_address"),
        resourceUrl = getNullableString("resource_url"),
        fileName = getNullableString("file_name"),
        fileFormat = getNullableString("file_format"),
        fileSizeLabel = getNullableString("file_size_label"),
        filePreviewLines = getNullableString("file_preview_lines"),
        visibility = getNullableString("visibility"),
        accessState = getNullableString("access_state"),
        thumbnailOrientation = getNullableString("thumbnail_orientation"),
        mediaAspectRatio = getNullableFloat("media_aspect_ratio"),
        thumbnailUrl = getNullableString("thumbnail_url"),
        mediaDurationMs = getNullableInt("media_duration_ms"),
        mediaMimeType = getNullableString("media_mime_type"),
        inlineTokens = getNullableString("inline_tokens"),
        metadataJson = getNullableString("metadata_json"),
        remoteMessageServerId = getNullableString("remote_msg_svr_id"),
        remoteTaskId = getNullableLong("remote_task_id"),
        sendState = getNullableString("send_state") ?: FloatingChatSendState.LocalOnly.toStorageValue(),
        sendErrorCode = getNullableString("send_error_code"),
        sendErrorMessage = getNullableString("send_error_message"),
        clientRequestId = getNullableString("client_request_id")
    )
}

private fun Cursor.toLocalChatMessageFileRef(): LocalChatMessageFileRef {
    return LocalChatMessageFileRef(
        messageId = getString(columnIndex("message_id")),
        fileId = getString(columnIndex("file_id")),
        role = getString(columnIndex("role")),
        position = getInt(columnIndex("position"))
    )
}

private fun Cursor.toLocalMomentPost(): LocalMomentPost {
    return LocalMomentPost(
        postId = getString(columnIndex("post_id")),
        author = getString(columnIndex("author")),
        content = getString(columnIndex("content")),
        displayTime = getString(columnIndex("display_time")),
        avatarText = getString(columnIndex("avatar_text")),
        avatarColor = getLong(columnIndex("avatar_color")),
        mediaKind = getNullableString("media_kind"),
        mediaUri = getNullableString("media_uri"),
        mediaPreviewUri = getNullableString("media_preview_uri"),
        mediaOrientation = getNullableString("media_orientation"),
        mediaAspectRatio = getNullableFloat("media_aspect_ratio"),
        mediaWidthDp = getNullableInt("media_width_dp"),
        mediaHeightDp = getNullableInt("media_height_dp"),
        mediaColor = getNullableLong("media_color"),
        mediaLabel = getNullableString("media_label"),
        linkTitle = getNullableString("link_title"),
        sourceLabel = getNullableString("source_label"),
        likedBy = decodeTextList(getNullableString("liked_by")),
        comments = decodeMomentComments(getNullableString("comments_json")),
        createdAt = getLong(columnIndex("created_at"))
    )
}

private fun Cursor.toLocalContactProfile(): LocalContactProfile {
    return LocalContactProfile(
        accountId = getString(columnIndex("account_id")),
        contactId = getString(columnIndex("contact_id")),
        remark = getNullableString("remark").orEmpty(),
        tags = getNullableString("tags").orEmpty(),
        memo = getNullableString("memo").orEmpty(),
        friendCircleVisible = getInt(columnIndex("friend_circle_visible")) == 1,
        onlyChat = getInt(columnIndex("only_chat")) == 1,
        phone = getNullableString("phone"),
        source = getNullableString("source"),
        addedTime = getNullableString("added_time"),
        commonGroupCount = getNullableInt("common_group_count"),
        updatedAt = getLong(columnIndex("updated_at"))
    )
}

private fun Cursor.toLocalGroupProfile(): LocalGroupProfile {
    return LocalGroupProfile(
        accountId = getString(columnIndex("account_id")),
        groupId = getString(columnIndex("group_id")),
        groupName = getNullableString("group_name").orEmpty(),
        remark = getNullableString("remark").orEmpty(),
        announcement = getNullableString("announcement").orEmpty(),
        myNickname = getNullableString("my_nickname").orEmpty(),
        mute = getInt(columnIndex("mute")) == 1,
        pinned = getInt(columnIndex("pinned")) == 1,
        saveToContacts = getInt(columnIndex("save_to_contacts")) == 1,
        showMemberNicknames = getInt(columnIndex("show_member_nicknames")) == 1,
        showMemberAvatars = getInt(columnIndex("show_member_avatars")) == 1,
        backgroundLabel = getNullableString("background_label").orEmpty(),
        updatedAt = getLong(columnIndex("updated_at"))
    )
}

private fun Cursor.getNullableString(name: String): String? {
    val index = columnIndex(name)
    return if (isNull(index)) null else getString(index)
}

private fun Cursor.getNullableInt(name: String): Int? {
    val index = columnIndex(name)
    return if (isNull(index)) null else getInt(index)
}

private fun Cursor.getNullableFloat(name: String): Float? {
    val index = columnIndex(name)
    return if (isNull(index)) null else getFloat(index)
}

private fun Cursor.getNullableLong(name: String): Long? {
    val index = columnIndex(name)
    return if (isNull(index)) null else getLong(index)
}

private fun Cursor.columnIndex(name: String): Int {
    return getColumnIndexOrThrow(name)
}

private fun encodeTextList(values: List<String>): String {
    return values.joinToString("\n") { value -> escapePayload(value) }
}

private fun decodeTextList(payload: String?): List<String> {
    if (payload.isNullOrEmpty()) return emptyList()
    return payload.lines()
        .map { line -> unescapePayload(line) }
        .filter { value -> value.isNotBlank() }
}

private fun encodeMomentComments(comments: List<LocalMomentComment>): String {
    return comments.joinToString("\n") { comment ->
        "${escapePayload(comment.author)}\t${escapePayload(comment.text)}"
    }
}

private fun decodeMomentComments(payload: String?): List<LocalMomentComment> {
    if (payload.isNullOrEmpty()) return emptyList()
    return payload.lines().mapNotNull { line ->
        val parts = line.split('\t', limit = 2)
        val author = parts.getOrNull(0)?.let(::unescapePayload)
        val text = parts.getOrNull(1)?.let(::unescapePayload)
        if (author.isNullOrBlank() || text.isNullOrBlank()) null else LocalMomentComment(author, text)
    }
}

private fun escapePayload(value: String): String {
    return value
        .replace("\\", "\\\\")
        .replace("\n", "\\n")
        .replace("\t", "\\t")
}

private fun unescapePayload(value: String): String {
    val builder = StringBuilder(value.length)
    var escaping = false
    value.forEach { char ->
        if (escaping) {
            builder.append(
                when (char) {
                    'n' -> '\n'
                    't' -> '\t'
                    else -> char
                }
            )
            escaping = false
        } else if (char == '\\') {
            escaping = true
        } else {
            builder.append(char)
        }
    }
    if (escaping) builder.append('\\')
    return builder.toString()
}

private inline fun SQLiteDatabase.transaction(block: SQLiteDatabase.() -> Unit) {
    beginTransaction()
    try {
        block()
        setTransactionSuccessful()
    } finally {
        endTransaction()
    }
}

private inline fun <T> SQLiteDatabase.transactionResult(block: SQLiteDatabase.() -> T): T {
    beginTransaction()
    try {
        val result = block()
        setTransactionSuccessful()
        return result
    } finally {
        endTransaction()
    }
}
