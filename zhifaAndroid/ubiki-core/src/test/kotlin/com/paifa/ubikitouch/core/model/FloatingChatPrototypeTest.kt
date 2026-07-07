package com.paifa.ubikitouch.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingChatPrototypeTest {
    @Test
    fun sampleConversationHasRequiredStaticContent() {
        val conversation = FloatingChatPrototype.sampleConversation()

        assertEquals("星河产品小组", conversation.peerName)
        assertEquals("林舟", conversation.accountName)
        assertTrue(conversation.groupContacts.size >= 4)
        assertTrue(conversation.contacts.size >= 10)
        assertTrue(conversation.accountContacts.size >= 9)
        assertTrue(conversation.messages.size >= 8)
        assertTrue(conversation.messages.any { it.text.contains("坐标") })
        assertTrue(conversation.messages.any { it.fromMe })
        assertTrue(conversation.messages.any { !it.fromMe })
        assertTrue(conversation.messages.any { it.kind == FloatingChatMessageKind.AiDraft })
        assertTrue(conversation.messages.any { it.kind == FloatingChatMessageKind.System })
        assertTrue(conversation.messages.any { it.quoteText != null })
        assertTrue(conversation.toolActions.contains(FloatingChatToolAction.Assistant))
        assertTrue(conversation.toolActions.size >= 16)
    }

    @Test
    fun contactsUseRealisticProfilesInsteadOfPlaceholders() {
        val conversation = FloatingChatPrototype.sampleConversation()
        val allContacts = conversation.contacts + conversation.accountContacts

        assertTrue(allContacts.none { it.name.contains("Test", ignoreCase = true) })
        assertTrue(allContacts.none { it.name.contains("account", ignoreCase = true) })
        assertTrue(allContacts.all { it.description.isNotBlank() })
        assertTrue(allContacts.all { it.avatarColor != 0L })
        assertTrue(allContacts.all { it.initials.length in 1..2 })
        assertTrue(conversation.contacts.any { it.name == "沈嘉木" && it.description.contains("前端") })
        assertTrue(conversation.accountContacts.any { it.name == "企业微信号" && it.description.contains("客户") })
    }

    @Test
    fun sampleConversationHasMultipleGroupThreadsAndMoreContacts() {
        val conversation = FloatingChatPrototype.sampleConversation()

        assertEquals(
            listOf("group-product", "group-ops", "group-ai", "group-camping"),
            conversation.groupContacts.map { it.id }
        )
        assertTrue(conversation.contacts.map { it.id }.containsAll(
            listOf("li-si", "wang-wu", "zhao-liu", "xiao-chen", "sun-lin", "he-miao", "qian-yue", "gu-yan", "luo-bei", "assistant")
        ))
        conversation.groupContacts.forEach { group ->
            assertTrue(group.name.endsWith("群"))
            assertTrue(group.description.isNotBlank())
        }
    }

    @Test
    fun sampleConversationMessagesAreRealisticInsteadOfPrototypeCopy() {
        val conversation = FloatingChatPrototype.sampleConversation()
        val bannedWords = listOf("演示", "调试", "消息类型", "气泡类型", "模拟", "这条私聊记录", "保留这条气泡")
        val searchableText = conversation.messages.flatMap { message ->
            listOfNotNull(
                message.text,
                message.detail,
                message.quoteText,
                message.cardName,
                message.cardSubtitle,
                message.appName,
                message.locationTitle,
                message.locationAddress,
                message.fileName
            ) + message.filePreviewLines
        }

        bannedWords.forEach { banned ->
            assertTrue(banned, searchableText.none { text -> text.contains(banned) })
        }
    }

    @Test
    fun sampleConversationContainsOneExampleForEverySupportedMessageType() {
        val conversation = FloatingChatPrototype.sampleConversation()

        assertEquals(
            setOf(
                FloatingChatMessageType.Location,
                FloatingChatMessageType.ContactLink,
                FloatingChatMessageType.MiniProgramLink,
                FloatingChatMessageType.Text,
                FloatingChatMessageType.MixedText,
                FloatingChatMessageType.Quote,
                FloatingChatMessageType.FilePreview,
                FloatingChatMessageType.ImageThumbnail,
                FloatingChatMessageType.VideoPreview,
                FloatingChatMessageType.Voice,
                FloatingChatMessageType.InlineContact,
                FloatingChatMessageType.InlineLocation
            ),
            FloatingChatMessageType.entries.toSet()
        )
        assertEquals(
            FloatingChatMessageType.entries.toSet(),
            conversation.messages.map { it.type }.toSet()
        )
        assertEquals(12, FloatingChatMessageType.entries.size)
        assertTrue(conversation.messages.size >= FloatingChatMessageType.entries.size)
    }

    @Test
    fun sampleConversationCoversNewMessagePayloads() {
        val conversation = FloatingChatPrototype.sampleConversation()

        assertEquals(
            FloatingChatContactCardKind.entries.toSet(),
            conversation.messages.mapNotNull { it.cardKind }.toSet()
        )
        assertEquals(
            FloatingChatFileFormat.entries.toSet(),
            conversation.messages.mapNotNull { it.fileFormat }.toSet()
        )
        assertEquals(
            FloatingChatVisibilityScope.entries.toSet(),
            conversation.messages.mapNotNull { it.visibility }.toSet()
        )
        assertEquals(
            FloatingChatAccessState.entries.toSet(),
            conversation.messages.mapNotNull { it.accessState }.toSet()
        )
        assertEquals(
            FloatingChatThumbnailOrientation.entries.toSet(),
            conversation.messages.mapNotNull { it.thumbnailOrientation }.toSet()
        )

        val mixed = conversation.messages.first { it.type == FloatingChatMessageType.MixedText }
        val mixedTokenTypes = mixed.inlineTokens.map { it.type }.toSet()
        assertTrue(mixed.inlineTokens.any {
            it.type == FloatingChatInlineTokenType.PaidianLink &&
                it.text.startsWith("[") &&
                it.text.endsWith("]")
        })
        assertTrue(mixed.inlineTokens.any {
            it.type == FloatingChatInlineTokenType.FileLink &&
                (it.text.startsWith("@") || it.text.startsWith("#"))
        })
        assertTrue(mixedTokenTypes.contains(FloatingChatInlineTokenType.Url))
        assertTrue(mixedTokenTypes.contains(FloatingChatInlineTokenType.Mention))
        assertTrue(mixedTokenTypes.contains(FloatingChatInlineTokenType.Ai))
        assertTrue(mixedTokenTypes.contains(FloatingChatInlineTokenType.ImageName))

        assertTrue(conversation.messages.filter { it.type == FloatingChatMessageType.FilePreview }.all {
            it.fileName != null &&
                it.filePreviewLines.isNotEmpty() &&
                it.resourceUrl?.startsWith("https://") == true
        })
        assertTrue(conversation.messages.filter { it.type == FloatingChatMessageType.ImageThumbnail }.all {
            it.thumbnailUrl?.startsWith("https://") == true
        })
        assertTrue(conversation.messages.filter { it.type == FloatingChatMessageType.VideoPreview }.all {
            it.thumbnailUrl?.startsWith("https://") == true &&
                it.resourceUrl?.startsWith("https://") == true
        })
        assertTrue(conversation.messages.any {
            it.type == FloatingChatMessageType.InlineContact &&
                it.cardName != null &&
                it.text.startsWith("推名片:")
        })
        assertTrue(conversation.messages.any {
            it.type == FloatingChatMessageType.InlineLocation &&
            it.locationTitle != null
        })
    }

    @Test
    fun privateThreadLatestIncomingMessagesAreVariedForHomeOverview() {
        val conversation = FloatingChatPrototype.sampleConversation()
        val latestIncomingTypes = conversation.contacts.map { contact ->
            val accountId = FloatingChatPrototype.pairedAccountFor(conversation, contact.id).id
            FloatingChatPrototype.privateMessagesFor(conversation, contact.id, accountId)
                .filter { message -> !message.fromMe }
                .last()
                .type
        }

        assertTrue(latestIncomingTypes.toSet().size >= 5)
        assertTrue(latestIncomingTypes.count { it == FloatingChatMessageType.InlineLocation } <= 1)
        assertTrue(latestIncomingTypes.contains(FloatingChatMessageType.Location))
    }

    @Test
    fun pickedImageMessageUsesStandaloneMediaPresentation() {
        val conversation = FloatingChatPrototype.sampleConversation()
        val sentMessage = FloatingChatPrototype.simulatedPickedMediaMessage(
            conversation = conversation,
            mediaKind = FloatingChatPrototype.PickedMediaKind.Image,
            mediaUri = "content://media/external/images/media/42",
            previewUri = "file:///data/user/0/com.paifa.ubikitouch/cache/floating-chat/image.jpg",
            selection = FloatingChatPrototype.ToolThreadSelection.Group,
            accountId = conversation.accountContacts.first().id,
            sequence = 3
        )

        assertEquals(FloatingChatMessageType.ImageThumbnail, sentMessage.type)
        assertEquals(FloatingChatMessagePresentation.MediaStandalone, sentMessage.presentation)
        assertTrue(sentMessage.text.isBlank())
    }

    @Test
    fun pickedVideoMessageUsesStandaloneMediaPresentation() {
        val conversation = FloatingChatPrototype.sampleConversation()
        val sentMessage = FloatingChatPrototype.simulatedPickedMediaMessage(
            conversation = conversation,
            mediaKind = FloatingChatPrototype.PickedMediaKind.Video,
            mediaUri = "content://media/external/video/media/77",
            previewUri = "file:///data/user/0/com.paifa.ubikitouch/cache/floating-chat/video.jpg",
            selection = FloatingChatPrototype.ToolThreadSelection.Group,
            accountId = conversation.accountContacts.first().id,
            sequence = 4
        )

        assertEquals(FloatingChatMessageType.VideoPreview, sentMessage.type)
        assertEquals(FloatingChatMessagePresentation.MediaStandalone, sentMessage.presentation)
        assertTrue(sentMessage.text.isBlank())
    }

    @Test
    fun outgoingVoiceMessageKeepsLocalAudioUriAndDuration() {
        val conversation = FloatingChatPrototype.sampleConversation()
        val sentMessage = FloatingChatPrototype.simulatedOutgoingGroupVoiceMessage(
            conversation = conversation,
            accountId = conversation.accountContacts.first().id,
            audioUri = "file:///data/user/0/com.paifa.ubikitouch/cache/floating-chat-voice/voice.m4a",
            durationMs = 2600,
            sequence = 5
        )

        assertEquals(FloatingChatMessageType.Voice, sentMessage.type)
        assertEquals(FloatingChatMessagePresentation.Bubble, sentMessage.presentation)
        assertEquals("0:03", sentMessage.detail)
        assertEquals("file:///data/user/0/com.paifa.ubikitouch/cache/floating-chat-voice/voice.m4a", sentMessage.resourceUrl)
        assertTrue(sentMessage.text.contains("语音"))
    }

    @Test
    fun mixedImageExamplesKeepBubblePresentation() {
        val conversation = FloatingChatPrototype.sampleConversation()
        val imageMessages = conversation.messages.filter { it.type == FloatingChatMessageType.ImageThumbnail }

        assertTrue(imageMessages.isNotEmpty())
        assertTrue(imageMessages.all { it.presentation == FloatingChatMessagePresentation.Bubble })
        assertTrue(imageMessages.all { it.text.isNotBlank() })
    }

    @Test
    fun sampleConversationUsesApprovedConnectionTargets() {
        val conversation = FloatingChatPrototype.sampleConversation()

        conversation.messages.forEach { message ->
            val expectedTarget = when {
                message.presentation == FloatingChatMessagePresentation.System -> FloatingChatConnectionTarget.None
                message.fromMe -> FloatingChatConnectionTarget.Account
                else -> FloatingChatConnectionTarget.User
            }

            assertEquals(message.id, expectedTarget, message.connectionTarget)
        }
    }

    @Test
    fun connectedMessagesHaveKnownOwners() {
        val conversation = FloatingChatPrototype.sampleConversation()
        val connectedMessages = conversation.messages.filter {
            it.connectionTarget != FloatingChatConnectionTarget.None
        }
        val userTargetIds = connectedMessages
            .filter { it.connectionTarget == FloatingChatConnectionTarget.User }
            .mapNotNull { it.connectionTargetId }
            .toSet()
        val accountTargetIds = connectedMessages
            .filter { it.connectionTarget == FloatingChatConnectionTarget.Account }
            .mapNotNull { it.connectionTargetId }
            .toSet()

        assertTrue(userTargetIds.containsAll(conversation.contacts.map { it.id }))
        assertTrue(conversation.accountContacts.map { it.id }.containsAll(accountTargetIds))
    }

    @Test
    fun everyContactHasPrivateThreadWithPairedAccount() {
        val conversation = FloatingChatPrototype.sampleConversation()

        conversation.contacts.forEach { contact ->
            val account = FloatingChatPrototype.pairedAccountFor(conversation, contact.id)
            val messages = FloatingChatPrototype.privateMessagesFor(
                conversation = conversation,
                contactId = contact.id,
                accountId = account.id
            )

            assertTrue(contact.id, messages.size >= 6)
            assertTrue(
                contact.id,
                messages.count {
                    it.presentation == FloatingChatMessagePresentation.Bubble &&
                        it.connectionTarget == FloatingChatConnectionTarget.User &&
                        it.connectionTargetId == contact.id
                } >= 3
            )
            assertTrue(
                account.id,
                messages.count {
                    it.presentation == FloatingChatMessagePresentation.Bubble &&
                        it.connectionTarget == FloatingChatConnectionTarget.Account &&
                        it.connectionTargetId == account.id
                } >= 3
            )
            assertTrue(messages.all {
                when (it.connectionTarget) {
                    FloatingChatConnectionTarget.User -> it.connectionTargetId == contact.id
                    FloatingChatConnectionTarget.Account -> it.connectionTargetId == account.id
                    FloatingChatConnectionTarget.None -> true
                }
            })
        }
    }

    @Test
    fun privateThreadDoesNotIncludeMessagesFromOtherContactsOnTheSameAccount() {
        val conversation = FloatingChatPrototype.sampleConversation()
        val liSiAccount = FloatingChatPrototype.pairedAccountFor(conversation, "li-si")
        val xiaoChenAccount = FloatingChatPrototype.pairedAccountFor(conversation, "xiao-chen")
        assertEquals("account-work", liSiAccount.id)
        assertEquals("account-main", xiaoChenAccount.id)

        val liSiMessages = FloatingChatPrototype.privateMessagesFor(
            conversation = conversation,
            contactId = "li-si",
            accountId = liSiAccount.id
        )

        assertTrue(liSiMessages.all { it.threadContactId == "li-si" })
        assertTrue(liSiMessages.none {
            it.connectionTargetId == "xiao-chen" || it.quoteAuthor == "陈若川"
        })
    }

    @Test
    fun groupMessagesUseOnlySharedConversationEntries() {
        val conversation = FloatingChatPrototype.sampleConversation()

        val groupMessages = FloatingChatPrototype.groupMessagesFor(conversation)

        assertTrue(groupMessages.isNotEmpty())
        assertTrue(groupMessages.all { it.threadContactId == null })
        assertTrue(groupMessages.any {
            it.connectionTarget == FloatingChatConnectionTarget.User &&
                it.connectionTargetId == "li-si"
        })
        assertTrue(groupMessages.any {
            it.connectionTarget == FloatingChatConnectionTarget.User &&
                it.connectionTargetId == "xiao-chen"
        })
        assertTrue(groupMessages.any { it.connectionTarget == FloatingChatConnectionTarget.Account })
        assertTrue(groupMessages.any { it.connectionTarget == FloatingChatConnectionTarget.None })
        assertTrue(groupMessages.none { it.id == "m101" })
    }

    @Test
    fun namedGroupMessagesStaySeparatedByGroupThread() {
        val conversation = FloatingChatPrototype.sampleConversation()

        val productMessages = FloatingChatPrototype.groupMessagesFor(conversation, "group-product")
        val opsMessages = FloatingChatPrototype.groupMessagesFor(conversation, "group-ops")
        val aiMessages = FloatingChatPrototype.groupMessagesFor(conversation, "group-ai")

        assertTrue(productMessages.isNotEmpty())
        assertTrue(productMessages.all { it.threadContactId == null })
        assertTrue(opsMessages.isNotEmpty())
        assertTrue(opsMessages.all { it.threadContactId == "group-ops" })
        assertTrue(aiMessages.isNotEmpty())
        assertTrue(aiMessages.all { it.threadContactId == "group-ai" })
        assertTrue(opsMessages.none { ops -> aiMessages.any { ai -> ai.id == ops.id } })
    }

    @Test
    fun simulatedOutgoingTextMessageTargetsOnlySelectedPrivateThread() {
        val conversation = FloatingChatPrototype.sampleConversation()
        val liSiAccount = FloatingChatPrototype.pairedAccountFor(conversation, "li-si")
        val sentMessage = FloatingChatPrototype.simulatedOutgoingTextMessage(
            conversation = conversation,
            contactId = "li-si",
            accountId = liSiAccount.id,
            text = "  收到，今晚我把交互状态补齐。  ",
            sequence = 7
        )
        val updatedConversation = conversation.copy(messages = conversation.messages + sentMessage)

        val liSiMessages = FloatingChatPrototype.privateMessagesFor(
            conversation = updatedConversation,
            contactId = "li-si",
            accountId = liSiAccount.id
        )
        val xiaoChenMessages = FloatingChatPrototype.privateMessagesFor(
            conversation = updatedConversation,
            contactId = "xiao-chen",
            accountId = FloatingChatPrototype.pairedAccountFor(conversation, "xiao-chen").id
        )

        assertEquals("local-li-si-${liSiAccount.id}-7", sentMessage.id)
        assertEquals(FloatingChatMessageType.Text, sentMessage.type)
        assertEquals("收到，今晚我把交互状态补齐。", sentMessage.text)
        assertTrue(sentMessage.fromMe)
        assertEquals(liSiAccount.name, sentMessage.senderName)
        assertEquals(FloatingChatConnectionTarget.Account, sentMessage.connectionTarget)
        assertEquals(liSiAccount.id, sentMessage.connectionTargetId)
        assertEquals("li-si", sentMessage.threadContactId)
        assertTrue(liSiMessages.any { it.id == sentMessage.id })
        assertTrue(xiaoChenMessages.none { it.id == sentMessage.id })
    }

    @Test
    fun simulatedOutgoingGroupTextMessageStaysInSelectedGroupConversation() {
        val conversation = FloatingChatPrototype.sampleConversation()
        val account = FloatingChatPrototype.pairedAccountFor(conversation, "group-ops")
        val sentMessage = FloatingChatPrototype.simulatedOutgoingGroupTextMessage(
            conversation = conversation,
            accountId = account.id,
            text = "  今晚会员日我这边确认可以上线。  ",
            sequence = 9,
            groupId = "group-ops"
        )
        val updatedConversation = conversation.copy(messages = conversation.messages + sentMessage)

        val opsMessages = FloatingChatPrototype.groupMessagesFor(updatedConversation, "group-ops")
        val productMessages = FloatingChatPrototype.groupMessagesFor(updatedConversation, "group-product")
        val privateMessages = FloatingChatPrototype.privateMessagesFor(
            conversation = updatedConversation,
            contactId = "li-si",
            accountId = account.id
        )

        assertEquals("local-group-ops-${account.id}-9", sentMessage.id)
        assertEquals(FloatingChatMessageType.Text, sentMessage.type)
        assertEquals("今晚会员日我这边确认可以上线。", sentMessage.text)
        assertTrue(sentMessage.fromMe)
        assertEquals(account.name, sentMessage.senderName)
        assertEquals(FloatingChatConnectionTarget.Account, sentMessage.connectionTarget)
        assertEquals(account.id, sentMessage.connectionTargetId)
        assertEquals("group-ops", sentMessage.threadContactId)
        assertTrue(opsMessages.any { it.id == sentMessage.id })
        assertTrue(productMessages.none { it.id == sentMessage.id })
        assertTrue(privateMessages.none { it.id == sentMessage.id })
    }

    @Test
    fun simulatedToolMessagesUseExpectedMessageTypes() {
        val conversation = FloatingChatPrototype.sampleConversation()
        val account = FloatingChatPrototype.pairedAccountFor(conversation, "group-product")

        val assistant = FloatingChatPrototype.simulatedToolMessage(
            conversation = conversation,
            action = FloatingChatToolAction.Assistant,
            selection = FloatingChatPrototype.ToolThreadSelection.GroupChat("group-product"),
            accountId = account.id,
            sequence = 1
        )
        val image = FloatingChatPrototype.simulatedToolMessage(
            conversation = conversation,
            action = FloatingChatToolAction.Gallery,
            selection = FloatingChatPrototype.ToolThreadSelection.Private("li-si"),
            accountId = account.id,
            sequence = 2
        )
        val video = FloatingChatPrototype.simulatedToolMessage(
            conversation = conversation,
            action = FloatingChatToolAction.Voice,
            selection = FloatingChatPrototype.ToolThreadSelection.GroupChat("group-product"),
            accountId = account.id,
            sequence = 3
        )

        assertEquals(FloatingChatMessageType.MixedText, assistant.type)
        assertEquals(FloatingChatMessageKind.AiDraft, assistant.kind)
        assertEquals("group-product", assistant.threadContactId)
        assertTrue(assistant.inlineTokens.any { it.type == FloatingChatInlineTokenType.Ai })

        assertEquals(FloatingChatMessageType.ImageThumbnail, image.type)
        assertEquals("li-si", image.threadContactId)
        assertEquals(FloatingChatThumbnailOrientation.Vertical, image.thumbnailOrientation)
        assertTrue(image.thumbnailUrl?.startsWith("https://") == true)

        assertEquals(FloatingChatMessageType.VideoPreview, video.type)
        assertEquals("group-product", video.threadContactId)
        assertEquals(FloatingChatThumbnailOrientation.Horizontal, video.thumbnailOrientation)
        assertTrue(video.resourceUrl?.endsWith(".mp4") == true)
    }

    @Test
    fun simulatedPickedMediaMessagesKeepRealContentUris() {
        val conversation = FloatingChatPrototype.sampleConversation()
        val account = FloatingChatPrototype.pairedAccountFor(conversation, "group-product")

        val image = FloatingChatPrototype.simulatedPickedMediaMessage(
            conversation = conversation,
            mediaKind = FloatingChatPrototype.PickedMediaKind.Image,
            mediaUri = "content://media/external/images/media/42",
            selection = FloatingChatPrototype.ToolThreadSelection.Private("li-si"),
            accountId = account.id,
            sequence = 4
        )
        val video = FloatingChatPrototype.simulatedPickedMediaMessage(
            conversation = conversation,
            mediaKind = FloatingChatPrototype.PickedMediaKind.Video,
            mediaUri = "content://media/external/video/media/77",
            selection = FloatingChatPrototype.ToolThreadSelection.GroupChat("group-product"),
            accountId = account.id,
            sequence = 5
        )

        assertEquals(FloatingChatMessageType.ImageThumbnail, image.type)
        assertEquals("content://media/external/images/media/42", image.thumbnailUrl)
        assertEquals("content://media/external/images/media/42", image.resourceUrl)
        assertEquals("li-si", image.threadContactId)

        assertEquals(FloatingChatMessageType.VideoPreview, video.type)
        assertEquals("content://media/external/video/media/77", video.thumbnailUrl)
        assertEquals("content://media/external/video/media/77", video.resourceUrl)
        assertEquals("group-product", video.threadContactId)
    }

    @Test
    fun simulatedPickedMediaMessagesCanUseSeparatePreviewUris() {
        val conversation = FloatingChatPrototype.sampleConversation()
        val account = FloatingChatPrototype.pairedAccountFor(conversation, "group-product")

        val image = FloatingChatPrototype.simulatedPickedMediaMessage(
            conversation = conversation,
            mediaKind = FloatingChatPrototype.PickedMediaKind.Image,
            mediaUri = "content://media/external/images/media/42",
            previewUri = "file:///data/user/0/com.paifa.ubikitouch/cache/floating-chat/image-42.jpg",
            selection = FloatingChatPrototype.ToolThreadSelection.Private("li-si"),
            accountId = account.id,
            sequence = 6
        )
        val video = FloatingChatPrototype.simulatedPickedMediaMessage(
            conversation = conversation,
            mediaKind = FloatingChatPrototype.PickedMediaKind.Video,
            mediaUri = "content://media/external/video/media/77",
            previewUri = "file:///data/user/0/com.paifa.ubikitouch/cache/floating-chat/video-77.jpg",
            selection = FloatingChatPrototype.ToolThreadSelection.GroupChat("group-product"),
            accountId = account.id,
            sequence = 7
        )

        assertEquals("file:///data/user/0/com.paifa.ubikitouch/cache/floating-chat/image-42.jpg", image.thumbnailUrl)
        assertEquals("content://media/external/images/media/42", image.resourceUrl)
        assertEquals("file:///data/user/0/com.paifa.ubikitouch/cache/floating-chat/video-77.jpg", video.thumbnailUrl)
        assertEquals("content://media/external/video/media/77", video.resourceUrl)
    }

    @Test
    fun simulatedPickedMediaMessagesKeepProvidedOrientation() {
        val conversation = FloatingChatPrototype.sampleConversation()
        val account = FloatingChatPrototype.pairedAccountFor(conversation, "group-product")

        val verticalImage = FloatingChatPrototype.simulatedPickedMediaMessage(
            conversation = conversation,
            mediaKind = FloatingChatPrototype.PickedMediaKind.Image,
            mediaUri = "content://media/external/images/media/42",
            selection = FloatingChatPrototype.ToolThreadSelection.GroupChat("group-product"),
            accountId = account.id,
            sequence = 8,
            orientation = FloatingChatThumbnailOrientation.Vertical
        )
        val horizontalImage = FloatingChatPrototype.simulatedPickedMediaMessage(
            conversation = conversation,
            mediaKind = FloatingChatPrototype.PickedMediaKind.Image,
            mediaUri = "content://media/external/images/media/43",
            selection = FloatingChatPrototype.ToolThreadSelection.GroupChat("group-product"),
            accountId = account.id,
            sequence = 9,
            orientation = FloatingChatThumbnailOrientation.Horizontal
        )
        val verticalVideo = FloatingChatPrototype.simulatedPickedMediaMessage(
            conversation = conversation,
            mediaKind = FloatingChatPrototype.PickedMediaKind.Video,
            mediaUri = "content://media/external/video/media/77",
            selection = FloatingChatPrototype.ToolThreadSelection.GroupChat("group-product"),
            accountId = account.id,
            sequence = 10,
            orientation = FloatingChatThumbnailOrientation.Vertical
        )

        assertEquals(FloatingChatThumbnailOrientation.Vertical, verticalImage.thumbnailOrientation)
        assertEquals(FloatingChatThumbnailOrientation.Horizontal, horizontalImage.thumbnailOrientation)
        assertEquals(FloatingChatThumbnailOrientation.Vertical, verticalVideo.thumbnailOrientation)
    }
}
