package com.paifa.ubikitouch.accessibility.floatingchat

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatExtractionContractTest {
    @Test
    fun chatContractAndRenderEntryPointsAreSeparated() {
        val contract = sourceFile("floatingchat/contract/ChatContract.kt")
        val coordinator = sourceFile("floatingchat/shell/FloatingChatCoordinator.kt")
        val shell = sourceFile("floatingchat/shell/FloatingChatShell.kt")
        val screen = sourceFile("floatingchat/chat/ChatScreen.kt")

        listOf(contract, coordinator, shell, screen).forEach { source ->
            assertTrue("Missing extracted chat source: ${source.path}", source.isFile)
        }
        val contractText = contract.readText()
        assertTrue(contractText.contains("data class ChatUiState"))
        assertTrue(contractText.contains("sealed interface ChatUiEvent"))
        assertFalse(contractText.contains("import android."))
        assertFalse(contractText.contains("import androidx.compose."))

        assertTrue(coordinator.readText().contains("class FloatingChatCoordinator"))
        assertTrue(shell.readText().contains("fun FloatingChatShell("))
        assertTrue(screen.readText().contains("fun ChatScreen("))
    }

    @Test
    fun sessionRailModelAndOrderingLiveInChatPackage() {
        val extracted = sourceFile(
            "floatingchat/chat/ChatSessionRail.kt"
        )
        assertTrue("Missing extracted session rail source", extracted.isFile)

        val text = extracted.readText()
        assertTrue(text.contains("sealed class SessionRailItem"))
        assertTrue(text.contains("fun sessionRailItemKeys("))
        assertTrue(text.contains("fun sessionRailItemKeysByLatestChatTime("))
        assertTrue(text.contains("fun sessionRailItemsByLatestChatTime("))
    }

    @Test
    fun connectorGeometryLivesInChatPackage() {
        val extracted = sourceFile("floatingchat/chat/ChatConnectorGeometry.kt")
        val layer = sourceFile("floatingchat/chat/ChatConnectorLayer.kt")
        assertTrue("Missing extracted connector geometry", extracted.isFile)
        assertTrue("Missing extracted connector layer", layer.isFile)

        val text = extracted.readText()
        assertTrue(text.contains("data class ChatConnectorTree"))
        assertTrue(text.contains("fun createChatConnectorLine("))
        assertTrue(text.contains("fun createChatConnectorTree("))
        assertTrue(text.contains("fun createChatConnectorBraceGeometry("))

        val legacy = sourceFile("FloatingChatOverlayUi.kt").readText()
        assertFalse(legacy.contains("internal data class ChatConnectorTree"))
        assertFalse(legacy.contains("internal fun createChatConnectorLine("))
        assertFalse(legacy.contains("internal fun createChatConnectorTree("))
        assertFalse(legacy.contains("private fun ChatConnectorLayer("))
    }

    @Test
    fun messagePresentationAndContentDispatchLiveInMessagePackage() {
        val presentation = sourceFile("floatingchat/message/MessagePresentation.kt")
        val listPresentation = sourceFile("floatingchat/message/MessageListPresentation.kt")
        val content = sourceFile("floatingchat/message/MessageContent.kt")
        val row = sourceFile("floatingchat/message/MessageRow.kt")
        val textContent = sourceFile("floatingchat/message/MessageTextContent.kt")
        val cards = sourceFile("floatingchat/message/MessageCards.kt")

        listOf(presentation, listPresentation, content, row, textContent, cards).forEach { source ->
            assertTrue("Missing extracted message source: ${source.path}", source.isFile)
        }
        assertTrue(content.readText().contains("fun MessageContent("))
        assertTrue(row.readText().contains("fun MessageRow("))
        assertTrue(row.readText().contains("fun MessageBlock("))
        assertTrue(textContent.readText().contains("fun SimpleTextMessageContent("))
        assertTrue(textContent.readText().contains("fun MixedTextMessageContent("))
        assertTrue(textContent.readText().contains("fun QuoteMessageContent("))
        assertTrue(textContent.readText().contains("fun ChatHistoryMessageContent("))
        assertTrue(cards.readText().contains("fun LocationMessageContent("))
        assertTrue(cards.readText().contains("fun ContactLinkCardContent("))
        assertTrue(cards.readText().contains("fun MiniProgramLinkContent("))
        assertTrue(cards.readText().contains("fun InlineContactContent("))
        assertTrue(cards.readText().contains("fun InlineLocationContent("))

        val legacy = sourceFile("FloatingChatOverlayUi.kt").readText()
        assertFalse(legacy.contains("private fun MessageContent("))
        assertFalse(legacy.contains("private fun MessageRow("))
        assertFalse(legacy.contains("internal fun MessageBlock("))
        assertFalse(legacy.contains("internal fun SimpleTextMessageContent("))
        assertFalse(legacy.contains("internal fun MixedTextMessageContent("))
        assertFalse(legacy.contains("internal fun QuoteMessageContent("))
        assertFalse(legacy.contains("internal fun ChatHistoryMessageContent("))
        assertFalse(legacy.contains("internal fun LocationMessageContent("))
        assertFalse(legacy.contains("internal fun ContactLinkCardContent("))
        assertFalse(legacy.contains("internal fun MiniProgramLinkContent("))
        assertFalse(legacy.contains("internal fun InlineContactContent("))
        assertFalse(legacy.contains("internal fun InlineLocationContent("))
    }

    @Test
    fun legacyOverlayNoLongerDefinesSessionRailOrdering() {
        val legacy = sourceFile("FloatingChatOverlayUi.kt")
        assertTrue("Missing legacy overlay source", legacy.isFile)

        val text = legacy.readText()
        assertFalse(text.contains("private sealed class SessionRailItem"))
        assertFalse(text.contains("internal fun sessionRailItemKeys("))
        assertFalse(text.contains("internal fun sessionRailItemKeysByLatestChatTime("))
    }

    @Test
    fun documentPreviewLivesInMediaPackage() {
        val documentPreview = sourceFile("floatingchat/media/DocumentPreview.kt")
        val nativeMediaSurface = sourceFile("floatingchat/media/NativeMediaSurface.kt")
        val mediaPreview = sourceFile("floatingchat/media/MediaPreview.kt")
        assertTrue("Missing extracted document preview", documentPreview.isFile)
        assertTrue("Missing extracted native media surface", nativeMediaSurface.isFile)
        assertTrue("Missing extracted media preview", mediaPreview.isFile)

        val text = documentPreview.readText()
        assertTrue(text.contains("fun FilePreviewContent("))
        assertTrue(text.contains("fun fileBadgeLabelFor("))
        assertTrue(nativeMediaSurface.readText().contains("fun VoiceMessageContent("))
        assertTrue(mediaPreview.readText().contains("fun ImageThumbnailContent("))
        assertTrue(mediaPreview.readText().contains("fun VideoPreviewContent("))

        val legacy = sourceFile("FloatingChatOverlayUi.kt").readText()
        assertFalse(legacy.contains("internal fun FilePreviewContent("))
        assertFalse(legacy.contains("internal fun fileBadgeLabelFor("))
        assertFalse(legacy.contains("internal fun VoiceMessageContent("))
        assertFalse(legacy.contains("internal fun ImageThumbnailContent("))
        assertFalse(legacy.contains("internal fun VideoPreviewContent("))
    }

    @Test
    fun mediaThumbnailRenderingAndLoadingLiveInMediaPackage() {
        val surface = sourceFile("floatingchat/media/MediaThumbnailSurface.kt")
        val loader = sourceFile("floatingchat/media/MediaThumbnailBitmapLoader.kt")
        assertTrue("Missing extracted media thumbnail surface", surface.isFile)
        assertTrue("Missing extracted media thumbnail loader", loader.isFile)

        val surfaceText = surface.readText()
        assertTrue(surfaceText.contains("fun MediaThumbnailSurface("))
        assertTrue(surfaceText.contains("fun PlaceholderVideoCanvas("))
        assertTrue(surfaceText.contains("fun VideoPlayGlyph("))

        val loaderText = loader.readText()
        assertTrue(loaderText.contains("fun rememberAsyncMediaThumbnailBitmap("))
        assertTrue(loaderText.contains("fun loadImageThumbnailBitmap("))
        assertTrue(loaderText.contains("fun loadVideoPreviewBitmap("))
        assertTrue(loaderText.contains("SharedBitmapMemoryCache"))
        assertTrue(loaderText.contains("fun decodeRemoteImageBitmap("))
        assertTrue(loaderText.contains("fun decodeFileBitmapRespectingExif("))

        val legacy = sourceFile("FloatingChatOverlayUi.kt").readText()
        assertFalse(legacy.contains("internal fun MediaThumbnailSurface("))
        assertFalse(legacy.contains("internal fun rememberAsyncMediaThumbnailBitmap("))
        assertFalse(legacy.contains("private val SharedBitmapMemoryCache"))
        assertFalse(legacy.contains("private fun decodeRemoteImageBitmap("))
        assertFalse(legacy.contains("private fun decodeFileBitmapRespectingExif("))
    }

    @Test
    fun friendRequestScreenUsesContactContractEvents() {
        val screen = sourceFile("floatingchat/contacts/FriendRequestScreen.kt")
        assertTrue("Missing extracted friend request screen", screen.isFile)

        val text = screen.readText()
        assertTrue(text.contains("fun FriendRequestScreen("))
        assertTrue(text.contains("state: FriendRequestUiState"))
        assertTrue(text.contains("onEvent: (ContactsUiEvent) -> Unit"))
        assertFalse(text.contains("ScrmFriendRequest"))
        assertFalse(text.contains("Context"))

        val legacy = sourceFile("FloatingChatOverlayUi.kt").readText()
        assertFalse(legacy.contains("private fun WechatFriendRequestsPanel("))
        assertFalse(legacy.contains("private fun ScrmFriendRequestList("))
    }

    @Test
    fun contactsScreenUsesPlatformIndependentStateAndContactEvents() {
        val screen = sourceFile("floatingchat/contacts/ContactsScreen.kt")
        assertTrue("Missing extracted contacts screen", screen.isFile)

        val text = screen.readText()
        assertTrue(text.contains("fun ContactsScreen("))
        assertTrue(text.contains("state: ContactsUiState"))
        assertFalse(text.contains("ContactsScreenUiState"))
        assertTrue(text.contains("onEvent: (ContactsUiEvent) -> Unit"))
        assertTrue(text.contains("Icons.Filled.Close"))
        assertTrue(text.contains("contentDescription = \"关闭通讯录\""))
        assertTrue(text.contains("onCloseClick = { onEvent(ContactsUiEvent.CloseRequested) }"))
        assertTrue(text.contains("ContactsUiEvent.QueryChanged"))
        assertTrue(text.contains("ContactsUiEvent.SearchSubmitted"))
        assertTrue(text.contains("ContactsUiEvent.SyncRequested"))
        assertTrue(text.contains("ContactsUiEvent.ContactSelected"))
        assertTrue(text.contains("ContactsUiEvent.ShortcutSelected"))
        assertTrue(text.contains("ContactsUiEvent.PlusMenuRequested"))
        assertFalse(text.contains("ScrmContact"))
        assertFalse(text.contains("Context"))
        assertFalse(text.contains("Service"))

        val legacy = sourceFile("FloatingChatOverlayUi.kt").readText()
        assertFalse(legacy.contains("private fun WechatContactsBookPanel("))
        assertFalse(legacy.contains("fun wechatContactIndexLabels("))
        assertFalse(legacy.contains("WechatContactsIndexText"))
    }

    @Test
    fun contactProfileScreenUsesPlatformIndependentStateAndEvents() {
        val screen = sourceFile("floatingchat/contacts/ContactProfileScreen.kt")
        val contract = sourceFile("floatingchat/contract/ContactsContract.kt")
        assertTrue("Missing extracted contact profile screen", screen.isFile)

        val text = screen.readText()
        assertTrue(text.contains("fun ContactProfileScreen("))
        assertTrue("ContactProfileScreen.kt must stay under 800 lines", screen.readLines().size < 800)
        assertTrue(text.contains("state: ContactProfileUiState"))
        assertTrue(text.contains("onEvent: (ContactProfileUiEvent) -> Unit"))
        assertTrue(text.contains("ContactSummary("))
        assertTrue(text.contains("avatarUrl = avatarUrl"))
        assertTrue(text.contains("ContactProfileUiEvent.VoiceCallRequested"))
        assertTrue(text.contains("ContactProfileUiEvent.VideoCallRequested"))
        assertFalse(text.contains("scrm", ignoreCase = true))
        assertFalse(text.contains("FloatingChatContact"))
        assertFalse(text.contains("LocalContactProfile"))
        assertFalse(text.contains("database", ignoreCase = true))
        assertFalse(text.contains("http", ignoreCase = true))
        assertFalse(text.contains("Activity"))
        assertFalse(text.contains("import android.content.Context"))
        assertFalse(text.contains("LocalContext.current"))

        val contractText = contract.readText()
        assertTrue(contractText.contains("data class ContactProfileUiState"))
        assertTrue(contractText.contains("val avatarUrl: String?"))
        assertTrue(contractText.contains("sealed interface ContactProfileUiEvent"))
        assertFalse(contractText.contains("import android."))
        assertFalse(contractText.contains("import androidx.compose."))

        val legacy = sourceFile("FloatingChatOverlayUi.kt").readText()
        assertFalse(legacy.contains("private fun WechatContactIntroPanel("))
        assertFalse(legacy.contains("private fun WechatContactIntroTopBar("))
        assertFalse(legacy.contains("private fun WechatContactIntroRow("))
        assertFalse(legacy.contains("private fun WechatContactIntroActionRow("))
        assertFalse(legacy.contains("private fun UserContactEditPanel("))
        assertFalse(legacy.contains("private fun FriendProfileTopBar("))
        assertFalse(legacy.contains("private fun FriendProfileHeader("))
        assertFalse(legacy.contains("private fun FriendProfilePhotosRow("))
        assertFalse(legacy.contains("private fun friendProfilePhotoColor("))
        assertTrue(legacy.contains("private fun GroupInfoHost("))
        assertTrue(legacy.contains("private fun defaultLocalGroupProfileFor("))
        assertTrue(legacy.contains("contactProfileIntroAction(event)"))
        assertTrue(legacy.contains("contactProfileEditorAction(event)"))
    }

    @Test
    fun groupInfoScreenUsesPlatformIndependentStateAndEvents() {
        val screen = sourceFile("floatingchat/group/GroupInfoScreen.kt")
        val contract = sourceFile("floatingchat/contract/ContactsContract.kt")
        assertTrue("Missing extracted group info screen", screen.isFile)

        val text = screen.readText()
        assertTrue(text.contains("fun GroupInfoScreen("))
        assertTrue("GroupInfoScreen.kt must stay under 800 lines", screen.readLines().size < 800)
        assertTrue(text.contains("state: GroupInfoUiState"))
        assertTrue(text.contains("onEvent: (GroupInfoUiEvent) -> Unit"))
        assertFalse(text.contains("scrm", ignoreCase = true))
        assertFalse(text.contains("FloatingChatContact"))
        assertFalse(text.contains("LocalGroupProfile"))
        assertFalse(text.contains("database", ignoreCase = true))
        assertFalse(text.contains("http", ignoreCase = true))
        assertFalse(text.contains("Activity"))
        assertFalse(text.contains("Context"))
        assertFalse(text.contains("Coroutine"))

        val contractText = contract.readText()
        assertTrue(contractText.contains("data class GroupInfoUiState"))
        assertTrue(contractText.contains("sealed interface GroupInfoUiEvent"))
        assertTrue(contractText.contains("sealed interface GroupInfoAction"))
        assertFalse(contractText.contains("import android."))
        assertFalse(contractText.contains("import androidx.compose."))

        val legacy = sourceFile("FloatingChatOverlayUi.kt").readText()
        assertFalse(legacy.contains("private fun GroupContactEditPanel("))
        assertFalse(legacy.contains("private fun GroupInfoTopBar("))
        assertFalse(legacy.contains("private fun GroupInfoMemberGridRow("))
        assertFalse(legacy.contains("private fun GroupInfoMemberCell("))
        assertFalse(legacy.contains("private fun GroupInfoAddMemberCell("))
        assertFalse(legacy.contains("private fun GroupInfoQrRow("))
        assertFalse(legacy.contains("private fun GroupInfoEditableRow("))
        assertFalse(legacy.contains("private fun GroupInfoSectionGap("))
        assertFalse(legacy.contains("private fun LegacyGroupInfo"))
        assertTrue(legacy.contains("private fun GroupMemberSelectionPanel("))
        assertTrue(legacy.contains("private fun GroupMemberPickerRow("))
        assertTrue(legacy.contains("private fun GroupMemberIntroPanel("))
        assertTrue(legacy.contains("groupInfoAction(event)"))
    }

    private fun sourceFile(relativePath: String): File {
        val moduleRelative = File(
            "src/main/kotlin/com/paifa/ubikitouch/accessibility",
            relativePath
        )
        if (moduleRelative.exists()) return moduleRelative

        return File(
            "ubiki-accessibility/src/main/kotlin/com/paifa/ubikitouch/accessibility",
            relativePath
        )
    }
}
