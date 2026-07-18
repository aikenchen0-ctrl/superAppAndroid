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
    fun sessionRailUiLivesInChatPackage() {
        val extracted = sourceFile("floatingchat/chat/ChatSessionRail.kt")
        assertTrue("Missing extracted session rail source", extracted.isFile)

        val text = extracted.readText()
        assertTrue(text.contains("fun ChatSessionRail("))
        assertTrue(text.contains("fun SessionRailAvatarItem("))
        assertTrue(text.contains("fun LeftRailFollowTextOverlay("))
        assertTrue(text.contains("fun GroupChatAvatar("))

        val legacy = sourceFile("FloatingChatOverlayUi.kt").readText()
        assertFalse(legacy.contains("private fun SessionRail("))
        assertFalse(legacy.contains("private fun ScrollableSessionRail("))
        assertFalse(legacy.contains("private fun SessionRailAvatarItem("))
        assertFalse(legacy.contains("private fun LeftRailFollowTextOverlay("))
        assertFalse(legacy.contains("private fun GroupChatAvatar("))
        assertFalse(legacy.contains("private fun GroupChatAvatarGrid("))
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
    fun connectorCoordinateStateLivesInChatPackage() {
        val state = sourceFile("floatingchat/chat/ConnectorCoordinateState.kt")
        assertTrue("Missing extracted connector coordinate state", state.isFile)

        val text = state.readText()
        assertTrue(text.contains("class ConnectorCoordinateState"))
        assertTrue(text.contains("data class RightRailVisibleAccountItem"))
        assertTrue(text.contains("data class LeftRailVisibleSessionItem"))
        assertTrue(text.contains("enum class RailPinnedAvatarEdge"))
        assertTrue(text.contains("enum class ConnectorAvatarLane"))
        assertTrue(text.contains("fun rightRailVirtualAccountAvatarBounds("))
        assertTrue(text.contains("fun leftRailVirtualSessionAvatarBounds("))
        assertTrue(text.contains("fun createGroupMemberMessageConnectorBranch("))
        assertTrue(text.contains("data class ConnectorOffscreenIndex"))

        val legacy = sourceFile("FloatingChatOverlayUi.kt").readText()
        assertFalse(legacy.contains("internal class ConnectorCoordinateState"))
        assertFalse(legacy.contains("internal data class RightRailVisibleAccountItem"))
        assertFalse(legacy.contains("internal data class LeftRailVisibleSessionItem"))
        assertFalse(legacy.contains("internal enum class RailPinnedAvatarEdge"))
        assertFalse(legacy.contains("internal enum class ConnectorAvatarLane"))
        assertFalse(legacy.contains("internal data class ConnectorOffscreenIndex"))
        assertFalse(legacy.contains("internal fun rightRailVirtualAccountAvatarBounds("))
        assertFalse(legacy.contains("internal fun leftRailVirtualSessionAvatarBounds("))
        assertFalse(legacy.contains("internal fun createGroupMemberMessageConnectorBranch("))
    }

    @Test
    fun chatLayoutStateLivesInChatPackage() {
        val state = sourceFile("floatingchat/chat/ChatLayoutState.kt")
        assertTrue("Missing extracted chat layout state", state.isFile)

        val text = state.readText()
        assertTrue(text.contains("data class LeftRailFollowInfo("))
        assertTrue(text.contains("fun leftRailUsesScrollableLazyColumn("))
        assertTrue(text.contains("fun leftRailTouchableWidthDp("))
        assertTrue(text.contains("fun messagePaneHorizontalPaddingDp("))
        assertTrue(text.contains("fun connectorLayerZIndex("))
        assertTrue(text.contains("fun imModuleConnectionLineColorArgb("))
        assertTrue(text.contains("fun connectorTargetIdForMessage("))
        assertFalse(text.contains("@Composable"))

        val legacy = sourceFile("FloatingChatOverlayUi.kt").readText()
        assertFalse(legacy.contains("internal data class LeftRailFollowInfo("))
        assertFalse(legacy.contains("internal fun leftRailUsesScrollableLazyColumn("))
        assertFalse(legacy.contains("internal fun leftRailTouchableWidthDp("))
        assertFalse(legacy.contains("internal fun messagePaneHorizontalPaddingDp("))
        assertFalse(legacy.contains("internal fun connectorLayerZIndex("))
        assertFalse(legacy.contains("internal fun imModuleConnectionLineColorArgb("))
        assertFalse(legacy.contains("internal fun connectorTargetIdForMessage("))
        assertFalse(legacy.contains("private const val SessionRailWidthDp"))
        assertFalse(legacy.contains("private const val MessagePaneHorizontalPaddingDp"))
    }

    @Test
    fun chatThreadStateLivesInChatPackage() {
        val state = sourceFile("floatingchat/chat/ChatThreadState.kt")
        assertTrue("Missing extracted chat thread state", state.isFile)

        val text = state.readText()
        assertTrue(text.contains("fun defaultChatThreadSelection("))
        assertTrue(text.contains("fun accountScopedConversation("))
        assertTrue(text.contains("data class AccountScopedConversation("))
        assertTrue(text.contains("fun visibleMessagesForThread("))
        assertTrue(text.contains("data class HomeUnreadThreadSummary("))
        assertTrue(text.contains("fun homeUnreadThreadSummaries("))
        assertTrue(text.contains("fun groupMemberContactForMessage("))
        assertFalse(text.contains("@Composable"))

        val legacy = sourceFile("FloatingChatOverlayUi.kt").readText()
        assertFalse(legacy.contains("internal fun defaultChatThreadSelection("))
        assertFalse(legacy.contains("internal fun accountScopedConversation("))
        assertFalse(legacy.contains("internal data class AccountScopedConversation("))
        assertFalse(legacy.contains("internal fun visibleMessagesForThread("))
        assertFalse(legacy.contains("internal data class HomeUnreadThreadSummary("))
        assertFalse(legacy.contains("internal fun homeUnreadThreadSummaries("))
        assertFalse(legacy.contains("internal fun groupMemberContactForMessage("))
        assertFalse(legacy.contains("private const val AccountScopedThreadSeparator"))
        assertFalse(legacy.contains("private const val DefaultHomeUnreadThreadLimit"))
    }

    @Test
    fun chatThreadEffectsLiveInChatPackage() {
        val effects = sourceFile("floatingchat/chat/ChatThreadEffects.kt")
        assertTrue("Missing extracted chat thread effects", effects.isFile)

        val text = effects.readText()
        assertTrue(text.contains("fun ChatThreadEffects("))
        assertTrue(text.contains("LaunchedEffect(effectiveConversation.groupContacts, effectiveConversation.contacts)"))
        assertTrue(text.contains("LaunchedEffect(selectedThread, selectedAccountId)"))

        val legacy = sourceFile("FloatingChatOverlayUi.kt").readText()
        assertFalse(legacy.contains("LaunchedEffect(effectiveConversation.groupContacts, effectiveConversation.contacts)"))
        assertFalse(legacy.contains("LaunchedEffect(selectedThread, selectedAccount.id)"))
    }

    @Test
    fun chatNavigationActionsLiveInChatPackage() {
        val actions = sourceFile("floatingchat/chat/ChatNavigationActions.kt")
        assertTrue("Missing extracted chat navigation actions", actions.isFile)

        val text = actions.readText()
        assertTrue(text.contains("class ChatNavigationActions("))
        assertTrue(text.contains("fun openChatThread("))
        assertTrue(text.contains("fun openHomeUnread("))
        assertTrue(text.contains("unreadThreadIds.remove(thread.toLocalThreadId())"))
        assertFalse(text.contains("import androidx.compose."))

        val legacy = sourceFile("FloatingChatOverlayUi.kt").readText()
        assertFalse(legacy.contains("fun openChatThread("))
        assertFalse(legacy.contains("fun openHomeUnread("))
    }

    @Test
    fun chatThreadSelectionLivesInChatPackage() {
        val selection = sourceFile("floatingchat/chat/ChatThreadSelection.kt")
        assertTrue("Missing extracted chat thread selection", selection.isFile)

        val text = selection.readText()
        assertTrue(text.contains("sealed interface ChatThreadSelection"))
        assertTrue(text.contains("const val GroupThreadId"))
        assertTrue(text.contains("fun ChatThreadSelection.toLocalThreadId("))
        assertTrue(text.contains("fun groupMemberRailContacts("))
        assertTrue(text.contains("fun FloatingChatContact.toGroupThreadSelection("))
        assertTrue(text.contains("fun FloatingChatPrototype.ToolThreadSelection.toChatThreadSelection("))
        assertFalse(text.contains("@Composable"))

        val legacy = sourceFile("FloatingChatOverlayUi.kt").readText()
        assertFalse(legacy.contains("internal sealed interface ChatThreadSelection"))
        assertFalse(legacy.contains("internal fun ChatThreadSelection.toLocalThreadId("))
        assertFalse(legacy.contains("internal fun groupMemberRailContacts("))
        assertFalse(legacy.contains("internal fun FloatingChatContact.toGroupThreadSelection("))
        assertFalse(legacy.contains("internal fun FloatingChatPrototype.ToolThreadSelection.toChatThreadSelection("))
        assertFalse(legacy.contains("internal const val GroupThreadId"))
    }

    @Test
    fun groupAvatarPolicyLivesInChatPackage() {
        val policy = sourceFile("floatingchat/chat/GroupAvatarPolicy.kt")
        assertTrue("Missing extracted group avatar policy", policy.isFile)

        val text = policy.readText()
        assertTrue(text.contains("fun groupChatAvatarGridMaxMembers("))
        assertTrue(text.contains("fun groupChatAvatarGridImageUris("))
        assertTrue(text.contains("fun groupChatAvatarDisplayImageUris("))
        assertTrue(text.contains("fun groupMemberAvatarSizeDp("))
        assertTrue(text.contains("fun groupMemberAvatarBubbleCenterOffsetDp("))
        assertTrue(text.contains("fun groupChatConnectorUsesMessageScopedMemberAvatar("))
        assertFalse(text.contains("@Composable"))

        val legacy = sourceFile("FloatingChatOverlayUi.kt").readText()
        assertFalse(legacy.contains("internal fun groupChatAvatarGridMaxMembers("))
        assertFalse(legacy.contains("internal fun groupChatAvatarGridImageUris("))
        assertFalse(legacy.contains("internal fun groupChatAvatarDisplayImageUris("))
        assertFalse(legacy.contains("internal fun groupMemberAvatarSizeDp("))
        assertFalse(legacy.contains("internal fun groupMemberAvatarBubbleCenterOffsetDp("))
        assertFalse(legacy.contains("internal fun groupChatConnectorUsesMessageScopedMemberAvatar("))
        assertFalse(legacy.contains("private const val GroupMemberAvatarSizeDp"))
        assertFalse(legacy.contains("private const val GroupMemberAvatarBubbleCenterOffsetDp"))
    }

    @Test
    fun momentPostMappingLivesInMomentsPackage() {
        val mapping = sourceFile("floatingchat/moments/MomentPostMapping.kt")
        assertTrue("Missing extracted moment post mapping", mapping.isFile)

        val text = mapping.readText()
        assertTrue(text.contains("fun AppMomentPost.toFloatingChatMediaMessage("))
        assertTrue(text.contains("fun AppMomentPost.toLocalMomentPost("))
        assertTrue(text.contains("fun LocalMomentPost.toAppMomentPost("))
        assertFalse(text.contains("@Composable"))

        val legacy = sourceFile("FloatingChatOverlayUi.kt").readText()
        assertFalse(legacy.contains("fun AppMomentPost.toFloatingChatMediaMessage("))
        assertFalse(legacy.contains("fun AppMomentPost.toLocalMomentPost("))
        assertFalse(legacy.contains("fun LocalMomentPost.toAppMomentPost("))
    }

    @Test
    fun momentToolActionsLiveInMomentsPackage() {
        val actions = sourceFile("floatingchat/moments/MomentToolActions.kt")
        assertTrue("Missing extracted moment tool actions", actions.isFile)

        val text = actions.readText()
        assertTrue(text.contains("class MomentToolActions("))
        assertTrue(text.contains("fun upsertMomentPost("))
        assertTrue(text.contains("fun pickMomentMedia("))
        assertTrue(text.contains("fun clearMomentMedia("))
        assertTrue(text.contains("fun previewMomentMedia("))
        assertTrue(text.contains("FloatingChatMediaPickerBridge.requestPick"))
        assertTrue(text.contains("FloatingChatMediaPreviewBridge.open"))
        assertFalse(text.contains("import androidx.compose."))

        val legacy = sourceFile("FloatingChatOverlayUi.kt").readText()
        assertFalse(legacy.contains("fun upsertMomentPost("))
        assertFalse(legacy.contains("return@FloatingBottomPanel"))
    }

    @Test
    fun momentsPolicyLivesInMomentsPackage() {
        val policy = sourceFile("floatingchat/moments/MomentsPolicy.kt")
        assertTrue("Missing extracted moments policy", policy.isFile)

        val text = policy.readText()
        assertTrue(text.contains("fun momentsToolOpensInAppTimeline("))
        assertTrue(text.contains("fun momentsTimelineSupportsComposePost("))
        assertTrue(text.contains("fun momentMaterialTenantIdForRoute("))
        assertTrue(text.contains("fun momentMaterialsPanelUsesAccountScopedTenant("))
        assertTrue(text.contains("fun momentsTimelineReusesChatMediaPreview("))
        assertFalse(text.contains("@Composable"))

        val legacy = sourceFile("FloatingChatOverlayUi.kt").readText()
        assertFalse(legacy.contains("internal fun momentsToolOpensInAppTimeline("))
        assertFalse(legacy.contains("internal fun momentsTimelineSupportsComposePost("))
        assertFalse(legacy.contains("internal fun momentMaterialTenantIdForRoute("))
        assertFalse(legacy.contains("internal fun momentMaterialsPanelUsesAccountScopedTenant("))
        assertFalse(legacy.contains("internal fun momentsTimelineReusesChatMediaPreview("))
    }

    @Test
    fun mediaUriPolicyLivesInMediaPackage() {
        val policy = sourceFile("floatingchat/media/MediaUriPolicy.kt")
        assertTrue("Missing extracted media URI policy", policy.isFile)

        val text = policy.readText()
        assertTrue(text.contains("fun isLocalMediaUri("))
        assertTrue(text.contains("fun isRemoteImageUri("))
        assertTrue(text.contains("fun normalizedRemoteImageUri("))
        assertTrue(text.contains("fun avatarImageDecodeMaxSizePx("))
        assertTrue(text.contains("fun remoteImageRetrySuppressedByRecentFailure("))
        assertTrue(text.contains("fun imageDecodeSampleSize("))
        assertTrue(text.contains("fun remoteImageConnectTimeoutMillis("))
        assertFalse(text.contains("@Composable"))

        val legacy = sourceFile("FloatingChatOverlayUi.kt").readText()
        assertFalse(legacy.contains("internal fun isLocalContentUri("))
        assertFalse(legacy.contains("internal fun isLocalMediaUri("))
        assertFalse(legacy.contains("internal fun isRemoteImageUri("))
        assertFalse(legacy.contains("internal fun normalizedRemoteImageUri("))
        assertFalse(legacy.contains("private val WeChatAvatarHttpsHosts"))
        assertFalse(legacy.contains("internal fun avatarImageDecodeMaxSizePx("))
        assertFalse(legacy.contains("internal fun remoteImageRetrySuppressedByRecentFailure("))
        assertFalse(legacy.contains("internal fun imageDecodeSampleSize("))
        assertFalse(legacy.contains("internal fun remoteImageConnectTimeoutMillis("))
        assertFalse(legacy.contains("private const val REMOTE_IMAGE_CONNECT_TIMEOUT_MS"))
    }

    @Test
    fun videoPresentationAndTexturePolicyLiveInMediaPackage() {
        val aspectRatio = sourceFile("floatingchat/media/VideoAspectRatio.kt")
        val texture = sourceFile("floatingchat/media/VideoTextureTransform.kt")
        val timecode = sourceFile("floatingchat/media/VideoTimecode.kt")
        listOf(aspectRatio, texture, timecode).forEach { source ->
            assertTrue("Missing extracted video media source: ${source.path}", source.isFile)
        }

        assertTrue(aspectRatio.readText().contains("fun mediaAspectRatioFromDimensions("))
        assertTrue(texture.readText().contains("fun textureViewScaleForAspectFit("))
        assertTrue(texture.readText().contains("fun applyTextureViewAspectFitTransform("))
        assertTrue(timecode.readText().contains("fun formatVideoTimecode("))

        val legacy = sourceFile("FloatingChatOverlayUi.kt").readText()
        assertFalse(legacy.contains("internal fun mediaAspectRatioFromDimensions("))
        assertFalse(legacy.contains("internal fun textureViewScaleForAspectFit("))
        assertFalse(legacy.contains("internal fun formatVideoTimecode("))
        assertFalse(legacy.contains("internal fun applyTextureViewAspectFitTransform("))
        assertFalse(legacy.contains("private fun lerpFloat("))
    }

    @Test
    fun mediaToolPolicyLivesInMediaPackage() {
        val policy = sourceFile("floatingchat/media/MediaToolPolicy.kt")
        assertTrue("Missing extracted media tool policy", policy.isFile)

        val text = policy.readText()
        assertTrue(text.contains("fun documentToolRequestsSystemFilePicker("))
        assertTrue(text.contains("fun pickedDocumentCreatesRealFileMessage("))
        assertTrue(text.contains("fun galleryToolPicksImageAndVideo("))
        assertTrue(text.contains("fun galleryVideoMessageForPlayback("))
        assertTrue(text.contains("fun cameraToolUsesWechatPressGesture("))
        assertTrue(text.contains("fun cameraVideoMaxDurationMs("))
        assertTrue(text.contains("CAMERA_VIDEO_MAX_DURATION_MS"))
        assertFalse(text.contains("@Composable"))

        val legacy = sourceFile("FloatingChatOverlayUi.kt").readText()
        assertFalse(legacy.contains("internal fun documentToolRequestsSystemFilePicker("))
        assertFalse(legacy.contains("internal fun pickedDocumentCreatesRealFileMessage("))
        assertFalse(legacy.contains("internal fun galleryToolPicksImageAndVideo("))
        assertFalse(legacy.contains("internal fun galleryVideoMessageForPlayback("))
        assertFalse(legacy.contains("internal fun cameraToolUsesWechatPressGesture("))
        assertFalse(legacy.contains("internal fun cameraVideoMaxDurationMs("))
        assertFalse(legacy.contains("private const val CameraVideoMaxDurationMs"))
    }

    @Test
    fun pickedMediaMessageFactoryLivesInMediaPackage() {
        val factory = sourceFile("floatingchat/media/PickedMediaMessageFactory.kt")
        assertTrue("Missing extracted picked media message factory", factory.isFile)

        val text = factory.readText()
        assertTrue(text.contains("fun pickedMediaMessageForEvent("))
        assertTrue(text.contains("fun pickedDocumentMessageForEvent("))
        assertFalse(text.contains("@Composable"))

        val legacy = sourceFile("FloatingChatOverlayUi.kt").readText()
        assertFalse(legacy.contains("FloatingChatPrototype.simulatedPickedMediaMessage("))
        assertFalse(legacy.contains("FloatingChatPrototype.pickedDocumentMessage("))
    }

    @Test
    fun pickedMediaMessageActionsLiveInMediaPackage() {
        val actions = sourceFile("floatingchat/media/PickedMediaMessageActions.kt")
        assertTrue("Missing extracted picked media message actions", actions.isFile)

        val text = actions.readText()
        assertTrue(text.contains("class PickedMediaMessageActions("))
        assertTrue(text.contains("fun addPickedMediaMessage("))
        assertTrue(text.contains("fun addPickedDocumentMessage("))
        assertFalse(text.contains("import android."))
        assertFalse(text.contains("import androidx.compose."))

        val legacy = sourceFile("FloatingChatOverlayUi.kt").readText()
        assertFalse(legacy.contains("pickedMediaMessageForEvent("))
        assertFalse(legacy.contains("pickedDocumentMessageForEvent("))
    }

    @Test
    fun pickedMediaEffectsLiveInMediaPackage() {
        val effects = sourceFile("floatingchat/media/PickedMediaEffects.kt")
        assertTrue("Missing extracted picked media effects", effects.isFile)

        val text = effects.readText()
        assertTrue(text.contains("fun PickedMediaEffects("))
        assertTrue(text.contains("LaunchedEffect(pickedMediaEvent)"))
        assertTrue(text.contains("FloatingChatMediaTarget.AccountAvatar"))
        assertTrue(text.contains("FloatingChatMediaTarget.Moment"))
        assertTrue(text.contains("addPickedDocumentMessage(event)"))

        val legacy = sourceFile("FloatingChatOverlayUi.kt").readText()
        assertFalse(legacy.contains("LaunchedEffect(runtimeState.pickedMediaEvent)"))
        assertFalse(legacy.contains("LaunchedEffect(runtimeState.pickedDocumentEvent)"))
    }

    @Test
    fun overlayAppearancePolicyLivesInShellPackage() {
        val policy = sourceFile("floatingchat/shell/FloatingChatAppearancePolicy.kt")
        assertTrue("Missing extracted overlay appearance policy", policy.isFile)

        val text = policy.readText()
        assertTrue(text.contains("fun floatingChatOverlayUsesRuntimeSizedCompositionSections("))
        assertTrue(text.contains("fun floatingChatBlankAreaClickCollapsesOverlay("))
        assertTrue(text.contains("fun floatingChatAppearancePanelPlacement("))
        assertTrue(text.contains("fun floatingChatOverlayUsesFrostedBackground("))
        assertTrue(text.contains("fun floatingChatCollapseRetainsExpandedComposition("))
        assertTrue(text.contains("fun Modifier.floatingChatFrostedBackdrop("))

        val legacy = sourceFile("FloatingChatOverlayUi.kt").readText()
        assertFalse(legacy.contains("internal fun floatingChatOverlayUsesRuntimeSizedCompositionSections("))
        assertFalse(legacy.contains("internal fun floatingChatBlankAreaClickCollapsesOverlay("))
        assertFalse(legacy.contains("internal fun floatingChatAppearancePanelPlacement("))
        assertFalse(legacy.contains("internal fun floatingChatOverlayUsesFrostedBackground("))
        assertFalse(legacy.contains("internal fun floatingChatCollapseRetainsExpandedComposition("))
        assertFalse(legacy.contains("private fun Modifier.floatingChatFrostedBackdrop("))
        assertFalse(legacy.contains("private fun Color.lightenedForFrostedBackdrop("))
    }

    @Test
    fun internalEdgeGestureLivesInShellPackage() {
        val gesture = sourceFile("floatingchat/shell/FloatingChatInternalEdgeGesture.kt")
        assertTrue("Missing extracted internal edge gesture", gesture.isFile)

        val text = gesture.readText()
        assertTrue(text.contains("fun Modifier.floatingChatInternalEdgeGesture("))
        assertTrue(text.contains("object FloatingChatInternalEdgeGestureDefaults"))
        assertTrue(text.contains("fun floatingChatOverlayHandlesOwnEdgeGestures("))
        assertTrue(text.contains("fun floatingChatInternalEdgeGestureTouchTargetDp("))
        assertTrue(text.contains("fun floatingChatExpandedBottomGestureHandledInsideOverlay("))

        val legacy = sourceFile("FloatingChatOverlayUi.kt").readText()
        assertFalse(legacy.contains("private fun Modifier.floatingChatInternalEdgeGesture("))
        assertFalse(legacy.contains("private fun edgeSideForPosition("))
        assertFalse(legacy.contains("private object FloatingChatInternalEdgeGestureDefaults"))
        assertFalse(legacy.contains("internal fun floatingChatOverlayHandlesOwnEdgeGestures("))
        assertFalse(legacy.contains("internal fun floatingChatInternalEdgeGestureTouchTargetDp("))
        assertFalse(legacy.contains("internal fun floatingChatExpandedBottomGestureHandledInsideOverlay("))
    }

    @Test
    fun overlayGestureBindingLivesInShellPackage() {
        val binding = sourceFile("floatingchat/shell/FloatingChatOverlayGestureBinding.kt")
        assertTrue("Missing extracted overlay gesture binding", binding.isFile)

        val text = binding.readText()
        assertTrue(text.contains("fun Modifier.floatingChatOverlayGestureBinding("))
        assertTrue(text.contains("LocalDensity.current"))
        assertTrue(text.contains("LocalViewConfiguration.current"))
        assertTrue(text.contains("rememberUpdatedState(onEdgeGesture)"))
        assertTrue(text.contains("floatingChatInternalEdgeGesture("))

        val legacy = sourceFile("FloatingChatOverlayUi.kt").readText()
        assertFalse(legacy.contains("val edgeGestureTouchTargetPx"))
        assertFalse(legacy.contains("val edgeGestureShortThresholdPx"))
        assertFalse(legacy.contains("val edgeGestureLongThresholdPx"))
        assertFalse(legacy.contains(".floatingChatInternalEdgeGesture("))
    }

    @Test
    fun previewChromeEffectsLiveInShellPackage() {
        val effects = sourceFile("floatingchat/shell/FloatingChatPreviewChromeEffects.kt")
        assertTrue("Missing extracted preview chrome effects", effects.isFile)

        val text = effects.readText()
        assertTrue(text.contains("fun FloatingChatPreviewChromeEffects("))
        assertTrue(text.contains("LaunchedEffect(mediaOverlayState.actionMessage)"))
        assertTrue(text.contains("LaunchedEffect(runtimeState.previewSession, runtimeState.documentPreviewMessage)"))
        assertTrue(text.contains("LaunchedEffect(runtimeState.dismissSignal)"))

        val legacy = sourceFile("FloatingChatOverlayUi.kt").readText()
        assertFalse(legacy.contains("LaunchedEffect(mediaOverlayState.actionMessage)"))
        assertFalse(legacy.contains("LaunchedEffect(runtimeState.previewSession, runtimeState.documentPreviewMessage)"))
        assertFalse(legacy.contains("LaunchedEffect(runtimeState.dismissSignal)"))
    }

    @Test
    fun conversationSyncEffectsLiveInShellPackage() {
        val effects = sourceFile("floatingchat/shell/FloatingChatConversationSyncEffects.kt")
        assertTrue("Missing extracted conversation sync effects", effects.isFile)

        val text = effects.readText()
        assertTrue(text.contains("fun FloatingChatConversationSyncEffects("))
        assertTrue(text.contains("LaunchedEffect(conversation)"))
        assertTrue(text.contains("LaunchedEffect(runtimeState.conversationUpdateEvent)"))
        assertTrue(text.contains("LaunchedEffect(runtimeState.localMessagesUpdateEvent)"))

        val legacy = sourceFile("FloatingChatOverlayUi.kt").readText()
        assertFalse(legacy.contains("LaunchedEffect(conversation)"))
        assertFalse(legacy.contains("LaunchedEffect(runtimeState.conversationUpdateEvent)"))
        assertFalse(legacy.contains("LaunchedEffect(runtimeState.localMessagesUpdateEvent)"))
    }

    @Test
    fun aiVoiceOverlayPolicyLivesInAiVoicePackage() {
        val policy = sourceFile("floatingchat/aivoice/AiVoiceOverlayPolicy.kt")
        assertTrue("Missing extracted AI voice overlay policy", policy.isFile)

        val text = policy.readText()
        assertTrue(text.contains("fun rightRailAssistantOpensAiConfigPanel("))
        assertTrue(text.contains("fun aiConfigPanelSupportsConnectionTest("))
        assertTrue(text.contains("fun aiConfigTemperatureLabel("))
        assertTrue(text.contains("fun blinkVoiceResultMessageText("))
        assertTrue(text.contains("fun blinkVoiceRecognitionAutoSendsChatMessage("))
        assertFalse(text.contains("@Composable"))

        val legacy = sourceFile("FloatingChatOverlayUi.kt").readText()
        assertFalse(legacy.contains("internal fun rightRailAssistantOpensAiConfigPanel("))
        assertFalse(legacy.contains("internal fun aiConfigPanelSupportsConnectionTest("))
        assertFalse(legacy.contains("internal fun aiConfigTemperatureLabel("))
        assertFalse(legacy.contains("internal fun blinkVoiceResultMessageText("))
        assertFalse(legacy.contains("internal fun blinkVoiceRecognitionAutoSendsChatMessage("))
    }

    @Test
    fun aiDraftMessageActionsLiveInAiVoicePackage() {
        val actions = sourceFile("floatingchat/aivoice/AiDraftMessageActions.kt")
        assertTrue("Missing extracted AI draft message actions", actions.isFile)

        val text = actions.readText()
        assertTrue(text.contains("fun editedAiDraftMessage("))
        assertTrue(text.contains("class AiDraftMessageActions("))
        assertTrue(text.contains("fun upsertDraftMessage("))
        assertTrue(text.contains("fun removeDraftMessage("))
        assertTrue(text.contains("fun updateDraftText("))
        assertTrue(text.contains("fun sendDraftMessage("))
        assertFalse(text.contains("import androidx.compose."))

        val legacy = sourceFile("FloatingChatOverlayUi.kt").readText()
        assertFalse(legacy.contains("fun upsertAiDraftMessage("))
        assertFalse(legacy.contains("fun removeAiDraftMessage("))
        assertFalse(legacy.contains("fun editedAiDraftMessage("))
        assertFalse(legacy.contains("fun updateAiDraftText("))
        assertFalse(legacy.contains("fun sendAiDraftMessage("))
    }

    @Test
    fun aiDraftGenerationActionsLiveInAiVoicePackage() {
        val actions = sourceFile("floatingchat/aivoice/AiDraftGenerationActions.kt")
        assertTrue("Missing extracted AI draft generation actions", actions.isFile)

        val text = actions.readText()
        assertTrue(text.contains("class AiDraftGenerationActions("))
        assertTrue(text.contains("fun generate(replaceMessage: FloatingChatMessage? = null)"))
        assertTrue(text.contains("FloatingChatAiClient().generateDraft("))
        assertTrue(text.contains("createFloatingChatAiLoadingDraftMessage("))

        val legacy = sourceFile("FloatingChatOverlayUi.kt").readText()
        assertFalse(legacy.contains("fun generateAiDraft("))
        assertFalse(legacy.contains("createFloatingChatAiLoadingDraftMessage("))
        assertFalse(legacy.contains("createFloatingChatAiDraftMessage("))
    }

    @Test
    fun aiDraftOverlayHostLivesInAiVoicePackage() {
        val host = sourceFile("floatingchat/aivoice/AiDraftOverlayHost.kt")
        assertTrue("Missing extracted AI draft overlay host", host.isFile)

        val text = host.readText()
        assertTrue(text.contains("fun AiDraftOverlayHost("))
        assertTrue(text.contains("AiDraftActionOverlay("))
        assertTrue(text.contains("AiDraftEditOverlay("))
        assertTrue(text.contains("draftGenerationActions.generate("))

        val legacy = sourceFile("FloatingChatOverlayUi.kt").readText()
        assertFalse(legacy.contains("aiDraftActionMessage?.let { message ->"))
        assertFalse(legacy.contains("aiDraftEditMessage?.let { message ->"))
    }

    @Test
    fun aiConfigTestActionsLiveInAiVoicePackage() {
        val actions = sourceFile("floatingchat/aivoice/AiConfigTestActions.kt")
        assertTrue("Missing extracted AI config test actions", actions.isFile)

        val text = actions.readText()
        assertTrue(text.contains("class AiConfigTestActions("))
        assertTrue(text.contains("fun test(candidate: FloatingChatAiConfig)"))
        assertTrue(text.contains("FloatingChatAiClient().testConfig("))

        val legacy = sourceFile("FloatingChatOverlayUi.kt").readText()
        assertFalse(legacy.contains("fun testAiConfig("))
        assertFalse(legacy.contains("FloatingChatAiClient().testConfig("))
        assertFalse(legacy.contains("withContext(Dispatchers.IO)"))
    }

    @Test
    fun blinkInputEffectsLiveInAiVoicePackage() {
        val effects = sourceFile("floatingchat/aivoice/BlinkInputEffects.kt")
        assertTrue("Missing extracted blink input effects", effects.isFile)

        val text = effects.readText()
        assertTrue(text.contains("fun FloatingChatBlinkInputEffects("))
        assertTrue(text.contains("LaunchedEffect(runtimeState.blinkVoiceResultEvent)"))
        assertTrue(text.contains("FloatingChatBlinkVoiceBridge.requestHeadlessCapture()"))
        assertTrue(text.contains("DisposableEffect(Unit)"))

        val legacy = sourceFile("FloatingChatOverlayUi.kt").readText()
        assertFalse(legacy.contains("LaunchedEffect(runtimeState.blinkVoiceResultEvent)"))
        assertFalse(legacy.contains("FloatingChatBlinkVoiceBridge.requestHeadlessCapture()"))
        assertFalse(legacy.contains("FloatingChatBlinkVoiceBridge.stopHeadlessCapture()"))
        assertFalse(legacy.contains("runtimeState.clearBlinkVoiceResultEvent("))
    }

    @Test
    fun blinkInputAiActionsLiveInAiVoicePackage() {
        val actions = sourceFile("floatingchat/aivoice/BlinkInputAiActions.kt")
        assertTrue("Missing extracted blink input AI actions", actions.isFile)

        val text = actions.readText()
        assertTrue(text.contains("class BlinkInputAiActions("))
        assertTrue(text.contains("fun run(action: FloatingChatBlinkInputAiAction"))
        assertTrue(text.contains("FloatingChatAiClient().generateDraft("))
        assertTrue(text.contains("buildFloatingChatAiInputPolishPrompt("))

        val legacy = sourceFile("FloatingChatOverlayUi.kt").readText()
        assertFalse(legacy.contains("fun runBlinkInputAiAction("))
        assertFalse(legacy.contains("buildFloatingChatAiInputPolishPrompt("))
    }

    @Test
    fun messagePresentationAndContentDispatchLiveInMessagePackage() {
        val presentation = sourceFile("floatingchat/message/MessagePresentation.kt")
        val listPresentation = sourceFile("floatingchat/message/MessageListPresentation.kt")
        val style = sourceFile("floatingchat/message/MessageBubbleStyle.kt")
        val content = sourceFile("floatingchat/message/MessageContent.kt")
        val row = sourceFile("floatingchat/message/MessageRow.kt")
        val textContent = sourceFile("floatingchat/message/MessageTextContent.kt")
        val cards = sourceFile("floatingchat/message/MessageCards.kt")

        listOf(presentation, listPresentation, style, content, row, textContent, cards).forEach { source ->
            assertTrue("Missing extracted message source: ${source.path}", source.isFile)
        }
        assertTrue(listPresentation.readText().contains("fun messageListBottomClearanceDp("))
        assertTrue(style.readText().contains("fun messageBubbleColor("))
        assertTrue(style.readText().contains("fun Modifier.aiDraftDashedBorder("))
        assertTrue(style.readText().contains("fun messageTypeUsesImModuleBubble("))
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
        assertFalse(legacy.contains("internal fun messageBubbleColor("))
        assertFalse(legacy.contains("internal fun Modifier.aiDraftDashedBorder("))
        assertFalse(legacy.contains("internal fun messageTypeUsesImModuleBubble("))
        assertFalse(legacy.contains("internal fun messageListBottomClearanceDp("))
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
    fun paymentMessageUiLivesInMessagePackage() {
        val payment = sourceFile("floatingchat/message/PaymentMessageUi.kt")
        assertTrue("Missing extracted payment message source", payment.isFile)

        val text = payment.readText()
        assertTrue(text.contains("fun PaymentCardContent("))
        assertTrue(text.contains("fun PaymentDetailOverlay("))
        assertTrue(text.contains("fun paymentCardKindLabelFor("))
        assertTrue(text.contains("fun transferMessageDetailForRecipient("))
        assertTrue(text.contains("fun FloatingChatMessage.isPaymentCardMessage("))

        val legacy = sourceFile("FloatingChatOverlayUi.kt").readText()
        assertFalse(legacy.contains("internal fun PaymentCardContent("))
        assertFalse(legacy.contains("private fun PaymentDetailOverlay("))
        assertFalse(legacy.contains("private enum class PaymentCardKind("))
        assertFalse(legacy.contains("private fun paymentCardKindFor("))
        assertFalse(legacy.contains("private fun RedPacketPaymentGlyph("))
        assertFalse(legacy.contains("private fun TransferPaymentGlyph("))
    }

    @Test
    fun paymentToolPolicyLivesInMessagePackage() {
        val policy = sourceFile("floatingchat/message/PaymentToolPolicy.kt")
        assertTrue("Missing extracted payment tool policy", policy.isFile)

        val text = policy.readText()
        assertTrue(text.contains("fun redPacketToolOpensInAppComposer("))
        assertTrue(text.contains("fun transferToolOpensInAppComposer("))
        assertTrue(text.contains("fun appOwnedPaymentToolsSendChatMessages("))
        assertTrue(text.contains("fun scrmSendStatusRendersOutsideMessageBubble("))
        assertFalse(text.contains("@Composable"))

        val legacy = sourceFile("FloatingChatOverlayUi.kt").readText()
        assertFalse(legacy.contains("internal fun redPacketToolOpensInAppComposer("))
        assertFalse(legacy.contains("internal fun transferToolOpensInAppComposer("))
        assertFalse(legacy.contains("internal fun appOwnedPaymentToolsSendChatMessages("))
        assertFalse(legacy.contains("internal fun scrmSendStatusRendersOutsideMessageBubble("))
    }

    @Test
    fun messageLongPressMenuLivesInMessagePackage() {
        val menu = sourceFile("floatingchat/message/MessageLongPressMenu.kt")
        assertTrue("Missing extracted message long press menu source", menu.isFile)

        val text = menu.readText()
        assertTrue(text.contains("enum class MessageLongPressAction("))
        assertTrue(text.contains("fun MessageLongPressMenuOverlay("))
        assertTrue(text.contains("fun MessageLongPressMenu("))
        assertTrue(text.contains("fun MultiSelectActionBar("))
        assertTrue(text.contains("fun messageLongPressPrimaryActions("))
        assertTrue(text.contains("fun multiSelectSelectionCountLabel("))

        val legacy = sourceFile("FloatingChatOverlayUi.kt").readText()
        assertFalse(legacy.contains("internal enum class MessageLongPressAction("))
        assertFalse(legacy.contains("private fun MessageLongPressMenuOverlay("))
        assertFalse(legacy.contains("internal fun MessageLongPressMenu("))
        assertFalse(legacy.contains("private fun MultiSelectActionBar("))
        assertFalse(legacy.contains("private fun LongPressBarButton("))
        assertFalse(legacy.contains("private fun MessageLongPressAction.icon("))
        assertFalse(legacy.contains("MessageLongPressMenuWidth = 300.dp"))
    }

    @Test
    fun messageInteractionUiLivesInMessagePackage() {
        val interaction = sourceFile("floatingchat/message/MessageInteractionUi.kt")
        assertTrue("Missing extracted message interaction UI source", interaction.isFile)

        val text = interaction.readText()
        assertTrue(text.contains("fun MessageSelectionToggle("))
        assertTrue(text.contains("fun MessageStateBadges("))
        assertTrue(text.contains("fun AiDraftActionOverlay("))
        assertTrue(text.contains("fun AiDraftEditOverlay("))

        val legacy = sourceFile("FloatingChatOverlayUi.kt").readText()
        assertFalse(legacy.contains("internal fun MessageSelectionToggle("))
        assertFalse(legacy.contains("internal fun MessageStateBadges("))
        assertFalse(legacy.contains("private fun AiDraftActionOverlay("))
        assertFalse(legacy.contains("private fun AiDraftEditOverlay("))
    }

    @Test
    fun messageLongPressActionsLiveInMessagePackage() {
        val actions = sourceFile("floatingchat/message/MessageLongPressActions.kt")
        assertTrue("Missing extracted message long press actions", actions.isFile)

        val text = actions.readText()
        assertTrue(text.contains("class MessageLongPressActions("))
        assertTrue(text.contains("fun performLongPressAction("))
        assertTrue(text.contains("fun deleteSelectedMessages("))
        assertTrue(text.contains("fun favoriteSelectedMessages("))
        assertFalse(text.contains("import android."))
        assertFalse(text.contains("import androidx.compose."))

        val legacy = sourceFile("FloatingChatOverlayUi.kt").readText()
        assertFalse(legacy.contains("val performLongPressAction:"))
        assertFalse(legacy.contains("val deleteSelectedMessages ="))
        assertFalse(legacy.contains("val favoriteSelectedMessages ="))
    }

    @Test
    fun messageForwardingUiLivesInMessagePackage() {
        val forwarding = sourceFile("floatingchat/message/MessageForwardingUi.kt")
        assertTrue("Missing extracted message forwarding UI source", forwarding.isFile)

        val text = forwarding.readText()
        assertTrue(text.contains("fun MessageForwardTargetOverlay("))
        assertTrue(text.contains("fun MultiForwardModeOverlay("))
        assertTrue(text.contains("fun ChatHistoryDetailOverlay("))
        assertTrue(text.contains("fun ForwardTargetRow("))
        assertTrue(text.contains("fun ForwardModeRow("))

        val legacy = sourceFile("FloatingChatOverlayUi.kt").readText()
        assertFalse(legacy.contains("private fun MessageForwardTargetOverlay("))
        assertFalse(legacy.contains("private fun ForwardTargetRow("))
        assertFalse(legacy.contains("private fun MultiForwardModeOverlay("))
        assertFalse(legacy.contains("private fun ForwardModeRow("))
        assertFalse(legacy.contains("private fun ChatHistoryDetailOverlay("))
        assertFalse(legacy.contains("private fun ChatHistoryDetailRow("))
    }

    @Test
    fun messageForwardingOverlayHostLivesInMessagePackage() {
        val host = sourceFile("floatingchat/message/MessageForwardingOverlayHost.kt")
        assertTrue("Missing extracted message forwarding overlay host", host.isFile)

        val text = host.readText()
        assertTrue(text.contains("fun MessageForwardingOverlayHost("))
        assertTrue(text.contains("MessageForwardTargetOverlay("))
        assertTrue(text.contains("MultiForwardModeOverlay("))
        assertTrue(text.contains("addCombinedForwardedMessage("))

        val legacy = sourceFile("FloatingChatOverlayUi.kt").readText()
        assertFalse(legacy.contains("forwardMessage?.let { message ->"))
        assertFalse(legacy.contains("if (forwardModeMessages.isNotEmpty())"))
        assertFalse(legacy.contains("if (pendingForwardMessages.isNotEmpty() && pendingForwardMode != null)"))
    }

    @Test
    fun messageInteractionOverlayHostLivesInMessagePackage() {
        val host = sourceFile("floatingchat/message/MessageInteractionOverlayHost.kt")
        assertTrue("Missing extracted message interaction overlay host", host.isFile)

        val text = host.readText()
        assertTrue(text.contains("fun MessageInteractionOverlayHost("))
        assertTrue(text.contains("PaymentDetailOverlay("))
        assertTrue(text.contains("MessageLongPressMenuOverlay("))
        assertTrue(text.contains("MultiSelectActionBar("))
        assertTrue(text.contains("ChatHistoryDetailOverlay("))

        val legacy = sourceFile("FloatingChatOverlayUi.kt").readText()
        assertFalse(legacy.contains("paymentDetailMessage?.let { message ->"))
        assertFalse(legacy.contains("longPressMessage?.let { message ->"))
        assertFalse(legacy.contains("if (multiSelectMode)"))
        assertFalse(legacy.contains("chatHistoryPreviewMessage?.let { message ->"))
    }

    @Test
    fun messageForwardingStateLivesInMessagePackage() {
        val state = sourceFile("floatingchat/message/MessageForwardingState.kt")
        assertTrue("Missing extracted message forwarding state source", state.isFile)

        val text = state.readText()
        assertTrue(text.contains("fun FloatingChatMessage.longPressCopyText("))
        assertTrue(text.contains("fun outgoingTextMessageWithOptionalQuote("))
        assertTrue(text.contains("fun preparedForwardedMessageForSend("))
        assertTrue(text.contains("fun combinedForwardChatHistoryMessage("))
        assertTrue(text.contains("fun combinedForwardChatHistoryOpensDetailPage("))
        assertFalse(text.contains("import androidx.compose."))

        val legacy = sourceFile("FloatingChatOverlayUi.kt").readText()
        assertFalse(legacy.contains("private fun FloatingChatMessage.longPressCopyText("))
        assertFalse(legacy.contains("internal fun outgoingTextMessageWithOptionalQuote("))
        assertFalse(legacy.contains("internal fun preparedForwardedMessageForSend("))
        assertFalse(legacy.contains("internal fun combinedForwardChatHistoryMessage("))
        assertFalse(legacy.contains("private fun chatHistoryTitleForTarget("))
        assertFalse(legacy.contains("private const val CombinedForwardPreviewMaxLines"))
    }

    @Test
    fun messageForwardingActionsLiveInMessagePackage() {
        val actions = sourceFile("floatingchat/message/MessageForwardingActions.kt")
        assertTrue("Missing extracted message forwarding action source", actions.isFile)

        val text = actions.readText()
        assertTrue(text.contains("data class ForwardStartSelection("))
        assertTrue(text.contains("fun forwardStartSelection("))
        assertTrue(text.contains("fun selectedMessagesForAction("))
        assertTrue(text.contains("class MessageForwardingActions("))
        assertTrue(text.contains("fun addForwardedMessage("))
        assertTrue(text.contains("fun addCombinedForwardedMessage("))
        assertFalse(text.contains("import androidx.compose."))

        val legacy = sourceFile("FloatingChatOverlayUi.kt").readText()
        assertFalse(legacy.contains("fun addForwardedMessage("))
        assertFalse(legacy.contains("fun addCombinedForwardedMessage("))
        assertFalse(legacy.contains("fun beginForward("))
        assertFalse(legacy.contains("fun selectedMessagesForAction("))
    }

    @Test
    fun outgoingMessageActionsLiveInMessagePackage() {
        val actions = sourceFile("floatingchat/message/OutgoingMessageActions.kt")
        assertTrue("Missing extracted outgoing message actions", actions.isFile)

        val text = actions.readText()
        assertTrue(text.contains("class OutgoingMessageActions("))
        assertTrue(text.contains("fun addTextMessage("))
        assertTrue(text.contains("fun addVoiceMessage("))
        assertTrue(text.contains("fun addToolMessage("))
        assertTrue(text.contains("fun addAccountCardMessage("))
        assertFalse(text.contains("import android."))
        assertFalse(text.contains("import androidx.compose."))

        val legacy = sourceFile("FloatingChatOverlayUi.kt").readText()
        assertFalse(legacy.contains("FloatingChatPrototype.simulatedOutgoingGroupTextMessage("))
        assertFalse(legacy.contains("FloatingChatPrototype.simulatedOutgoingTextMessage("))
        assertFalse(legacy.contains("FloatingChatPrototype.simulatedOutgoingGroupVoiceMessage("))
        assertFalse(legacy.contains("FloatingChatPrototype.simulatedOutgoingVoiceMessage("))
        assertFalse(legacy.contains("FloatingChatPrototype.simulatedToolMessage("))
        assertFalse(legacy.contains("outgoingTextMessageWithOptionalQuote("))
    }

    @Test
    fun messageCoordinatePaneLivesInChatPackage() {
        val pane = sourceFile("floatingchat/chat/MessageCoordinatePane.kt")
        assertTrue("Missing extracted message coordinate pane", pane.isFile)

        val text = pane.readText()
        assertTrue(text.contains("fun MessageCoordinatePane("))
        assertTrue(text.contains("MessageRow("))
        assertTrue(text.contains("messageListReusableContentType("))

        val legacy = sourceFile("FloatingChatOverlayUi.kt").readText()
        assertFalse(legacy.contains("private fun MessageCoordinatePane("))
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
        assertFalse(legacy.contains("LegacyDocumentReaderContentBody("))
        assertFalse(legacy.contains("LegacyMediaActionSheetHeader("))
    }

    @Test
    fun mediaActionSheetLivesInMediaPackage() {
        val sheet = sourceFile("floatingchat/media/MediaActionSheetOverlay.kt")
        assertTrue("Missing extracted media action sheet", sheet.isFile)

        val text = sheet.readText()
        assertTrue(text.contains("fun FloatingChatMediaActionSheetOverlay("))
        assertTrue(text.contains("enum class FloatingChatMediaAction"))
        assertTrue(text.contains("fun FloatingChatMediaActionIcon("))
        assertTrue(text.contains("fun FloatingChatStandaloneImageQuickActions("))
        assertTrue(text.contains("fun FloatingChatMediaAction.toContract("))

        val legacy = sourceFile("FloatingChatOverlayUi.kt").readText()
        assertFalse(legacy.contains("private fun MediaActionSheetOverlay("))
        assertFalse(legacy.contains("private fun MediaActionIcon("))
        assertFalse(legacy.contains("private fun StandaloneImageQuickActions("))
        assertFalse(legacy.contains("private fun MediaRoundActionButton("))
        assertFalse(legacy.contains("private enum class MediaAction("))
        assertFalse(legacy.contains("private fun MediaAction.toContract("))
    }

    @Test
    fun mediaActionHandlingLivesInMediaPackage() {
        val handler = sourceFile("floatingchat/media/MediaActionHandler.kt")
        assertTrue("Missing extracted media action handler", handler.isFile)

        val text = handler.readText()
        assertTrue(text.contains("fun performMediaAction("))
        assertTrue(text.contains("onFavoriteChanged"))
        assertTrue(text.contains("onOpenActions"))

        val legacy = sourceFile("FloatingChatOverlayUi.kt").readText()
        assertFalse(legacy.contains("internal fun performMediaAction("))
        assertFalse(legacy.contains("private fun shareMedia("))
        assertFalse(legacy.contains("private fun saveMediaToGallery("))
    }

    @Test
    fun mediaActionDispatchActionsLiveInMediaPackage() {
        val actions = sourceFile("floatingchat/media/MediaActionDispatchActions.kt")
        assertTrue("Missing extracted media action dispatch actions", actions.isFile)

        val text = actions.readText()
        assertTrue(text.contains("class MediaActionDispatchActions("))
        assertTrue(text.contains("fun handleMediaAction("))
        assertTrue(text.contains("performMediaAction("))
        assertTrue(text.contains("onActionResult(result)"))
        assertFalse(text.contains("import androidx.compose."))

        val legacy = sourceFile("FloatingChatOverlayUi.kt").readText()
        assertFalse(legacy.contains("val handleMediaAction:"))
        assertFalse(legacy.contains("performMediaAction("))
    }

    @Test
    fun mediaOverlayHostLivesInMediaPackage() {
        val host = sourceFile("floatingchat/media/MediaOverlayHost.kt")
        assertTrue("Missing extracted media overlay host", host.isFile)

        val text = host.readText()
        assertTrue(text.contains("fun MediaOverlayHost("))
        assertTrue(text.contains("FloatingChatMediaActionSheetOverlay("))
        assertTrue(text.contains("FloatingChatMediaPreviewHost("))
        assertTrue(text.contains("DocumentPreviewOverlay("))

        val legacy = sourceFile("FloatingChatOverlayUi.kt").readText()
        assertFalse(legacy.contains("mediaOverlayState.actionMessage?.let { message ->"))
        assertFalse(legacy.contains("runtimeState.previewSession?.let { session ->"))
        assertFalse(legacy.contains("runtimeState.documentPreviewMessage?.let { message ->"))
    }

    @Test
    fun mediaPreviewPolicyLivesInMediaPackage() {
        val policy = sourceFile("floatingchat/media/MediaPreviewPolicy.kt")
        assertTrue("Missing extracted media preview policy", policy.isFile)

        val text = policy.readText()
        assertTrue(text.contains("fun standaloneMediaHeightDp("))
        assertTrue(text.contains("fun mediaPreviewSupportsZoom("))
        assertTrue(text.contains("fun mediaPickerRestoresExpandedOverlayAfterResult("))
        assertTrue(text.contains("fun chatListVideoUsesAspectFit("))

        val legacy = sourceFile("FloatingChatOverlayUi.kt").readText()
        assertFalse(legacy.contains("internal fun standaloneMediaHeightDp("))
        assertFalse(legacy.contains("internal fun mediaPreviewSupportsZoom("))
        assertFalse(legacy.contains("internal fun mediaPickerRestoresExpandedOverlayAfterResult("))
        assertFalse(legacy.contains("internal fun chatListVideoUsesAspectFit("))
        assertFalse(legacy.contains("private const val StandaloneImageMaxWidthDp"))
        assertFalse(legacy.contains("private const val StandaloneMediaMinHeightDp"))
        assertFalse(legacy.contains("private const val StandaloneMediaMaxHeightDp"))
    }

    @Test
    fun mediaOverlayStateLivesInShellPackage() {
        val state = sourceFile("floatingchat/shell/FloatingChatMediaOverlayState.kt")
        assertTrue("Missing extracted media overlay state", state.isFile)

        val text = state.readText()
        assertTrue(text.contains("class FloatingChatMediaOverlayState"))
        assertTrue(text.contains("var actionMessage by mutableStateOf<FloatingChatMessage?>(null)"))
        assertTrue(text.contains("var actionStatus by mutableStateOf<String?>(null)"))
        assertTrue(text.contains("val favoriteMediaIds"))
        assertTrue(text.contains("fun openActions("))
        assertTrue(text.contains("fun closeActions("))
        assertTrue(text.contains("fun applyActionResult("))

        val legacy = sourceFile("FloatingChatOverlayUi.kt").readText()
        assertFalse(legacy.contains("var mediaActionMessage by remember { mutableStateOf<FloatingChatMessage?>(null) }"))
        assertFalse(legacy.contains("var mediaActionStatus by remember { mutableStateOf<String?>(null) }"))
        assertFalse(legacy.contains("var actionStatus by remember { mutableStateOf<String?>(null) }"))
        assertFalse(legacy.contains("val favoriteMediaIds = remember { mutableStateMapOf<String, Boolean>() }"))
        assertFalse(legacy.contains("val favoriteMediaIds = remember { mutableMapOf<String, Boolean>() }"))
    }

    @Test
    fun mediaPreviewOverlaysLiveInMediaPackage() {
        val documentOverlay = sourceFile("floatingchat/media/DocumentPreviewOverlay.kt")
        val previewOverlay = sourceFile("floatingchat/media/MediaPreviewOverlay.kt")
        val previewHost = sourceFile("floatingchat/media/MediaPreviewHost.kt")
        assertTrue("Missing extracted document preview overlay", documentOverlay.isFile)
        assertTrue("Missing extracted media preview overlay", previewOverlay.isFile)
        assertTrue("Missing extracted media preview host", previewHost.isFile)

        val documentText = documentOverlay.readText()
        assertTrue(documentText.contains("fun DocumentPreviewOverlay("))
        assertTrue(documentText.contains("DocumentReaderContentBody("))

        val previewText = previewOverlay.readText()
        assertTrue(previewText.contains("fun MediaPreviewOverlay("))
        assertTrue(previewText.contains("fun MediaPreviewPage("))
        assertTrue(previewText.contains("fun MediaPreviewVideoPlayer("))
        assertTrue(previewText.contains("fun mediaPreviewFrameSize("))
        assertTrue(previewHost.readText().contains("fun FloatingChatMediaPreviewHost("))

        val legacy = sourceFile("FloatingChatOverlayUi.kt").readText()
        assertFalse(legacy.contains("private fun DocumentPreviewOverlay("))
        assertFalse(legacy.contains("private fun MediaPreviewOverlay("))
        assertFalse(legacy.contains("fun FloatingChatMediaPreviewHost("))
        assertFalse(legacy.contains("private fun MediaPreviewPage("))
        assertFalse(legacy.contains("private fun MediaPreviewVideoPlayer("))
        assertFalse(legacy.contains("private data class MediaPreviewFrame("))
        assertFalse(legacy.contains("private fun mediaPreviewFrameSize("))
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
    fun favoriteCollectionStateLivesInToolsPackage() {
        val store = sourceFile("floatingchat/tools/FavoriteCollectionStore.kt")
        assertTrue("Missing extracted favorite collection store", store.isFile)

        val text = store.readText()
        assertTrue(text.contains("data class FavoriteCollectionItem("))
        assertTrue(text.contains("fun favoriteCollectionItems("))
        assertTrue(text.contains("fun selectedFavoriteCollectionItems("))
        assertTrue(text.contains("fun favoriteCollectionItemsAfterRemove("))
        assertTrue(text.contains("fun loadFavoriteCollectionItems("))
        assertTrue(text.contains("fun saveFavoriteCollectionItems("))
        assertTrue(text.contains("fun favoriteCollectionPreviewMessage("))
        assertFalse(text.contains("import androidx.compose."))

        val legacy = sourceFile("FloatingChatOverlayUi.kt").readText()
        assertFalse(legacy.contains("private fun favoriteCollectionItems("))
        assertFalse(legacy.contains("internal data class FavoriteCollectionItem("))
        assertFalse(legacy.contains("private fun loadFavoriteCollectionItems("))
        assertFalse(legacy.contains("private fun saveFavoriteCollectionItems("))
        assertFalse(legacy.contains("private const val FAVORITE_COLLECTION_PREFS"))
        assertFalse(legacy.contains("private const val FavoriteCollectionMaxCount"))
    }

    @Test
    fun favoriteCollectionActionsLiveInToolsPackage() {
        val actions = sourceFile("floatingchat/tools/FavoriteCollectionActions.kt")
        assertTrue("Missing extracted favorite collection actions", actions.isFile)

        val text = actions.readText()
        assertTrue(text.contains("class FavoriteCollectionActions("))
        assertTrue(text.contains("fun removeFavoriteItem("))
        assertTrue(text.contains("fun previewFavoriteItem("))
        assertTrue(text.contains("fun forwardFavoriteItem("))
        assertTrue(text.contains("fun deleteSelectedFavoriteItems("))
        assertTrue(text.contains("fun forwardSelectedFavoriteItems("))

        val legacy = sourceFile("FloatingChatOverlayUi.kt").readText()
        assertFalse(legacy.contains("fun removeFavoriteItem("))
        assertFalse(legacy.contains("fun previewFavoriteItem("))
        assertFalse(legacy.contains("fun forwardFavoriteItem("))
        assertFalse(legacy.contains("fun deleteSelectedFavoriteItems("))
        assertFalse(legacy.contains("fun forwardSelectedFavoriteItems("))
    }

    @Test
    fun favoriteCollectionOverlayHostLivesInToolsPackage() {
        val host = sourceFile("floatingchat/tools/FavoriteCollectionOverlayHost.kt")
        assertTrue("Missing extracted favorite collection overlay host", host.isFile)

        val text = host.readText()
        assertTrue(text.contains("fun FavoriteCollectionOverlayHost("))
        assertTrue(text.contains("FavoriteCollectionPreviewOverlay("))
        assertTrue(text.contains("FavoriteCollectionLongPressMenuOverlay("))

        val legacy = sourceFile("FloatingChatOverlayUi.kt").readText()
        assertFalse(legacy.contains("favoritePreviewItem?.let { item ->"))
        assertFalse(legacy.contains("favoriteLongPressItem?.let { item ->"))
    }

    @Test
    fun favoriteCollectionUiLivesInToolsPackage() {
        val ui = sourceFile("floatingchat/tools/FavoriteCollectionUi.kt")
        assertTrue("Missing extracted favorite collection UI", ui.isFile)

        val text = ui.readText()
        assertTrue(text.contains("fun FavoriteCollectionPreviewOverlay("))
        assertTrue(text.contains("fun FavoriteCollectionLongPressMenuOverlay("))
        assertTrue(text.contains("fun FavoriteCollectionPanel("))
        assertTrue(text.contains("fun favoriteCollectionLongPressActions("))

        val legacy = sourceFile("FloatingChatOverlayUi.kt").readText()
        assertFalse(legacy.contains("private fun FavoriteCollectionPreviewOverlay("))
        assertFalse(legacy.contains("private fun FavoriteCollectionLongPressMenuOverlay("))
        assertFalse(legacy.contains("private fun FavoriteCollectionPanel("))
        assertFalse(legacy.contains("private fun FavoriteCollectionRow("))
        assertFalse(legacy.contains("private fun FavoriteCollectionSelectionBar("))
        assertFalse(legacy.contains("private fun favoriteIconFor("))
        assertFalse(legacy.contains("private fun favoriteAccentColor("))
    }

    @Test
    fun bottomInputUiAndPanelModeLiveOutsideLegacyOverlay() {
        val input = sourceFile("floatingchat/input/BottomInputBar.kt")
        val panelMode = sourceFile("floatingchat/shell/BottomPanelMode.kt")
        assertTrue("Missing extracted bottom input UI", input.isFile)
        assertTrue("Missing extracted bottom panel mode", panelMode.isFile)

        val inputText = input.readText()
        assertTrue(inputText.contains("enum class BottomInputAction"))
        assertTrue(inputText.contains("fun BottomInputBar("))
        assertTrue(inputText.contains("fun bottomInputActionOrder("))
        assertTrue(inputText.contains("fun bottomInputLeadingAction("))

        val modeText = panelMode.readText()
        assertTrue(modeText.contains("enum class BottomPanelMode"))
        assertTrue(modeText.contains("fun BottomPanelMode.isCenteredToolFeaturePanel("))

        val legacy = sourceFile("FloatingChatOverlayUi.kt").readText()
        assertFalse(legacy.contains("private fun BottomInputBar("))
        assertFalse(legacy.contains("private fun QuotedComposerPreview("))
        assertFalse(legacy.contains("private fun AlignedMessageInputField("))
        assertFalse(legacy.contains("private fun BottomIcon("))
        assertFalse(legacy.contains("internal enum class BottomInputAction"))
        assertFalse(legacy.contains("private enum class BottomPanelMode"))
        assertFalse(legacy.contains("private fun BottomPanelMode.isCenteredToolFeaturePanel("))
        assertFalse(legacy.contains("private const val BottomInputBarMinHeightDp"))
    }

    @Test
    fun inputMessageActionsLiveInInputPackage() {
        val actions = sourceFile("floatingchat/input/InputMessageActions.kt")
        assertTrue("Missing extracted input message actions", actions.isFile)

        val text = actions.readText()
        assertTrue(text.contains("class InputMessageActions("))
        assertTrue(text.contains("fun sendTextToCurrentThread("))
        assertTrue(text.contains("fun sendInputMessage("))
        assertTrue(text.contains("fun sendVoiceMessage("))
        assertTrue(text.contains("fun showBlinkInputStatus("))
        assertTrue(text.contains("fun clearBlinkInputStatus("))
        assertFalse(text.contains("import androidx.compose."))

        val legacy = sourceFile("FloatingChatOverlayUi.kt").readText()
        assertFalse(legacy.contains("val sendInputMessage ="))
        assertFalse(legacy.contains("val sendVoiceMessage:"))
        assertFalse(legacy.contains("fun showBlinkInputStatus("))
    }

    @Test
    fun toolActionOrderStateLivesInToolsPackage() {
        val store = sourceFile("floatingchat/tools/ToolActionOrderStore.kt")
        assertTrue("Missing extracted tool action order store", store.isFile)

        val text = store.readText()
        assertTrue(text.contains("fun referenceToolActionsFor("))
        assertTrue(text.contains("fun toolActionOpensBottomPanel("))
        assertTrue(text.contains("fun moveToolAction("))
        assertTrue(text.contains("fun toolReorderTargetIndex("))
        assertTrue(text.contains("fun loadToolActionOrder("))
        assertTrue(text.contains("fun mergeToolActionOrder("))
        assertTrue(text.contains("fun saveToolActionOrder("))
        assertFalse(text.contains("import androidx.compose."))

        val legacy = sourceFile("FloatingChatOverlayUi.kt").readText()
        assertFalse(legacy.contains("internal fun referenceToolActionsFor("))
        assertFalse(legacy.contains("internal fun toolActionOpensBottomPanel("))
        assertFalse(legacy.contains("internal fun moveToolAction("))
        assertFalse(legacy.contains("internal fun toolReorderTargetIndex("))
        assertFalse(legacy.contains("private fun loadToolActionOrder("))
        assertFalse(legacy.contains("internal fun mergeToolActionOrder("))
        assertFalse(legacy.contains("private fun saveToolActionOrder("))
        assertFalse(legacy.contains("private const val TOOL_ORDER_PREFS"))
    }

    @Test
    fun toolActionPresentationLivesInToolsPackage() {
        val presentation = sourceFile("floatingchat/tools/ToolActionPresentation.kt")
        assertTrue("Missing extracted tool action presentation", presentation.isFile)

        val text = presentation.readText()
        assertTrue(text.contains("fun toolActionLabel("))
        assertTrue(text.contains("fun toolActionIcon("))

        val legacy = sourceFile("FloatingChatOverlayUi.kt").readText()
        assertFalse(legacy.contains("internal fun toolActionLabel("))
        assertFalse(legacy.contains("private fun toolActionIcon("))
    }

    @Test
    fun quickPhraseStoreLivesInToolsPackage() {
        val store = sourceFile("floatingchat/tools/QuickPhraseStore.kt")
        assertTrue("Missing extracted quick phrase store", store.isFile)

        val text = store.readText()
        assertTrue(text.contains("fun loadQuickPhrases("))
        assertTrue(text.contains("fun saveQuickPhrases("))
        assertTrue(text.contains("fun normalizeQuickPhrases("))
        assertTrue(text.contains("fun defaultQuickPhrases("))
        assertFalse(text.contains("@Composable"))

        val legacy = sourceFile("FloatingChatOverlayUi.kt").readText()
        assertFalse(legacy.contains("private fun loadQuickPhrases("))
        assertFalse(legacy.contains("private fun saveQuickPhrases("))
        assertFalse(legacy.contains("private fun normalizeQuickPhrases("))
        assertFalse(legacy.contains("private const val QUICK_PHRASE_PREFS"))
        assertFalse(legacy.contains("private val DefaultQuickPhrases"))
    }

    @Test
    fun quickPhraseActionsLiveInToolsPackage() {
        val actions = sourceFile("floatingchat/tools/QuickPhraseActions.kt")
        assertTrue("Missing extracted quick phrase actions", actions.isFile)

        val text = actions.readText()
        assertTrue(text.contains("class QuickPhraseActions("))
        assertTrue(text.contains("fun updateQuickPhrases("))
        assertTrue(text.contains("fun sendQuickPhrase("))
        assertTrue(text.contains("fun addQuickPhrase("))
        assertTrue(text.contains("fun updateQuickPhrase("))
        assertTrue(text.contains("fun deleteQuickPhrase("))
        assertFalse(text.contains("import androidx.compose."))

        val legacy = sourceFile("FloatingChatOverlayUi.kt").readText()
        assertFalse(legacy.contains("fun updateQuickPhrases("))
        assertFalse(legacy.contains("val sendQuickPhrase:"))
        assertFalse(legacy.contains("normalizeQuickPhrases(nextPhrases)"))
    }

    @Test
    fun rightRailLayoutStateLivesInToolsPackage() {
        val state = sourceFile("floatingchat/tools/RightRailLayoutState.kt")
        assertTrue("Missing extracted right rail layout state", state.isFile)

        val text = state.readText()
        assertTrue(text.contains("data class RightRailWeights("))
        assertTrue(text.contains("fun rightRailWeightsForAccountWeight("))
        assertTrue(text.contains("fun rightRailAccountWeightForAccountAreaDrag("))
        assertTrue(text.contains("fun rightRailAccountWeightForToolAreaDrag("))
        assertTrue(text.contains("fun rightRailListContentHeightDp("))
        assertTrue(text.contains("fun rightRailWidthDp("))
        assertTrue(text.contains("fun rightRailToolButtonWidthDp("))
        assertTrue(text.contains("fun rightRailSelectedAccountFirstVisibleIndex("))
        assertFalse(text.contains("import androidx.compose."))

        val legacy = sourceFile("FloatingChatOverlayUi.kt").readText()
        assertFalse(legacy.contains("internal data class RightRailWeights("))
        assertFalse(legacy.contains("internal fun rightRailWeightsForAccountWeight("))
        assertFalse(legacy.contains("internal fun rightRailAccountWeightForAccountAreaDrag("))
        assertFalse(legacy.contains("internal fun rightRailAccountWeightForToolAreaDrag("))
        assertFalse(legacy.contains("internal fun rightRailListContentHeightDp("))
        assertFalse(legacy.contains("internal fun rightRailWidthDp("))
        assertFalse(legacy.contains("internal fun rightRailToolButtonWidthDp("))
        assertFalse(legacy.contains("internal fun rightRailSelectedAccountFirstVisibleIndex("))
    }

    @Test
    fun rightCoordinateRailUiLivesInToolsPackage() {
        val rail = sourceFile("floatingchat/tools/RightCoordinateRail.kt")
        assertTrue("Missing extracted right coordinate rail UI", rail.isFile)

        val text = rail.readText()
        assertTrue(text.contains("fun RightCoordinateRail("))
        assertTrue(text.contains("fun AccountRailAvatarItem("))
        assertTrue(text.contains("fun ToolButton("))
        assertTrue(text.contains("fun RightRailDivider("))
        assertTrue(text.contains("fun toolReorderDraggedTranslationY("))

        val legacy = sourceFile("FloatingChatOverlayUi.kt").readText()
        assertFalse(legacy.contains("private fun RightCoordinateRail("))
        assertFalse(legacy.contains("private fun AccountRailAvatarItem("))
        assertFalse(legacy.contains("private fun ToolButton("))
        assertFalse(legacy.contains("private fun RightRailDivider("))
        assertFalse(legacy.contains("private fun AiffStatusStrip("))
        assertFalse(legacy.contains("private fun toolReorderDraggedTranslationY("))
    }

    @Test
    fun rightRailToolPolicyLivesInToolsPackage() {
        val policy = sourceFile("floatingchat/tools/RightRailToolPolicy.kt")
        assertTrue("Missing extracted right rail tool policy", policy.isFile)

        val text = policy.readText()
        assertTrue(text.contains("fun rightRailToolButtonsUseMaterialIcons("))
        assertTrue(text.contains("fun rightRailToolReorderUsesLongPressDragGesture("))
        assertTrue(text.contains("fun rightRailToolGestureCancelsClickWhenMovedPastTouchSlop("))
        assertTrue(text.contains("fun rightRailKeepsSelectedAccountConnectorAnchorWhenCompressed("))
        assertTrue(text.contains("fun rightRailAccountConnectorAnchorFollowsVirtualOffscreenPosition("))
        assertTrue(text.contains("fun rightRailPinnedSelectedAccountConnectorAnchorYWhenCompressed("))
        assertFalse(text.contains("@Composable"))

        val legacy = sourceFile("FloatingChatOverlayUi.kt").readText()
        assertFalse(legacy.contains("internal fun rightRailToolButtonsUseMaterialIcons("))
        assertFalse(legacy.contains("internal fun rightRailToolReorderUsesLongPressDragGesture("))
        assertFalse(legacy.contains("internal fun rightRailToolGestureCancelsClickWhenMovedPastTouchSlop("))
        assertFalse(legacy.contains("internal fun rightRailKeepsSelectedAccountConnectorAnchorWhenCompressed("))
        assertFalse(legacy.contains("internal fun rightRailAccountConnectorAnchorFollowsVirtualOffscreenPosition("))
        assertFalse(legacy.contains("internal fun rightRailPinnedSelectedAccountConnectorAnchorYWhenCompressed("))
    }

    @Test
    fun toolActionPolicyLivesInToolsPackage() {
        val policy = sourceFile("floatingchat/tools/ToolActionPolicy.kt")
        assertTrue("Missing extracted tool action policy", policy.isFile)

        val text = policy.readText()
        assertTrue(text.contains("fun simulatedMessageToolActions("))
        assertFalse(text.contains("@Composable"))

        val legacy = sourceFile("FloatingChatOverlayUi.kt").readText()
        assertFalse(legacy.contains("internal fun simulatedMessageToolActions("))
    }

    @Test
    fun toolActionDispatchLivesInToolsPackage() {
        val dispatch = sourceFile("floatingchat/tools/ToolActionDispatch.kt")
        assertTrue("Missing extracted tool action dispatch", dispatch.isFile)

        val text = dispatch.readText()
        assertTrue(text.contains("sealed interface ToolActionDispatch"))
        assertTrue(text.contains("fun toolActionDispatchFor("))
        assertTrue(text.contains("data object PickGalleryMedia"))
        assertTrue(text.contains("data object OpenAssistantPanel"))
        assertTrue(text.contains("data class OpenBottomPanel("))
        assertFalse(text.contains("import android."))
        assertFalse(text.contains("import androidx.compose."))

        val legacy = sourceFile("FloatingChatOverlayUi.kt").readText()
        assertFalse(legacy.contains("FloatingChatToolAction.Gallery ->"))
        assertFalse(legacy.contains("FloatingChatToolAction.Blink ->"))
        assertFalse(legacy.contains("FloatingChatToolAction.Camera ->"))
        assertFalse(legacy.contains("FloatingChatToolAction.QuickPhrase ->"))
        assertFalse(legacy.contains("FloatingChatToolAction.MomentMaterials ->"))
        assertFalse(legacy.contains("FloatingChatToolAction.Files ->"))
        assertFalse(legacy.contains("FloatingChatToolAction.Assistant ->"))
        assertFalse(legacy.contains("FloatingChatToolAction.AiVoice ->"))
        assertFalse(legacy.contains("FloatingChatToolAction.Contacts ->"))
    }

    @Test
    fun toolMessageActionsLiveInToolsPackage() {
        val actions = sourceFile("floatingchat/tools/ToolMessageActions.kt")
        assertTrue("Missing extracted tool message actions", actions.isFile)

        val text = actions.readText()
        assertTrue(text.contains("class ToolMessageActions("))
        assertTrue(text.contains("fun pickAccountAvatar("))
        assertTrue(text.contains("fun addToolMessage("))
        assertTrue(text.contains("fun sendAccountCard("))
        assertTrue(text.contains("fun sendToolMessage("))
        assertTrue(text.contains("FloatingChatMediaPickerBridge.requestPick"))
        assertTrue(text.contains("FloatingChatBlinkVoiceBridge.requestCapture"))
        assertFalse(text.contains("import androidx.compose."))

        val legacy = sourceFile("FloatingChatOverlayUi.kt").readText()
        assertFalse(legacy.contains("fun pickAccountAvatar("))
        assertFalse(legacy.contains("fun addToolMessage("))
        assertFalse(legacy.contains("fun sendAccountCard("))
        assertFalse(legacy.contains("val sendToolMessage:"))
        assertFalse(legacy.contains("when (val dispatch = toolActionDispatchFor(action))"))
    }

    @Test
    fun accountProfileStateLivesInAccountPackage() {
        val store = sourceFile("floatingchat/account/AccountProfileStore.kt")
        assertTrue("Missing extracted account profile store", store.isFile)

        val text = store.readText()
        assertTrue(text.contains("data class FloatingChatAccountProfile("))
        assertTrue(text.contains("fun defaultAccountProfileFor("))
        assertTrue(text.contains("fun FloatingChatAccountProfile.toContact("))
        assertTrue(text.contains("fun accountProfileCardMessage("))
        assertTrue(text.contains("fun loadAccountProfile("))
        assertTrue(text.contains("fun saveAccountProfile("))
        assertFalse(text.contains("import androidx.compose."))

        val legacy = sourceFile("FloatingChatOverlayUi.kt").readText()
        assertFalse(legacy.contains("internal data class FloatingChatAccountProfile("))
        assertFalse(legacy.contains("internal fun defaultAccountProfileFor("))
        assertFalse(legacy.contains("fun FloatingChatAccountProfile.toContact("))
        assertFalse(legacy.contains("internal fun accountProfileCardMessage("))
        assertFalse(legacy.contains("private fun loadAccountProfile("))
        assertFalse(legacy.contains("private fun saveAccountProfile("))
        assertFalse(legacy.contains("private const val ACCOUNT_PROFILE_PREFS"))
    }

    @Test
    fun accountProfileUiLivesInAccountPackage() {
        val ui = sourceFile("floatingchat/account/AccountProfileUi.kt")
        assertTrue("Missing extracted account profile UI", ui.isFile)

        val text = ui.readText()
        assertTrue(text.contains("fun AccountEditOverlay("))
        assertTrue(text.contains("fun AccountCardPreviewContent("))
        assertTrue(text.contains("fun AccountCardPickerPanel("))
        assertTrue(text.contains("fun AccountProfileQrPreviewCard("))
        assertTrue(text.contains("fun createAccountQrBitmap("))

        val legacy = sourceFile("FloatingChatOverlayUi.kt").readText()
        assertFalse(legacy.contains("private fun AccountEditOverlay("))
        assertFalse(legacy.contains("private fun AccountEditPanel("))
        assertFalse(legacy.contains("private fun AccountProfileAvatarPreview("))
        assertFalse(legacy.contains("private fun AccountProfileQrPreviewCard("))
        assertFalse(legacy.contains("internal fun AccountCardPreviewContent("))
        assertFalse(legacy.contains("private fun AccountCardPreview("))
        assertFalse(legacy.contains("private fun AccountCardPickerPanel("))
        assertFalse(legacy.contains("private fun createAccountQrBitmap("))
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
    fun scrmContactsPanelLivesInContactsPackage() {
        val panel = sourceFile("floatingchat/contacts/ScrmContactsPanel.kt")
        assertTrue("Missing extracted SCRM contacts panel", panel.isFile)

        val text = panel.readText()
        assertTrue(text.contains("fun ScrmContactsPanel("))
        assertTrue(text.contains("fun WechatStartGroupPanel("))
        assertTrue(text.contains("fun scrmContactGroupSummaries("))
        assertTrue(text.contains("fun scrmDirectAddFriendRequest("))
        assertTrue(text.contains("fun scrmPrivateChatThreadIdForContact("))
        assertTrue(text.contains("fun wechatStartGroupDoneLabel("))
        assertTrue(text.contains("val WechatContactsHeaderBackground"))

        val legacy = sourceFile("FloatingChatOverlayUi.kt").readText()
        assertFalse(legacy.contains("private fun ScrmContactsPanel("))
        assertFalse(legacy.contains("private fun WechatStartGroupPanel("))
        assertFalse(legacy.contains("internal fun scrmContactGroupSummaries("))
        assertFalse(legacy.contains("internal fun scrmDirectAddFriendRequest("))
        assertFalse(legacy.contains("internal fun scrmPrivateChatThreadIdForContact("))
        assertFalse(legacy.contains("private val WechatContactsHeaderBackground"))
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
        assertFalse(legacy.contains("contactProfileIntroAction(event)"))
        assertFalse(legacy.contains("contactProfileEditorAction(event)"))
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
        assertFalse(legacy.contains("private fun GroupMemberSelectionPanel("))
        assertFalse(legacy.contains("private fun GroupMemberPickerRow("))
        assertFalse(legacy.contains("private fun GroupMemberIntroPanel("))
        assertFalse(legacy.contains("groupInfoAction(event)"))
    }

    @Test
    fun groupInfoHostLivesInGroupPackage() {
        val host = sourceFile("floatingchat/group/GroupInfoHost.kt")
        assertTrue("Missing extracted group info host", host.isFile)

        val text = host.readText()
        assertTrue(text.contains("fun GroupInfoHost("))
        assertTrue(text.contains("groupInfoAction(event)"))
        assertTrue(text.contains("submitRemoteGroupTask("))
        assertTrue(text.contains("ScrmContactTaskRunner("))
        assertTrue(text.contains("GroupMemberSelectionPanel("))

        val legacy = sourceFile("FloatingChatOverlayUi.kt").readText()
        assertFalse(legacy.contains("private fun GroupInfoHost("))
        assertFalse(legacy.contains("submitRemoteGroupTask("))
        assertFalse(legacy.contains("groupInfoAction(event)"))
    }

    @Test
    fun groupMemberSelectionPanelLivesInGroupPackage() {
        val panel = sourceFile("floatingchat/group/GroupMemberSelectionPanel.kt")
        assertTrue("Missing extracted group member selection panel", panel.isFile)

        val text = panel.readText()
        assertTrue(text.contains("enum class GroupMemberPickerMode"))
        assertTrue(text.contains("fun GroupMemberSelectionPanel("))
        assertTrue(text.contains("fun GroupMemberPickerRow("))
        assertTrue(text.contains("fun GroupMemberSelectionSearchField("))
        assertTrue(text.contains("fun GroupMemberSelectionButton("))

        val legacy = sourceFile("FloatingChatOverlayUi.kt").readText()
        assertFalse(legacy.contains("private enum class GroupMemberPickerMode"))
        assertFalse(legacy.contains("private fun GroupMemberSelectionPanel("))
        assertFalse(legacy.contains("private fun GroupMemberPickerRow("))
    }

    @Test
    fun contactEditStateLivesInContactsPackage() {
        val state = sourceFile("floatingchat/contacts/ContactEditState.kt")
        assertTrue("Missing extracted contact edit state", state.isFile)

        val text = state.readText()
        assertTrue(text.contains("sealed interface ContactEditorTarget"))
        assertTrue(text.contains("fun mergeContactProfileDraft("))
        assertTrue(text.contains("fun contactProfileKey("))
        assertTrue(text.contains("fun groupProfileKey("))
        assertTrue(text.contains("fun defaultLocalContactProfileFor("))
        assertTrue(text.contains("fun defaultLocalGroupProfileFor("))
        assertFalse(text.contains("import androidx.compose."))

        val legacy = sourceFile("FloatingChatOverlayUi.kt").readText()
        assertFalse(legacy.contains("internal fun mergeContactProfileDraft("))
        assertFalse(legacy.contains("private fun contactProfileKey("))
        assertFalse(legacy.contains("private fun groupProfileKey("))
        assertFalse(legacy.contains("private fun defaultLocalContactProfileFor("))
        assertFalse(legacy.contains("private fun defaultLocalGroupProfileFor("))
        assertFalse(legacy.contains("private sealed interface ContactEditorTarget"))
    }

    @Test
    fun contactPanelPolicyLivesInContactsPackage() {
        val policy = sourceFile("floatingchat/contacts/ContactPanelPolicy.kt")
        assertTrue("Missing extracted contact panel policy", policy.isFile)

        val text = policy.readText()
        assertTrue(text.contains("fun contactsToolOpensCenteredFloatingPanel("))
        assertTrue(text.contains("fun contactPanelMatchesMomentsFloatingSheet("))
        assertTrue(text.contains("fun leftRailAvatarsSupportLongPressEditPanel("))
        assertTrue(text.contains("fun contactEditPanelWechatFieldLabels("))
        assertFalse(text.contains("@Composable"))

        val legacy = sourceFile("FloatingChatOverlayUi.kt").readText()
        assertFalse(legacy.contains("internal fun contactsToolOpensCenteredFloatingPanel("))
        assertFalse(legacy.contains("internal fun contactPanelMatchesMomentsFloatingSheet("))
        assertFalse(legacy.contains("internal fun leftRailAvatarsSupportLongPressEditPanel("))
        assertFalse(legacy.contains("internal fun contactEditPanelWechatFieldLabels("))
    }

    @Test
    fun contactRemoteTaskActionsLiveInContactsPackage() {
        val actions = sourceFile("floatingchat/contacts/ContactRemoteTaskActions.kt")
        assertTrue("Missing extracted contact remote task actions", actions.isFile)

        val text = actions.readText()
        assertTrue(text.contains("class ContactRemoteTaskActions("))
        assertTrue(text.contains("data class GroupMemberAddFriendTaskState("))
        assertTrue(text.contains("fun deleteFriendFromProfile("))
        assertTrue(text.contains("fun addFriendFromGroupMember("))
        assertTrue(text.contains("ScrmContactTaskRunner("))

        val legacy = sourceFile("FloatingChatOverlayUi.kt").readText()
        assertFalse(legacy.contains("fun deleteFriendFromProfile("))
        assertFalse(legacy.contains("fun addFriendFromGroupMember("))
        assertFalse(legacy.contains("ScrmContactTaskRunner("))
        assertFalse(legacy.contains("ScrmAddFriendRequest("))
    }

    @Test
    fun contactProfileHostLivesInContactsPackage() {
        val host = sourceFile("floatingchat/contacts/ContactProfileHost.kt")
        assertTrue("Missing extracted contact profile host", host.isFile)

        val text = host.readText()
        assertTrue(text.contains("fun ContactProfileEditorHost("))
        assertTrue(text.contains("contactProfileEditorAction(event)"))
        assertTrue(text.contains("mergeContactProfileDraft("))

        val legacy = sourceFile("FloatingChatOverlayUi.kt").readText()
        assertFalse(legacy.contains("private fun ContactProfileEditorHost("))
    }

    @Test
    fun contactEditOverlayLivesInContactsPackage() {
        val overlay = sourceFile("floatingchat/contacts/ContactEditOverlay.kt")
        assertTrue("Missing extracted contact edit overlay", overlay.isFile)

        val text = overlay.readText()
        assertTrue(text.contains("fun ContactEditOverlay("))
        assertTrue(text.contains("ContactProfileEditorHost("))
        assertTrue(text.contains("GroupInfoHost("))
        assertTrue(text.contains("GroupMemberScreen("))
        assertTrue(text.contains("groupMemberAction(event)"))

        val legacy = sourceFile("FloatingChatOverlayUi.kt").readText()
        assertFalse(legacy.contains("private fun ContactEditOverlay("))
        assertFalse(legacy.contains("groupMemberAction(event)"))
    }

    @Test
    fun profileEditorOverlayHostLivesInContactsPackage() {
        val host = sourceFile("floatingchat/contacts/ProfileEditorOverlayHost.kt")
        assertTrue("Missing extracted profile editor overlay host", host.isFile)

        val text = host.readText()
        assertTrue(text.contains("fun ProfileEditorOverlayHost("))
        assertTrue(text.contains("ContactEditOverlay("))
        assertTrue(text.contains("AccountEditOverlay("))

        val legacy = sourceFile("FloatingChatOverlayUi.kt").readText()
        assertFalse(legacy.contains("contactEditorTarget?.let { target ->"))
        assertFalse(legacy.contains("accountEditorTarget?.let { account ->"))
    }

    @Test
    fun profilePersistenceActionsLiveInContactsPackage() {
        val actions = sourceFile("floatingchat/contacts/ProfilePersistenceActions.kt")
        assertTrue("Missing extracted profile persistence actions", actions.isFile)

        val text = actions.readText()
        assertTrue(text.contains("class ProfilePersistenceActions("))
        assertTrue(text.contains("fun updateAccountProfile("))
        assertTrue(text.contains("fun updateContactProfile("))
        assertTrue(text.contains("fun updateGroupProfile("))
        assertTrue(text.contains("saveAccountProfile(context, profile)"))
        assertFalse(text.contains("import androidx.compose."))

        val legacy = sourceFile("FloatingChatOverlayUi.kt").readText()
        assertFalse(legacy.contains("fun updateAccountProfile("))
        assertFalse(legacy.contains("fun updateContactProfile("))
        assertFalse(legacy.contains("fun updateGroupProfile("))
    }

    @Test
    fun groupInfoStateLivesInGroupPackage() {
        val state = sourceFile("floatingchat/group/GroupInfoState.kt")
        assertTrue("Missing extracted group info state", state.isFile)

        val text = state.readText()
        assertTrue(text.contains("fun groupInfoMemberCount("))
        assertTrue(text.contains("data class GroupInfoMemberGridItem("))
        assertTrue(text.contains("fun groupInfoMembersForGroup("))
        assertTrue(text.contains("fun groupInviteCandidates("))
        assertTrue(text.contains("fun groupKickCandidates("))
        assertTrue(text.contains("fun groupInfoMemberGridRows("))
        assertTrue(text.contains("fun groupMemberAddFriendStatusText("))
        assertFalse(text.contains("import androidx.compose."))

        val legacy = sourceFile("FloatingChatOverlayUi.kt").readText()
        assertFalse(legacy.contains("internal fun groupInfoMemberCount("))
        assertFalse(legacy.contains("internal data class GroupInfoMemberGridItem("))
        assertFalse(legacy.contains("internal fun groupInfoMembersForGroup("))
        assertFalse(legacy.contains("internal fun groupInviteCandidates("))
        assertFalse(legacy.contains("internal fun groupKickCandidates("))
        assertFalse(legacy.contains("internal fun groupInfoMemberGridRows("))
        assertFalse(legacy.contains("internal fun groupMemberAddFriendStatusText("))
    }

    @Test
    fun groupEditPolicyLivesInGroupPackage() {
        val policy = sourceFile("floatingchat/group/GroupEditPolicy.kt")
        assertTrue("Missing extracted group edit policy", policy.isFile)

        val text = policy.readText()
        assertTrue(text.contains("fun groupAvatarLongPressOpensFloatingEditPanel("))
        assertTrue(text.contains("fun groupEditPanelUsesWechatChatInfoLayout("))
        assertTrue(text.contains("fun groupEditPanelInviteAndKickUseRealScrmApis("))
        assertTrue(text.contains("fun groupEditPanelWechatFieldLabels("))
        assertFalse(text.contains("@Composable"))

        val legacy = sourceFile("FloatingChatOverlayUi.kt").readText()
        assertFalse(legacy.contains("internal fun groupAvatarLongPressOpensFloatingEditPanel("))
        assertFalse(legacy.contains("internal fun groupEditPanelUsesWechatChatInfoLayout("))
        assertFalse(legacy.contains("internal fun groupEditPanelInviteAndKickUseRealScrmApis("))
        assertFalse(legacy.contains("internal fun groupEditPanelWechatFieldLabels("))
    }

    @Test
    fun scrmPanelRoutingLivesInScrmPackage() {
        val routing = sourceFile("scrm/ScrmContactsPanelRouting.kt")
        assertTrue("Missing extracted SCRM panel routing helpers", routing.isFile)

        val text = routing.readText()
        assertTrue(text.contains("fun scrmContactsPanelRouteForSelectedAccount("))
        assertTrue(text.contains("fun scrmRouteCurrentDeviceMismatchMessage("))
        assertTrue(text.contains("fun Throwable.toScrmContactsPanelMessage("))

        val legacy = sourceFile("FloatingChatOverlayUi.kt").readText()
        assertFalse(legacy.contains("internal fun scrmContactsPanelRouteForSelectedAccount("))
        assertFalse(legacy.contains("internal fun scrmRouteCurrentDeviceMismatchMessage("))
        assertFalse(legacy.contains("private fun Throwable.toScrmContactsPanelMessage("))
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
