import UIKit
@preconcurrency import AVFoundation
import AVKit
import BlinkVoiceKit
import CoreImage
import CoreLocation
import CoreMotion
import CryptoKit
import EventKit
import ImageIO
import MapKit
import MobileCoreServices
import Photos
import PhotosUI
import QuickLook
import Security
import UniformTypeIdentifiers
import Vision


extension ChatWindowViewController: UIGestureRecognizerDelegate {
    func gestureRecognizerShouldBegin(_ gestureRecognizer: UIGestureRecognizer) -> Bool {
        if gestureRecognizer === conversationProfileAreaPanGesture,
           let panGesture = gestureRecognizer as? UIPanGestureRecognizer {
            guard state.selectedFriendID != nil else { return false }
            let velocity = panGesture.velocity(in: view)
            return velocity.x < -260 && abs(velocity.x) > abs(velocity.y) * 1.45
        }
        return true
    }

    func gestureRecognizer(
        _ gestureRecognizer: UIGestureRecognizer,
        shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer
    ) -> Bool {
        if gestureRecognizer === conversationProfileAreaPanGesture
            || otherGestureRecognizer === conversationProfileAreaPanGesture {
            return otherGestureRecognizer.view === messageCollectionView
                || gestureRecognizer.view === messageCollectionView
        }
        if gestureRecognizer === rightEdgeDouyinPanGesture
            || otherGestureRecognizer === rightEdgeDouyinPanGesture {
            return false
        }
        if gestureRecognizer === leftEdgePanGesture || otherGestureRecognizer === leftEdgePanGesture {
            return gestureRecognizer.view === leftCollectionView
                && otherGestureRecognizer.view === leftCollectionView
        }
        if gestureRecognizer === leftScrollPreviewPanGesture
            || otherGestureRecognizer === leftScrollPreviewPanGesture
            || gestureRecognizer === leftCollectionView.panGestureRecognizer
            || otherGestureRecognizer === leftCollectionView.panGestureRecognizer {
            return gestureRecognizer.view === leftCollectionView
                && otherGestureRecognizer.view === leftCollectionView
        }
        return gestureRecognizer.view === messageCollectionView || otherGestureRecognizer.view === messageCollectionView
    }

    func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldReceive touch: UITouch) -> Bool {
        if chromeView.isHidden {
            return false
        }

        if gestureRecognizer === rightEdgeDouyinPanGesture {
            let point = touch.location(in: rightRailContainer)
            return rightRailContainer.bounds.insetBy(dx: 0, dy: -8).contains(point)
                && point.x >= rightRailContainer.bounds.midX
        }

        if gestureRecognizer === leftEdgePanGesture {
            let point = touch.location(in: leftCollectionView)
            return leftCollectionView.bounds.insetBy(dx: 0, dy: -8).contains(point)
                && point.x <= leftCollectionView.bounds.midX
        }

        if gestureRecognizer === leftScrollPreviewPanGesture {
            let point = touch.location(in: leftCollectionView)
            return leftCollectionView.bounds.insetBy(dx: 0, dy: -8).contains(point)
        }

        if gestureRecognizer === conversationProfileAreaPanGesture {
            guard state.selectedFriendID != nil else { return false }
            let point = touch.location(in: messageCollectionView)
            return messageCollectionView.bounds.insetBy(dx: 0, dy: -12).contains(point)
        }

        if gestureRecognizer.view === selectionAssistOverlayView,
           let touchedView = touch.view,
           touchedView.firstSuperview(of: SelectionAssistJoystickView.self) != nil {
            return false
        }

        if gestureRecognizer === messageDragLongPressGesture {
            let point = touch.location(in: messageCollectionView)
            guard let indexPath = messageCollectionView.indexPathForItem(at: point),
                  let message = renderedMessage(at: indexPath)
            else { return false }
            return canShowContextMenu(for: message)
        }

        if gestureRecognizer.view === leftCollectionView {
            let point = touch.location(in: leftCollectionView)
            return leftCollectionView.indexPathForItem(at: point) != nil
        }

        if let touchedView = touch.view, touchedView.isDescendant(of: inputBar) {
            return false
        }
        if let touchedView = touch.view, touchedView.isDescendant(of: emojiPanelView) {
            return false
        }
        if let touchedView = touch.view, touchedView.isDescendant(of: mentionPanelView) {
            return false
        }

        return true
    }
}

extension ChatWindowViewController: UITextFieldDelegate {
    func textFieldShouldBeginEditing(_ textField: UITextField) -> Bool {
        setEmojiPanelVisible(false, animated: true)
        if textField === inputBar.textField {
            inputLongEyeRewriteTrigger.start()
        }
        return true
    }

    func textFieldDidEndEditing(_ textField: UITextField) {
        setMentionPanelVisible(false, animated: true)
        if textField === inputBar.textField {
            inputLongEyeRewriteTrigger.stop()
        }
    }

    func textFieldShouldReturn(_ textField: UITextField) -> Bool {
        sendMessage()
        return true
    }
}


extension ChatWindowViewController {
    func loadPersistedMessagesOrSeedStore() {
        let signpostID = PerformanceSignpost.begin("StartupMessageHydration")
        defer { PerformanceSignpost.end("StartupMessageHydration", id: signpostID) }
        let persistedMessages = conversationStateStore.loadMessages(participants: state.participants)
        if !persistedMessages.isEmpty {
            state.messages = persistedMessages
            removeLegacyOpenApiStatusMessagesFromState()
            hydrateMissingParticipantsFromPersistedMessages(state.messages)
            normalizePersistedInFlightMessages()
            messageMutationPlanner.reset(to: state.messages)
            isMessagePersistenceReady = true
            ChatSQLiteStore.shared.setString(Self.messageSeedRevision, forKey: Self.messageSeedRevisionKey)
            return
        }

        if ChatSQLiteStore.shared.string(forKey: Self.messageSeedRevisionKey) != Self.messageSeedRevision {
            normalizePersistedInFlightMessages()
            isMessagePersistenceReady = true
            ChatSQLiteStore.shared.setString(Self.messageSeedRevision, forKey: Self.messageSeedRevisionKey)
            persistInitialMessageState()
            DispatchQueue.main.async { [weak self] in
                self?.leftCollectionView.reloadData()
                self?.messageCollectionView.reloadData()
                self?.rightToolCollectionView.reloadData()
                self?.updateConnections()
            }
            return
        }

        if persistedMessages.isEmpty {
            normalizePersistedInFlightMessages()
            isMessagePersistenceReady = true
            persistInitialMessageState()
        }
        if autoReplyConfiguration.isEnabled {
            markExistingIncomingMessagesAsHandledForAutoReply()
        }
    }

    func removeLegacyOpenApiStatusMessagesFromState() {
        let legacyIDs = Set(state.messages.filter { $0.detail.contains("OpenAPI发送状态") }.map(\.id))
        guard !legacyIDs.isEmpty else { return }
        state.messages.removeAll { legacyIDs.contains($0.id) }
        ChatSQLiteStore.shared.deleteMessages(ids: legacyIDs)
    }

    func hydrateMissingParticipantsFromPersistedMessages(_ messages: [ChatMessage]) {
        var knownIDs = Set(state.participants.map(\.id))
        for message in messages where knownIDs.insert(message.sender.id).inserted {
            state.participants.append(message.sender)
        }
        for message in messages where !knownIDs.contains(message.conversationID) {
            let fallback = message.sender.id == message.conversationID
                ? message.sender
                : ChatParticipant(
                    id: message.conversationID,
                    displayName: message.isGroupConversation ? "历史群聊" : "历史会话",
                    tintColor: message.sender.tintColor,
                    initials: message.isGroupConversation ? "群" : "会",
                    isCurrentUser: false,
                    avatarURL: nil,
                    kind: message.isGroupConversation ? .group : .contact
                )
            guard knownIDs.insert(fallback.id).inserted else { continue }
            state.participants.append(fallback)
        }
    }

    func loadPersistedOpenApiDeliveryStatuses() {
        let messageIDs = Set(state.messages.map(\.id))
        openApiDeliveryStatusesByMessageID = conversationStateStore.loadOpenApiDeliveryStatuses(messageIDs: messageIDs)
    }

    func loadPersistedOpenApiIMSnapshotIfAvailable() {
        let signpostID = PerformanceSignpost.begin("StartupOpenAPIHydration")
        defer { PerformanceSignpost.end("StartupOpenAPIHydration", id: signpostID) }
        guard let persisted = conversationStateStore.loadOpenApiIMSnapshot(
            snapshotKey: Self.openApiIMSnapshotStorageKey,
            updatedAtKey: Self.openApiIMSnapshotUpdatedAtStorageKey
        ) else {
            return
        }
        guard ChatSQLiteStore.shared.string(forKey: Self.openApiIMSnapshotSchemaVersionStorageKey)
                == Self.openApiIMSnapshotSchemaVersion else {
            openApiIMSnapshotCacheDate = nil
            ChatSQLiteStore.shared.setString(nil, forKey: Self.openApiIMSnapshotUpdatedAtStorageKey)
            return
        }
        let snapshot = persisted.snapshot
        let containsLegacyScopedConversationIDs = (snapshot.contacts + snapshot.chatrooms).contains { context in
            let namespace = context.kind == .chatroom ? "openapi-chatroom" : "openapi-contact"
            return context.participantID != OpenApiStableID.uuid(namespace: namespace, key: context.wxid)
        }
        if containsLegacyScopedConversationIDs {
            openApiIMSnapshotCacheDate = nil
            ChatSQLiteStore.shared.setString(nil, forKey: Self.openApiIMSnapshotUpdatedAtStorageKey)
            return
        }
        let validChatrooms = snapshot.chatrooms.filter { OpenApiDisplay.validChatRoomID($0.wxid) != nil }
        let containsInvalidChatrooms = validChatrooms.count != snapshot.chatrooms.count
        let runtimeSnapshot = containsInvalidChatrooms
            ? OpenApiIMSnapshot(
                accounts: snapshot.accounts,
                contacts: snapshot.contacts,
                chatrooms: validChatrooms,
                friendRequests: snapshot.friendRequests,
                taskResults: snapshot.taskResults
            )
            : snapshot
        openApiIMSnapshotCacheDate = persisted.cacheDate
        if containsInvalidChatrooms {
            openApiIMSnapshotCacheDate = nil
            ChatSQLiteStore.shared.setString(nil, forKey: Self.openApiIMSnapshotUpdatedAtStorageKey)
        }
        let didApplySnapshot = applyOpenApiIMSnapshot(runtimeSnapshot, shouldPersistSnapshot: false)
        hasLoadedOpenApiIMSnapshot = didApplySnapshot
        openApiIMBootstrapState = didApplySnapshot ? .loaded : .empty
    }

    func persistOpenApiIMSnapshot(_ snapshot: OpenApiIMSnapshot) {
        openApiIMSnapshotCacheDate = conversationStateStore.persistOpenApiIMSnapshot(
            snapshot,
            snapshotKey: Self.openApiIMSnapshotStorageKey,
            updatedAtKey: Self.openApiIMSnapshotUpdatedAtStorageKey
        )
        ChatSQLiteStore.shared.setString(
            Self.openApiIMSnapshotSchemaVersion,
            forKey: Self.openApiIMSnapshotSchemaVersionStorageKey
        )
    }

    func clearPersistedOpenApiIMSnapshot() {
        openApiIMSnapshotCacheDate = nil
        conversationStateStore.clearOpenApiIMSnapshot(
            snapshotKey: Self.openApiIMSnapshotStorageKey,
            updatedAtKey: Self.openApiIMSnapshotUpdatedAtStorageKey
        )
        ChatSQLiteStore.shared.setString(nil, forKey: Self.openApiIMSnapshotSchemaVersionStorageKey)
    }

    func removeOpenApiConversationFromPersistedSnapshot(participantID: UUID) {
        openApiConversationContextsByID.removeValue(forKey: participantID)
        openApiFriendRequestContexts.removeAll { $0.participantID == participantID }
        guard var snapshot = ChatSQLiteStore.shared.codable(OpenApiIMSnapshot.self, forKey: Self.openApiIMSnapshotStorageKey) else {
            return
        }
        snapshot.contacts.removeAll { $0.participantID == participantID }
        snapshot.chatrooms.removeAll { $0.participantID == participantID }
        snapshot.friendRequests.removeAll { $0.participantID == participantID }
        persistOpenApiIMSnapshot(snapshot)
    }

    var shouldRefreshOpenApiIMSnapshot: Bool {
        guard hasLoadedOpenApiIMSnapshot else { return true }
        guard let openApiIMSnapshotCacheDate else { return true }
        return Date().timeIntervalSince(openApiIMSnapshotCacheDate) > Self.openApiIMSnapshotRefreshInterval
    }

    func persistMessages() {
        guard isMessagePersistenceReady else { return }
        let mutations = messageMutationPlanner.plan(for: state.messages)
        applyMessageMutationsToPersistence(mutations)
    }

    func persistMessageMutations(
        _ mutations: [MessageMutation],
        invalidatesVisibleDataCaches: Bool = true
    ) {
        guard isMessagePersistenceReady, !mutations.isEmpty else { return }
        messageMutationPlanner.applyKnownMutations(mutations)
        applyMessageMutationsToPersistence(
            mutations,
            invalidatesVisibleDataCaches: invalidatesVisibleDataCaches
        )
    }

    private func applyMessageMutationsToPersistence(
        _ mutations: [MessageMutation],
        invalidatesVisibleDataCaches: Bool = true
    ) {
        guard !mutations.isEmpty else { return }
        applyPendingReplyIndexMutations(mutations)
        if invalidatesVisibleDataCaches {
            invalidateVisibleDataCaches(invalidatePendingReplyIndex: false)
        }
        conversationStateStore.applyMessageMutations(mutations)
    }

    func applyPendingReplyIndexMutations(_ mutations: [MessageMutation]) {
        guard pendingReplyIndex.isInitialized, !mutations.isEmpty else { return }
        var index = pendingReplyIndex
        index.apply(
            mutations,
            keyForMessage: { [unowned self] message in
                self.unansweredRecipientAccountID(for: message).map { accountID in
                    PendingReplyConversationAccountKey(
                    conversationID: message.conversationID,
                        accountID: accountID
                    )
                }
            },
            isRelevant: { [unowned self] in self.isHomeReplyRelevantMessage($0) },
            isIncoming: { [unowned self] in self.isIncomingHomeReplyMessage($0) }
        )
        pendingReplyIndex = index
    }

    func persistInitialMessageState(invalidatePendingReplyIndex: Bool = true) {
        guard isMessagePersistenceReady else { return }
        invalidateVisibleDataCaches(
            invalidatePendingReplyIndex: invalidatePendingReplyIndex
        )
        conversationStateStore.saveState(state)
        messageMutationPlanner.reset(to: state.messages)
    }

    func commitSuccessfulGroupRename(groupID: UUID, newName: String) {
        let normalizedName = newName.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !normalizedName.isEmpty else { return }

        if let index = state.participants.firstIndex(where: { $0.id == groupID }) {
            let old = state.participants[index]
            state.participants[index] = ChatParticipant(
                id: old.id,
                displayName: normalizedName,
                tintColor: old.tintColor,
                initials: String(normalizedName.prefix(1)),
                isCurrentUser: old.isCurrentUser,
                avatarURL: old.avatarURL,
                kind: old.kind
            )
        }

        if let context = openApiConversationContextsByID[groupID] {
            openApiConversationContextsByID[groupID] = context.replacingDisplayName(normalizedName)
        }

        if let oldCard = state.contactCards[groupID] {
            state.contactCards[groupID] = ContactCardProfile(
                accountID: oldCard.accountID,
                displayName: normalizedName,
                role: oldCard.role,
                company: oldCard.company,
                wechatID: oldCard.wechatID,
                phone: oldCard.phone,
                bio: oldCard.bio,
                styleIndex: oldCard.styleIndex
            )
        }

        if var snapshot = ChatSQLiteStore.shared.codable(
            OpenApiIMSnapshot.self,
            forKey: Self.openApiIMSnapshotStorageKey
        ) {
            snapshot.chatrooms = snapshot.chatrooms.map {
                $0.participantID == groupID ? $0.replacingDisplayName(normalizedName) : $0
            }
            persistOpenApiIMSnapshot(snapshot)
        }

        persistMessages()
    }

    func syncOpenApiIMDataIfNeeded() {
        syncOpenApiIMData(force: shouldRefreshOpenApiIMSnapshot, showsNotice: false)
    }

    func syncOpenApiIMData(
        force: Bool,
        showsNotice: Bool = true,
        completion: ((Bool) -> Void)? = nil
    ) {
        guard !isOpenApiIMSyncing else {
            completion?(false)
            return
        }
        guard force || !hasLoadedOpenApiIMSnapshot || shouldRefreshOpenApiIMSnapshot else {
            completion?(true)
            return
        }
        isOpenApiIMSyncing = true
        if !hasLoadedOpenApiIMSnapshot {
            openApiIMBootstrapState = .loading
            updateHeaderForSelection()
            leftCollectionView.reloadData()
            rightAccountCollectionView.reloadData()
            messageCollectionView.reloadData()
        }
        if showsNotice {
            showNotice("正在同步真实帐号和好友")
        }

        Task { @MainActor in
            do {
                let snapshot = try await OpenApiIMSynchronizer.loadSnapshot()
                let didApplySnapshot = applyOpenApiIMSnapshot(snapshot)
                if !didApplySnapshot {
                    clearPersistedOpenApiIMSnapshot()
                }
                hasLoadedOpenApiIMSnapshot = true
                isOpenApiIMSyncing = false
                openApiIMBootstrapState = didApplySnapshot ? .loaded : .empty
                updateHeaderForSelection()
                rightAccountCollectionView.reloadData()
                if showsNotice {
                    showNotice("已同步 \(snapshot.accounts.count) 个帐号、\(snapshot.contacts.count) 个好友、\(snapshot.chatrooms.count) 个群聊、\(snapshot.friendRequests.count) 条好友申请")
                }
                completion?(true)
            } catch {
                isOpenApiIMSyncing = false
                if !hasLoadedOpenApiIMSnapshot {
                    openApiIMBootstrapState = .failed(error.localizedDescription)
                    updateHeaderForSelection()
                    leftCollectionView.reloadData()
                    rightAccountCollectionView.reloadData()
                    messageCollectionView.reloadData()
                }
                if showsNotice {
                    showNotice("OpenAPI 同步失败：\(error.localizedDescription)")
                }
                completion?(false)
            }
        }
    }

    func syncOpenApiChatroomsFromTool() {
        if isOpenApiIMSyncing {
            showNotice("正在同步，请稍后")
            return
        }

        guard !openApiAccountContextsByID.isEmpty else {
            showNotice("正在读取微信帐号")
            syncOpenApiIMData(force: true) { [weak self] success in
                guard success else { return }
                self?.syncOpenApiChatroomsFromTool()
            }
            return
        }

        let account = openApiAccountContextsByID[state.selectedAccountID]
            ?? openApiAccountContextsByID.values.sorted {
                if $0.isOnline != $1.isOnline { return $0.isOnline && !$1.isOnline }
                return $0.nickname.localizedStandardCompare($1.nickname) == .orderedAscending
            }.first
        guard let account else {
            showNotice("没有可同步的微信帐号")
            return
        }

        let previousChatroomKeys = Set(openApiConversationContextsByID.values
            .filter { $0.kind == .chatroom }
            .map { "\($0.ownerWxid)|\($0.wxid)" })

        isOpenApiIMSyncing = true
        rightToolCollectionView.reloadData()
        showNotice("正在同步「\(account.nickname)」的群聊")

        Task { @MainActor in
            do {
                var body: [String: Any] = [
                    "weChatId": account.wxid,
                    "flag": 1
                ]
                if !account.clientUuid.isEmpty {
                    body["deviceUuid"] = account.clientUuid
                }

                let syncResult = try await OpenApiHTTPClient.request(
                    "POST",
                    path: "/openapi/v1/chatrooms/sync",
                    body: body
                )
                let taskNote = await waitForOpenApiChatroomSyncTaskIfPossible(syncResult)
                try? await Task.sleep(nanoseconds: 600_000_000)

                let snapshot = try await OpenApiIMSynchronizer.loadSnapshot(
                    contactLimitPerAccount: 1500,
                    groupLimitPerAccount: 500
                )
                let didApplySnapshot = applyOpenApiIMSnapshot(snapshot)
                if !didApplySnapshot {
                    clearPersistedOpenApiIMSnapshot()
                }
                hasLoadedOpenApiIMSnapshot = true
                openApiIMBootstrapState = didApplySnapshot ? .loaded : .empty
                isOpenApiIMSyncing = false
                rightToolCollectionView.reloadData()

                if leftSidebarDisplayMode == .friends {
                    setLeftSidebarDisplayMode(.friendsAndGroups)
                }

                let currentChatroomKeys = Set(snapshot.chatrooms.map { "\($0.ownerWxid)|\($0.wxid)" })
                let addedCount = max(0, currentChatroomKeys.subtracting(previousChatroomKeys).count)
                let selectedAccountGroupCount = snapshot.chatrooms.filter { $0.ownerWxid == account.wxid }.count
                let addedText = addedCount > 0 ? "，新增 \(addedCount) 个" : ""
                let taskText = taskNote.isEmpty ? "" : "，\(taskNote)"
                showNotice("群聊已刷新：当前帐号 \(selectedAccountGroupCount) 个\(addedText)\(taskText)")
            } catch {
                isOpenApiIMSyncing = false
                rightToolCollectionView.reloadData()
                showNotice("群聊同步失败：\(error.localizedDescription)")
            }
        }
    }

    func waitForOpenApiChatroomSyncTaskIfPossible(_ result: OpenApiHTTPResult) async -> String {
        guard let taskID = openApiTaskID(from: result.jsonObject) else {
            let payload = openApiTaskPayload(from: result.jsonObject)
            let message = openApiFirstString(in: payload, keys: ["message", "error", "errorMessage"])
            return message.isEmpty ? "已直接刷新列表" : message
        }

        var latestPendingText = ""
        for attempt in 0..<8 {
            if attempt > 0 {
                try? await Task.sleep(nanoseconds: UInt64(700_000_000 + attempt * 250_000_000))
            }
            do {
                let taskResult = try await OpenApiHTTPClient.request("GET", path: "/openapi/v1/tasks/\(taskID)")
                switch openApiTaskPollState(from: taskResult.jsonObject) {
                case .succeeded(let message):
                    return message.isEmpty ? "同步任务已完成" : "同步任务已完成：\(message)"
                case .failed(let message):
                    return message.isEmpty ? "同步任务失败" : "同步任务失败：\(message)"
                case .pending(let message):
                    latestPendingText = message
                }
            } catch {
                latestPendingText = error.localizedDescription
            }
        }
        return latestPendingText.isEmpty ? "同步任务仍在处理" : "同步任务处理中：\(latestPendingText)"
    }

    @discardableResult
    func applyOpenApiIMSnapshot(_ snapshot: OpenApiIMSnapshot, shouldPersistSnapshot: Bool = true) -> Bool {
        cancelOutstandingCollectionPrefetches()
        guard !snapshot.accounts.isEmpty else {
            openApiAccountContextsByID = [:]
            openApiConversationContextsByID = [:]
            openApiConversationContextsByAccountID = [:]
            openApiRelatedAccountIDsByConversationID = [:]
            openApiFriendRequestContexts = []
            lastAppliedOpenApiIMSnapshot = nil
            isMessagePersistenceReady = true
            updateLeftFriendTotalBadge()
            cancelLeftScrollPreview()
            leftCollectionView.reloadData()
            rightAccountCollectionView.reloadData()
            rightToolCollectionView.reloadData()
            messageCollectionView.reloadData()
            updateConnections()
            return false
        }

        if lastAppliedOpenApiIMSnapshot == snapshot {
            if shouldPersistSnapshot {
                persistOpenApiIMSnapshot(snapshot)
            }
            scheduleOpenApiChatMessageSyncWhenIdle()
            return true
        }

        if isHomeTimeline, isUnreadFoldExpansionInProgress {
            deferredOpenApiIMSnapshotAfterUnreadExpansion = snapshot
            shouldPersistDeferredOpenApiIMSnapshot = shouldPersistSnapshot
            return true
        }

        let preservesUnreadTimelineViewport = isHomeTimeline
        let preservedUnreadOffsetY = messageCollectionView.contentOffset.y
        let preservedUnreadAnchor = preservesUnreadTimelineViewport
            ? (unreadFoldPostExpansionAnchor ?? unreadTimelineLayoutSnapshot().flatMap {
                UnreadTimelineGroupViewportAnchor.captureVisible(
                    snapshot: $0,
                    contentOffsetY: preservedUnreadOffsetY
                )
            })
            : nil
        let preservedHomeRenderedMessageLimit = homeRenderedMessageLimit
        let preservedDirectVisibleLimits = unreadDirectVisibleLimitByConversationID
        let preservedGroupVisibleLimits = unreadGroupVisibleLimitByConversationID
        let preservedExpandedConversationIDs = expandedUnreadConversationIDs
        let preservedExpandedDirectKeys = expandedUnreadDirectKeys

        // Group member hydration is independent from the account/contact
        // snapshot. Keep the resolved member objects (including members that
        // share a contact participant ID) while the snapshot rebuilds the
        // directory; otherwise a late snapshot replaces real names/avatars
        // with the message/bootstrap placeholders.
        let explicitGroupMemberParticipantIDs = Set(
            explicitGroupMemberIDsByGroupID.values.flatMap { $0 }
        )
        var preservedGroupMemberParticipants: [ChatParticipant] = []
        var preservedGroupMemberParticipantIDs = Set<UUID>()
        func preserveHydratedGroupMember(_ participant: ChatParticipant) {
            guard !participant.isCurrentUser,
                  participant.kind != .group,
                  (participant.kind == .groupMember
                    || explicitGroupMemberParticipantIDs.contains(participant.id)),
                  preservedGroupMemberParticipantIDs.insert(participant.id).inserted
            else { return }
            preservedGroupMemberParticipants.append(participant)
        }
        state.participants.forEach(preserveHydratedGroupMember)
        for message in state.messages where message.isGroupConversation && !message.isOutgoing {
            preserveHydratedGroupMember(message.sender)
        }

        let oldSelectedAccountID = state.selectedAccountID
        let orderedAccountContexts = snapshot.accounts.sorted { $0.participantID.uuidString < $1.participantID.uuidString }
        let orderedConversationContexts = snapshot.conversations.sorted { $0.participantID.uuidString < $1.participantID.uuidString }
        let aiParticipants = state.participants.filter(\.isAIAccount)
        let accountParticipants = orderedAccountContexts.map { context in
            ChatParticipant(
                id: context.participantID,
                displayName: context.nickname,
                tintColor: context.tintColor,
                initials: context.initials,
                isCurrentUser: true,
                avatarURL: context.avatar,
                kind: .account
            )
        }
        let conversationParticipants = orderedConversationContexts.map { context in
            ChatParticipant(
                id: context.participantID,
                displayName: context.displayName,
                tintColor: context.tintColor,
                initials: context.initials,
                isCurrentUser: false,
                avatarURL: context.avatar,
                kind: context.kind == .chatroom ? .group : .contact
            )
        }
        let accountByWxid = Dictionary(snapshot.accounts.map { ($0.wxid, $0) }, uniquingKeysWith: { current, _ in current })
        openApiAccountContextsByID = Dictionary(snapshot.accounts.map { ($0.participantID, $0) }, uniquingKeysWith: { current, _ in current })
        openApiConversationContextsByID = Dictionary(snapshot.conversations.map { ($0.participantID, $0) }, uniquingKeysWith: { current, _ in current })
        var contextsByAccountID: [UUID: [UUID: OpenApiConversationContext]] = [:]
        var relatedAccountIDs: [UUID: Set<UUID>] = [:]
        for context in snapshot.contacts + snapshot.chatrooms {
            guard let account = accountByWxid[context.ownerWxid] else { continue }
            contextsByAccountID[context.participantID, default: [:]][account.participantID] = context
            relatedAccountIDs[context.participantID, default: []].insert(account.participantID)
        }
        openApiConversationContextsByAccountID = contextsByAccountID
        openApiRelatedAccountIDsByConversationID = relatedAccountIDs
        openApiFriendRequestContexts = snapshot.friendRequests
        pendingReplyIndexBuildGeneration &+= 1
        invalidateVisibleDataCaches(invalidatePendingReplyIndex: false)
        var cards = state.contactCards
        for context in orderedAccountContexts {
            cards[context.participantID] = ContactCardProfile(
                accountID: context.participantID,
                displayName: context.nickname,
                role: context.isOnline ? "OpenAPI 在线帐号" : "OpenAPI 微信帐号",
                company: "只发 SCRM",
                wechatID: context.wxid,
                phone: "",
                bio: "来自 OpenAPI /wechat-accounts 的真实微信帐号。",
                styleIndex: safeHashIndex(context.wxid, modulo: 4)
            )
        }
        for context in orderedConversationContexts {
            let sourceLines = [
                context.friendNo.isEmpty ? "" : "好友号：\(context.friendNo)",
                context.remark.isEmpty ? "" : "备注：\(context.remark)",
                context.sourceChannel.isEmpty ? "" : "来源渠道：\(context.sourceChannel)",
                context.customerLevel.isEmpty ? "" : "客户等级：\(context.customerLevel)",
                context.profileKey.isEmpty ? "" : "画像键：\(context.profileKey)",
                context.notes.isEmpty ? "" : "备注说明：\(context.notes)",
                "wxid：\(context.wxid)",
                "所属帐号：\(accountByWxid[context.ownerWxid]?.nickname ?? context.ownerWxid)"
            ].filter { !$0.isEmpty }
            cards[context.participantID] = ContactCardProfile(
                accountID: context.participantID,
                displayName: context.displayName,
                role: context.kind == .chatroom ? "OpenAPI 群聊" : "OpenAPI 好友",
                company: "只发 SCRM",
                wechatID: context.friendNo,
                phone: context.phone,
                bio: sourceLines.joined(separator: "\n"),
                styleIndex: safeHashIndex("\(context.ownerWxid)-\(context.wxid)", modulo: 4)
            )
        }
        state.contactCards = cards
        var rebuiltParticipants = accountParticipants + aiParticipants
        rebuiltParticipants.append(contentsOf: conversationParticipants.filter {
            !preservedGroupMemberParticipantIDs.contains($0.id)
        })
        rebuiltParticipants.append(contentsOf: preservedGroupMemberParticipants)
        state.participants = rebuiltParticipants
        // 启动阶段已经完成消息水合时，应用帐号快照不再重复全表读取和 JSON 解码。
        let shouldHydratePersistedMessages = !isMessagePersistenceReady
        if shouldHydratePersistedMessages {
            state.messages = AppKits.registry.dataLayer.loadMessages(participants: state.participants)
        }
        normalizePersistedOpenApiGroupMessages()
        state.messages.removeAll { message in
            message.detail.contains("OpenAPI同步预览")
                || message.detail.contains("OpenAPI好友申请")
                || message.detail.contains("OpenAPI任务回执")
                || message.detail.contains("OpenAPI发送状态")
        }
        isMessagePersistenceReady = true
        if openApiAccountContextsByID[oldSelectedAccountID] != nil {
            state.selectedAccountID = oldSelectedAccountID
        } else {
            state.selectedAccountID = orderedAccountContexts.first?.participantID ?? state.selectedAccountID
        }
        if let unansweredAccountFilterID,
           openApiAccountContextsByID[unansweredAccountFilterID] == nil {
            setUnansweredTimelineRoute(scope: .all, focus: nil)
        }
        if let selectedFriendID = state.selectedFriendID,
           !state.participants.contains(where: { $0.id == selectedFriendID }) {
            state.selectedFriendID = nil
            setUnansweredTimelineRoute(scope: .all, focus: nil)
        }
        rebuildOpenApiGroupMembership(snapshot, accountByWxid: accountByWxid)
        appendOpenApiFriendRequestMessages(for: snapshot, accountByWxid: accountByWxid)
        applyOpenApiTaskResults(snapshot.taskResults)
        removeUnreadTimelineTestDataIfPresent()
        // Do not reset these markers when applying a directory snapshot. A
        // selected group's member request may already have completed (or be
        // in flight); resetting the markers starts a second request and lets
        // its incomplete sender data overwrite the hydrated presentation.
        hydrateOpenApiGroupMessageSenders()

        clearMessageHeightCache()
        resetHomeMessageWindow()
        if preservesUnreadTimelineViewport {
            homeRenderedMessageLimit = preservedHomeRenderedMessageLimit
            unreadDirectVisibleLimitByConversationID = preservedDirectVisibleLimits
            unreadGroupVisibleLimitByConversationID = preservedGroupVisibleLimits
            expandedUnreadConversationIDs = preservedExpandedConversationIDs
            expandedUnreadDirectKeys = preservedExpandedDirectKeys
            unreadFoldPostExpansionAnchor = preservedUnreadAnchor
        }
        // Snapshot application changes participants, relationships, and messages
        // together. Persist one atomic cache view so normalized relationship
        // tables cannot lag behind the message rows.
        persistInitialMessageState(invalidatePendingReplyIndex: false)
        if shouldPersistSnapshot {
            persistOpenApiIMSnapshot(snapshot)
        }
        lastAppliedOpenApiIMSnapshot = snapshot
        schedulePendingReplyIndexRebuild()
        updateHeaderForSelection()
        cancelLeftScrollPreview()
        leftCollectionView.reloadData()
        updateLeftCollectionBounceInsets()
        rightAccountCollectionView.reloadData()
        rightToolCollectionView.reloadData()
        messageCollectionView.reloadData()
        DispatchQueue.main.async { [weak self] in
            guard let self else { return }
            self.prefetchCurrentSidebarAvatarWindow()
            self.updateLeftCollectionBounceInsets()
            if preservesUnreadTimelineViewport {
                self.messageCollectionView.layoutIfNeeded()
                self.leftCollectionView.layoutIfNeeded()
                let minimumY = -self.messageCollectionView.adjustedContentInset.top
                let maximumY = max(
                    minimumY,
                    self.messageCollectionView.contentSize.height
                        - self.messageCollectionView.bounds.height
                        + self.messageCollectionView.adjustedContentInset.bottom
                )
                let restoredY = self.unreadTimelineLayoutSnapshot().flatMap { snapshot in
                    preservedUnreadAnchor?.restoredContentOffsetY(
                        snapshot: snapshot,
                        minimumOffsetY: minimumY,
                        maximumOffsetY: maximumY
                    )
                } ?? min(max(preservedUnreadOffsetY, minimumY), maximumY)
                self.messageCollectionView.setContentOffset(
                    CGPoint(x: self.messageCollectionView.contentOffset.x, y: restoredY),
                    animated: false
                )
                self.leftCollectionView.setContentOffset(
                    CGPoint(x: self.leftCollectionView.contentOffset.x, y: restoredY),
                    animated: false
                )
            } else {
                self.ensureSelectedLeftSidebarItemVisible(animated: false)
                self.scrollInitialMessagesToBottomIfNeeded()
            }
            self.updateConnections()
            self.scheduleConversationHeightPrewarming()
            self.scheduleOpenApiChatMessageSyncWhenIdle()
        }
        return true
    }

    func installUnreadTimelineTestDataIfNeeded() {
        guard !state.currentUsers.isEmpty else { return }
        let fixture = ChatDataFactory.makeUnreadTimelineTestData(accounts: state.currentUsers)
        let fixtureParticipantIDs = Set(fixture.participants.map(\.id))
        let fixtureMessageIDs = Set(fixture.messages.map(\.id))
        let accounts = state.currentUsers
        let currentAccountIDs = Set(accounts.map(\.id))
        let conversationIDs = Set(fixture.conversationKindsByID.keys)
        let participantIDs = fixtureParticipantIDs.union(currentAccountIDs)

        state.participants.removeAll { fixtureParticipantIDs.contains($0.id) }
        state.participants.append(contentsOf: fixture.participants)
        state.messages = state.messages.compactMap { message in
            guard conversationIDs.contains(message.conversationID) else { return message }
            guard !fixtureMessageIDs.contains(message.id) else { return nil }
            guard participantIDs.contains(message.sender.id) else { return nil }
            let expectedKind = fixture.conversationKindsByID[message.conversationID]
            guard message.isGroupConversation == (expectedKind == .chatroom) else { return nil }

            if message.isOutgoing,
               message.sender.isCurrentUser,
               currentAccountIDs.contains(message.sender.id),
               fixture.relatedAccountIDsByConversationID[message.conversationID]?.contains(message.sender.id) == true {
                return message.recipientAccountID == message.sender.id
                    ? message
                    : message.replacingRecipientAccountID(message.sender.id)
            }
            guard let recipientAccountID = message.recipientAccountID,
                  currentAccountIDs.contains(recipientAccountID),
                  fixture.relatedAccountIDsByConversationID[message.conversationID]?.contains(recipientAccountID) == true
            else { return nil }
            return message
        }
        state.messages.append(contentsOf: fixture.messages)

        for relatedAccountIDs in fixture.relatedAccountIDsByConversationID.values {
            prioritizeUnansweredAccountColors(for: relatedAccountIDs)
        }
        for (groupID, memberIDs) in fixture.groupMemberIDsByGroupID {
            let relatedAccountIDs = fixture.relatedAccountIDsByConversationID[groupID] ?? []
            explicitGroupMemberIDsByGroupID[groupID] = (relatedAccountIDs + memberIDs).reduce(into: []) { result, id in
                guard !result.contains(id) else { return }
                result.append(id)
            }
        }
    }

    func removeUnreadTimelineTestDataIfPresent() {
        let fixture = ChatDataFactory.makeUnreadTimelineTestData(accounts: state.currentUsers)
        let participantIDs = Set(fixture.participants.map(\.id))
        let conversationIDs = Set(fixture.conversationKindsByID.keys)
        state.participants.removeAll { participantIDs.contains($0.id) }
        state.messages.removeAll { conversationIDs.contains($0.conversationID) }
        for conversationID in conversationIDs {
            explicitGroupMemberIDsByGroupID.removeValue(forKey: conversationID)
        }
    }

    func appendOpenApiFriendRequestMessages(
        for snapshot: OpenApiIMSnapshot,
        accountByWxid: [String: OpenApiWeChatAccountContext]
    ) {
        for (index, request) in snapshot.friendRequests.enumerated() {
            guard let participant = state.participants.first(where: { $0.id == request.participantID }),
                  let owner = accountByWxid[request.ownerWxid]
            else { continue }
            let sentAt = request.requestTime ?? Date(timeIntervalSince1970: TimeInterval(index))
            let requestMessage = request.requestMessage.isEmpty ? "请求添加你为好友" : request.requestMessage
            let body = "好友申请：\(requestMessage)"
            let detail = [
                "OpenAPI好友申请",
                "所属帐号ID：\(owner.participantID.uuidString)",
                "ownerWxid：\(request.ownerWxid)",
                "requestWxid：\(request.requestWxid)",
                request.backendID.isEmpty ? "" : "requestId：\(request.backendID)",
                request.source.isEmpty ? "" : "source：\(request.source)",
                "status：\(request.statusText)"
            ].filter { !$0.isEmpty }.joined(separator: "\n")
            state.messages.append(ChatMessage(
                id: OpenApiStableID.uuid(namespace: "openapi-friend-request-message", key: "\(request.ownerWxid)-\(request.requestWxid)-\(request.backendID)"),
                conversationID: request.participantID,
                type: .text,
                sender: participant,
                body: body,
                detail: detail,
                isOutgoing: false,
                presentation: .avatarOnly,
                timestamp: request.requestTime.map { ChatMessage.displayTimestamp(for: $0) } ?? "",
                sentAt: sentAt,
                recipientAccountID: owner.participantID
            ))
        }
    }

    func applyOpenApiTaskResults(_ taskResults: [OpenApiTaskResultContext]) {
        guard !taskResults.isEmpty else { return }
        var changedMessageIDs = Set<UUID>()
        for task in taskResults {
            let taskID = task.taskID.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !taskID.isEmpty,
                  let existing = openApiDeliveryStatusesByMessageID.values.first(where: { $0.taskIDs.contains(taskID) })
            else {
                continue
            }
            let note = [
                task.displayBody,
                task.msgSvrId.isEmpty ? "" : "msgSvrId：\(task.msgSvrId)",
                task.statusText.isEmpty ? "" : "TaskResult：\(task.statusText)"
            ].filter { !$0.isEmpty }.joined(separator: "\n")
            setOpenApiDeliveryStatus(
                forMessageID: existing.messageID,
                conversationID: existing.conversationID,
                taskIDs: existing.taskIDs,
                state: task.state,
                note: note,
                reload: false
            )
            changedMessageIDs.insert(existing.messageID)
        }
        guard !changedMessageIDs.isEmpty, messageCollectionView.window != nil else { return }
        for messageID in changedMessageIDs {
            reloadRenderedMessageItem(messageID: messageID, updatesConnections: false)
        }
        scheduleConnectionUpdate()
    }

    func rebuildOpenApiGroupMembership(
        _ snapshot: OpenApiIMSnapshot,
        accountByWxid: [String: OpenApiWeChatAccountContext]
    ) {
        var accountIDsByGroupID: [UUID: [UUID]] = [:]
        for context in snapshot.chatrooms {
            guard let owner = accountByWxid[context.ownerWxid] else { continue }
            accountIDsByGroupID[context.participantID, default: []].append(owner.participantID)
        }
        for (groupID, ownerAccountIDs) in accountIDsByGroupID {
            let existingIDs = explicitGroupMemberIDsByGroupID[groupID] ?? []
            let mergedIDs = (ownerAccountIDs + existingIDs).reduce(into: [UUID]()) { result, id in
                guard !result.contains(id) else { return }
                result.append(id)
            }
            explicitGroupMemberIDsByGroupID[groupID] = mergedIDs
        }
    }

    func normalizePersistedInFlightMessages() {
        for index in state.messages.indices {
            let message = state.messages[index]
            let isNotification = message.type == .system || message.type == .groupNotice
            let normalizedBody: String?
            if message.detail == "AI自动回复" && message.body == "AI自动回复生成中..." {
                normalizedBody = "收到，我稍后回复你。"
            } else if message.detail.contains("类型：AI预测回复"),
                      message.body.contains("建议回复：") {
                normalizedBody = suggestedReplyText(fromAutoReplyBody: message.body)
            } else if message.isAI && (message.body == "Codex 正在生成..." || message.body == "Codex 正在生成回复...") {
                normalizedBody = "Codex 上次生成已中断，请重新发送。"
            } else {
                normalizedBody = nil
            }
            guard normalizedBody != nil || isNotification else { continue }
            let finalBody = normalizedBody ?? message.body

            state.messages[index] = ChatMessage(
                id: message.id,
                conversationID: message.conversationID,
                type: message.type,
                sender: message.sender,
                body: finalBody,
                detail: message.detail,
                isOutgoing: isNotification ? false : message.isOutgoing,
                presentation: isNotification ? .bare : message.presentation,
                timestamp: message.timestamp,
                sentAt: message.sentAt,
                backendMessageID: message.backendMessageID,
                attachmentURL: message.attachmentURL,
                isAI: message.isAI,
                isGroupConversation: message.isGroupConversation,
                richElements: normalizedBody == nil ? message.richElements : richElements(from: finalBody),
                contactCard: message.contactCard,
                contactCardAccountID: message.contactCardAccountID,
                quotedMessageID: message.quotedMessageID,
                mergedForwardMessages: message.mergedForwardMessages,
                recipientAccountID: message.recipientAccountID,
                unansweredMetadata: message.unansweredMetadata
            )
        }
    }

    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        ensureVerticalMessageLayout()
        if hasPresentedInitialChatWindow {
            pollOpenApiChatChanges()
        }
        if navigationController?.topViewController === self {
            navigationController?.setNavigationBarHidden(true, animated: false)
            startConnectionTracking()
            startTiltQRCodeDetection()
        }
    }

    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        installGlobalBlinkControlGestures()
        refreshMessageLayoutAfterNavigation()
        scrollInitialMessagesToBottomIfNeeded()
        scheduleConversationHeightPrewarming()
        hasPresentedInitialChatWindow = true
        scheduleOpenApiChatMessageSyncWhenIdle()
#if DEBUG
        startPerformanceAutoRunIfNeeded()
#endif
    }

#if DEBUG
    func startPerformanceAutoRunIfNeeded() {
        guard ChatDataFactory.shouldAutoRunPerformanceScenario,
              !hasStartedPerformanceAutoRun else { return }
        hasStartedPerformanceAutoRun = true
        PerformanceSignpost.event("PerformanceAutoRunStarted")

        let fractions: [CGFloat] = [1, 0.55, 0, 0.72, 0.18, 1, 0.35, 0]
        for (index, fraction) in fractions.enumerated() {
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.0 + Double(index) * 0.85) { [weak self] in
                guard let self, self.viewIfLoaded?.window != nil else { return }
                let maximumOffset = max(
                    -self.messageCollectionView.adjustedContentInset.top,
                    self.messageCollectionView.contentSize.height
                        - self.messageCollectionView.bounds.height
                        + self.messageCollectionView.adjustedContentInset.bottom
                )
                let minimumOffset = -self.messageCollectionView.adjustedContentInset.top
                let targetY = minimumOffset + (maximumOffset - minimumOffset) * fraction
                self.messageCollectionView.setContentOffset(
                    CGPoint(x: 0, y: targetY),
                    animated: true
                )
                PerformanceSignpost.event("PerformanceAutoScrollStep")

                if index == 3,
                   let nextAccount = self.state.currentUsers.dropFirst().first {
                    self.selectRightAccount(accountID: nextAccount.id)
                    PerformanceSignpost.event("PerformanceAutoAccountSwitch")
                }
            }
        }
    }
#endif

    override func viewDidDisappear(_ animated: Bool) {
        super.viewDidDisappear(animated)
        cancelLeftScrollPreview()
        if navigationController?.topViewController !== self {
            cancelOutstandingCollectionPrefetches()
            openApiChatSyncStartWorkItem?.cancel()
            openApiChatSyncStartWorkItem = nil
            stopConnectionTracking()
            stopTiltQRCodeDetection()
        }
    }

    func refreshMessageLayoutAfterNavigation() {
        guard isViewLoaded, windowState != .minimized, !chromeView.isHidden else { return }
        cancelLeftScrollPreview()
        stopLeftScrollPerformanceUpdates()
        hideMessageTrashDropTarget(animated: false)
        clearMessageDropFeedback(animated: false)

        let currentSize = view.bounds.size
        if !hasCompletedInitialNavigationLayoutRefresh {
            hasCompletedInitialNavigationLayoutRefresh = true
            lastNavigationLayoutRefreshSize = currentSize
            updateSelectedLeftSidebarFloatingItem(animated: false)
            updateLeftCollectionBounceInsets()
            scheduleConnectionUpdate()
            return
        }
        let requiresFullRefresh = abs(lastNavigationLayoutRefreshSize.width - currentSize.width) > 0.5
            || abs(lastNavigationLayoutRefreshSize.height - currentSize.height) > 0.5
        lastNavigationLayoutRefreshSize = currentSize

        guard requiresFullRefresh else {
            // Returning from a pushed controller does not change the collection
            // data or geometry. Keep the existing cells and offsets instead of
            // synchronously rebuilding all four collection views.
            updateSelectedLeftSidebarFloatingItem(animated: false)
            updateLeftCollectionBounceInsets()
            scheduleConnectionUpdate()
            return
        }

        leftCollectionView.collectionViewLayout.invalidateLayout()
        rightAccountCollectionView.collectionViewLayout.invalidateLayout()
        rightToolCollectionView.collectionViewLayout.invalidateLayout()
        messageCollectionView.collectionViewLayout.invalidateLayout()
        UIView.performWithoutAnimation {
            leftCollectionView.reloadData()
            rightAccountCollectionView.reloadData()
            rightToolCollectionView.reloadData()
            messageCollectionView.reloadData()
            view.setNeedsLayout()
            view.layoutIfNeeded()
            messageCollectionView.layoutIfNeeded()
        }
        updateSelectedLeftSidebarFloatingItem(animated: false)
        scheduleConnectionUpdate()
        DispatchQueue.main.async { [weak self] in
            guard let self else { return }
            self.updateConnections()
        }
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        chromeView.layer.shadowPath = UIBezierPath(
            roundedRect: chromeView.bounds,
            cornerRadius: 0
        ).cgPath
        updateRightRailHeightConstraint()
        invalidateSidebarLayoutsIfNeeded()
        updateLeftCollectionBounceInsets()
        updateSelectedLeftSidebarFloatingItem(animated: false)
        if abs(lastMessageHeightCacheWidth - messageCollectionView.bounds.width) > 0.5 {
            clearMessageHeightCache()
            lastMessageHeightCacheWidth = messageCollectionView.bounds.width
            ensureVerticalMessageLayout(reloadData: true)
            scheduleConversationHeightPrewarming()
        }
        scheduleConnectionUpdate()
    }

    func startConnectionTracking() {
        scheduleConnectionUpdate()
    }

    func stopConnectionTracking() {
        stopFrameCoordinator(clearLeftScrollWork: true)
        connectionOverlay.anchors = []
    }

    func scheduleConnectionUpdate() {
        guard isViewLoaded, windowState != .minimized, !chromeView.isHidden else {
            stopFrameCoordinator(clearLeftScrollWork: true)
            connectionOverlay.anchors = []
            return
        }
        connectionUpdateNeeded = true
        ensureFrameCoordinator()
    }

    func ensureFrameCoordinator() {
        guard frameCoordinatorDisplayLink == nil else { return }
        let displayLink = CADisplayLink(target: self, selector: #selector(handleFrameCoordinatorDisplayLink(_:)))
        let maximumFramesPerSecond = max(
            viewIfLoaded?.window?.screen.maximumFramesPerSecond ?? UIScreen.main.maximumFramesPerSecond,
            1
        )
        if #available(iOS 15.0, *) {
            let maximum = Float(maximumFramesPerSecond)
            displayLink.preferredFrameRateRange = CAFrameRateRange(
                minimum: min(30, maximum),
                maximum: maximum,
                preferred: maximum
            )
        } else {
            displayLink.preferredFramesPerSecond = maximumFramesPerSecond
        }
        displayLink.add(to: .main, forMode: .common)
        frameCoordinatorDisplayLink = displayLink
    }

    @objc func handleFrameCoordinatorDisplayLink(_ displayLink: CADisplayLink) {
        guard frameCoordinatorDisplayLink === displayLink else {
            displayLink.invalidate()
            return
        }
        guard isViewLoaded, windowState != .minimized, !chromeView.isHidden else {
            stopFrameCoordinator(clearLeftScrollWork: true)
            connectionOverlay.anchors = []
            return
        }

        performLeftScrollFrameUpdates()
        if connectionUpdateNeeded {
            let isScrolling = isConnectionScrollActive()
            let elapsed = displayLink.timestamp - lastConnectionUpdateTimestamp
            if !isScrolling || elapsed >= scrollingConnectionUpdateInterval {
                connectionUpdateNeeded = false
                lastConnectionUpdateTimestamp = displayLink.timestamp
                performConnectionUpdate()
            }
        }
        if isConnectionScrollActive() {
            connectionUpdateNeeded = true
        }
        if !connectionUpdateNeeded, !hasPendingLeftScrollFrameWork {
            stopFrameCoordinator(clearLeftScrollWork: false)
        }
    }

    func stopFrameCoordinator(clearLeftScrollWork: Bool) {
        frameCoordinatorDisplayLink?.invalidate()
        frameCoordinatorDisplayLink = nil
        connectionUpdateNeeded = false
        lastConnectionUpdateTimestamp = 0
        deferredConnectionLayoutPassCount = 0
        if clearLeftScrollWork {
            resetLeftScrollPerformanceState()
        }
    }

    func isConnectionScrollActive() -> Bool {
        messageCollectionView.isDragging
            || messageCollectionView.isDecelerating
            || leftCollectionView.isDragging
            || leftCollectionView.isDecelerating
            || rightAccountCollectionView.isDragging
            || rightAccountCollectionView.isDecelerating
            || rightToolCollectionView.isDragging
            || rightToolCollectionView.isDecelerating
            || isLeftScrollPreviewGestureActive()
    }

    var isHomeTimeline: Bool {
        // A selected conversation is authoritative even when a controller is
        // created with the default unanswered route (for example, in tests or
        // while restoring a persisted selection).
        guard state.selectedFriendID == nil else { return false }
        if case .unanswered = chatWindowRoute {
            return true
        }
        return false
    }

    var isAccountDirectory: Bool {
        if case .accountDirectory = chatWindowRoute {
            return true
        }
        return false
    }

    var unansweredAccountFilterID: UUID? {
        chatWindowRoute.unansweredScope?.accountID
    }

    var selectedUnansweredMessageID: UUID? {
        chatWindowRoute.unansweredFocus?.messageID
            ?? chatWindowRoute.unansweredOrigin?.sourceFocus.messageID
    }

    var selectedUnansweredItemID: UnansweredItemID? {
        chatWindowRoute.unansweredFocus?.itemID
            ?? chatWindowRoute.unansweredOrigin?.sourceFocus.itemID
    }

    var unansweredNavigationOrigin: UnansweredNavigationOrigin? {
        chatWindowRoute.unansweredOrigin
    }

    func setUnansweredTimelineRoute(
        scope: UnansweredAccountScope,
        focus: UnansweredTimelineFocus?
    ) {
        chatWindowRoute = .unanswered(scope: scope, focus: focus)
    }

    func setConversationRoute(
        accountID: UUID,
        conversationID: UUID,
        unansweredOrigin: UnansweredNavigationOrigin?,
        directoryOriginAccountID: UUID? = nil
    ) {
        chatWindowRoute = .conversation(
            accountID: accountID,
            conversationID: conversationID,
            unansweredOrigin: unansweredOrigin,
            directoryOriginAccountID: directoryOriginAccountID
        )
    }

    var renderedMessages: [ChatMessage] {
        let cacheKey = renderedMessagesCurrentCacheKey()
        if cacheKey == renderedMessagesCacheKey {
            return renderedMessagesCache
        }
        let messages: [ChatMessage]
        if isHomeTimeline {
            let pending = pendingReplyHomeMessages(limit: homeRenderedMessageLimit)
            messages = groupedUnreadMessagesForDisplay(pending)
        } else if isAccountDirectory {
            messages = []
        } else {
            messages = scopedVisibleMessagesForCurrentConversation()
        }
        renderedMessagesCacheKey = cacheKey
        renderedMessagesCache = messages
        return messages
    }

    func renderedMessagesCurrentCacheKey() -> String {
        [
            "revision:\(visibleDataRevision)",
            "home:\(isHomeTimeline)",
            "directory:\(isAccountDirectory)",
            state.selectedFriendID?.uuidString ?? "home",
            state.selectedAccountID.uuidString,
            unansweredAccountFilterID?.uuidString ?? "all-accounts",
            "\(homeRenderedMessageLimit)",
            "\(state.messages.count)",
            state.messages.last?.id.uuidString ?? "none",
            state.messages.last.map { "\($0.sentAt.timeIntervalSinceReferenceDate)" } ?? "none"
        ].joined(separator: "|")
    }

    func scopedVisibleMessagesForCurrentConversation() -> [ChatMessage] {
        guard let selectedFriendID = state.selectedFriendID,
              let conversation = participantLookupByID()[selectedFriendID]
        else { return state.visibleMessages }
        if isGroupConversation(conversation) {
            return state.messages.filter { $0.conversationID == selectedFriendID }
        }
        guard !conversation.isAIAccount else { return state.visibleMessages }
        return state.visibleMessages.filter { isDirectMessage($0, visibleForAccountID: state.selectedAccountID) }
    }

    func pendingReplyHomeMessages(limit: Int) -> [ChatMessage] {
        guard limit > 0 else { return [] }
        return Array(pendingReplyHomePresentation().messages.suffix(limit))
    }

    func pendingReplyHomeMessages() -> [ChatMessage] {
        pendingReplyHomePresentation().messages
    }

    func pendingReplyHomePresentation() -> PendingReplyPresentation {
        ensurePendingReplyIndex()
        let limitsKey = unreadDirectVisibleLimitByConversationID
            .sorted {
                let lhs = "\($0.key.conversationID.uuidString):\($0.key.accountID.uuidString)"
                let rhs = "\($1.key.conversationID.uuidString):\($1.key.accountID.uuidString)"
                return lhs < rhs
            }
            .map {
                "\($0.key.conversationID.uuidString):\($0.key.accountID.uuidString):\($0.value)"
            }
            .joined(separator: ",")
        let groupLimitsKey = unreadGroupVisibleLimitByConversationID
            .sorted { $0.key.uuidString < $1.key.uuidString }
            .map { "\($0.key.uuidString):\($0.value)" }
            .joined(separator: ",")
        let cacheKey = [
            "revision:\(visibleDataRevision)",
            "messages:\(state.messages.count)",
            state.messages.last?.id.uuidString ?? "none",
            state.messages.last.map { "\($0.sentAt.timeIntervalSinceReferenceDate)" } ?? "none",
            "pending:\(pendingReplyIndex.totalPendingCount)",
            "account:\(unansweredAccountFilterID?.uuidString ?? "all")",
            "limits:\(limitsKey)",
            "group-limits:\(groupLimitsKey)",
            "preview:\(hasCompletedOpenApiChatBootstrap ? 0 : 1)"
        ].joined(separator: "|")
        if cacheKey == pendingReplyPresentationCacheKey,
           let cached = pendingReplyPresentationCache {
            return cached
        }
        let rawPending = pendingReplyIndex.latestMessages(
            limit: pendingReplyIndex.totalPendingCount
        ) + makeUnreadPreviewMessages()
        let replyRelevantMessages = state.messages.filter(isHomeReplyRelevantMessage)
        let presentation = PendingReplyPresentationBuilder.build(
            pendingMessages: rawPending,
            allMessages: replyRelevantMessages,
            currentAccountIDs: Set(state.currentUsers.map(\.id)),
            accountFilterID: unansweredAccountFilterID,
            directAccountID: { [unowned self] message in
                self.unansweredRecipientAccountID(for: message)
            },
            directVisibleLimits: unreadDirectVisibleLimitByConversationID,
            groupVisibleLimits: unreadGroupVisibleLimitByConversationID
        )
        pendingReplyPresentationCacheKey = cacheKey
        pendingReplyPresentationCache = presentation
        return presentation
    }

    func unreadTimelineFoldPresentation(
        conversationID: UUID
    ) -> UnreadTimelineFoldPresentation? {
        for message in renderedMessages where message.conversationID == conversationID {
            if let fold = unreadTimelineFoldPresentation(for: message) {
                return fold
            }
        }
        return nil
    }

    func unreadTimelineFoldPresentation(
        for message: ChatMessage
    ) -> UnreadTimelineFoldPresentation? {
        let presentation = pendingReplyHomePresentation()
        if !message.isGroupConversation,
           let accountID = unansweredRecipientAccountID(for: message) {
            let key = PendingReplyConversationAccountKey(
                conversationID: message.conversationID,
                accountID: accountID
            )
            guard !expandedUnreadDirectKeys.contains(key),
                  let hiddenCount = presentation.hiddenDirectMessageCountByKey[key],
                  hiddenCount > 0
            else { return nil }
            return UnreadTimelineFoldPresentation(
                conversationID: message.conversationID,
                accountID: accountID,
                kind: .directMessages,
                title: unreadTimelineFoldTitle(hiddenCount: hiddenCount)
            )
        }
        guard !expandedUnreadConversationIDs.contains(message.conversationID) else { return nil }
        if let hiddenCount = presentation
            .hiddenGroupItemCountByConversationID[message.conversationID],
           hiddenCount > 0 {
            return UnreadTimelineFoldPresentation(
                conversationID: message.conversationID,
                accountID: nil,
                kind: .groupItems,
                title: unreadTimelineFoldTitle(hiddenCount: hiddenCount)
            )
        }
        return nil
    }

    func unreadTimelineFoldTitle(hiddenCount: Int) -> String {
        let displayedCount = hiddenCount > 99 ? "99+" : String(hiddenCount)
        return "展开 \(displayedCount) 条未回复"
    }

    func handleUnreadTimelineFoldTap(_ presentation: UnreadTimelineFoldPresentation) {
        guard isHomeTimeline else { return }
        guard !isUnreadFoldExpansionInProgress else {
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.12) { [weak self] in
                self?.handleUnreadTimelineFoldTap(presentation)
            }
            return
        }
        let currentLimit: Int
        let targetLimit: Int
        let completesExpansion: Bool
        switch presentation.kind {
        case .groupItems:
            currentLimit = unreadGroupVisibleLimitByConversationID[
                presentation.conversationID
            ] ?? 3
            let hiddenCount = pendingReplyHomePresentation()
                .hiddenGroupItemCountByConversationID[presentation.conversationID] ?? 0
            guard hiddenCount > 0 else { return }
            targetLimit = currentLimit + min(
                hiddenCount,
                UnreadTimelineLayoutMetrics.foldExpansionBatchSize
            )
            completesExpansion = hiddenCount <= UnreadTimelineLayoutMetrics.foldExpansionBatchSize
        case .directMessages:
            guard let accountID = presentation.accountID else { return }
            let key = PendingReplyConversationAccountKey(
                conversationID: presentation.conversationID,
                accountID: accountID
            )
            currentLimit = unreadDirectVisibleLimitByConversationID[key] ?? 3
            let hiddenCount = pendingReplyHomePresentation()
                .hiddenDirectMessageCountByKey[key] ?? 0
            guard hiddenCount > 0 else { return }
            targetLimit = currentLimit + min(
                hiddenCount,
                UnreadTimelineLayoutMetrics.foldExpansionBatchSize
            )
            completesExpansion = hiddenCount <= UnreadTimelineLayoutMetrics.foldExpansionBatchSize
        }
        if completesExpansion, let accountID = presentation.accountID {
            expandedUnreadDirectKeys.insert(PendingReplyConversationAccountKey(
                conversationID: presentation.conversationID,
                accountID: accountID
            ))
        } else if completesExpansion {
            expandedUnreadConversationIDs.insert(presentation.conversationID)
        }
        isUnreadFoldExpansionInProgress = true
        unreadFoldExpansionGeneration &+= 1
        continueUnreadFoldExpansion(
            conversationID: presentation.conversationID,
            accountID: presentation.accountID,
            kind: presentation.kind,
            currentLimit: currentLimit,
            targetLimit: targetLimit,
            generation: unreadFoldExpansionGeneration
        )
    }

    func continueUnreadFoldExpansion(
        conversationID: UUID,
        accountID: UUID?,
        kind: UnreadTimelineFoldKind,
        currentLimit: Int,
        targetLimit: Int,
        generation: Int
    ) {
        guard isHomeTimeline,
              generation == unreadFoldExpansionGeneration
        else {
            finishUnreadFoldExpansion()
            return
        }
        let nextLimit = targetLimit
        let addedCount = max(0, nextLimit - currentLimit)
        guard addedCount > 0 else {
            finishUnreadFoldExpansion()
            return
        }
        let oldMessages = renderedMessages
        let oldLeftItems = visibleLeftItemsForSelectedAccount()
        let oldPresentationCount = pendingReplyHomePresentation().messages.count
        if let oldTrailingIndex = oldMessages.lastIndex(where: {
            guard $0.conversationID == conversationID else { return false }
            return accountID == nil || unansweredRecipientAccountID(for: $0) == accountID
        }),
           let oldTrailingCell = messageCollectionView.cellForItem(
               at: IndexPath(item: oldTrailingIndex, section: 0)
           ) as? ChatMessageCell {
            oldTrailingCell.prepareForUnansweredFoldExpansionTransition()
        }
        switch kind {
        case .groupItems:
            unreadGroupVisibleLimitByConversationID[conversationID] = nextLimit
        case .directMessages:
            guard let accountID else {
                finishUnreadFoldExpansion()
                return
            }
            unreadDirectVisibleLimitByConversationID[PendingReplyConversationAccountKey(
                conversationID: conversationID,
                accountID: accountID
            )] = nextLimit
        }
        let expandedPresentationCount = pendingReplyHomePresentation().messages.count
        homeRenderedMessageLimit += max(0, expandedPresentationCount - oldPresentationCount)
        applyUnreadFoldExpansion(
            conversationID: conversationID,
            oldMessages: oldMessages,
            oldLeftItems: oldLeftItems
        ) { [weak self] in
            guard let self, generation == self.unreadFoldExpansionGeneration else { return }
            self.finishUnreadFoldExpansion()
        }
    }

    func applyUnreadFoldExpansion(
        conversationID: UUID,
        oldMessages: [ChatMessage],
        oldLeftItems: [SidebarItem],
        completion: (() -> Void)? = nil
    ) {
        let fallbackOffsetY = messageCollectionView.contentOffset.y
        let oldMessageFramesByID = Dictionary(
            uniqueKeysWithValues: messageCollectionView.indexPathsForVisibleItems.compactMap {
                indexPath -> (UUID, CGRect)? in
                guard let message = oldMessages[safe: indexPath.item],
                      let cell = messageCollectionView.cellForItem(at: indexPath)
                else { return nil }
                return (message.id, cell.frame)
            }
        )
        let oldLeftFramesByID = Dictionary(
            uniqueKeysWithValues: leftCollectionView.indexPathsForVisibleItems.compactMap {
                indexPath -> (UUID, CGRect)? in
                guard let item = oldLeftItems[safe: indexPath.item],
                      let participantID = item.participantID,
                      let cell = leftCollectionView.cellForItem(at: indexPath)
                else { return nil }
                return (participantID, cell.frame)
            }
        )
        let oldTrailingFrame = oldMessages.lastIndex(where: {
            $0.conversationID == conversationID
        }).flatMap { index in
            oldMessageFramesByID[oldMessages[index].id]
                ?? unreadTimelineLayoutSnapshot()?.messageFrames[safe: index]
        }
        let drawerOriginY = oldTrailingFrame.map {
            $0.maxY - UnreadTimelineLayoutMetrics.foldFooterHeight
        } ?? fallbackOffsetY
        let messageWasScrollEnabled = messageCollectionView.isScrollEnabled
        let leftWasScrollEnabled = leftCollectionView.isScrollEnabled
        unreadFoldPostExpansionAnchor = unreadTimelineLayoutSnapshot().flatMap {
            UnreadTimelineGroupViewportAnchor.capture(
                participantID: conversationID,
                snapshot: $0,
                contentOffsetY: fallbackOffsetY
            )
        }
        unreadFoldLockedContentOffsetY = fallbackOffsetY
        isSynchronizingUnreadScroll = true
        messageCollectionView.isScrollEnabled = false
        leftCollectionView.isScrollEnabled = false
        invalidateSelectionVisibleDataCaches()
        let newMessages = renderedMessages
        let newLeftItems = visibleLeftItemsForSelectedAccount()
        _ = unreadTimelineLayoutSnapshot()

        let oldMessageIDs = oldMessages.map(\.id)
        let oldMessageIDSet = Set(oldMessageIDs)
        let retainedMessageIDs = newMessages.lazy
            .filter { oldMessageIDSet.contains($0.id) }
            .map(\.id)
        let insertedIndexPaths = newMessages.indices.compactMap { index -> IndexPath? in
            oldMessageIDSet.contains(newMessages[index].id)
                ? nil
                : IndexPath(item: index, section: 0)
        }
        let oldLeftIDs = oldLeftItems.compactMap(\.participantID)
        let newLeftIDs = newLeftItems.compactMap(\.participantID)
        let canInsertInPlace = Array(retainedMessageIDs) == oldMessageIDs
            && oldLeftIDs == newLeftIDs
            && !insertedIndexPaths.isEmpty

        if canInsertInPlace {
            // 展开只插入本组新增消息；左侧头像不重载，上方内容保持原位。
            messageCollectionView.performBatchUpdates {
                messageCollectionView.insertItems(at: insertedIndexPaths)
                leftCollectionView.collectionViewLayout.invalidateLayout()
            } completion: { [weak self] _ in
                guard let self else { return }
                self.messageCollectionView.isScrollEnabled = messageWasScrollEnabled
                self.leftCollectionView.isScrollEnabled = leftWasScrollEnabled
                self.messageCollectionView.setContentOffset(
                    CGPoint(x: self.messageCollectionView.contentOffset.x, y: fallbackOffsetY),
                    animated: false
                )
                self.leftCollectionView.setContentOffset(
                    CGPoint(x: self.leftCollectionView.contentOffset.x, y: fallbackOffsetY),
                    animated: false
                )
                self.updateLeftCollectionBounceInsets()
                self.unreadFoldLockedContentOffsetY = nil
                self.isSynchronizingUnreadScroll = false
                self.scheduleConnectionUpdate()
                completion?()
            }
            return
        }

        UIView.performWithoutAnimation {
            messageCollectionView.reloadData()
            leftCollectionView.reloadData()
            messageCollectionView.collectionViewLayout.invalidateLayout()
            leftCollectionView.collectionViewLayout.invalidateLayout()
            messageCollectionView.layoutIfNeeded()
            leftCollectionView.layoutIfNeeded()
            messageCollectionView.setContentOffset(
                CGPoint(x: messageCollectionView.contentOffset.x, y: fallbackOffsetY),
                animated: false
            )
            leftCollectionView.setContentOffset(
                CGPoint(x: leftCollectionView.contentOffset.x, y: fallbackOffsetY),
                animated: false
            )
            messageCollectionView.layoutIfNeeded()
            leftCollectionView.layoutIfNeeded()

            for indexPath in messageCollectionView.indexPathsForVisibleItems {
                guard let message = newMessages[safe: indexPath.item],
                      let cell = messageCollectionView.cellForItem(at: indexPath)
                else { continue }
                cell.alpha = 1
                if let oldFrame = oldMessageFramesByID[message.id] {
                    cell.transform = CGAffineTransform(
                        translationX: 0,
                        y: oldFrame.minY - cell.frame.minY
                    )
                } else if message.conversationID == conversationID {
                    cell.alpha = 0
                    cell.transform = CGAffineTransform(
                        translationX: 0,
                        y: drawerOriginY - cell.frame.minY
                    ).scaledBy(x: 1, y: 0.96)
                } else {
                    cell.alpha = 0
                    cell.transform = .identity
                }
            }
            for indexPath in leftCollectionView.indexPathsForVisibleItems {
                guard let item = newLeftItems[safe: indexPath.item],
                      let participantID = item.participantID,
                      let cell = leftCollectionView.cellForItem(at: indexPath)
                else { continue }
                cell.alpha = 1
                if let oldFrame = oldLeftFramesByID[participantID] {
                    cell.transform = CGAffineTransform(
                        translationX: 0,
                        y: oldFrame.minY - cell.frame.minY
                    )
                } else {
                    cell.alpha = 0
                    cell.transform = .identity
                }
            }
        }

        UIView.animate(
            withDuration: 0.28,
            delay: 0,
            options: [.curveEaseInOut, .beginFromCurrentState]
        ) {
            self.messageCollectionView.visibleCells.forEach {
                $0.transform = .identity
                $0.alpha = 1
            }
            self.leftCollectionView.visibleCells.forEach {
                $0.transform = .identity
                $0.alpha = 1
            }
        } completion: { [weak self] _ in
            guard let self else { return }
            self.messageCollectionView.isScrollEnabled = messageWasScrollEnabled
            self.leftCollectionView.isScrollEnabled = leftWasScrollEnabled
            self.messageCollectionView.setContentOffset(
                CGPoint(x: self.messageCollectionView.contentOffset.x, y: fallbackOffsetY),
                animated: false
            )
            self.leftCollectionView.setContentOffset(
                CGPoint(x: self.leftCollectionView.contentOffset.x, y: fallbackOffsetY),
                animated: false
            )
            self.updateLeftCollectionBounceInsets()
            self.unreadFoldLockedContentOffsetY = nil
            self.isSynchronizingUnreadScroll = false
            self.scheduleConnectionUpdate()
            completion?()
        }
    }

    func finishUnreadFoldExpansion() {
        unreadFoldLockedContentOffsetY = nil
        isUnreadFoldExpansionInProgress = false
        if let deferredSnapshot = deferredOpenApiIMSnapshotAfterUnreadExpansion {
            let shouldPersist = shouldPersistDeferredOpenApiIMSnapshot
            deferredOpenApiIMSnapshotAfterUnreadExpansion = nil
            shouldPersistDeferredOpenApiIMSnapshot = true
            needsUnreadTimelineRefreshAfterExpansion = false
            DispatchQueue.main.async { [weak self] in
                _ = self?.applyOpenApiIMSnapshot(
                    deferredSnapshot,
                    shouldPersistSnapshot: shouldPersist
                )
            }
            return
        }
        guard needsUnreadTimelineRefreshAfterExpansion else { return }
        needsUnreadTimelineRefreshAfterExpansion = false
        DispatchQueue.main.async { [weak self] in
            self?.refreshUIAfterOpenApiMessageSync()
        }
    }

    @discardableResult
    func applyUnreadTimelineExternalRefresh(
        oldMessages: [ChatMessage],
        oldLeftItems: [SidebarItem],
        groupAnchor: UnreadTimelineGroupViewportAnchor?,
        fallbackContentOffsetY: CGFloat,
        completion: (() -> Void)? = nil
    ) -> Bool {
        guard isHomeTimeline else { return false }
        let newMessages = renderedMessages
        let newLeftItems = visibleLeftItemsForSelectedAccount()
        let canApplyIncrementalDifference = messageCollectionView.numberOfItems(inSection: 0)
            == oldMessages.count
            && leftCollectionView.numberOfItems(inSection: 0) == oldLeftItems.count
            && unansweredCompletionDifferenceIsSafe(
                oldIDs: oldMessages.map(\.id),
                newIDs: newMessages.map(\.id)
            )
            && unansweredCompletionDifferenceIsSafe(
                oldIDs: oldLeftItems.map { $0.participantID ?? $0.id },
                newIDs: newLeftItems.map { $0.participantID ?? $0.id }
            )

        let newSnapshot = unreadTimelineLayoutSnapshot()
        var pendingCompletions = 2
        let finishRefresh: () -> Void = { [weak self] in
            guard let self else { return }
            pendingCompletions -= 1
            guard pendingCompletions == 0 else { return }
            self.messageCollectionView.layoutIfNeeded()
            self.leftCollectionView.layoutIfNeeded()
            let minimumY = -self.messageCollectionView.adjustedContentInset.top
            let maximumY = max(
                minimumY,
                (newSnapshot?.contentHeight ?? self.messageCollectionView.contentSize.height)
                    - self.messageCollectionView.bounds.height
                    + self.messageCollectionView.adjustedContentInset.bottom
            )
            let restoredY = newSnapshot.flatMap { snapshot in
                groupAnchor?.restoredContentOffsetY(
                    snapshot: snapshot,
                    minimumOffsetY: minimumY,
                    maximumOffsetY: maximumY
                )
            } ?? min(max(fallbackContentOffsetY, minimumY), maximumY)
            self.messageCollectionView.setContentOffset(
                CGPoint(x: self.messageCollectionView.contentOffset.x, y: restoredY),
                animated: false
            )
            self.updateLeftCollectionBounceInsets()
            self.syncUnreadTimelineScroll(from: self.messageCollectionView)
            self.scheduleConnectionUpdate()
            self.scheduleConversationHeightPrewarming()
            if self.unreadFoldPostExpansionAnchor?.participantID
                == groupAnchor?.participantID {
                self.unreadFoldPostExpansionAnchor = nil
            }
            completion?()
        }

        guard canApplyIncrementalDifference else {
            UIView.performWithoutAnimation {
                messageCollectionView.reloadData()
                leftCollectionView.reloadData()
                messageCollectionView.collectionViewLayout.invalidateLayout()
                leftCollectionView.collectionViewLayout.invalidateLayout()
            }
            DispatchQueue.main.async {
                finishRefresh()
                finishRefresh()
            }
            return true
        }

        applyUnansweredCompletionDifference(
            in: messageCollectionView,
            oldIDs: oldMessages.map(\.id),
            newIDs: newMessages.map(\.id),
            invalidatesLayout: true,
            completion: finishRefresh
        )
        applyUnansweredCompletionDifference(
            in: leftCollectionView,
            oldIDs: oldLeftItems.map { $0.participantID ?? $0.id },
            newIDs: newLeftItems.map { $0.participantID ?? $0.id },
            invalidatesLayout: true,
            completion: finishRefresh
        )
        return true
    }

    func ensurePendingReplyIndex() {
        guard !pendingReplyIndex.isInitialized else { return }
        guard !isBuildingPendingReplyIndex else { return }
        isBuildingPendingReplyIndex = true
        defer { isBuildingPendingReplyIndex = false }
        if let result = pendingReplyIndexAsyncResultStore.latest(),
           result.generation == pendingReplyIndexBuildGeneration {
            pendingReplyIndex = result.index
            return
        }
        let signpostID = PerformanceSignpost.begin("PendingReplyIndexRebuild")
        defer { PerformanceSignpost.end("PendingReplyIndexRebuild", id: signpostID) }
        var index = pendingReplyIndex
        index.rebuild(
            messages: state.messages,
            keyForMessage: { [unowned self] message in
                self.unansweredRecipientAccountID(for: message).map { accountID in
                    PendingReplyConversationAccountKey(
                        conversationID: message.conversationID,
                        accountID: accountID
                    )
                }
            },
            isRelevant: { [unowned self] in self.isHomeReplyRelevantMessage($0) },
            isIncoming: { [unowned self] in self.isIncomingHomeReplyMessage($0) }
        )
        pendingReplyIndex = index
    }

    func schedulePendingReplyIndexRebuild() {
        pendingReplyIndexRebuildWorkItem?.cancel()
        pendingReplyIndexRebuildWorkItem = nil
        pendingReplyIndexBuildGeneration &+= 1
        let generation = pendingReplyIndexBuildGeneration
        let messages = state.messages
        guard !messages.isEmpty else { return }

        let currentAccountIDs = Set(state.currentUsers.map(\.id))
        let accountIDByWxid = Dictionary(
            openApiAccountContextsByID.values.map { ($0.wxid, $0.participantID) },
            uniquingKeysWith: { current, _ in current }
        )
        let ownerAccountIDByConversationID = openApiConversationContextsByID.reduce(
            into: [UUID: UUID]()
        ) { result, pair in
            guard let accountID = accountIDByWxid[pair.value.ownerWxid] else { return }
            result[pair.key] = accountID
        }
        let unresolvedConversationIDs = Set(messages.lazy.map(\.conversationID)).subtracting(
            ownerAccountIDByConversationID.keys
        )
        let participantsByID = Dictionary(
            state.participants.map { ($0.id, $0) },
            uniquingKeysWith: { current, _ in current }
        )
        var inferredAccountIDsByConversationID: [UUID: Set<UUID>] = [:]
        inferredAccountIDsByConversationID.reserveCapacity(unresolvedConversationIDs.count)
        for message in messages where unresolvedConversationIDs.contains(message.conversationID) {
            if let recipientAccountID = message.recipientAccountID,
               currentAccountIDs.contains(recipientAccountID) {
                inferredAccountIDsByConversationID[message.conversationID, default: []]
                    .insert(recipientAccountID)
            } else if message.isOutgoing,
                      currentAccountIDs.contains(message.sender.id) {
                inferredAccountIDsByConversationID[message.conversationID, default: []]
                    .insert(message.sender.id)
            }
        }
        var fallbackAccountIDByConversationID: [UUID: UUID] = [:]
        fallbackAccountIDByConversationID.reserveCapacity(unresolvedConversationIDs.count)
        for conversationID in unresolvedConversationIDs {
            guard let participant = participantsByID[conversationID] else { continue }
            let relatedAccountIDs: Set<UUID>
            if let explicitRelatedAccountIDs = openApiRelatedAccountIDsByConversationID[conversationID],
               !explicitRelatedAccountIDs.isEmpty {
                relatedAccountIDs = explicitRelatedAccountIDs
            } else if participant.isAIAccount {
                relatedAccountIDs = currentAccountIDs
            } else if isGroupConversation(participant) || isGroupParticipant(participant),
                      let explicitMemberIDs = explicitGroupMemberIDsByGroupID[conversationID],
                      !explicitMemberIDs.isEmpty {
                relatedAccountIDs = Set(explicitMemberIDs)
                    .intersection(currentAccountIDs)
                    .subtracting(removedGroupMemberIDsByGroupID[conversationID] ?? [])
            } else {
                relatedAccountIDs = inferredAccountIDsByConversationID[conversationID] ?? []
            }
            guard let accountID = state.currentUsers.lazy.map(\.id)
                .first(where: relatedAccountIDs.contains)
            else { continue }
            fallbackAccountIDByConversationID[conversationID] = accountID
        }

        var deliveryConfirmedMessageIDs = Set<UUID>()
        deliveryConfirmedMessageIDs.reserveCapacity(openApiDeliveryStatusesByMessageID.count)
        for (messageID, record) in openApiDeliveryStatusesByMessageID
        where OpenApiVisibleTaskState(rawValue: record.stateRawValue) == .succeeded {
            deliveryConfirmedMessageIDs.insert(messageID)
        }
        var requiresDeliveryConfirmationMessageIDs = Set<UUID>()
        for message in messages where message.isOutgoing && shouldAttemptOpenApiSend(message) {
            guard openApiSendContext(
                for: message,
                allowsCurrentSelectionFallback: false
            ) != nil else { continue }
            requiresDeliveryConfirmationMessageIDs.insert(message.id)
        }

        DispatchQueue.global(qos: .userInitiated).async { [weak self] in
            let signpostID = PerformanceSignpost.begin("PendingReplyIndexBackgroundRebuild")
            var inputs: [PendingReplyIndexedInput] = []
            inputs.reserveCapacity(messages.count)
            let receiverPrefixes = [
                "接收帐号ID：", "目标帐号ID：", "收件帐号ID：",
                "所属帐号ID：", "帐号ID："
            ]

            for message in messages {
                guard !message.detail.contains("OpenAPI同步预览"),
                      !message.detail.hasPrefix("AI自动回复预测"),
                      !message.isAI,
                      !message.sender.isAIAccount
                else { continue }
                switch message.type {
                case .system, .groupNotice:
                    continue
                default:
                    break
                }
                if requiresDeliveryConfirmationMessageIDs.contains(message.id),
                   !deliveryConfirmedMessageIDs.contains(message.id) {
                    continue
                }

                var accountID: UUID?
                if let recipientAccountID = message.recipientAccountID,
                   currentAccountIDs.contains(recipientAccountID) {
                    accountID = recipientAccountID
                } else if let ownerAccountID = ownerAccountIDByConversationID[message.conversationID] {
                    accountID = ownerAccountID
                } else {
                    for line in message.detail.components(separatedBy: .newlines) {
                        guard let prefix = receiverPrefixes.first(where: { line.hasPrefix($0) }) else {
                            continue
                        }
                        let rawID = String(line.dropFirst(prefix.count))
                            .trimmingCharacters(in: .whitespacesAndNewlines)
                        if let parsedID = UUID(uuidString: rawID),
                           currentAccountIDs.contains(parsedID) {
                            accountID = parsedID
                            break
                        }
                    }
                    if accountID == nil,
                       message.isOutgoing,
                       currentAccountIDs.contains(message.sender.id) {
                        accountID = message.sender.id
                    }
                    if accountID == nil {
                        accountID = fallbackAccountIDByConversationID[message.conversationID]
                    }
                }
                guard let accountID else { continue }
                inputs.append(
                    PendingReplyIndexedInput(
                        message: message,
                        key: PendingReplyConversationAccountKey(
                            conversationID: message.conversationID,
                            accountID: accountID
                        ),
                        isIncoming: !message.isOutgoing
                            && !message.sender.isCurrentUser
                            && !currentAccountIDs.contains(message.sender.id)
                    )
                )
            }

            var index = PendingReplyIndex()
            index.rebuild(classifiedInputs: inputs)
            self?.pendingReplyIndexAsyncResultStore.store(index, generation: generation)
            PerformanceSignpost.end("PendingReplyIndexBackgroundRebuild", id: signpostID)
            DispatchQueue.main.async { [weak self] in
                guard let self else { return }
                guard self.pendingReplyIndexBuildGeneration == generation else { return }
                guard !self.isUnreadFoldExpansionInProgress else {
                    self.needsUnreadTimelineRefreshAfterExpansion = true
                    return
                }
                let oldMessages = self.renderedMessages
                let oldLeftItems = self.visibleLeftItemsForSelectedAccount()
                let oldOffsetY = self.messageCollectionView.contentOffset.y
                let groupAnchor = self.unreadFoldPostExpansionAnchor
                    ?? self.unreadTimelineLayoutSnapshot().flatMap {
                        UnreadTimelineGroupViewportAnchor.captureVisible(
                            snapshot: $0,
                            contentOffsetY: oldOffsetY
                        )
                    }
                self.pendingReplyIndex = index
                self.invalidateSelectionVisibleDataCaches()
                guard self.isHomeTimeline, self.viewIfLoaded?.window != nil else { return }
                guard !self.applyUnreadTimelineExternalRefresh(
                    oldMessages: oldMessages,
                    oldLeftItems: oldLeftItems,
                    groupAnchor: groupAnchor,
                    fallbackContentOffsetY: oldOffsetY
                ) else { return }
                self.messageCollectionView.reloadData()
                self.leftCollectionView.reloadData()
                DispatchQueue.main.async { [weak self] in
                    guard let self else { return }
                    self.messageCollectionView.layoutIfNeeded()
                    self.leftCollectionView.layoutIfNeeded()
                    self.updateLeftCollectionBounceInsets()
                    self.syncUnreadTimelineScroll(from: self.messageCollectionView)
                    self.scheduleConnectionUpdate()
                }
            }
        }
    }

    func requestPendingReplyIndexRebuild() {
        pendingReplyIndexBuildGeneration &+= 1
        pendingReplyIndexRebuildWorkItem?.cancel()
        let workItem = DispatchWorkItem { [weak self] in
            self?.schedulePendingReplyIndexRebuild()
        }
        pendingReplyIndexRebuildWorkItem = workItem
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.01, execute: workItem)
    }

    func homeRenderableMessageCount() -> Int {
        pendingReplyHomePresentation().messages.count
    }

    func makeUnreadPreviewMessages() -> [ChatMessage] {
        if hasCompletedOpenApiChatBootstrap { return [] }
        let arguments = ProcessInfo.processInfo.arguments
        guard arguments.contains("--enable-unread-preview")
                || arguments.contains("--performance-auto-run")
        else { return [] }
        if !unreadPreviewMessages.isEmpty { return unreadPreviewMessages }
        let accountIDs = Set(state.currentUsers.map(\.id))
        let senders = state.participants
            .filter { $0.kind == .contact && !accountIDs.contains($0.id) }
            .prefix(5)
        let texts = [
            ["您好，前面发的方案您看到了吗？", "有两个细节想再确认一下。", "您方便的时候回复我就可以。"],
            ["资料已经整理好了。", "需要我现在发给您吗？"],
            ["明天下午的时间可以吗？"],
            ["客户这边刚刚有新的反馈。", "我把重点问题列出来了。"],
            ["好的，收到后麻烦告诉我一下。"]
        ]
        let now = Date(timeIntervalSince1970: 1_784_790_000)
        unreadPreviewMessages = senders.enumerated().flatMap { senderIndex, sender in
            texts[senderIndex % texts.count].enumerated().map { messageIndex, text in
                let recipientAccountID = accountIDForDirectConversation(
                    sender,
                    preferredAccountID: state.selectedAccountID
                )
                return ChatMessage(
                    id: OpenApiStableID.uuid(
                        namespace: "unread-preview-message",
                        key: "\(sender.id.uuidString)-\(messageIndex)"
                    ),
                    conversationID: sender.id,
                    type: .text,
                    sender: sender,
                    body: text,
                    detail: "未回消息预览",
                    isOutgoing: false,
                    presentation: messageIndex == 0 ? .avatarOnly : .bare,
                    timestamp: "",
                    sentAt: now.addingTimeInterval(TimeInterval(senderIndex * 180 + messageIndex * 24)),
                    recipientAccountID: recipientAccountID
                )
            }
        }
        return unreadPreviewMessages
    }

    func groupedUnreadMessagesForDisplay(_ messages: [ChatMessage]) -> [ChatMessage] {
        let messageByID = Dictionary(uniqueKeysWithValues: messages.map { ($0.id, $0) })
        let presentation = pendingReplyHomePresentation()
        var conversationOrder: [UUID] = []
        var seenConversationIDs = Set<UUID>()
        for message in messages where seenConversationIDs.insert(message.conversationID).inserted {
            conversationOrder.append(message.conversationID)
        }
        var itemsByConversation: [UUID: [UnansweredItem]] = [:]
        for item in presentation.items {
            guard item.messageIDs.contains(where: { messageByID[$0] != nil }) else { continue }
            itemsByConversation[item.conversationID, default: []].append(item)
        }
        let messageRankByID = Dictionary(
            uniqueKeysWithValues: messages.enumerated().map { ($0.element.id, $0.offset) }
        )
        return conversationOrder.flatMap { conversationID in
            let orderedItems = (itemsByConversation[conversationID] ?? []).sorted { lhs, rhs in
                let lhsRank = lhs.messageIDs.compactMap { messageRankByID[$0] }.min() ?? Int.max
                let rhsRank = rhs.messageIDs.compactMap { messageRankByID[$0] }.min() ?? Int.max
                if lhsRank == rhsRank {
                    return lhs.id.discriminator < rhs.id.discriminator
                }
                return lhsRank < rhsRank
            }
            return orderedItems.flatMap { item in
                var displayed = item.messageIDs
                    .compactMap { messageByID[$0] }
                    .sorted(by: messageSortAscending)
                if let draft = inlineAutoReplyDraft(for: item) {
                    displayed.append(draft)
                }
                return displayed
            }
        }
    }

    func inlineAutoReplyDraft(for item: UnansweredItem) -> ChatMessage? {
        guard item.id == selectedUnansweredItemID,
              let target = selectedUnansweredSendTarget(),
              target.item.id == item.id
        else { return nil }
        let sourceMessageIDs = Set(item.messageIDs)
        return state.messages.reversed().first { message in
            guard isAutoReplyPrediction(message),
                  autoReplyReceiverAccountID(from: message) == target.accountID,
                  let sourceMessageID = autoReplySourceMessageID(from: message)
            else { return false }
            return sourceMessageIDs.contains(sourceMessageID)
        }
    }

    func unansweredItemForDisplayedMessage(
        _ message: ChatMessage,
        presentation: PendingReplyPresentation? = nil
    ) -> UnansweredItem? {
        let resolvedPresentation = presentation ?? pendingReplyHomePresentation()
        if let item = resolvedPresentation.item(forMessageID: message.id) {
            return item
        }
        guard isAutoReplyPrediction(message),
              let sourceMessageID = autoReplySourceMessageID(from: message)
        else { return nil }
        return resolvedPresentation.item(forMessageID: sourceMessageID)
    }

    func isHomeReplyRelevantMessage(_ message: ChatMessage) -> Bool {
        guard !message.detail.contains("OpenAPI同步预览"),
              !isAutoReplyPrediction(message),
              !message.isAI,
              !message.sender.isAIAccount
        else { return false }
        if message.isOutgoing,
           shouldAttemptOpenApiSend(message),
           openApiSendContext(
                for: message,
                allowsCurrentSelectionFallback: false
           ) != nil {
            return latestOpenApiSendState(for: message) == .succeeded
        }
        switch message.type {
        case .system, .groupNotice:
            return false
        default:
            return true
        }
    }

    func isIncomingHomeReplyMessage(_ message: ChatMessage) -> Bool {
        let currentAccountIDs = Set(state.currentUsers.map(\.id))
        return !message.isOutgoing
            && !message.sender.isCurrentUser
            && !currentAccountIDs.contains(message.sender.id)
    }

    func messageSortAscending(_ lhs: ChatMessage, _ rhs: ChatMessage) -> Bool {
        if lhs.sentAt == rhs.sentAt {
            return lhs.id.uuidString < rhs.id.uuidString
        }
        return lhs.sentAt < rhs.sentAt
    }

    func canRenderMessageForSelectedAccount(_ message: ChatMessage) -> Bool {
        guard message.detail.hasPrefix("AI自动回复预测") else { return true }
        let receiverID = message.recipientAccountID ?? message.detail
            .components(separatedBy: .newlines)
            .first { $0.hasPrefix("接收帐号ID：") }
            .map { String($0.dropFirst("接收帐号ID：".count)).trimmingCharacters(in: .whitespacesAndNewlines) }
            .flatMap(UUID.init(uuidString:))
        return receiverID.map { $0 == state.selectedAccountID } ?? true
    }

    func renderedMessage(at indexPath: IndexPath) -> ChatMessage? {
        renderedMessages[safe: indexPath.item]
    }

    func renderedIndex(of messageID: UUID) -> Int? {
        renderedMessages.firstIndex { $0.id == messageID }
    }

    func renderedIndexPath(of messageID: UUID) -> IndexPath? {
        guard let index = renderedIndex(of: messageID) else { return nil }
        return IndexPath(item: index, section: 0)
    }

    func reloadRenderedMessageItem(messageID: UUID, updatesConnections: Bool = true) {
        let messages = renderedMessages
        guard messageCollectionView.numberOfItems(inSection: 0) == messages.count,
              let index = messages.firstIndex(where: { $0.id == messageID })
        else {
            messageCollectionView.reloadData()
            if updatesConnections {
                scheduleConnectionUpdate()
            }
            return
        }
        messageCollectionView.reloadItems(at: [IndexPath(item: index, section: 0)])
        if updatesConnections {
            scheduleConnectionUpdate()
        }
    }

    func insertRenderedMessageItemIfPossible(
        messageID: UUID,
        previousMessageIDs: [UUID],
        scrollToInsertedMessage: Bool
    ) {
        let currentMessageIDs = renderedMessages.map(\.id)
        guard messageCollectionView.numberOfItems(inSection: 0) == previousMessageIDs.count,
              currentMessageIDs.count == previousMessageIDs.count + 1,
              let insertedIndex = currentMessageIDs.firstIndex(of: messageID),
              currentMessageIDs.enumerated().compactMap({ index, id in
                  index == insertedIndex ? nil : id
              }) == previousMessageIDs
        else {
            messageCollectionView.reloadData()
            if scrollToInsertedMessage, let indexPath = renderedIndexPath(of: messageID) {
                messageCollectionView.scrollToItem(at: indexPath, at: .bottom, animated: true)
            }
            scheduleConnectionUpdate()
            return
        }

        let indexPath = IndexPath(item: insertedIndex, section: 0)
        messageCollectionView.performBatchUpdates {
            messageCollectionView.insertItems(at: [indexPath])
        } completion: { [weak self] _ in
            guard let self else { return }
            if scrollToInsertedMessage,
               indexPath.item < self.messageCollectionView.numberOfItems(inSection: 0) {
                self.messageCollectionView.scrollToItem(
                    at: indexPath,
                    at: .bottom,
                    animated: true
                )
            }
            self.scheduleConnectionUpdate()
        }
    }

    func reloadVisibleRightToolItems() {
        let currentItemCount = visibleRightToolItems().count
        guard rightToolCollectionView.numberOfItems(inSection: 0) == currentItemCount else {
            rightToolCollectionView.reloadData()
            return
        }
        let visibleIndexPaths = rightToolCollectionView.indexPathsForVisibleItems
            .filter { $0.item < currentItemCount }
        guard !visibleIndexPaths.isEmpty else { return }
        rightToolCollectionView.reloadItems(at: visibleIndexPaths)
    }

    func reloadRightAccountItems(accountIDs: Set<UUID>) {
        guard !accountIDs.isEmpty else { return }
        let items = visibleRightAccountItems()
        guard rightAccountCollectionView.numberOfItems(inSection: 0) == items.count else {
            rightAccountCollectionView.reloadData()
            return
        }
        let indexPaths = items.enumerated().compactMap { index, item -> IndexPath? in
            guard let participantID = item.participantID,
                  accountIDs.contains(participantID)
            else { return nil }
            return IndexPath(item: index, section: 0)
        }
        guard !indexPaths.isEmpty else { return }
        rightAccountCollectionView.reloadItems(at: indexPaths)
    }

    func lastRenderedMessageIndexPath() -> IndexPath? {
        let count = renderedMessages.count
        guard count > 0 else { return nil }
        return IndexPath(item: count - 1, section: 0)
    }

    func resetHomeMessageWindow() {
        unreadFoldExpansionGeneration &+= 1
        isUnreadFoldExpansionInProgress = false
        unreadFoldLockedContentOffsetY = nil
        unreadFoldPostExpansionAnchor = nil
        needsUnreadTimelineRefreshAfterExpansion = false
        deferredOpenApiIMSnapshotAfterUnreadExpansion = nil
        shouldPersistDeferredOpenApiIMSnapshot = true
        homeRenderedMessageLimit = homeInitialMessageLimit
        unreadDirectVisibleLimitByConversationID.removeAll(keepingCapacity: true)
        expandedUnreadDirectKeys.removeAll(keepingCapacity: true)
        unreadGroupVisibleLimitByConversationID.removeAll(keepingCapacity: true)
        expandedUnreadConversationIDs.removeAll(keepingCapacity: true)
        pendingReplyPresentationCacheKey = ""
        pendingReplyPresentationCache = nil
    }

    func invalidateVisibleDataCaches(invalidatePendingReplyIndex: Bool = true) {
        visibleDataRevision &+= 1
        visibleLeftItemsCacheKey = ""
        visibleLeftItemsCache = []
        baseVisibleLeftItemsCacheKey = ""
        baseVisibleLeftItemsCache = []
        visibleLeftIndexCacheKey = ""
        visibleLeftIndexByParticipantID = [:]
        visibleRightAccountItemsCacheKey = ""
        visibleRightAccountItemsCache = []
        participantLookupCacheKey = ""
        participantLookupCache = [:]
        groupConversationMetadataCacheRevision = -1
        groupConversationIDsCache = []
        inferredGroupMembersCache = [:]
        resolvedGroupMembersCache = [:]
        latestSidebarMessagesCacheKey = ""
        latestSidebarMessagesCache = [:]
        latestSidebarSummaryCacheKey = ""
        latestSidebarSummaryCache = [:]
        renderedMessagesCacheKey = ""
        renderedMessagesCache = []
        renderedMessagesByConversationCacheKey = ""
        renderedMessagesByConversationCache = [:]
        unreadTimelineLayoutCacheKey = ""
        unreadTimelineLayoutCache = nil
        pendingReplyPresentationCacheKey = ""
        pendingReplyPresentationCache = nil
        if invalidatePendingReplyIndex {
            pendingReplyIndex.invalidate()
            requestPendingReplyIndexRebuild()
        }
    }

    func invalidateSelectionVisibleDataCaches(
        invalidatePendingPresentation: Bool = true
    ) {
        visibleLeftItemsCacheKey = ""
        visibleLeftItemsCache = []
        visibleLeftIndexCacheKey = ""
        visibleLeftIndexByParticipantID = [:]
        visibleRightAccountItemsCacheKey = ""
        visibleRightAccountItemsCache = []
        renderedMessagesCacheKey = ""
        renderedMessagesCache = []
        renderedMessagesByConversationCacheKey = ""
        renderedMessagesByConversationCache = [:]
        unreadTimelineLayoutCacheKey = ""
        unreadTimelineLayoutCache = nil
        if invalidatePendingPresentation {
            pendingReplyPresentationCacheKey = ""
            pendingReplyPresentationCache = nil
        }
    }

    func invalidateHomePaginationCaches() {
        visibleLeftItemsCacheKey = ""
        visibleLeftItemsCache = []
        visibleLeftIndexCacheKey = ""
        visibleLeftIndexByParticipantID = [:]
        renderedMessagesCacheKey = ""
        renderedMessagesCache = []
        renderedMessagesByConversationCacheKey = ""
        renderedMessagesByConversationCache = [:]
        unreadTimelineLayoutCacheKey = ""
        unreadTimelineLayoutCache = nil
    }

    func invalidateUnansweredCompletionCaches() {
        visibleLeftItemsCacheKey = ""
        visibleLeftItemsCache = []
        visibleLeftIndexCacheKey = ""
        visibleLeftIndexByParticipantID = [:]
        visibleRightAccountItemsCacheKey = ""
        visibleRightAccountItemsCache = []
        renderedMessagesCacheKey = ""
        renderedMessagesCache = []
        renderedMessagesByConversationCacheKey = ""
        renderedMessagesByConversationCache = [:]
        unreadTimelineLayoutCacheKey = ""
        unreadTimelineLayoutCache = nil
        pendingReplyPresentationCacheKey = ""
        pendingReplyPresentationCache = nil
    }

    func renderedMessagesByConversation() -> [UUID: [ChatMessage]] {
        let cacheKey = renderedMessagesCurrentCacheKey()
        if cacheKey == renderedMessagesByConversationCacheKey {
            return renderedMessagesByConversationCache
        }
        let grouped = Dictionary(grouping: renderedMessages, by: \.conversationID)
        renderedMessagesByConversationCacheKey = cacheKey
        renderedMessagesByConversationCache = grouped
        return grouped
    }

    func primeVisibleLeftItemsCache(_ items: [SidebarItem], activeParticipantID: UUID?) {
        visibleLeftItemsCacheKey = visibleLeftItemsCurrentCacheKey()
        visibleLeftItemsCache = items.map { item in
            SidebarItem(
                id: item.id,
                kind: item.kind,
                title: item.title,
                symbolName: item.symbolName,
                tintColor: item.tintColor,
                participantID: item.participantID,
                targetMessageID: item.targetMessageID,
                isActive: item.participantID == activeParticipantID,
                toolMessageType: item.toolMessageType,
                customTool: item.customTool,
                avatarURL: item.avatarURL,
                isOnline: item.isOnline,
                compositeAvatarTitles: item.compositeAvatarTitles
            )
        }
    }

    func loadOlderHomeMessagesIfNeeded() {
        guard isHomeTimeline,
              !isUnreadFoldExpansionInProgress,
              !isHomePaginationInProgress,
              !isHomePaginationScheduled,
              messageCollectionView.contentOffset.y < 160,
              homeRenderedMessageLimit < homeRenderableMessageCount()
        else { return }
        // Keep pagination construction out of the active UIScrollView callback.
        // Deferring one run-loop turn lets UIKit commit the finger-driven frame
        // first and also coalesces duplicate callbacks from the linked rails.
        isHomePaginationScheduled = true
        DispatchQueue.main.async { [weak self] in
            guard let self else { return }
            self.isHomePaginationScheduled = false
            self.performOlderHomeMessageLoadIfNeeded()
        }
    }

    func performOlderHomeMessageLoadIfNeeded() {
        guard isHomeTimeline,
              !isUnreadFoldExpansionInProgress,
              !isHomePaginationInProgress,
              messageCollectionView.contentOffset.y < 160,
              homeRenderedMessageLimit < homeRenderableMessageCount()
        else { return }

        let signpostID = PerformanceSignpost.begin("UnreadTopPagination")
        isHomePaginationInProgress = true
        let oldMessages = renderedMessages
        let oldLeftItems = visibleLeftItemsForSelectedAccount()
        let oldSnapshot = unreadTimelineLayoutSnapshot()
        let oldContentHeight = oldSnapshot?.contentHeight
            ?? messageCollectionView.collectionViewLayout.collectionViewContentSize.height
        let oldOffsetY = messageCollectionView.contentOffset.y
        let paginationAnchor = oldSnapshot.flatMap {
            UnreadTimelinePaginationAnchor.capture(
                messages: oldMessages,
                snapshot: $0,
                contentOffsetY: oldOffsetY
            )
        }
        homeRenderedMessageLimit = min(
            homeRenderedMessageLimit + homeMessagePageSize,
            homeRenderableMessageCount()
        )
        // Pagination changes only the visible window. Keep the pending-reply
        // index and aggregation cache intact so scrolling never rescans the
        // complete message store on the main thread.
        invalidateHomePaginationCaches()
        let newMessages = renderedMessages
        let newLeftItems = visibleLeftItemsForSelectedAccount()
        let newSnapshot = unreadTimelineLayoutSnapshot()
        let newContentHeight = newSnapshot?.contentHeight ?? oldContentHeight

        var pendingCompletions = 2
        let completeBatch: () -> Void = { [weak self] in
            guard let self else { return }
            pendingCompletions -= 1
            guard pendingCompletions == 0 else { return }
            let minimumOffsetY = -self.messageCollectionView.adjustedContentInset.top
            let adjustedY = paginationAnchor.flatMap { anchor in
                newSnapshot.flatMap {
                    anchor.restoredContentOffsetY(
                        messages: newMessages,
                        snapshot: $0,
                        minimumOffsetY: minimumOffsetY
                    )
                }
            } ?? max(minimumOffsetY, oldOffsetY + newContentHeight - oldContentHeight)
            self.messageCollectionView.setContentOffset(
                CGPoint(x: self.messageCollectionView.contentOffset.x, y: adjustedY),
                animated: false
            )
            self.syncUnreadTimelineScroll(from: self.messageCollectionView)
            self.isHomePaginationInProgress = false
            self.scheduleConnectionUpdate()
            self.scheduleConversationHeightPrewarming()
            PerformanceSignpost.end("UnreadTopPagination", id: signpostID)
        }

        applyCollectionDifference(
            in: messageCollectionView,
            oldIDs: oldMessages.map(\.id),
            newIDs: newMessages.map(\.id),
            completion: completeBatch
        )
        applyCollectionDifference(
            in: leftCollectionView,
            oldIDs: oldLeftItems.map { $0.participantID ?? $0.id },
            newIDs: newLeftItems.map { $0.participantID ?? $0.id },
            invalidatesLayout: true,
            completion: completeBatch
        )
    }

    func applyCollectionDifference<ID: Hashable>(
        in collectionView: UICollectionView,
        oldIDs: [ID],
        newIDs: [ID],
        invalidatesLayout: Bool = false,
        completion: @escaping () -> Void
    ) {
        let difference = newIDs.difference(from: oldIDs).inferringMoves()
        guard !difference.isEmpty else {
            if invalidatesLayout {
                collectionView.collectionViewLayout.invalidateLayout()
            }
            completion()
            return
        }

        UIView.performWithoutAnimation {
            collectionView.performBatchUpdates {
                for change in difference {
                    switch change {
                    case .remove(let offset, _, let associatedOffset):
                        let source = IndexPath(item: offset, section: 0)
                        if let destinationOffset = associatedOffset {
                            collectionView.moveItem(
                                at: source,
                                to: IndexPath(item: destinationOffset, section: 0)
                            )
                        } else {
                            collectionView.deleteItems(at: [source])
                        }
                    case .insert(let offset, _, let associatedOffset):
                        guard associatedOffset == nil else { continue }
                        collectionView.insertItems(
                            at: [IndexPath(item: offset, section: 0)]
                        )
                    }
                }
                if invalidatesLayout {
                    collectionView.collectionViewLayout.invalidateLayout()
                }
            } completion: { _ in
                completion()
            }
        }
    }

    func applyUnreadTimelineFilterTransition(
        oldMessages: [ChatMessage],
        oldLeftItems: [SidebarItem],
        completion: @escaping () -> Void
    ) {
        let newMessages = renderedMessages
        let newLeftItems = visibleLeftItemsForSelectedAccount()
        var pendingCompletions = 2
        let completeBatch: () -> Void = { [weak self] in
            guard let self else { return }
            pendingCompletions -= 1
            guard pendingCompletions == 0 else { return }
            UIView.performWithoutAnimation {
                self.reloadVisibleItems(
                    in: self.messageCollectionView,
                    expectedItemCount: newMessages.count
                )
                self.reloadVisibleItems(
                    in: self.leftCollectionView,
                    expectedItemCount: newLeftItems.count
                )
            }
            completion()
        }

        applyCollectionDifference(
            in: messageCollectionView,
            oldIDs: oldMessages.map(\.id),
            newIDs: newMessages.map(\.id),
            invalidatesLayout: true,
            completion: completeBatch
        )
        applyCollectionDifference(
            in: leftCollectionView,
            oldIDs: oldLeftItems.map { $0.participantID ?? $0.id },
            newIDs: newLeftItems.map { $0.participantID ?? $0.id },
            invalidatesLayout: true,
            completion: completeBatch
        )
    }

    func reloadVisibleItems(in collectionView: UICollectionView, expectedItemCount: Int) {
        guard collectionView.numberOfItems(inSection: 0) == expectedItemCount else {
            collectionView.reloadData()
            return
        }
        let visibleIndexPaths = collectionView.indexPathsForVisibleItems.filter {
            $0.item < expectedItemCount
        }
        guard !visibleIndexPaths.isEmpty else { return }
        collectionView.reloadItems(at: visibleIndexPaths)
    }

    func heightCacheKey(
        for message: ChatMessage,
        width: CGFloat,
        showsGroupAvatar: Bool,
        showsGroupName: Bool,
        usesUnansweredPresentation: Bool = false
    ) -> MessageHeightCacheKey {
        MessageHeightCacheKey(
            messageID: message.id,
            messageHash: message.layoutContentHash,
            width: Int(width.rounded(.toNearestOrAwayFromZero)),
            showsGroupAvatar: showsGroupAvatar,
            showsGroupName: showsGroupName,
            usesUnansweredPresentation: usesUnansweredPresentation,
            deliveryStatusText: openApiDeliveryStatusText(for: message)
        )
    }

    func cachedHeight(
        for message: ChatMessage,
        width: CGFloat,
        showsGroupAvatar: Bool,
        showsGroupName: Bool,
        usesUnansweredPresentation: Bool = false
    ) -> CGFloat {
        let key = heightCacheKey(
            for: message,
            width: width,
            showsGroupAvatar: showsGroupAvatar,
            showsGroupName: showsGroupName,
            usesUnansweredPresentation: usesUnansweredPresentation
        )
        if let cached = messageHeightCache[key] {
            return cached
        }
        let height = AppKits.registry.messageRenderKit.estimatedMessageHeight(
            message,
            width: width,
            showsGroupAvatar: showsGroupAvatar,
            showsGroupName: showsGroupName,
            usesUnansweredPresentation: usesUnansweredPresentation,
            deliveryStatusText: openApiDeliveryStatusText(for: message)
        )
        storeMessageHeight(height, for: key)
        return height
    }

    func storeMessageHeight(_ height: CGFloat, for key: MessageHeightCacheKey) {
        guard messageHeightCache[key] == nil else { return }
        if messageHeightCache.count >= maximumMessageHeightCacheEntries {
            let evictionCount = max(1, maximumMessageHeightCacheEntries / 4)
            for cachedKey in messageHeightCacheInsertionOrder.prefix(evictionCount) {
                messageHeightCache.removeValue(forKey: cachedKey)
            }
            messageHeightCacheInsertionOrder.removeFirst(
                min(evictionCount, messageHeightCacheInsertionOrder.count)
            )
        }
        messageHeightCache[key] = height
        messageHeightCacheInsertionOrder.append(key)
    }

    func clearMessageHeightCache(keepingCapacity: Bool = true) {
        messageHeightCache.removeAll(keepingCapacity: keepingCapacity)
        messageHeightCacheInsertionOrder.removeAll(keepingCapacity: keepingCapacity)
    }

    func cancelConversationHeightPrewarming() {
        conversationHeightPrewarmGeneration &+= 1
    }

    func scheduleConversationHeightPrewarming() {
        guard isViewLoaded,
              messageCollectionView.bounds.width > 0,
              !state.messages.isEmpty
        else { return }

        conversationHeightPrewarmGeneration &+= 1
        let generation = conversationHeightPrewarmGeneration
        let messages: [ChatMessage]
        if isHomeTimeline {
            let prewarmLimit = min(
                homeRenderedMessageLimit + homeMessagePageSize,
                homeRenderableMessageCount()
            )
            messages = groupedUnreadMessagesForDisplay(
                pendingReplyHomeMessages(limit: prewarmLimit) + makeUnreadPreviewMessages()
            )
        } else {
            messages = renderedMessages
        }
        let width = messageCollectionView.bounds.width
        let inputs = messageHeightPrewarmInputs(messages: messages, width: width)
        guard !inputs.isEmpty else { return }
        messageHeightPrewarmQueue.async { [weak self] in
            let results = Self.measureMessageHeightPrewarmInputs(inputs)
            DispatchQueue.main.async { [weak self] in
                guard let self,
                      generation == self.conversationHeightPrewarmGeneration,
                      abs(self.messageCollectionView.bounds.width - width) < 0.5
                else { return }
                for (key, height) in results {
                    self.storeMessageHeight(height, for: key)
                }
            }
        }
    }

    func prepareCurrentMessageHeightsForReload(
        completion: @escaping () -> Void
    ) {
        guard isViewLoaded, messageCollectionView.bounds.width > 0 else {
            DispatchQueue.main.async(execute: completion)
            return
        }
        conversationHeightPrewarmGeneration &+= 1
        let generation = conversationHeightPrewarmGeneration
        let width = messageCollectionView.bounds.width
        let inputs = messageHeightPrewarmInputs(messages: renderedMessages, width: width)
        guard !inputs.isEmpty else {
            DispatchQueue.main.async(execute: completion)
            return
        }
        messageHeightPrewarmQueue.async { [weak self] in
            let results = Self.measureMessageHeightPrewarmInputs(inputs)
            DispatchQueue.main.async { [weak self] in
                guard let self else {
                    completion()
                    return
                }
                if generation == self.conversationHeightPrewarmGeneration,
                   abs(self.messageCollectionView.bounds.width - width) < 0.5 {
                    for (key, height) in results {
                        self.storeMessageHeight(height, for: key)
                    }
                }
                completion()
            }
        }
    }

    func messageHeightPrewarmInputs(
        messages: [ChatMessage],
        width: CGFloat
    ) -> [MessageHeightPrewarmInput] {
        messages.compactMap { message in
            let settings = isHomeTimeline
                ? GroupDisplaySettings(showsAvatar: false, showsName: true)
                : groupDisplaySettings(for: message)
            let deliveryStatusText = openApiDeliveryStatusText(for: message)
            let key = MessageHeightCacheKey(
                messageID: message.id,
                messageHash: message.layoutContentHash,
                width: Int(width.rounded(.toNearestOrAwayFromZero)),
                showsGroupAvatar: settings.showsAvatar,
                showsGroupName: settings.showsName,
                usesUnansweredPresentation: isHomeTimeline,
                deliveryStatusText: deliveryStatusText
            )
            guard messageHeightCache[key] == nil else { return nil }
            return MessageHeightPrewarmInput(
                key: key,
                message: message,
                width: width,
                showsGroupAvatar: settings.showsAvatar,
                showsGroupName: settings.showsName,
                usesUnansweredPresentation: isHomeTimeline,
                deliveryStatusText: deliveryStatusText
            )
        }
    }

    static func measureMessageHeightPrewarmInputs(
        _ inputs: [MessageHeightPrewarmInput]
    ) -> [(MessageHeightCacheKey, CGFloat)] {
        inputs.map { input in
            let height = ChatMessageBubbleView.estimatedHeight(
                for: input.message,
                width: input.width,
                showsGroupAvatar: input.showsGroupAvatar,
                showsGroupName: input.showsGroupName,
                usesUnansweredPresentation: input.usesUnansweredPresentation,
                deliveryStatusText: input.deliveryStatusText
            )
            return (input.key, height)
        }
    }

    func setupChrome() {
        chromeView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(chromeView)
        localIslandIconView.tintColor = nil
        localIslandIconView.contentMode = .scaleAspectFit
        localIslandIconView.clipsToBounds = true
        localIslandIconView.layer.cornerRadius = 5
        localIslandIconView.layer.cornerCurve = .continuous
        localIslandIconView.alpha = 0
        localIslandIconView.isHidden = true
        localIslandIconView.translatesAutoresizingMaskIntoConstraints = false
        chromeView.addSubview(localIslandIconView)

        widthConstraint = chromeView.widthAnchor.constraint(equalTo: view.widthAnchor)
        heightConstraint = chromeView.heightAnchor.constraint(equalTo: view.heightAnchor)
        centerXConstraint = chromeView.centerXAnchor.constraint(equalTo: view.centerXAnchor)
        topConstraint = chromeView.topAnchor.constraint(equalTo: view.topAnchor)
        bottomConstraint = chromeView.bottomAnchor.constraint(equalTo: view.bottomAnchor)

        frameConstraints = [
            widthConstraint,
            heightConstraint,
            centerXConstraint,
            topConstraint,
            bottomConstraint
        ].compactMap { $0 }
        NSLayoutConstraint.activate(frameConstraints)
        NSLayoutConstraint.activate([
            localIslandIconView.centerXAnchor.constraint(equalTo: chromeView.centerXAnchor),
            localIslandIconView.centerYAnchor.constraint(equalTo: chromeView.centerYAnchor),
            localIslandIconView.widthAnchor.constraint(equalToConstant: 22),
            localIslandIconView.heightAnchor.constraint(equalToConstant: 22)
        ])

        chromeView.layer.cornerRadius = 0
        chromeView.layer.borderWidth = 0
        chromeView.layer.shadowOpacity = 0
        chromeView.visualEffectView.layer.cornerRadius = 0
        chromeView.resizeHandle.isHidden = true
        chromeView.closeButton.isHidden = true
        chromeView.closeButton.addTarget(self, action: #selector(minimizeToIsland), for: .touchUpInside)
    }

    @objc func editHeaderConversation() {
        guard let friend = state.activeFriend else { return }
        if isGroupConversation(friend) {
            presentGroupInfoPage(for: friend)
            return
        }
        guard activeDirectFriendForInfoHome() != nil else { return }
        presentActiveFriendInfoHomeFromSwipe()
        DispatchQueue.main.async { [weak self] in
            guard let self else { return }
            let presenter = self.navigationController?.topViewController ?? self
            self.presentRemarkEditor(for: friend, from: presenter)
        }
    }

    @objc func presentHeaderScanAddMenu() {
        let alert = UIAlertController(
            title: localized("扫一扫与加人", "Scan or add friend"),
            message: nil,
            preferredStyle: .actionSheet
        )
        alert.addAction(UIAlertAction(title: localized("添加好友", "Add friend"), style: .default) { [weak self] _ in
            self?.presentOpenApiFriendManagementPage()
        })
        alert.addAction(UIAlertAction(title: localized("扫一扫", "Scan"), style: .default) { [weak self] _ in
            self?.presentOpenApiFriendManagementPage()
        })
        alert.addAction(UIAlertAction(title: localized("取消", "Cancel"), style: .cancel))
        presentSheet(alert, sourceView: headerScanAddButton)
    }

    func setMessageHeaderCompact(_ compact: Bool, animated: Bool = true) {
        guard isOrdinarySingleConversation else {
            headerCompactResetWorkItem?.cancel()
            isMessageHeaderCompact = false
            headerNameButton.alpha = 1
            headerUnreadBadgeView.alpha = 1
            headerTitleStack.transform = .identity
            headerConversationEditButton.transform = .identity
            headerNameButton.transform = .identity
            return
        }
        guard isMessageHeaderCompact != compact else { return }
        isMessageHeaderCompact = compact
        let changes = {
            self.headerNameButton.alpha = compact ? 0 : 1
            self.headerUnreadBadgeView.alpha = compact ? 0 : 1
            self.headerTitleStack.transform = compact
                ? CGAffineTransform(translationX: -76, y: 0)
                : .identity
            self.headerConversationEditButton.transform = compact
                ? CGAffineTransform(translationX: -76, y: 0)
                : .identity
            self.headerNameButton.transform = compact
                ? CGAffineTransform(translationX: -76, y: 0)
                : .identity
        }
        if animated {
            UIView.animate(withDuration: 0.18, delay: 0, options: [.beginFromCurrentState, .curveEaseOut], animations: changes)
        } else {
            changes()
        }
    }

    var isOrdinarySingleConversation: Bool {
        guard !isHomeTimeline, !isAccountDirectory, let friend = state.activeFriend else { return false }
        return !isGroupConversation(friend) && !isGroupParticipant(friend)
    }

    func scheduleMessageHeaderRestore() {
        guard isOrdinarySingleConversation else { return }
        headerCompactResetWorkItem?.cancel()
        let workItem = DispatchWorkItem { [weak self] in
            DispatchQueue.main.async {
                self?.setMessageHeaderCompact(false)
            }
        }
        headerCompactResetWorkItem = workItem
        DispatchQueue.main.asyncAfter(deadline: .now() + 1, execute: workItem)
    }

    func setupHeader() {
        let contentView = chromeView.visualEffectView.contentView

        headerView.translatesAutoresizingMaskIntoConstraints = false
        contentView.addSubview(headerView)

        let headerIconConfiguration = UIImage.SymbolConfiguration(pointSize: 13, weight: .semibold)
        headerNameButton.setImage(
            UIImage(systemName: "chevron.left", withConfiguration: headerIconConfiguration),
            for: .normal
        )
        headerNameButton.tintColor = .white
        headerNameButton.setTitle(localized("返回", "Back"), for: .normal)
        headerNameButton.titleLabel?.font = .systemFont(ofSize: 15, weight: .semibold)
        headerNameButton.titleLabel?.shadowColor = UIColor.black.withAlphaComponent(0.28)
        headerNameButton.titleLabel?.shadowOffset = CGSize(width: 0, height: 1)
        headerNameButton.isHidden = false
        headerNameButton.contentHorizontalAlignment = .leading
        headerNameButton.semanticContentAttribute = .forceLeftToRight
        headerNameButton.addTarget(self, action: #selector(returnToUnreadMessages), for: .touchUpInside)
        headerNameButton.translatesAutoresizingMaskIntoConstraints = false

        headerConversationEditButton.setImage(
            UIImage(systemName: "square.and.pencil", withConfiguration: headerIconConfiguration),
            for: .normal
        )
        headerConversationEditButton.tintColor = UIColor.white.withAlphaComponent(0.9)
        headerConversationEditButton.accessibilityLabel = localized("编辑会话备注", "Edit conversation remark")
        headerConversationEditButton.addTarget(self, action: #selector(editHeaderConversation), for: .touchUpInside)
        headerConversationEditButton.translatesAutoresizingMaskIntoConstraints = false

        headerUnreadBadgeView.backgroundColor = .systemRed
        headerUnreadBadgeView.layer.cornerRadius = 4
        headerUnreadBadgeView.layer.borderWidth = 1.5
        headerUnreadBadgeView.layer.borderColor = UIColor.black.withAlphaComponent(0.7).cgColor
        headerUnreadBadgeView.isHidden = true
        headerUnreadBadgeView.isUserInteractionEnabled = false
        headerUnreadBadgeView.translatesAutoresizingMaskIntoConstraints = false

        headerScanAddButton.setImage(
            UIImage(systemName: "qrcode.viewfinder", withConfiguration: headerIconConfiguration),
            for: .normal
        )
        headerScanAddButton.tintColor = .white
        headerScanAddButton.accessibilityLabel = localized("扫一扫与添加好友", "Scan or add friend")
        headerScanAddButton.addTarget(self, action: #selector(presentHeaderScanAddMenu), for: .touchUpInside)
        headerScanAddButton.translatesAutoresizingMaskIntoConstraints = false

        accountButton.setTitle(nil, for: .normal)
        accountButton.setImage(
            UIImage(
                systemName: "person.2.fill",
                withConfiguration: UIImage.SymbolConfiguration(pointSize: 17, weight: .semibold)
            ),
            for: .normal
        )
        accountButton.tintColor = .white
        accountButton.backgroundColor = .clear
        accountButton.layer.cornerRadius = 0
        accountButton.layer.cornerCurve = .continuous
        accountButton.layer.borderWidth = 0
        accountButton.accessibilityLabel = localized("全部帐号", "All Accounts")
        accountButton.contentHorizontalAlignment = .right
        accountButton.semanticContentAttribute = .forceLeftToRight
        accountButton.addTarget(self, action: #selector(presentMyAccounts), for: .touchUpInside)
        accountButton.translatesAutoresizingMaskIntoConstraints = false


        globalSearchButton.setImage(
            UIImage(systemName: "magnifyingglass", withConfiguration: headerIconConfiguration),
            for: .normal
        )
        globalSearchButton.tintColor = .white
        globalSearchButton.backgroundColor = .clear
        globalSearchButton.layer.cornerRadius = 0
        globalSearchButton.layer.cornerCurve = .continuous
        globalSearchButton.layer.borderWidth = 0
        globalSearchButton.accessibilityLabel = "全局搜索"
        globalSearchButton.addTarget(self, action: #selector(presentGlobalSearchPage), for: .touchUpInside)
        globalSearchButton.translatesAutoresizingMaskIntoConstraints = false

        islandButton.setImage(UIImage(systemName: "arrow.down.to.line.compact"), for: .normal)
        islandButton.tintColor = .white
        islandButton.backgroundColor = UIColor.white.withAlphaComponent(0.22)
        islandButton.layer.cornerRadius = 18
        islandButton.accessibilityLabel = "\u{6700}\u{5c0f}\u{5316}\u{5230}\u{7075}\u{52a8}\u{5c9b}"
        islandButton.addTarget(self, action: #selector(minimizeToIsland), for: .touchUpInside)
        islandButton.isHidden = true
        islandButton.translatesAutoresizingMaskIntoConstraints = false

        titleLabel.text = nil
        titleLabel.isHidden = false
        titleLabel.textColor = UIColor.white.withAlphaComponent(0.9)
        titleLabel.font = .systemFont(ofSize: 15, weight: .semibold)
        titleLabel.textAlignment = .left
        titleLabel.lineBreakMode = .byTruncatingTail

        titleSubtitleLabel.text = nil
        titleSubtitleLabel.isHidden = true
        titleSubtitleLabel.textColor = UIColor.white.withAlphaComponent(0.68)
        titleSubtitleLabel.font = .systemFont(ofSize: 10, weight: .regular)
        titleSubtitleLabel.textAlignment = .left
        titleSubtitleLabel.lineBreakMode = .byTruncatingTail

        headerTitleStack.axis = .vertical
        headerTitleStack.alignment = .fill
        headerTitleStack.spacing = 0
        headerTitleStack.addArrangedSubview(titleLabel)
        headerTitleStack.addArrangedSubview(titleSubtitleLabel)
        headerTitleStack.translatesAutoresizingMaskIntoConstraints = false

        headerView.addSubview(islandButton)
        headerView.addSubview(headerNameButton)
        headerView.addSubview(headerUnreadBadgeView)
        headerView.addSubview(headerConversationEditButton)
        headerView.addSubview(headerScanAddButton)
        headerView.addSubview(globalSearchButton)
        headerView.addSubview(accountButton)
        headerView.addSubview(headerTitleStack)
        headerView.addSubview(chromeView.closeButton)

        NSLayoutConstraint.activate([
            headerView.topAnchor.constraint(equalTo: contentView.safeAreaLayoutGuide.topAnchor),
            headerView.leadingAnchor.constraint(equalTo: contentView.leadingAnchor),
            headerView.trailingAnchor.constraint(equalTo: contentView.trailingAnchor),
            headerView.heightAnchor.constraint(equalToConstant: 50),

            islandButton.leadingAnchor.constraint(equalTo: headerView.leadingAnchor, constant: 10),
            islandButton.centerYAnchor.constraint(equalTo: headerView.centerYAnchor, constant: 6),
            islandButton.widthAnchor.constraint(equalToConstant: 36),
            islandButton.heightAnchor.constraint(equalToConstant: 36),

            headerNameButton.leadingAnchor.constraint(equalTo: headerView.leadingAnchor, constant: 14),
            headerNameButton.centerYAnchor.constraint(equalTo: headerView.centerYAnchor),
            headerNameButton.heightAnchor.constraint(equalToConstant: 44),
            headerNameButton.widthAnchor.constraint(equalToConstant: 76),

            headerUnreadBadgeView.widthAnchor.constraint(equalToConstant: 8),
            headerUnreadBadgeView.heightAnchor.constraint(equalToConstant: 8),
            headerUnreadBadgeView.leadingAnchor.constraint(equalTo: headerNameButton.leadingAnchor, constant: 2),
            headerUnreadBadgeView.topAnchor.constraint(equalTo: headerNameButton.topAnchor, constant: 8),

            headerConversationEditButton.leadingAnchor.constraint(equalTo: headerTitleStack.trailingAnchor, constant: 3),
            headerConversationEditButton.centerYAnchor.constraint(equalTo: headerView.centerYAnchor),
            headerConversationEditButton.widthAnchor.constraint(equalToConstant: 30),
            headerConversationEditButton.heightAnchor.constraint(equalToConstant: 34),

            accountButton.centerYAnchor.constraint(equalTo: headerView.centerYAnchor),
            accountButton.heightAnchor.constraint(equalToConstant: 38),
            accountButton.widthAnchor.constraint(equalToConstant: 82),

            headerScanAddButton.centerYAnchor.constraint(equalTo: headerView.centerYAnchor),
            headerScanAddButton.widthAnchor.constraint(equalToConstant: 30),
            headerScanAddButton.heightAnchor.constraint(equalToConstant: 30),

            headerScanAddButton.trailingAnchor.constraint(equalTo: accountButton.leadingAnchor, constant: -3),
            accountButton.trailingAnchor.constraint(equalTo: globalSearchButton.leadingAnchor, constant: -4),

            globalSearchButton.centerYAnchor.constraint(equalTo: headerView.centerYAnchor),
            globalSearchButton.widthAnchor.constraint(equalToConstant: 30),
            globalSearchButton.heightAnchor.constraint(equalToConstant: 30),
            globalSearchButton.trailingAnchor.constraint(equalTo: headerView.trailingAnchor, constant: -10),

            headerTitleStack.leadingAnchor.constraint(equalTo: headerNameButton.trailingAnchor, constant: 7),
            headerTitleStack.centerYAnchor.constraint(equalTo: headerNameButton.centerYAnchor),
            headerTitleStack.trailingAnchor.constraint(lessThanOrEqualTo: headerView.trailingAnchor, constant: -150),

            chromeView.closeButton.trailingAnchor.constraint(equalTo: headerView.trailingAnchor, constant: -10),
            chromeView.closeButton.topAnchor.constraint(equalTo: headerView.topAnchor, constant: 4),
            chromeView.closeButton.widthAnchor.constraint(equalToConstant: 36),
            chromeView.closeButton.heightAnchor.constraint(equalToConstant: 36)
        ])
    }

    func setupCollections() {
        let contentView = chromeView.visualEffectView.contentView

        [leftCollectionView, messageCollectionView, rightAccountCollectionView, rightToolCollectionView].forEach {
            $0.backgroundColor = .clear
            $0.showsVerticalScrollIndicator = false
            $0.showsHorizontalScrollIndicator = false
            $0.translatesAutoresizingMaskIntoConstraints = false
            $0.delegate = self
            $0.dataSource = self
            $0.register(SidebarCell.self, forCellWithReuseIdentifier: SidebarCell.reuseIdentifier)
            $0.contentInsetAdjustmentBehavior = .never
        }
        ChatMessageCell.reuseIdentifiers.forEach { reuseIdentifier in
            messageCollectionView.register(
                ChatMessageCell.self,
                forCellWithReuseIdentifier: reuseIdentifier
            )
        }
        messageCollectionView.showsVerticalScrollIndicator = false
        messageCollectionView.alwaysBounceVertical = true
        messageCollectionView.alwaysBounceHorizontal = false
        messageCollectionView.isDirectionalLockEnabled = true
        messageCollectionView.keyboardDismissMode = .onDrag
        messageCollectionView.contentInset = UIEdgeInsets(top: 0, left: 0, bottom: 14, right: 0)
        messageCollectionView.dragDelegate = self
        messageCollectionView.dragInteractionEnabled = true
        messageCollectionView.prefetchDataSource = self
        messageCollectionView.backgroundView = unansweredEmptyStateLabel
        leftCollectionView.prefetchDataSource = self
        rightAccountCollectionView.prefetchDataSource = self
        // The account and tool rails share one vertical region. When the tool
        // rail expands, account cells outside the compressed account bounds
        // must not render over the tool list.
        rightAccountCollectionView.clipsToBounds = true
        leftCollectionView.dropDelegate = self
        leftCollectionView.alwaysBounceVertical = true
        if let rightAccountLayout = rightAccountCollectionView.collectionViewLayout as? UICollectionViewFlowLayout {
            rightAccountLayout.sectionInset.top = 6
            rightAccountLayout.sectionInset.left = 0
            rightAccountLayout.sectionInset.right = 0
        }

        let contentContainer = UIView()
        contentContainer.translatesAutoresizingMaskIntoConstraints = false
        contentContainer.clipsToBounds = true
        contentView.addSubview(contentContainer)
        contentContainer.addSubview(connectionOverlay)
        contentContainer.addSubview(leftCollectionView)
        contentContainer.addSubview(messageCollectionView)
        contentContainer.addSubview(rightRailContainer)
        connectionOverlay.layer.zPosition = 0
        leftCollectionView.layer.zPosition = 1
        messageCollectionView.layer.zPosition = 1
        rightRailContainer.layer.zPosition = 1
        rightRailContainer.translatesAutoresizingMaskIntoConstraints = false
        rightRailContainer.addSubview(rightAccountCollectionView)
        rightRailContainer.addSubview(aiffButton)
        rightRailContainer.addSubview(rightToolCollectionView)
        contentContainer.addSubview(leftScrollPreviewView)
        contentContainer.addSubview(leftFriendTotalBadgeView)
        contentContainer.addSubview(selectedLeftSidebarFloatingView)
        contentContainer.addSubview(leftEdgeFeedbackView)
        contentContainer.addSubview(rightEdgeDouyinFeedbackView)
        leftScrollPreviewView.layer.zPosition = 4
        leftFriendTotalBadgeView.layer.zPosition = 6
        selectedLeftSidebarFloatingView.layer.zPosition = 5
        connectionOverlay.translatesAutoresizingMaskIntoConstraints = false
        leftFriendTotalBadgeView.translatesAutoresizingMaskIntoConstraints = false
        selectedLeftSidebarFloatingView.translatesAutoresizingMaskIntoConstraints = false
        selectedLeftSidebarFloatingView.isHidden = true
        selectedLeftSidebarFloatingView.alpha = 0
        selectedLeftSidebarFloatingView.isUserInteractionEnabled = false
        setupLeftScrollPreviewView()
        setupLeftFriendTotalBadge()
        setupRightEdgeDouyinFeedbackView()
        setupLeftEdgeFeedbackView()
        setupAiffButton()
        setupConversationProfileAreaGesture(in: contentContainer)

        accountRailHeightConstraint = rightAccountCollectionView.heightAnchor.constraint(
            equalToConstant: rightAccountListHeight(forVisibleItems: minimumRightAccountItemsVisible)
        )
        selectedLeftSidebarFloatingTopConstraint = selectedLeftSidebarFloatingView.topAnchor.constraint(
            equalTo: leftCollectionView.topAnchor,
            constant: 2
        )

        contentBottomConstraint = contentContainer.bottomAnchor.constraint(equalTo: quotePreviewBar.topAnchor)

        NSLayoutConstraint.activate([
            contentContainer.topAnchor.constraint(equalTo: headerView.bottomAnchor),
            contentContainer.leadingAnchor.constraint(equalTo: contentView.leadingAnchor),
            contentContainer.trailingAnchor.constraint(equalTo: contentView.trailingAnchor),
            contentBottomConstraint!,

            leftCollectionView.topAnchor.constraint(equalTo: contentContainer.topAnchor),
            leftCollectionView.leadingAnchor.constraint(equalTo: contentContainer.leadingAnchor, constant: 2),
            leftCollectionView.bottomAnchor.constraint(equalTo: contentContainer.bottomAnchor),
            leftCollectionView.widthAnchor.constraint(equalToConstant: Self.sidebarRailWidth),

            rightRailContainer.topAnchor.constraint(equalTo: contentContainer.topAnchor),
            rightRailContainer.trailingAnchor.constraint(equalTo: contentContainer.trailingAnchor, constant: -2),
            rightRailContainer.bottomAnchor.constraint(equalTo: contentContainer.bottomAnchor),
            rightRailContainer.widthAnchor.constraint(equalToConstant: Self.sidebarRailWidth),

            messageCollectionView.topAnchor.constraint(equalTo: contentContainer.topAnchor),
            messageCollectionView.leadingAnchor.constraint(equalTo: leftCollectionView.trailingAnchor),
            messageCollectionView.trailingAnchor.constraint(equalTo: rightRailContainer.leadingAnchor),
            messageCollectionView.bottomAnchor.constraint(equalTo: contentContainer.bottomAnchor),

            rightAccountCollectionView.topAnchor.constraint(equalTo: rightRailContainer.topAnchor),
            rightAccountCollectionView.leadingAnchor.constraint(equalTo: rightRailContainer.leadingAnchor),
            rightAccountCollectionView.trailingAnchor.constraint(equalTo: rightRailContainer.trailingAnchor),
            accountRailHeightConstraint!,

            aiffButton.topAnchor.constraint(equalTo: rightAccountCollectionView.bottomAnchor, constant: 2),
            aiffButton.leadingAnchor.constraint(equalTo: rightRailContainer.leadingAnchor, constant: 4),
            aiffButton.trailingAnchor.constraint(equalTo: rightRailContainer.trailingAnchor, constant: -4),
            aiffButton.heightAnchor.constraint(equalToConstant: 36),

            rightToolCollectionView.topAnchor.constraint(equalTo: aiffButton.bottomAnchor, constant: 2),
            rightToolCollectionView.leadingAnchor.constraint(equalTo: rightRailContainer.leadingAnchor),
            rightToolCollectionView.trailingAnchor.constraint(equalTo: rightRailContainer.trailingAnchor),
            rightToolCollectionView.bottomAnchor.constraint(equalTo: rightRailContainer.bottomAnchor),

            connectionOverlay.topAnchor.constraint(equalTo: contentContainer.topAnchor),
            connectionOverlay.leadingAnchor.constraint(equalTo: contentContainer.leadingAnchor),
            connectionOverlay.trailingAnchor.constraint(equalTo: contentContainer.trailingAnchor),
            connectionOverlay.bottomAnchor.constraint(equalTo: contentContainer.bottomAnchor),

            rightEdgeDouyinFeedbackView.topAnchor.constraint(equalTo: contentContainer.topAnchor),
            rightEdgeDouyinFeedbackView.leadingAnchor.constraint(equalTo: contentContainer.leadingAnchor),
            rightEdgeDouyinFeedbackView.trailingAnchor.constraint(equalTo: contentContainer.trailingAnchor),
            rightEdgeDouyinFeedbackView.bottomAnchor.constraint(equalTo: contentContainer.bottomAnchor),

            leftEdgeFeedbackView.topAnchor.constraint(equalTo: contentContainer.topAnchor),
            leftEdgeFeedbackView.leadingAnchor.constraint(equalTo: contentContainer.leadingAnchor),
            leftEdgeFeedbackView.trailingAnchor.constraint(equalTo: contentContainer.trailingAnchor),
            leftEdgeFeedbackView.bottomAnchor.constraint(equalTo: contentContainer.bottomAnchor),

            leftFriendTotalBadgeView.topAnchor.constraint(equalTo: leftCollectionView.topAnchor, constant: 4),
            leftFriendTotalBadgeView.centerXAnchor.constraint(equalTo: leftCollectionView.centerXAnchor),
            leftFriendTotalBadgeView.widthAnchor.constraint(equalToConstant: Self.sidebarItemSize.width),
            leftFriendTotalBadgeView.heightAnchor.constraint(equalToConstant: 30),

            selectedLeftSidebarFloatingView.leadingAnchor.constraint(equalTo: leftCollectionView.leadingAnchor, constant: 1),
            selectedLeftSidebarFloatingView.widthAnchor.constraint(equalToConstant: Self.sidebarItemSize.width),
            selectedLeftSidebarFloatingView.heightAnchor.constraint(equalToConstant: Self.sidebarItemSize.height),
            selectedLeftSidebarFloatingTopConstraint!
        ])
        updateLeftFriendTotalBadge()
    }

    func setupLeftFriendTotalBadge() {
        leftFriendTotalBadgeView.layer.cornerRadius = 10
        leftFriendTotalBadgeView.layer.cornerCurve = .continuous
        leftFriendTotalBadgeView.layer.masksToBounds = true
        leftFriendTotalBadgeView.layer.borderWidth = 0.7
        leftFriendTotalBadgeView.layer.borderColor = UIColor.white.withAlphaComponent(0.28).cgColor
        leftFriendTotalBadgeView.isUserInteractionEnabled = false
        leftFriendTotalBadgeView.isHidden = true
        leftFriendTotalBadgeView.alpha = 0

        leftFriendTotalBadgeLabel.font = .systemFont(ofSize: 9.5, weight: .bold)
        leftFriendTotalBadgeLabel.textColor = UIColor.label.withAlphaComponent(0.92)
        leftFriendTotalBadgeLabel.textAlignment = .center
        leftFriendTotalBadgeLabel.numberOfLines = 2
        leftFriendTotalBadgeLabel.adjustsFontSizeToFitWidth = true
        leftFriendTotalBadgeLabel.minimumScaleFactor = 0.72
        leftFriendTotalBadgeLabel.translatesAutoresizingMaskIntoConstraints = false
        leftFriendTotalBadgeView.contentView.addSubview(leftFriendTotalBadgeLabel)

        NSLayoutConstraint.activate([
            leftFriendTotalBadgeLabel.topAnchor.constraint(equalTo: leftFriendTotalBadgeView.contentView.topAnchor, constant: 2),
            leftFriendTotalBadgeLabel.leadingAnchor.constraint(equalTo: leftFriendTotalBadgeView.contentView.leadingAnchor, constant: 4),
            leftFriendTotalBadgeLabel.trailingAnchor.constraint(equalTo: leftFriendTotalBadgeView.contentView.trailingAnchor, constant: -4),
            leftFriendTotalBadgeLabel.bottomAnchor.constraint(equalTo: leftFriendTotalBadgeView.contentView.bottomAnchor, constant: -2)
        ])
    }

    func setupConversationGestureHintView() {
        conversationGestureHintView.backgroundColor = UIColor(red: 0.04, green: 0.10, blue: 0.08, alpha: 0.88)
        conversationGestureHintView.layer.cornerRadius = 16
        conversationGestureHintView.layer.cornerCurve = .continuous
        conversationGestureHintView.layer.borderWidth = 2
        conversationGestureHintView.layer.borderColor = UIColor(red: 0.47, green: 0.83, blue: 0.08, alpha: 0.95).cgColor
        conversationGestureHintView.layer.shadowColor = UIColor.black.cgColor
        conversationGestureHintView.layer.shadowOpacity = 0.22
        conversationGestureHintView.layer.shadowRadius = 8
        conversationGestureHintView.layer.shadowOffset = CGSize(width: 0, height: 4)
        conversationGestureHintView.isUserInteractionEnabled = true
        conversationGestureHintView.isHidden = false
        conversationGestureHintView.alpha = 1
        conversationGestureHintView.translatesAutoresizingMaskIntoConstraints = false
        let tapGesture = UITapGestureRecognizer(target: self, action: #selector(handleConversationGestureHintTap))
        conversationGestureHintView.addGestureRecognizer(tapGesture)
        let panGesture = UIPanGestureRecognizer(target: self, action: #selector(handleMessageCollectionPanForProfile(_:)))
        panGesture.cancelsTouchesInView = false
        conversationGestureHintView.addGestureRecognizer(panGesture)

        let topLabel = makeGestureHintLabel("上滑")
        let leftLabel = makeGestureHintLabel("← 左滑")
        let centerLabel = makeGestureHintLabel("点击/左滑进入主页")
        let rightLabel = makeGestureHintLabel("右滑")
        let bottomLabel = makeGestureHintLabel("下滑")
        centerLabel.font = .systemFont(ofSize: 11, weight: .bold)
        centerLabel.textColor = UIColor(red: 0.05, green: 0.10, blue: 0.07, alpha: 1)
        centerLabel.backgroundColor = UIColor(red: 0.47, green: 0.83, blue: 0.08, alpha: 0.95)
        centerLabel.layer.cornerRadius = 10
        centerLabel.layer.cornerCurve = .continuous
        centerLabel.clipsToBounds = true
        centerLabel.numberOfLines = 1

        [topLabel, leftLabel, centerLabel, rightLabel, bottomLabel].forEach {
            conversationGestureHintView.addSubview($0)
        }

        NSLayoutConstraint.activate([
            topLabel.topAnchor.constraint(equalTo: conversationGestureHintView.topAnchor, constant: 10),
            topLabel.centerXAnchor.constraint(equalTo: conversationGestureHintView.centerXAnchor),
            topLabel.widthAnchor.constraint(equalToConstant: 48),
            topLabel.heightAnchor.constraint(equalToConstant: 24),

            centerLabel.centerXAnchor.constraint(equalTo: conversationGestureHintView.centerXAnchor),
            centerLabel.centerYAnchor.constraint(equalTo: conversationGestureHintView.centerYAnchor),
            centerLabel.widthAnchor.constraint(equalToConstant: 112),
            centerLabel.heightAnchor.constraint(equalToConstant: 24),

            leftLabel.trailingAnchor.constraint(equalTo: centerLabel.leadingAnchor, constant: -8),
            leftLabel.centerYAnchor.constraint(equalTo: centerLabel.centerYAnchor),
            leftLabel.widthAnchor.constraint(equalToConstant: 52),
            leftLabel.heightAnchor.constraint(equalToConstant: 24),

            rightLabel.leadingAnchor.constraint(equalTo: centerLabel.trailingAnchor, constant: 8),
            rightLabel.centerYAnchor.constraint(equalTo: centerLabel.centerYAnchor),
            rightLabel.widthAnchor.constraint(equalToConstant: 42),
            rightLabel.heightAnchor.constraint(equalToConstant: 24),

            bottomLabel.bottomAnchor.constraint(equalTo: conversationGestureHintView.bottomAnchor, constant: -10),
            bottomLabel.centerXAnchor.constraint(equalTo: conversationGestureHintView.centerXAnchor),
            bottomLabel.widthAnchor.constraint(equalToConstant: 48),
            bottomLabel.heightAnchor.constraint(equalToConstant: 24)
        ])
    }

    @objc func handleConversationGestureHintTap() {
        openActiveFriendInfoHomeFromGesture()
    }

    func makeGestureHintLabel(_ text: String) -> UILabel {
        let label = UILabel()
        label.text = text
        label.textAlignment = .center
        label.font = .systemFont(ofSize: 12, weight: .medium)
        label.textColor = UIColor.white.withAlphaComponent(0.92)
        label.shadowColor = UIColor.black.withAlphaComponent(0.22)
        label.shadowOffset = CGSize(width: 0, height: 0.6)
        label.translatesAutoresizingMaskIntoConstraints = false
        return label
    }

    func setupConversationProfileAreaGesture(in contentContainer: UIView) {
        let profileAreaPan = UIPanGestureRecognizer(target: self, action: #selector(handleMessageCollectionPanForProfile(_:)))
        profileAreaPan.cancelsTouchesInView = false
        profileAreaPan.delaysTouchesBegan = false
        profileAreaPan.delaysTouchesEnded = false
        profileAreaPan.delegate = self
        contentContainer.addGestureRecognizer(profileAreaPan)
        conversationProfileAreaPanGesture = profileAreaPan
    }

    func setupLeftScrollPreviewView() {
        leftScrollPreviewView.isHidden = true
        leftScrollPreviewView.alpha = 0
        leftScrollPreviewView.backgroundColor = .clear
        leftScrollPreviewView.isUserInteractionEnabled = false
        leftScrollPreviewView.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            leftScrollPreviewView.topAnchor.constraint(equalTo: leftCollectionView.topAnchor),
            leftScrollPreviewView.leadingAnchor.constraint(equalTo: leftCollectionView.trailingAnchor, constant: 4),
            leftScrollPreviewView.widthAnchor.constraint(equalToConstant: 176),
            leftScrollPreviewView.bottomAnchor.constraint(equalTo: leftCollectionView.bottomAnchor)
        ])
    }

    func updateSelectedLeftSidebarFloatingItem(animated: Bool = false) {
        guard let selectedID = state.selectedFriendID else {
            hideSelectedLeftSidebarFloatingItem(animated: animated)
            return
        }

        let leftItems = visibleLeftItemsForSelectedAccount()
        guard let index = leftItems.firstIndex(where: { $0.participantID == selectedID }),
              let itemFrame = leftSidebarItemFrame(at: IndexPath(item: index, section: 0))
        else {
            hideSelectedLeftSidebarFloatingItem(animated: animated)
            return
        }

        let visibleBounds = leftCollectionView.bounds.insetBy(dx: 0, dy: 4)
        let isFullyVisible = itemFrame.minY >= visibleBounds.minY
            && itemFrame.maxY <= visibleBounds.maxY
        guard !isFullyVisible else {
            hideSelectedLeftSidebarFloatingItem(animated: animated)
            return
        }

        let sourceItem = leftItems[index]
        let activeItem = SidebarItem(
            id: sourceItem.id,
            kind: sourceItem.kind,
            title: sourceItem.title,
            symbolName: sourceItem.symbolName,
            tintColor: sourceItem.tintColor,
            participantID: sourceItem.participantID,
            targetMessageID: sourceItem.targetMessageID,
            isActive: true,
            toolMessageType: sourceItem.toolMessageType,
            customTool: sourceItem.customTool,
            avatarURL: sidebarAvatarURL(for: sourceItem.participantID) ?? sourceItem.avatarURL,
            isOnline: sourceItem.isOnline,
            compositeAvatarTitles: sourceItem.compositeAvatarTitles
        )
        selectedLeftSidebarFloatingView.configure(with: activeItem)

        let itemHeight = Self.sidebarItemSize.height
        let topConstant: CGFloat
        if itemFrame.midY < visibleBounds.midY {
            topConstant = 2
        } else {
            topConstant = max(2, leftCollectionView.bounds.height - itemHeight - 4)
        }

        selectedLeftSidebarFloatingTopConstraint?.constant = topConstant
        selectedLeftSidebarFloatingView.isHidden = false
        if animated {
            UIView.animate(
                withDuration: 0.14,
                delay: 0,
                options: [.beginFromCurrentState, .allowUserInteraction]
            ) {
                self.selectedLeftSidebarFloatingView.alpha = 1
                self.leftCollectionView.superview?.layoutIfNeeded()
            }
        } else {
            selectedLeftSidebarFloatingView.alpha = 1
            var frame = selectedLeftSidebarFloatingView.frame
            frame.origin.y = topConstant
            frame.size = Self.sidebarItemSize
            selectedLeftSidebarFloatingView.frame = frame
        }
    }

    func hideSelectedLeftSidebarFloatingItem(animated: Bool = false) {
        let changes = {
            self.selectedLeftSidebarFloatingView.alpha = 0
        }
        let completion: (Bool) -> Void = { _ in
            self.selectedLeftSidebarFloatingView.isHidden = true
        }
        if animated {
            UIView.animate(withDuration: 0.12, delay: 0, options: [.beginFromCurrentState, .allowUserInteraction], animations: changes, completion: completion)
        } else {
            changes()
            completion(true)
        }
    }

    func ensureSelectedLeftSidebarItemVisible(animated: Bool) {
        guard let selectedID = state.selectedFriendID,
              let index = visibleLeftItemsForSelectedAccount().firstIndex(where: { $0.participantID == selectedID })
        else {
            hideSelectedLeftSidebarFloatingItem(animated: animated)
            return
        }

        leftCollectionView.layoutIfNeeded()
        let indexPath = IndexPath(item: index, section: 0)
        if let itemFrame = leftSidebarItemFrame(at: indexPath) {
            let visibleBounds = leftCollectionView.bounds.insetBy(dx: 0, dy: 8)
            if itemFrame.minY >= visibleBounds.minY && itemFrame.maxY <= visibleBounds.maxY {
                updateSelectedLeftSidebarFloatingItem(animated: animated)
                return
            }
        }

        leftCollectionView.scrollToItem(at: indexPath, at: .centeredVertically, animated: animated)
        DispatchQueue.main.asyncAfter(deadline: .now() + (animated ? 0.26 : 0.02)) { [weak self] in
            self?.updateSelectedLeftSidebarFloatingItem(animated: false)
        }
    }

    func ensureSelectedRightAccountItemVisible(animated: Bool) {
        let selectedAccountID = unansweredAccountFilterID ?? state.selectedAccountID
        let items = visibleRightAccountItems()
        guard let index = items.firstIndex(where: { $0.participantID == selectedAccountID })
        else { return }

        rightAccountCollectionView.layoutIfNeeded()
        let indexPath = IndexPath(item: index, section: 0)
        if let frame = rightAccountCollectionView.layoutAttributesForItem(at: indexPath)?.frame {
            let visibleBounds = rightAccountCollectionView.bounds.insetBy(dx: 0, dy: 6)
            if frame.minY >= visibleBounds.minY && frame.maxY <= visibleBounds.maxY {
                return
            }
        }
        guard indexPath.item < rightAccountCollectionView.numberOfItems(inSection: 0) else {
            return
        }
        rightAccountCollectionView.scrollToItem(
            at: indexPath,
            at: .centeredVertically,
            animated: animated
        )
    }

    func leftSidebarItemFrame(at indexPath: IndexPath) -> CGRect? {
        if let attributes = leftCollectionView.layoutAttributesForItem(at: indexPath) {
            return attributes.frame
        }
        guard let layout = leftCollectionView.collectionViewLayout as? UICollectionViewFlowLayout else {
            return nil
        }
        let itemSize = layout.itemSize
        let y = layout.sectionInset.top + CGFloat(indexPath.item) * (itemSize.height + layout.minimumLineSpacing)
        return CGRect(
            x: layout.sectionInset.left,
            y: y,
            width: itemSize.width,
            height: itemSize.height
        )
    }

    func updateLeftScrollPreview() {
        let leftItems = visibleLeftItemsForSelectedAccount()
        let participantsByID = participantLookupByID()
        let selectedAccount = participantsByID[state.selectedAccountID] ?? state.currentUser
        let visibleIndexPaths = leftCollectionView.indexPathsForVisibleItems
            .filter { $0.item < leftItems.count }
            .sorted { $0.item < $1.item }
        var visibleIDs = Set<UUID>()

        UIView.performWithoutAnimation {
            for indexPath in visibleIndexPaths {
                guard let participantID = leftItems[indexPath.item].participantID,
                      let participant = participantsByID[participantID],
                      let attributes = leftCollectionView.layoutAttributesForItem(at: indexPath)
                else { continue }

                visibleIDs.insert(participantID)
                let card = leftScrollPreviewCardsByID[participantID] ?? {
                    let newCard = makeLeftScrollPreviewCard(for: participant)
                    leftScrollPreviewCardsByID[participantID] = newCard
                    leftScrollPreviewView.addSubview(newCard)
                    return newCard
                }()
                if card.superview == nil {
                    leftScrollPreviewView.addSubview(card)
                }
                updateLeftScrollPreviewCard(card, for: participant, selectedAccount: selectedAccount)
                card.frame = leftPreviewFrame(for: attributes.frame)
                card.alpha = 1
                card.isHidden = false
            }
            leftScrollPreviewCardsByID.forEach { id, card in
                if !visibleIDs.contains(id) {
                    card.isHidden = true
                }
            }
        }

        guard !visibleIDs.isEmpty else {
            hideLeftScrollPreview(animated: false)
            return
        }
        leftScrollPreviewView.isHidden = false
        if leftScrollPreviewView.alpha < 1 {
            UIView.animate(withDuration: 0.08) {
                self.leftScrollPreviewView.alpha = 1
            }
        }
    }

    func cancelLeftScrollPreview() {
        stopLeftScrollPerformanceUpdates()
        leftScrollPreviewView.layer.removeAllAnimations()
        leftScrollPreviewView.alpha = 0
        leftScrollPreviewView.isHidden = true
        leftScrollPreviewCardsByID.values.forEach { $0.removeFromSuperview() }
        leftScrollPreviewCardsByID.removeAll()
    }

    func hideLeftScrollPreview(animated: Bool) {
        let changes = {
            self.leftScrollPreviewView.alpha = 0
        }
        let completion: (Bool) -> Void = { _ in
            self.leftScrollPreviewView.isHidden = true
            self.leftScrollPreviewCardsByID.values.forEach { $0.isHidden = true }
        }
        if animated {
            UIView.animate(withDuration: 0.18, animations: changes, completion: completion)
        } else {
            changes()
            completion(true)
        }
    }

    func leftPreviewFrame(for itemFrame: CGRect) -> CGRect {
        let convertedFrame = leftScrollPreviewView.convert(itemFrame, from: leftCollectionView)
        let width: CGFloat = 172
        let height: CGFloat = 48
        let minY: CGFloat = 4
        let maxY = max(minY, leftScrollPreviewView.bounds.height - height - 4)
        let y = min(max(convertedFrame.midY - height / 2, minY), maxY)
        return CGRect(x: 0, y: y, width: width, height: height)
    }

    func makeLeftScrollPreviewCard(for participant: ChatParticipant) -> UIView {
        let blur = UIBlurEffect(style: .systemUltraThinMaterialLight)
        let card = UIVisualEffectView(effect: blur)
        card.layer.cornerRadius = 9
        card.layer.cornerCurve = .continuous
        card.clipsToBounds = true
        card.layer.borderWidth = 1
        card.layer.borderColor = UIColor.white.withAlphaComponent(0.58).cgColor

        let nameLabel = UILabel()
        nameLabel.tag = Self.leftPreviewNameLabelTag
        nameLabel.text = displayName(for: participant)
        nameLabel.font = .systemFont(ofSize: 12.6, weight: .bold)
        nameLabel.textColor = UIColor(red: 0.07, green: 0.18, blue: 0.22, alpha: 1)
        nameLabel.numberOfLines = 1
        nameLabel.lineBreakMode = .byTruncatingTail
        nameLabel.translatesAutoresizingMaskIntoConstraints = false

        let summary = latestMessageSummary(for: participant)
        let previewLabel = UILabel()
        previewLabel.tag = Self.leftPreviewMessageLabelTag
        previewLabel.text = summary.text
        previewLabel.font = .systemFont(ofSize: 10.8, weight: .medium)
        previewLabel.textColor = UIColor(red: 0.17, green: 0.30, blue: 0.34, alpha: 0.92)
        previewLabel.numberOfLines = 1
        previewLabel.lineBreakMode = .byTruncatingTail
        previewLabel.translatesAutoresizingMaskIntoConstraints = false

        let timeLabel = UILabel()
        timeLabel.tag = Self.leftPreviewTimeLabelTag
        timeLabel.isHidden = true
        timeLabel.text = summary.time
        timeLabel.font = .systemFont(ofSize: 9.6, weight: .semibold)
        timeLabel.textColor = UIColor(red: 0.24, green: 0.36, blue: 0.40, alpha: 0.70)
        timeLabel.textAlignment = .left
        timeLabel.translatesAutoresizingMaskIntoConstraints = false

        nameLabel.setContentCompressionResistancePriority(.required, for: .vertical)
        previewLabel.setContentCompressionResistancePriority(.required, for: .vertical)
        [nameLabel, previewLabel].forEach(card.contentView.addSubview)
        NSLayoutConstraint.activate([
            nameLabel.leadingAnchor.constraint(equalTo: card.contentView.leadingAnchor, constant: 10),
            nameLabel.trailingAnchor.constraint(equalTo: card.contentView.trailingAnchor, constant: -10),
            nameLabel.topAnchor.constraint(equalTo: card.contentView.topAnchor, constant: 6),
            previewLabel.leadingAnchor.constraint(equalTo: nameLabel.leadingAnchor),
            previewLabel.trailingAnchor.constraint(equalTo: card.contentView.trailingAnchor, constant: -10),
            previewLabel.topAnchor.constraint(equalTo: nameLabel.bottomAnchor, constant: 3),
            previewLabel.heightAnchor.constraint(greaterThanOrEqualToConstant: 15),
            previewLabel.bottomAnchor.constraint(lessThanOrEqualTo: card.contentView.bottomAnchor, constant: -6)
        ])
        updateLeftScrollPreviewCard(card, for: participant)
        return card
    }

    func updateLeftScrollPreviewCard(_ card: UIView, for participant: ChatParticipant, selectedAccount: ChatParticipant? = nil) {
        let summary = latestMessageSummary(for: participant)
        (card.viewWithTag(Self.leftPreviewNameLabelTag) as? UILabel)?.text = displayName(for: participant)
        (card.viewWithTag(Self.leftPreviewMessageLabelTag) as? UILabel)?.text = summary.text
        (card.viewWithTag(Self.leftPreviewTimeLabelTag) as? UILabel)?.text = summary.time
    }

    func leftScrollPreviewAccount(
        for participant: ChatParticipant,
        selectedAccount: ChatParticipant?
    ) -> ChatParticipant {
        let participantsByID = participantLookupByID()
        let currentAccountIDs = Set(state.currentUsers.map(\.id))

        if state.selectedFriendID != nil,
           let selectedAccount,
           currentAccountIDs.contains(selectedAccount.id),
           isConversation(participant, relatedToAccountID: selectedAccount.id) {
            return selectedAccount
        }

        let resolvedAccountID: UUID?
        if isGroupConversation(participant) || isGroupParticipant(participant) {
            let groupAccountIDs = currentAccountIDsInGroup(participant)
            resolvedAccountID = state.currentUsers.map(\.id).first { groupAccountIDs.contains($0) }
                ?? groupAccountIDs.sorted { $0.uuidString < $1.uuidString }.first
        } else {
            resolvedAccountID = accountIDForDirectConversation(
                participant,
                preferredAccountID: state.selectedAccountID
            )
        }

        if let resolvedAccountID {
            return participantsByID[resolvedAccountID]
                ?? state.currentUsers.first { $0.id == resolvedAccountID }
                ?? selectedAccount
                ?? state.currentUser
        }
        return selectedAccount
            ?? participantsByID[state.selectedAccountID]
            ?? state.currentUsers.first
            ?? state.currentUser
    }

    func configurePreviewAvatar(
        _ imageView: UIImageView,
        title: String,
        color: UIColor,
        avatarURL: URL?,
        size: CGSize
    ) {
        imageView.tintColor = nil
        imageView.accessibilityIdentifier = avatarURL?.absoluteString
        guard let avatarURL else {
            imageView.image = UIImage.sidebarPlaceholderAvatar(title: title, color: color, size: size)
            imageView.backgroundColor = color.withAlphaComponent(0.18)
            return
        }
        if let cached = AvatarPipeline.shared.cachedImage(for: avatarURL, targetSize: size) {
            imageView.image = cached
            imageView.backgroundColor = .clear
            return
        }
        imageView.image = UIImage.sidebarPlaceholderAvatar(title: title, color: color, size: size)
        imageView.backgroundColor = color.withAlphaComponent(0.18)
        _ = AvatarPipeline.shared.load(avatarURL, targetSize: size) { [weak imageView] image in
            guard let imageView,
                  imageView.accessibilityIdentifier == avatarURL.absoluteString,
                  let image
            else { return }
            imageView.image = image
            imageView.backgroundColor = .clear
        }
    }

    func latestMessageSummary(for participant: ChatParticipant) -> (text: String, time: String) {
        let cacheKey = latestSidebarMessagesCurrentCacheKey()
        if cacheKey == latestSidebarSummaryCacheKey,
           let cached = latestSidebarSummaryCache[participant.id] {
            return cached
        }
        if cacheKey != latestSidebarSummaryCacheKey {
            latestSidebarSummaryCacheKey = cacheKey
            latestSidebarSummaryCache = [:]
        }
        let message = latestSidebarMessagesByParticipantID()[participant.id]
        guard let message else { return ("暂无消息", "") }
        let body = message.body.trimmingCharacters(in: .whitespacesAndNewlines)
        let summary = (text: body.isEmpty ? message.type.title : body, time: message.displayTimestamp)
        latestSidebarSummaryCache[participant.id] = summary
        return summary
    }

    func updateLeftCollectionBounceInsets() {
        guard leftCollectionView.bounds.height > 0 else { return }
        let contentHeight = leftCollectionView.collectionViewLayout.collectionViewContentSize.height
        let requiredScrollableSlack = leftCollectionView.bounds.height + 28
        let bottomInset = min(96, max(20, requiredScrollableSlack - contentHeight))
        if abs(leftCollectionView.contentInset.bottom - bottomInset) > 0.5 {
            leftCollectionView.contentInset.bottom = bottomInset
            leftCollectionView.verticalScrollIndicatorInsets.bottom = bottomInset
        }
    }

    func updateLeftFriendTotalBadge() {
        leftFriendTotalBadgeView.isHidden = true
        leftFriendTotalBadgeView.alpha = 0
        leftFriendTotalBadgeLabel.text = nil
        leftFriendTotalBadgeView.accessibilityLabel = nil

        let desiredTopInset: CGFloat = 0
        if abs(leftCollectionView.contentInset.top - desiredTopInset) > 0.5 {
            var inset = leftCollectionView.contentInset
            inset.top = desiredTopInset
            leftCollectionView.contentInset = inset
            leftCollectionView.verticalScrollIndicatorInsets.top = desiredTopInset
        }
    }

    func visibleRightAccountsFriendSummary() -> (accountCount: Int, friendCount: Int) {
        let visibleAccountIDs = Set(visibleRightAccountItems().compactMap(\.participantID))
        let ownerWxids = Set(
            visibleAccountIDs.compactMap { accountID in
                OpenApiDisplay.cleaned(openApiAccountContextsByID[accountID]?.wxid ?? "")
            }.filter { !$0.isEmpty }
        )
        guard !ownerWxids.isEmpty else {
            return (0, 0)
        }
        let uniqueFriendKeys = Set(
            openApiConversationContextsByID.values.compactMap { context -> String? in
                guard context.kind == .contact,
                      ownerWxids.contains(context.ownerWxid),
                      !context.wxid.isEmpty
                else { return nil }
                return "\(context.ownerWxid)|\(context.wxid)"
            }
        )
        return (ownerWxids.count, uniqueFriendKeys.count)
    }

    func isLeftScrollPreviewGestureActive() -> Bool {
        guard let state = leftScrollPreviewPanGesture?.state else { return false }
        return state == .began || state == .changed
    }

    func scheduleLeftScrollPerformanceUpdate(
        preview: Bool,
        floating: Bool = false,
        avatarPrefetch: Bool,
        connection: Bool
    ) {
        leftScrollNeedsPreviewUpdate = leftScrollNeedsPreviewUpdate || preview
        leftScrollNeedsFloatingUpdate = leftScrollNeedsFloatingUpdate || floating
        leftScrollNeedsAvatarPrefetch = leftScrollNeedsAvatarPrefetch || avatarPrefetch
        leftScrollNeedsConnectionUpdate = leftScrollNeedsConnectionUpdate || connection
        ensureFrameCoordinator()
    }

    var hasPendingLeftScrollFrameWork: Bool {
        leftScrollNeedsPreviewUpdate
            || leftScrollNeedsFloatingUpdate
            || leftScrollNeedsAvatarPrefetch
            || leftScrollNeedsConnectionUpdate
    }

    func performLeftScrollFrameUpdates() {
        guard isViewLoaded, leftCollectionView.window != nil else {
            resetLeftScrollPerformanceState()
            return
        }
        let isLeftScrollActive = leftCollectionView.isDragging
            || leftCollectionView.isDecelerating
            || isLeftScrollPreviewGestureActive()
        guard isLeftScrollActive || hasPendingLeftScrollFrameWork else {
            leftScrollFrameCounter = 0
            return
        }

        leftScrollFrameCounter += 1
        let frameInterval = leftCollectionView.isDecelerating ? 3 : (leftCollectionView.isDragging ? 2 : 1)
        if leftScrollNeedsFloatingUpdate {
            leftScrollNeedsFloatingUpdate = false
            updateVisibleUnreadTimelineAvatarPositions()
        }
        if frameInterval == 1 || leftScrollFrameCounter.isMultiple(of: frameInterval) {
            updateSelectedLeftSidebarFloatingItem(animated: false)
        }
        if leftScrollNeedsPreviewUpdate,
           frameInterval == 1 || leftScrollFrameCounter.isMultiple(of: frameInterval) {
            leftScrollNeedsPreviewUpdate = false
            updateLeftScrollPreview()
        }
        if leftScrollNeedsAvatarPrefetch,
           leftScrollFrameCounter.isMultiple(of: leftCollectionView.isDecelerating ? 10 : 6) || frameInterval == 1 {
            leftScrollNeedsAvatarPrefetch = false
            prefetchVisibleSidebarAvatarWindow(in: leftCollectionView, extraItems: 10)
        }
        if leftScrollNeedsConnectionUpdate,
           frameInterval == 1 || leftScrollFrameCounter.isMultiple(of: frameInterval) {
            leftScrollNeedsConnectionUpdate = false
            connectionUpdateNeeded = true
        }
        if !isLeftScrollActive, !hasPendingLeftScrollFrameWork {
            leftScrollFrameCounter = 0
        }
    }

    func stopLeftScrollPerformanceUpdates() {
        resetLeftScrollPerformanceState()
    }

    func resetLeftScrollPerformanceState() {
        leftScrollNeedsPreviewUpdate = false
        leftScrollNeedsFloatingUpdate = false
        leftScrollNeedsAvatarPrefetch = false
        leftScrollNeedsConnectionUpdate = false
        leftScrollFrameCounter = 0
    }

    func setupRightEdgeDouyinFeedbackView() {
        rightEdgeDouyinFeedbackView.translatesAutoresizingMaskIntoConstraints = false
        rightEdgeDouyinFeedbackView.isUserInteractionEnabled = false
        rightEdgeDouyinFeedbackView.alpha = 0
        rightEdgeDouyinFeedbackView.layer.zPosition = 20

        configureEdgeFeedbackStyle(
            container: rightEdgeDouyinFeedbackView,
            blobLayer: rightEdgeDouyinBlobLayer,
            rimLayer: rightEdgeDouyinRimLayer,
            highlightLayer: rightEdgeDouyinHighlightLayer,
            trailLayer: rightEdgeDouyinTrailLayer,
            iconView: rightEdgeDouyinArrowView,
            hintLabel: rightEdgeDouyinHintLabel
        )
    }

    func setupLeftEdgeFeedbackView() {
        leftEdgeFeedbackView.translatesAutoresizingMaskIntoConstraints = false
        leftEdgeFeedbackView.isUserInteractionEnabled = false
        leftEdgeFeedbackView.alpha = 0
        leftEdgeFeedbackView.layer.zPosition = 20

        configureEdgeFeedbackStyle(
            container: leftEdgeFeedbackView,
            blobLayer: leftEdgeBlobLayer,
            rimLayer: leftEdgeRimLayer,
            highlightLayer: leftEdgeHighlightLayer,
            trailLayer: leftEdgeTrailLayer,
            iconView: leftEdgeArrowView,
            hintLabel: leftEdgeHintLabel
        )
    }

    func configureEdgeFeedbackStyle(
        container: UIView,
        blobLayer: CAShapeLayer,
        rimLayer: CAShapeLayer,
        highlightLayer: CAShapeLayer,
        trailLayer: CAShapeLayer,
        iconView: UIImageView,
        hintLabel: UILabel
    ) {
        blobLayer.fillColor = UIColor.white.withAlphaComponent(0.86).cgColor
        blobLayer.shadowColor = UIColor.black.withAlphaComponent(0.18).cgColor
        blobLayer.shadowOffset = CGSize(width: -2, height: 2)
        blobLayer.shadowOpacity = 1
        blobLayer.shadowRadius = 8
        container.layer.addSublayer(blobLayer)

        rimLayer.fillColor = UIColor.clear.cgColor
        rimLayer.strokeColor = UIColor(red: 0.32, green: 0.49, blue: 0.98, alpha: 0.42).cgColor
        rimLayer.lineWidth = 1.4
        container.layer.addSublayer(rimLayer)

        highlightLayer.fillColor = UIColor.white.withAlphaComponent(0.30).cgColor
        container.layer.addSublayer(highlightLayer)

        trailLayer.fillColor = UIColor.clear.cgColor
        trailLayer.strokeColor = UIColor.white.withAlphaComponent(0.12).cgColor
        trailLayer.lineWidth = 1.5
        trailLayer.lineCap = .round
        trailLayer.lineJoin = .round
        container.layer.addSublayer(trailLayer)

        iconView.tintColor = UIColor(red: 0.10, green: 0.15, blue: 0.28, alpha: 0.82)
        iconView.contentMode = .center
        iconView.backgroundColor = UIColor.white.withAlphaComponent(0.62)
        iconView.layer.cornerRadius = 18
        iconView.layer.cornerCurve = .continuous
        iconView.layer.borderWidth = 1
        iconView.layer.borderColor = UIColor(red: 0.32, green: 0.49, blue: 0.98, alpha: 0.24).cgColor
        iconView.bounds = CGRect(x: 0, y: 0, width: 36, height: 36)
        container.addSubview(iconView)

        hintLabel.textColor = UIColor(red: 0.08, green: 0.12, blue: 0.20, alpha: 0.78)
        hintLabel.font = .systemFont(ofSize: 10, weight: .semibold)
        hintLabel.textAlignment = .center
        hintLabel.alpha = 0
        hintLabel.backgroundColor = UIColor.white.withAlphaComponent(0.64)
        hintLabel.layer.cornerRadius = 7
        hintLabel.clipsToBounds = true
        hintLabel.bounds = CGRect(x: 0, y: 0, width: 58, height: 16)
        container.addSubview(hintLabel)
    }

    func setupAiffButton() {
        aiffButton.translatesAutoresizingMaskIntoConstraints = false
        aiffButton.setImage(.chatRobotIcon(size: CGSize(width: 26, height: 26)), for: .normal)
        aiffButton.tintColor = nil
        aiffButton.backgroundColor = UIColor.white.withAlphaComponent(0.16)
        aiffButton.layer.cornerRadius = 15
        aiffButton.layer.cornerCurve = .continuous
        aiffButton.layer.borderWidth = 1
        aiffButton.layer.borderColor = UIColor.white.withAlphaComponent(0.45).cgColor
        aiffButton.imageView?.contentMode = .scaleAspectFit
        aiffButton.accessibilityLabel = "AI"
        aiffButton.addTarget(self, action: #selector(showAIConversation), for: .touchUpInside)
    }

    func setupInputBar() {
        let contentView = chromeView.visualEffectView.contentView
        setupEmojiPanel(in: contentView)

        inputBarBottomFillView.backgroundColor = UIColor(white: 0.94, alpha: 0.96)
        inputBarBottomFillView.translatesAutoresizingMaskIntoConstraints = false
        contentView.addSubview(inputBarBottomFillView)

        setupQuotePreviewBar()
        contentView.addSubview(quotePreviewBar)
        setupMentionPanel()
        contentView.addSubview(mentionPanelView)

        inputBar.translatesAutoresizingMaskIntoConstraints = false
        contentView.addSubview(inputBar)
        inputBar.textField.accessibilityIdentifier = AIInputFloatingAssistant.allowedTextFieldIdentifier
        inputBar.textField.delegate = self
        inputBar.textField.addTarget(self, action: #selector(handleInputTextChanged), for: .editingChanged)
        inputBar.voiceButton.addTarget(self, action: #selector(handleVoicePressBegan), for: .touchDown)
        inputBar.voiceButton.addTarget(self, action: #selector(handleVoicePressEnded), for: [.touchUpInside, .touchUpOutside])
        inputBar.voiceButton.addTarget(self, action: #selector(handleVoicePressCancelled), for: .touchCancel)
        inputBar.giftButton.addTarget(self, action: #selector(showStickerSheet), for: .touchUpInside)
        inputBar.moreButton.addTarget(self, action: #selector(showAIConversation), for: .touchUpInside)
        inputBar.minimizeButton.addTarget(self, action: #selector(showSelectedAccountFavorites), for: .touchUpInside)
        setupMultiSelectBar(in: contentView)
        setupVoiceRecordingPrompt(in: contentView)
        inputBarBottomConstraint = inputBar.bottomAnchor.constraint(equalTo: contentView.safeAreaLayoutGuide.bottomAnchor)
        emojiPanelHeightConstraint = emojiPanelView.heightAnchor.constraint(equalToConstant: 0)
        quotePreviewHeightConstraint = quotePreviewBar.heightAnchor.constraint(equalToConstant: 0)
        mentionPanelHeightConstraint = mentionPanelView.heightAnchor.constraint(equalToConstant: 0)

        NSLayoutConstraint.activate([
            quotePreviewBar.leadingAnchor.constraint(equalTo: contentView.leadingAnchor),
            quotePreviewBar.trailingAnchor.constraint(equalTo: contentView.trailingAnchor),
            quotePreviewBar.bottomAnchor.constraint(equalTo: inputBar.topAnchor),
            quotePreviewHeightConstraint!,

            mentionPanelView.leadingAnchor.constraint(equalTo: contentView.leadingAnchor, constant: 12),
            mentionPanelView.trailingAnchor.constraint(equalTo: contentView.trailingAnchor, constant: -12),
            mentionPanelView.bottomAnchor.constraint(equalTo: quotePreviewBar.topAnchor),
            mentionPanelHeightConstraint!,

            inputBar.leadingAnchor.constraint(equalTo: contentView.leadingAnchor),
            inputBar.trailingAnchor.constraint(equalTo: contentView.trailingAnchor),
            inputBarBottomConstraint!,
            inputBar.heightAnchor.constraint(equalToConstant: 56),

            multiSelectBar.leadingAnchor.constraint(equalTo: inputBar.leadingAnchor),
            multiSelectBar.trailingAnchor.constraint(equalTo: inputBar.trailingAnchor),
            multiSelectBar.topAnchor.constraint(equalTo: inputBar.topAnchor),
            multiSelectBar.bottomAnchor.constraint(equalTo: inputBar.bottomAnchor),

            emojiPanelView.leadingAnchor.constraint(equalTo: contentView.leadingAnchor),
            emojiPanelView.trailingAnchor.constraint(equalTo: contentView.trailingAnchor),
            emojiPanelView.bottomAnchor.constraint(equalTo: contentView.safeAreaLayoutGuide.bottomAnchor),
            emojiPanelHeightConstraint!,

            voiceRecordingPromptView.centerXAnchor.constraint(equalTo: contentView.centerXAnchor),
            voiceRecordingPromptView.bottomAnchor.constraint(equalTo: inputBar.topAnchor, constant: -12),
            voiceRecordingPromptView.heightAnchor.constraint(equalToConstant: 42),
            voiceRecordingPromptView.widthAnchor.constraint(greaterThanOrEqualToConstant: 172),

            inputBarBottomFillView.topAnchor.constraint(equalTo: inputBar.bottomAnchor),
            inputBarBottomFillView.leadingAnchor.constraint(equalTo: contentView.leadingAnchor),
            inputBarBottomFillView.trailingAnchor.constraint(equalTo: contentView.trailingAnchor),
            inputBarBottomFillView.bottomAnchor.constraint(equalTo: contentView.bottomAnchor)
        ])
    }

    func setupVoiceRecordingPrompt(in contentView: UIView) {
        voiceRecordingPromptView.backgroundColor = UIColor.black.withAlphaComponent(0.72)
        voiceRecordingPromptView.layer.cornerRadius = 18
        voiceRecordingPromptView.layer.cornerCurve = .continuous
        voiceRecordingPromptView.alpha = 0
        voiceRecordingPromptView.isHidden = true
        voiceRecordingPromptView.translatesAutoresizingMaskIntoConstraints = false
        contentView.addSubview(voiceRecordingPromptView)

        voiceRecordingPromptIconView.tintColor = .white
        voiceRecordingPromptIconView.contentMode = .scaleAspectFit
        voiceRecordingPromptIconView.translatesAutoresizingMaskIntoConstraints = false
        voiceRecordingPromptView.addSubview(voiceRecordingPromptIconView)

        voiceRecordingPromptLabel.text = "正在录音，松手发送"
        voiceRecordingPromptLabel.textColor = .white
        voiceRecordingPromptLabel.font = .systemFont(ofSize: 14, weight: .medium)
        voiceRecordingPromptLabel.translatesAutoresizingMaskIntoConstraints = false
        voiceRecordingPromptView.addSubview(voiceRecordingPromptLabel)

        NSLayoutConstraint.activate([
            voiceRecordingPromptIconView.leadingAnchor.constraint(equalTo: voiceRecordingPromptView.leadingAnchor, constant: 16),
            voiceRecordingPromptIconView.centerYAnchor.constraint(equalTo: voiceRecordingPromptView.centerYAnchor),
            voiceRecordingPromptIconView.widthAnchor.constraint(equalToConstant: 18),
            voiceRecordingPromptIconView.heightAnchor.constraint(equalToConstant: 18),

            voiceRecordingPromptLabel.leadingAnchor.constraint(equalTo: voiceRecordingPromptIconView.trailingAnchor, constant: 8),
            voiceRecordingPromptLabel.trailingAnchor.constraint(equalTo: voiceRecordingPromptView.trailingAnchor, constant: -16),
            voiceRecordingPromptLabel.centerYAnchor.constraint(equalTo: voiceRecordingPromptView.centerYAnchor)
        ])
    }

    func setupMultiSelectBar(in contentView: UIView) {
        multiSelectBar.backgroundColor = UIColor(white: 0.98, alpha: 0.98)
        multiSelectBar.layer.borderWidth = 1 / UIScreen.main.scale
        multiSelectBar.layer.borderColor = UIColor.black.withAlphaComponent(0.12).cgColor
        multiSelectBar.isHidden = true
        multiSelectBar.translatesAutoresizingMaskIntoConstraints = false
        contentView.addSubview(multiSelectBar)

        let stack = UIStackView(arrangedSubviews: [
            multiForwardButton,
            multiMergeButton,
            multiDeleteButton,
            multiCancelButton
        ])
        stack.axis = .horizontal
        stack.alignment = .center
        stack.distribution = .equalSpacing
        stack.translatesAutoresizingMaskIntoConstraints = false
        multiSelectBar.addSubview(stack)

        updateMultiSelectButtonTitles()

        NSLayoutConstraint.activate([
            stack.leadingAnchor.constraint(equalTo: multiSelectBar.leadingAnchor, constant: 18),
            stack.trailingAnchor.constraint(equalTo: multiSelectBar.trailingAnchor, constant: -18),
            stack.topAnchor.constraint(equalTo: multiSelectBar.topAnchor, constant: 4),
            stack.bottomAnchor.constraint(equalTo: multiSelectBar.bottomAnchor, constant: -4)
        ])
    }

    func configureMultiSelectButton(
        _ button: UIButton,
        title: String,
        symbolName: String,
        action: Selector,
        isDestructive: Bool
    ) {
        var configuration = UIButton.Configuration.plain()
        configuration.image = UIImage(systemName: symbolName)
        configuration.imagePlacement = .top
        configuration.imagePadding = 4
        configuration.title = title
        configuration.baseForegroundColor = isDestructive ? UIColor.systemRed : UIColor.label
        configuration.contentInsets = NSDirectionalEdgeInsets(top: 4, leading: 6, bottom: 4, trailing: 6)
        button.configuration = configuration
        button.titleLabel?.font = .systemFont(ofSize: 11, weight: .medium)
        button.removeTarget(nil, action: nil, for: .touchUpInside)
        button.addTarget(self, action: action, for: .touchUpInside)
    }

    func setupQuotePreviewBar() {
        quotePreviewBar.backgroundColor = UIColor(white: 0.94, alpha: 0.96)
        quotePreviewBar.isHidden = true
        quotePreviewBar.translatesAutoresizingMaskIntoConstraints = false

        quoteAccentView.backgroundColor = UIColor(red: 0.47, green: 0.83, blue: 0.08, alpha: 1)
        quoteAccentView.layer.cornerRadius = 1.5
        quoteAccentView.translatesAutoresizingMaskIntoConstraints = false
        quotePreviewBar.addSubview(quoteAccentView)

        quoteThumbnailView.contentMode = .scaleAspectFill
        quoteThumbnailView.clipsToBounds = true
        quoteThumbnailView.layer.cornerRadius = 6
        quoteThumbnailView.layer.cornerCurve = .continuous
        quoteThumbnailView.backgroundColor = UIColor.black.withAlphaComponent(0.08)
        quoteThumbnailView.isHidden = true
        quoteThumbnailView.isUserInteractionEnabled = true
        quoteThumbnailView.addGestureRecognizer(UITapGestureRecognizer(target: self, action: #selector(previewPendingAttachment)))
        quoteThumbnailView.translatesAutoresizingMaskIntoConstraints = false
        quotePreviewBar.addSubview(quoteThumbnailView)

        quotePreviewLabel.font = .systemFont(ofSize: 12.5, weight: .medium)
        quotePreviewLabel.textColor = UIColor(red: 0.16, green: 0.18, blue: 0.20, alpha: 0.86)
        quotePreviewLabel.lineBreakMode = .byTruncatingTail
        quotePreviewLabel.numberOfLines = 1
        quotePreviewLabel.translatesAutoresizingMaskIntoConstraints = false
        quotePreviewBar.addSubview(quotePreviewLabel)

        quoteCloseButton.setImage(UIImage(systemName: "xmark.circle.fill"), for: .normal)
        quoteCloseButton.tintColor = UIColor.black.withAlphaComponent(0.34)
        quoteCloseButton.addTarget(self, action: #selector(clearQuotedMessage), for: .touchUpInside)
        quoteCloseButton.translatesAutoresizingMaskIntoConstraints = false
        quotePreviewBar.addSubview(quoteCloseButton)

        NSLayoutConstraint.activate([
            quoteAccentView.leadingAnchor.constraint(equalTo: quotePreviewBar.leadingAnchor, constant: 16),
            quoteAccentView.centerYAnchor.constraint(equalTo: quotePreviewBar.centerYAnchor),
            quoteAccentView.widthAnchor.constraint(equalToConstant: 3),
            quoteAccentView.heightAnchor.constraint(equalToConstant: 22),

            quoteThumbnailView.leadingAnchor.constraint(equalTo: quoteAccentView.trailingAnchor, constant: 10),
            quoteThumbnailView.centerYAnchor.constraint(equalTo: quotePreviewBar.centerYAnchor),
            quoteThumbnailView.widthAnchor.constraint(equalToConstant: 34),
            quoteThumbnailView.heightAnchor.constraint(equalToConstant: 34),

            quotePreviewLabel.leadingAnchor.constraint(equalTo: quoteThumbnailView.trailingAnchor, constant: 10),
            quotePreviewLabel.centerYAnchor.constraint(equalTo: quotePreviewBar.centerYAnchor),
            quotePreviewLabel.trailingAnchor.constraint(equalTo: quoteCloseButton.leadingAnchor, constant: -8),

            quoteCloseButton.trailingAnchor.constraint(equalTo: quotePreviewBar.trailingAnchor, constant: -14),
            quoteCloseButton.centerYAnchor.constraint(equalTo: quotePreviewBar.centerYAnchor),
            quoteCloseButton.widthAnchor.constraint(equalToConstant: 28),
            quoteCloseButton.heightAnchor.constraint(equalToConstant: 28)
        ])
    }

    func setupMentionPanel() {
        mentionPanelView.backgroundColor = UIColor(white: 0.97, alpha: 0.98)
        mentionPanelView.layer.cornerRadius = 14
        mentionPanelView.layer.cornerCurve = .continuous
        mentionPanelView.layer.borderWidth = 1
        mentionPanelView.layer.borderColor = UIColor.black.withAlphaComponent(0.08).cgColor
        mentionPanelView.clipsToBounds = true
        mentionPanelView.isHidden = true
        mentionPanelView.translatesAutoresizingMaskIntoConstraints = false

        mentionScrollView.showsHorizontalScrollIndicator = false
        mentionScrollView.translatesAutoresizingMaskIntoConstraints = false
        mentionPanelView.addSubview(mentionScrollView)

        mentionStackView.axis = .horizontal
        mentionStackView.spacing = 8
        mentionStackView.alignment = .center
        mentionStackView.translatesAutoresizingMaskIntoConstraints = false
        mentionScrollView.addSubview(mentionStackView)

        NSLayoutConstraint.activate([
            mentionScrollView.topAnchor.constraint(equalTo: mentionPanelView.topAnchor),
            mentionScrollView.leadingAnchor.constraint(equalTo: mentionPanelView.leadingAnchor, constant: 8),
            mentionScrollView.trailingAnchor.constraint(equalTo: mentionPanelView.trailingAnchor, constant: -8),
            mentionScrollView.bottomAnchor.constraint(equalTo: mentionPanelView.bottomAnchor),

            mentionStackView.topAnchor.constraint(equalTo: mentionScrollView.contentLayoutGuide.topAnchor),
            mentionStackView.leadingAnchor.constraint(equalTo: mentionScrollView.contentLayoutGuide.leadingAnchor),
            mentionStackView.trailingAnchor.constraint(equalTo: mentionScrollView.contentLayoutGuide.trailingAnchor),
            mentionStackView.bottomAnchor.constraint(equalTo: mentionScrollView.contentLayoutGuide.bottomAnchor),
            mentionStackView.heightAnchor.constraint(equalTo: mentionScrollView.frameLayoutGuide.heightAnchor)
        ])
    }

    func setupEmojiPanel(in contentView: UIView) {
        emojiPanelView.backgroundColor = UIColor(white: 0.96, alpha: 0.98)
        emojiPanelView.clipsToBounds = true
        emojiPanelView.isHidden = true
        emojiPanelView.translatesAutoresizingMaskIntoConstraints = false
        contentView.addSubview(emojiPanelView)

        let titleLabel = UILabel()
        titleLabel.text = "\u{6240}\u{6709}\u{8868}\u{60c5}"
        titleLabel.font = .systemFont(ofSize: 15, weight: .semibold)
        titleLabel.textColor = UIColor(red: 0.14, green: 0.16, blue: 0.18, alpha: 1)
        titleLabel.translatesAutoresizingMaskIntoConstraints = false
        emojiPanelView.addSubview(titleLabel)

        emojiScrollView.showsVerticalScrollIndicator = false
        emojiScrollView.translatesAutoresizingMaskIntoConstraints = false
        emojiPanelView.addSubview(emojiScrollView)

        emojiGridView.axis = .vertical
        emojiGridView.spacing = 10
        emojiGridView.translatesAutoresizingMaskIntoConstraints = false
        emojiScrollView.addSubview(emojiGridView)

        let columns = 8
        var currentRow: UIStackView?
        for (index, emoji) in emojiItems.enumerated() {
            if index % columns == 0 {
                let row = UIStackView()
                row.axis = .horizontal
                row.distribution = .fillEqually
                row.spacing = 8
                emojiGridView.addArrangedSubview(row)
                currentRow = row
            }

            let button = UIButton(type: .system)
            button.setTitle(emoji, for: .normal)
            button.titleLabel?.font = .systemFont(ofSize: emoji.count > 2 ? 18 : 28)
            button.backgroundColor = .clear
            button.layer.cornerRadius = 8
            button.tag = index
            button.addTarget(self, action: #selector(sendEmojiFromPanel(_:)), for: .touchUpInside)
            button.heightAnchor.constraint(equalToConstant: 38).isActive = true
            currentRow?.addArrangedSubview(button)
        }

        NSLayoutConstraint.activate([
            titleLabel.topAnchor.constraint(equalTo: emojiPanelView.topAnchor, constant: 14),
            titleLabel.leadingAnchor.constraint(equalTo: emojiPanelView.leadingAnchor, constant: 16),
            titleLabel.trailingAnchor.constraint(lessThanOrEqualTo: emojiPanelView.trailingAnchor, constant: -16),

            emojiScrollView.topAnchor.constraint(equalTo: titleLabel.bottomAnchor, constant: 14),
            emojiScrollView.leadingAnchor.constraint(equalTo: emojiPanelView.leadingAnchor, constant: 16),
            emojiScrollView.trailingAnchor.constraint(equalTo: emojiPanelView.trailingAnchor, constant: -16),
            emojiScrollView.bottomAnchor.constraint(equalTo: emojiPanelView.bottomAnchor, constant: -12),

            emojiGridView.topAnchor.constraint(equalTo: emojiScrollView.contentLayoutGuide.topAnchor),
            emojiGridView.leadingAnchor.constraint(equalTo: emojiScrollView.contentLayoutGuide.leadingAnchor),
            emojiGridView.trailingAnchor.constraint(equalTo: emojiScrollView.contentLayoutGuide.trailingAnchor),
            emojiGridView.bottomAnchor.constraint(equalTo: emojiScrollView.contentLayoutGuide.bottomAnchor),
            emojiGridView.widthAnchor.constraint(equalTo: emojiScrollView.frameLayoutGuide.widthAnchor)
        ])
    }

    func setupKeyboardObservers() {
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(handleKeyboardFrameChange(_:)),
            name: UIResponder.keyboardWillChangeFrameNotification,
            object: nil
        )
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(handleKeyboardFrameChange(_:)),
            name: UIResponder.keyboardWillHideNotification,
            object: nil
        )
    }

    func setupSnapshotDragObserver() {
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(handleSafeAreaSnapshotDrag(_:)),
            name: .safeAreaSnapshotDragDidUpdate,
            object: nil
        )
    }

    func setupScreenshotProtectionObserver() {
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(handleUserDidTakeScreenshot),
            name: UIApplication.userDidTakeScreenshotNotification,
            object: nil
        )
    }

    @objc func handleSafeAreaSnapshotDrag(_ notification: Notification) {
        guard let image = notification.userInfo?[SafeAreaSnapshotDragUserInfoKey.image] as? UIImage,
              let stateRawValue = notification.userInfo?[SafeAreaSnapshotDragUserInfoKey.state] as? Int,
              let state = UIGestureRecognizer.State(rawValue: stateRawValue),
              let pointValue = notification.userInfo?[SafeAreaSnapshotDragUserInfoKey.screenPoint] as? NSValue
        else { return }

        let title = notification.userInfo?[SafeAreaSnapshotDragUserInfoKey.title] as? String ?? "页面快照"
        let payload = MediaDragPayload(
            title: title,
            url: nil,
            image: image,
            isVideo: false
        )
        handlePreviewMediaDrag(
            payload: payload,
            state: state,
            screenPoint: pointValue.cgPointValue
        )
    }

    @objc func handleUserDidTakeScreenshot() {
        guard !isScreenshotBlurPromptVisible else { return }
        isScreenshotBlurPromptVisible = true
        let alert = UIAlertController(
            title: "检测到截图",
            message: "iOS 已保存当前截图。你可以开启图片/视频模糊保护，后续截图时聊天中的图片、视频和 GIF 会自动模糊显示。",
            preferredStyle: .alert
        )
        alert.addAction(UIAlertAction(title: "模糊图片/视频", style: .default) { [weak self] _ in
            guard let self else { return }
            self.screenshotMediaBlurEnabled = true
            self.isScreenshotBlurPromptVisible = false
            self.messageCollectionView.reloadData()
            self.updateConnections()
            self.showNotice("已开启截图模糊保护")
        })
        alert.addAction(UIAlertAction(title: "不需要模糊", style: .cancel) { [weak self] _ in
            guard let self else { return }
            self.screenshotMediaBlurEnabled = false
            self.isScreenshotBlurPromptVisible = false
            self.messageCollectionView.reloadData()
            self.updateConnections()
            self.showNotice("已保持原图显示")
        })
        present(alert, animated: true)
    }

    func setupGestures() {
        let outsideTap = UITapGestureRecognizer(target: self, action: #selector(handleGlobalTap(_:)))
        outsideTap.cancelsTouchesInView = false
        outsideTap.delegate = self
        view.addGestureRecognizer(outsideTap)

        let headerLongPress = UILongPressGestureRecognizer(target: self, action: #selector(handleHeaderLongPress(_:)))
        headerLongPress.minimumPressDuration = 0.45
        headerView.addGestureRecognizer(headerLongPress)

        let islandTap = UITapGestureRecognizer(target: self, action: #selector(expandFromLocalIsland))
        islandTap.cancelsTouchesInView = false
        chromeView.addGestureRecognizer(islandTap)

        let leftParticipantLongPress = UILongPressGestureRecognizer(target: self, action: #selector(handleLeftParticipantLongPress(_:)))
        leftParticipantLongPress.minimumPressDuration = 0.45
        leftParticipantLongPress.cancelsTouchesInView = true
        leftParticipantLongPress.delegate = self
        leftCollectionView.addGestureRecognizer(leftParticipantLongPress)
        leftCollectionView.panGestureRecognizer.addTarget(self, action: #selector(handleLeftListPan(_:)))
        let leftScrollPreviewPan = UIPanGestureRecognizer(target: self, action: #selector(handleLeftListPan(_:)))
        leftScrollPreviewPan.cancelsTouchesInView = false
        leftScrollPreviewPan.delaysTouchesBegan = false
        leftScrollPreviewPan.delaysTouchesEnded = false
        leftScrollPreviewPan.delegate = self
        leftCollectionView.addGestureRecognizer(leftScrollPreviewPan)
        leftScrollPreviewPanGesture = leftScrollPreviewPan

        let rightToolLongPress = UILongPressGestureRecognizer(target: self, action: #selector(handleRightToolLongPress(_:)))
        rightToolLongPress.minimumPressDuration = 0.45
        rightToolLongPress.cancelsTouchesInView = true
        rightToolCollectionView.addGestureRecognizer(rightToolLongPress)

        installGlobalBlinkControlGestures()

        let messageTapTracker = UITapGestureRecognizer(target: self, action: #selector(trackMessageTap(_:)))
        messageTapTracker.cancelsTouchesInView = false
        messageTapTracker.delegate = self
        messageCollectionView.addGestureRecognizer(messageTapTracker)
        blinkClosedAutoReplyTrigger.onStatus = { [weak self] status in
            self?.showNotice(status)
        }
        blinkClosedAutoReplyTrigger.onTouchIsolationChanged = { [weak self] isIsolating in
            guard let self else { return }
            self.suppressMessageTap(for: isIsolating ? 0.85 : 0.35)
            self.setBlinkInteractionPerformanceIsolation(isIsolating)
        }
        blinkClosedAutoReplyTrigger.onSingleBlink = { [weak self] in
            self?.openDouyinFromRightEdgeGesture()
        }
        blinkClosedAutoReplyTrigger.onDoubleBlink = { [weak self] in
            guard let self else { return }
            if self.isHomeTimeline {
                self.triggerAutoReplyForLatestCurrentMessage()
            } else {
                self.presentCodexAssistantPage()
            }
        }
        blinkClosedAutoReplyTrigger.onTriggered = { [weak self] in
            self?.triggerAutoReplyForLatestCurrentMessage()
        }
        inputLongEyeRewriteTrigger.onStatus = { [weak self] status in
            self?.showNotice(status)
        }
        inputLongEyeRewriteTrigger.onTriggered = { [weak self] in
            self?.handleInputLongEyeRewriteTrigger()
        }

        messageCollectionView.panGestureRecognizer.addTarget(self, action: #selector(handleMessageCollectionPanForProfile(_:)))

        let rightEdgeDouyinPan = UIPanGestureRecognizer(target: self, action: #selector(handleRightEdgeDouyinPan(_:)))
        rightEdgeDouyinPan.cancelsTouchesInView = false
        rightEdgeDouyinPan.delegate = self
        rightRailContainer.addGestureRecognizer(rightEdgeDouyinPan)
        rightEdgeDouyinPanGesture = rightEdgeDouyinPan

        let leftEdgePan = UIPanGestureRecognizer(target: self, action: #selector(handleLeftEdgePan(_:)))
        leftEdgePan.cancelsTouchesInView = false
        leftEdgePan.delegate = self
        leftCollectionView.addGestureRecognizer(leftEdgePan)
        leftEdgePanGesture = leftEdgePan
    }

    func installGlobalBlinkControlGestures() {
        guard isViewLoaded else { return }
        let hostView = view.window ?? navigationController?.view ?? view!
        blinkClosedAutoReplyTrigger.install(on: hostView)
    }

    @objc func trackMessageTap(_ gesture: UITapGestureRecognizer) {
        guard gesture.state == .ended else { return }
        guard !isMessageTapSuppressed else { return }
        lastMessageTapLocation = gesture.location(in: messageCollectionView)
        dismissComposerInput(animated: true)
    }

    var isMessageTapSuppressed: Bool {
        Date() < suppressMessageTapUntil
    }

    func suppressMessageTap(for interval: TimeInterval) {
        suppressMessageTapUntil = Date().addingTimeInterval(interval)
        lastMessageTapLocation = nil
    }

    func setBlinkInteractionPerformanceIsolation(_ isIsolating: Bool) {
        if isIsolating {
            for collectionView in [
                messageCollectionView,
                leftCollectionView,
                rightAccountCollectionView,
                rightToolCollectionView
            ] {
                collectionView.setContentOffset(collectionView.contentOffset, animated: false)
                collectionView.layer.removeAllAnimations()
            }
            stopFrameCoordinator(clearLeftScrollWork: true)
        } else {
            scheduleConnectionUpdate()
        }
    }

    @objc func handleMessageCollectionPanForProfile(_ gesture: UIPanGestureRecognizer) {
        switch gesture.state {
        case .began:
            conversationProfilePanStartPoint = gesture.location(in: view)
            return
        case .ended:
            break
        default:
            return
        }

        if let lastConversationProfileOpenAt,
           Date().timeIntervalSince(lastConversationProfileOpenAt) < 1.2 {
            return
        }

        let endPoint = gesture.location(in: view)
        let translation = gesture.translation(in: messageCollectionView)
        let velocity = gesture.velocity(in: messageCollectionView)
        let horizontalDistance = conversationProfilePanStartPoint.x - endPoint.x
        let verticalDistance = abs(endPoint.y - conversationProfilePanStartPoint.y)
        let isDeliberateLeftSwipe = horizontalDistance > 96
            && translation.x < -88
            && horizontalDistance > verticalDistance * 1.65
        let isFastLeftSwipe = horizontalDistance > 52
            && velocity.x < -720
            && abs(velocity.x) > abs(velocity.y) * 1.8
        guard isDeliberateLeftSwipe || isFastLeftSwipe else { return }
        lastConversationProfileOpenAt = Date()
        openActiveFriendInfoHomeFromGesture()
    }

    @objc func handleLeftListPan(_ gesture: UIPanGestureRecognizer) {
        let translation = gesture.translation(in: leftCollectionView)
        let velocity = gesture.velocity(in: leftCollectionView)
        let isVerticalDrag = abs(translation.y) > 3 || abs(velocity.y) > 24
        switch gesture.state {
        case .began, .changed:
            guard isVerticalDrag else { return }
            updateLeftScrollPreview()
        case .ended, .cancelled, .failed:
            hideLeftScrollPreview(animated: true)
        default:
            break
        }
    }

    @objc func handleRightEdgeDouyinPan(_ gesture: UIPanGestureRecognizer) {
        switch gesture.state {
        case .began:
            setRightRailScrollingEnabled(false)
            isRightEdgeGestureActive = true
            rightEdgeDouyinGestureStartPoint = gesture.location(in: rightEdgeDouyinFeedbackView)
            rightEdgeGestureCurrentTarget = nil
            updateRightEdgeDouyinFeedback(for: gesture, target: nil)
        case .changed:
            let target = edgeLaunchTarget(for: gesture)
            rightEdgeGestureCurrentTarget = target
            updateRightEdgeDouyinFeedback(for: gesture, target: target)
        case .ended:
            let target = edgeLaunchTarget(for: gesture)
            hideRightEdgeDouyinFeedback()
            launchRightEdgeTargetIfNeeded(target, gesture: gesture)
            rightEdgeGestureCurrentTarget = nil
            isRightEdgeGestureActive = false
            setRightRailScrollingEnabled(true)
        case .cancelled, .failed:
            hideRightEdgeDouyinFeedback()
            rightEdgeGestureCurrentTarget = nil
            isRightEdgeGestureActive = false
            setRightRailScrollingEnabled(true)
        default:
            break
        }
    }

    @objc func handleLeftEdgePan(_ gesture: UIPanGestureRecognizer) {
        switch gesture.state {
        case .began:
            isLeftEdgePullConfirmed = false
            isLeftEdgeGestureActive = false
            leftEdgeGestureStartPoint = gesture.location(in: leftEdgeFeedbackView)
            leftEdgeGestureCurrentTarget = nil
            hideLeftEdgeFeedback()
        case .changed:
            let translation = gesture.translation(in: leftCollectionView)
            if !isLeftEdgePullConfirmed {
                let isRightPull = translation.x > 18 && abs(translation.x) > abs(translation.y) * 1.25
                let isVerticalScroll = abs(translation.y) > 8 && abs(translation.y) >= abs(translation.x)
                if isVerticalScroll {
                    hideLeftEdgeFeedback()
                    return
                }
                guard isRightPull else { return }
                isLeftEdgePullConfirmed = true
                isLeftEdgeGestureActive = true
                leftCollectionView.isScrollEnabled = false
                leftEdgeGestureStartPoint = gesture.location(in: leftEdgeFeedbackView)
            }
            let target = leftEdgeLaunchTarget(for: gesture)
            leftEdgeGestureCurrentTarget = target
            updateLeftEdgeFeedback(for: gesture, target: target)
        case .ended:
            guard isLeftEdgePullConfirmed else {
                hideLeftEdgeFeedback()
                leftEdgeGestureCurrentTarget = nil
                isLeftEdgeGestureActive = false
                leftCollectionView.isScrollEnabled = true
                return
            }
            let target = leftEdgeLaunchTarget(for: gesture)
            hideLeftEdgeFeedback()
            launchLeftEdgeTargetIfNeeded(target, gesture: gesture)
            leftEdgeGestureCurrentTarget = nil
            isLeftEdgeGestureActive = false
            isLeftEdgePullConfirmed = false
            leftCollectionView.isScrollEnabled = true
        case .cancelled, .failed:
            hideLeftEdgeFeedback()
            leftEdgeGestureCurrentTarget = nil
            isLeftEdgeGestureActive = false
            isLeftEdgePullConfirmed = false
            leftCollectionView.isScrollEnabled = true
        default:
            break
        }
    }

    func setRightRailScrollingEnabled(_ isEnabled: Bool) {
        rightAccountCollectionView.isScrollEnabled = isEnabled
        rightToolCollectionView.isScrollEnabled = isEnabled
    }

    func edgeLaunchTarget(for gesture: UIPanGestureRecognizer) -> EdgeLaunchTarget? {
        let translation = gesture.translation(in: rightRailContainer)
        let horizontalPull = -translation.x
        guard horizontalPull > 44 else { return nil }
        if translation.y < -28 {
            return .douyin
        }
        if translation.y > 28 {
            return .codex
        }
        return .wechat
    }

    func launchRightEdgeTargetIfNeeded(_ target: EdgeLaunchTarget?, gesture: UIPanGestureRecognizer) {
        let translation = gesture.translation(in: rightRailContainer)
        guard -translation.x > 58, let target else { return }
        switch target {
        case .douyin:
            openDouyinFromRightEdgeGesture()
        case .wechat:
            openWechatFromRightEdgeGesture()
        case .codex:
            presentCodexAssistantPage()
        }
    }

    func leftEdgeLaunchTarget(for gesture: UIPanGestureRecognizer) -> EdgeLaunchTarget? {
        let translation = gesture.translation(in: leftCollectionView)
        let horizontalPull = translation.x
        guard horizontalPull > 44 else { return nil }
        if translation.y < -28 {
            return .douyin
        }
        if translation.y > 28 {
            return .codex
        }
        return .wechat
    }

    func launchLeftEdgeTargetIfNeeded(_ target: EdgeLaunchTarget?, gesture: UIPanGestureRecognizer) {
        let translation = gesture.translation(in: leftCollectionView)
        guard translation.x > 58, let target else { return }
        switch target {
        case .douyin:
            openDouyinFromRightEdgeGesture()
        case .wechat:
            openWechatFromRightEdgeGesture()
        case .codex:
            presentCodexAssistantPage()
        }
    }

    func updateRightEdgeDouyinFeedback(for gesture: UIPanGestureRecognizer, target: EdgeLaunchTarget?) {
        let point = gesture.location(in: rightEdgeDouyinFeedbackView)
        let translation = gesture.translation(in: rightEdgeDouyinFeedbackView)
        let horizontalPull = max(0, -translation.x)
        let verticalPull = abs(translation.y)
        let progress = min(1, max(horizontalPull / 86, verticalPull / 160))
        guard progress > 0.04 || gesture.state == .began else { return }

        let clampedPoint = CGPoint(
            x: min(max(point.x, rightEdgeDouyinFeedbackView.bounds.minX + 28), rightEdgeDouyinFeedbackView.bounds.maxX - 20),
            y: min(max(point.y, rightEdgeDouyinFeedbackView.bounds.minY + 24), rightEdgeDouyinFeedbackView.bounds.maxY - 44)
        )
        let startY = min(max(rightEdgeDouyinGestureStartPoint.y, 58), rightEdgeDouyinFeedbackView.bounds.maxY - 58)
        let clampedStart = CGPoint(
            x: rightEdgeDouyinFeedbackView.bounds.maxX,
            y: startY
        )
        let target = target ?? rightEdgeGestureCurrentTarget
        let baseColor = sideEffectFillColor.withAlphaComponent(0.76 + progress * 0.18)
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        let curvePath = rightEdgePullPath(start: clampedStart, current: clampedPoint, progress: progress)
        rightEdgeDouyinBlobLayer.path = curvePath.cgPath
        rightEdgeDouyinBlobLayer.fillColor = baseColor.cgColor
        rightEdgeDouyinBlobLayer.shadowOpacity = Float(0.28 + progress * 0.28)
        rightEdgeDouyinBlobLayer.shadowRadius = 6 + progress * 6
        rightEdgeDouyinRimLayer.path = rightEdgeRimPath(start: clampedStart, current: clampedPoint, progress: progress).cgPath
        rightEdgeDouyinRimLayer.strokeColor = edgeRimColor(for: target, progress: progress).cgColor
        rightEdgeDouyinHighlightLayer.path = rightEdgePullHighlightPath(start: clampedStart, current: clampedPoint, progress: progress).cgPath
        rightEdgeDouyinHighlightLayer.fillColor = UIColor.white.withAlphaComponent(0.22 + progress * 0.16).cgColor

        let trailPath = rightEdgePullTrailPath(start: clampedStart, current: clampedPoint, progress: progress)
        rightEdgeDouyinTrailLayer.path = trailPath.cgPath
        rightEdgeDouyinTrailLayer.strokeColor = edgeRimColor(for: target, progress: progress * 0.5).withAlphaComponent(0.18).cgColor
        rightEdgeDouyinTrailLayer.lineWidth = 1.2 + progress * 1.2
        CATransaction.commit()

        let arrowCenter = clampedRightEdgeContentCenter(start: clampedStart, current: clampedPoint)
        if let target {
            let symbolName: String
            switch target {
            case .douyin:
                symbolName = "music.note"
            case .wechat:
                symbolName = "message.fill"
            case .codex:
                symbolName = "sparkles"
            }
            rightEdgeDouyinArrowView.image = UIImage(systemName: symbolName)
        } else {
            rightEdgeDouyinArrowView.image = UIImage(systemName: "chevron.left")
        }
        rightEdgeDouyinArrowView.center = arrowCenter
        rightEdgeDouyinArrowView.transform = CGAffineTransform(scaleX: 0.76 + progress * 0.24, y: 0.76 + progress * 0.24)
        rightEdgeDouyinArrowView.backgroundColor = UIColor.white.withAlphaComponent(target == nil ? 0.46 : 0.74)
        rightEdgeDouyinArrowView.layer.borderColor = edgeRimColor(for: target, progress: progress).withAlphaComponent(0.30).cgColor
        rightEdgeDouyinHintLabel.center = CGPoint(x: arrowCenter.x, y: arrowCenter.y + 28)
        rightEdgeDouyinHintLabel.center = clampedRightEdgeLabelCenter(
            proposed: rightEdgeDouyinHintLabel.center,
            start: clampedStart,
            current: clampedPoint
        )
        rightEdgeDouyinHintLabel.text = target?.title ?? "左拉微信"
        rightEdgeDouyinHintLabel.backgroundColor = UIColor.white.withAlphaComponent(0.62)
        rightEdgeDouyinHintLabel.alpha = progress > 0.46 ? 1 : 0

        if rightEdgeDouyinFeedbackView.alpha == 0 {
            rightEdgeDouyinFeedbackView.alpha = 1
        }
    }

    func updateLeftEdgeFeedback(for gesture: UIPanGestureRecognizer, target: EdgeLaunchTarget?) {
        let point = gesture.location(in: leftEdgeFeedbackView)
        let translation = gesture.translation(in: leftEdgeFeedbackView)
        let horizontalPull = max(0, translation.x)
        let progress = min(1, horizontalPull / 86)
        guard progress > 0.08 else { return }

        let clampedPoint = CGPoint(
            x: min(max(point.x, leftEdgeFeedbackView.bounds.minX + 20), leftEdgeFeedbackView.bounds.maxX - 28),
            y: min(max(point.y, leftEdgeFeedbackView.bounds.minY + 24), leftEdgeFeedbackView.bounds.maxY - 44)
        )
        let startY = min(max(leftEdgeGestureStartPoint.y, 58), leftEdgeFeedbackView.bounds.maxY - 58)
        let start = CGPoint(x: leftEdgeFeedbackView.bounds.minX, y: startY)
        let target = target ?? leftEdgeGestureCurrentTarget
        let baseColor = sideEffectFillColor.withAlphaComponent(0.76 + progress * 0.18)

        CATransaction.begin()
        CATransaction.setDisableActions(true)
        let curvePath = leftEdgePullPath(start: start, current: clampedPoint, progress: progress)
        leftEdgeBlobLayer.path = curvePath.cgPath
        leftEdgeBlobLayer.fillColor = baseColor.cgColor
        leftEdgeBlobLayer.shadowOpacity = Float(0.28 + progress * 0.28)
        leftEdgeBlobLayer.shadowRadius = 6 + progress * 6
        leftEdgeRimLayer.path = leftEdgeRimPath(start: start, current: clampedPoint, progress: progress).cgPath
        leftEdgeRimLayer.strokeColor = edgeRimColor(for: target, progress: progress).cgColor
        leftEdgeHighlightLayer.path = leftEdgePullHighlightPath(start: start, current: clampedPoint, progress: progress).cgPath
        leftEdgeHighlightLayer.fillColor = UIColor.white.withAlphaComponent(0.22 + progress * 0.16).cgColor
        let trailPath = leftEdgePullTrailPath(start: start, current: clampedPoint, progress: progress)
        leftEdgeTrailLayer.path = trailPath.cgPath
        leftEdgeTrailLayer.strokeColor = edgeRimColor(for: target, progress: progress * 0.5).withAlphaComponent(0.18).cgColor
        leftEdgeTrailLayer.lineWidth = 1.2 + progress * 1.2
        CATransaction.commit()

        let iconCenter = clampedLeftEdgeContentCenter(start: start, current: clampedPoint)
        if let target {
            let symbolName: String
            switch target {
            case .douyin:
                symbolName = "music.note"
            case .wechat:
                symbolName = "message.fill"
            case .codex:
                symbolName = "sparkles"
            }
            leftEdgeArrowView.image = UIImage(systemName: symbolName)
        } else {
            leftEdgeArrowView.image = UIImage(systemName: "chevron.right")
        }
        leftEdgeArrowView.center = iconCenter
        leftEdgeArrowView.transform = CGAffineTransform(scaleX: 0.76 + progress * 0.24, y: 0.76 + progress * 0.24)
        leftEdgeArrowView.backgroundColor = UIColor.white.withAlphaComponent(target == nil ? 0.46 : 0.74)
        leftEdgeArrowView.layer.borderColor = edgeRimColor(for: target, progress: progress).withAlphaComponent(0.30).cgColor

        leftEdgeHintLabel.center = clampedLeftEdgeLabelCenter(
            proposed: CGPoint(x: iconCenter.x, y: iconCenter.y + 28),
            start: start,
            current: clampedPoint
        )
        leftEdgeHintLabel.text = target?.title ?? "右拉微信"
        leftEdgeHintLabel.backgroundColor = UIColor.white.withAlphaComponent(0.62)
        leftEdgeHintLabel.alpha = progress > 0.46 ? 1 : 0

        if leftEdgeFeedbackView.alpha == 0 {
            leftEdgeFeedbackView.alpha = 1
        }
    }

    func edgeRimColor(for target: EdgeLaunchTarget?, progress: CGFloat) -> UIColor {
        switch target {
        case .douyin:
            return UIColor(red: 0.44, green: 0.42, blue: 0.60, alpha: 0.28 + progress * 0.20)
        case .wechat:
            return UIColor(red: 0.12, green: 0.72, blue: 0.52, alpha: 0.30 + progress * 0.22)
        case .codex:
            return UIColor(red: 0.32, green: 0.49, blue: 0.98, alpha: 0.30 + progress * 0.22)
        case .none:
            return sideEffectStrokeColor.withAlphaComponent(0.30 + progress * 0.16)
        }
    }

    func sideEffectTuning() -> (minHeight: CGFloat, maxHeight: CGFloat, baseHeight: CGFloat, verticalFactor: CGFloat, progressHeight: CGFloat, depthScale: CGFloat, centerFactor: CGFloat) {
        switch sideEffectTemplateIndex {
        case 1:
            return (70, 118, 64, 0.28, 10, 0.78, 0.72)
        case 2:
            return (150, 238, 128, 1.06, 38, 1.00, 0.36)
        case 3:
            return (58, 108, 52, 0.48, 8, 0.58, 0.44)
        case 4:
            return (112, 166, 106, 0.20, 12, 0.72, 0.58)
        case 5:
            return (104, 214, 84, 1.26, 48, 1.16, 0.46)
        default:
            return (98, 170, 86, 0.78, 22, 1.0, 0.42)
        }
    }

    func rightEdgePullPath(start: CGPoint, current: CGPoint, progress: CGFloat) -> UIBezierPath {
        edgeEffectPath(start: start, current: current, progress: progress, isLeft: false, closes: true)
    }

    func leftEdgePullPath(start: CGPoint, current: CGPoint, progress: CGFloat) -> UIBezierPath {
        edgeEffectPath(start: start, current: current, progress: progress, isLeft: true, closes: true)
    }

    func rightEdgeRimPath(start: CGPoint, current: CGPoint, progress: CGFloat) -> UIBezierPath {
        edgeEffectPath(start: start, current: current, progress: progress, isLeft: false, closes: false)
    }

    func leftEdgeRimPath(start: CGPoint, current: CGPoint, progress: CGFloat) -> UIBezierPath {
        edgeEffectPath(start: start, current: current, progress: progress, isLeft: true, closes: false)
    }

    func edgeEffectPath(start: CGPoint, current: CGPoint, progress: CGFloat, isLeft: Bool, closes: Bool) -> UIBezierPath {
        let tuning = sideEffectTuning()
        let rawDepth = (isLeft ? current.x - start.x : start.x - current.x) + 26
        let depth = min(122, max(28, rawDepth * tuning.depthScale))
        let verticalOffset = current.y - start.y
        let centerY = start.y + verticalOffset * tuning.centerFactor
        let panelHeight = min(tuning.maxHeight, max(tuning.minHeight, abs(verticalOffset) * tuning.verticalFactor + tuning.baseHeight + progress * tuning.progressHeight))
        let top = CGPoint(x: start.x, y: centerY - panelHeight * 0.50)
        let bottom = CGPoint(x: start.x, y: centerY + panelHeight * 0.50)
        let sign: CGFloat = isLeft ? 1 : -1
        let vertex = CGPoint(x: start.x + sign * depth, y: current.y)
        let path = UIBezierPath()
        path.move(to: top)
        switch sideEffectTemplateIndex {
        case 1:
            path.addCurve(
                to: bottom,
                controlPoint1: CGPoint(x: start.x + sign * depth * 0.92, y: centerY - panelHeight * 0.34),
                controlPoint2: CGPoint(x: start.x + sign * depth * 0.92, y: centerY + panelHeight * 0.34)
            )
        case 2:
            let waist = start.x + sign * depth * 0.18
            path.addCurve(
                to: CGPoint(x: waist, y: centerY - panelHeight * 0.22),
                controlPoint1: CGPoint(x: start.x + sign * depth * 0.06, y: top.y + panelHeight * 0.18),
                controlPoint2: CGPoint(x: waist, y: centerY - panelHeight * 0.34)
            )
            path.addQuadCurve(to: CGPoint(x: waist, y: centerY + panelHeight * 0.22), controlPoint: vertex)
            path.addCurve(
                to: bottom,
                controlPoint1: CGPoint(x: waist, y: centerY + panelHeight * 0.34),
                controlPoint2: CGPoint(x: start.x + sign * depth * 0.06, y: bottom.y - panelHeight * 0.18)
            )
        case 3:
            path.addQuadCurve(
                to: bottom,
                controlPoint: CGPoint(x: start.x + sign * depth * 0.68, y: current.y)
            )
        case 4:
            path.addCurve(
                to: bottom,
                controlPoint1: CGPoint(x: start.x + sign * depth * 0.74, y: top.y + panelHeight * 0.18),
                controlPoint2: CGPoint(x: start.x + sign * depth * 0.74, y: bottom.y - panelHeight * 0.18)
            )
        case 5:
            let mid = CGPoint(x: start.x + sign * depth * 0.72, y: current.y)
            path.addCurve(
                to: mid,
                controlPoint1: CGPoint(x: start.x + sign * depth * 0.12, y: top.y + panelHeight * 0.16),
                controlPoint2: CGPoint(x: start.x + sign * depth * 1.18, y: mid.y - panelHeight * 0.30)
            )
            path.addCurve(
                to: bottom,
                controlPoint1: CGPoint(x: start.x + sign * depth * 1.18, y: mid.y + panelHeight * 0.30),
                controlPoint2: CGPoint(x: start.x + sign * depth * 0.12, y: bottom.y - panelHeight * 0.16)
            )
        default:
            path.addQuadCurve(to: bottom, controlPoint: vertex)
        }
        if closes {
            path.close()
        }
        return path
    }

    func rightEdgePullHeadCenter(start: CGPoint, current: CGPoint) -> CGPoint {
        let depth = min(96, max(38, start.x - current.x + 26))
        let leftLimit = start.x - depth * 0.42
        let rightLimit = start.x - 30
        return CGPoint(
            x: min(max(start.x - depth * 0.32, leftLimit), rightLimit),
            y: min(max(current.y, 28), rightEdgeDouyinFeedbackView.bounds.maxY - 44)
        )
    }

    func clampedRightEdgeContentCenter(start: CGPoint, current: CGPoint) -> CGPoint {
        let base = rightEdgePullHeadCenter(start: start, current: current)
        let depth = min(96, max(38, start.x - current.x + 26))
        let iconHalfWidth: CGFloat = 18
        let minX = start.x - depth * 0.58 + iconHalfWidth + 4
        let maxX = start.x - iconHalfWidth - 8
        return CGPoint(
            x: min(max(base.x, minX), maxX),
            y: base.y
        )
    }

    func clampedRightEdgeLabelCenter(proposed: CGPoint, start: CGPoint, current: CGPoint) -> CGPoint {
        let depth = min(96, max(38, start.x - current.x + 26))
        let halfWidth = rightEdgeDouyinHintLabel.bounds.width * 0.5
        let minX = start.x - depth * 0.62 + halfWidth + 6
        let maxX = start.x - halfWidth - 8
        return CGPoint(
            x: min(max(proposed.x, minX), maxX),
            y: proposed.y
        )
    }

    func leftEdgePullHeadCenter(start: CGPoint, current: CGPoint) -> CGPoint {
        let depth = min(96, max(38, current.x - start.x + 26))
        let leftLimit = start.x + 30
        let rightLimit = start.x + depth * 0.42
        return CGPoint(
            x: min(max(start.x + depth * 0.32, leftLimit), rightLimit),
            y: min(max(current.y, 28), leftEdgeFeedbackView.bounds.maxY - 44)
        )
    }

    func clampedLeftEdgeContentCenter(start: CGPoint, current: CGPoint) -> CGPoint {
        let base = leftEdgePullHeadCenter(start: start, current: current)
        let depth = min(96, max(38, current.x - start.x + 26))
        let iconHalfWidth: CGFloat = 18
        let minX = start.x + iconHalfWidth + 8
        let maxX = start.x + depth * 0.58 - iconHalfWidth - 4
        return CGPoint(
            x: min(max(base.x, minX), maxX),
            y: base.y
        )
    }

    func clampedLeftEdgeLabelCenter(proposed: CGPoint, start: CGPoint, current: CGPoint) -> CGPoint {
        let depth = min(96, max(38, current.x - start.x + 26))
        let halfWidth = leftEdgeHintLabel.bounds.width * 0.5
        let minX = start.x + halfWidth + 8
        let maxX = start.x + depth * 0.62 - halfWidth - 6
        return CGPoint(
            x: min(max(proposed.x, minX), maxX),
            y: proposed.y
        )
    }

    func rightEdgePullHighlightPath(start: CGPoint, current: CGPoint, progress: CGFloat) -> UIBezierPath {
        let depth = min(74, max(28, start.x - current.x + 12))
        let verticalOffset = current.y - start.y
        let centerY = start.y + verticalOffset * 0.42
        let panelHeight = min(126, max(66, abs(verticalOffset) * 0.50 + 52 + progress * 14))
        let top = CGPoint(x: start.x - 7, y: centerY - panelHeight * 0.34)
        let bottom = CGPoint(x: start.x - 7, y: centerY + panelHeight * 0.16)
        let peak = CGPoint(x: start.x - depth * 0.62, y: current.y - panelHeight * 0.02)
        let path = UIBezierPath()
        path.move(to: top)
        path.addQuadCurve(
            to: bottom,
            controlPoint: peak
        )
        path.close()
        return path
    }

    func leftEdgePullHighlightPath(start: CGPoint, current: CGPoint, progress: CGFloat) -> UIBezierPath {
        let depth = min(74, max(28, current.x - start.x + 12))
        let verticalOffset = current.y - start.y
        let centerY = start.y + verticalOffset * 0.42
        let panelHeight = min(126, max(66, abs(verticalOffset) * 0.50 + 52 + progress * 14))
        let top = CGPoint(x: start.x + 7, y: centerY - panelHeight * 0.34)
        let bottom = CGPoint(x: start.x + 7, y: centerY + panelHeight * 0.16)
        let peak = CGPoint(x: start.x + depth * 0.62, y: current.y - panelHeight * 0.02)
        let path = UIBezierPath()
        path.move(to: top)
        path.addQuadCurve(to: bottom, controlPoint: peak)
        path.close()
        return path
    }

    func rightEdgePullTrailPath(start: CGPoint, current: CGPoint, progress: CGFloat) -> UIBezierPath {
        let depth = min(70, max(24, start.x - current.x + 10))
        let path = UIBezierPath()
        path.move(to: CGPoint(x: start.x - 6, y: current.y))
        path.addCurve(
            to: CGPoint(x: start.x - depth * 0.78, y: current.y),
            controlPoint1: CGPoint(x: start.x - depth * 0.18, y: current.y),
            controlPoint2: CGPoint(x: start.x - depth * 0.48, y: current.y)
        )
        return path
    }

    func leftEdgePullTrailPath(start: CGPoint, current: CGPoint, progress: CGFloat) -> UIBezierPath {
        let depth = min(70, max(24, current.x - start.x + 10))
        let path = UIBezierPath()
        path.move(to: CGPoint(x: start.x + 6, y: current.y))
        path.addCurve(
            to: CGPoint(x: start.x + depth * 0.78, y: current.y),
            controlPoint1: CGPoint(x: start.x + depth * 0.18, y: current.y),
            controlPoint2: CGPoint(x: start.x + depth * 0.48, y: current.y)
        )
        return path
    }

    func hideRightEdgeDouyinFeedback() {
        UIView.animate(withDuration: 0.18, delay: 0, options: [.curveEaseOut, .beginFromCurrentState]) {
            self.rightEdgeDouyinFeedbackView.alpha = 0
            self.rightEdgeDouyinArrowView.transform = CGAffineTransform(scaleX: 0.75, y: 0.75)
            self.rightEdgeDouyinHintLabel.alpha = 0
        } completion: { _ in
            self.rightEdgeDouyinBlobLayer.path = nil
            self.rightEdgeDouyinRimLayer.path = nil
            self.rightEdgeDouyinHighlightLayer.path = nil
            self.rightEdgeDouyinTrailLayer.path = nil
            self.rightEdgeDouyinArrowView.transform = .identity
            self.rightEdgeDouyinArrowView.image = UIImage(systemName: "chevron.left")
        }
    }

    func hideLeftEdgeFeedback() {
        UIView.animate(withDuration: 0.18, delay: 0, options: [.curveEaseOut, .beginFromCurrentState]) {
            self.leftEdgeFeedbackView.alpha = 0
            self.leftEdgeArrowView.transform = CGAffineTransform(scaleX: 0.75, y: 0.75)
            self.leftEdgeHintLabel.alpha = 0
        } completion: { _ in
            self.leftEdgeBlobLayer.path = nil
            self.leftEdgeRimLayer.path = nil
            self.leftEdgeHighlightLayer.path = nil
            self.leftEdgeTrailLayer.path = nil
            self.leftEdgeArrowView.transform = .identity
            self.leftEdgeArrowView.image = UIImage(systemName: "chevron.right")
        }
    }

    @objc func handleKeyboardFrameChange(_ notification: Notification) {
        guard windowState != .minimized,
              let userInfo = notification.userInfo,
              let keyboardFrameValue = userInfo[UIResponder.keyboardFrameEndUserInfoKey] as? NSValue
        else { return }

        let keyboardFrameInView = view.convert(keyboardFrameValue.cgRectValue, from: nil)
        let overlap = max(0, view.bounds.maxY - keyboardFrameInView.minY)
        if overlap > 0 {
            setEmojiPanelVisible(false, animated: false)
        }
        inputBarBottomConstraint?.constant = -overlap
        inputBarBottomFillView.alpha = overlap > 0 ? 0 : 1

        let duration = (userInfo[UIResponder.keyboardAnimationDurationUserInfoKey] as? NSNumber)?.doubleValue ?? 0.25
        let curveRaw = (userInfo[UIResponder.keyboardAnimationCurveUserInfoKey] as? NSNumber)?.uintValue ?? UIView.AnimationOptions.curveEaseInOut.rawValue
        let options = UIView.AnimationOptions(rawValue: curveRaw << 16)

        UIView.animate(withDuration: duration, delay: 0, options: options) {
            self.view.layoutIfNeeded()
            self.updateConnections()
        } completion: { _ in
            self.scrollVisibleMessagesToBottomIfNeeded(animated: true)
        }
    }

    func scrollVisibleMessagesToBottomIfNeeded(animated: Bool) {
        guard inputBar.textField.isFirstResponder, let indexPath = lastRenderedMessageIndexPath() else { return }
        messageCollectionView.scrollToItem(at: indexPath, at: .bottom, animated: animated)
    }

    func scrollInitialMessagesToBottomIfNeeded() {
        guard !didScrollInitialMessagesToBottom, !renderedMessages.isEmpty else { return }
        didScrollInitialMessagesToBottom = true
        scrollMessagesToBottomAfterLayout(animated: false)
    }

    func scrollMessagesToBottomAfterLayout(animated: Bool) {
        DispatchQueue.main.async { [weak self] in
            guard let self, !self.renderedMessages.isEmpty else { return }
            self.messageCollectionView.layoutIfNeeded()
            DispatchQueue.main.async { [weak self] in
                guard let self, let indexPath = self.lastRenderedMessageIndexPath() else { return }
                self.messageCollectionView.layoutIfNeeded()
                self.messageCollectionView.scrollToItem(at: indexPath, at: .bottom, animated: animated)
                self.scheduleConnectionUpdate()
            }
        }
    }

    @objc func minimizeToIsland() {
        didMoveToIslandForBackground = false
        setMinimizedInIsland(animated: true, hideLocally: false)
    }

    func setMinimizedInIsland(animated: Bool, hideLocally: Bool) {
        view.endEditing(true)
        setEmojiPanelVisible(false, animated: false)
        startIslandActivity()
        applyWindowState(.minimized, animated: animated)
        chromeView.isHidden = hideLocally
    }

    func startIslandActivity() {
        FloatingChatActivityManager.shared.startOrUpdate(
            state: makeFloatingChatActivityState()
        )
    }

    func syncIslandActivityIfNeeded() {
        guard windowState == .minimized || didMoveToIslandForBackground else { return }
        startIslandActivity()
    }

    func makeFloatingChatActivityState() -> FloatingChatActivityState {
        let visibleMessages = state.visibleMessages
        let activeTitle = state.activeFriend.map(displayName(for:)) ?? "主页消息"
        let selectedAccountName = displayNameForAccount(id: state.selectedAccountID)
        let latestMessage = visibleMessages.last
        let unreadCount = floatingIslandUnreadCount(in: visibleMessages)
        let aiStatus = floatingIslandAIStatus()
        let callStatus = floatingIslandCallStatus(in: visibleMessages)
        let mediaStatus = floatingIslandMediaStatus(in: visibleMessages)
        let imStatusText = state.selectedFriendID == nil
            ? "\(visibleMessages.count) 条最近消息"
            : "\(latestMessage?.type.title ?? "暂无消息")"

        return FloatingChatActivityState(
            title: activeTitle,
            subtitle: state.selectedFriendID == nil ? "\(selectedAccountName) · 全部会话" : "\(selectedAccountName) · 点击继续聊天",
            unreadCount: unreadCount,
            messages: liveActivityMessages(),
            draft: inputBar.textField.text ?? "",
            appName: "只发",
            appBadgeText: "只",
            appIconAssetName: "IslandAppIcon",
            imStatusText: imStatusText,
            imStatusLevel: unreadCount > 0 ? "busy" : "normal",
            aiTaskText: aiStatus.text,
            aiTaskActive: aiStatus.isActive,
            callStatusText: callStatus.text,
            callStatusKind: callStatus.kind,
            mediaStatusText: mediaStatus.text,
            mediaStatusKind: mediaStatus.kind,
            updatedAtText: latestMessage?.displayTimestamp ?? currentTimestamp()
        )
    }

    func floatingIslandUnreadCount(in messages: [ChatMessage]) -> Int {
        let incomingCount = messages
            .suffix(99)
            .filter {
                !$0.isOutgoing
                    && !$0.sender.isCurrentUser
                    && $0.type != .system
                    && $0.type != .groupNotice
            }
            .count
        return min(incomingCount, 99)
    }

    func floatingIslandAIStatus() -> (text: String, isActive: Bool) {
        if !autoReplyActiveConversationIDs.isEmpty {
            return ("正在生成草稿", true)
        }
        if !streamingAIMessageIDs.isEmpty {
            return ("回复待确认", true)
        }
        if autoReplyConfiguration.isEnabled {
            return ("自动回复已开启", false)
        }
        return ("AI 待命", false)
    }

    func floatingIslandCallStatus(in messages: [ChatMessage]) -> (text: String, kind: String) {
        guard let message = messages.reversed().first(where: { $0.type == .voiceCall || $0.type == .videoCall }) else {
            return ("无通话", "none")
        }
        switch message.type {
        case .voiceCall:
            return ("语音通话 \(message.displayTimestamp)", "voice")
        case .videoCall:
            return ("视频通话 \(message.displayTimestamp)", "video")
        default:
            return ("无通话", "none")
        }
    }

    func floatingIslandMediaStatus(in messages: [ChatMessage]) -> (text: String, kind: String) {
        if let playingVoiceMessageID,
           messages.contains(where: { $0.id == playingVoiceMessageID }) {
            return ("语音播放中", "voice")
        }
        guard let message = messages.reversed().first(where: { floatingIslandMediaKind(for: $0.type) != nil }) else {
            return ("无媒体播放", "none")
        }
        return ("最近\(message.type.title)", floatingIslandMediaKind(for: message.type) ?? "none")
    }

    func floatingIslandMediaKind(for type: ChatMessageType) -> String? {
        switch type {
        case .voice:
            return "voice"
        case .image, .capturedPhoto, .stickerGif:
            return "image"
        case .video, .channelsVideo, .channelsLive:
            return "video"
        case .music:
            return "music"
        default:
            return nil
        }
    }

    func liveActivityMessages() -> [FloatingChatActivityMessage] {
        state.visibleMessages
            .suffix(5)
            .map {
                let text = floatingIslandMessageText(for: $0)
                return FloatingChatActivityMessage(
                    sender: $0.sender.displayName,
                    text: text,
                    isOutgoing: $0.isOutgoing
                )
            }
    }

    func floatingIslandMessageText(for message: ChatMessage) -> String {
        if isAutoReplyPrediction(message) {
            let reply = suggestedReplyText(fromAutoReplyBody: message.body).trimmingCharacters(in: .whitespacesAndNewlines)
            return reply.isEmpty ? "AI 正在生成草稿" : reply
        }
        if !message.body.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            return message.body
        }
        if !message.detail.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            return message.detail
        }
        return message.type.title
    }

    @objc func expandFromLocalIsland() {
        guard windowState == .minimized else { return }
        showChatFromIsland()
    }

    func showChatFromIsland() {
        guard isViewLoaded else { return }
        resetTransientUIForSceneTransition()
        didMoveToIslandForBackground = false
        FloatingChatActivityManager.shared.end()
        applyWindowState(.expanded, animated: true)
    }

    func moveChatToIslandForBackground() {
        guard isViewLoaded else { return }
        resetTransientUIForSceneTransition()
        didMoveToIslandForBackground = true
        setMinimizedInIsland(animated: false, hideLocally: true)
    }

    func restoreFloatingChatFromForeground() {
        guard isViewLoaded, didMoveToIslandForBackground else { return }
        resetTransientUIForSceneTransition()
        didMoveToIslandForBackground = false
        FloatingChatActivityManager.shared.end()
        applyWindowState(.expanded, animated: false)
    }

    func resetTransientUIForSceneTransition() {
        view.endEditing(true)
        dismissComposerInput(animated: false)
        setMentionPanelVisible(false, animated: false)
        setEmojiPanelVisible(false, animated: false)
        inputBarBottomConstraint?.constant = 0
        inputBarBottomFillView.alpha = 1
        resetVoiceRecordingUIForSceneTransition()
        cancelLeftScrollPreview()
        clearMessageDropFeedback(animated: false)
        hideMessageTrashDropTarget(animated: false)
        hideMediaDragView()
        resetMessageDrag()
        hideSelectionAssistOverlay()
        cancelSelectionLasso()
        resetEdgeFeedbackForSceneTransition()
        rightToolCollectionView.cancelInteractiveMovement()
        rightToolCollectionView.visibleCells.forEach { $0.layer.zPosition = 0 }
        setRightRailScrollingEnabled(true)
        leftCollectionView.isScrollEnabled = true
        messageCollectionView.collectionViewLayout.invalidateLayout()
        leftCollectionView.collectionViewLayout.invalidateLayout()
        rightAccountCollectionView.collectionViewLayout.invalidateLayout()
        rightToolCollectionView.collectionViewLayout.invalidateLayout()
        clearMessageHeightCache()
        lastMessageHeightCacheWidth = 0
        view.setNeedsLayout()
        view.layoutIfNeeded()
        ensureVerticalMessageLayout(reloadData: true)
        messageCollectionView.setContentOffset(
            CGPoint(x: 0, y: messageCollectionView.contentOffset.y),
            animated: false
        )
        scheduleConnectionUpdate()
        AIVoiceAssistantOverlayCoordinator.shared.resetForSceneTransition()
    }

    func resetVoiceRecordingUIForSceneTransition() {
        let recordedURL = audioRecordingURL
        audioRecorder?.stop()
        audioRecorder = nil
        audioRecordingURL = nil
        audioRecordingStartedAt = nil
        isVoicePressActive = false
        resetVoiceButtonAppearance()
        voiceRecordingPromptView.layer.removeAllAnimations()
        voiceRecordingPromptView.alpha = 0
        voiceRecordingPromptView.isHidden = true
        if let recordedURL {
            try? FileManager.default.removeItem(at: recordedURL)
        }
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
    }

    func resetEdgeFeedbackForSceneTransition() {
        rightEdgeDouyinFeedbackView.layer.removeAllAnimations()
        leftEdgeFeedbackView.layer.removeAllAnimations()
        rightEdgeDouyinFeedbackView.alpha = 0
        leftEdgeFeedbackView.alpha = 0
        rightEdgeDouyinArrowView.transform = .identity
        leftEdgeArrowView.transform = .identity
        rightEdgeDouyinArrowView.image = UIImage(systemName: "chevron.left")
        leftEdgeArrowView.image = UIImage(systemName: "chevron.right")
        rightEdgeDouyinHintLabel.alpha = 0
        leftEdgeHintLabel.alpha = 0

        [
            rightEdgeDouyinBlobLayer,
            rightEdgeDouyinRimLayer,
            rightEdgeDouyinHighlightLayer,
            rightEdgeDouyinTrailLayer,
            leftEdgeBlobLayer,
            leftEdgeRimLayer,
            leftEdgeHighlightLayer,
            leftEdgeTrailLayer
        ].forEach { layer in
            layer.removeAllAnimations()
            layer.path = nil
        }

        rightEdgeGestureCurrentTarget = nil
        leftEdgeGestureCurrentTarget = nil
        isRightEdgeGestureActive = false
        isLeftEdgeGestureActive = false
        isLeftEdgePullConfirmed = false
    }

    @objc func handleHeaderLongPress(_ gesture: UILongPressGestureRecognizer) {
        guard gesture.state == .began else { return }
        toggleCompactState()
    }

    @objc func toggleCompactState() {
        applyWindowState(.expanded, animated: true)
    }

    @objc func handleGlobalTap(_ gesture: UITapGestureRecognizer) {
        guard gesture.state == .ended else { return }
        let point = gesture.location(in: view)
        if inputBar.convert(inputBar.bounds, to: view).contains(point)
            || emojiPanelView.convert(emojiPanelView.bounds, to: view).contains(point)
            || mentionPanelView.convert(mentionPanelView.bounds, to: view).contains(point) {
            return
        }

        dismissComposerInput(animated: true)
    }

    @discardableResult
    func dismissComposerInput(animated: Bool) -> Bool {
        let isKeyboardActive = inputBar.textField.isFirstResponder
        let isEmojiVisible = !emojiPanelView.isHidden || (emojiPanelHeightConstraint?.constant ?? 0) > 0.5
        let isMentionVisible = !mentionPanelView.isHidden || (mentionPanelHeightConstraint?.constant ?? 0) > 0.5
        guard isKeyboardActive || isEmojiVisible || isMentionVisible else { return false }

        setMentionPanelVisible(false, animated: animated)
        if isEmojiVisible {
            setEmojiPanelVisible(false, animated: animated)
        }
        inputBar.textField.resignFirstResponder()
        view.endEditing(true)

        DispatchQueue.main.asyncAfter(deadline: .now() + 0.30) { [weak self] in
            guard let self,
                  !self.inputBar.textField.isFirstResponder,
                  (self.emojiPanelHeightConstraint?.constant ?? 0) <= 0.5
            else { return }
            self.inputBarBottomConstraint?.constant = 0
            self.inputBarBottomFillView.alpha = 1
            let updates = {
                self.view.layoutIfNeeded()
                self.updateConnections()
            }
            if animated {
                UIView.animate(withDuration: 0.16, delay: 0, options: [.curveEaseOut, .beginFromCurrentState], animations: updates)
            } else {
                updates()
            }
        }
        return true
    }

    @objc func handleLeftParticipantLongPress(_ gesture: UILongPressGestureRecognizer) {
        guard gesture.state == .began else { return }
        let point = gesture.location(in: leftCollectionView)
        guard let indexPath = leftCollectionView.indexPathForItem(at: point),
              let leftItem = visibleLeftItemsForSelectedAccount()[safe: indexPath.item],
              let participantID = leftItem.participantID,
              let participant = state.participants.first(where: { $0.id == participantID })
        else { return }

        let sourceView = leftCollectionView.cellForItem(at: indexPath) ?? leftCollectionView
        presentLeftParticipantActions(for: participant, sourceView: sourceView)
    }

    func sendPatPat(to participant: ChatParticipant) {
        let participantID = participant.id
        let shouldSwitchConversation = state.selectedFriendID != participantID
        if shouldSwitchConversation {
            selectFriend(id: participantID)
        }

        DispatchQueue.main.async { [weak self] in
            guard let self else { return }
            self.insertSystemMessage(
                "你拍了拍\(participant.displayName)",
                conversationID: participantID,
                animated: !shouldSwitchConversation
            )

            guard let updatedIndex = self.visibleLeftItemsForSelectedAccount().firstIndex(where: { $0.participantID == participantID }),
                  updatedIndex < self.leftCollectionView.numberOfItems(inSection: 0)
            else { return }

            let updatedIndexPath = IndexPath(item: updatedIndex, section: 0)
            self.leftCollectionView.scrollToItem(at: updatedIndexPath, at: .centeredVertically, animated: true)
            self.pulseSidebarItem(in: self.leftCollectionView, at: updatedIndexPath)
        }
    }

    @objc func handleRightToolLongPress(_ gesture: UILongPressGestureRecognizer) {
        handleRightToolSortLongPress(gesture)
    }

    func handleRightToolSortLongPress(_ gesture: UILongPressGestureRecognizer) {
        let point = gesture.location(in: rightToolCollectionView)
        switch gesture.state {
        case .began:
            guard let indexPath = rightToolCollectionView.indexPathForItem(at: point),
                  canReorderRightTool(at: indexPath)
            else { return }
            rightToolCollectionView.beginInteractiveMovementForItem(at: indexPath)
            rightToolCollectionView.cellForItem(at: indexPath)?.layer.zPosition = 8
        case .changed:
            rightToolCollectionView.updateInteractiveMovementTargetPosition(point)
        case .ended:
            rightToolCollectionView.endInteractiveMovement()
            rightToolCollectionView.visibleCells.forEach { $0.layer.zPosition = 0 }
        default:
            rightToolCollectionView.cancelInteractiveMovement()
            rightToolCollectionView.visibleCells.forEach { $0.layer.zPosition = 0 }
        }
    }

    @objc func sendMessage() {
        guard requireActiveConversationForSending() else { return }
        let conversationID = currentConversationID()
        let text = inputBar.textField.text?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        let codexPrompt = codexPromptIfMentioned(in: text)
        let codexReplyPrompt = codexPrompt ?? (isActiveCodexConversation && !text.isEmpty ? text : nil)
        if !pendingAttachments.isEmpty {
            setMentionPanelVisible(false, animated: true)
            inputBar.textField.text = nil
            sendPendingAttachments(pendingAttachments, text: text)
            clearPendingAttachment()
            if let codexReplyPrompt {
                startCodexStreamingReplyInConversation(prompt: codexReplyPrompt, conversationID: conversationID)
            }
            return
        }

        guard !text.isEmpty else {
            view.endEditing(true)
            return
        }

        setMentionPanelVisible(false, animated: true)
        inputBar.textField.text = nil
        if let quotedMessage {
            insertOutgoingMessage(
                type: .quotedReply,
                body: text,
                detail: quoteSummary(for: quotedMessage),
                quotedMessageID: quotedMessage.id
            )
            setQuotedMessage(nil)
        } else {
            insertOutgoingMessage(type: .text, body: text, richElements: richElements(from: text))
        }

        if let codexReplyPrompt {
            if isActiveCodexConversation {
                startCodexStreamingDirectReply(prompt: codexReplyPrompt)
            } else {
                startCodexStreamingReplyInConversation(prompt: codexReplyPrompt, conversationID: conversationID)
            }
        }
    }

    @objc func handleInputTextChanged() {
        updateMentionPanelForCurrentInput()
    }

    func handleInputLongEyeRewriteTrigger() {
        guard inputBar.textField.isFirstResponder else {
            inputLongEyeRewriteTrigger.stop()
            return
        }

        let currentText = inputBar.textField.text ?? ""
        let trimmed = currentText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else {
            triggerAutoReplyForLatestCurrentMessage()
            return
        }

        guard !isInputLongEyeRewriteStreaming else {
            showNotice("AI 正在改写输入内容")
            return
        }

        isInputLongEyeRewriteStreaming = true
        let originalText = currentText
        var streamedText = ""
        showNotice("闭眼触发 AI 改写")
        let prompt = """
        请改写下面输入框里的内容，保留核心意思，让表达更自然、清楚、适合直接在聊天中发送。
        只返回改写后的正文，不要解释。

        \(trimmed)
        """

        streamCodexMessage(prompt: prompt) { [weak self] delta in
            DispatchQueue.main.async {
                guard let self else { return }
                streamedText += delta
                self.inputBar.textField.text = streamedText
                self.inputBar.textField.sendActions(for: .editingChanged)
            }
        } completion: { [weak self] result in
            DispatchQueue.main.async {
                guard let self else { return }
                self.isInputLongEyeRewriteStreaming = false
                switch result {
                case .success:
                    if streamedText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                        self.inputBar.textField.text = originalText
                        self.inputBar.textField.sendActions(for: .editingChanged)
                        self.showNotice("AI 未返回改写内容")
                    } else {
                        self.showNotice("已改写输入内容")
                    }
                case .failure:
                    if streamedText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                        self.inputBar.textField.text = originalText
                        self.inputBar.textField.sendActions(for: .editingChanged)
                    }
                    self.showNotice("AI 改写失败")
                }
            }
        }
    }

    @objc func handleVoicePressBegan() {
        guard requireActiveConversationForSending() else { return }
        guard audioRecorder?.isRecording != true else { return }
        isVoicePressActive = true
        view.endEditing(true)
        setEmojiPanelVisible(false, animated: true)
        startVoiceRecording()
    }

    @objc func handleVoicePressEnded() {
        isVoicePressActive = false
        guard audioRecorder?.isRecording == true else {
            hideVoiceRecordingPrompt()
            return
        }
        finishVoiceRecording()
    }

    @objc func handleVoicePressCancelled() {
        isVoicePressActive = false
        guard audioRecorder?.isRecording == true else {
            hideVoiceRecordingPrompt()
            return
        }
        cancelVoiceRecording(shouldShowNotice: true)
    }

    @objc func showStickerSheet() {
        guard requireActiveConversationForSending() else { return }
        view.endEditing(true)
        setEmojiPanelVisible(emojiPanelHeightConstraint?.constant == 0, animated: true)
    }

    @objc func sendEmojiFromPanel(_ sender: UIButton) {
        guard requireActiveConversationForSending() else { return }
        guard sender.tag >= 0, sender.tag < emojiItems.count else { return }
        let emoji = emojiItems[sender.tag]
        insertTextIntoMessageField(emoji)
    }

    func insertTextIntoMessageField(_ text: String) {
        let field = inputBar.textField
        if let selectedRange = field.selectedTextRange {
            field.replace(selectedRange, withText: text)
        } else {
            field.text = (field.text ?? "") + text
        }
        field.sendActions(for: .editingChanged)
    }

    func updateMentionPanelForCurrentInput() {
        let text = inputBar.textField.text ?? ""
        guard shouldShowMentionPanel(for: text), !mentionCandidates().isEmpty else {
            setMentionPanelVisible(false, animated: true)
            return
        }
        setMentionPanelVisible(false, animated: true)
        presentMentionPicker(initialQuery: mentionQuery(in: text))
    }

    func shouldShowMentionPanel(for text: String) -> Bool {
        guard let lastAtIndex = text.lastIndex(of: "@") else { return false }
        let suffix = text[text.index(after: lastAtIndex)...]
        guard !suffix.contains(" "), !suffix.contains("\n") else { return false }
        if lastAtIndex == text.startIndex { return true }
        let previous = text[text.index(before: lastAtIndex)]
        return previous.isWhitespace || previous.isNewline
    }

    func mentionQuery(in text: String) -> String {
        guard let lastAtIndex = text.lastIndex(of: "@") else { return "" }
        return String(text[text.index(after: lastAtIndex)...])
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }

    func mentionCandidates() -> [ChatParticipant] {
        var candidates: [ChatParticipant]
        if let activeFriend = state.activeFriend, isGroupConversation(activeFriend) {
            candidates = groupMembers(for: activeFriend).filter {
                !$0.isCurrentUser && $0.id != state.selectedAccountID && $0.id != activeFriend.id
            }
        } else {
            let activeGroupID = state.activeFriend?.id
            let groupMessages = state.messages.filter {
                $0.isGroupConversation && (activeGroupID == nil || $0.conversationID == activeGroupID)
            }
            candidates = groupMessages.map(\.sender)
                .filter { !$0.isCurrentUser }
        }

        if candidates.isEmpty {
            candidates = state.friends.filter { !$0.isCurrentUser }
        }

        return candidates.reduce(into: [ChatParticipant]()) { result, participant in
            guard !result.contains(where: { $0.id == participant.id }) else { return }
            result.append(participant)
        }
    }

    func reloadMentionCandidates() {
        mentionStackView.arrangedSubviews.forEach {
            mentionStackView.removeArrangedSubview($0)
            $0.removeFromSuperview()
        }

        mentionCandidates().prefix(12).forEach { participant in
            let button = UIButton(type: .system)
            button.setTitle("@\(participant.displayName)", for: .normal)
            button.titleLabel?.font = .systemFont(ofSize: 13, weight: .semibold)
            button.tintColor = UIColor.systemBlue
            button.backgroundColor = UIColor.systemBlue.withAlphaComponent(0.10)
            button.layer.cornerRadius = 12
            button.layer.cornerCurve = .continuous
            button.applyContentInsets(top: 7, leading: 10, bottom: 7, trailing: 10)
            button.heightAnchor.constraint(equalToConstant: 34).isActive = true
            button.addAction(UIAction { [weak self] _ in
                self?.insertMention(participant.displayName)
            }, for: .touchUpInside)
            mentionStackView.addArrangedSubview(button)
        }
    }

    func presentMentionPicker(initialQuery: String) {
        guard !isMentionPickerPresented else { return }
        isMentionPickerPresented = true
        let controller = MentionPickerViewController(
            candidates: mentionCandidates(),
            initialQuery: initialQuery
        )
        controller.onSelect = { [weak self] participant in
            self?.insertMention(participant.displayName)
        }
        controller.onDismiss = { [weak self] in
            self?.isMentionPickerPresented = false
        }
        let navigationController = UINavigationController(rootViewController: controller)
        navigationController.modalPresentationStyle = .pageSheet
        if let sheet = navigationController.sheetPresentationController {
            sheet.detents = [.medium(), .large()]
            sheet.prefersGrabberVisible = true
        }
        present(navigationController, animated: true)
    }

    func insertMention(_ name: String) {
        var text = inputBar.textField.text ?? ""
        if let lastAtIndex = text.lastIndex(of: "@") {
            text.replaceSubrange(lastAtIndex..<text.endIndex, with: "@\(name) ")
        } else {
            text += "@\(name) "
        }
        inputBar.textField.text = text
        inputBar.textField.becomeFirstResponder()
        setMentionPanelVisible(false, animated: true)
    }

    func setMentionPanelVisible(_ isVisible: Bool, animated: Bool) {
        mentionPanelView.isHidden = false
        mentionPanelHeightConstraint?.constant = isVisible ? 48 : 0

        let updates = {
            self.view.layoutIfNeeded()
        }
        let completion: (Bool) -> Void = { _ in
            self.mentionPanelView.isHidden = !isVisible
        }
        if animated {
            UIView.animate(withDuration: 0.18, delay: 0, options: [.curveEaseOut], animations: updates, completion: completion)
        } else {
            updates()
            completion(true)
        }
    }

    func setEmojiPanelVisible(_ isVisible: Bool, animated: Bool) {
        emojiPanelView.isHidden = false
        emojiPanelHeightConstraint?.constant = isVisible ? 260 : 0
        inputBarBottomConstraint?.constant = isVisible ? -260 : 0
        inputBarBottomFillView.alpha = isVisible ? 0 : 1

        let updates = {
            self.view.layoutIfNeeded()
            self.updateConnections()
        }
        let completion: (Bool) -> Void = { _ in
            self.emojiPanelView.isHidden = !isVisible
            if isVisible {
                self.scrollVisibleMessagesToBottomIfNeeded(animated: true)
            }
        }

        if animated {
            UIView.animate(withDuration: 0.22, delay: 0, options: [.curveEaseOut], animations: updates, completion: completion)
        } else {
            updates()
            completion(true)
        }
    }

    func loadAppLanguage() {
        if let rawValue = ChatSQLiteStore.shared.string(forKey: Self.appLanguageStorageKey)
            ?? UserDefaults.standard.string(forKey: Self.appLanguageStorageKey),
           let savedLanguage = AppLanguage(rawValue: rawValue) {
            appLanguage = savedLanguage
        } else {
            appLanguage = .zh
        }
    }

    func toggleAppLanguage() {
        appLanguage = appLanguage == .zh ? .en : .zh
        ChatSQLiteStore.shared.setString(appLanguage.rawValue, forKey: Self.appLanguageStorageKey)
        refreshLocalizedText()
    }

    func localized(_ zh: String, _ en: String) -> String {
        appLanguage.text(zh, en)
    }

    func refreshLocalizedText() {
        accountButton.accessibilityLabel = localized("全部帐号", "All Accounts")
        updateHeaderForSelection()
        updateMultiSelectButtonTitles()
    }

    func updateMultiSelectButtonTitles() {
        configureMultiSelectButton(
            multiForwardButton,
            title: localized("逐条转发", "Forward"),
            symbolName: "arrowshape.turn.up.right",
            action: #selector(forwardSelectedMessages),
            isDestructive: false
        )
        configureMultiSelectButton(
            multiMergeButton,
            title: localized("合并转发", "Merge"),
            symbolName: "text.line.first.and.arrowtriangle.forward",
            action: #selector(mergeForwardSelectedMessages),
            isDestructive: false
        )
        configureMultiSelectButton(
            multiDeleteButton,
            title: localized("删除", "Delete"),
            symbolName: "trash",
            action: #selector(deleteSelectedMessages),
            isDestructive: true
        )
        configureMultiSelectButton(
            multiCancelButton,
            title: localized("取消", "Cancel"),
            symbolName: "xmark",
            action: #selector(cancelMultiSelect),
            isDestructive: false
        )
    }

    @objc func showSelectedAccountFavorites() {
        presentFavoriteShareSheet(sourceView: inputBar.minimizeButton)
    }

    @objc func showToolSheet() {
        let alert = UIAlertController(title: "\u{53d1}\u{9001}", message: nil, preferredStyle: .actionSheet)
        addToolActions(to: alert)
        alert.addAction(UIAlertAction(title: "\u{53d6}\u{6d88}", style: .cancel))
        presentSheet(alert, sourceView: inputBar.moreButton)
    }

}
