package com.paifa.ubikitouch.accessibility

import com.paifa.ubikitouch.core.model.FloatingChatThumbnailOrientation
import com.paifa.ubikitouch.core.model.FloatingChatToolAction
import com.paifa.ubikitouch.core.model.FloatingChatPrototype
import com.paifa.ubikitouch.core.model.FloatingChatConnectionTarget
import com.paifa.ubikitouch.core.model.FloatingChatContact
import com.paifa.ubikitouch.core.model.FloatingChatConversation
import com.paifa.ubikitouch.core.model.FloatingChatFileFormat
import com.paifa.ubikitouch.core.model.FloatingChatMessage
import com.paifa.ubikitouch.core.model.FloatingChatMessagePresentation
import com.paifa.ubikitouch.core.model.FloatingChatMessageType
import com.paifa.ubikitouch.core.model.FloatingChatSendState
import com.paifa.ubikitouch.core.model.FloatingChatContactCardKind
import com.paifa.ubikitouch.core.model.GestureAction
import com.paifa.ubikitouch.core.model.gestureMappingOrder
import com.paifa.ubikitouch.accessibility.data.LocalGroupProfile
import com.paifa.ubikitouch.accessibility.floatingchat.account.*
import com.paifa.ubikitouch.accessibility.floatingchat.chat.sessionRailItemKeys
import com.paifa.ubikitouch.accessibility.floatingchat.chat.sessionRailItemKeysByLatestChatTime
import com.paifa.ubikitouch.accessibility.floatingchat.chat.*
import com.paifa.ubikitouch.accessibility.floatingchat.components.avatarTextTagsVisible
import com.paifa.ubikitouch.accessibility.floatingchat.components.resolvedAvatarImageUri
import com.paifa.ubikitouch.accessibility.floatingchat.contacts.*
import com.paifa.ubikitouch.accessibility.floatingchat.group.*
import com.paifa.ubikitouch.accessibility.floatingchat.input.*
import com.paifa.ubikitouch.accessibility.floatingchat.message.MessageHorizontalPlacement
import com.paifa.ubikitouch.accessibility.floatingchat.message.fixedThumbnailHeightDp
import com.paifa.ubikitouch.accessibility.floatingchat.message.messageHorizontalPlacement
import com.paifa.ubikitouch.accessibility.floatingchat.message.messageListInitialFirstVisibleItemIndex
import com.paifa.ubikitouch.accessibility.floatingchat.message.messageListReusableContentType
import com.paifa.ubikitouch.accessibility.floatingchat.message.messageListViewportKey
import com.paifa.ubikitouch.accessibility.floatingchat.message.messageUsesBubbleChrome
import com.paifa.ubikitouch.accessibility.floatingchat.message.*
import com.paifa.ubikitouch.accessibility.floatingchat.message.scrmSendStatusTextFor
import com.paifa.ubikitouch.accessibility.floatingchat.message.shouldRetargetMessageList
import com.paifa.ubikitouch.accessibility.floatingchat.media.*
import com.paifa.ubikitouch.accessibility.floatingchat.moments.*
import com.paifa.ubikitouch.accessibility.floatingchat.shell.FloatingChatOverlayRuntimeState
import com.paifa.ubikitouch.accessibility.floatingchat.shell.*
import com.paifa.ubikitouch.accessibility.floatingchat.tools.*
import com.paifa.ubikitouch.accessibility.scrm.ScrmRecentTaskResults
import com.paifa.ubikitouch.accessibility.scrm.ScrmRequestException
import com.paifa.ubikitouch.accessibility.scrm.ScrmTaskApi
import com.paifa.ubikitouch.accessibility.scrm.ScrmTaskResult
import com.paifa.ubikitouch.accessibility.scrm.ScrmTaskSubmissionResult
import com.paifa.ubikitouch.accessibility.scrm.ScrmFloatingAccountRoute
import com.paifa.ubikitouch.accessibility.scrm.ScrmContact
import com.paifa.ubikitouch.accessibility.scrm.ScrmDevice
import com.paifa.ubikitouch.accessibility.floatingchat.aivoice.*
import com.paifa.ubikitouch.accessibility.scrm.scrmContactsPanelRouteForSelectedAccount
import com.paifa.ubikitouch.accessibility.scrm.scrmRouteCurrentDeviceMismatchMessage
import androidx.compose.ui.geometry.Offset
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class FloatingChatMessageUiContractTest {
    @Test
    fun thumbnailHeightsUseFixedDpValues() {
        assertEquals(120, fixedThumbnailHeightDp(FloatingChatThumbnailOrientation.Vertical))
        assertEquals(56, fixedThumbnailHeightDp(FloatingChatThumbnailOrientation.Horizontal))
        assertEquals(56, fixedThumbnailHeightDp(null))
    }

    @Test
    fun messageListLeavesEnoughBottomClearanceForFloatingInputBar() {
        assertEquals(160, messageListBottomClearanceDp())
        assertEquals(true, messageListUsesKeyboardInsets())
        assertEquals(true, messageListAutoScrollsDuringInput())
        assertEquals(true, messageListAutoScrollsOnInputFocus())
        assertEquals(true, messageListAutoScrollsOnImeInsetChange())
    }

    @Test
    fun outgoingScrmMessagesExposeSendStateLabels() {
        val queued = sendStateMessage(FloatingChatSendState.Queued)
        val succeeded = sendStateMessage(FloatingChatSendState.Succeeded)
        val failed = sendStateMessage(
            FloatingChatSendState.FailedFinal,
            sendErrorMessage = "无权限发送"
        )
        val localOnly = sendStateMessage(FloatingChatSendState.LocalOnly)

        assertEquals("待提交", scrmSendStatusTextFor(queued))
        assertEquals("已发送", scrmSendStatusTextFor(succeeded))
        assertEquals("发送失败：无权限发送", scrmSendStatusTextFor(failed))
        assertEquals(null, scrmSendStatusTextFor(localOnly))
        assertEquals(true, scrmSendStatusRendersOutsideMessageBubble())
    }

    @Test
    fun switchingThreadsRetargetsTheReusedMessageViewportBeforeMeasure() {
        val first = messageListViewportKey(
            selection = ChatThreadSelection.Private("he-miao"),
            selectedAccountId = "account-main",
            homeOverviewVisible = false
        )
        val second = messageListViewportKey(
            selection = ChatThreadSelection.Private("qian-yue"),
            selectedAccountId = "account-main",
            homeOverviewVisible = false
        )

        assertEquals(false, first == second)
        assertEquals(true, shouldRetargetMessageList(first, second))
        assertEquals(false, shouldRetargetMessageList(first, first))
        assertEquals(0, messageListInitialFirstVisibleItemIndex(messageCount = 0))
        assertEquals(4, messageListInitialFirstVisibleItemIndex(messageCount = 5))
        assertEquals(
            0,
            messageListInitialFirstVisibleItemIndex(
                messageCount = 5,
                homeOverviewVisible = true
            )
        )
        assertEquals(
            4,
            messageListInitialFirstVisibleItemIndex(
                messageCount = 5,
                homeOverviewVisible = false
            )
        )
        assertEquals(
            messageListReusableContentType(FloatingChatMessageType.Text),
            messageListReusableContentType(FloatingChatMessageType.FilePreview)
        )
        assertEquals(true, floatingChatOverlayUsesRuntimeSizedCompositionSections())
    }

    private fun sendStateMessage(
        sendState: FloatingChatSendState,
        sendErrorMessage: String? = null
    ): FloatingChatMessage {
        return FloatingChatMessage(
            id = "message-$sendState",
            type = FloatingChatMessageType.Text,
            text = "hello",
            fromMe = true,
            senderName = "me",
            time = "刚刚",
            sendState = sendState,
            sendErrorMessage = sendErrorMessage
        )
    }

    @Test
    fun gestureOverlayUsesConfiguredTriggerBarWidth() {
        assertEquals(1, gestureOverlayThicknessDp(0))
        assertEquals(1, gestureOverlayThicknessDp(1))
        assertEquals(8, gestureOverlayThicknessDp(8))
        assertEquals(24, gestureOverlayThicknessDp(24))
        assertEquals(48, gestureOverlayThicknessDp(48))
        assertEquals(72, gestureOverlayThicknessDp(72))
        assertEquals(96, gestureOverlayThicknessDp(120))
    }

    @Test
    fun gestureOverlayTouchTargetUsesSmallUsableMinimum() {
        assertEquals(1, gestureOverlayThicknessDp(1))
        assertEquals(8, gestureOverlayTouchTargetDp(1))
        assertEquals(8, gestureOverlayTouchTargetDp(8))
        assertEquals(24, gestureOverlayTouchTargetDp(24))
        assertEquals(48, gestureOverlayTouchTargetDp(48))
        assertEquals(96, gestureOverlayTouchTargetDp(120))
    }

    @Test
    fun autoGestureInputModePrefersNativeTouchInteractionOnAndroid13AndNewer() {
        assertEquals(
            ResolvedGestureInputMode.NativeTouchInteraction,
            resolveGestureInputMode(
                requestedMode = GestureInputMode.Auto,
                sdkInt = 33,
                nativeTouchInteractionAvailable = true,
                secureTakeoverApplied = false
            )
        )
        assertEquals(
            ResolvedGestureInputMode.SlimOverlayFallback,
            resolveGestureInputMode(
                requestedMode = GestureInputMode.Auto,
                sdkInt = 32,
                nativeTouchInteractionAvailable = false,
                secureTakeoverApplied = false
            )
        )
        assertEquals(
            ResolvedGestureInputMode.SecureSlimOverlay,
            resolveGestureInputMode(
                requestedMode = GestureInputMode.Auto,
                sdkInt = 32,
                nativeTouchInteractionAvailable = false,
                secureTakeoverApplied = true
            )
        )
    }

    @Test
    fun nativeTouchInteractionModeDoesNotCreateEdgeOverlayWindows() {
        assertEquals(false, shouldCreateGestureOverlayWindows(ResolvedGestureInputMode.NativeTouchInteraction))
        assertEquals(true, shouldCreateGestureOverlayWindows(ResolvedGestureInputMode.SecureSlimOverlay))
        assertEquals(true, shouldCreateGestureOverlayWindows(ResolvedGestureInputMode.SlimOverlayFallback))
    }

    @Test
    fun nativeTouchExplorationKeepsSystemEdgeGesturesWhileFloatingChatIsExpanded() {
        val enabled = NativeTouchInteractionEligibility(
            sdkInt = 34,
            requestedMode = GestureInputMode.Auto,
            runtimeFailed = false,
            globalEnabled = true,
            screenInteractive = true,
            packageBlocked = false,
            paused = false,
            landscapeDisabled = false,
            keyboardDisabled = false
        )

        assertEquals(true, shouldRequestNativeTouchInteraction(enabled, floatingChatExpanded = false))
        assertEquals(true, shouldRequestNativeTouchInteraction(enabled, floatingChatExpanded = true))
        assertEquals(true, nativeTouchRecoveryNeeded(floatingChatExpanded = true, controllerRunning = false))
        assertEquals(true, nativeTouchRecoveryNeeded(floatingChatExpanded = false, controllerRunning = false))
        assertEquals(false, nativeTouchRecoveryNeeded(floatingChatExpanded = false, controllerRunning = true))
        assertEquals(
            false,
            shouldRequestNativeTouchInteraction(
                enabled.copy(globalEnabled = false),
                floatingChatExpanded = false
            )
        )
    }

    @Test
    fun keyboardAlwaysSuspendsNativeTouchExplorationToPreventTypingSpeech() {
        val eligibility = NativeTouchInteractionEligibility(
            sdkInt = 34,
            requestedMode = GestureInputMode.Auto,
            runtimeFailed = false,
            globalEnabled = true,
            screenInteractive = true,
            packageBlocked = false,
            paused = false,
            landscapeDisabled = false,
            keyboardDisabled = true
        )

        assertEquals(false, shouldRequestNativeTouchInteraction(eligibility, floatingChatExpanded = true))
    }

    @Test
    fun nativeTouchInteractionResumesWhenExpandedChatIsCoveredByExternalPicker() {
        val enabled = NativeTouchInteractionEligibility(
            sdkInt = 34,
            requestedMode = GestureInputMode.Auto,
            runtimeFailed = false,
            globalEnabled = true,
            screenInteractive = true,
            packageBlocked = false,
            paused = false,
            landscapeDisabled = false,
            keyboardDisabled = false
        )

        assertEquals(
            false,
            floatingChatOwnsGestureSurface(
                floatingChatExpanded = true,
                externalActivityVisible = true
            )
        )
        assertEquals(
            true,
            shouldRequestNativeTouchInteraction(
                eligibility = enabled,
                floatingChatExpanded = true,
                externalActivityVisible = true
            )
        )
        assertEquals(
            true,
            shouldRequestNativeTouchInteraction(
                eligibility = enabled,
                floatingChatExpanded = true,
                externalActivityVisible = false
            )
        )
    }

    @Test
    fun expandActionRestoresChatWhenAnExternalPickerIsCoveringIt() {
        assertEquals(
            true,
            shouldRestoreFloatingChatFromExternalActivity(
                action = GestureAction.ExpandFloatingChat,
                externalActivityVisible = true
            )
        )
        assertEquals(
            false,
            shouldRestoreFloatingChatFromExternalActivity(
                action = GestureAction.Back,
                externalActivityVisible = true
            )
        )
        assertEquals(
            false,
            shouldRestoreFloatingChatFromExternalActivity(
                action = GestureAction.ExpandFloatingChat,
                externalActivityVisible = false
            )
        )
    }

    @Test
    fun hiddenExpandedChatViewIsRestoredInsteadOfIgnoringExpand() {
        assertEquals(
            true,
            shouldRestoreHiddenExpandedChatView(
                floatingChatExpanded = true,
                chatViewHidden = true
            )
        )
        assertEquals(
            false,
            shouldRestoreHiddenExpandedChatView(
                floatingChatExpanded = false,
                chatViewHidden = true
            )
        )
        assertEquals(
            false,
            shouldRestoreHiddenExpandedChatView(
                floatingChatExpanded = true,
                chatViewHidden = false
            )
        )
    }

    @Test
    fun nativeTouchInteractionKeepsSystemSizedEdgeStartAreaWithoutOverlayBlocking() {
        assertEquals(24, nativeTouchInteractionEdgeStartTargetDp(1))
        assertEquals(24, nativeTouchInteractionEdgeStartTargetDp(8))
        assertEquals(24, nativeTouchInteractionEdgeStartTargetDp(24))
        assertEquals(48, nativeTouchInteractionEdgeStartTargetDp(48))
        assertEquals(96, nativeTouchInteractionEdgeStartTargetDp(120))
    }

    @Test
    fun nativeTouchInteractionHitTestHonorsEdgeZones() {
        val leftConfig = com.paifa.ubikitouch.core.model.EdgeZoneConfig(
            side = com.paifa.ubikitouch.core.model.EdgeSide.LEFT,
            zoneId = 0,
            enabled = true,
            thicknessDp = 1,
            topInsetPercent = 10,
            bottomInsetPercent = 10
        )
        val rightConfig = leftConfig.copy(
            side = com.paifa.ubikitouch.core.model.EdgeSide.RIGHT,
            zoneId = 1
        )

        assertEquals(
            NativeEdgeGestureHit(com.paifa.ubikitouch.core.model.EdgeSide.LEFT, 0),
            nativeEdgeGestureHitTest(
                x = 12f,
                y = 500f,
                screenWidthPx = 1080,
                screenHeightPx = 1000,
                density = 1f,
                leftConfigs = listOf(leftConfig),
                rightConfigs = listOf(rightConfig)
            )
        )
        assertEquals(
            NativeEdgeGestureHit(com.paifa.ubikitouch.core.model.EdgeSide.RIGHT, 1),
            nativeEdgeGestureHitTest(
                x = 1068f,
                y = 500f,
                screenWidthPx = 1080,
                screenHeightPx = 1000,
                density = 1f,
                leftConfigs = listOf(leftConfig),
                rightConfigs = listOf(rightConfig)
            )
        )
        assertEquals(
            null,
            nativeEdgeGestureHitTest(
                x = 60f,
                y = 500f,
                screenWidthPx = 1080,
                screenHeightPx = 1000,
                density = 1f,
                leftConfigs = listOf(leftConfig),
                rightConfigs = listOf(rightConfig)
            )
        )
        assertEquals(
            null,
            nativeEdgeGestureHitTest(
                x = 12f,
                y = 40f,
                screenWidthPx = 1080,
                screenHeightPx = 1000,
                density = 1f,
                leftConfigs = listOf(leftConfig),
                rightConfigs = listOf(rightConfig)
            )
        )
    }

    @Test
    fun nativeTouchDelegationIsRequestedOnlyOncePerGesture() {
        assertEquals(
            true,
            shouldRequestNativeTouchDelegation(
                alreadyDelegated = false,
                platformIsDelegating = false
            )
        )
        assertEquals(
            false,
            shouldRequestNativeTouchDelegation(
                alreadyDelegated = true,
                platformIsDelegating = false
            )
        )
        assertEquals(
            false,
            shouldRequestNativeTouchDelegation(
                alreadyDelegated = false,
                platformIsDelegating = true
            )
        )
    }

    @Test
    fun nativeTouchLeavesExpandedChatEdgesToInternalGestureSurface() {
        val leftConfig = com.paifa.ubikitouch.core.model.EdgeZoneConfig(
            side = com.paifa.ubikitouch.core.model.EdgeSide.LEFT,
            zoneId = 0,
            enabled = true,
            thicknessDp = 1,
            topInsetPercent = 10,
            bottomInsetPercent = 20
        )
        val rightConfig = leftConfig.copy(
            side = com.paifa.ubikitouch.core.model.EdgeSide.RIGHT,
            zoneId = 1,
            thicknessDp = 32,
            topInsetPercent = 20,
            bottomInsetPercent = 10
        )
        val config = NativeEdgeGestureConfig(
            screenWidthPx = 1080,
            screenHeightPx = 2000,
            density = 1f,
            leftConfigs = listOf(leftConfig),
            rightConfigs = listOf(rightConfig),
            shortThresholdPx = 24f,
            longThresholdPx = 72f
        )

        assertEquals(
            listOf(
                NativeTouchInterceptRect(
                    side = com.paifa.ubikitouch.core.model.EdgeSide.LEFT,
                    zoneId = 0,
                    left = 0,
                    top = 200,
                    right = 24,
                    bottom = 1600
                ),
                NativeTouchInterceptRect(
                    side = com.paifa.ubikitouch.core.model.EdgeSide.RIGHT,
                    zoneId = 1,
                    left = 1048,
                    top = 400,
                    right = 1080,
                    bottom = 1800
                )
            ),
            nativeTouchInterceptRects(config, floatingChatExpanded = false)
        )
        assertEquals(
            nativeTouchInterceptRects(config, floatingChatExpanded = false),
            nativeTouchInterceptRects(config, floatingChatExpanded = true)
        )
    }

    @Test
    fun nativeBackTakeoverDisablesSystemSideBackInsets() {
        assertEquals(
            listOf(
                SecureSettingOverride("back_gesture_inset_scale_left", "0"),
                SecureSettingOverride("back_gesture_inset_scale_right", "0"),
                SecureSettingOverride("hide_navigationbar_enable", "2")
            ),
            nativeBackGestureTakeoverSettings()
        )
    }

    @Test
    fun edgeBackWaveUsesContinuousCurveInsteadOfDelayedArrow() {
        assertEquals(true, backWaveShowsFromFirstDragPixel())
        assertEquals(true, backWaveUsesBezierCurveSurface())
        assertEquals(true, backWaveUsesRounderCurveSurface())
        assertEquals(true, backWaveUsesRubberBulgeSurface())
        assertEquals(true, backWaveBendsWithFingerY())
        assertEquals(true, backWaveUsesPureEdgeParabolicShape())
        assertEquals(false, backWaveUsesAospStyleEdgeArrow())
        assertEquals(false, backWaveDrawsArrowGlyph())
        assertEquals(true, backWaveDrawsDirectionArrowGlyph())
        assertEquals(false, backWaveDrawsLargeArrowGlyph())
        assertEquals(true, backWaveUsesParabolicTrajectory())
        assertEquals(true, backWaveSamplesParabolaBeforeFill())
        assertEquals(true, backWaveUsesBubbleHighlight())
        assertEquals(true, backWaveUsesSubtleDirectionCue())
        assertEquals(true, backWaveLongDistanceHasStrongerDeformation())
        assertEquals(true, backWaveUsesContinuousStretchInsteadOfLongThresholdJump())
        assertEquals(true, backWaveVerticalPullStretchesParabola())
        assertEquals(true, backWaveKeepsOuterSizeWhileLineWarps())
        assertEquals(true, backWaveAppearsContinuouslyFromZeroToMaxSize())
        assertEquals(true, backWaveDistinguishesShortAndLongDistance())
        assertEquals(true, backWaveDiagonalGesturesUseShortLongAnimation())
        assertEquals(true, backWaveVisualProgressUsesLongThreshold())
    }

    @Test
    fun inwardPullDistanceThresholdsAreConfigurable() {
        assertEquals(24, defaultShortPullThresholdDp())
        assertEquals(72, defaultLongPullThresholdDp())
        assertEquals(8, sanitizeShortPullThresholdDp(1))
        assertEquals(40, sanitizeShortPullThresholdDp(40))
        assertEquals(32, sanitizeLongPullThresholdDp(shortThresholdDp = 24, longThresholdDp = 12))
        assertEquals(220, sanitizeLongPullThresholdDp(shortThresholdDp = 24, longThresholdDp = 220))
        assertEquals(320, sanitizeLongPullThresholdDp(shortThresholdDp = 24, longThresholdDp = 420))
        assertEquals(
            listOf(
                com.paifa.ubikitouch.core.model.GestureType.PULL_INWARD_SHORT,
                com.paifa.ubikitouch.core.model.GestureType.PULL_INWARD_LONG,
                com.paifa.ubikitouch.core.model.GestureType.PULL_DIAGONAL_UP_SHORT,
                com.paifa.ubikitouch.core.model.GestureType.PULL_DIAGONAL_UP_LONG,
                com.paifa.ubikitouch.core.model.GestureType.PULL_DIAGONAL_DOWN_SHORT,
                com.paifa.ubikitouch.core.model.GestureType.PULL_DIAGONAL_DOWN_LONG,
                com.paifa.ubikitouch.core.model.GestureType.PULL_INWARD_HOLD,
                com.paifa.ubikitouch.core.model.GestureType.SWIPE_UP,
                com.paifa.ubikitouch.core.model.GestureType.SWIPE_DOWN
            ),
            gestureMappingOrder()
        )
    }

    @Test
    fun groupChatMemberRailUsesOnlyUserMessageSenders() {
        val conversation = FloatingChatPrototype.sampleConversation()
        val members = groupMemberRailContacts(
            contacts = conversation.contacts,
            messages = FloatingChatPrototype.groupMessagesFor(conversation)
        )

        assertEquals(
            listOf("li-si", "xiao-chen", "wang-wu", "zhao-liu"),
            members.map { contact -> contact.id }
        )
        assertEquals(28, groupMemberAvatarSizeDp())
    }

    @Test
    fun groupChatAvatarUsesWechatStyleNineGridMemberImages() {
        val group = FloatingChatContact(
            id = "group",
            name = "Group",
            initials = "G",
            description = "Group",
            avatarColor = 0xFF5B7CFA,
            avatarUrl = "https://cdn.example.com/group.png",
            groupMemberAvatarUrls = (1..10).map { index -> "https://cdn.example.com/member-$index.png" }
        )

        assertEquals(9, groupChatAvatarGridMaxMembers())
        assertEquals(
            (1..9).map { index -> "https://cdn.example.com/member-$index.png" },
            groupChatAvatarGridImageUris(group)
        )
        assertEquals(
            (1..9).map { index -> "https://cdn.example.com/member-$index.png" },
            groupChatAvatarDisplayImageUris(group)
        )
        assertEquals(true, groupChatAvatarUsesNineGridMemberImages())
    }

    @Test
    fun groupChatAvatarFallsBackToRemoteRoomAvatarWhenMemberImagesAreMissing() {
        val group = FloatingChatContact(
            id = "group",
            name = "Group",
            initials = "G",
            description = "Group",
            avatarColor = 0xFF5B7CFA,
            avatarUrl = "http://mmbiz.qpic.cn/room-avatar.png"
        )

        assertEquals(emptyList<String>(), groupChatAvatarGridImageUris(group))
        assertEquals(
            listOf("https://mmbiz.qpic.cn/room-avatar.png"),
            groupChatAvatarDisplayImageUris(group)
        )
        assertEquals(true, groupChatAvatarSingleFallbackImageFillsTile())
    }

    @Test
    fun blankLocalAvatarDoesNotMaskRemoteApiAvatar() {
        assertEquals(
            "https://cdn.example.com/account.png",
            resolvedAvatarImageUri(
                localImageUri = "",
                remoteAvatarUrl = "https://cdn.example.com/account.png"
            )
        )
        assertEquals(
            "content://local/account.png",
            resolvedAvatarImageUri(
                localImageUri = "content://local/account.png",
                remoteAvatarUrl = "https://cdn.example.com/account.png"
            )
        )
    }

    @Test
    fun runtimeConversationUpdateCarriesRefreshedAccountAndThreadState() {
        val state = FloatingChatOverlayRuntimeState()
        val conversation = FloatingChatConversation(
            peerName = "Account B",
            accountName = "Account B",
            contacts = listOf(
                FloatingChatContact("account-b::contact-1", "Contact 1", "C1", "Account B", 0xFF1B9AAA)
            ),
            accountContacts = listOf(
                FloatingChatContact("account-a", "Account A", "A", "Account", 0xFF3A86FF),
                FloatingChatContact("account-b", "Account B", "B", "Account", 0xFF06D6A0, selected = true)
            ),
            messages = emptyList(),
            toolActions = emptyList(),
            groupContacts = listOf(
                FloatingChatContact("account-b::group-1", "Group 1", "G1", "Account B", 0xFF5B7CFA)
            )
        )

        state.deliverConversationUpdate(
            conversation = conversation,
            selectedAccountId = "account-b",
            selectedThread = ChatThreadSelection.GroupChat("account-b::group-1")
        )
        val firstEvent = state.conversationUpdateEvent

        assertEquals(conversation, firstEvent?.conversation)
        assertEquals("account-b", firstEvent?.selectedAccountId)
        assertEquals(ChatThreadSelection.GroupChat("account-b::group-1"), firstEvent?.selectedThread)
        assertEquals(ChatThreadSelection.GroupChat("account-b::group-1"), state.selectedThread)

        state.deliverConversationUpdate(
            conversation = conversation,
            selectedAccountId = "account-a",
            selectedThread = ChatThreadSelection.Private("account-a::contact-1")
        )

        assertEquals((firstEvent?.token ?: 0L) + 1L, state.conversationUpdateEvent?.token)
        state.clearConversationUpdate(state.conversationUpdateEvent?.token ?: -1L)
        assertEquals(null, state.conversationUpdateEvent)
    }

    @Test
    fun groupChatUserConnectorsUseMemberRailInsteadOfSessionRail() {
        assertEquals(
            ConnectorAvatarLane.GroupMember,
            connectorAvatarLaneFor(
                selection = ChatThreadSelection.Group,
                target = FloatingChatConnectionTarget.User
            )
        )
        assertEquals(
            ConnectorAvatarLane.Session,
            connectorAvatarLaneFor(
                selection = ChatThreadSelection.Private("li-si"),
                target = FloatingChatConnectionTarget.User
            )
        )
        assertEquals(
            ConnectorAvatarLane.Account,
            connectorAvatarLaneFor(
                selection = ChatThreadSelection.Group,
                target = FloatingChatConnectionTarget.Account
            )
        )
    }

    @Test
    fun localContentUrisAreRecognizedForRealMediaRendering() {
        assertEquals(true, isLocalContentUri("content://media/external/images/media/42"))
        assertEquals(true, isLocalContentUri("content://media/external/video/media/77"))
        assertEquals(false, isLocalContentUri("https://aiff.app/images/demo.jpg"))
        assertEquals(false, isLocalContentUri(null))
    }

    @Test
    fun localCacheFileUrisAreRecognizedForRealMediaRendering() {
        assertEquals(true, isLocalMediaUri("content://media/external/images/media/42"))
        assertEquals(true, isLocalMediaUri("file:///data/user/0/com.paifa.ubikitouch/cache/floating-chat/image.jpg"))
        assertEquals(false, isLocalMediaUri("https://aiff.app/images/demo.jpg"))
        assertEquals(false, isLocalMediaUri(null))
    }

    @Test
    fun remoteAvatarLoadingUsesBoundedNetworkAndMemoryLimits() {
        assertEquals(true, isRemoteImageUri("https://example.com/avatar.jpg"))
        assertEquals(true, isRemoteImageUri("http://112.74.164.233/avatar.jpg"))
        assertEquals(
            "https://mmbiz.qpic.cn/mmhead/avatar.jpg",
            normalizedRemoteImageUri("http://mmbiz.qpic.cn/mmhead/avatar.jpg")
        )
        assertEquals(
            "https://wx.qlogo.cn/mmhead/avatar.jpg",
            normalizedRemoteImageUri("http://wx.qlogo.cn/mmhead/avatar.jpg")
        )
        assertEquals(
            "http://112.74.164.233/avatar.jpg",
            normalizedRemoteImageUri("http://112.74.164.233/avatar.jpg")
        )
        assertEquals(null, normalizedRemoteImageUri(null))
        assertEquals(2_500, remoteImageConnectTimeoutMillis())
        assertEquals(3_500, remoteImageReadTimeoutMillis())
        assertEquals(2 * 1024 * 1024, remoteImageMaxBytes())
        assertEquals(96, avatarImageDecodeMaxSizePx())
        assertEquals(true, avatarImageLoadsUseDedicatedSmallDecodeSize())
        assertEquals(2, remoteAvatarMaxConcurrentLoads())
        assertEquals(10 * 60 * 1000, remoteImageFailureRetryDelayMillis())
        assertEquals(
            true,
            remoteImageRetrySuppressedByRecentFailure(
                lastFailureUptimeMillis = 10_000L,
                nowUptimeMillis = 12_000L,
                retryDelayMillis = remoteImageFailureRetryDelayMillis().toLong()
            )
        )
        assertEquals(
            false,
            remoteImageRetrySuppressedByRecentFailure(
                lastFailureUptimeMillis = 10_000L,
                nowUptimeMillis = 10_000L + remoteImageFailureRetryDelayMillis() + 1L,
                retryDelayMillis = remoteImageFailureRetryDelayMillis().toLong()
            )
        )
    }

    @Test
    fun leftRailDropsDuplicateSessionItemsBeforeLazyColumnKeysAreBuilt() {
        val duplicateContact = FloatingChatContact(
            id = "contact-scrm-account:a8f5bb37d42cc17e:wxid_os5j5pjwy5so22__scrm-contact:qq-13462583081",
            name = "Duplicate",
            initials = "D",
            description = "Friend",
            avatarColor = 0xFF1B9AAA
        )
        val duplicateGroup = FloatingChatContact(
            id = "account__scrm-group:room@chatroom",
            name = "Room",
            initials = "R",
            description = "Group",
            avatarColor = 0xFF5B7CFA
        )

        val keys = sessionRailItemKeys(
            groups = listOf(duplicateGroup, duplicateGroup.copy(avatarUrl = "https://cdn.example.com/room.png")),
            contacts = listOf(duplicateContact, duplicateContact.copy(avatarUrl = "https://cdn.example.com/contact.png"))
        )

        assertEquals(keys.distinct(), keys)
        assertEquals(2, keys.size)
    }

    @Test
    fun allAccountHomeOverviewAggregationIsDeferredUntilHomeIsVisible() {
        assertEquals(false, shouldBuildAllAccountHomeOverview(homeOverviewVisible = false))
        assertEquals(true, shouldBuildAllAccountHomeOverview(homeOverviewVisible = true))
    }

    @Test
    fun mediaWatermarkPrefersOriginalResourceUriOverCachedPreviewUri() {
        assertEquals(
            "content://media/external/images/media/42",
            mediaWatermarkText(
                resourceUrl = "content://media/external/images/media/42",
                thumbnailUrl = "file:///data/user/0/com.paifa.ubikitouch/cache/floating-chat/image.jpg"
            )
        )
        assertEquals(
            "https://aiff.app/images/demo.jpg",
            mediaWatermarkText(
                resourceUrl = null,
                thumbnailUrl = "https://aiff.app/images/demo.jpg"
            )
        )
    }

    @Test
    fun standaloneMediaMessagesSkipBubbleChrome() {
        assertEquals(false, messageUsesBubbleChrome(FloatingChatMessagePresentation.MediaStandalone))
        assertEquals(true, messageUsesBubbleChrome(FloatingChatMessagePresentation.Bubble))
        assertEquals(true, messageUsesBubbleChrome(FloatingChatMessagePresentation.SpecialCard))
        assertEquals(false, messageUsesBubbleChrome(FloatingChatMessagePresentation.System))
    }

    @Test
    fun standaloneMediaActionsOnlyShowInPreview() {
        assertEquals(false, standaloneMediaShowsInlineActions())
        assertEquals(true, mediaPreviewShowsActions())
        assertEquals(true, mediaPreviewUsesScrim())
        assertEquals(false, mediaPreviewUsesCardShadow())
    }

    @Test
    fun chatListStandaloneMediaIsClean() {
        assertEquals(false, messageBubbleShowsAccessChips())
        assertEquals(false, standaloneMediaListShowsAccessChips())
        assertEquals(false, standaloneMediaListShowsWatermark())
        assertEquals(false, standaloneMediaListUsesVerticalCropScrim())
        assertEquals(true, mediaPreviewShowsAccessChips())
        assertEquals(false, mediaPreviewShowsWatermark())
        assertEquals(false, mediaPreviewUsesVerticalCropScrim())
    }

    @Test
    fun mediaPreviewButtonsExposeRealActions() {
        assertEquals(
            setOf(
                MediaActionContract.AnalyzeImage,
                MediaActionContract.FindObject,
                MediaActionContract.Share,
                MediaActionContract.Save,
                MediaActionContract.Favorite,
                MediaActionContract.More
            ),
            mediaPreviewActionContracts()
        )
    }

    @Test
    fun mediaPreviewUsesOriginalAspectFitSizing() {
        assertEquals(true, mediaPreviewUsesAspectFit())
        assertEquals(false, mediaPreviewUsesCropScaling())
        assertEquals(1f, mediaPreviewMaxWidthFraction())
        assertEquals(1f, mediaPreviewMaxHeightFraction())
    }

    @Test
    fun mediaPreviewMatchesWechatFullscreenChrome() {
        assertEquals(true, mediaPreviewUsesBlackBackdrop())
        assertEquals(false, mediaPreviewHidesFloatingChatButton())
        assertEquals(true, mediaPreviewCoversSystemBars())
        assertEquals(true, mediaPreviewRequestsImmersiveSystemBars())
        assertEquals(true, mediaPreviewOverlayAvoidsDecorInsets())
        assertEquals(false, mediaPreviewUsesDedicatedFullscreenActivity())
        assertEquals(false, mediaPreviewKeepsAccessibilityOverlayHidden())
        assertEquals(true, mediaPreviewImmersiveUsesDecorViewInsetsController())
        assertEquals(true, mediaPreviewRunsInsideFloatingOverlay())
        assertEquals(true, mediaPreviewRestoresOverlayWithoutRecreate())
    }

    @Test
    fun momentsTimelineMediaUsesInPlaceFullscreenPreview() {
        assertEquals(false, momentsTimelineRowsOpenDetail())
        assertEquals(true, momentsTimelineMediaOpensFullscreenPreview())
        assertEquals(true, momentsTimelineKeepsCurrentPageForTextAndActions())
        assertEquals(true, momentsTimelineReusesChatMediaPreview())
    }

    @Test
    fun momentsTimelineUsesScrmDataInsteadOfSimulatedDefaults() {
        assertEquals(true, momentsTimelineUsesRemoteScrmSource())
        assertEquals(false, momentsTimelineSeedsWechatSimulationPosts())

        val data = Json.parseToJsonElement(
            """
            {
              "moments": [{
                "circleId": 123,
                "nickname": "Alice",
                "content": "real moment",
                "publishTime": 1710000000,
                "images": ["https://cdn.example.com/moment.jpg"],
                "likedBy": ["Bob"],
                "comments": [{"nickname":"Cindy","content":"nice"}]
              }]
            }
            """.trimIndent()
        )
        val posts = scrmMomentPostsFromTaskData(data)

        assertEquals(1, posts.size)
        assertEquals("scrm-moment:123", posts.single().id)
        assertEquals(123L, scrmCircleIdForMomentPostId(posts.single().id))
        assertEquals("Alice", posts.single().author)
        assertEquals("real moment", posts.single().content)
        assertEquals(MomentMediaKind.Image, posts.single().media?.kind)
        assertEquals("https://cdn.example.com/moment.jpg", posts.single().media?.uri)
        assertEquals(listOf("Bob"), posts.single().likedBy)
        assertEquals(AppMomentComment("Cindy", "nice"), posts.single().comments.single())
    }

    @Test
    fun scrmMomentParserDropsSyntheticSnsSendOkComments() {
        val data = Json.parseToJsonElement(
            """
            {
              "moments": [{
                "circleId": 124,
                "nickname": "Alice",
                "content": "real moment",
                "comments": [
                  {"nickname":"Alice","content":"sns_send_ok"},
                  {"nickname":"Bob","content":"real reply"}
                ]
              }]
            }
            """.trimIndent()
        )

        val post = scrmMomentPostsFromTaskData(data).single()

        assertEquals(listOf(AppMomentComment("Bob", "real reply")), post.comments)
    }

    @Test
    fun submittedMomentLocalPostShowsDraftWithoutSyntheticComment() {
        val media = AppMomentMedia(
            kind = MomentMediaKind.Image,
            uri = "content://local/moment.jpg",
            previewUri = "content://local/moment-preview.jpg",
            label = "moment.jpg"
        )

        val post = localScrmMomentPostForSubmittedDraft(
            clientRequestId = "moment-request-1",
            weChatId = "wxid_account",
            content = " posted body ",
            media = media,
            createdAt = 123L
        )

        assertEquals("local-scrm-moment:moment-request-1", post.id)
        assertEquals("\u6211", post.author)
        assertEquals("posted body", post.content)
        assertEquals(media, post.media)
        assertEquals(123L, post.createdAt)
        assertEquals(emptyList<AppMomentComment>(), post.comments)
        assertFalse(post.comments.any { comment -> comment.text == "sns_send_ok" })
    }

    @Test
    fun momentPublishCanTreatMissingRecentTaskResultAsAcceptedPending() {
        val taskApi = object : ScrmTaskApi {
            override fun getTask(taskId: Long): ScrmTaskResult {
                throw ScrmRequestException(
                    statusCode = 404,
                    message = "未找到该 taskId 的近期结果，可能 Android 尚未回包"
                )
            }

            override fun getRecentTasks(
                deviceUuid: String?,
                count: Int
            ): ScrmRecentTaskResults {
                return ScrmRecentTaskResults(count = count)
            }
        }

        val outcome = submitScrmMomentTaskAndAwait(
            taskApi = taskApi,
            treatMissingRecentTaskAsAccepted = true,
            maxPollAttempts = 1,
            sleepMillis = {}
        ) {
            ScrmTaskSubmissionResult(
                taskId = 301L,
                success = true,
                message = "accepted"
            )
        }

        assertEquals(301L, outcome.taskId)
        assertEquals(false, outcome.completed)
        assertEquals(emptyList<Any>(), outcome.data)
        assertEquals(true, outcome.message.contains("301"))
    }

    @Test
    fun expandedFloatingChatDoesNotCollapseOnBlankAreaClick() {
        assertEquals(false, floatingChatBlankAreaClickCollapsesOverlay())
        assertEquals(true, floatingChatBackKeyCollapsesOverlay())
        assertEquals(true, floatingChatBlankAreaClickHidesKeyboard())
        assertEquals(false, floatingChatBlankAreaClickHidesKeyboardWhenInputNotFocused())
    }

    @Test
    fun floatingChatExpansionReusesRetainedComposition() {
        assertEquals(true, floatingChatCollapseRetainsExpandedComposition())
        assertEquals(true, floatingChatExpandReusesRetainedComposeView())
        assertEquals(true, floatingChatRetainedCollapsedOverlayIsNotTouchable())
    }

    @Test
    fun mediaPickerRestoresCurrentFloatingChatThread() {
        val conversation = FloatingChatPrototype.sampleConversation()

        assertEquals(true, mediaPickerDoesNotCollapseFloatingChatToButton())
        assertEquals(true, mediaPickerHidesWholeFloatingChatWindow())
        assertEquals(true, mediaPickerKeepsFloatingChatWindowPersistent())
        assertEquals(true, cameraToolKeepsFloatingChatWindowPersistent())
        assertEquals(false, mediaPickerShowsLightweightTransitionSurface())
        assertEquals(true, mediaPickerRestoresExpandedOverlayAfterResult())
        assertEquals(true, mediaPickerRestoresOverlayBeforeDeliveringPickedMedia())
        assertEquals(true, mediaPickerProcessesPickedMediaOffMainThread())
        assertEquals(
            ChatThreadSelection.Private("li-si"),
            initialChatThreadSelection(
                conversation = conversation,
                preferredSelection = ChatThreadSelection.Private("li-si")
            )
        )
        assertEquals(
            ChatThreadSelection.GroupChat("group-product"),
            initialChatThreadSelection(
                conversation = conversation,
                preferredSelection = ChatThreadSelection.Private("missing-contact")
            )
        )
        assertEquals(
            ChatThreadSelection.Private("li-si"),
            FloatingChatPrototype.ToolThreadSelection.Private("li-si").toChatThreadSelection()
        )
    }

    @Test
    fun textChatBubblesReuseImModuleDesignTokens() {
        assertEquals(0x40FFFFFF, imModuleSelfBubbleColorArgb())
        assertEquals(0x00000000, imModuleOtherBubbleColorArgb())
        assertEquals(0x4DFFFFFF, imModuleSelfBubbleBorderColorArgb())
        assertEquals(0x80FFFFFF.toInt(), imModuleOtherBubbleBorderColorArgb())
        assertEquals(0xF8F8FCFF.toInt(), imModuleBubbleTextColorArgb())
        assertEquals(0xE6000000.toInt(), imModuleBubbleShadowColorArgb())
        assertEquals(true, imModuleBubbleUsesDemoGlassEffect())
        assertEquals(20, imModuleSelfBubbleBackdropBlurDp())
        assertEquals(8, imModuleSelfBubbleShadowOffsetYDp())
        assertEquals(32, imModuleSelfBubbleShadowBlurDp())
        assertEquals(true, imModuleOtherBubbleIsTransparentWithHalfBorder())
        assertEquals(true, cardMessageTextUsesImModuleShadow())
        assertEquals(true, resourceUrlTextUsesImModuleShadow())
        assertEquals(true, chipTextUsesImModuleShadow())
        assertEquals(true, inlineCardTextUsesImModuleShadow())
        assertEquals(true, systemPromptMessageUsesTextOnly())
        assertEquals(true, systemPromptTextUsesShadow())
        assertEquals(true, messageTypeUsesImModuleBubble(FloatingChatMessageType.Text))
        assertEquals(true, messageTypeUsesImModuleBubble(FloatingChatMessageType.MixedText))
        assertEquals(true, messageTypeUsesImModuleBubble(FloatingChatMessageType.Quote))
        assertEquals(true, messageTypeUsesImModuleBubble(FloatingChatMessageType.ContactLink))
        assertEquals(true, messageTypeUsesImModuleBubble(FloatingChatMessageType.Location))
        assertEquals(true, messageTypeUsesImModuleBubble(FloatingChatMessageType.MiniProgramLink))
        assertEquals(true, messageTypeUsesImModuleBubble(FloatingChatMessageType.FilePreview))
        assertEquals(true, messageTypeUsesImModuleBubble(FloatingChatMessageType.Voice))
        assertEquals(true, messageTypeUsesImModuleBubble(FloatingChatMessageType.InlineContact))
        assertEquals(true, messageTypeUsesImModuleBubble(FloatingChatMessageType.InlineLocation))
        assertEquals(false, messageTypeUsesImModuleBubble(FloatingChatMessageType.ImageThumbnail))
        assertEquals(false, messageTypeUsesImModuleBubble(FloatingChatMessageType.VideoPreview))
    }

    @Test
    fun nonTextMessagesUseSolidLightCardPalettes() {
        assertEquals(0xFFE6F3EE.toInt(), cardMessageColorArgbFor(FloatingChatMessageType.Location))
        assertEquals(0xFFEAF1FA.toInt(), cardMessageColorArgbFor(FloatingChatMessageType.ContactLink))
        assertEquals(0xFFE7F2F3.toInt(), cardMessageColorArgbFor(FloatingChatMessageType.MiniProgramLink))
        assertEquals(0xFFEAEFF4.toInt(), cardMessageColorArgbFor(FloatingChatMessageType.FilePreview))
        assertEquals(0xFFE7EEF6.toInt(), cardMessageColorArgbFor(FloatingChatMessageType.Voice))
        assertEquals(0xFFFF9D2E.toInt(), paymentCardMessageColorArgb())
        assertEquals(0xF8F8FCFF.toInt(), cardMessagePrimaryTextColorArgb())
        assertEquals(0xD9F1F7FA.toInt(), cardMessageSecondaryTextColorArgb())
    }

    @Test
    fun paymentMessagesUseWechatStyleOrangeCards() {
        assertEquals(true, paymentCardUsesWechatStyleLayout())
        assertEquals(52, paymentCardMinHeightDp())
        assertEquals(13, paymentCardTitleTextSizeSp())
        assertEquals(10, paymentCardFooterTextSizeSp())
        assertEquals(true, paymentCardUsesNormalTextWeight())
        assertEquals(32, paymentCardGlyphSizeDp())
        assertEquals(7, paymentCardOuterVerticalPaddingDp())
        assertEquals(false, paymentCardTextUsesShadow())
        assertEquals(0xFFFFC98F.toInt(), paymentCardClaimedMessageColorArgb())
        assertEquals(0xFFE7A967.toInt(), paymentCardClaimedBorderColorArgb())
        assertEquals("已领取", paymentCardClaimedStatusLabel(isTransfer = false))
        assertEquals("已收款", paymentCardClaimedStatusLabel(isTransfer = true))
        assertEquals("红包", paymentCardKindLabelFor("https://aiff.app/app/red-packet/1", "浮窗红包", "浮窗红包 ¥8.88"))
        assertEquals("转账", paymentCardKindLabelFor("https://aiff.app/app/transfer/1", "浮窗转账", "转账 ¥50.00"))
        assertEquals("¥50.00", paymentCardAmountTextFor("转账 ¥50.00"))
        assertEquals("请收款", paymentCardTransferSubtitle())
        assertEquals(true, paymentCardClickOpensAmountViewer("https://aiff.app/app/red-packet/1", "浮窗红包", "浮窗红包 ¥8.88"))
        assertEquals(true, paymentCardClickOpensAmountViewer("https://aiff.app/app/transfer/1", "浮窗转账", "转账 ¥50.00"))
        assertEquals(true, transferCardClickOpensAmountViewer())
        assertEquals("¥50.00", transferAmountTextFor("转账 ¥50.00"))
        assertEquals("转账给 何苗：晚餐AA", transferMessageDetailForRecipient("何苗", "晚餐AA"))
        assertEquals("转账给 何苗", transferMessageDetailForRecipient("何苗", ""))
        assertEquals("转账给你，请查收", transferMessageDetailForRecipient(null, ""))
        assertEquals("红包", paymentCardRedPacketFooter())
        assertEquals(true, redPacketCardClickOpensAmountViewer())
        assertEquals("¥8.88", redPacketAmountTextFor("浮窗红包 ¥8.88"))
        assertEquals(true, redPacketCanClaimInThread(fromMe = false, selectedThread = ChatThreadSelection.Private("li-si")))
        assertEquals(false, redPacketCanClaimInThread(fromMe = true, selectedThread = ChatThreadSelection.Private("li-si")))
        assertEquals(true, redPacketCanClaimInThread(fromMe = true, selectedThread = ChatThreadSelection.GroupChat("group-ops")))
        assertEquals(true, redPacketClaimUsesAnimatedState())
        assertEquals(420, redPacketClaimAnimationDurationMs())
        assertEquals("领取红包", redPacketClaimButtonLabel(claimed = false, amountText = "¥8.88"))
        assertEquals("已领取 ¥8.88", redPacketClaimButtonLabel(claimed = true, amountText = "¥8.88"))
    }

    @Test
    fun groupTransferCanPickRecipientFromCurrentGroupMembers() {
        val conversation = FloatingChatPrototype.sampleConversation()
        val recipients = transferRecipientCandidatesForThread(
            conversation = conversation,
            selectedThread = ChatThreadSelection.GroupChat("group-ops"),
            selectedAccountId = "account-store"
        )

        assertEquals(true, transferPanelSupportsGroupRecipientSelection())
        assertEquals(listOf("孙临", "何苗", "顾言"), recipients.map { it.name })
    }

    @Test
    fun transferClaimRequiresDesignatedRecipient() {
        val account = FloatingChatContact(
            id = "account-store",
            name = "门店服务",
            initials = "门店",
            description = "线下门店",
            avatarColor = 0xFFB56576
        )
        val otherAccount = FloatingChatContact(
            id = "account-main",
            name = "林舟",
            initials = "林舟",
            description = "个人微信",
            avatarColor = 0xFF3A86FF
        )
        val incomingTransfer = FloatingChatMessage(
            id = "transfer-incoming",
            type = FloatingChatMessageType.MiniProgramLink,
            text = "转账 ¥88.00",
            fromMe = false,
            senderName = "何苗",
            time = "刚刚",
            presentation = FloatingChatMessagePresentation.SpecialCard,
            connectionTarget = FloatingChatConnectionTarget.User,
            connectionTargetId = "he-miao",
            threadContactId = "he-miao",
            appName = "浮窗转账",
            resourceUrl = "https://aiff.app/app/transfer/1"
        )
        val incomingGroupTransferForAccount = incomingTransfer.copy(
            id = "transfer-group-incoming",
            threadContactId = "group-ops",
            cardName = account.name,
            resourceUrl = transferResourceUrlWithRecipient(
                resourceUrl = "https://aiff.app/app/transfer/2",
                recipientId = account.id
            )
        )
        val outgoingTransferToContact = incomingTransfer.copy(
            id = "transfer-outgoing",
            fromMe = true,
            senderName = account.name,
            connectionTarget = FloatingChatConnectionTarget.Account,
            connectionTargetId = account.id,
            threadContactId = "group-ops",
            cardName = "何苗",
            resourceUrl = transferResourceUrlWithRecipient(
                resourceUrl = "https://aiff.app/app/transfer/3",
                recipientId = "he-miao"
            )
        )

        assertEquals(
            true,
            transferCanClaimInThread(
                message = incomingTransfer,
                selectedThread = ChatThreadSelection.Private("he-miao"),
                selectedAccount = account
            )
        )
        assertEquals(
            false,
            transferCanClaimInThread(
                message = incomingTransfer.copy(fromMe = true),
                selectedThread = ChatThreadSelection.Private("he-miao"),
                selectedAccount = account
            )
        )
        assertEquals(
            true,
            transferCanClaimInThread(
                message = incomingGroupTransferForAccount,
                selectedThread = ChatThreadSelection.GroupChat("group-ops"),
                selectedAccount = account
            )
        )
        assertEquals(
            false,
            transferCanClaimInThread(
                message = incomingGroupTransferForAccount,
                selectedThread = ChatThreadSelection.GroupChat("group-ops"),
                selectedAccount = otherAccount
            )
        )
        assertEquals(
            false,
            transferCanClaimInThread(
                message = outgoingTransferToContact,
                selectedThread = ChatThreadSelection.GroupChat("group-ops"),
                selectedAccount = account
            )
        )
    }

    @Test
    fun locationMessagesUseMapPreviewCard() {
        assertEquals(true, locationMessageUsesMapPreview())
        assertEquals(86, locationMapPreviewHeightDp())
        assertEquals(0xFF10B978.toInt(), locationMapPinColorArgb())
    }

    @Test
    fun fileMessagesUseWechatDocumentCards() {
        assertEquals(true, filePreviewUsesWechatDocumentCard())
        assertEquals(46, fileWechatCardMinHeightDp())
        assertEquals(10, fileWechatTitleTextSizeSp())
        assertEquals(8, fileWechatSizeTextSizeSp())
        assertEquals(true, fileWechatCardUsesNormalTextWeight())
        assertEquals(false, fileWechatCardUsesTextShadow())
        assertEquals(25, fileBadgeWidthDp())
        assertEquals(29, fileBadgeHeightDp())
        assertEquals("DOCX", fileBadgeLabelFor("guest-system.docx", FloatingChatFileFormat.Word))
        assertEquals("PDF", fileBadgeLabelFor("guest-system.pdf", FloatingChatFileFormat.Pdf))
        assertEquals("TXT", fileBadgeLabelFor("notes.txt", FloatingChatFileFormat.Txt))
        assertEquals("MD", fileBadgeLabelFor("readme.md", FloatingChatFileFormat.Markdown))
        assertEquals("ZIP", fileBadgeLabelFor("archive.zip", null))
        assertEquals("RAR", fileBadgeLabelFor("archive.rar", null))
        assertEquals(0xFF1E88E5.toInt(), fileBadgeColorArgbFor("guest-system.docx", FloatingChatFileFormat.Word))
        assertEquals(0xFFE44747.toInt(), fileBadgeColorArgbFor("guest-system.pdf", FloatingChatFileFormat.Pdf))
    }

    @Test
    fun fileMessagesOpenFullscreenPreviewAndKeepLongPressMenu() {
        assertEquals(true, filePreviewOpensFullscreenViewer())
        assertEquals(false, filePreviewUsesInlineExpansion())
        assertEquals(true, filePreviewCardUsesCombinedClickForPreviewAndLongPress())
        assertEquals(true, documentPreviewRunsInsideFloatingOverlay())
        assertEquals(true, documentExternalOpenHidesFloatingOverlay())
        assertEquals(true, documentExternalOpenRestoresOverlayOnReturn())
    }

    @Test
    fun documentReaderOpensCommonFilesInsideFloatingChatBeforeExternalFallback() {
        assertEquals(
            FloatingChatDocumentReaderKind.Pdf,
            documentReaderKindFor("manual.pdf", "application/pdf", FloatingChatFileFormat.Pdf)
        )
        assertEquals(
            FloatingChatDocumentReaderKind.PlainText,
            documentReaderKindFor("notes.md", "text/markdown", FloatingChatFileFormat.Markdown)
        )
        assertEquals(
            FloatingChatDocumentReaderKind.DocxText,
            documentReaderKindFor(
                "readme.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                FloatingChatFileFormat.Word
            )
        )
        assertEquals(
            FloatingChatDocumentReaderKind.Spreadsheet,
            documentReaderKindFor(
                "list.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                null
            )
        )
        assertEquals(
            FloatingChatDocumentReaderKind.ZipArchive,
            documentReaderKindFor("archive.zip", "application/zip", null)
        )
        assertEquals(
            FloatingChatDocumentReaderKind.Unsupported,
            documentReaderKindFor("archive.rar", "application/vnd.rar", null)
        )
        assertEquals(false, documentReaderDefaultsToExternalApp())
        assertEquals(true, documentReaderKeepsExternalOpenAsFallback())
        assertEquals(true, documentReaderPdfUsesAndroidPdfRenderer())
        assertEquals(true, documentReaderDocxExtractsTextInApp())
        assertEquals(true, documentReaderZipShowsEntryList())
    }

    @Test
    fun documentReaderExtractsTextFromNamespacedDocxXml() {
        val xml = """
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                <w:body>
                    <w:p><w:r><w:t>第一段</w:t></w:r></w:p>
                    <w:p><w:r><w:t>第二段</w:t></w:r></w:p>
                </w:body>
            </w:document>
        """.trimIndent()

        assertEquals(listOf("第一段", "第二段"), docxTextLinesFromDocumentXml(xml))
    }

    @Test
    fun leftSessionRailShowsDemoScrollFollowText() {
        val conversation = FloatingChatPrototype.sampleConversation()
        val selectedAccount = selectedAccountForThread(
            conversation = conversation,
            selection = ChatThreadSelection.Private("li-si")
        )
        val info = leftRailFollowInfoForContact(
            conversation = conversation,
            contact = conversation.contacts.first { contact -> contact.id == "li-si" },
            selectedAccountId = selectedAccount.id
        )

        assertEquals(true, leftRailUsesScrollableLazyColumn())
        assertEquals(true, leftRailAllowsScrollWhenContentIsShort())
        assertEquals(true, leftRailSupportsBidirectionalScrolling())
        assertEquals(1, leftRailLeadingSpacerItemCount())
        assertEquals(0, leftRailInitialFirstVisibleItemIndex())
        assertEquals(0, leftRailScrollableTopPaddingDp(itemCount = 5, viewportHeightDp = 800))
        assertEquals(true, leftRailDefaultsToTopAlignedAvatars())
        assertEquals(true, leftRailBouncesBackToTopWhenContentFitsViewport())
        assertEquals(false, leftRailDisablesScrollWhenContentFitsViewport())
        assertEquals(true, leftRailUsesNonAnimatedTopResetWhenContentFitsViewport())
        assertEquals(true, leftRailSupportsGentleTopPullAndRelease())
        assertEquals(18, leftRailTopOverscrollMaxDp())
        assertEquals(170, leftRailTopOverscrollReturnMs())
        assertEquals(true, leftRailContentFitsViewport(itemCount = 5, viewportHeightDp = 800))
        assertEquals(true, leftRailScrollShowsFollowTextOverlay())
        assertEquals(0, leftRailFollowTextHideDelayMs())
        assertEquals(true, leftRailFollowTextHidesOnRelease())
        assertEquals(42, leftRailFollowTextStartOffsetDp())
        assertEquals(280, leftRailFollowTextWidthDp())
        assertEquals(322, leftRailFollowTextLayerWidthDp())
        assertEquals(56, leftRailTouchableWidthDp())
        assertEquals(8, railScreenEdgeInsetPx())
        assertEquals(8, leftRailAvatarScreenEdgeInsetPx())
        assertEquals(0, leftRailFollowTextInnerPaddingDp())
        assertEquals(true, leftRailFollowTextStartsAtAvatarRightEdge())
        assertEquals(true, leftRailFollowTextIncludesScreenEdgeInset())
        assertEquals(true, leftRailFollowTextUsesAvatarTopAlignment())
        assertEquals(true, leftRailFollowTextUsesLiveAvatarBounds())
        assertEquals(true, leftRailFollowTextYMatchesAvatarBounds())
        assertEquals(true, leftRailFollowTextBoundsUseSingleInvalidationVersion())
        assertEquals(true, leftRailFollowTextUsesCompactTypography())
        assertEquals(false, leftRailFollowTextUsesBackgroundHalo())
        assertEquals(294, leftRailScrollableBottomPaddingDp(itemCount = 14, viewportHeightDp = 800))
        assertEquals(726, leftRailScrollableBottomPaddingDp(itemCount = 5, viewportHeightDp = 800))
        assertEquals(false, leftRailUsesPointerDragToRevealFollowText())
        assertEquals(false, leftRailFollowTextOverlayConsumesPointerEvents())
        assertEquals(false, keyboardDismissUsesBlockingFullScreenPointerLayer())
        assertEquals(true, leftRailLayerDrawsAboveConnectorLayer())
        assertEquals(30f, leftRailLayerZIndex())
        assertEquals(10f, connectorLayerZIndex())
        assertEquals(
            1,
            leftRailSelectedThreadFirstVisibleIndex(
                groups = conversation.groupContacts,
                contacts = conversation.contacts,
                selectedThread = ChatThreadSelection.GroupChat("group-product")
            )
        )
        assertEquals(
            7,
            leftRailSelectedThreadFirstVisibleIndex(
                groups = conversation.groupContacts,
                contacts = conversation.contacts,
                selectedThread = ChatThreadSelection.Private("zhao-liu")
            )
        )
        assertEquals(
            11,
            leftRailSelectedThreadFirstVisibleIndex(
                groups = conversation.groupContacts,
                contacts = conversation.contacts,
                selectedThread = ChatThreadSelection.Private("qian-yue")
            )
        )
        assertEquals(false, leftRailScrollsSelectedThreadAvatarIntoViewForConnectors())
        assertEquals(true, leftRailKeepsScrollPositionWhenSelectingVisibleAvatar())
        assertEquals(true, leftRailClearsDisposedAvatarConnectorBounds())
        assertEquals(true, leftRailKeepsAnySessionConnectorAnchorWhenScrolledOffscreen())
        assertEquals(0f, leftRailSessionConnectorAnchorFollowsVirtualOffscreenPosition())
        assertEquals(true, leftRailPinsSelectedAvatarWhileScrolledOffscreen())
        assertEquals(
            RailPinnedAvatarEdge.Top,
            leftRailPinnedSelectedAvatarEdge(
                sessionIds = listOf("a", "b", "c"),
                selectedSessionId = "a",
                visibleItems = listOf(
                    LeftRailVisibleSessionItem(index = 1, offset = 0, size = 42),
                    LeftRailVisibleSessionItem(index = 2, offset = 48, size = 42)
                ),
                viewportHeightPx = 120f,
                fallbackStepPx = 48f
            )
        )
        assertEquals(
            RailPinnedAvatarEdge.Bottom,
            leftRailPinnedSelectedAvatarEdge(
                sessionIds = listOf("a", "b", "c"),
                selectedSessionId = "c",
                visibleItems = listOf(
                    LeftRailVisibleSessionItem(index = 0, offset = 0, size = 42),
                    LeftRailVisibleSessionItem(index = 1, offset = 48, size = 42)
                ),
                viewportHeightPx = 120f,
                fallbackStepPx = 48f
            )
        )
        assertEquals(
            null,
            leftRailPinnedSelectedAvatarEdge(
                sessionIds = listOf("a", "b", "c"),
                selectedSessionId = "b",
                visibleItems = listOf(LeftRailVisibleSessionItem(index = 1, offset = 48, size = 42)),
                viewportHeightPx = 120f,
                fallbackStepPx = 48f
            )
        )
        assertEquals(true, leftRailFollowTextIncludesNameLastMessageAndTime())
        assertEquals(true, leftRailFollowTextUsesDarkTextShadow())
        assertEquals(conversation.contacts.first { contact -> contact.id == "li-si" }.name, info.name)
        assertEquals(
            visibleMessagesForThread(
                conversation = conversation,
                selection = ChatThreadSelection.Private("li-si"),
                selectedAccountId = selectedAccount.id
            ).last().text,
            info.lastMessage
        )
        assertEquals(
            visibleMessagesForThread(
                conversation = conversation,
                selection = ChatThreadSelection.Private("li-si"),
                selectedAccountId = selectedAccount.id
            ).last().time,
            info.lastTime
        )
    }

    @Test
    fun messagePaneUsesVisibleItemLazyComposition() {
        assertEquals(true, messagePaneUsesLazyColumn())
        assertEquals(true, messagePaneOnlyComposesVisibleMessages())
        assertEquals(true, messagePaneUsesStableMessageKeys())
        assertEquals(true, messagePaneUsesMessageContentTypes())
        assertEquals(true, connectorLayerUsesLazyListVisibleItemsOnly())
        assertEquals(true, connectorLayerClearsStaleMessageBoundsWhenThreadChanges())
        assertEquals(true, connectorLayerClearsStaleMessageBoundsWhenMediaMessagesChange())
        assertEquals(true, connectorLayerUsesPrecomputedOffscreenTargets())
        assertEquals(true, messageBoundsForLongPressAreCapturedOnlyWhenNeeded())
        assertEquals(true, connectorLayerAvoidsSnapshotStateForPerFrameMessageBounds())
        assertEquals(true, connectorLayerDefersLazyListLayoutReadsToDrawPhase())
        assertEquals(true, connectorLayerReusesNativePaintDuringDraw())
        assertEquals(true, connectorCoordinateCacheUsesSingleInvalidationVersion())
        assertEquals(true, mediaThumbnailsDecodeOffMainThread())
        assertEquals(true, privateChatConnectorUsesSelectedAvatarLiveBounds())
        assertEquals(true, privateChatConnectorAllowsMessagesWithoutStoredTargetId())
        assertEquals(true, privateChatConnectorKeepsSelectedAvatarAnchorWhenLazyItemDisposes())
        assertEquals(true, privateChatConnectorDoesNotReuseGroupThreadAvatarAfterGroupSwitch())
    }

    @Test
    fun rightToolListIncludesBlinkVoiceSdkEntry() {
        assertEquals("眨眼", toolActionLabel(FloatingChatToolAction.Blink))
        assertEquals(
            true,
            referenceToolActionsFor(FloatingChatToolAction.entries).contains(FloatingChatToolAction.Blink)
        )
        assertEquals(false, simulatedMessageToolActions().contains(FloatingChatToolAction.Blink))
        assertEquals(
            "com.paifa.ubikitouch.app.FloatingChatBlinkVoiceActivity",
            blinkVoiceBridgeActivityClassName()
        )
        assertEquals(false, blinkVoiceCaptureAutoFinishOnEvent())
        assertEquals("眨眼识别：双眨，270ms", blinkVoiceResultMessageText("DOUBLE_BLINK", 270L))
    }

    @Test
    fun blinkVoiceCaptureUsesFloatingRealtimeChineseStatus() {
        assertEquals(true, blinkVoiceCaptureUsesFloatingWindow())
        assertEquals(true, blinkVoiceRealtimeStatusUsesChineseText())
        assertEquals(true, blinkVoiceCaptureClosesOnlyOnManualExit())
        assertEquals(listOf("SINGLE_BLINK", "DOUBLE_BLINK", "LONG_CLOSE"), blinkVoiceRecognizedEventTypes())
        assertEquals("等待眨眼识别", blinkVoiceRealtimeStatusLabel(null))
        assertEquals("识别到单眨", blinkVoiceRealtimeStatusLabel("SINGLE_BLINK"))
        assertEquals("识别到双眨", blinkVoiceRealtimeStatusLabel("DOUBLE_BLINK"))
        assertEquals("识别到长闭眼", blinkVoiceRealtimeStatusLabel("LONG_CLOSE"))
        assertEquals("识别到单眨", blinkVoiceStatusLogEntry("SINGLE_BLINK"))
        assertEquals("识别到双眨", blinkVoiceStatusLogEntry("DOUBLE_BLINK"))
        assertEquals("识别到长闭眼", blinkVoiceStatusLogEntry("LONG_CLOSE"))
        assertEquals(false, blinkVoiceRealtimeStatusLabel(null).contains("睁眼"))
        assertEquals(false, blinkVoiceRealtimeStatusLabel(null).contains("右眼闭合"))
    }

    @Test
    fun inputFocusStartsHeadlessBlinkVoiceAiControl() {
        assertEquals(true, floatingChatInputFocusStartsHeadlessBlinkVoice())
        assertEquals(true, blinkVoiceHeadlessCaptureKeepsFloatingChatVisible())
        assertEquals(true, blinkVoiceHeadlessCaptureStopsWhenInputBlurred())
        assertEquals(true, blinkVoiceHeadlessCaptureStopsAfterFirstRecognizedEvent())
        assertEquals(true, blinkVoiceResultEventMarksHeadlessSource())
    }

    @Test
    fun blinkVoiceInputStatusUsesFloatingMarqueeHint() {
        assertEquals(true, blinkVoiceInputStatusUsesFloatingHintBar())
        assertEquals(true, blinkVoiceInputStatusUsesMarquee())
        assertEquals(true, blinkVoiceInputStatusAppearsAboveInputBar())
        assertEquals(true, blinkVoiceInputStatusAutoDismisses())
        assertEquals(2600, blinkVoiceInputStatusAutoDismissMs())
    }

    @Test
    fun aiGeneratedInputCanBeClearedWithOneTap() {
        assertEquals(true, blinkVoiceAiGeneratedInputTracksClearableState())
        assertEquals(true, bottomInputBarShowsAiGeneratedClearAction())
        assertEquals(true, aiGeneratedInputClearActionClearsInputAndHint())
    }

    @Test
    fun chatConnectorsStayWhiteSingleLineTree() {
        assertEquals(0xF2F8FCFF.toInt(), imModuleConnectionLineColorArgb())
        assertEquals(6f, imModuleConnectionLineStrokeWidthPx())
        assertEquals(0x4D000000, imModuleConnectionLineShadowColorArgb())
        assertEquals(4f, imModuleConnectionLineShadowBlurPx())
        assertEquals(1f, imModuleConnectionLineShadowOffsetXPx())
        assertEquals(1f, imModuleConnectionLineShadowOffsetYPx())
        assertEquals(48f, imModuleConnectionLineHorizontalOffsetPx())
        assertEquals(32f, imModuleConnectionLineMinimumBranchPx())
        assertEquals(12f, imModuleConnectionLineCornerRadiusPx())
        assertEquals(0.25f, imModuleConnectionLineCornerArcFraction())
        assertEquals(true, imModuleConnectionLineUsesRoundedElbows())
        assertEquals(12f, connectorRoundedElbowRadiusPx(48f))
        assertEquals(0f, imModuleConnectionLineBubbleGapPx())
        assertEquals(0f, imModuleConnectionLineBubbleOverlapPx())
        assertEquals(true, imModuleConnectionLineUsesBraceHooks())
        assertEquals(true, imModuleConnectionLineHooksStartAtTrunkJoint())
        assertEquals(true, imModuleConnectionLineUsesNativeCanvasShadow())
        assertEquals(false, imModuleConnectionLineDrawsEndpointDots())

        val userLine = createChatConnectorLine(
            avatarBounds = androidx.compose.ui.geometry.Rect(0f, 90f, 42f, 132f),
            bubbleBounds = androidx.compose.ui.geometry.Rect(120f, 80f, 220f, 140f),
            layerBounds = androidx.compose.ui.geometry.Rect(0f, 0f, 360f, 720f),
            target = FloatingChatConnectionTarget.User
        )
        val accountLine = createChatConnectorLine(
            avatarBounds = androidx.compose.ui.geometry.Rect(318f, 90f, 360f, 132f),
            bubbleBounds = androidx.compose.ui.geometry.Rect(120f, 80f, 220f, 140f),
            layerBounds = androidx.compose.ui.geometry.Rect(0f, 0f, 360f, 720f),
            target = FloatingChatConnectionTarget.Account
        )
        val hook = ChatConnectorBraceHook(
            center = Offset(300f, 180f),
            branchEnd = Offset(220f, 180f),
            radius = 12f,
            verticalDirection = -1f
        )

        assertEquals(120f, userLine.end.x)
        assertEquals(220f, accountLine.end.x)
        assertEquals(Offset(300f, 165f), hook.roundedElbowGeometry().curveStart)
    }

    @Test
    fun privateChatConnectorsBindToCurrentUserAndAccountRails() {
        val selection = ChatThreadSelection.Private("li-si")
        val incoming = FloatingChatMessage(
            id = "incoming",
            type = FloatingChatMessageType.Text,
            text = "hello",
            fromMe = false,
            senderName = "wrong",
            time = "10:00",
            connectionTarget = FloatingChatConnectionTarget.User,
            connectionTargetId = "wrong-user"
        )
        val outgoing = FloatingChatMessage(
            id = "outgoing",
            type = FloatingChatMessageType.Text,
            text = "ok",
            fromMe = true,
            senderName = "me",
            time = "10:01",
            connectionTarget = FloatingChatConnectionTarget.Account,
            connectionTargetId = "wrong-account"
        )
        val pickedImage = FloatingChatMessage(
            id = "picked-image",
            type = FloatingChatMessageType.ImageThumbnail,
            text = "",
            fromMe = true,
            senderName = "me",
            time = "10:02",
            connectionTarget = FloatingChatConnectionTarget.Account,
            connectionTargetId = null
        )

        assertEquals(
            "li-si",
            connectorTargetIdForMessage(
                message = incoming,
                selection = selection,
                selectedAccountId = "account-work",
                groupMemberAvatarsVisible = false
            )
        )
        assertEquals(
            "account-work",
            connectorTargetIdForMessage(
                message = outgoing,
                selection = selection,
                selectedAccountId = "account-work",
                groupMemberAvatarsVisible = false
            )
        )
        assertEquals(
            "account-work",
            connectorTargetIdForMessage(
                message = pickedImage,
                selection = selection,
                selectedAccountId = "account-work",
                groupMemberAvatarsVisible = false
            )
        )
        assertEquals(true, privateChatConnectorsUseCurrentThreadTargets())
        assertEquals(true, privateChatLeftConnectorAnchorsToUserAvatarRightEdge())
        assertEquals(true, privateChatRightConnectorAnchorsToAccountAvatarLeftEdge())
    }

    @Test
    fun privateChatConnectorsKeepLateRightAccountsComposed() {
        val conversation = FloatingChatPrototype.sampleConversation()
        val heMiaoAccount = selectedAccountForThread(
            conversation = conversation,
            selection = ChatThreadSelection.Private("he-miao")
        )
        val qianYueAccount = selectedAccountForThread(
            conversation = conversation,
            selection = ChatThreadSelection.Private("qian-yue")
        )

        assertEquals("account-service", heMiaoAccount.id)
        assertEquals("account-private", qianYueAccount.id)
        assertEquals(
            7,
            rightRailSelectedAccountFirstVisibleIndex(
                accounts = conversation.accountContacts,
                selectedAccountId = heMiaoAccount.id
            )
        )
        assertEquals(
            8,
            rightRailSelectedAccountFirstVisibleIndex(
                accounts = conversation.accountContacts,
                selectedAccountId = qianYueAccount.id
            )
        )
        assertEquals(true, rightRailScrollsSelectedAccountIntoViewForConnectors())
    }

    @Test
    fun bottomInputBarMatchesFloatingCapsuleControls() {
        assertEquals(46, bottomInputBarMinHeightDp())
        assertEquals(128, bottomInputBarMaxHeightDp())
        assertEquals(10, bottomInputBarBottomPaddingDp())
        assertEquals(true, bottomInputBarUsesKeyboardInsets())
        assertEquals(true, bottomInputControlsUseCenterAlignment())
        assertEquals(true, bottomInputUsesCustomBasicTextField())
        assertEquals(11, bottomInputTextSizeSp())
        assertEquals(11, bottomInputPlaceholderTextSizeSp())
        assertEquals(32, bottomInputIconButtonSizeDp())
        assertEquals(18, bottomInputIconSizeDp())
        assertEquals(1, bottomInputMinLines())
        assertEquals(4, bottomInputMaxLines())
        assertEquals(
            listOf(
                BottomInputAction.Home,
                BottomInputAction.Voice,
                BottomInputAction.Text,
                BottomInputAction.Gift,
                BottomInputAction.Assistant
            ),
            bottomInputActionOrder()
        )
        assertEquals(BottomInputAction.Home, bottomInputLeadingAction(inputFocused = false))
        assertEquals(BottomInputAction.Emoji, bottomInputLeadingAction(inputFocused = true))
        assertEquals(true, bottomHomeButtonShowsUnrepliedOverview())
        assertEquals(true, bottomHomeButtonSwapsToEmojiWhenInputFocused())
        assertEquals(false, bottomInputBarVisibleForCenteredToolPanel(centeredToolFeaturePanelVisible = true))
        assertEquals(true, bottomInputBarVisibleForCenteredToolPanel(centeredToolFeaturePanelVisible = false))
    }

    @Test
    fun homeUnreadOverviewShowsLatestUnreadPerThreadAndClearsOnOpen() {
        val conversation = FloatingChatPrototype.sampleConversation()
        val accountConversations = accountScopedConversations(conversation)
        val summaries = homeUnreadThreadSummaries(
            accountConversations = accountConversations
        )
        val unreadThreadIds = defaultAllAccountHomeUnreadThreadIds(conversation)
        val first = summaries.first()
        val afterOpen = unreadThreadIdsAfterOpeningHomeUnreadBubble(
            unreadThreadIds = unreadThreadIds,
            summary = first
        )

        assertEquals(true, homeUnreadOverviewUsesLatestMessagePerThread())
        assertEquals(true, homeUnreadOverviewBubblesJumpToThread())
        assertEquals(true, homeUnreadOverviewClearsUnreadAfterOpen())
        assertEquals(true, homeUnreadOverviewKeepsConnectorLines())
        assertEquals(true, homeUnreadAvatarGreenDotReflectsThreadState())
        assertEquals(true, homeUnreadOverviewUsesSourceScopedConnectorLines())
        assertEquals(summaries.map { summary -> summary.threadId }.toSet(), unreadThreadIds)
        assertEquals(summaries.map { it.threadId }.distinct(), summaries.map { it.threadId })
        assertEquals(false, afterOpen.contains(first.threadId))
        assertEquals(true, summaries.all { it.message.type == FloatingChatMessageType.Text })
        assertEquals(true, summaries.all { it.message.presentation == FloatingChatMessagePresentation.Bubble })
        assertEquals(true, summaries.all { !it.message.fromMe })
        assertEquals(true, summaries.all { it.message.text.isNotBlank() })
        assertEquals(true, summaries.all { it.message.senderName.contains("未回") })
        assertEquals(true, summaries.any { it.unreadCount >= 3 })
        assertEquals(true, summaries.any { it.selection is ChatThreadSelection.GroupChat })
        assertEquals(true, summaries.any { it.selection is ChatThreadSelection.Private })
        assertEquals(true, summaries.map { it.accountId }.distinct().size > 1)
        assertEquals(first.accountId, accountIdForScopedThreadSelection(first.selection))
        assertEquals(true, homeUnreadOverviewShowsAllAccounts())
        assertEquals(true, homeUnreadBubbleSwitchesToOwningAccount())
        assertEquals(true, homeUnreadOverviewTracksAllUnrepliedThreads())
    }

    @Test
    fun homeOverviewShowsUnrepliedThreadsEvenWhenTheyAreNotUnread() {
        val conversation = FloatingChatPrototype.sampleConversation()

        val summaries = homeUnreadThreadSummaries(
            accountConversations = accountScopedConversations(conversation)
        )

        assertEquals(true, summaries.size > 5)
        assertEquals(true, summaries.all { it.message.senderName.contains("未回") })
    }

    @Test
    fun homeOverviewShowsOnlyUnrepliedIncomingMessagesAfterLastSelfReply() {
        val conversation = FloatingChatConversation(
            peerName = "Test",
            accountName = "Me",
            contacts = listOf(FloatingChatContact("customer", "Customer", "C", "Customer", 0xFF1B9AAA)),
            accountContacts = listOf(FloatingChatContact("account", "Me", "M", "Account", 0xFF3A86FF, selected = true)),
            messages = listOf(
                FloatingChatMessage(
                    id = "old-incoming",
                    type = FloatingChatMessageType.Text,
                    text = "old incoming",
                    fromMe = false,
                    senderName = "Customer",
                    time = "10:00",
                    connectionTargetId = "customer",
                    threadContactId = "customer"
                ),
                FloatingChatMessage(
                    id = "my-reply",
                    type = FloatingChatMessageType.Text,
                    text = "my reply",
                    fromMe = true,
                    senderName = "Me",
                    time = "10:01",
                    connectionTargetId = "account",
                    threadContactId = "customer"
                ),
                FloatingChatMessage(
                    id = "needs-reply-1",
                    type = FloatingChatMessageType.Text,
                    text = "needs reply one",
                    fromMe = false,
                    senderName = "Customer",
                    time = "10:02",
                    connectionTargetId = "customer",
                    threadContactId = "customer"
                ),
                FloatingChatMessage(
                    id = "needs-reply-2",
                    type = FloatingChatMessageType.Text,
                    text = "needs reply two",
                    fromMe = false,
                    senderName = "Customer",
                    time = "10:03",
                    connectionTargetId = "customer",
                    threadContactId = "customer"
                )
            ),
            toolActions = emptyList()
        )

        val summary = homeUnreadThreadSummaries(
            conversation = conversation
        ).single()

        assertEquals(true, homeUnreadOverviewUsesUnrepliedMessagesAfterLastSelfReply())
        assertEquals(2, summary.unreadCount)
        assertEquals("needs-reply-2", summary.message.id.removePrefix("home-unread-${summary.threadId}-"))
        assertEquals("Customer - 2 条未回 - Me", summary.message.senderName)
    }

    @Test
    fun homeUnreadOverviewConnectorLinesDoNotUseGroupMemberAvatars() {
        val contact = FloatingChatPrototype.sampleConversation().contacts.first()
        val message = FloatingChatMessage(
            id = "home-private-unread",
            type = FloatingChatMessageType.Text,
            text = "主页未读",
            fromMe = false,
            senderName = contact.name,
            time = "刚刚",
            connectionTarget = FloatingChatConnectionTarget.User,
            connectionTargetId = contact.id
        )
        val contactsById = mapOf(contact.id to contact)

        assertEquals(
            contact,
            groupMemberContactForMessage(
                message = message,
                selectedThread = ChatThreadSelection.GroupChat("group-1"),
                homeOverviewVisible = false,
                contactsById = contactsById,
                groupMemberAvatarsVisible = true
            )
        )
        assertEquals(
            null,
            groupMemberContactForMessage(
                message = message,
                selectedThread = ChatThreadSelection.GroupChat("group-1"),
                homeOverviewVisible = true,
                contactsById = contactsById,
                groupMemberAvatarsVisible = true
            )
        )
        assertEquals(true, homeUnreadOverviewSuppressesGroupMemberAvatars())
        assertEquals(
            "home-source:${contact.id}",
            homeOverviewConnectorKeyDebugId(message)
        )
    }

    @Test
    fun accountScopedConversationKeepsAllPreScopedRemoteContactsForSelectedAccount() {
        val accountOne = FloatingChatContact("account-one", "One", "O", "Account", 0xFF3A86FF, selected = true)
        val accountTwo = FloatingChatContact("account-two", "Two", "T", "Account", 0xFF06D6A0)
        val accountOneContacts = (1..8).map { index ->
            FloatingChatContact(
                id = "${accountOne.id}__wxid_a_$index",
                name = "A$index",
                initials = "A$index",
                description = "Friend",
                avatarColor = 0xFF1B9AAA
            )
        }
        val accountTwoContacts = (1..3).map { index ->
            FloatingChatContact(
                id = "${accountTwo.id}__wxid_b_$index",
                name = "B$index",
                initials = "B$index",
                description = "Friend",
                avatarColor = 0xFFE07A5F
            )
        }
        val accountOneGroups = listOf(
            FloatingChatContact(
                id = "${accountOne.id}__room_a@chatroom",
                name = "A Room",
                initials = "AR",
                description = "Group",
                avatarColor = 0xFF8E7DBE
            )
        )
        val conversation = FloatingChatConversation(
            peerName = "SCRM",
            accountName = "One",
            contacts = accountOneContacts + accountTwoContacts,
            groupContacts = accountOneGroups,
            accountContacts = listOf(accountOne, accountTwo),
            messages = emptyList(),
            toolActions = FloatingChatToolAction.entries
        )

        val scoped = accountScopedConversation(conversation, accountOne.id)

        assertEquals(accountOneContacts.map { it.id }, scoped.contacts.map { it.id })
        assertEquals(accountOneGroups.map { it.id }, scoped.groupContacts.map { it.id })
        assertEquals(true, scoped.contacts.size > 5)
        assertEquals(false, scoped.contacts.any { contact -> contact.id.startsWith("${accountTwo.id}__") })
    }

    @Test
    fun homeUnreadCandidatesAreDerivedFromMessageThreadsInsteadOfEveryContact() {
        val account = FloatingChatContact("account-aiken", "aiken", "ai", "Account", 0xFF3A86FF)
        val contacts = (1..4_600).map { index ->
            FloatingChatContact(
                id = "account-aiken__scrm-contact:friend-$index",
                name = "Friend $index",
                initials = "F$index",
                description = "Friend",
                avatarColor = 0xFF1B9AAA
            )
        }
        val target = contacts[4_200]
        val conversation = FloatingChatConversation(
            peerName = "SCRM",
            accountName = "aiken",
            contacts = contacts,
            groupContacts = emptyList(),
            accountContacts = listOf(account),
            messages = listOf(
                FloatingChatMessage(
                    id = "remote-message-1",
                    type = FloatingChatMessageType.Text,
                    text = "ping",
                    fromMe = false,
                    senderName = target.name,
                    time = "now",
                    connectionTarget = FloatingChatConnectionTarget.User,
                    connectionTargetId = target.id,
                    threadContactId = target.id
                )
            ),
            toolActions = FloatingChatToolAction.entries
        )

        val candidates = homeUnreadCandidateSelections(conversation)

        assertEquals(listOf(ChatThreadSelection.Private(target.id)), candidates)
    }

    @Test
    fun contactsPanelRouteUsesCurrentScrmAccountInsteadOfStoredDefault() {
        val selectedAccountId = com.paifa.ubikitouch.accessibility.scrm.scrmFloatingAccountId(
            deviceUuid = "device-current",
            weChatId = "wxid_current"
        )

        val route = scrmContactsPanelRouteForSelectedAccount(
            selectedAccountId = selectedAccountId,
            fallbackDeviceUuid = "device-default",
            fallbackWeChatId = "wxid_default"
        )

        assertEquals("device-current", route?.deviceUuid)
        assertEquals("wxid_current", route?.weChatId)
    }

    @Test
    fun leftRailFollowInfosAreBuiltFromVisibleAvatarBoundsOnly() {
        val account = FloatingChatContact("account-aiken", "aiken", "AI", "Account", 0xFF3A86FF)
        val contacts = (1..4_600).map { index ->
            FloatingChatContact(
                id = "contact-$index",
                name = "Friend $index",
                initials = "F$index",
                description = "Friend",
                avatarColor = 0xFF1B9AAA
            )
        }
        val visibleContact = contacts[4_200]
        val conversation = FloatingChatConversation(
            peerName = "SCRM",
            accountName = "aiken",
            contacts = contacts,
            groupContacts = emptyList(),
            accountContacts = listOf(account),
            messages = listOf(
                FloatingChatMessage(
                    id = "message-visible",
                    type = FloatingChatMessageType.Text,
                    text = "visible latest",
                    fromMe = false,
                    senderName = visibleContact.name,
                    time = "12:00",
                    connectionTarget = FloatingChatConnectionTarget.User,
                    connectionTargetId = visibleContact.id,
                    threadContactId = visibleContact.id
                )
            ),
            toolActions = FloatingChatToolAction.entries
        )

        val infos = leftRailVisibleFollowInfos(
            conversation = conversation,
            selectedAccountId = account.id,
            contactsById = contacts.associateBy { it.id },
            visibleAvatarBounds = mapOf(
                visibleContact.id to androidx.compose.ui.geometry.Rect(0f, 120f, 28f, 148f)
            ),
            railRootTopPx = 20f
        )

        assertEquals(listOf(visibleContact.id), infos.map { it.contactId })
        assertEquals("visible latest", infos.single().lastMessage)
        assertEquals(100f, infos.single().topPx)
        assertEquals(28f, infos.single().heightPx)
    }

    @Test
    fun blinkVoiceRecognitionDoesNotAutoSendChatMessage() {
        assertEquals(false, blinkVoiceRecognitionAutoSendsChatMessage())
    }

    @Test
    fun leftRailOrdersSessionsByLatestChatMessage() {
        val account = FloatingChatContact("account-a", "A", "A", "Account", 0xFF3A86FF, selected = true)
        val older = FloatingChatContact("older", "Older", "O", "Friend", 0xFF1B9AAA)
        val newest = FloatingChatContact("newest", "Newest", "N", "Friend", 0xFF06D6A0)
        val group = FloatingChatContact("group-alpha", "Group", "G", "Group", 0xFF5B7CFA)
        val silent = FloatingChatContact("silent", "Silent", "S", "Friend", 0xFF9B5DE5)
        val conversation = FloatingChatConversation(
            peerName = "SCRM",
            accountName = "A",
            contacts = listOf(older, newest, silent),
            groupContacts = listOf(group),
            accountContacts = listOf(account),
            messages = listOf(
                FloatingChatMessage(
                    id = "older-message",
                    type = FloatingChatMessageType.Text,
                    text = "older",
                    fromMe = false,
                    senderName = older.name,
                    time = "09:00",
                    connectionTarget = FloatingChatConnectionTarget.User,
                    connectionTargetId = older.id,
                    threadContactId = older.id
                ),
                FloatingChatMessage(
                    id = "group-message",
                    type = FloatingChatMessageType.Text,
                    text = "group",
                    fromMe = false,
                    senderName = group.name,
                    time = "10:00",
                    connectionTarget = FloatingChatConnectionTarget.User,
                    connectionTargetId = group.id,
                    threadContactId = group.id
                ),
                FloatingChatMessage(
                    id = "newest-message",
                    type = FloatingChatMessageType.Text,
                    text = "newest",
                    fromMe = false,
                    senderName = newest.name,
                    time = "11:00",
                    connectionTarget = FloatingChatConnectionTarget.User,
                    connectionTargetId = newest.id,
                    threadContactId = newest.id
                )
            ),
            toolActions = FloatingChatToolAction.entries
        )

        assertEquals(true, leftRailSortsSessionsByLatestChatTime())
        assertEquals(
            listOf("contact-newest", "group-group-alpha", "contact-older", "contact-silent"),
            sessionRailItemKeysByLatestChatTime(conversation, selectedAccountId = account.id)
        )
    }

    @Test
    fun accountScopedConversationDoesNotInventFiveContactsForUnloadedRemoteAccount() {
        val accountOne = FloatingChatContact("account-one", "One", "O", "Account", 0xFF3A86FF)
        val accountTwo = FloatingChatContact("account-two", "Two", "T", "Account", 0xFF06D6A0)
        val accountOneContacts = (1..8).map { index ->
            FloatingChatContact(
                id = "${accountOne.id}__wxid_a_$index",
                name = "A$index",
                initials = "A$index",
                description = "Friend",
                avatarColor = 0xFF1B9AAA
            )
        }
        val conversation = FloatingChatConversation(
            peerName = "SCRM",
            accountName = "One",
            contacts = accountOneContacts,
            groupContacts = emptyList(),
            accountContacts = listOf(accountOne, accountTwo),
            messages = emptyList(),
            toolActions = FloatingChatToolAction.entries
        )

        val scoped = accountScopedConversation(conversation, accountTwo.id)

        assertEquals(emptyList<String>(), scoped.contacts.map { contact -> contact.id })
        assertEquals(true, scoped.accountContacts.first { account -> account.id == accountTwo.id }.selected)
    }

    @Test
    fun coordinateBodySelectedAccountUsesClickedAccountEvenBeforeDetailsLoad() {
        val accountOne = FloatingChatContact("account-one", "One", "O", "Account", 0xFF3A86FF)
        val accountTwo = FloatingChatContact("account-two", "Two", "T", "Account", 0xFF06D6A0, selected = true)
        val conversation = FloatingChatConversation(
            peerName = "SCRM",
            accountName = "Two",
            contacts = emptyList(),
            groupContacts = emptyList(),
            accountContacts = listOf(accountOne, accountTwo),
            messages = emptyList(),
            toolActions = FloatingChatToolAction.entries
        )

        assertEquals(
            "account-two",
            selectedAccountForCoordinateBody(
                conversation = conversation,
                selectedThread = ChatThreadSelection.Group,
                activeAccountId = "account-two"
            ).id
        )
    }

    @Test
    fun rightRailSelectedAccountAvatarHasVisibleHighlightRing() {
        assertEquals(true, rightRailSelectedAccountAvatarUsesHighlightRing())
        assertEquals(3, rightRailSelectedAccountAvatarHighlightStrokeDp())
        assertEquals(true, leftRailSelectedAvatarUsesAccountHighlightRing())
        assertEquals(rightRailSelectedAccountAvatarHighlightStrokeDp(), leftRailSelectedAvatarHighlightStrokeDp())
    }

    @Test
    fun homeUnreadOverviewUsesOriginalThreadAvatarAsConnectorSource() {
        val message = FloatingChatMessage(
            id = "home-private-unread",
            type = FloatingChatMessageType.Text,
            text = "主页未读",
            fromMe = false,
            senderName = "何苗",
            time = "刚刚",
            connectionTarget = FloatingChatConnectionTarget.User,
            connectionTargetId = "account-a__he-miao"
        )

        assertEquals("home-source:account-a__he-miao", homeOverviewConnectorKeyDebugId(message))
        assertEquals("account-a__he-miao", homeOverviewConnectorSourceKeyDebugId(message))
    }

    @Test
    fun homeUnreadOverviewConnectorLinesUseFallbackWhenRailAvatarIsOffscreen() {
        val layerBounds = androidx.compose.ui.geometry.Rect(0f, 0f, 360f, 720f)
        val viewportBounds = androidx.compose.ui.geometry.Rect(56f, 40f, 320f, 680f)
        val bubbleBounds = listOf(androidx.compose.ui.geometry.Rect(120f, 200f, 260f, 250f))
        val fallbackBounds = homeOverviewFallbackConnectorAvatarBounds(
            bubbleBounds = bubbleBounds,
            layerBounds = layerBounds,
            visibleRootBounds = viewportBounds,
            target = FloatingChatConnectionTarget.User
        )

        val tree = createChatConnectorTree(
            avatarBounds = fallbackBounds!!,
            bubbleBounds = bubbleBounds,
            layerBounds = layerBounds,
            visibleRootBounds = viewportBounds,
            target = FloatingChatConnectionTarget.User,
            hasMessagesAbove = false,
            hasMessagesBelow = false
        )

        assertEquals(42f, fallbackBounds.right)
        assertEquals(88f, tree?.trunkStart?.x)
        assertEquals(120f, tree?.messageBranches?.single()?.end?.x)
        assertEquals(true, homeUnreadOverviewUsesFallbackConnectorSourceWhenRailAvatarIsOffscreen())
    }

    @Test
    fun offscreenAccountConnectorUsesPinnedAccountEdgeNotMessageViewportBottom() {
        val tree = createChatConnectorTree(
            avatarBounds = androidx.compose.ui.geometry.Rect(314f, 280f, 356f, 322f),
            bubbleBounds = listOf(androidx.compose.ui.geometry.Rect(120f, 200f, 260f, 250f)),
            layerBounds = androidx.compose.ui.geometry.Rect(0f, 0f, 360f, 720f),
            visibleRootBounds = androidx.compose.ui.geometry.Rect(56f, 40f, 320f, 680f),
            target = FloatingChatConnectionTarget.Account,
            hasMessagesAbove = false,
            hasMessagesBelow = false,
            avatarOffscreenEdge = ChatConnectorViewportEdge.Below
        )

        assertEquals(301f, tree?.trunkEnd?.y)
    }

    @Test
    fun bottomEmojiAndVoicePanelsUseRealPlatformComponents() {
        assertEquals(true, bottomEmojiPanelUsesAndroidXEmojiPicker())
        assertEquals("androidx.emoji2:emoji2-emojipicker:1.6.0", bottomEmojiPickerDependencyCoordinate())
        assertEquals(true, bottomEmojiPanelKeepsPickerOpenAfterSelection())
        assertEquals(300, bottomEmojiPanelHeightDp())
        assertEquals(true, bottomFloatingPanelUsesDarkText())
        assertEquals(true, voiceInputRecordsAudioMessage())
        assertEquals(true, voiceInputSendsRecordedAudio())
        assertEquals(true, voiceMessageSupportsPlayback())
        assertEquals("audio/mp4", voiceRecorderMimeType())
        assertEquals("m4a", voiceRecorderFileExtension())
        assertEquals(true, voiceInputRequiresRecordAudioPermission())
        assertEquals(
            listOf(
                "android.permission.RECORD_AUDIO",
                "android.permission.ACCESS_FINE_LOCATION",
                "android.permission.ACCESS_COARSE_LOCATION"
            ),
            floatingChatRuntimePermissions()
        )
    }

    @Test
    fun assistantPredictedMessageUsesGreenDashedBubble() {
        val conversation = FloatingChatPrototype.sampleConversation()
        val account = FloatingChatPrototype.pairedAccountFor(conversation, "group-product")
        val assistant = FloatingChatPrototype.simulatedToolMessage(
            conversation = conversation,
            action = FloatingChatToolAction.Assistant,
            selection = FloatingChatPrototype.ToolThreadSelection.GroupChat("group-product"),
            accountId = account.id,
            sequence = 1
        )

        assertEquals(true, bottomInputAssistantActionSendsPredictedMessage())
        assertEquals(true, aiDraftMessageUsesGreenDashedBubble(assistant))
        assertEquals(false, aiDraftMessageUsesSolidBubbleBorder(assistant))
        assertEquals(0xFF7DCC16.toInt(), aiDraftBubbleDashedBorderColorArgb())
    }

    @Test
    fun mediaPreviewSupportsPinchZoomAndSingleTapDismiss() {
        assertEquals(true, mediaPreviewSupportsZoom())
        assertEquals(true, mediaPreviewSingleTapDismisses())
        assertEquals(1f, mediaPreviewMinimumZoom())
        assertEquals(4f, mediaPreviewMaximumZoom())
        assertEquals(true, mediaPreviewSupportsSwipeBetweenThreadMedia())
        assertEquals(true, mediaPreviewImageTransformLetsPagerHandleSingleFingerSwipe())
        assertEquals(true, mediaPreviewDismissGestureUsesVerticalOnlyDrag())
        assertEquals(true, mediaPreviewIgnoresExistingDismissSignalOnOpen())
        assertEquals(true, mediaPreviewSupportsDragToDismiss())
        assertEquals(true, mediaPreviewUsesShrinkDismissAnimation())
        assertEquals(true, mediaPreviewBackNavigationCloses())
    }

    @Test
    fun videoPreviewUsesRealPlayerAndOriginalAspectFit() {
        assertEquals(true, mediaPreviewVideoUsesNativePlayer())
        assertEquals(true, mediaPreviewVideoUsesAspectFit())
        assertEquals(false, mediaPreviewVideoUsesImageZoomGestures())
        assertEquals(false, mediaPreviewVideoAutoPlays())
        assertEquals(true, mediaPreviewVideoShowsProgressBar())
        assertEquals(true, mediaPreviewVideoShowsTimecodes())
        assertEquals(true, mediaPreviewVideoShowsInlinePlayPause())
        assertEquals(true, mediaPreviewVideoControlsAvoidActionButtons())
        assertEquals(true, mediaPreviewVideoUsesOriginalAspectFrame())
        assertEquals(true, mediaPreviewVideoPlayButtonUsesMaterialIcon())
        assertEquals(true, mediaPreviewVideoPauseButtonUsesMaterialIcon())
        assertEquals(12, mediaPreviewVideoControlBottomPaddingDp())
        assertEquals(true, mediaPreviewVideoControlsFloatOverVideoFrame())
        assertEquals(true, mediaPreviewVideoPlayerReleaseIsIdempotent())
        assertEquals(true, mediaPreviewVideoPlayerReleaseHandlesInvalidState())
    }

    @Test
    fun chatListStandaloneMediaUsesOriginalAspectSizing() {
        assertEquals(true, standaloneMediaListUsesAspectFit())
        assertEquals(false, standaloneMediaListUsesUniformSquareShape())
    }

    @Test
    fun chatListStandaloneMediaUsesMoreCompactFootprint() {
        assertEquals(168, standaloneMediaListMaxWidthDp())
        assertEquals(72, standaloneMediaListMinHeightDp())
        assertEquals(176, standaloneMediaListMaxHeightDp())
    }

    @Test
    fun chatListVideoUsesCoverCardContract() {
        assertEquals(false, chatListVideoUsesInlinePlayer())
        assertEquals(true, chatListVideoUsesAspectFit())
        assertEquals(false, chatListVideoAutoPlays())
    }

    @Test
    fun standaloneMediaMessagesRespectSenderSideInsteadOfCentering() {
        assertEquals(
            MessageHorizontalPlacement.End,
            messageHorizontalPlacement(
                presentation = FloatingChatMessagePresentation.MediaStandalone,
                fromMe = true
            )
        )
        assertEquals(
            MessageHorizontalPlacement.Start,
            messageHorizontalPlacement(
                presentation = FloatingChatMessagePresentation.MediaStandalone,
                fromMe = false
            )
        )
    }

    @Test
    fun standaloneVideoMessagesStayBubblelessLikeStandaloneImages() {
        assertEquals(
            false,
            messageUsesBubbleChrome(FloatingChatMessagePresentation.MediaStandalone)
        )
        assertEquals(
            true,
            standaloneMessageTypeUsesCleanMediaSurface(FloatingChatMessageType.VideoPreview)
        )
        assertEquals(
            true,
            standaloneMessageTypeUsesCleanMediaSurface(FloatingChatMessageType.ImageThumbnail)
        )
    }

    @Test
    fun standaloneMediaCanBePreviewedAndLongPressedFromSameTouchLayer() {
        assertEquals(true, standaloneMediaUsesCombinedClickForPreviewAndLongPress())
        assertEquals(true, standaloneMediaLongPressOpensMessageMenu())
        assertEquals(true, standaloneMediaClickTogglesSelectionInMultiSelect())
    }

    @Test
    fun rotatedVideoDimensionsSwapAspectRatio() {
        assertEquals((9f / 16f).toString(), mediaAspectRatioFromDimensions(1920, 1080, 90)?.toString())
        assertEquals((9f / 16f).toString(), mediaAspectRatioFromDimensions(1920, 1080, 270)?.toString())
        assertEquals((16f / 9f).toString(), mediaAspectRatioFromDimensions(1920, 1080, 0)?.toString())
    }

    @Test
    fun videoTimecodeFormattingMatchesPlayerChrome() {
        assertEquals("00:00", formatVideoTimecode(0))
        assertEquals("01:05", formatVideoTimecode(65_000))
        assertEquals("1:01:01", formatVideoTimecode(3_661_000))
    }

    @Test
    fun threadMediaPreviewOnlyIncludesMediaFromCurrentContext() {
        val conversation = FloatingChatPrototype.sampleConversation()
        val groupMedia = previewableThreadMedia(
            messages = visibleMessagesForThread(
                conversation = conversation,
                selection = ChatThreadSelection.GroupChat("group-product"),
                selectedAccountId = FloatingChatPrototype.pairedAccountFor(conversation, "group-product").id
            )
        )
        val privateMedia = previewableThreadMedia(
            messages = visibleMessagesForThread(
                conversation = conversation,
                selection = ChatThreadSelection.Private("li-si"),
                selectedAccountId = FloatingChatPrototype.pairedAccountFor(conversation, "li-si").id
            )
        )

        assertEquals(true, groupMedia.all { it.threadContactId == null || it.threadContactId == "group-product" })
        assertEquals(true, privateMedia.all { it.threadContactId == "li-si" || it.threadContactId == null })
        assertEquals(true, groupMedia.all { it.type == FloatingChatMessageType.ImageThumbnail || it.type == FloatingChatMessageType.VideoPreview })
        assertEquals(true, privateMedia.all { it.type == FloatingChatMessageType.ImageThumbnail || it.type == FloatingChatMessageType.VideoPreview })
    }

    @Test
    fun rightRailToolActionThatDirectlySendsSimulatedMessageIsExplicit() {
        assertEquals(
            emptySet<FloatingChatToolAction>(),
            simulatedMessageToolActions()
        )
        assertEquals(true, rightRailAssistantOpensAiConfigPanel())
        assertEquals(true, bottomInputAssistantUsesAiDraftPrediction())
        assertEquals(true, assistantPredictionRequiresAiConfiguration())
        assertEquals(true, aiConfigPanelSupportsConnectionTest())
        assertEquals("\u751f\u6210\u5185\u5bb9\u6e29\u5ea6", aiConfigTemperatureLabel())
        assertEquals(true, localMessageReplacementInvalidatesDisplayConversation())
        assertEquals(true, documentToolRequestsSystemFilePicker())
        assertEquals(true, pickedDocumentCreatesRealFileMessage())
        assertEquals(true, pickedDocumentMessagesEnterScrmOutbox())
    }

    @Test
    fun rightRailToolsMatchWechatStyleFirstPass() {
        val aiVoiceAction = enumValueOf<FloatingChatToolAction>("AiVoice")

        assertEquals(
            listOf(
                FloatingChatToolAction.Assistant,
                aiVoiceAction,
                FloatingChatToolAction.Contacts,
                FloatingChatToolAction.Blink,
                FloatingChatToolAction.Gallery,
                FloatingChatToolAction.Camera,
                FloatingChatToolAction.Location,
                FloatingChatToolAction.Favorite,
                FloatingChatToolAction.RedPacket,
                FloatingChatToolAction.Transfer,
                FloatingChatToolAction.Files,
                FloatingChatToolAction.Card,
                FloatingChatToolAction.Moments,
                FloatingChatToolAction.MomentMaterials,
                FloatingChatToolAction.QuickPhrase
            ),
            referenceToolActionsFor(FloatingChatToolAction.entries)
        )
        assertEquals("联系人", toolActionLabel(FloatingChatToolAction.Contacts))
        assertEquals("相册", toolActionLabel(FloatingChatToolAction.Gallery))
        assertEquals("摄影", toolActionLabel(FloatingChatToolAction.Camera))
        assertEquals("位置", toolActionLabel(FloatingChatToolAction.Location))
        assertEquals("收藏", toolActionLabel(FloatingChatToolAction.Favorite))
        assertEquals("红包", toolActionLabel(FloatingChatToolAction.RedPacket))
        assertEquals("转账", toolActionLabel(FloatingChatToolAction.Transfer))
        assertEquals("文档", toolActionLabel(FloatingChatToolAction.Files))
        assertEquals("推名片", toolActionLabel(FloatingChatToolAction.Card))
        assertEquals("朋友圈", toolActionLabel(FloatingChatToolAction.Moments))
        assertEquals("朋友圈素材", toolActionLabel(FloatingChatToolAction.MomentMaterials))
        assertEquals("快捷语", toolActionLabel(FloatingChatToolAction.QuickPhrase))
        assertEquals("AI语音", toolActionLabel(aiVoiceAction))
        assertEquals(true, toolActionOpensBottomPanel(FloatingChatToolAction.MomentMaterials))
        assertEquals("MomentMaterials", toolActionBottomPanelModeName(FloatingChatToolAction.MomentMaterials))
        assertEquals(true, toolActionOpensBottomPanel(aiVoiceAction))
        assertEquals("AiVoice", toolActionBottomPanelModeName(aiVoiceAction))
        assertEquals(true, rightRailToolButtonsUseMaterialIcons())
        assertEquals(true, rightRailToolButtonsShowTextLabels())
        assertEquals(true, rightRailToolButtonsSupportLongPressReorder())
        assertEquals(true, rightRailToolButtonsSupportDragReorder())
        assertEquals(true, rightRailToolReorderUsesLongPressDragGesture())
        assertEquals(true, rightRailToolReorderFollowsDraggedIcon())
        assertEquals(true, rightRailToolReorderKeepsOriginalIcon())
        assertEquals(true, rightRailToolReorderShowsLongPressFeedback())
        assertEquals(true, rightRailToolReorderAnimatesDisplacedItems())
        assertEquals(true, rightRailToolReorderUsesSinglePointerGesture())
        assertEquals(true, rightRailToolReorderUsesAbsoluteFingerOffset())
        assertEquals(true, rightRailToolReorderSkipsPlacementAnimationForDraggedItem())
        assertEquals(true, rightRailToolReorderModeLetsAnyButtonDragImmediately())
        assertEquals(true, rightRailToolReorderModeChangeDoesNotRestartActivePointerGesture())
        assertEquals(false, rightRailToolReorderParentConsumesTapGestures())
        assertEquals(true, rightRailToolGestureCancelsLongPressWhenMovedBeforeTimeout())
        assertEquals(true, rightRailToolGestureCancelsClickWhenMovedPastTouchSlop())
        assertEquals(false, rightRailToolGestureConsumesDownBeforeClick())
        assertEquals(true, rightRailToolReorderMovesByDraggedCenterCrossingSlots())
        assertEquals(true, rightRailToolListScrollDisabledDuringReorderDrag())
        assertEquals(true, rightRailToolReorderPersistsOnDragEnd())
        assertEquals(true, rightRailToolSingleTapExitsReorder())
        assertEquals(false, rightRailToolReorderUsesClickToMove())
        assertEquals(true, contactsToolOpensCenteredFloatingPanel())
        assertEquals(true, contactPanelMatchesMomentsFloatingSheet())
        assertEquals(false, rightRailUsesDiscreteSnapExpansion())
        assertEquals(false, rightRailUsesContinuousDragExpansion())
        assertEquals(true, rightRailUsesAreaBasedExpansion())
        assertEquals(true, rightRailUsesIndependentListScrolling())
        assertEquals(false, rightRailKeepsAccountAndToolSectionHeightsStableWhileScrolling())
        assertEquals(true, rightRailStopsExpansionAtContentHeightWhenItemsAreShort())
        assertEquals(true, rightRailKeepsSelectedAccountConnectorAnchorWhenCompressed())
        assertEquals(true, rightRailKeepsAnyAccountConnectorAnchorWhenScrolledOffscreen())
        assertEquals(0f, rightRailAccountConnectorAnchorFollowsVirtualOffscreenPosition())
        assertEquals(0f, rightRailSingleVisibleAccountKeepsUpperOffscreenAnchorAbove())
        assertEquals(true, rightRailOffscreenAccountConnectorUsesEdgeIndicator())
        assertEquals(46f, rightRailPinnedSelectedAccountConnectorAnchorYWhenCompressed())
        assertEquals(true, rightRailPinsSelectedAccountAvatarWhileScrolledOffscreen())
        assertEquals(
            RailPinnedAvatarEdge.Top,
            rightRailPinnedSelectedAccountEdge(
                accountIds = listOf("a", "b", "c"),
                selectedAccountId = "a",
                visibleItems = listOf(
                    RightRailVisibleAccountItem(index = 1, offset = 0, size = 42),
                    RightRailVisibleAccountItem(index = 2, offset = 48, size = 42)
                ),
                viewportHeightPx = 120f,
                fallbackStepPx = 48f
            )
        )
        assertEquals(
            RailPinnedAvatarEdge.Bottom,
            rightRailPinnedSelectedAccountEdge(
                accountIds = listOf("a", "b", "c"),
                selectedAccountId = "c",
                visibleItems = listOf(
                    RightRailVisibleAccountItem(index = 0, offset = 0, size = 42),
                    RightRailVisibleAccountItem(index = 1, offset = 48, size = 42)
                ),
                viewportHeightPx = 120f,
                fallbackStepPx = 48f
            )
        )
        assertEquals(
            null,
            rightRailPinnedSelectedAccountEdge(
                accountIds = listOf("a", "b", "c"),
                selectedAccountId = "b",
                visibleItems = listOf(RightRailVisibleAccountItem(index = 1, offset = 48, size = 42)),
                viewportHeightPx = 120f,
                fallbackStepPx = 48f
            )
        )
        assertEquals(0.42f, defaultRightRailAccountWeight())
        assertEquals(0.24f, minRightRailAccountWeight())
        assertEquals(0.70f, maxRightRailAccountWeight())
        assertEquals(0.25f, rightRailSectionShiftFraction())
        assertEquals(0.67f, rightRailAccountWeightForAccountAreaDrag())
        assertEquals(0.24f, rightRailAccountWeightForToolAreaDrag())
        assertEquals(58, rightRailWidthDp())
        assertEquals(8, rightRailAvatarScreenEdgeInsetPx())
        assertEquals(8, rightRailToolIconScreenEdgeInsetPx())
        assertEquals(42, rightRailToolButtonWidthDp())
        assertEquals(42, rightRailToolButtonHeightDp())
        assertEquals(
            listOf(
                FloatingChatToolAction.Gallery,
                FloatingChatToolAction.Assistant,
                FloatingChatToolAction.Camera
            ),
            moveToolAction(
                actions = listOf(
                    FloatingChatToolAction.Assistant,
                    FloatingChatToolAction.Gallery,
                    FloatingChatToolAction.Camera
                ),
                fromIndex = 1,
                toIndex = 0
            )
        )
        assertEquals(0, toolReorderTargetIndex(0, 21f, 48f, 3))
        assertEquals(1, toolReorderTargetIndex(0, 25f, 48f, 3))
        assertEquals(0, toolReorderTargetIndex(1, -25f, 48f, 3))
        assertEquals(2, toolReorderTargetIndex(1, 64f, 48f, 3))
        assertEquals(2, toolReorderTargetIndex(2, 99f, 48f, 3))
        assertEquals(
            listOf(
                FloatingChatToolAction.Assistant,
                aiVoiceAction,
                FloatingChatToolAction.Contacts
            ),
            mergeToolActionOrder(
                storedActions = listOf(
                    FloatingChatToolAction.Assistant,
                    FloatingChatToolAction.Contacts
                ),
                fallbackActions = listOf(
                    FloatingChatToolAction.Assistant,
                    aiVoiceAction,
                    FloatingChatToolAction.Contacts
                )
            )
        )
    }

    @Test
    fun rightRailAccountProfileEditingFeedsCardTool() {
        val conversation = FloatingChatPrototype.sampleConversation()
        val account = conversation.accountContacts.first()
        val profile = defaultAccountProfileFor(account).copy(
            name = "林舟",
            phone = "13800138000",
            signature = "把每一次会话都处理清楚",
            gender = "男",
            company = "星河产品实验室",
            title = "产品负责人",
            region = "深圳",
            wechatId = "linzhou_app",
            email = "linzhou@example.com",
            tags = "产品,协作",
            avatarInitials = "林舟",
            avatarColor = 0xFF5E8FA3,
            avatarImageUri = "file:///tmp/linzhou-avatar.jpg"
        )
        val message = accountProfileCardMessage(
            profile = profile,
            baseMessage = FloatingChatPrototype.simulatedToolMessage(
                conversation = conversation,
                action = FloatingChatToolAction.Card,
                selection = FloatingChatPrototype.ToolThreadSelection.Private(conversation.contacts.first().id),
                accountId = account.id,
                sequence = 1
            )
        )

        assertEquals(true, rightRailAccountAvatarSupportsLongPressEdit())
        assertEquals(true, rightRailAccountAvatarClickSelectsSendingAccount())
        assertEquals(
            listOf("avatarImage", "name", "phone", "signature", "gender", "company", "title", "region", "wechatId", "email", "tags"),
            accountProfileEditorFieldKeys()
        )
        assertEquals(true, accountProfileEditorSupportsImageAvatarUpload())
        assertEquals(true, accountProfileEditorHidesAvatarColorPalette())
        assertEquals(true, accountProfileEditorPersistsChanges())
        assertEquals(true, cardToolSendsEditedAccountProfileCard())
        assertEquals(true, cardToolUsesSelectedAccountInsteadOfThreadDefault())
        assertEquals(true, cardToolOpensAccountPickerInsteadOfDirectSend())
        assertEquals(true, cardToolAccountPickerShowsAllAccounts())
        assertEquals(true, contactLinkMessageReusesAccountCardPreview())
        assertEquals("推名片：林舟", message.text)
        assertEquals(FloatingChatContactCardKind.Personal, message.cardKind)
        assertEquals("林舟", message.cardName)
        assertEquals("产品负责人 · 星河产品实验室 · 深圳", message.cardSubtitle)
        assertEquals(
            "电话 13800138000 · 微信 linzhou_app · 邮箱 linzhou@example.com · 性别 男 · 签名 把每一次会话都处理清楚 · 标签 产品,协作",
            message.detail
        )
        assertEquals("https://aiff.app/cards/account-main-linzhou-app", message.resourceUrl)
        assertEquals("file:///tmp/linzhou-avatar.jpg", message.thumbnailUrl)
    }

    @Test
    fun rightRailSelectedAccountDrivesCardProfile() {
        val conversation = FloatingChatPrototype.sampleConversation()
        val defaultAccount = selectedAccountForThread(
            conversation = conversation,
            selection = ChatThreadSelection.Private("li-si")
        )
        val selectedAccount = selectedAccountForThread(
            conversation = conversation,
            selection = ChatThreadSelection.Private("li-si"),
            overrideAccountId = "account-private"
        )
        val profile = defaultAccountProfileFor(selectedAccount).copy(
            name = "私域顾问",
            phone = "13900001111",
            wechatId = "private_advisor",
            avatarImageUri = "content://avatar/account-private"
        )
        val message = accountProfileCardMessage(
            profile = profile,
            baseMessage = FloatingChatPrototype.simulatedToolMessage(
                conversation = conversation,
                action = FloatingChatToolAction.Card,
                selection = FloatingChatPrototype.ToolThreadSelection.Private("li-si"),
                accountId = selectedAccount.id,
                sequence = 2
            )
        )

        assertEquals("account-work", defaultAccount.id)
        assertEquals("account-private", selectedAccount.id)
        assertEquals("推名片：私域顾问", message.text)
        assertEquals("私域顾问", message.cardName)
        assertEquals("content://avatar/account-private", message.thumbnailUrl)
        assertEquals("https://aiff.app/cards/account-private-private-advisor", message.resourceUrl)
    }

    @Test
    fun accountProfileUsesRemoteAvatarAndShowsWechatQr() {
        val account = FloatingChatContact(
            id = "account-aiken",
            name = "Aiken",
            initials = "AI",
            description = "scrm account",
            avatarColor = 0xFF5E8FA3,
            avatarUrl = "https://cdn.example.com/aiken.jpg"
        )
        val profile = defaultAccountProfileFor(account).copy(wechatId = "aiken_001")

        assertEquals("https://cdn.example.com/aiken.jpg", profile.avatarImageUri)
        assertEquals(true, accountProfileEditorSupportsWechatQrCode())
        assertEquals("aiken_001", accountProfileQrPayload(profile))
    }

    @Test
    fun momentMaterialsAreAccountScopedAndOpenDetailPage() {
        val route = ScrmFloatingAccountRoute(
            deviceUuid = "device-1",
            weChatId = "wxid_aiken"
        )

        assertEquals("wxid_aiken", momentMaterialTenantIdForRoute(route))
        assertEquals(null, momentMaterialTenantIdForRoute(null))
        assertEquals(true, momentMaterialsPanelUsesAccountScopedTenant())
        assertEquals(true, momentMaterialsPanelUsesQuickPhraseStyleList())
        assertEquals(true, momentMaterialsPanelOpensIndependentDetailPage())
    }

    @Test
    fun contactAddFriendCanParseFindResultButEntrySubmitsDirectApply() {
        val data = Json.parseToJsonElement(
            """
            {
              "data": {
                "friendId": "wxid_found",
                "nickname": "Alice",
                "avatar": "https://cdn.example.com/alice.jpg",
                "region": "Shenzhen",
                "signature": "hello"
              }
            }
            """.trimIndent()
        )
        val profile = requireNotNull(scrmFriendSearchProfileFromFindContactData(data))

        assertEquals("wxid_found", profile.friendId)
        assertEquals("Alice", profile.displayName)
        assertEquals("https://cdn.example.com/alice.jpg", profile.avatarUrl)
        assertEquals("Shenzhen", profile.region)
        assertEquals("hello", profile.signature)
        val request = scrmDirectAddFriendRequest(
            deviceUuid = "device-1",
            weChatId = "wxid_owner",
            friendAccount = "  wxid_target  ",
            message = "  我是白菜  "
        )

        assertEquals(false, contactAddFriendUsesSearchBeforeApply())
        assertEquals(false, contactAddFriendShowsIndependentProfileBeforeApply())
        assertEquals("device-1", request.deviceUuid)
        assertEquals("wxid_owner", request.weChatId)
        assertEquals("wxid_target", request.friendWxid)
        assertEquals("我是白菜", request.message)
        assertEquals("正在发送好友申请", wechatContactsStatusText(loading = true, status = "正在发送好友申请", error = null))
        assertEquals("好友申请已发送：已提交任务 #42", friendApplySubmittedStatus("已提交任务 #42"))
    }

    @Test
    fun accountAvatarClickSwitchesToIndependentAccountWorkspace() {
        val conversation = FloatingChatPrototype.sampleConversation()
        val mainConversation = accountScopedConversation(
            conversation = conversation,
            activeAccountId = "account-main"
        )
        val workConversation = accountScopedConversation(
            conversation = conversation,
            activeAccountId = "account-work"
        )
        val workThread = defaultChatThreadSelection(workConversation)
        val workMessages = visibleMessagesForThread(
            conversation = workConversation,
            selection = workThread,
            selectedAccountId = "account-work"
        )
        val resetThread = initialChatThreadSelection(
            conversation = workConversation,
            preferredSelection = ChatThreadSelection.GroupChat(mainConversation.groupContacts.first().id)
        )
        val clickResetThread = selectedThreadAfterAccountAvatarClick(
            conversation = conversation,
            clickedAccountId = "account-work",
            currentThread = ChatThreadSelection.GroupChat(mainConversation.groupContacts.first().id)
        )

        assertEquals(true, rightRailAccountAvatarClickSwitchesActiveAccountWorkspace())
        assertEquals("account-work", selectedAccountIdAfterAccountAvatarClick("account-main", "account-work"))
        assertEquals(conversation.accountContacts.size, workConversation.accountContacts.size)
        assertEquals(true, workConversation.accountContacts.first { it.id == "account-work" }.selected)
        assertEquals(false, mainConversation.contacts.map { it.id } == workConversation.contacts.map { it.id })
        assertEquals(false, mainConversation.groupContacts.map { it.id } == workConversation.groupContacts.map { it.id })
        assertEquals(true, workConversation.contacts.all { it.id.startsWith("account-work__") })
        assertEquals(true, workConversation.groupContacts.all { it.id.startsWith("account-work__") })
        assertEquals("account-work", selectedAccountForThread(workConversation, workThread).id)
        assertEquals(true, workMessages.isNotEmpty())
        assertEquals(true, workMessages.filter { it.fromMe }.all { it.connectionTargetId == "account-work" })
        assertEquals(workConversation.groupContacts.first().id, (resetThread as ChatThreadSelection.GroupChat).groupId)
        assertEquals(workConversation.groupContacts.first().id, (clickResetThread as ChatThreadSelection.GroupChat).groupId)
        assertEquals(true, accountScopedFriendsAndGroupsAreIndependent())
        assertEquals(true, accountSwitchResetsThreadToScopedDefault())
    }

    @Test
    fun storeServiceAccountShowsThreeTimesAsManyLeftRailContactsWithoutAvatarTextTags() {
        val conversation = FloatingChatPrototype.sampleConversation()
        val storeConversation = accountScopedConversation(
            conversation = conversation,
            activeAccountId = "account-store"
        )
        val mainConversation = accountScopedConversation(
            conversation = conversation,
            activeAccountId = "account-main"
        )

        assertEquals(15, storeConversation.contacts.size)
        assertEquals(5, mainConversation.contacts.size)
        assertEquals(false, avatarTextTagsVisible())
    }

    @Test
    fun quickPhrasePanelSupportsRecentPhrasesAndCrud() {
        assertEquals(true, quickPhraseToolOpensPanelInsteadOfDirectSend())
        assertEquals(true, quickPhrasePanelShowsRecentPhrases())
        assertEquals(true, quickPhrasePanelSupportsCrud())
        assertEquals(true, quickPhrasePanelCanSendSelectedPhrase())
        assertEquals(
            listOf(
                "收到，我先看一下，稍后同步进展。",
                "这个我确认后回复你。",
                "可以，按这个方案推进。"
            ),
            defaultQuickPhrases()
        )
    }

    @Test
    fun appOwnedWechatLikeToolsOpenInternalFlows() {
        assertEquals(true, momentsToolOpensInAppTimeline())
        assertEquals(true, momentsTimelineBelongsToFloatingChatApp())
        assertEquals(true, momentsTimelineSupportsComposePost())
        assertEquals(true, momentsTimelineMatchesWechatFeedLayout())
        assertEquals(true, momentsTimelineShowsAvatarNameMediaTimeAndMore())
        assertEquals(true, momentsTimelineSupportsLikeAndComment())
        assertEquals(true, momentsMoreButtonShowsWechatLikeCommentMenu())
        assertEquals(true, momentsInlineLikeCommentButtonsAreHiddenUntilMoreMenu())
        assertEquals(true, momentsComposerSupportsImageAndVideo())
        assertEquals(true, momentsMediaPickDoesNotSendChatMessage())
        assertEquals(true, momentsPanelUsesLargerFloatingSheetWithCompactContent())
        assertEquals(true, momentsComposedPostsPersistInSqlite())
        assertEquals(true, momentsTimelineRestoresPersistedPostsOnOverlayRecreate())
        assertEquals(true, locationPermissionRequestHidesFloatingOverlayUntilResult())
        assertEquals(true, toolFeaturePanelsUseCenteredFloatingSheet())
        assertEquals(330, toolFeaturePanelMinWidthDp())
        assertEquals(430, toolFeaturePanelMaxWidthDp())
        assertEquals(560, toolFeaturePanelMaxHeightDp())
        assertEquals(true, redPacketToolOpensInAppComposer())
        assertEquals(true, redPacketPanelSendsAmountAndGreeting())
        assertEquals(true, transferToolOpensInAppComposer())
        assertEquals(true, transferPanelSendsAmountAndNote())
        assertEquals(true, locationToolOpensInAppPickerInsteadOfDirectSend())
        assertEquals(true, locationPanelSendsSelectedLocation())
        assertEquals(true, locationToolUsesRealDeviceLocation())
        assertEquals(true, locationToolRequestsRuntimePermission())
        assertEquals(true, locationPanelStartsRealLocationRefreshAutomatically())
        assertEquals(false, locationPanelUsesOnlyPresetLocations())
        assertEquals(true, locationMessageIncludesCoordinatesInResourceUrl())
        assertEquals(true, favoriteToolOpensInAppCollectionPage())
        assertEquals(true, favoritePageShowsSavedMessagesLinksImagesVideos())
        assertEquals(true, favoriteCollectionPersistsSavedItems())
        assertEquals(true, favoriteCollectionRestoresSavedItemsBeforeCurrentSession())
        assertEquals(false, favoriteToolDirectlySendsSimulatedMessage())
        assertEquals(true, appOwnedPaymentToolsSendChatMessages())
        assertEquals(false, appOwnedWechatLikeToolsOpenWechat())
    }

    @Test
    fun groupChatMemberAvatarsMoveWithMessagesAndCanBeHidden() {
        assertEquals(true, groupChatMemberAvatarScrollsWithMessageBubble())
        assertEquals(4, groupMemberAvatarBubbleCenterOffsetDp())
        assertEquals(true, groupChatConnectorUsesMessageScopedMemberAvatar())
        assertEquals(true, groupChatMemberToBubbleConnectorUsesDirectLine())
        assertEquals(true, groupChatMemberToBubbleConnectorSkipsTreeBend())
        assertEquals(true, groupChatMemberAvatarVisibilityCanBeToggledFromGroupEditPanel())
        assertEquals(true, groupChatHiddenMemberAvatarUsesGroupAvatarConnector())
    }

    @Test
    fun groupMemberToBubbleConnectorIsASingleDirectSegment() {
        val branch = createGroupMemberMessageConnectorBranch(
            avatarBounds = androidx.compose.ui.geometry.Rect(10f, 20f, 38f, 48f),
            bubbleBounds = androidx.compose.ui.geometry.Rect(48f, 10f, 148f, 58f),
            layerBounds = androidx.compose.ui.geometry.Rect(0f, 0f, 200f, 100f)
        )

        assertEquals(38f, branch.start.x)
        assertEquals(34f, branch.start.y)
        assertEquals(48f, branch.end.x)
        assertEquals(34f, branch.end.y)
    }

    @Test
    fun leftRailAvatarsOpenFloatingEditPanels() {
        assertEquals(true, leftRailAvatarsSupportLongPressEditPanel())
        assertEquals(true, contactEditPanelSupportsRemarkAndTags())
        assertEquals(true, contactEditPanelUsesWechatFriendProfileLayout())
        assertEquals(true, contactEditPanelHasDeleteFriendAction())
        assertEquals(true, wechatContactIntroFriendProfileReusesLongPressPanel())
        assertEquals(listOf("备注", "朋友权限", "更多信息"), contactEditPanelWechatSectionTitles())
        assertEquals(
            listOf("备注名", "电话", "标签", "备注", "照片", "朋友圈和状态", "仅聊天", "我和他的共同群聊", "来源", "添加时间"),
            contactEditPanelWechatFieldLabels()
        )
        assertEquals(true, groupAvatarLongPressOpensFloatingEditPanel())
        assertEquals(true, groupEditPanelUsesWechatChatInfoLayout())
        assertEquals(true, groupEditPanelShowsAllLoadedMembers())
        assertEquals(true, groupEditPanelMemberAvatarOpensContactIntro())
        assertEquals(true, groupEditPanelInviteAndKickUseRealScrmApis())
        assertEquals(listOf("添加成员"), groupInfoMemberManagementLabels(canManageMembers = false))
        assertEquals(listOf("添加成员", "移出成员"), groupInfoMemberManagementLabels(canManageMembers = true))
        assertEquals("添加到通讯录", groupInfoMemberPrimaryActionLabel(isFriend = false))
        assertEquals("发消息", groupInfoMemberPrimaryActionLabel(isFriend = true))
        assertEquals("正在发送好友申请", groupMemberAddFriendStatusText(loading = true, status = null, error = null))
        assertEquals("好友申请已发送：等待对方确认", groupMemberAddFriendStatusText(loading = false, status = "好友申请已发送：等待对方确认", error = null))
        assertEquals(
            listOf("群聊名称", "群二维码", "群公告", "备注", "查找聊天记录", "消息免打扰", "置顶聊天", "保存到通讯录", "我在群里的昵称", "显示群成员昵称", "显示群成员头像", "设置当前聊天背景", "清空聊天记录", "投诉", "退出群聊"),
            groupEditPanelWechatFieldLabels()
        )
        assertEquals(true, groupEditPanelPersistsChangesInSqlite())
        assertEquals(true, groupEditPanelStoresMemberAvatarVisibilityPerGroup())

        val group = FloatingChatContact(
            id = "group-a",
            name = "真的很爱上班了",
            initials = "群",
            description = "group",
            avatarColor = 0xFF5B7CFA,
            groupMemberContacts = (1..12).map { index ->
                FloatingChatContact(
                    id = "member-$index",
                    name = "成员$index",
                    initials = "成$index",
                    description = "member",
                    avatarColor = 0xFF3A86FF + index
                )
            }
        )
        val knownFriend = group.groupMemberContacts.first().copy(name = "已是好友")
        val ownerMember = group.groupMemberContacts.first().copy(groupMemberIsOwner = true)
        val knownOwnerFriend = ownerMember.copy(name = "已是好友群主", groupMemberIsOwner = false)

        assertEquals(12, groupInfoMemberCount(group.groupMemberContacts))
        assertEquals(12, groupInfoMembersForGroup(group, contacts = listOf(knownFriend), messages = emptyList()).size)
        assertEquals("已是好友", groupInfoMembersForGroup(group, contacts = listOf(knownFriend), messages = emptyList()).first().name)
        assertEquals(
            true,
            groupInfoMembersForGroup(
                group.copy(groupMemberContacts = listOf(ownerMember)),
                contacts = listOf(knownOwnerFriend),
                messages = emptyList()
            ).first().groupMemberIsOwner
        )
        assertEquals(4, groupInfoMemberGridRows(group.groupMemberContacts).size)
        assertEquals(true, groupInfoMemberIsFriend(knownFriend, contacts = listOf(knownFriend)))
        assertEquals(false, groupInfoMemberIsFriend(group.groupMemberContacts[1], contacts = listOf(knownFriend)))

        assertEquals(false, groupInfoCanManageMembers(currentMember = null))
        assertEquals(
            true,
            groupInfoCanManageMembers(
                FloatingChatContact(
                    id = "owner",
                    name = "群主",
                    initials = "主",
                    description = "member",
                    avatarColor = 0xFF111111,
                    groupMemberIsOwner = true
                )
            )
        )
        assertEquals(
            true,
            groupInfoCanManageMembers(
                FloatingChatContact(
                    id = "admin",
                    name = "管理员",
                    initials = "管",
                    description = "member",
                    avatarColor = 0xFF222222,
                    groupMemberIsAdmin = true
                )
            )
        )
        assertEquals(
            false,
            groupInfoCanManageMembers(
                FloatingChatContact(
                    id = "member",
                    name = "普通成员",
                    initials = "员",
                    description = "member",
                    avatarColor = 0xFF333333
                )
            )
        )
    }

    @Test
    fun groupProfileEditsApplyToGroupListAndConnectorVisibility() {
        val conversation = FloatingChatPrototype.sampleConversation()
        val profile = LocalGroupProfile(
            accountId = "account-store",
            groupId = "group-ops",
            groupName = "真的很爱上班了",
            remark = "华东门店会员日",
            announcement = "今晚 18:00 前同步排班",
            myNickname = "白茶豆腐",
            mute = true,
            pinned = true,
            saveToContacts = false,
            showMemberNicknames = true,
            showMemberAvatars = false,
            updatedAt = 123L
        )
        val updated = applyGroupProfilesToConversation(
            conversation = conversation,
            profiles = listOf(profile),
            accountId = "account-store"
        )

        assertEquals("真的很爱上班了", updated.groupContacts.first { it.id == "group-ops" }.name)
        assertEquals("真的", updated.groupContacts.first { it.id == "group-ops" }.initials)
        assertEquals(false, groupMemberAvatarsVisibleForSelection(ChatThreadSelection.GroupChat("group-ops"), mapOf("group-ops" to profile)))
        assertEquals(true, groupMemberAvatarsVisibleForSelection(ChatThreadSelection.GroupChat("group-ai"), mapOf("group-ops" to profile)))
    }

    @Test
    fun favoriteCollectionStorageRoundTripsSavedFields() {
        assertEquals(true, favoriteCollectionSerializationRoundTrips())
    }

    @Test
    fun favoriteCollectionOpensContentAndUsesLimitedLongPressActions() {
        val imageUri = "content://media/external/images/media/42"
        val item = FavoriteCollectionItem(
            messageId = "favorite-image",
            type = FloatingChatMessageType.ImageThumbnail,
            title = "图片收藏",
            description = imageUri,
            source = "何苗 - 14:21"
        )
        val previewMessage = favoriteCollectionPreviewMessage(item)

        assertEquals(true, favoriteCollectionClickOpensContentPreview())
        assertEquals(false, favoriteCollectionClickSendsToChat())
        assertEquals(FloatingChatMessageType.ImageThumbnail, previewMessage.type)
        assertEquals(FloatingChatMessagePresentation.MediaStandalone, previewMessage.presentation)
        assertEquals(imageUri, previewMessage.resourceUrl)
        assertEquals(imageUri, previewMessage.thumbnailUrl)
        assertEquals(
            listOf(
                MessageLongPressAction.Forward,
                MessageLongPressAction.Delete,
                MessageLongPressAction.MultiSelect
            ),
            favoriteCollectionLongPressActions()
        )
        assertEquals(listOf("转发", "删除", "关闭"), favoriteCollectionPreviewActionLabels())
        assertEquals("收藏", favoriteCollectionPreviewTimestampLabel())
        assertEquals("已选 2", favoriteCollectionSelectionCountLabel(2))
        assertEquals(false, locationMapBubbleTextUsesShadow())

        val legacyTextItem = FavoriteCollectionItem(
            messageId = "legacy-text",
            type = FloatingChatMessageType.Text,
            title = "\u93c2\u56e8\u6e70\u93c0\u60f0\u68cc",
            description = "\u6924\u572d\u6d30\u6769\u6d98\u5bb3",
            source = "\u6d63\u66e1\u5acd \u8def 14:21"
        )
        val legacyPreviewMessage = favoriteCollectionPreviewMessage(legacyTextItem)

        assertEquals("文本收藏", legacyPreviewMessage.text)
        assertEquals("项目进度", legacyPreviewMessage.detail)
        assertEquals("何苗 · 14:21", legacyPreviewMessage.senderName)
        assertEquals("何苗 · 14:21", legacyPreviewMessage.quoteAuthor)
        assertEquals("项目进度", legacyPreviewMessage.quoteText)
    }

    @Test
    fun favoriteCollectionStoredSourceFollowsCurrentRemarkedMessageSender() {
        val currentMessage = FloatingChatMessage(
            id = "favorite-remark-source",
            type = FloatingChatMessageType.Text,
            text = "已收藏的消息",
            fromMe = false,
            senderName = "老沈",
            time = "09:10"
        )
        val staleItem = FavoriteCollectionItem(
            messageId = "favorite-remark-source",
            type = FloatingChatMessageType.Text,
            title = "已收藏的消息",
            description = "旧描述",
            source = "沈嘉木 · 09:10"
        )

        val refreshed = refreshFavoriteCollectionSourcesFromMessages(
            items = listOf(staleItem),
            messages = listOf(currentMessage)
        ).single()

        assertEquals("老沈 · 09:10", refreshed.source)
    }

    @Test
    fun galleryAndCameraMediaEntryContractsMatchWechat() {
        assertEquals(true, galleryToolPicksImageAndVideo())
        assertEquals(true, galleryToolDetectsPickedMediaKindFromMimeType())
        assertEquals("file:///data/user/0/com.paifa.ubikitouch/cache/floating-chat-original-media/video-1.mp4", playableVideoUriTextForMessage(galleryVideoMessageForPlayback()))
        assertEquals(null, playableVideoUriTextForMessage(galleryVideoMessageWithoutVideoResource()))
        assertEquals(true, galleryVideoPickerKeepsActivityAliveUntilMediaDelivery())
        assertEquals(true, cameraToolUsesWechatPressGesture())
        assertEquals(true, cameraToolTapCapturesPhoto())
        assertEquals(true, cameraToolLongPressRecordsVideo())
        assertEquals(true, cameraCapturePreviewRequiresExplicitSend())
        assertEquals(true, cameraCaptureShowsCapturedPhotoPreview())
        assertEquals(true, cameraCaptureShowsCapturedVideoPreview())
        assertEquals(true, cameraCapturePreviewCoversLiveCameraPreview())
        assertEquals(true, cameraCaptureNormalizesPhotoOrientationBeforeSend())
        assertEquals(true, cameraCaptureUsesDisplayRotationForPhotoOutput())
        assertEquals(true, cameraLongPressStartsRecordingImmediately())
        assertEquals(15_000, cameraVideoMaxDurationMs())
        assertEquals(true, cameraVideoAutoStopsAtMaxDuration())
        assertEquals(true, cameraRecordingShowsShutterProgressRing())
        assertEquals(true, cameraRecordingProgressUsesVideoMaxDuration())
        assertEquals(true, cameraRecordingReleaseShowsCapturedVideoPreview())
        assertEquals(true, cameraCapturePreviewOffersRetakeAndSend())
        assertEquals(true, cameraVideoCapturePreviewShowsPosterFrameBeforePlayback())
        assertEquals(true, cameraVideoCapturePreviewSwitchesToPlayerOnTap())
        assertEquals(true, cameraVideoCapturePreviewUsesRealPlayer())
        assertEquals(true, cameraVideoCapturePreviewStartsPlaybackOnTap())
        assertEquals(true, cameraVideoCapturePreviewShowsPlaybackFailureState())
        assertEquals(false, cameraVideoCapturePreviewSetsTextureViewBackground())
        assertEquals(true, cameraVideoCapturePreviewPlayerReleaseIsIdempotent())
        assertEquals(true, cameraVideoCapturePreviewPlayerReleaseHandlesInvalidState())
    }

    @Test
    fun floatingChatOverlaySurvivesRecoverableWindowErrors() {
        assertEquals(true, floatingChatServiceOverlayOperationsAreGuarded())
        assertEquals(true, floatingChatServiceOverlayRefreshRequiresInitializedControllers())
        assertEquals(true, floatingChatOverlayStaysAboveGestureOverlay())
        assertEquals(false, gestureOverlayIsBroughtToFrontAfterFloatingChatRecreated())
        assertEquals(
            false,
            floatingChatShouldRefreshExpandedWindowZOrder(
                force = false,
                hasComposeView = true,
                currentExpanded = true,
                nextExpanded = true
            )
        )
        assertEquals(
            false,
            floatingChatShouldRefreshExpandedWindowZOrder(
                force = false,
                hasComposeView = true,
                currentExpanded = false,
                nextExpanded = true
            )
        )
        assertEquals(true, floatingChatOverlayHandlesOwnEdgeGestures())
        assertEquals(false, floatingChatOverlayEdgeGestureConsumesPlainTaps())
        assertEquals(false, floatingChatInternalEdgeGestureObservesInitialPointerPass())
        assertEquals(8, floatingChatInternalEdgeGestureTouchTargetDp())
        assertEquals(gestureOverlayTouchTargetDp(1), floatingChatInternalEdgeGestureTouchTargetDp())
        assertEquals(false, floatingChatInternalEdgeGestureCoversSideRails())
        assertEquals(false, floatingChatInternalEdgeGestureUsesEarlyHorizontalLock())
        assertEquals(true, floatingChatExpandedBottomGestureHandledInsideOverlay())
        assertEquals(false, scrmConversationRefreshRecreatesExpandedFloatingChatOverlay())
        assertEquals(true, floatingChatOverlayHidesSemanticsFromItsOwningAccessibilityService())
        assertEquals(false, messageBlockUsesNegativePadding())
    }

    @Test
    fun messageLongPressMenuMatchesWechatWithoutSearchOrListen() {
        assertEquals(
            listOf(
                MessageLongPressAction.Copy,
                MessageLongPressAction.Forward,
                MessageLongPressAction.Favorite,
                MessageLongPressAction.Delete,
                MessageLongPressAction.MultiSelect,
                MessageLongPressAction.Quote,
                MessageLongPressAction.Reminder
            ),
            messageLongPressPrimaryActions()
        )
        assertEquals(true, messageLongPressUsesWechatFloatingPanel())
        assertEquals(true, messageLongPressSupportsInternalForwarding())
        assertEquals(true, messageLongPressReminderUsesUiStateOnly())
        assertEquals(false, messageLongPressIncludesSearch())
        assertEquals(false, messageLongPressIncludesListenFromHere())
        assertEquals(true, messageLongPressSupportsMultiSelectMode())
        assertEquals(true, messageLongPressQuoteShowsComposerPreview())
        assertEquals(true, messageLongPressMenuAnchorsToMessageBounds())
        assertEquals(false, messageLongPressMenuUsesFixedSidePosition())
    }

    @Test
    fun multiSelectForwardOffersSeparateAndCombinedChatHistory() {
        val messages = listOf(
            FloatingChatMessage(
                id = "m1",
                type = FloatingChatMessageType.Text,
                text = "运营😳",
                fromMe = false,
                senderName = "糯米惹仁汤",
                time = "10:39"
            ),
            FloatingChatMessage(
                id = "m2",
                type = FloatingChatMessageType.Text,
                text = "我说你晚上别睡过了",
                fromMe = false,
                senderName = "糖糖",
                time = "10:39"
            ),
            FloatingChatMessage(
                id = "m3",
                type = FloatingChatMessageType.Text,
                text = "打麻将",
                fromMe = false,
                senderName = "糖糖",
                time = "10:39"
            )
        )

        val history = combinedForwardChatHistoryMessage(
            messages = messages,
            conversation = FloatingChatConversation(
                peerName = "群聊",
                accountName = "我",
                contacts = emptyList(),
                accountContacts = listOf(
                    FloatingChatContact("account-1", "我", "我", "account", 0xFF123456)
                ),
                messages = emptyList(),
                toolActions = emptyList()
            ),
            target = ChatThreadSelection.GroupChat("room@chatroom"),
            accountId = "account-1",
            sequence = 9
        )

        assertEquals("已选 3", multiSelectSelectionCountLabel(3))
        assertEquals(listOf("逐条转发", "合并转发"), multiForwardModeLabels())
        assertEquals(FloatingChatMessageType.ChatHistory, history.type)
        assertEquals("群聊的聊天记录", history.text)
        assertEquals(listOf("糯米惹仁汤：运营😳", "糖糖：我说你晚上别睡过了", "糖糖：打麻将"), history.filePreviewLines)
        assertEquals(true, combinedForwardChatHistoryOpensDetailPage())
    }

    @Test
    fun wechatContactsPageContractsMatchRequestedEntryFlow() {
        val contacts = listOf(
            ScrmContact(id = 1, nickname = "Alice", wxid = "wxid_alice"),
            ScrmContact(id = 2, remarks = "白菜豆腐", wxid = "wxid_bai"),
            ScrmContact(id = 3, nickname = "Bob", wxid = "wxid_bob")
        )

        assertEquals("通讯录", wechatContactsTitle())
        assertEquals(listOf("发起群聊", "添加朋友", "扫一扫", "收付款"), wechatContactsPlusMenuLabels())
        assertEquals(listOf("发起群聊", "收付款"), wechatContactsPendingMenuLabels())
        assertEquals(true, wechatContactsStartGroupUsesContactPicker())
        assertEquals("发起群聊", wechatStartGroupTitle())
        assertEquals("完成", wechatStartGroupDoneLabel(0))
        assertEquals("完成(2)", wechatStartGroupDoneLabel(2))
        assertEquals(listOf("选择一个群", "面对面建群", "企业微信联系人"), wechatStartGroupOptionLabels())
        assertEquals(200, scrmContactsPanelContactQuery("wxid_account", pageNumber = 1, search = null).pageSize)
        assertEquals(2, scrmContactsPanelContactQuery("wxid_account", pageNumber = 2, search = "Alice").page)
        assertEquals("Alice", scrmContactsPanelContactQuery("wxid_account", pageNumber = 2, search = "Alice").search)
        assertEquals(
            "选择账号与设备当前微信账号不一致：当前设备 device-1 正在登录 wxid_current，不能使用 wxid_selected 执行该操作。请切换到对应微信账号后刷新。",
            scrmRouteCurrentDeviceMismatchMessage(
                route = ScrmFloatingAccountRoute("device-1", "wxid_selected"),
                devices = listOf(
                    ScrmDevice(
                        uuid = "device-1",
                        weChatId = "wxid_current",
                        isOnline = true,
                        status = 1,
                        androidApi = 35,
                        appVersionCode = 1,
                        updatedAt = "2026-07-15T00:00:00Z"
                    )
                )
            )
        )
        assertEquals(
            null,
            scrmRouteCurrentDeviceMismatchMessage(
                route = ScrmFloatingAccountRoute("device-1", "wxid_current"),
                devices = listOf(
                    ScrmDevice(
                        uuid = "device-1",
                        weChatId = "wxid_current",
                        isOnline = true,
                        status = 1,
                        androidApi = 35,
                        appVersionCode = 1,
                        updatedAt = "2026-07-15T00:00:00Z"
                    )
                )
            )
        )
        assertEquals(
            listOf("wxid_alice", "wxid_bai"),
            scrmCreateChatRoomMemberWxids(
                listOf(
                    ScrmContact(id = 1, nickname = "Alice", wxid = "wxid_alice"),
                    ScrmContact(id = 2, remarks = "白菜单腐", wxid = "wxid_bai"),
                    ScrmContact(id = 4, nickname = "Alice duplicate", wxid = "wxid_alice"),
                    ScrmContact(id = 5, nickname = "No remote id")
                )
            )
        )
        assertEquals(
            listOf("扫一扫", "手机联系人", "雷达", "企业微信联系人", "面对面建群", "公众号", "服务号"),
            wechatAddFriendPageEntryLabels()
        )
        assertEquals(listOf("Alice"), filterWechatContacts(contacts, "ali").map { it.displayName })
        assertEquals(listOf("白菜豆腐"), filterWechatContacts(contacts, "wxid_bai").map { it.displayName })
        assertEquals(listOf("A", "B"), groupedWechatContactSections(contacts).map { it.title })
        val summaries = scrmContactGroupSummaries(contacts, "")
        assertEquals(listOf("A", "B"), summaries.map { it.title })
        assertEquals(listOf("1", "3", "2"), summaries.flatMap { group -> group.contacts }.map { it.id })
        assertEquals("Alice", summaries.first().contacts.first().displayName)
        assertEquals(contacts[1], scrmContactsBySummaryId(contacts).getValue("2"))
        assertEquals(listOf("☆", "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z", "#"), contactIndexLabels())
        assertEquals(listOf("朋友资料", "朋友圈"), wechatContactIntroInfoRowLabels())
        assertEquals(listOf("发消息", "音视频通话"), wechatContactIntroActionLabels())
    }

    @Test
    fun quotedComposerSendsQuoteMessage() {
        val baseMessage = FloatingChatMessage(
            id = "outgoing",
            type = FloatingChatMessageType.Text,
            text = "这条我引用回复",
            fromMe = true,
            senderName = "当前账号",
            time = "刚刚"
        )
        val quotedSource = FloatingChatMessage(
            id = "source",
            type = FloatingChatMessageType.Text,
            text = "原始消息内容",
            fromMe = false,
            senderName = "何苗",
            time = "14:21",
            remoteMessageServerId = "88990011"
        )

        val quotedMessage = outgoingTextMessageWithOptionalQuote(baseMessage, quotedSource)

        assertEquals(FloatingChatMessageType.Quote, quotedMessage.type)
        assertEquals("这条我引用回复", quotedMessage.text)
        assertEquals("何苗", quotedMessage.quoteAuthor)
        assertEquals("原始消息内容", quotedMessage.quoteText)
        assertEquals("88990011", quotedMessage.remoteMessageServerId)
        assertEquals(baseMessage.connectionTarget, quotedMessage.connectionTarget)
        assertEquals(baseMessage.connectionTargetId, quotedMessage.connectionTargetId)
    }

    @Test
    fun forwardedMessageIsPreparedForRealOutgoingSend() {
        val conversation = FloatingChatConversation(
            peerName = "Friend",
            accountName = "Main",
            contacts = emptyList(),
            accountContacts = listOf(
                FloatingChatContact(
                    id = "account-1",
                    name = "Main",
                    initials = "M",
                    description = "account",
                    avatarColor = 0xFF123456
                )
            ),
            messages = emptyList(),
            toolActions = emptyList()
        )
        val source = FloatingChatMessage(
            id = "source",
            type = FloatingChatMessageType.Text,
            text = "forward me",
            fromMe = false,
            senderName = "Friend",
            time = "14:21"
        )
        var capturedThreadId: String? = null

        val forwarded = preparedForwardedMessageForSend(
            source = source,
            conversation = conversation,
            target = ChatThreadSelection.Private("wxid_friend"),
            accountId = "account-1",
            sequence = 7,
            prepareOutgoingMessage = { message, threadId ->
                capturedThreadId = threadId
                message.copy(
                    sendState = FloatingChatSendState.Queued,
                    clientRequestId = "request-forward"
                )
            }
        )

        assertEquals("private:wxid_friend", capturedThreadId)
        assertEquals("local-forward-wxid_friend-account-1-7", forwarded.id)
        assertEquals(true, forwarded.fromMe)
        assertEquals("account-1", forwarded.connectionTargetId)
        assertEquals("wxid_friend", forwarded.threadContactId)
        assertEquals(FloatingChatSendState.Queued, forwarded.sendState)
        assertEquals("request-forward", forwarded.clientRequestId)
    }

    @Test
    fun floatingChatAppearanceUsesConfigurableFrostedBackground() {
        assertEquals(true, defaultFloatingChatFrostedBackgroundEnabled())
        assertEquals(78, defaultFloatingChatBackgroundOpacityPercent())
        assertEquals(18, defaultFloatingChatBlurRadiusDp())
        assertEquals(0, sanitizeFloatingChatBackgroundOpacityPercent(-1))
        assertEquals(64, sanitizeFloatingChatBackgroundOpacityPercent(64))
        assertEquals(100, sanitizeFloatingChatBackgroundOpacityPercent(140))
        assertEquals(0, sanitizeFloatingChatBlurRadiusDp(-2))
        assertEquals(24, sanitizeFloatingChatBlurRadiusDp(24))
        assertEquals(40, sanitizeFloatingChatBlurRadiusDp(96))
        assertEquals("after_global_controls", floatingChatAppearancePanelPlacement())
        assertEquals(true, floatingChatAppearanceSettingsPreview())
        assertEquals(true, floatingChatOverlayUsesFrostedBackground())
        assertEquals(true, floatingChatOverlaySupportsRuntimeBackgroundOpacity())
        assertEquals(true, floatingChatOverlaySupportsRuntimeBlurRadius())
        assertEquals(true, floatingChatOverlaySupportsRuntimeBackgroundColor())
        assertEquals(0xEAF3F6, defaultFloatingChatBackgroundColorRgb())
        assertEquals(0x000000, sanitizeFloatingChatBackgroundColorRgb(-1))
        assertEquals(0x336B78, sanitizeFloatingChatBackgroundColorRgb(0x336B78))
        assertEquals(0xFFFFFF, sanitizeFloatingChatBackgroundColorRgb(0x1FFFFFF))
        assertEquals(
            listOf(0xEAF3F6, 0xF1E9DC, 0xE8F0E8, 0xECEAF7, 0xF2E8EA),
            floatingChatBackgroundColorPresetRgbs()
        )
        assertEquals(true, floatingChatOverlayFallsBackWhenBackdropBlurUnavailable())
        assertEquals(true, floatingChatOverlayRefreshRecreatesCurrentStateForAppearanceChanges())
        assertEquals(true, isFloatingChatAppearancePreferenceKey("floating_chat_frosted_background_enabled"))
        assertEquals(true, isFloatingChatAppearancePreferenceKey("floating_chat_background_opacity_percent"))
        assertEquals(true, isFloatingChatAppearancePreferenceKey("floating_chat_blur_radius_dp"))
        assertEquals(true, isFloatingChatAppearancePreferenceKey("floating_chat_background_color_rgb"))
        assertEquals(false, isFloatingChatAppearancePreferenceKey("overlay_opacity"))
        assertEquals(false, isFloatingChatAppearancePreferenceKey(null))
        assertEquals(true, floatingChatAppearanceRefreshUsesDebouncedSingleRunnable())
        assertEquals(false, floatingChatCollapseRemovesOverlayInsteadOfShowingButton())
        assertEquals(false, floatingChatCollapsedStateShowsFloatingButton())
    }

    @Test
    fun bottomGestureBarClassifiesEachSupportedGesture() {
        assertEquals(
            BottomGestureBarGestureType.Tap,
            resolveBottomGestureBarGestureType(
                deltaX = 0f,
                deltaY = 0f,
                gestureDurationMillis = 120L,
                upwardStationaryMillis = 0L
            )
        )
        assertEquals(
            BottomGestureBarGestureType.SwipeUp,
            resolveBottomGestureBarGestureType(
                deltaX = 0f,
                deltaY = -120f,
                gestureDurationMillis = 180L,
                upwardStationaryMillis = 0L
            )
        )
        assertEquals(
            BottomGestureBarGestureType.SwipeUp,
            resolveBottomGestureBarGestureType(
                deltaX = 0f,
                deltaY = -120f,
                gestureDurationMillis = 520L,
                upwardStationaryMillis = 499L
            )
        )
        assertEquals(
            BottomGestureBarGestureType.SwipeUpHold,
            resolveBottomGestureBarGestureType(
                deltaX = 0f,
                deltaY = -120f,
                gestureDurationMillis = 680L,
                upwardStationaryMillis = 500L
            )
        )
        assertEquals(
            BottomGestureBarGestureType.SwipeHorizontal,
            resolveBottomGestureBarGestureType(
                deltaX = 120f,
                deltaY = -8f,
                gestureDurationMillis = 180L,
                upwardStationaryMillis = 0L
            )
        )
        assertEquals(
            BottomGestureBarGestureType.LongPress,
            resolveBottomGestureBarGestureType(
                deltaX = 0f,
                deltaY = 0f,
                gestureDurationMillis = 600L,
                upwardStationaryMillis = 0L
            )
        )
    }

    @Test
    fun bottomGestureBarUsesExistingDefaultsAndAllowsWidthConfiguration() {
        assertEquals(156, defaultBottomGestureBarWidthDp())
        assertEquals(96, sanitizeBottomGestureBarWidthDp(20))
        assertEquals(188, sanitizeBottomGestureBarWidthDp(188))
        assertEquals(260, sanitizeBottomGestureBarWidthDp(600))
        assertEquals(GestureAction.Back, defaultBottomGestureBarAction(BottomGestureBarGestureType.Tap))
        assertEquals(GestureAction.Home, defaultBottomGestureBarAction(BottomGestureBarGestureType.SwipeUp))
        assertEquals(GestureAction.Recents, defaultBottomGestureBarAction(BottomGestureBarGestureType.SwipeUpHold))
        assertEquals(GestureAction.Back, defaultBottomGestureBarAction(BottomGestureBarGestureType.SwipeHorizontal))
        assertEquals(GestureAction.Back, defaultBottomGestureBarAction(BottomGestureBarGestureType.LongPress))
        assertEquals(true, bottomGestureBarDispatchesGestureActionAfterTouchEvent())
    }

    @Test
    fun expandedFloatingChatRemovesEdgeGestureWindowsButKeepsBottomBar() {
        assertEquals(false, edgeGestureOverlayWindowsAllowed(floatingChatExpanded = true))
        assertEquals(true, edgeGestureOverlayWindowsAllowed(floatingChatExpanded = false))
        assertEquals(true, bottomGestureBarVisibleForFloatingChat(floatingChatExpanded = true))
        assertEquals(true, bottomGestureBarVisibleForFloatingChat(floatingChatExpanded = false))
        assertEquals(true, bottomGestureBarRecreatesAfterFloatingChatWindowUpdate())
    }

    @Test
    fun bottomGestureBarKeepsVisibleOverlayAndNativeTouchRegion() {
        val config = NativeEdgeGestureConfig(
            screenWidthPx = 1080,
            screenHeightPx = 2400,
            density = 3f,
            leftConfigs = emptyList(),
            rightConfigs = emptyList(),
            shortThresholdPx = 24f,
            longThresholdPx = 72f,
            bottomGestureWidthDp = 156
        )

        assertEquals(true, bottomGestureBarExternalOverlayVisibleForFloatingChat(floatingChatExpanded = false))
        assertEquals(true, bottomGestureBarUsesNativeTouchInteractionSurface())
        assertEquals(
            NativeBottomGestureInterceptRect(
                left = 306,
                top = 2310,
                right = 774,
                bottom = 2400
            ),
            nativeBottomGestureInterceptRect(config, floatingChatExpanded = false)
        )
        assertEquals(true, nativeBottomGestureHitTest(540f, 2380f, config, floatingChatExpanded = false))
        assertEquals(false, nativeBottomGestureHitTest(120f, 2380f, config, floatingChatExpanded = false))
    }

    @Test
    fun scrmConversationPaginationStopsAtKnownTotalOrShortUnknownPage() {
        assertEquals(
            true,
            shouldRequestNextScrmConversationPage(
                returnedItemCount = 200,
                loadedItemCount = 200,
                totalCount = 401,
                pageSize = 200
            )
        )
        assertEquals(
            false,
            shouldRequestNextScrmConversationPage(
                returnedItemCount = 1,
                loadedItemCount = 401,
                totalCount = 401,
                pageSize = 200
            )
        )
        assertEquals(
            false,
            shouldRequestNextScrmConversationPage(
                returnedItemCount = 37,
                loadedItemCount = 37,
                totalCount = 0,
                pageSize = 200
            )
        )
    }

    @Test
    fun bottomGestureBarMapsTapSwipeAndUpwardPauseToSystemActions() {
        assertEquals(
            BottomGestureBarAction.Back,
            resolveBottomGestureBarAction(
                deltaX = 0f,
                deltaY = 0f,
                upwardStationaryMillis = 0L
            )
        )
        assertEquals(
            BottomGestureBarAction.Home,
            resolveBottomGestureBarAction(
                deltaX = 0f,
                deltaY = -120f,
                upwardStationaryMillis = 0L
            )
        )
        assertEquals(
            BottomGestureBarAction.Recents,
            resolveBottomGestureBarAction(
                deltaX = 0f,
                deltaY = -120f,
                upwardStationaryMillis = 500L
            )
        )
        assertEquals(
            BottomGestureBarAction.Back,
            resolveBottomGestureBarAction(
                deltaX = 100f,
                deltaY = -8f,
                upwardStationaryMillis = 0L
            )
        )
        assertEquals(0, bottomGestureBarBottomInsetDp())
    }
}
