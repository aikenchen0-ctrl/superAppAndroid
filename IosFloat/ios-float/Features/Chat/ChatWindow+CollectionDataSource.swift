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


extension ChatWindowViewController: UICollectionViewDataSource {
    func collectionView(_ collectionView: UICollectionView, numberOfItemsInSection section: Int) -> Int {
        if collectionView === leftCollectionView {
            return visibleLeftItemsForSelectedAccount().count
        }
        if collectionView === rightAccountCollectionView {
            return visibleRightAccountItems().count
        }
        if collectionView === rightToolCollectionView {
            return visibleRightToolItems().count
        }
        let messageCount = renderedMessages.count
        updateUnansweredEmptyState(messageCount: messageCount)
        return messageCount
    }

    func collectionView(
        _ collectionView: UICollectionView,
        cellForItemAt indexPath: IndexPath
    ) -> UICollectionViewCell {
        if collectionView === messageCollectionView {
            let message = renderedMessage(at: indexPath)
            let reuseIdentifier = message.map {
                ChatMessageCell.reuseIdentifier(for: $0.type)
            } ?? ChatMessageCell.reuseIdentifier(for: .system)
            let cell = collectionView.dequeueReusableCell(
                withReuseIdentifier: reuseIdentifier,
                for: indexPath
            ) as! ChatMessageCell
            cell.onConnectionGeometryDidChange = { [weak self] in
                self?.scheduleConnectionUpdate()
            }
            cell.onUnansweredFoldTap = nil
            guard let message else {
                cell.configure(
                    with: emptyPlaceholderMessage(),
                    isVoicePlaying: false,
                    isAIStreaming: false,
                    screenshotMediaBlurEnabled: false,
                    mediaPrivacyBlurEnabled: false,
                    uses3DBubbleAppearance: bubble3DAppearanceEnabled,
                    deliveryStatusText: nil,
                    isSelecting: false,
                    isSelected: false,
                    showsGroupAvatar: true,
                    showsGroupName: true,
                    groupTopSpacing: 0,
                    isUnansweredSelected: false,
                    unansweredRecipientItem: nil,
                    usesUnansweredPresentation: false
                )
                return cell
            }
            let groupSettings: GroupDisplaySettings = {
                guard !isHomeTimeline else {
                    return GroupDisplaySettings(showsAvatar: false, showsName: true)
                }
                return groupDisplaySettings(for: message)
            }()
            let usesUnansweredPresentation = isHomeTimeline
            let foldBottomOffset = isHomeTimeline
                ? unreadTimelineLayoutSnapshot()?.messageFoldBottomOffsets[safe: indexPath.item] ?? nil
                : nil
            let foldPresentation = foldBottomOffset == nil
                ? nil
                : unreadTimelineFoldPresentation(for: message)
            if let foldPresentation {
                cell.onUnansweredFoldTap = { [weak self] in
                    self?.handleUnreadTimelineFoldTap(foldPresentation)
                }
            }
            let recipientItems: [SidebarItem] = usesUnansweredPresentation
                ? unansweredRecipientAccountIDs(for: message).compactMap { recipientAccountID in
                    state.currentUsers.first(where: { $0.id == recipientAccountID }).map { account in
                        SidebarItem(
                            id: account.id,
                            kind: .account,
                            title: account.displayName,
                            symbolName: "person.crop.circle.fill",
                            tintColor: unansweredAccountColor(for: account.id),
                            participantID: account.id,
                            targetMessageID: message.id,
                            isActive: true,
                            avatarURL: account.avatarURL
                        )
                    }
                }
                : []
            let usesGroupAccountPresentation = usesOutgoingGroupAccountPresentation(for: message)
            let outgoingGroupAccountDotColor = outgoingGroupAccountID(for: message).map {
                unansweredAccountColor(for: $0)
            }
            cell.configure(
                with: message,
                isVoicePlaying: message.id == playingVoiceMessageID,
                isAIStreaming: streamingAIMessageIDs.contains(message.id),
                screenshotMediaBlurEnabled: screenshotMediaBlurEnabled,
                mediaPrivacyBlurEnabled: shouldBlurMediaMessage(message),
                uses3DBubbleAppearance: usesUnansweredPresentation ? false : bubble3DAppearanceEnabled,
                deliveryStatusText: openApiDeliveryStatusText(for: message),
                isSelecting: isMultiSelecting,
                isSelected: selectedMessageIDs.contains(message.id),
                // The unanswered rail is the sender identity; do not duplicate
                // the avatar inside the message bubble.
                showsGroupAvatar: usesUnansweredPresentation ? false : groupSettings.showsAvatar,
                showsGroupName: usesUnansweredPresentation || groupSettings.showsName,
                groupTopSpacing: unreadTimelineMessageTopSpacing(at: indexPath.item),
                groupBottomPadding: isHomeTimeline
                    ? (unreadTimelineLayoutSnapshot()?.messageBottomPaddings[safe: indexPath.item] ?? 0)
                    : 0,
                isUnansweredSelected: usesUnansweredPresentation
                    && (selectedUnansweredItemID
                        ?? pendingReplyHomePresentation().items.first?.id)
                        == unansweredItemForDisplayedMessage(message)?.id,
                unansweredRecipientItem: recipientItems.first,
                unansweredRecipientItems: recipientItems,
                usesUnansweredPresentation: usesUnansweredPresentation,
                outgoingGroupAccountDotColor: outgoingGroupAccountDotColor,
                usesOutgoingGroupAccountPresentation: usesGroupAccountPresentation,
                unansweredFoldTitle: foldPresentation?.title
            )
            return cell
        }

        let cell = collectionView.dequeueReusableCell(
            withReuseIdentifier: SidebarCell.reuseIdentifier,
            for: indexPath
        ) as! SidebarCell
        guard let item = sidebarItem(for: collectionView, at: indexPath) else {
            cell.configure(with: emptyPlaceholderSidebarItem())
            return cell
        }
        if collectionView === rightAccountCollectionView, isShowingInitialAccountLoading {
            cell.configureLoadingAccount(index: indexPath.item)
            return cell
        }
        configureSidebarCell(cell, with: item, in: collectionView)
        return cell
    }

    func configureSidebarCell(
        _ cell: SidebarCell,
        with item: SidebarItem,
        in collectionView: UICollectionView
    ) {
        let accountBorderColor: UIColor?
        let accountBorderWidth: CGFloat?
        let accountConnectionDotColor: UIColor?
        if collectionView === rightAccountCollectionView,
           let accountID = item.participantID {
            let selectedAccountID = isHomeTimeline
                ? unansweredAccountFilterID
                : state.selectedAccountID
            let isSelected = item.participantID == selectedAccountID
            let isGroupDetail = !isHomeTimeline
                && state.activeFriend.map(isGroupConversation) == true
            let isRelatedToGroup = !isGroupDetail || isRightAccountAvailable(accountID)
            let showsHomeAccountBorder = isHomeTimeline
                && (unansweredAccountFilterID == nil || isSelected)
            if showsHomeAccountBorder || (isSelected && isRelatedToGroup) {
                accountBorderColor = unansweredAccountColor(for: accountID)
                accountBorderWidth = isHomeTimeline && unansweredAccountFilterID == nil ? 2 : 3
            } else {
                accountBorderColor = nil
                accountBorderWidth = 0
            }
            accountConnectionDotColor = isHomeTimeline || (isGroupDetail && isRelatedToGroup)
                ? unansweredAccountColor(for: accountID)
                : nil
        } else {
            accountBorderColor = nil
            accountBorderWidth = nil
            accountConnectionDotColor = nil
        }
        let homeTopOffset: CGFloat
        if isHomeTimeline,
           let participantID = item.participantID,
           let snapshot = unreadTimelineLayoutSnapshot(),
           let groupIndex = snapshot.groupIndexByParticipantID[participantID],
           let group = snapshot.groups[safe: groupIndex] {
            homeTopOffset = unreadTimelineAvatarTopOffset(for: group)
        } else {
            homeTopOffset = 0
        }
        let isAvailable: Bool
        if collectionView === rightAccountCollectionView, let accountID = item.participantID {
            let isGroupDetail = !isHomeTimeline
                && state.activeFriend.map(isGroupConversation) == true
            isAvailable = !isGroupDetail || isRightAccountAvailable(accountID)
        } else {
            isAvailable = true
        }
        cell.configure(
            with: item,
            accountBorderColor: accountBorderColor,
            accountBorderWidth: accountBorderWidth,
            accountConnectionDotColor: accountConnectionDotColor,
            alignsToTop: collectionView === leftCollectionView && isHomeTimeline,
            topOffset: collectionView === leftCollectionView && isHomeTimeline ? homeTopOffset : 0,
            isAvailable: isAvailable
        )
    }

    func unreadTimelineAvatarTopOffset(for group: UnreadTimelineGroupLayout) -> CGFloat {
        let naturalAvatarFrame: CGRect
        if let measuredTop = unreadTimelineMeasuredAvatarTopByParticipantID[group.participantID] {
            naturalAvatarFrame = CGRect(
                x: group.avatarFrame.minX,
                y: measuredTop,
                width: group.avatarFrame.width,
                height: group.avatarFrame.height
            )
        } else {
            naturalAvatarFrame = group.avatarFrame
        }
        return UnreadTimelineAvatarPinning.topOffset(
            groupFrame: group.frame,
            naturalAvatarFrame: naturalAvatarFrame,
            viewportTop: leftCollectionView.bounds.minY + 4,
            viewportBottom: leftCollectionView.bounds.maxY - 4,
            avatarHeight: UnreadTimelineLayoutMetrics.avatarSideLength
        )
    }

    func updateVisibleUnreadTimelineAvatarPositions() {
        guard isHomeTimeline,
              let snapshot = unreadTimelineLayoutSnapshot()
        else { return }
        let leftItems = visibleLeftItemsForSelectedAccount()
        for case let cell as SidebarCell in leftCollectionView.visibleCells {
            guard let indexPath = leftCollectionView.indexPath(for: cell),
                  let participantID = leftItems[safe: indexPath.item]?.participantID,
                  let groupIndex = snapshot.groupIndexByParticipantID[participantID],
                  let group = snapshot.groups[safe: groupIndex]
            else { continue }
            cell.updateUnreadTimelineTopOffset(unreadTimelineAvatarTopOffset(for: group))
        }
    }

    func sidebarItem(for collectionView: UICollectionView, at indexPath: IndexPath) -> SidebarItem? {
        if collectionView === leftCollectionView {
            return visibleLeftItemsForSelectedAccount()[safe: indexPath.item]
        }
        if collectionView === rightAccountCollectionView {
            return visibleRightAccountItems()[safe: indexPath.item]
        }
        return visibleRightToolItems()[safe: indexPath.item]
    }

    func emptyPlaceholderMessage() -> ChatMessage {
        ChatMessage(
            id: UUID(),
            conversationID: state.selectedFriendID ?? state.currentUser.id,
            type: .system,
            sender: state.currentUser,
            body: "",
            detail: "",
            isOutgoing: false,
            presentation: .bare,
            timestamp: "",
            sentAt: Date()
        )
    }

    func updateUnansweredEmptyState(messageCount: Int) {
        if isAccountDirectory {
            unansweredEmptyStateLabel.text = localized(
                "选择左侧好友或群聊\n即可主动发送消息",
                "Choose a friend or group on the left\nto start a conversation"
            )
            unansweredEmptyStateLabel.accessibilityLabel = localized(
                "选择左侧好友或群聊，即可主动发送消息",
                "Choose a friend or group on the left to start a conversation"
            )
            unansweredEmptyStateLabel.isHidden = false
            return
        }
        let shouldShow = UnansweredEmptyStatePolicy.shouldShow(
            isHomeTimeline: isHomeTimeline,
            messageCount: messageCount,
            bootstrapState: openApiIMBootstrapState,
            hasLoadedSnapshot: hasLoadedOpenApiIMSnapshot
        )
        unansweredEmptyStateLabel.text = localized(
            "暂无未回消息\n所有待回复事项均已处理",
            "No unreplied messages\nAll reply items are complete"
        )
        unansweredEmptyStateLabel.accessibilityLabel = localized(
            "暂无未回消息，所有待回复事项均已处理",
            "No unreplied messages. All reply items are complete."
        )
        unansweredEmptyStateLabel.isHidden = !shouldShow
    }

    func emptyPlaceholderSidebarItem() -> SidebarItem {
        SidebarItem(
            id: UUID(),
            kind: .tool,
            title: "",
            symbolName: "circle",
            tintColor: .clear,
            participantID: nil,
            targetMessageID: UUID(),
            isActive: false
        )
    }

    func collectionView(_ collectionView: UICollectionView, canMoveItemAt indexPath: IndexPath) -> Bool {
        collectionView === rightToolCollectionView && canReorderRightTool(at: indexPath)
    }

    func collectionView(
        _ collectionView: UICollectionView,
        moveItemAt sourceIndexPath: IndexPath,
        to destinationIndexPath: IndexPath
    ) {
        guard collectionView === rightToolCollectionView,
              let sourceToolIndex = customRightToolOrderIndex(forVisibleIndex: sourceIndexPath.item),
              let destinationToolIndex = customRightToolOrderIndex(forVisibleIndex: destinationIndexPath.item)
        else {
            collectionView.reloadData()
            return
        }
        guard sourceToolIndex != destinationToolIndex,
              customRightToolOrderKeys.indices.contains(sourceToolIndex),
              customRightToolOrderKeys.indices.contains(destinationToolIndex)
        else { return }

        let moved = customRightToolOrderKeys.remove(at: sourceToolIndex)
        customRightToolOrderKeys.insert(moved, at: destinationToolIndex)
    }
}

extension ChatWindowViewController {
    func unansweredRecipientAccountIDs(for message: ChatMessage) -> [UUID] {
        if isHomeTimeline {
            if let unansweredAccountFilterID,
               state.currentUsers.contains(where: { $0.id == unansweredAccountFilterID }) {
                return [unansweredAccountFilterID]
            }
            if let item = unansweredItemForDisplayedMessage(message) {
                let targetIDs = Set(item.targetAccountIDs)
                return state.currentUsers.lazy.map(\.id)
                    .filter(targetIDs.contains)
                    .prefix(UnansweredRecipientAvatarStackView.maximumVisibleAvatarCount)
                    .map { $0 }
            }
        }
        if let conversation = state.participants.first(where: { $0.id == message.conversationID }),
           isGroupConversation(conversation) || isGroupParticipant(conversation) {
            let relatedAccountIDs = currentAccountIDsInGroup(conversation)
            let orderedIDs = state.currentUsers.map(\.id).filter(relatedAccountIDs.contains)
            if !orderedIDs.isEmpty {
                return Array(orderedIDs.prefix(UnansweredRecipientAvatarStackView.maximumVisibleAvatarCount))
            }
        }
        return unansweredRecipientAccountID(for: message).map { [$0] } ?? []
    }

    func unansweredRecipientAccountID(for message: ChatMessage) -> UUID? {
        if let recipientAccountID = message.recipientAccountID,
           state.currentUsers.contains(where: { $0.id == recipientAccountID }) {
            return recipientAccountID
        }

        if let context = openApiConversationContextsByID[message.conversationID],
           let account = openApiAccountContextsByID.values.first(where: { $0.wxid == context.ownerWxid }) {
            return account.participantID
        }

        let receiverPrefixes = [
            "接收帐号ID：", "目标帐号ID：", "收件帐号ID：",
            "所属帐号ID：", "帐号ID："
        ]
        for line in message.detail.components(separatedBy: .newlines) {
            for prefix in receiverPrefixes where line.hasPrefix(prefix) {
                let rawID = String(line.dropFirst(prefix.count)).trimmingCharacters(in: .whitespacesAndNewlines)
                if let accountID = UUID(uuidString: rawID), state.currentUsers.contains(where: { $0.id == accountID }) {
                    return accountID
                }
            }
        }

        if message.isOutgoing,
           state.currentUsers.contains(where: { $0.id == message.sender.id }) {
            return message.sender.id
        }

        if let conversation = state.participants.first(where: { $0.id == message.conversationID }),
           let account = state.currentUsers.first(where: { isConversation(conversation, relatedToAccountID: $0.id) }) {
            return account.id
        }

        return nil
    }

    func groupAccountID(for message: ChatMessage, in group: ChatParticipant) -> UUID? {
        let relatedAccountIDs = currentAccountIDsInGroup(group)
        guard !relatedAccountIDs.isEmpty else { return nil }

        if message.sender.isCurrentUser,
           relatedAccountIDs.contains(message.sender.id) {
            return message.sender.id
        }

        if let recipientAccountID = message.recipientAccountID,
           relatedAccountIDs.contains(recipientAccountID) {
            return recipientAccountID
        }

        let receiverPrefixes = [
            "接收帐号ID：", "目标帐号ID：", "收件帐号ID：",
            "所属帐号ID：", "帐号ID："
        ]
        for line in message.detail.components(separatedBy: .newlines) {
            for prefix in receiverPrefixes where line.hasPrefix(prefix) {
                let rawID = String(line.dropFirst(prefix.count)).trimmingCharacters(in: .whitespacesAndNewlines)
                if let accountID = UUID(uuidString: rawID), relatedAccountIDs.contains(accountID) {
                    return accountID
                }
            }
        }

        // Conversation ownership is not message ownership in a multi-account
        // group. Missing identity must stay unresolved instead of following UI state.
        return nil
    }

    func unansweredAccountColor(for accountID: UUID) -> UIColor {
        let palette: [UIColor] = [
            UIColor(red: 0.05, green: 0.43, blue: 0.95, alpha: 1), // blue
            UIColor(red: 1.00, green: 0.48, blue: 0.00, alpha: 1), // orange
            UIColor(red: 0.05, green: 0.68, blue: 0.25, alpha: 1), // green
            UIColor(red: 0.58, green: 0.25, blue: 0.85, alpha: 1), // purple
            UIColor(red: 0.92, green: 0.08, blue: 0.12, alpha: 1), // red
            UIColor(red: 0.00, green: 0.68, blue: 0.82, alpha: 1), // cyan
            UIColor(red: 0.82, green: 0.68, blue: 0.00, alpha: 1), // yellow
            UIColor(red: 0.85, green: 0.08, blue: 0.62, alpha: 1), // magenta
            UIColor(red: 0.25, green: 0.22, blue: 0.72, alpha: 1), // indigo
            UIColor(red: 0.00, green: 0.50, blue: 0.42, alpha: 1), // teal
            UIColor(red: 0.50, green: 0.72, blue: 0.05, alpha: 1), // lime
            UIColor(red: 0.28, green: 0.32, blue: 0.38, alpha: 1), // charcoal
            UIColor(red: 0.35, green: 0.70, blue: 1.00, alpha: 1), // sky
            UIColor(red: 0.62, green: 0.36, blue: 0.08, alpha: 1), // brown
            UIColor(red: 0.20, green: 0.84, blue: 0.62, alpha: 1), // mint
            UIColor(red: 0.03, green: 0.18, blue: 0.48, alpha: 1)  // navy
        ]
        if !hasLoadedUnansweredAccountColorAssignments {
            let stored = ChatSQLiteStore.shared.codable(
                [String: Int].self,
                forKey: "ChatWindowUnansweredAccountColorAssignments"
            ) ?? [:]
            unansweredAccountColorIndexByID = stored.reduce(into: [:]) { result, pair in
                if let id = UUID(uuidString: pair.key) {
                    result[id] = pair.value
                }
            }
            hasLoadedUnansweredAccountColorAssignments = true
        }

        var didChangeAssignments = false
        var usedIndices = Set<Int>()
        let orderedAccountIDs = Set(state.currentUsers.map(\.id) + [accountID])
            .sorted { $0.uuidString < $1.uuidString }
        for id in orderedAccountIDs {
            if let existingIndex = unansweredAccountColorIndexByID[id],
               existingIndex >= 0,
               !usedIndices.contains(existingIndex) {
                usedIndices.insert(existingIndex)
                continue
            }

            var nextIndex = 0
            while usedIndices.contains(nextIndex) {
                nextIndex += 1
            }
            unansweredAccountColorIndexByID[id] = nextIndex
            usedIndices.insert(nextIndex)
            didChangeAssignments = true
        }
        if didChangeAssignments {
            ChatSQLiteStore.shared.setCodable(
                Dictionary(uniqueKeysWithValues: unansweredAccountColorIndexByID.map {
                    ($0.key.uuidString, $0.value)
                }),
                forKey: "ChatWindowUnansweredAccountColorAssignments"
            )
        }
        let index = unansweredAccountColorIndexByID[accountID] ?? 0
        guard index >= palette.count else { return palette[index] }

        let goldenAngle = 0.618033988749895
        let hue = (CGFloat(index) * goldenAngle).truncatingRemainder(dividingBy: 1)
        let saturation = 0.72 + CGFloat(index % 3) * 0.08
        let brightness = 0.88 + CGFloat((index / 3) % 2) * 0.08
        return UIColor(hue: hue, saturation: saturation, brightness: brightness, alpha: 1)
    }

    func prioritizeUnansweredAccountColors(for accountIDs: [UUID]) {
        guard !accountIDs.isEmpty else { return }
        if !hasLoadedUnansweredAccountColorAssignments {
            let stored = ChatSQLiteStore.shared.codable(
                [String: Int].self,
                forKey: "ChatWindowUnansweredAccountColorAssignments"
            ) ?? [:]
            unansweredAccountColorIndexByID = stored.reduce(into: [:]) { result, pair in
                if let id = UUID(uuidString: pair.key) {
                    result[id] = pair.value
                }
            }
            hasLoadedUnansweredAccountColorAssignments = true
        }

        let prioritizedIDs = Array(accountIDs.prefix(3))
        let prioritizedIDSet = Set(prioritizedIDs)
        var nextIndex = prioritizedIDs.count
        let displacedIDs = unansweredAccountColorIndexByID
            .filter { !prioritizedIDSet.contains($0.key) && $0.value < prioritizedIDs.count }
            .map(\.key)
            .sorted { $0.uuidString < $1.uuidString }
        for (index, id) in prioritizedIDs.enumerated() {
            unansweredAccountColorIndexByID[id] = index
        }
        var usedIndices = Set(unansweredAccountColorIndexByID.compactMap { pair in
            prioritizedIDSet.contains(pair.key) || !displacedIDs.contains(pair.key) ? pair.value : nil
        })
        for id in displacedIDs {
            while usedIndices.contains(nextIndex) { nextIndex += 1 }
            unansweredAccountColorIndexByID[id] = nextIndex
            usedIndices.insert(nextIndex)
        }
        ChatSQLiteStore.shared.setCodable(
            Dictionary(uniqueKeysWithValues: unansweredAccountColorIndexByID.map {
                ($0.key.uuidString, $0.value)
            }),
            forKey: "ChatWindowUnansweredAccountColorAssignments"
        )
    }
}

extension ChatWindowViewController: UICollectionViewDataSourcePrefetching {
    func collectionView(_ collectionView: UICollectionView, prefetchItemsAt indexPaths: [IndexPath]) {
        if collectionView === leftCollectionView || collectionView === rightAccountCollectionView {
            prefetchSidebarAvatars(in: collectionView, at: indexPaths)
            return
        }

        guard collectionView === messageCollectionView else { return }
        let width = collectionView.bounds.width
        guard width > 0 else { return }
        for indexPath in indexPaths {
            guard let message = renderedMessage(at: indexPath) else { continue }
            if let previousMessageID = messageMediaPrefetchIDsByIndexPath[indexPath],
               previousMessageID != message.id {
                cancelMessageMediaPrefetch(for: previousMessageID)
            }
            messageMediaPrefetchIDsByIndexPath[indexPath] = message.id
            let groupSettings = isHomeTimeline
                ? GroupDisplaySettings(showsAvatar: false, showsName: true)
                : groupDisplaySettings(for: message)
            _ = cachedHeight(
                for: message,
                width: width,
                showsGroupAvatar: groupSettings.showsAvatar,
                showsGroupName: groupSettings.showsName,
                usesUnansweredPresentation: isHomeTimeline
            )
            prefetchMessageMedia(for: message)
        }
    }

    func collectionView(_ collectionView: UICollectionView, cancelPrefetchingForItemsAt indexPaths: [IndexPath]) {
        if collectionView === leftCollectionView || collectionView === rightAccountCollectionView {
            cancelSidebarAvatarPrefetch(in: collectionView, at: indexPaths)
            return
        }
        guard collectionView === messageCollectionView else { return }
        for indexPath in indexPaths {
            let messageID = messageMediaPrefetchIDsByIndexPath.removeValue(forKey: indexPath)
                ?? renderedMessage(at: indexPath)?.id
            guard let messageID else { continue }
            cancelMessageMediaPrefetch(for: messageID)
        }
    }

    func cancelMessageMediaPrefetch(for messageID: UUID) {
        let group = messageMediaPrefetchGroups.removeValue(forKey: messageID)
        messageMediaPrefetchIDsByIndexPath = messageMediaPrefetchIDsByIndexPath.filter {
            $0.value != messageID
        }
        group?.requests.forEach { $0.cancel() }
    }

    func cancelOutstandingCollectionPrefetches() {
        let mediaGroups = Array(messageMediaPrefetchGroups.values)
        let avatarRequests = Array(sidebarAvatarPrefetchTasks.values)
        messageMediaPrefetchGroups.removeAll(keepingCapacity: true)
        messageMediaPrefetchIDsByIndexPath.removeAll(keepingCapacity: true)
        sidebarAvatarPrefetchTasks.removeAll(keepingCapacity: true)
        mediaGroups.forEach { group in
            group.requests.forEach { $0.cancel() }
        }
        avatarRequests.forEach { $0.cancel() }
    }

    struct MessageMediaPrefetchDescriptor {
        let url: URL
        let kind: MediaThumbnailPipeline.ContentKind
        let targetSize: CGSize
    }

    func prefetchMessageMedia(for message: ChatMessage) {
        guard messageMediaPrefetchGroups[message.id] == nil else { return }
        let scale = messageCollectionView.window?.screen.scale ?? UIScreen.main.scale
        let pending = messageMediaPrefetchDescriptors(for: message).filter { descriptor in
            MediaThumbnailPipeline.shared.cachedImage(
                for: descriptor.url,
                kind: descriptor.kind,
                targetSize: descriptor.targetSize,
                scale: scale
            ) == nil
        }
        guard !pending.isEmpty else { return }

        let group = MessageMediaPrefetchGroup(remainingRequestCount: pending.count)
        messageMediaPrefetchGroups[message.id] = group
        for descriptor in pending {
            let request = MediaThumbnailPipeline.shared.load(
                descriptor.url,
                kind: descriptor.kind,
                targetSize: descriptor.targetSize,
                scale: scale,
                priority: URLSessionTask.lowPriority
            ) { [weak self, weak group] _ in
                guard let self, let group,
                      self.messageMediaPrefetchGroups[message.id] === group
                else { return }
                group.remainingRequestCount -= 1
                if group.remainingRequestCount == 0 {
                    self.messageMediaPrefetchGroups.removeValue(forKey: message.id)
                    self.messageMediaPrefetchIDsByIndexPath = self.messageMediaPrefetchIDsByIndexPath.filter {
                        $0.value != message.id
                    }
                }
            }
            group.requests.append(request)
        }
    }

    func messageMediaPrefetchDescriptors(for message: ChatMessage) -> [MessageMediaPrefetchDescriptor] {
        var descriptors: [MessageMediaPrefetchDescriptor] = []
        if let url = message.attachmentURL {
            let descriptor: MessageMediaPrefetchDescriptor?
            switch message.type {
            case .stickerGif:
                descriptor = MessageMediaPrefetchDescriptor(
                    url: url,
                    kind: .image(trimsTransparentCanvas: !url.isFileURL),
                    targetSize: CGSize(width: 320, height: 320)
                )
            case .image, .capturedPhoto:
                let extensionName = url.pathExtension.lowercased()
                let trimsTransparentCanvas = !url.isFileURL
                    || (message.type != .capturedPhoto && ["png", "webp"].contains(extensionName))
                descriptor = MessageMediaPrefetchDescriptor(
                    url: url,
                    kind: .image(trimsTransparentCanvas: trimsTransparentCanvas),
                    targetSize: CGSize(width: 320, height: 320)
                )
            case .video:
                descriptor = MessageMediaPrefetchDescriptor(
                    url: url,
                    kind: .video,
                    targetSize: CGSize(width: 320, height: 320)
                )
            case .channelsVideo:
                descriptor = MessageMediaPrefetchDescriptor(
                    url: url,
                    kind: .video,
                    targetSize: CGSize(width: 480, height: 320)
                )
            case .favorite:
                descriptor = MessageMediaPrefetchDescriptor(
                    url: url,
                    kind: .image(trimsTransparentCanvas: extensionName(for: url) != "gif"),
                    targetSize: CGSize(width: 84, height: 84)
                )
            default:
                descriptor = nil
            }
            if let descriptor { descriptors.append(descriptor) }
        }

        for element in message.richElements {
            guard case .image(_, let urlText, let aspect, _) = element,
                  let url = URL(string: urlText)
            else { continue }
            let height: CGFloat = aspect == .vertical ? 168 : 96
            descriptors.append(
                MessageMediaPrefetchDescriptor(
                    url: url,
                    kind: .image(trimsTransparentCanvas: true),
                    targetSize: CGSize(width: 360, height: height)
                )
            )
        }
        return descriptors
    }

    private func extensionName(for url: URL) -> String {
        url.pathExtension.lowercased()
    }

    func prefetchSidebarAvatars(in collectionView: UICollectionView, at indexPaths: [IndexPath]) {
        for url in sidebarAvatarURLs(in: collectionView, at: indexPaths) {
            guard sidebarAvatarPrefetchTasks[url] == nil,
                  AvatarPipeline.shared.cachedImage(for: url) == nil
            else { continue }
            sidebarAvatarPrefetchTasks[url] = AvatarPipeline.shared.load(
                url,
                priority: URLSessionTask.lowPriority
            ) { [weak self] _ in
                self?.sidebarAvatarPrefetchTasks.removeValue(forKey: url)
            }
        }
    }

    func prefetchVisibleSidebarAvatarWindow(in collectionView: UICollectionView, extraItems: Int = 14) {
        guard collectionView === leftCollectionView || collectionView === rightAccountCollectionView else { return }
        let itemCount = collectionView.numberOfItems(inSection: 0)
        guard itemCount > 0 else { return }
        let visibleItems = collectionView.indexPathsForVisibleItems.map(\.item)
        let lower: Int
        let upper: Int
        if let minVisible = visibleItems.min(), let maxVisible = visibleItems.max() {
            lower = max(0, minVisible - extraItems)
            upper = min(itemCount - 1, maxVisible + extraItems)
        } else {
            let estimatedStart = max(0, Int(collectionView.contentOffset.y / max(Self.sidebarItemSize.height + 10, 1)) - extraItems)
            lower = estimatedStart
            upper = min(itemCount - 1, estimatedStart + extraItems * 2 + 8)
        }
        guard lower <= upper else { return }
        let indexPaths = (lower...upper).map { IndexPath(item: $0, section: 0) }
        prefetchSidebarAvatars(in: collectionView, at: indexPaths)
    }

    func cancelSidebarAvatarPrefetch(in collectionView: UICollectionView, at indexPaths: [IndexPath]) {
        for url in sidebarAvatarURLs(in: collectionView, at: indexPaths) {
            sidebarAvatarPrefetchTasks[url]?.cancel()
            sidebarAvatarPrefetchTasks.removeValue(forKey: url)
        }
    }

    func sidebarAvatarURLs(in collectionView: UICollectionView, at indexPaths: [IndexPath]) -> [URL] {
        let items: [SidebarItem]
        if collectionView === leftCollectionView {
            items = visibleLeftItemsForSelectedAccount()
        } else if collectionView === rightAccountCollectionView {
            items = visibleRightAccountItems()
        } else {
            return []
        }

        var seen = Set<URL>()
        return indexPaths.compactMap { indexPath in
            guard let item = items[safe: indexPath.item],
                  let url = sidebarAvatarURL(for: item.participantID) ?? item.avatarURL,
                  seen.insert(url).inserted
            else { return nil }
            return url
        }
    }
}


extension ChatWindowViewController {
    func visibleRightToolItems() -> [SidebarItem] {
        let referenceMessageID = state.visibleMessages.first?.id
            ?? state.messages.first?.id
            ?? UUID()
        let momentsItem = SidebarItem(
            id: UUID(uuidString: "22222222-2222-2222-2222-222222222222") ?? UUID(),
            kind: .tool,
            title: Self.momentsToolTitle,
            symbolName: "camera.aperture",
            tintColor: UIColor(red: 0.19, green: 0.45, blue: 0.78, alpha: 1),
            participantID: nil,
            targetMessageID: referenceMessageID,
            isActive: false,
            customTool: .moments
        )
        let autoReplyItem = SidebarItem(
            id: UUID(uuidString: "33333333-3333-3333-3333-333333333333") ?? UUID(),
            kind: .tool,
            title: Self.autoReplyToolTitle,
            symbolName: autoReplyConfiguration.isEnabled ? "bolt.badge.checkmark" : "bolt.badge.xmark",
            tintColor: autoReplyConfiguration.isEnabled
                ? UIColor(red: 0.10, green: 0.58, blue: 0.36, alpha: 1)
                : UIColor(red: 0.42, green: 0.47, blue: 0.52, alpha: 1),
            participantID: nil,
            targetMessageID: referenceMessageID,
            isActive: autoReplyConfiguration.isEnabled,
            customTool: .autoReply
        )
        let accessReviewItem = SidebarItem(
            id: UUID(uuidString: "11111111-1111-1111-1111-111111111111") ?? UUID(),
            kind: .tool,
            title: Self.accessReviewToolTitle,
            symbolName: "checkmark.shield",
            tintColor: UIColor(red: 0.28, green: 0.36, blue: 0.78, alpha: 1),
            participantID: nil,
            targetMessageID: referenceMessageID,
            isActive: false,
            customTool: .accessReview
        )
        let hiddenUsersItem = SidebarItem(
            id: UUID(uuidString: "44444444-4444-4444-4444-444444444444") ?? UUID(),
            kind: .tool,
            title: Self.hiddenUsersToolTitle,
            symbolName: "eye.slash",
            tintColor: UIColor(red: 0.50, green: 0.42, blue: 0.62, alpha: 1),
            participantID: nil,
            targetMessageID: referenceMessageID,
            isActive: !hiddenParticipantIDs.isEmpty,
            customTool: .hiddenUsers
        )
        let leftSidebarDisplayItem = SidebarItem(
            id: UUID(uuidString: "44444444-4444-4444-4444-555555555555") ?? UUID(),
            kind: .tool,
            title: leftSidebarDisplayMode.toolTitle,
            symbolName: leftSidebarDisplayMode.symbolName,
            tintColor: leftSidebarDisplayMode.tintColor,
            participantID: nil,
            targetMessageID: referenceMessageID,
            isActive: true,
            customTool: .leftSidebarDisplay
        )
        let sideEffectItem = SidebarItem(
            id: UUID(uuidString: "55555555-5555-5555-5555-555555555555") ?? UUID(),
            kind: .tool,
            title: Self.sideEffectToolTitle,
            symbolName: "water.waves",
            tintColor: sideEffectStrokeColor.withAlphaComponent(1),
            participantID: nil,
            targetMessageID: referenceMessageID,
            isActive: true,
            customTool: .sideEffect
        )
        let bubble3DItem = SidebarItem(
            id: UUID(uuidString: "77777777-7777-7777-7777-777777777777") ?? UUID(),
            kind: .tool,
            title: Self.bubble3DToolTitle,
            symbolName: bubble3DAppearanceEnabled ? "cube.fill" : "cube",
            tintColor: bubble3DAppearanceEnabled
                ? UIColor(red: 0.22, green: 0.38, blue: 0.82, alpha: 1)
                : UIColor(red: 0.42, green: 0.47, blue: 0.52, alpha: 1),
            participantID: nil,
            targetMessageID: referenceMessageID,
            isActive: bubble3DAppearanceEnabled,
            customTool: .bubble3D
        )
        let sendNameEnabled = sendNameEnabled(for: state.selectedAccountID)
        let sendNameItem = SidebarItem(
            id: UUID(uuidString: "66666666-6666-6666-6666-666666666666") ?? UUID(),
            kind: .tool,
            title: Self.sendNameToolTitle,
            symbolName: sendNameEnabled ? "person.text.rectangle.fill" : "person.text.rectangle",
            tintColor: sendNameEnabled
                ? UIColor(red: 0.16, green: 0.54, blue: 0.42, alpha: 1)
                : UIColor(red: 0.46, green: 0.50, blue: 0.54, alpha: 1),
            participantID: state.selectedAccountID,
            targetMessageID: referenceMessageID,
            isActive: sendNameEnabled,
            customTool: .sendName
        )
        let finderPublishItem = SidebarItem(
            id: UUID(uuidString: "88888888-8888-8888-8888-888888888888") ?? UUID(),
            kind: .tool,
            title: Self.finderPublishToolTitle,
            symbolName: "video.badge.plus",
            tintColor: UIColor(red: 0.50, green: 0.36, blue: 0.86, alpha: 1),
            participantID: nil,
            targetMessageID: referenceMessageID,
            isActive: false,
            customTool: .finderPublish
        )
        let customerProfileItem = SidebarItem(
            id: UUID(uuidString: "99999999-9999-9999-9999-999999999999") ?? UUID(),
            kind: .tool,
            title: Self.customerProfileToolTitle,
            symbolName: "person.text.rectangle",
            tintColor: UIColor(red: 0.16, green: 0.52, blue: 0.68, alpha: 1),
            participantID: nil,
            targetMessageID: referenceMessageID,
            isActive: false,
            customTool: .customerProfile
        )
        let contactRelationsItem = SidebarItem(
            id: UUID(uuidString: "99999999-aaaa-bbbb-cccc-999999999999") ?? UUID(),
            kind: .tool,
            title: Self.contactRelationsToolTitle,
            symbolName: "person.2.wave.2",
            tintColor: UIColor(red: 0.50, green: 0.42, blue: 0.16, alpha: 1),
            participantID: nil,
            targetMessageID: referenceMessageID,
            isActive: false,
            customTool: .contactRelations
        )
        let momentMaterialsItem = SidebarItem(
            id: UUID(uuidString: "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa") ?? UUID(),
            kind: .tool,
            title: Self.momentMaterialsToolTitle,
            symbolName: "tray.full",
            tintColor: UIColor(red: 0.42, green: 0.58, blue: 0.22, alpha: 1),
            participantID: nil,
            targetMessageID: referenceMessageID,
            isActive: false,
            customTool: .momentMaterials
        )
        let wechatMiniProgramItem = SidebarItem(
            id: UUID(uuidString: "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb") ?? UUID(),
            kind: .tool,
            title: Self.wechatMiniProgramToolTitle,
            symbolName: "app.badge",
            tintColor: UIColor(red: 0.10, green: 0.62, blue: 0.36, alpha: 1),
            participantID: nil,
            targetMessageID: referenceMessageID,
            isActive: false,
            customTool: .wechatMiniProgramShare
        )
        let blinkVoiceItem = SidebarItem(
            id: UUID(uuidString: "cccccccc-cccc-cccc-cccc-cccccccccccc") ?? UUID(),
            kind: .tool,
            title: Self.blinkVoiceToolTitle,
            symbolName: "eye",
            tintColor: UIColor(red: 0.18, green: 0.55, blue: 0.76, alpha: 1),
            participantID: nil,
            targetMessageID: referenceMessageID,
            isActive: false,
            customTool: .blinkVoice
        )
        let voiceAssistantItem = SidebarItem(
            id: UUID(uuidString: "cccccccc-cccc-cccc-cccc-dddddddddddd") ?? UUID(),
            kind: .tool,
            title: Self.voiceAssistantToolTitle,
            symbolName: "waveform.and.mic",
            tintColor: UIColor(red: 0.44, green: 0.36, blue: 0.86, alpha: 1),
            participantID: nil,
            targetMessageID: referenceMessageID,
            isActive: false,
            customTool: .voiceAssistant
        )
        let selectionAssistItem = SidebarItem(
            id: UUID(uuidString: "cccccccc-cccc-cccc-cccc-eeeeeeeeeeee") ?? UUID(),
            kind: .tool,
            title: Self.selectionAssistToolTitle,
            symbolName: "scope",
            tintColor: UIColor(red: 0.22, green: 0.50, blue: 0.72, alpha: 1),
            participantID: nil,
            targetMessageID: referenceMessageID,
            isActive: selectionAssistOverlayView != nil || selectionLassoOverlayView != nil,
            customTool: .selectionAssist
        )
        let backgroundRemovalItem = SidebarItem(
            id: UUID(uuidString: "cccccccc-cccc-cccc-cccc-ffffffffffff") ?? UUID(),
            kind: .tool,
            title: Self.backgroundRemovalToolTitle,
            symbolName: automaticBackgroundRemovalEnabled ? "person.crop.rectangle.badge.checkmark" : "person.crop.rectangle",
            tintColor: automaticBackgroundRemovalEnabled
                ? UIColor(red: 0.10, green: 0.58, blue: 0.42, alpha: 1)
                : UIColor(red: 0.42, green: 0.48, blue: 0.56, alpha: 1),
            participantID: nil,
            targetMessageID: referenceMessageID,
            isActive: automaticBackgroundRemovalEnabled,
            customTool: .backgroundRemoval
        )
        let uiOperationLabItem = SidebarItem(
            id: UUID(uuidString: "dddddddd-dddd-dddd-dddd-dddddddddddd") ?? UUID(),
            kind: .tool,
            title: Self.uiOperationLabToolTitle,
            symbolName: "slider.horizontal.3",
            tintColor: UIColor(red: 0.20, green: 0.48, blue: 0.62, alpha: 1),
            participantID: nil,
            targetMessageID: referenceMessageID,
            isActive: false,
            customTool: .uiOperationLab
        )
        let openApiEnvironmentItem = SidebarItem(
            id: UUID(uuidString: "eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee") ?? UUID(),
            kind: .tool,
            title: Self.openApiEnvironmentToolTitle,
            symbolName: "network",
            tintColor: UIColor(red: 0.18, green: 0.42, blue: 0.68, alpha: 1),
            participantID: nil,
            targetMessageID: referenceMessageID,
            isActive: false,
            customTool: .openApiEnvironment
        )
        let openApiBusinessItem = SidebarItem(
            id: UUID(uuidString: "eeeeeeee-eeee-eeee-eeee-ffffffffffff") ?? UUID(),
            kind: .tool,
            title: Self.openApiBusinessToolTitle,
            symbolName: "iphone.and.arrow.forward",
            tintColor: UIColor(red: 0.12, green: 0.50, blue: 0.46, alpha: 1),
            participantID: nil,
            targetMessageID: referenceMessageID,
            isActive: false,
            customTool: .openApiBusiness
        )
        let openApiFriendsItem = SidebarItem(
            id: UUID(uuidString: "eeeeeeee-eeee-eeee-eeee-aaaaaaaaaaaa") ?? UUID(),
            kind: .tool,
            title: Self.openApiFriendsToolTitle,
            symbolName: "person.crop.circle.badge.plus",
            tintColor: UIColor(red: 0.12, green: 0.56, blue: 0.36, alpha: 1),
            participantID: nil,
            targetMessageID: referenceMessageID,
            isActive: isOpenApiIMSyncing,
            customTool: .openApiFriends
        )
        let syncChatroomsItem = SidebarItem(
            id: UUID(uuidString: "eeeeeeee-eeee-eeee-eeee-bbbbbbbbbbbb") ?? UUID(),
            kind: .tool,
            title: Self.syncChatroomsToolTitle,
            symbolName: isOpenApiIMSyncing ? "arrow.triangle.2.circlepath.circle.fill" : "arrow.triangle.2.circlepath.circle",
            tintColor: isOpenApiIMSyncing
                ? UIColor(red: 0.20, green: 0.46, blue: 0.80, alpha: 1)
                : UIColor(red: 0.18, green: 0.43, blue: 0.74, alpha: 1),
            participantID: state.selectedAccountID,
            targetMessageID: referenceMessageID,
            isActive: isOpenApiIMSyncing,
            customTool: .syncChatrooms
        )
        let orderedToolItems = orderedRightToolItems(
            allItems: [
                blinkVoiceItem,
                voiceAssistantItem,
                selectionAssistItem,
                backgroundRemovalItem,
                uiOperationLabItem,
                openApiEnvironmentItem,
                openApiBusinessItem,
                openApiFriendsItem,
                syncChatroomsItem,
                autoReplyItem,
                momentsItem,
                momentMaterialsItem,
                wechatMiniProgramItem,
                finderPublishItem,
                customerProfileItem,
                contactRelationsItem,
                accessReviewItem,
                hiddenUsersItem,
                leftSidebarDisplayItem,
                sideEffectItem,
                bubble3DItem,
                sendNameItem
            ] + state.rightToolItems
        )
        let baseItems = state.activeFriend?.isAIAccount == true
            ? orderedToolItems.filter { item in
                item.toolMessageType != .voiceCall
                    && item.toolMessageType != .videoCall
                    && item.toolMessageType != .redPacket
                    && item.toolMessageType != .transfer
                    && item.toolMessageType != .splitBill
            }
            : orderedToolItems

        guard let group = state.activeFriend, isGroupConversation(group) else {
            return baseItems
        }
        let groupInfoItem = SidebarItem(
            id: group.id,
            kind: .tool,
            title: Self.groupInfoToolTitle,
            symbolName: "person.3.fill",
            tintColor: UIColor(red: 0.18, green: 0.48, blue: 0.76, alpha: 1),
            participantID: group.id,
            targetMessageID: referenceMessageID,
            isActive: false,
            toolMessageType: nil
        )
        return [groupInfoItem] + baseItems
    }

    func canReorderRightTool(at indexPath: IndexPath) -> Bool {
        let items = visibleRightToolItems()
        guard let item = items[safe: indexPath.item] else { return false }
        return item.title != Self.groupInfoToolTitle
    }

    func rightToolOrderKey(for item: SidebarItem) -> String {
        if let customTool = item.customTool {
            return "custom:\(customTool)"
        }
        if let type = item.toolMessageType {
            return "type:\(type.rawValue)"
        }
        return "item:\(item.id.uuidString)"
    }

    func defaultRightToolOrderKeys() -> [String] {
        let referenceMessageID = state.messages.first?.id ?? UUID()
        let fixedItems = [
            SidebarItem(
                id: UUID(uuidString: "cccccccc-cccc-cccc-cccc-cccccccccccc") ?? UUID(),
                kind: .tool,
                title: Self.blinkVoiceToolTitle,
                symbolName: "eye",
                tintColor: UIColor(red: 0.18, green: 0.55, blue: 0.76, alpha: 1),
                participantID: nil,
                targetMessageID: referenceMessageID,
                isActive: false,
                customTool: .blinkVoice
            ),
            SidebarItem(
                id: UUID(uuidString: "cccccccc-cccc-cccc-cccc-dddddddddddd") ?? UUID(),
                kind: .tool,
                title: Self.voiceAssistantToolTitle,
                symbolName: "waveform.and.mic",
                tintColor: UIColor(red: 0.44, green: 0.36, blue: 0.86, alpha: 1),
                participantID: nil,
                targetMessageID: referenceMessageID,
                isActive: false,
                customTool: .voiceAssistant
            ),
            SidebarItem(
                id: UUID(uuidString: "cccccccc-cccc-cccc-cccc-eeeeeeeeeeee") ?? UUID(),
                kind: .tool,
                title: Self.selectionAssistToolTitle,
                symbolName: "scope",
                tintColor: UIColor(red: 0.22, green: 0.50, blue: 0.72, alpha: 1),
                participantID: nil,
                targetMessageID: referenceMessageID,
                isActive: false,
                customTool: .selectionAssist
            ),
            SidebarItem(
                id: UUID(uuidString: "cccccccc-cccc-cccc-cccc-ffffffffffff") ?? UUID(),
                kind: .tool,
                title: Self.backgroundRemovalToolTitle,
                symbolName: "person.crop.rectangle",
                tintColor: UIColor(red: 0.10, green: 0.58, blue: 0.42, alpha: 1),
                participantID: nil,
                targetMessageID: referenceMessageID,
                isActive: false,
                customTool: .backgroundRemoval
            ),
            SidebarItem(
                id: UUID(uuidString: "dddddddd-dddd-dddd-dddd-dddddddddddd") ?? UUID(),
                kind: .tool,
                title: Self.uiOperationLabToolTitle,
                symbolName: "slider.horizontal.3",
                tintColor: UIColor(red: 0.20, green: 0.48, blue: 0.62, alpha: 1),
                participantID: nil,
                targetMessageID: referenceMessageID,
                isActive: false,
                customTool: .uiOperationLab
            ),
            SidebarItem(
                id: UUID(uuidString: "eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee") ?? UUID(),
                kind: .tool,
                title: Self.openApiEnvironmentToolTitle,
                symbolName: "network",
                tintColor: UIColor(red: 0.18, green: 0.42, blue: 0.68, alpha: 1),
                participantID: nil,
                targetMessageID: referenceMessageID,
                isActive: false,
                customTool: .openApiEnvironment
            ),
            SidebarItem(
                id: UUID(uuidString: "eeeeeeee-eeee-eeee-eeee-ffffffffffff") ?? UUID(),
                kind: .tool,
                title: Self.openApiBusinessToolTitle,
                symbolName: "point.3.connected.trianglepath.dotted",
                tintColor: UIColor(red: 0.12, green: 0.50, blue: 0.46, alpha: 1),
                participantID: nil,
                targetMessageID: referenceMessageID,
                isActive: false,
                customTool: .openApiBusiness
            ),
            SidebarItem(
                id: UUID(uuidString: "eeeeeeee-eeee-eeee-eeee-aaaaaaaaaaaa") ?? UUID(),
                kind: .tool,
                title: Self.openApiFriendsToolTitle,
                symbolName: "person.crop.circle.badge.plus",
                tintColor: .systemGreen,
                participantID: nil,
                targetMessageID: referenceMessageID,
                isActive: false,
                customTool: .openApiFriends
            ),
            SidebarItem(
                id: UUID(uuidString: "eeeeeeee-eeee-eeee-eeee-bbbbbbbbbbbb") ?? UUID(),
                kind: .tool,
                title: Self.syncChatroomsToolTitle,
                symbolName: "arrow.triangle.2.circlepath.circle",
                tintColor: UIColor(red: 0.18, green: 0.43, blue: 0.74, alpha: 1),
                participantID: nil,
                targetMessageID: referenceMessageID,
                isActive: false,
                customTool: .syncChatrooms
            ),
            SidebarItem(
                id: UUID(uuidString: "55555555-5555-5555-5555-555555555555") ?? UUID(),
                kind: .tool,
                title: Self.sideEffectToolTitle,
                symbolName: "water.waves",
                tintColor: .systemTeal,
                participantID: nil,
                targetMessageID: referenceMessageID,
                isActive: false,
                customTool: .sideEffect
            ),
            SidebarItem(
                id: UUID(uuidString: "77777777-7777-7777-7777-777777777777") ?? UUID(),
                kind: .tool,
                title: Self.bubble3DToolTitle,
                symbolName: "cube",
                tintColor: .systemIndigo,
                participantID: nil,
                targetMessageID: referenceMessageID,
                isActive: false,
                customTool: .bubble3D
            ),
            SidebarItem(
                id: UUID(uuidString: "33333333-3333-3333-3333-333333333333") ?? UUID(),
                kind: .tool,
                title: Self.autoReplyToolTitle,
                symbolName: "bolt.badge.xmark",
                tintColor: .systemGray,
                participantID: nil,
                targetMessageID: referenceMessageID,
                isActive: false,
                customTool: .autoReply
            ),
            SidebarItem(
                id: UUID(uuidString: "22222222-2222-2222-2222-222222222222") ?? UUID(),
                kind: .tool,
                title: Self.momentsToolTitle,
                symbolName: "camera.aperture",
                tintColor: .systemBlue,
                participantID: nil,
                targetMessageID: referenceMessageID,
                isActive: false,
                customTool: .moments
            ),
            SidebarItem(
                id: UUID(uuidString: "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa") ?? UUID(),
                kind: .tool,
                title: Self.momentMaterialsToolTitle,
                symbolName: "tray.full",
                tintColor: UIColor(red: 0.42, green: 0.58, blue: 0.22, alpha: 1),
                participantID: nil,
                targetMessageID: referenceMessageID,
                isActive: false,
                customTool: .momentMaterials
            ),
            SidebarItem(
                id: UUID(uuidString: "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb") ?? UUID(),
                kind: .tool,
                title: Self.wechatMiniProgramToolTitle,
                symbolName: "app.badge",
                tintColor: UIColor(red: 0.10, green: 0.62, blue: 0.36, alpha: 1),
                participantID: nil,
                targetMessageID: state.visibleMessages.first?.id ?? UUID(),
                isActive: false,
                customTool: .wechatMiniProgramShare
            ),
            SidebarItem(
                id: UUID(uuidString: "88888888-8888-8888-8888-888888888888") ?? UUID(),
                kind: .tool,
                title: Self.finderPublishToolTitle,
                symbolName: "video.badge.plus",
                tintColor: .systemPurple,
                participantID: nil,
                targetMessageID: referenceMessageID,
                isActive: false,
                customTool: .finderPublish
            ),
            SidebarItem(
                id: UUID(uuidString: "99999999-9999-9999-9999-999999999999") ?? UUID(),
                kind: .tool,
                title: Self.customerProfileToolTitle,
                symbolName: "person.text.rectangle",
                tintColor: .systemCyan,
                participantID: nil,
                targetMessageID: referenceMessageID,
                isActive: false,
                customTool: .customerProfile
            ),
            SidebarItem(
                id: UUID(uuidString: "99999999-aaaa-bbbb-cccc-999999999999") ?? UUID(),
                kind: .tool,
                title: Self.contactRelationsToolTitle,
                symbolName: "person.2.wave.2",
                tintColor: UIColor(red: 0.50, green: 0.42, blue: 0.16, alpha: 1),
                participantID: nil,
                targetMessageID: referenceMessageID,
                isActive: false,
                customTool: .contactRelations
            ),
            SidebarItem(
                id: UUID(uuidString: "11111111-1111-1111-1111-111111111111") ?? UUID(),
                kind: .tool,
                title: Self.accessReviewToolTitle,
                symbolName: "checkmark.shield",
                tintColor: .systemIndigo,
                participantID: nil,
                targetMessageID: referenceMessageID,
                isActive: false,
                customTool: .accessReview
            ),
            SidebarItem(
                id: UUID(uuidString: "44444444-4444-4444-4444-444444444444") ?? UUID(),
                kind: .tool,
                title: Self.hiddenUsersToolTitle,
                symbolName: "eye.slash",
                tintColor: .systemPurple,
                participantID: nil,
                targetMessageID: referenceMessageID,
                isActive: false,
                customTool: .hiddenUsers
            ),
            SidebarItem(
                id: UUID(uuidString: "44444444-4444-4444-4444-555555555555") ?? UUID(),
                kind: .tool,
                title: Self.leftSidebarDisplayToolTitle,
                symbolName: "person.2.fill",
                tintColor: UIColor(red: 0.22, green: 0.50, blue: 0.68, alpha: 1),
                participantID: nil,
                targetMessageID: referenceMessageID,
                isActive: true,
                customTool: .leftSidebarDisplay
            ),
            SidebarItem(
                id: UUID(uuidString: "66666666-6666-6666-6666-666666666666") ?? UUID(),
                kind: .tool,
                title: Self.sendNameToolTitle,
                symbolName: "person.text.rectangle.fill",
                tintColor: .systemGreen,
                participantID: nil,
                targetMessageID: referenceMessageID,
                isActive: true,
                customTool: .sendName
            )
        ]
        return (fixedItems + state.rightToolItems).map(rightToolOrderKey)
    }

    func orderedRightToolItems(allItems: [SidebarItem]) -> [SidebarItem] {
        let keyedItems = Dictionary(allItems.map { (rightToolOrderKey(for: $0), $0) }, uniquingKeysWith: { current, _ in current })
        var keys = customRightToolOrderKeys.filter { keyedItems[$0] != nil }
        let missingKeys = allItems.map(rightToolOrderKey).filter { !keys.contains($0) }
        keys.append(contentsOf: missingKeys)
        customRightToolOrderKeys = keys
        return keys.compactMap { keyedItems[$0] }
    }

    func customRightToolOrderIndex(forVisibleIndex visibleIndex: Int) -> Int? {
        let visibleItems = visibleRightToolItems()
        guard visibleItems.indices.contains(visibleIndex) else { return nil }
        let key = rightToolOrderKey(for: visibleItems[visibleIndex])
        return customRightToolOrderKeys.firstIndex(of: key)
    }

    func visibleLeftItemsForSelectedAccount() -> [SidebarItem] {
        let cacheKey = visibleLeftItemsCurrentCacheKey()
        if cacheKey == visibleLeftItemsCacheKey {
            return visibleLeftItemsCache
        }
        let participantsByID = participantLookupByID()
        let allItems = baseVisibleLeftItems(participantsByID: participantsByID)
        let items: [SidebarItem]
        if isHomeTimeline {
            let allHomeItems = leftItemsWithPinnedAI(
                applyLeftSidebarDisplayMode(to: allItems, participantsByID: participantsByID),
                participantsByID: participantsByID
            )
            let unreadConversationIDs = renderedMessages.map(\.conversationID)
            let unreadConversationIDSet = Set(unreadConversationIDs)
            let homeItems = allHomeItems.filter { item in
                item.participantID.map(unreadConversationIDSet.contains) ?? false
            }
            let unreadRank = Dictionary(
                unreadConversationIDs.enumerated().map { ($0.element, $0.offset) },
                uniquingKeysWith: min
            )
            items = homeItems.enumerated().sorted { lhs, rhs in
                let lhsRank = lhs.element.participantID.flatMap { unreadRank[$0] } ?? Int.max
                let rhsRank = rhs.element.participantID.flatMap { unreadRank[$0] } ?? Int.max
                return lhsRank == rhsRank ? lhs.offset < rhs.offset : lhsRank < rhsRank
            }.map(\.element)
        } else if isAccountDirectory {
            items = leftItemsForAccount(
                state.selectedAccountID,
                from: allItems,
                keepSelectedConversation: false,
                participantsByID: participantsByID
            )
        } else {
            items = leftItemsForAccount(
                state.selectedAccountID,
                from: allItems,
                keepSelectedConversation: true,
                participantsByID: participantsByID
            )
        }
        let resolvedItems = items.map {
            leftSidebarItem($0, active: $0.participantID == state.selectedFriendID)
        }
        visibleLeftItemsCacheKey = cacheKey
        visibleLeftItemsCache = resolvedItems
        return resolvedItems
    }

    func participantLookupByID() -> [UUID: ChatParticipant] {
        let cacheKey = participantLookupCurrentCacheKey()
        if cacheKey == participantLookupCacheKey {
            return participantLookupCache
        }
        let lookup = Dictionary(state.participants.map { ($0.id, $0) }, uniquingKeysWith: { current, _ in current })
        participantLookupCacheKey = cacheKey
        participantLookupCache = lookup
        return lookup
    }

    func participantLookupCurrentCacheKey() -> String {
        [
            "\(state.participants.count)",
            state.participants.last?.id.uuidString ?? "none",
            openApiIMBootstrapCacheKey,
            "contacts:\(openApiConversationContextsByID.count)",
            "accounts:\(openApiAccountContextsByID.count)",
            "remarks:\(participantRemarksByID.sorted { $0.key.uuidString < $1.key.uuidString }.map { "\($0.key.uuidString)=\($0.value)" }.joined(separator: ","))"
        ].joined(separator: "|")
    }

    func latestSidebarMessagesByParticipantID() -> [UUID: ChatMessage] {
        let cacheKey = latestSidebarMessagesCurrentCacheKey()
        if cacheKey == latestSidebarMessagesCacheKey {
            return latestSidebarMessagesCache
        }
        let latest = Self.latestMessagesByConversation(
            from: state.messages,
            selectedAccountID: state.selectedAccountID
        )
        latestSidebarMessagesCacheKey = cacheKey
        latestSidebarMessagesCache = latest
        latestSidebarSummaryCacheKey = ""
        latestSidebarSummaryCache = [:]
        return latest
    }

    func latestSidebarMessagesCurrentCacheKey() -> String {
        [
            state.selectedAccountID.uuidString,
            "\(state.messages.count)",
            state.messages.last?.id.uuidString ?? "none",
            state.messages.last.map { "\($0.sentAt.timeIntervalSince1970)" } ?? "0"
        ].joined(separator: "|")
    }

    func leftSidebarIndexByParticipantID() -> [UUID: Int] {
        let items = visibleLeftItemsForSelectedAccount()
        let cacheKey = visibleLeftItemsCacheKey
        if cacheKey == visibleLeftIndexCacheKey {
            return visibleLeftIndexByParticipantID
        }
        var indexByID: [UUID: Int] = [:]
        indexByID.reserveCapacity(items.count)
        for (index, item) in items.enumerated() {
            guard let participantID = item.participantID,
                  indexByID[participantID] == nil
            else { continue }
            indexByID[participantID] = index
        }
        visibleLeftIndexCacheKey = cacheKey
        visibleLeftIndexByParticipantID = indexByID
        return indexByID
    }

    func prefetchCurrentSidebarAvatarWindow() {
        prefetchVisibleSidebarAvatarWindow(in: leftCollectionView, extraItems: 20)
        prefetchVisibleSidebarAvatarWindow(in: rightAccountCollectionView, extraItems: 8)
        let leftCount = min(visibleLeftItemsForSelectedAccount().count, 24)
        if leftCount > 0 {
            prefetchSidebarAvatars(
                in: leftCollectionView,
                at: (0..<leftCount).map { IndexPath(item: $0, section: 0) }
            )
        }

        let rightCount = min(visibleRightAccountItems().count, 8)
        if rightCount > 0 {
            prefetchSidebarAvatars(
                in: rightAccountCollectionView,
                at: (0..<rightCount).map { IndexPath(item: $0, section: 0) }
            )
        }
    }

    func visibleLeftItemsCurrentCacheKey() -> String {
        [
            "v:\(visibleDataRevision)",
            "mode:\(leftSidebarDisplayMode.rawValue)",
            "home:\(isHomeTimeline)",
            "directory:\(isAccountDirectory)",
            state.selectedFriendID?.uuidString ?? "home",
            state.selectedAccountID.uuidString,
            unansweredAccountFilterID?.uuidString ?? "all-accounts",
            "homeLimit:\(homeRenderedMessageLimit)",
            openApiIMBootstrapCacheKey,
            "\(state.participants.count)",
            "\(state.messages.count)",
            state.messages.last?.id.uuidString ?? "none",
            "contacts:\(openApiConversationContextsByID.count)",
            "accounts:\(openApiAccountContextsByID.count)"
        ].joined(separator: "|")
    }

    var openApiIMBootstrapCacheKey: String {
        switch openApiIMBootstrapState {
        case .loading:
            return hasLoadedOpenApiIMSnapshot ? "loaded-refreshing" : "loading"
        case .loaded:
            return "loaded"
        case .empty:
            return "empty"
        case .failed(let message):
            return "failed:\(message)"
        }
    }

    func baseVisibleLeftItems(participantsByID: [UUID: ChatParticipant]? = nil) -> [SidebarItem] {
        let cacheKey = baseVisibleLeftItemsCurrentCacheKey()
        if cacheKey == baseVisibleLeftItemsCacheKey {
            return baseVisibleLeftItemsCache
        }
        let participantsByID = participantsByID ?? participantLookupByID()
        let latestMessages = latestSidebarMessagesByParticipantID()
        let messageBackedItems = state.leftItems
            .filter { item in
                guard let participantID = item.participantID else { return true }
                return !hiddenParticipantIDs.contains(participantID)
            }
            .map { item in
                guard let participantID = item.participantID,
                      let participant = participantsByID[participantID]
                else { return item }
                let isGroup = isGroupConversation(participant) || isGroupParticipant(participant)
                return SidebarItem(
                    id: item.id,
                    kind: item.kind,
                    title: displayName(for: participant),
                    symbolName: isGroup ? "person.3.fill" : item.symbolName,
                    tintColor: item.tintColor,
                    participantID: item.participantID,
                    targetMessageID: item.targetMessageID,
                    isActive: item.isActive,
                    toolMessageType: item.toolMessageType,
                    customTool: item.customTool,
                    avatarURL: sidebarAvatarURL(for: participantID, participantsByID: participantsByID) ?? item.avatarURL,
                    compositeAvatarTitles: isGroup ? sidebarCompositeAvatarTitles(for: participant) : []
                )
            }
        var seenParticipantIDs = Set(messageBackedItems.compactMap(\.participantID))
        let contextItems = state.participants.compactMap { participant -> SidebarItem? in
            guard !participant.isCurrentUser,
                  !hiddenParticipantIDs.contains(participant.id),
                  openApiConversationContextsByID[participant.id] != nil,
                  seenParticipantIDs.insert(participant.id).inserted
            else { return nil }
            let latestMessage = latestMessages[participant.id]
            return SidebarItem(
                id: participant.id,
                kind: .session,
                title: displayName(for: participant),
                symbolName: isGroupConversation(participant) ? "person.3.fill" : "person.fill",
                tintColor: participant.tintColor,
                participantID: participant.id,
                targetMessageID: latestMessage?.id ?? participant.id,
                isActive: state.selectedFriendID == participant.id,
                avatarURL: sidebarAvatarURL(for: participant.id, participantsByID: participantsByID) ?? participant.avatarURL,
                compositeAvatarTitles: isGroupConversation(participant)
                    ? sidebarCompositeAvatarTitles(for: participant)
                    : []
            )
        }
        let items = messageBackedItems + contextItems
        baseVisibleLeftItemsCacheKey = cacheKey
        baseVisibleLeftItemsCache = items
        return items
    }

    func baseVisibleLeftItemsCurrentCacheKey() -> String {
        [
            "v:\(visibleDataRevision)",
            state.selectedAccountID.uuidString,
            openApiIMBootstrapCacheKey,
            "left:\(state.leftItems.count)",
            "participants:\(state.participants.count)",
            "messages:\(state.messages.count)",
            "contacts:\(openApiConversationContextsByID.count)"
        ].joined(separator: "|")
    }

    func leftSidebarItem(_ item: SidebarItem, active: Bool) -> SidebarItem {
        SidebarItem(
            id: item.id,
            kind: item.kind,
            title: item.title,
            symbolName: item.symbolName,
            tintColor: item.tintColor,
            participantID: item.participantID,
            targetMessageID: item.targetMessageID,
            isActive: active,
            toolMessageType: item.toolMessageType,
            customTool: item.customTool,
            avatarURL: item.avatarURL,
            isOnline: item.isOnline,
            compositeAvatarTitles: item.compositeAvatarTitles
        )
    }

    func sidebarCompositeAvatarTitles(for group: ChatParticipant) -> [String] {
        Array(groupMembers(for: group).map { displayName(for: $0) }.prefix(4))
    }

    func sidebarAvatarURL(for participantID: UUID?) -> URL? {
        sidebarAvatarURL(for: participantID, participantsByID: nil)
    }

    func sidebarAvatarURL(for participantID: UUID?, participantsByID: [UUID: ChatParticipant]?) -> URL? {
        guard let participantID else { return nil }
        if let context = openApiConversationContextsByID[participantID],
           let avatar = context.avatar {
            return avatar
        }
        if let account = openApiAccountContextsByID[participantID],
           let avatar = account.avatar {
            return avatar
        }
        if let participantsByID {
            return participantsByID[participantID]?.avatarURL
        }
        return state.participants.first { $0.id == participantID }?.avatarURL
    }

    func leftItemsForAccount(
        _ accountID: UUID,
        from allItems: [SidebarItem]? = nil,
        keepSelectedConversation: Bool,
        participantsByID providedParticipantsByID: [UUID: ChatParticipant]? = nil
    ) -> [SidebarItem] {
        let participantsByID = providedParticipantsByID ?? participantLookupByID()
        let allItems = allItems ?? baseVisibleLeftItems(participantsByID: participantsByID)
        let filteredItems = allItems.filter { item in
            if isAISidebarItem(item, participantsByID: participantsByID) {
                return true
            }
            guard let participantID = item.participantID,
                  let participant = participantsByID[participantID]
            else { return false }
            if keepSelectedConversation, item.participantID == state.selectedFriendID {
                return isConversation(participant, relatedToAccountID: accountID)
            }
            return isConversation(participant, relatedToAccountID: accountID)
        }
        return leftItemsWithPinnedAI(
            applyLeftSidebarDisplayMode(to: filteredItems, participantsByID: participantsByID),
            participantsByID: participantsByID
        )
    }

    func applyLeftSidebarDisplayMode(
        to items: [SidebarItem],
        participantsByID: [UUID: ChatParticipant]? = nil
    ) -> [SidebarItem] {
        guard leftSidebarDisplayMode != .friendsAndGroups else { return items }
        let participantsByID = participantsByID ?? participantLookupByID()
        return items.filter { item in
            if isAISidebarItem(item, participantsByID: participantsByID) {
                return true
            }
            guard let participantID = item.participantID,
                  let participant = participantsByID[participantID]
            else { return false }
            let isGroup = isGroupConversation(participant) || isGroupParticipant(participant)
            switch leftSidebarDisplayMode {
            case .friendsAndGroups:
                return true
            case .friends:
                return !isGroup
            case .groups:
                return isGroup
            }
        }
    }

    func leftItemsWithPinnedAI(_ items: [SidebarItem], participantsByID: [UUID: ChatParticipant]? = nil) -> [SidebarItem] {
        let participantsByID = participantsByID ?? participantLookupByID()
        let aiItems = items.filter { isAISidebarItem($0, participantsByID: participantsByID) }
            .sorted { lhs, rhs in
                aiSidebarRank(lhs, participantsByID: participantsByID) < aiSidebarRank(rhs, participantsByID: participantsByID)
            }
        let aiIDs = Set(aiItems.map(\.id))
        return aiItems + items.filter { !aiIDs.contains($0.id) }
    }

    func isAISidebarItem(_ item: SidebarItem, participantsByID: [UUID: ChatParticipant]? = nil) -> Bool {
        guard let participantID = item.participantID,
              let participant = participantsByID?[participantID] ?? state.participants.first(where: { $0.id == participantID })
        else { return false }
        return participant.isAIAccount
    }

    func aiSidebarRank(_ item: SidebarItem, participantsByID: [UUID: ChatParticipant]? = nil) -> Int {
        guard let participantID = item.participantID,
              let name = (participantsByID?[participantID] ?? state.participants.first(where: { $0.id == participantID }))?.displayName
        else { return 99 }
        switch name {
        case "Claude AI": return 0
        case "Codex": return 1
        default: return 2
        }
    }

    func visibleRightAccountItems() -> [SidebarItem] {
        let cacheKey = visibleRightAccountItemsCurrentCacheKey()
        if cacheKey == visibleRightAccountItemsCacheKey {
            return visibleRightAccountItemsCache
        }
        let items = makeVisibleRightAccountItems()
        visibleRightAccountItemsCacheKey = cacheKey
        visibleRightAccountItemsCache = items
        return items
    }

    func makeVisibleRightAccountItems() -> [SidebarItem] {
        if let bootstrapItems = openApiBootstrapAccountItems() {
            return bootstrapItems
        }

        let items = baseRightAccountItems()
        let activeAccountID = isHomeTimeline ? unansweredAccountFilterID : state.selectedAccountID
        return items.map { rightAccountItem($0, active: $0.participantID == activeAccountID) }
    }

    func visibleRightAccountItemsCurrentCacheKey() -> String {
        [
            "v:\(visibleDataRevision)",
            state.selectedFriendID?.uuidString ?? "home",
            state.selectedAccountID.uuidString,
            unansweredAccountFilterID?.uuidString ?? "all-accounts",
            openApiIMBootstrapCacheKey,
            "\(state.participants.count)",
            "\(state.messages.count)",
            state.messages.last?.id.uuidString ?? "none",
            state.messages.last.map { "\($0.sentAt.timeIntervalSinceReferenceDate)" } ?? "none"
        ].joined(separator: "|")
    }

    func baseRightAccountItems() -> [SidebarItem] {
        let accounts = state.currentUsers.isEmpty ? [state.currentUser] : state.currentUsers
        let visibleMessages = state.visibleMessages
        let fallbackMessage = visibleMessages.last ?? state.messages.last
        return accounts.map { account in
            let targetMessage = visibleMessages.last { $0.sender.id == account.id }
                ?? fallbackMessage
            return SidebarItem(
                id: account.id,
                kind: .account,
                title: state.contactCards[account.id]?.displayName ?? account.displayName,
                symbolName: "person.crop.circle.fill",
                tintColor: account.tintColor,
                participantID: account.id,
                targetMessageID: targetMessage?.id ?? account.id,
                isActive: account.id == (isHomeTimeline ? unansweredAccountFilterID : state.selectedAccountID),
                avatarURL: account.avatarURL,
                isOnline: openApiAccountContextsByID[account.id]?.isOnline
            )
        }
    }

    func rightAccountItem(_ item: SidebarItem, active: Bool) -> SidebarItem {
        SidebarItem(
            id: item.id,
            kind: item.kind,
            title: item.title,
            symbolName: item.symbolName,
            tintColor: item.tintColor,
            participantID: item.participantID,
            targetMessageID: item.targetMessageID,
            isActive: active,
            toolMessageType: item.toolMessageType,
            customTool: item.customTool,
            avatarURL: item.avatarURL,
            isOnline: item.isOnline,
            compositeAvatarTitles: item.compositeAvatarTitles
        )
    }

    func openApiBootstrapAccountItems() -> [SidebarItem]? {
        switch openApiIMBootstrapState {
        case .loading where !hasLoadedOpenApiIMSnapshot:
            return (0..<Int(max(minimumRightAccountItemsVisible, 3))).map { index in
                SidebarItem(
                    id: UUID(uuidString: String(format: "00000000-0000-0000-0001-%012d", index + 1)) ?? UUID(),
                    kind: .account,
                    title: index == 0 ? "拉取中" : "",
                    symbolName: "person.crop.circle",
                    tintColor: UIColor.white.withAlphaComponent(0.18),
                    participantID: nil,
                    targetMessageID: UUID(),
                    isActive: false
                )
            }
        case .failed:
            return [
                SidebarItem(
                    id: UUID(uuidString: "00000000-0000-0000-0002-000000000001") ?? UUID(),
                    kind: .account,
                    title: "重试",
                    symbolName: "arrow.clockwise",
                    tintColor: UIColor(red: 0.92, green: 0.56, blue: 0.18, alpha: 1),
                    participantID: nil,
                    targetMessageID: UUID(),
                    isActive: false
                )
            ]
        case .empty:
            return [
                SidebarItem(
                    id: UUID(uuidString: "00000000-0000-0000-0003-000000000001") ?? UUID(),
                    kind: .account,
                    title: "暂无",
                    symbolName: "person.crop.circle.badge.exclamationmark",
                    tintColor: UIColor(red: 0.44, green: 0.50, blue: 0.52, alpha: 1),
                    participantID: nil,
                    targetMessageID: UUID(),
                    isActive: false
                )
            ]
        default:
            return nil
        }
    }

    var isShowingInitialAccountLoading: Bool {
        if case .loading = openApiIMBootstrapState, !hasLoadedOpenApiIMSnapshot {
            return true
        }
        return false
    }

    func handleRightAccountBootstrapSelectionIfNeeded() -> Bool {
        switch openApiIMBootstrapState {
        case .loading where !hasLoadedOpenApiIMSnapshot:
            showNotice("正在拉取真实帐号和好友")
            return true
        case .failed, .empty:
            syncOpenApiIMData(force: true, showsNotice: true)
            return true
        default:
            return false
        }
    }

    func currentAccountIDsInGroup(_ group: ChatParticipant) -> Set<UUID> {
        let currentAccountIDs = Set(state.currentUsers.map(\.id))
        if let relatedAccountIDs = openApiRelatedAccountIDsByConversationID[group.id],
           !relatedAccountIDs.isEmpty {
            return relatedAccountIDs.intersection(currentAccountIDs)
        }
        if let explicitIDs = explicitGroupMemberIDsByGroupID[group.id], !explicitIDs.isEmpty {
            let removedIDs = removedGroupMemberIDsByGroupID[group.id] ?? []
            let explicitAccountIDs = Set(explicitIDs)
                .intersection(currentAccountIDs)
                .subtracting(removedIDs)
            if !explicitAccountIDs.isEmpty {
                return explicitAccountIDs
            }
        }
        if let context = openApiConversationContextsByID[group.id],
           context.kind == .chatroom {
            let ownerAccountIDs = Set(
                openApiAccountContextsByID.values
                    .filter { $0.wxid == context.ownerWxid }
                    .map(\.participantID)
            )
            let visibleOwnerIDs = ownerAccountIDs.intersection(currentAccountIDs)
            if !visibleOwnerIDs.isEmpty {
                return visibleOwnerIDs
            }
        }

        let messageAccountIDs = Set(
            state.messages
                .filter { $0.conversationID == group.id && $0.isGroupConversation && $0.sender.isCurrentUser }
                .map(\.sender.id)
        ).intersection(currentAccountIDs)
        if !messageAccountIDs.isEmpty {
            return messageAccountIDs
        }

        return Set(state.currentUsers.prefix(1).map(\.id))
    }

    func presentGroupInfoPage(for group: ChatParticipant) {
        let settings = groupDisplaySettings(for: group)
        let controller = GroupInfoViewController(
            group: group,
            remark: settings.remark,
            myGroupName: settings.myGroupName,
            showsAvatar: settings.showsAvatar,
            showsName: settings.showsName,
            members: groupMembers(for: group),
            friendMemberIDs: friendMemberIDs(in: group)
        )
        controller.onSaveSettings = { [weak self] updated in
            guard let self else { return }
            self.groupDisplaySettingsByGroupID[group.id] = GroupDisplaySettings(
                showsAvatar: updated.showsAvatar,
                showsName: updated.showsName,
                remark: updated.remark,
                myGroupName: updated.myGroupName
            )
            self.persistGroupDisplaySettings()
            let currentGroup = self.state.participants.first(where: { $0.id == group.id }) ?? group
            self.applyMyGroupNameToLocalMessages(in: currentGroup)
            self.persistMessages()
            self.cancelLeftScrollPreview()
            self.invalidateVisibleDataCaches()
            self.updateHeaderForSelection()
            self.leftCollectionView.reloadData()
            self.updateLeftCollectionBounceInsets()
            self.rightToolCollectionView.reloadData()
            self.messageCollectionView.reloadData()
            self.updateConnections()
            self.showNotice("群聊信息已保存")
        }
        controller.onRenameGroup = { [weak self, weak controller] name, completion in
            guard let self else {
                completion(false)
                return
            }
            self.submitOpenApiChatroomRenameIfAvailable(group, name: name) { [weak self] success in
                guard let self else {
                    completion(false)
                    return
                }
                guard success else {
                    completion(false)
                    return
                }
                self.commitSuccessfulGroupRename(groupID: group.id, newName: name)
                self.invalidateVisibleDataCaches()
                self.leftCollectionView.reloadData()
                self.updateHeaderForSelection()
                controller?.setMembersStatus("群聊名字已同步到微信")
                completion(true)
            }
        }
        controller.onRemoveMember = { [weak self, weak controller] member, completion in
            guard let self else {
                completion(false)
                return
            }
            self.submitOpenApiGroupMemberKickIfAvailable(member, from: group, controller: controller, completion: completion)
        }
        controller.onSendGroupNotice = { [weak self] notice in
            guard let self else { return }
            self.insertGroupNotice(notice, in: group)
            self.submitOpenApiChatroomNoticeIfAvailable(group, notice: notice)
        }
        controller.onRefreshGroup = { [weak self, weak controller] in
            guard let self, let controller else { return }
            self.submitOpenApiChatroomRefreshIfAvailable(group, controller: controller)
        }
        controller.onInviteMembers = { [weak self, weak controller] in
            guard let self, let controller else { return }
            self.presentOpenApiGroupInvitePicker(for: group, controller: controller)
        }
        controller.onPullQRCode = { [weak self] in
            self?.submitOpenApiChatroomQRCodeIfAvailable(group)
        }
        controller.onExitGroup = { [weak self] in
            self?.confirmOpenApiChatroomExitIfAvailable(group)
        }
        controller.onAddFriendFromGroup = { [weak self, weak controller] member in
            guard let self else { return }
            self.submitOpenApiAddFriendFromGroupIfAvailable(member, group: group) { success in
                guard success else { return }
                controller?.markMemberAsFriend(member)
            }
        }
        navigationItem.backBarButtonItem = UIBarButtonItem(title: "返回", style: .plain, target: nil, action: nil)
        navigationController?.setNavigationBarHidden(false, animated: false)
        navigationController?.pushViewController(controller, animated: true)
        refreshOpenApiGroupMembersIfAvailable(for: group, controller: controller)
    }

    func refreshOpenApiGroupMembersIfAvailable(
        for group: ChatParticipant,
        controller: GroupInfoViewController
    ) {
        guard let context = openApiGroupContext(for: group)
        else { return }
        let hydrationKey = "\(context.account.wxid)|\(context.group.wxid)"
        if openApiHydratedGroupMemberKeys.contains(hydrationKey) {
            controller.updateMembers(
                groupMembers(for: group),
                friendMemberIDs: friendMemberIDs(in: group),
                status: nil
            )
            return
        }
        guard openApiGroupMemberHydrationInFlightKeys.insert(hydrationKey).inserted else { return }
        controller.setMembersStatus("正在同步真实群成员...")
        Task { @MainActor in
            defer { self.openApiGroupMemberHydrationInFlightKeys.remove(hydrationKey) }
            do {
                let memberContexts = try await OpenApiIMSynchronizer.loadChatroomMembers(
                    for: context.account,
                    chatRoomID: context.group.wxid,
                    limit: 300
                )
                guard controller.navigationController != nil else { return }
                let members = applyOpenApiGroupMembers(memberContexts, group: group, owner: context.account)
                controller.updateMembers(
                    members,
                    friendMemberIDs: friendMemberIDs(in: group),
                    status: memberContexts.isEmpty ? "接口未返回群成员，已保留本地成员。" : nil
                )
                showNotice("已同步\(members.count)个群成员")
            } catch {
                guard controller.navigationController != nil else { return }
                controller.setMembersStatus("群成员同步失败：\(error.localizedDescription)")
            }
        }
    }

    @discardableResult
    func applyOpenApiGroupMembers(
        _ memberContexts: [OpenApiChatRoomMemberContext],
        group: ChatParticipant,
        owner: OpenApiWeChatAccountContext
    ) -> [ChatParticipant] {
        guard !memberContexts.isEmpty else {
            return groupMembers(for: group)
        }

        let currentAccountsByWxid = Dictionary(
            openApiAccountContextsByID.values.map { ($0.wxid.lowercased(), $0) },
            uniquingKeysWith: { current, _ in current }
        )
        let contactsByWxid = Dictionary(
            grouping: openApiConversationContextsByID.values.filter {
                $0.kind == .contact && $0.ownerWxid.caseInsensitiveCompare(owner.wxid) == .orderedSame
            },
            by: { $0.wxid.lowercased() }
        ).compactMapValues(\.first)
        let contactsByFriendNo = Dictionary(
            grouping: openApiConversationContextsByID.values.filter {
                $0.kind == .contact
                    && $0.ownerWxid.caseInsensitiveCompare(owner.wxid) == .orderedSame
                    && !OpenApiDisplay.cleaned($0.friendNo).isEmpty
            },
            by: { $0.friendNo.lowercased() }
        ).compactMapValues(\.first)
        var participantsByID = participantLookupByID()
        var resolvedMemberIDs: [UUID] = []
        var resolvedParticipantsByWxid: [String: ChatParticipant] = [:]
        var resolvedParticipantsByHistoricalSenderID: [UUID: ChatParticipant] = [:]

        func isPresentableName(_ value: String) -> Bool {
            let cleaned = OpenApiDisplay.cleaned(value)
            guard !cleaned.isEmpty,
                  cleaned != "群成员",
                  cleaned.lowercased() != "unknown",
                  !cleaned.lowercased().hasPrefix("wxid_"),
                  !cleaned.hasSuffix("@chatroom")
            else { return false }
            return true
        }

        func isIdentifierName(_ value: String, memberWxid: String) -> Bool {
            let cleaned = OpenApiDisplay.cleaned(value).lowercased()
            return cleaned == memberWxid.lowercased()
                || cleaned.hasPrefix("wxid_")
                || cleaned.hasSuffix("@chatroom")
        }

        func preferredMemberName(
            _ memberContext: OpenApiChatRoomMemberContext,
            contact: OpenApiConversationContext?
        ) -> String {
            // Keep the group display name aligned with the contact header:
            // remark first, then the public WeChat number, then nickname.
            let candidates = [
                memberContext.remarks,
                contact?.remark ?? "",
                memberContext.friendNo,
                contact?.friendNo ?? "",
                memberContext.nickname,
                contact?.displayName ?? "",
                memberContext.displayName
            ]
            return candidates
                .map(OpenApiDisplay.cleaned)
                .first {
                    isPresentableName($0)
                        && !isIdentifierName($0, memberWxid: memberContext.memberWxid)
                } ?? ""
        }

        func appendUniqueMemberID(_ id: UUID) {
            guard !resolvedMemberIDs.contains(id) else { return }
            resolvedMemberIDs.append(id)
        }

        appendUniqueMemberID(owner.participantID)
        for memberContext in memberContexts {
            let normalizedMemberWxid = memberContext.memberWxid.lowercased()
            let contact = contactsByWxid[normalizedMemberWxid]
                ?? contactsByFriendNo[memberContext.friendNo.lowercased()]
            let candidateName = preferredMemberName(memberContext, contact: contact)
            guard !candidateName.isEmpty else { continue }
            var participant: ChatParticipant
            if let account = currentAccountsByWxid[normalizedMemberWxid],
               let existing = participantsByID[account.participantID] {
                participant = existing
            } else if let contact,
                      let existing = participantsByID[contact.participantID] {
                participant = existing
            } else {
                let participantID = OpenApiStableID.uuid(
                    namespace: "openapi-group-member",
                    key: "\(memberContext.chatRoomID)|\(memberContext.memberWxid)"
                )
                participant = participantsByID[participantID] ?? ChatParticipant(
                    id: participantID,
                    displayName: candidateName,
                    tintColor: memberContext.tintColor,
                    initials: OpenApiDisplay.initials(from: candidateName, fallback: "员"),
                    isCurrentUser: false,
                    avatarURL: memberContext.avatar,
                    kind: .groupMember
                )
                if participantsByID[participantID] == nil {
                    state.participants.append(participant)
                    participantsByID[participantID] = participant
                }
            }
            let isKnownAccountOrContact = openApiAccountContextsByID[participant.id] != nil
                || openApiConversationContextsByID[participant.id] != nil
            let hasUsableAPIName = isPresentableName(candidateName)
            let currentNameIsPlaceholder = !isPresentableName(participant.displayName)
                || isIdentifierName(participant.displayName, memberWxid: memberContext.memberWxid)
            let hasLocalRemark = !OpenApiDisplay.cleaned(participantRemarksByID[participant.id]).isEmpty
            let shouldUpdateDisplayName = hasUsableAPIName
                && participant.displayName != candidateName
                && !participant.isCurrentUser
                && !hasLocalRemark
                && (!isKnownAccountOrContact || currentNameIsPlaceholder || participant.kind == .contact)
            let shouldUpdateAvatar = memberContext.avatar.map { participant.avatarURL != $0 } ?? false
            if shouldUpdateDisplayName || shouldUpdateAvatar {
                let updated = ChatParticipant(
                    id: participant.id,
                    displayName: shouldUpdateDisplayName ? candidateName : participant.displayName,
                    tintColor: participant.tintColor,
                    initials: shouldUpdateDisplayName
                        ? OpenApiDisplay.initials(from: candidateName, fallback: "员")
                        : participant.initials,
                    isCurrentUser: participant.isCurrentUser,
                    avatarURL: memberContext.avatar ?? participant.avatarURL,
                    kind: participant.kind
                )
                participant = updated
                participantsByID[updated.id] = updated
                if let index = state.participants.firstIndex(where: { $0.id == updated.id }) {
                    state.participants[index] = updated
                }
                state.messages = state.messages.map {
                    $0.sender.id == updated.id ? $0.replacingSender(updated) : $0
                }
            }

            appendUniqueMemberID(participant.id)
            resolvedParticipantsByWxid[normalizedMemberWxid] = participant
            let historicalSenderID = OpenApiStableID.uuid(
                namespace: "openapi-group-member",
                key: "\(memberContext.chatRoomID)|\(memberContext.memberWxid)"
            )
            resolvedParticipantsByHistoricalSenderID[historicalSenderID] = participant
            let normalizedHistoricalSenderID = OpenApiStableID.uuid(
                namespace: "openapi-group-member",
                key: "\(memberContext.chatRoomID.lowercased())|\(normalizedMemberWxid)"
            )
            resolvedParticipantsByHistoricalSenderID[normalizedHistoricalSenderID] = participant
            if state.contactCards[participant.id] == nil || !memberContext.memberWxid.isEmpty {
                state.contactCards[participant.id] = ContactCardProfile(
                    accountID: participant.id,
                    displayName: participant.displayName,
                    role: memberContext.roleText,
                    company: "只发 SCRM 群成员",
                    wechatID: memberContext.memberWxid,
                    phone: "",
                    bio: [
                        "群聊ID：\(memberContext.chatRoomID)",
                        "成员wxid：\(memberContext.memberWxid)",
                        memberContext.remarks.isEmpty ? "" : "备注：\(memberContext.remarks)"
                    ].filter { !$0.isEmpty }.joined(separator: "\n"),
                    styleIndex: safeHashIndex(memberContext.memberWxid, modulo: 4)
                )
            }
        }

        removedGroupMemberIDsByGroupID[group.id] = []
        explicitGroupMemberIDsByGroupID[group.id] = resolvedMemberIDs
        state.messages = state.messages.map { message in
            guard message.conversationID == group.id,
                  message.isGroupConversation,
                  !message.isOutgoing
            else { return message }
            if let resolvedSender = resolvedParticipantsByHistoricalSenderID[message.sender.id] {
                return message.replacingSender(resolvedSender)
            }
            let parsedWxid = OpenApiChatContentParser.parse(message.body, chatType: 2)
                .senderWxid.lowercased()
            guard !parsedWxid.isEmpty,
                  let resolvedSender = resolvedParticipantsByWxid[parsedWxid],
                  message.sender != resolvedSender
            else { return message }
            return message.replacingSender(resolvedSender)
        }
        let nonCurrentMembers = Dictionary(
            resolvedParticipantsByWxid.values
                .filter { !$0.isCurrentUser }
                .map { ($0.id, $0) },
            uniquingKeysWith: { current, _ in current }
        ).values
        if nonCurrentMembers.count == 1, let onlyOtherMember = nonCurrentMembers.first {
            state.messages = state.messages.map { message in
                guard message.conversationID == group.id,
                      message.isGroupConversation,
                      !message.isOutgoing,
                      message.sender.displayName != "群通知"
                else { return message }
                return message.replacingSender(onlyOtherMember)
            }
        }
        if let groupContext = openApiConversationContextsByAccountID[group.id]?[owner.participantID]
            ?? openApiConversationContextsByID[group.id]
        {
            openApiHydratedGroupMemberKeys.insert("\(owner.wxid)|\(groupContext.wxid)")
        }
        invalidateVisibleDataCaches()
        cancelLeftScrollPreview()
        leftCollectionView.reloadData()
        messageCollectionView.reloadData()
        updateLeftCollectionBounceInsets()
        rightAccountCollectionView.reloadData()
        updateConnections()
        persistMessages()
        return groupMembers(for: group)
    }

    func submitOpenApiGroupMemberKickIfAvailable(
        _ member: ChatParticipant,
        from group: ChatParticipant,
        controller: GroupInfoViewController?,
        completion: @escaping (Bool) -> Void
    ) {
        guard let context = openApiGroupContext(for: group)
        else {
            showNotice("当前群聊没有匹配的 OpenAPI 群 ID")
            completion(false)
            return
        }
        let memberWxid = memberWxid(for: member).trimmingCharacters(in: .whitespacesAndNewlines)
        guard !memberWxid.isEmpty, !memberWxid.hasSuffix("@chatroom") else {
            showNotice("该群成员缺少可移出的 wxid")
            completion(false)
            return
        }
        controller?.setMembersStatus("正在移出 \(member.displayName)...")
        let account = OpenApiSocialAccount(
            deviceUUID: context.account.clientUuid,
            weChatID: context.account.wxid
        )
        Task { @MainActor in
            do {
                _ = try await OpenApiSocialManagementService().removeMembers(
                    account: account,
                    chatRoomID: context.group.wxid,
                    memberWxids: [memberWxid]
                )
                if var explicitIDs = explicitGroupMemberIDsByGroupID[group.id] {
                    explicitIDs.removeAll { $0 == member.id }
                    explicitGroupMemberIDsByGroupID[group.id] = explicitIDs
                }
                invalidateVisibleDataCaches()
                rightToolCollectionView.reloadData()
                controller?.updateMembers(
                    groupMembers(for: group),
                    friendMemberIDs: friendMemberIDs(in: group),
                    status: "已移出 \(member.displayName)，正在刷新群资料..."
                )
                insertSystemMessage("\(member.displayName)已被移出群聊", conversationID: group.id)
                showNotice("已移出 \(member.displayName)，正在刷新群资料")
                completion(true)
                if let controller {
                    submitOpenApiChatroomRefreshIfAvailable(group, controller: controller)
                }
            } catch {
                controller?.setMembersStatus("移出失败：\(error.localizedDescription)")
                showNotice("移出群成员接口失败：\(error.localizedDescription)")
                completion(false)
            }
        }
    }

    func openApiGroupContext(for group: ChatParticipant) -> (account: OpenApiWeChatAccountContext, group: OpenApiConversationContext)? {
        guard let groupContext = openApiConversationContextsByID[group.id],
              let normalizedGroupContext = normalizedOpenApiConversationContext(groupContext, participantID: group.id),
              normalizedGroupContext.kind == .chatroom,
              let account = openApiAccountContextsByID.values.first(where: { $0.wxid == normalizedGroupContext.ownerWxid }),
              !account.wxid.isEmpty,
              OpenApiDisplay.validChatRoomID(normalizedGroupContext.wxid) != nil
        else { return nil }
        return (account, normalizedGroupContext)
    }

    func memberWxid(for member: ChatParticipant) -> String {
        if let account = openApiAccountContextsByID[member.id], !account.wxid.isEmpty {
            return account.wxid
        }
        if let conversation = openApiConversationContextsByID[member.id], !conversation.wxid.isEmpty {
            return conversation.wxid
        }
        return state.contactCards[member.id]?.wechatID.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
    }

    func friendMemberIDs(in group: ChatParticipant) -> Set<UUID> {
        guard let context = openApiGroupContext(for: group) else {
            return Set(groupMembers(for: group).filter { $0.isCurrentUser }.map(\.id))
        }
        let friendWxids = Set(openApiConversationContextsByID.values
            .filter { $0.ownerWxid == context.account.wxid && $0.kind == .contact }
            .map { $0.wxid.lowercased() })
        let accountWxids = Set(openApiAccountContextsByID.values.map { $0.wxid.lowercased() })
        return Set(groupMembers(for: group).compactMap { member in
            let wxid = memberWxid(for: member).lowercased()
            if member.isCurrentUser || accountWxids.contains(wxid) || friendWxids.contains(wxid) {
                return member.id
            }
            if let memberContext = openApiConversationContextsByID[member.id],
               memberContext.kind == .contact,
               memberContext.ownerWxid == context.account.wxid {
                return member.id
            }
            return nil
        })
    }

    func submitOpenApiChatroomRenameIfAvailable(_ group: ChatParticipant, name: String, completion: @escaping (Bool) -> Void) {
        guard let context = openApiGroupContext(for: group) else {
            showNotice("当前群聊没有匹配的 OpenAPI 群 ID")
            completion(false)
            return
        }
        let cleanedName = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanedName.isEmpty else { completion(false); return }
        let account = OpenApiSocialAccount(
            deviceUUID: context.account.clientUuid,
            weChatID: context.account.wxid
        )
        Task { @MainActor in
            do {
                _ = try await OpenApiSocialManagementService().renameChatRoom(
                    account: account,
                    chatRoomID: context.group.wxid,
                    name: cleanedName
                )
                showNotice("群聊名字已修改")
                completion(true)
            } catch {
                showNotice("修改群聊名字失败：\(error.localizedDescription)")
                completion(false)
            }
        }
    }

    func submitOpenApiChatroomNoticeIfAvailable(_ group: ChatParticipant, notice: String) {
        guard let context = openApiGroupContext(for: group) else { return }
        let cleanedNotice = notice.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanedNotice.isEmpty else { return }
        let account = OpenApiSocialAccount(
            deviceUUID: context.account.clientUuid,
            weChatID: context.account.wxid
        )
        showNotice("设置群公告已提交")
        Task { @MainActor in
            do {
                let taskID = try await OpenApiSocialManagementService().updateChatRoomNotice(
                    account: account,
                    chatRoomID: context.group.wxid,
                    notice: cleanedNotice
                )
                showNotice("设置群公告已完成\(taskID.isEmpty ? "" : "：\(taskID)")")
            } catch {
                showNotice("设置群公告失败：\(error.localizedDescription)")
            }
        }
    }

    func submitOpenApiChatroomRefreshIfAvailable(_ group: ChatParticipant, controller: GroupInfoViewController) {
        guard let context = openApiGroupContext(for: group) else {
            showNotice("当前群聊没有匹配的 OpenAPI 群 ID")
            return
        }
        controller.setMembersStatus("正在刷新群资料和成员...")
        let account = OpenApiSocialAccount(
            deviceUUID: context.account.clientUuid,
            weChatID: context.account.wxid
        )
        Task { @MainActor [weak self, weak controller] in
            guard let self, let controller else { return }
            do {
                let taskID = try await OpenApiSocialManagementService().refreshChatRoom(
                    account: account,
                    chatRoomID: context.group.wxid
                )
                self.showNotice("刷新群资料已完成\(taskID.isEmpty ? "" : "：\(taskID)")")
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.85) {
                    guard controller.navigationController != nil else { return }
                    self.refreshOpenApiGroupMembersIfAvailable(for: group, controller: controller)
                }
            } catch {
                controller.setMembersStatus("刷新失败：\(error.localizedDescription)")
                self.showNotice("刷新群资料失败：\(error.localizedDescription)")
            }
        }
    }

    func submitOpenApiChatroomQRCodeIfAvailable(_ group: ChatParticipant) {
        guard let context = openApiGroupContext(for: group) else {
            showNotice("当前群聊没有匹配的 OpenAPI 群 ID")
            return
        }
        showNotice("正在拉取群二维码")
        let account = OpenApiSocialAccount(
            deviceUUID: context.account.clientUuid,
            weChatID: context.account.wxid
        )
        Task { @MainActor in
            do {
                let result = try await OpenApiSocialManagementService().fetchChatRoomQRCode(
                    account: account,
                    chatRoomID: context.group.wxid
                )
                let payload = try await awaitOpenApiTaskPayloadIfNeeded(result, action: "拉取群二维码")
                let qrPayload = openApiQRCodePayload(from: payload)
                guard qrPayload.hasDisplayableValue else {
                    showNotice("群二维码已返回，但没有可展示的二维码内容")
                    presentOpenApiRawQRCodeResult(group: group, payload: payload)
                    return
                }
                presentOpenApiGroupQRCode(group: group, payload: qrPayload)
            } catch {
                showNotice("拉取群二维码失败：\(error.localizedDescription)")
            }
        }
    }

    func awaitOpenApiTaskPayloadIfNeeded(_ result: OpenApiHTTPResult, action: String) async throws -> [String: Any] {
        guard let root = result.jsonObject as? [String: Any] else {
            throw OpenApiMediaSendError.taskResultUnknown(
                action,
                "响应不是 JSON；HTTP \(result.statusCode)：\(openApiDiagnosticBody(result.rawText))"
            )
        }
        guard let taskID = openApiTaskID(from: root) else {
            if let success = openApiBoolValue(root["success"]), !success {
                let message = openApiFirstString(in: root, keys: ["message", "error", "errorMessage", "detail", "resultCode", "code"])
                throw OpenApiMediaSendError.taskFailed(action, message.isEmpty ? "后端返回 success=false" : message)
            }
            return openApiTaskPayload(from: root)
        }
        var latestPayload: [String: Any] = [:]
        var latestStateText = ""
        for attempt in 0..<12 {
            if attempt > 0 {
                try await Task.sleep(nanoseconds: UInt64(650_000_000 + attempt * 250_000_000))
            }
            let taskResult = try await OpenApiHTTPClient.request("GET", path: "/openapi/v1/tasks/\(taskID)")
            latestPayload = openApiTaskPayload(from: taskResult.jsonObject)
            switch openApiTaskPollState(from: taskResult.jsonObject) {
            case .succeeded:
                return latestPayload
            case .failed(let message):
                throw OpenApiMediaSendError.taskFailed(action, message.isEmpty ? "任务失败，taskId=\(taskID)" : message)
            case .pending(let message):
                latestStateText = message
            }
        }
        throw OpenApiMediaSendError.taskResultUnknown(
            action,
            "任务未返回终态，taskId=\(taskID)\(latestStateText.isEmpty ? "" : "，最新状态：\(latestStateText)")"
        )
    }

    func openApiQRCodePayload(from object: Any?) -> OpenApiQRCodePayload {
        var candidate = OpenApiQRCodePayload()
        func rememberContent(_ value: Any?) {
            let text = openApiStringValue(value)
            guard !text.isEmpty, candidate.qrContent.isEmpty else { return }
            candidate.qrContent = text
        }
        func rememberURL(_ value: Any?, key: String) {
            let text = openApiStringValue(value)
            guard !text.isEmpty else { return }
            if candidate.qrURLText.isEmpty {
                candidate.qrURLText = text
            }
            if candidate.imageURL == nil,
               openApiQRCodeURLLooksLikeImage(text, sourceKey: key),
               let url = OpenApiDisplay.url(from: text) {
                candidate.imageURL = url
            }
        }
        func walk(_ value: Any?) {
            if let dictionary = value as? [String: Any] {
                for key in ["qrContent", "qrcodeContent", "qrCodeContent", "qr_code_content", "rawContent", "content"] {
                    rememberContent(dictionary[key])
                }
                for key in ["imageUrl", "imageURL", "image", "qrcodeImage", "qrCodeImage", "qrImageUrl", "qrImageURL", "qrcodeUrl", "qrCodeUrl", "qrCodeURL", "qrUrl", "qrURL", "url", "link"] {
                    rememberURL(dictionary[key], key: key)
                }
                for key in ["data", "result", "payload", "task", "taskResult", "qrcode", "qrCode", "item"] {
                    walk(dictionary[key])
                }
                if !candidate.hasDisplayableValue {
                    for nested in dictionary.values {
                        walk(nested)
                    }
                }
            } else if let array = value as? [Any] {
                for nested in array {
                    walk(nested)
                }
            } else if let text = value as? String {
                let cleaned = text.trimmingCharacters(in: .whitespacesAndNewlines)
                guard !cleaned.isEmpty else { return }
                if cleaned.hasPrefix("http") || cleaned.hasPrefix("weixin://") || cleaned.hasPrefix("wxp://") {
                    rememberURL(cleaned, key: "value")
                }
            }
        }
        walk(object)
        return candidate
    }

    func openApiQRCodeURLLooksLikeImage(_ text: String, sourceKey: String) -> Bool {
        guard let url = OpenApiDisplay.url(from: text) else { return false }
        let lowerKey = sourceKey.lowercased()
        if lowerKey.contains("image")
            || lowerKey.contains("qrcode")
            || lowerKey.contains("qr") {
            return true
        }
        let extensionText = url.pathExtension.lowercased()
        if ["png", "jpg", "jpeg", "webp", "gif", "bmp"].contains(extensionText) {
            return true
        }
        let path = url.path.lowercased()
        return path.contains("qrcode") || path.contains("qr-code") || path.contains("/qr/")
    }

    func presentOpenApiGroupQRCode(group: ChatParticipant, payload: OpenApiQRCodePayload) {
        let encodedPayload = payload.qrContent.isEmpty ? payload.qrURLText : payload.qrContent
        let copyPayload = payload.qrContent.isEmpty ? payload.qrURLText : payload.qrContent
        let subtitle = payload.imageURL == nil ? "群二维码内容已拉取成功" : "群二维码图片已拉取成功"
        let hint = payload.imageURL == nil
            ? "使用微信扫码入群；如扫码失败可复制二维码内容排查。"
            : "这里显示后端返回的二维码图片，直接用微信扫码入群。"
        let controller = TiltQRCodeViewController(
            titleText: group.displayName,
            subtitleText: subtitle,
            payload: encodedPayload,
            copyPayload: copyPayload,
            imageURL: payload.imageURL,
            hintText: hint
        )
        let navigationController = UINavigationController(rootViewController: controller)
        navigationController.modalPresentationStyle = .fullScreen
        present(navigationController, animated: true)
    }

    func presentOpenApiRawQRCodeResult(group: ChatParticipant, payload: [String: Any]) {
        let raw = (try? JSONSerialization.data(withJSONObject: payload, options: [.prettyPrinted]))
            .flatMap { String(data: $0, encoding: .utf8) } ?? "\(payload)"
        let alert = UIAlertController(title: "\(group.displayName) 群二维码结果", message: raw, preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: "复制", style: .default) { _ in
            UIPasteboard.general.string = raw
        })
        alert.addAction(UIAlertAction(title: "关闭", style: .cancel))
        present(alert, animated: true)
    }

    func confirmOpenApiChatroomExitIfAvailable(_ group: ChatParticipant) {
        guard let context = openApiGroupContext(for: group) else {
            showNotice("当前群聊没有匹配的 OpenAPI 群 ID")
            return
        }
        let alert = UIAlertController(
            title: "退出群聊",
            message: "确认让「\(context.account.nickname)」退出「\(group.displayName)」？",
            preferredStyle: .alert
        )
        alert.addAction(UIAlertAction(title: "取消", style: .cancel))
        alert.addAction(UIAlertAction(title: "退出", style: .destructive) { [weak self] _ in
            guard let self else { return }
            let account = OpenApiSocialAccount(
                deviceUUID: context.account.clientUuid,
                weChatID: context.account.wxid
            )
            self.showNotice("退出群聊已提交")
            Task { @MainActor in
                do {
                    let taskID = try await OpenApiSocialManagementService().exitChatRoom(
                        account: account,
                        chatRoomID: context.group.wxid
                    )
                    self.showNotice("退出群聊已完成\(taskID.isEmpty ? "" : "：\(taskID)")")
                    self.syncOpenApiIMData(force: true)
                } catch {
                    self.showNotice("退出群聊失败：\(error.localizedDescription)")
                }
            }
        })
        present(alert, animated: true)
    }

    func presentOpenApiGroupInvitePicker(for group: ChatParticipant, controller: GroupInfoViewController) {
        guard let context = openApiGroupContext(for: group) else {
            showNotice("当前群聊没有匹配的 OpenAPI 群 ID")
            return
        }
        let existingWxids = Set(groupMembers(for: group).map { memberWxid(for: $0).lowercased() }.filter { !$0.isEmpty })
        let cachedContacts = openApiConversationContextsByID.values
            .filter { $0.ownerWxid == context.account.wxid && $0.kind == .contact && !existingWxids.contains($0.wxid.lowercased()) }
            .sorted { $0.displayName.localizedStandardCompare($1.displayName) == .orderedAscending }
        let picker = OpenApiGroupInvitePickerViewController(
            groupName: group.displayName,
            accountName: context.account.nickname,
            contacts: cachedContacts
        )
        picker.onInvite = { [weak self, weak controller, weak picker] wxids in
            guard let self, let controller else { return }
            picker?.navigationController?.popViewController(animated: true)
            self.submitOpenApiGroupMemberInvite(wxids: wxids, group: group, controller: controller)
        }
        picker.onManualInput = { [weak self, weak controller, weak picker] in
            guard let self, let controller else { return }
            picker?.navigationController?.popViewController(animated: true)
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.18) {
                self.presentOpenApiGroupInviteInput(for: group, controller: controller)
            }
        }
        if let navigationController = controller.navigationController {
            navigationController.pushViewController(picker, animated: true)
        } else {
            let navigationController = UINavigationController(rootViewController: picker)
            navigationController.modalPresentationStyle = .formSheet
            controller.present(navigationController, animated: true)
        }

        Task { @MainActor in
            do {
                let loadedContacts = try await OpenApiIMSynchronizer.loadContactsForInvite(for: context.account, limit: 3000)
                let contacts = loadedContacts
                    .filter { $0.kind == .contact && !existingWxids.contains($0.wxid.lowercased()) }
                    .sorted { $0.displayName.localizedStandardCompare($1.displayName) == .orderedAscending }
                mergeOpenApiContactsIntoCurrentState(contacts)
                picker.updateContacts(contacts, status: "已加载完整好友 \(contacts.count) 个")
            } catch {
                picker.updateStatus("完整好友加载失败，当前显示缓存好友 \(cachedContacts.count) 个")
            }
        }
    }

    func mergeOpenApiContactsIntoCurrentState(_ contacts: [OpenApiConversationContext]) {
        guard !contacts.isEmpty else { return }
        var participantIndexByID = Dictionary(uniqueKeysWithValues: state.participants.enumerated().map { ($0.element.id, $0.offset) })
        for context in contacts {
            openApiConversationContextsByID[context.participantID] = context
            let participant = ChatParticipant(
                id: context.participantID,
                displayName: context.displayName,
                tintColor: context.tintColor,
                initials: context.initials,
                isCurrentUser: false,
                avatarURL: context.avatar,
                kind: context.kind == .chatroom ? .group : .contact
            )
            if let index = participantIndexByID[context.participantID] {
                state.participants[index] = participant
            } else {
                participantIndexByID[context.participantID] = state.participants.count
                state.participants.append(participant)
            }
            if state.contactCards[context.participantID] == nil {
                state.contactCards[context.participantID] = ContactCardProfile(
                    accountID: context.participantID,
                    displayName: context.displayName,
                    role: "OpenAPI 好友",
                    company: "只发 SCRM",
                    wechatID: context.wxid,
                    phone: context.phone,
                    bio: "wxid：\(context.wxid)",
                    styleIndex: safeHashIndex("\(context.ownerWxid)-\(context.wxid)", modulo: 4)
                )
            }
        }
        invalidateVisibleDataCaches()
        leftCollectionView.reloadData()
        updateLeftCollectionBounceInsets()
        updateSelectedLeftSidebarFloatingItem(animated: false)
    }

    func presentOpenApiGroupInviteInput(for group: ChatParticipant, controller: GroupInfoViewController) {
        let alert = UIAlertController(title: "邀请群成员", message: "输入要邀请进群的好友 wxid，可用逗号分隔多个。", preferredStyle: .alert)
        alert.addTextField { field in
            field.placeholder = "wxid_xxx, wxid_yyy"
            field.autocapitalizationType = .none
            field.autocorrectionType = .no
        }
        alert.addAction(UIAlertAction(title: "取消", style: .cancel))
        alert.addAction(UIAlertAction(title: "邀请", style: .default) { [weak self, weak alert, weak controller] _ in
            guard let self, let controller else { return }
            let wxids = (alert?.textFields?.first?.text ?? "")
                .components(separatedBy: CharacterSet(charactersIn: ",，;；\n "))
                .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
                .filter { !$0.isEmpty }
            self.submitOpenApiGroupMemberInvite(wxids: wxids, group: group, controller: controller)
        })
        present(alert, animated: true)
    }

    func submitOpenApiGroupMemberInvite(wxids: [String], group: ChatParticipant, controller: GroupInfoViewController) {
        guard let context = openApiGroupContext(for: group) else {
            showNotice("当前群聊没有匹配的 OpenAPI 群 ID")
            return
        }
        let cleanedWxids = wxids.map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }.filter { !$0.isEmpty }
        guard !cleanedWxids.isEmpty else {
            showNotice("请选择或输入要邀请的好友 wxid")
            return
        }
        let account = OpenApiSocialAccount(
            deviceUUID: context.account.clientUuid,
            weChatID: context.account.wxid
        )
        showNotice("邀请群成员已提交")
        Task { @MainActor in
            do {
                let taskID = try await OpenApiSocialManagementService().inviteMembers(
                    account: account,
                    chatRoomID: context.group.wxid,
                    memberWxids: cleanedWxids
                )
                showNotice("邀请群成员已完成\(taskID.isEmpty ? "" : "：\(taskID)")")
                self.submitOpenApiChatroomRefreshIfAvailable(group, controller: controller)
            } catch {
                showNotice("邀请群成员失败：\(error.localizedDescription)")
            }
        }
    }

    func submitOpenApiAddFriendFromGroupIfAvailable(
        _ member: ChatParticipant,
        group: ChatParticipant,
        completion: @escaping (Bool) -> Void
    ) {
        guard let context = openApiGroupContext(for: group) else {
            showNotice("当前群聊没有匹配的 OpenAPI 群 ID")
            completion(false)
            return
        }
        let wxid = memberWxid(for: member)
        guard !wxid.isEmpty, !wxid.hasSuffix("@chatroom") else {
            showNotice("该群成员缺少可添加的 wxid")
            completion(false)
            return
        }
        let account = OpenApiSocialAccount(
            deviceUUID: context.account.clientUuid,
            weChatID: context.account.wxid
        )
        showNotice("群内加好友已提交")
        Task { @MainActor in
            do {
                _ = try await OpenApiSocialManagementService().addFriendFromChatRoom(
                    account: account,
                    chatRoomID: context.group.wxid,
                    friendWxid: wxid,
                    message: "你好，我们在群里见过。",
                    remark: member.displayName
                )
                showNotice("群内加好友已完成")
                completion(true)
            } catch {
                showNotice("群内加好友失败：\(error.localizedDescription)")
                completion(false)
            }
        }
    }

    func insertGroupNotice(_ notice: String, in group: ChatParticipant) {
        let trimmed = notice.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        state.selectedFriendID = group.id
        let message = ChatMessage(
            id: UUID(),
            conversationID: group.id,
            type: .groupNotice,
            sender: state.currentUser,
            body: "群公告已更新",
            detail: trimmed,
            isOutgoing: false,
            presentation: .bare,
            timestamp: currentTimestamp(),
            sentAt: Date(),
            isGroupConversation: true
        )
        state.messages.append(message)
        persistMessages()
        cancelLeftScrollPreview()
        leftCollectionView.reloadData()
        updateLeftCollectionBounceInsets()
        rightAccountCollectionView.reloadData()
        rightToolCollectionView.reloadData()
        messageCollectionView.reloadData()
        if let indexPath = lastRenderedMessageIndexPath() {
            messageCollectionView.scrollToItem(at: indexPath, at: .bottom, animated: true)
        }
        updateConnections()
        showNotice("群公告已发送到\(group.displayName)")
    }

    func sendQuickPhraseFromTool() {
        guard let phrase = quickPhrases.first else {
            showMessageActionNotice("请先长按“快捷语”设置快捷语")
            return
        }
        insertOutgoingMessage(type: .text, body: phrase)
    }

    func presentFavoriteShareSheet(sourceView: UIView) {
        let account = state.currentUser
        let profile = contactCardProfile(for: account)
        let items = favoriteShareItems(for: account, profile: profile)
        let controller = FavoriteLibraryViewController(
            accountName: profile.displayName,
            items: items
        )
        controller.onSelectItem = { [weak self] item in
            guard let self else { return }
            self.sendFavoriteShare(item, account: account, profile: profile)
            self.navigationController?.popToViewController(self, animated: true)
        }
        navigationController?.setNavigationBarHidden(false, animated: false)
        navigationItem.backBarButtonItem = UIBarButtonItem(title: "返回", style: .plain, target: nil, action: nil)
        navigationController?.pushViewController(controller, animated: true)
    }

    func favoriteShareItems(for account: ChatParticipant, profile: ContactCardProfile) -> [FavoriteShareItem] {
        let accountIndex = state.currentUsers.firstIndex(where: { $0.id == account.id }) ?? 0
        let presets: [[FavoriteShareItem]] = [
            [
                FavoriteShareItem(title: "官网资料合集", subtitle: "链接收藏", detail: "https://cc2.cx\n\(profile.company) 官网、资料入口和常用页面。", category: .link, dateText: "今天", source: profile.displayName, previewKind: .link),
                FavoriteShareItem(title: "客户沟通话术", subtitle: "文档收藏", detail: "DOC · \(profile.role)\n包含常用开场、跟进、售后回复模板。", category: .file, dateText: "6月18日", source: profile.displayName, previewKind: .file),
                FavoriteShareItem(title: "门店照片参考", subtitle: "图片收藏", detail: "图片 9 张 · 可在 App 内查看高清原图。", category: .media, dateText: "5月16日", source: profile.displayName, previewKind: .image),
                FavoriteShareItem(title: "与沐北的聊天记录", subtitle: "聊天记录", detail: "Mandy: 这个1650...\n102管家: 是的\n小小佩琪", category: .chat, dateText: "2025年6月5日", source: profile.displayName, previewKind: .chatRecord),
                FavoriteShareItem(title: "轻松生成二维码：SpringBoot 与 ZXing 完美结合", subtitle: "网页链接", detail: "技术文章 · https://cc2.cx/articles/qrcode", category: .link, dateText: "2024年4月3日", source: "公众号", previewKind: .link)
            ],
            [
                FavoriteShareItem(title: "AI 提示词库", subtitle: "文本收藏", detail: "适合生成回复、总结聊天、整理任务的提示词。", category: .text, dateText: "今天", source: profile.displayName, previewKind: .text),
                FavoriteShareItem(title: "需求分析文档", subtitle: "PDF 收藏", detail: "PDF · 产品需求拆解、功能边界和验收清单。", category: .file, dateText: "6月12日", source: profile.displayName, previewKind: .file),
                FavoriteShareItem(title: "自动化链接集合", subtitle: "链接收藏", detail: "https://cc2.cx/ai\n模型配置、接口说明、调试入口。", category: .link, dateText: "5月20日", source: profile.displayName, previewKind: .link),
                FavoriteShareItem(title: "Codex 调试录屏", subtitle: "视频收藏", detail: "视频 02:18 · API 配置和流式输出检查。", category: .media, dateText: "4月9日", source: profile.displayName, previewKind: .video),
                FavoriteShareItem(title: "接口联调聊天记录", subtitle: "聊天记录", detail: "Codex: 连接成功\nEason: 生成草稿\n系统: 已保存配置", category: .chat, dateText: "2025年3月2日", source: profile.displayName, previewKind: .chatRecord)
            ],
            [
                FavoriteShareItem(title: "售后处理 SOP", subtitle: "文档收藏", detail: "WORD · 退款、换货、投诉升级流程。", category: .file, dateText: "今天", source: profile.displayName, previewKind: .file),
                FavoriteShareItem(title: "客服快捷回复", subtitle: "文本收藏", detail: "包含物流、支付、活动、会员问题的常用答复。", category: .text, dateText: "昨天", source: profile.displayName, previewKind: .text),
                FavoriteShareItem(title: "问题截图归档", subtitle: "图片收藏", detail: "图片 12 张 · 问题示例和处理结果。", category: .media, dateText: "6月1日", source: profile.displayName, previewKind: .image),
                FavoriteShareItem(title: "订单售后链接", subtitle: "链接收藏", detail: "https://cc2.cx/support\n售后单、退款审核、物流跟踪入口。", category: .link, dateText: "5月8日", source: profile.displayName, previewKind: .link)
            ],
            [
                FavoriteShareItem(title: "今日门店数据", subtitle: "表格收藏", detail: "XLSX · 客流、成交、库存、收银汇总。", category: .file, dateText: "今天", source: profile.displayName, previewKind: .file),
                FavoriteShareItem(title: "活动物料包", subtitle: "文件收藏", detail: "ZIP · 海报、短视频、导购话术和陈列图。", category: .file, dateText: "昨天", source: profile.displayName, previewKind: .file),
                FavoriteShareItem(title: "店长巡检清单", subtitle: "文档收藏", detail: "MD · 开店、闭店、卫生、陈列、设备检查。", category: .file, dateText: "6月20日", source: profile.displayName, previewKind: .file),
                FavoriteShareItem(title: "陈列短视频参考", subtitle: "视频收藏", detail: "视频 00:45 · 门店陈列动线示例。", category: .media, dateText: "5月16日", source: profile.displayName, previewKind: .video),
                FavoriteShareItem(title: "巡店群聊天记录", subtitle: "聊天记录", detail: "店长: 已完成巡检\n导购: 陈列已调整\n运营: 收到", category: .chat, dateText: "2025年6月5日", source: profile.displayName, previewKind: .chatRecord)
            ]
        ]
        var items = presets[accountIndex % presets.count]
        items.append(contentsOf: items.prefix(3).map {
            FavoriteShareItem(
                title: $0.title,
                subtitle: $0.subtitle,
                detail: $0.detail,
                category: .recent,
                dateText: $0.dateText,
                source: $0.source,
                previewKind: $0.previewKind
            )
        })
        let userItems = userFavoriteItemsByAccountID[account.id] ?? []
        items.insert(contentsOf: userItems, at: 0)
        return items
    }

    func favoriteMessage(_ message: ChatMessage) {
        let item = favoriteShareItem(from: message)
        let accountID = state.selectedAccountID
        var items = userFavoriteItemsByAccountID[accountID] ?? []
        if let existingIndex = items.firstIndex(where: {
            $0.title == item.title
                && $0.source == item.source
                && $0.detail == item.detail
        }) {
            items.remove(at: existingIndex)
        }
        items.insert(item, at: 0)
        userFavoriteItemsByAccountID[accountID] = Array(items.prefix(80))
        persistUserFavoriteItems()
        showNotice("已收藏")
    }

    func favoriteShareItem(from message: ChatMessage) -> FavoriteShareItem {
        let category = favoriteCategory(for: message)
        let previewKind = favoritePreviewKind(for: message, category: category)
        let title = favoriteTitle(for: message)
        let subtitle = favoriteSubtitle(for: message, category: category)
        let detail = favoriteDetail(for: message, category: category)
        return FavoriteShareItem(
            title: title,
            subtitle: subtitle,
            detail: detail,
            category: category,
            dateText: "今天",
            source: message.sender.displayName,
            previewKind: previewKind
        )
    }

    func favoriteCategory(for message: ChatMessage) -> FavoriteCategory {
        switch message.type {
        case .image, .capturedPhoto, .stickerGif, .video, .channelsVideo, .channelsLive, .music:
            return .media
        case .file:
            return .file
        case .webLink, .article, .miniProgram, .contactCard, .groupInvite:
            return .link
        case .mergedForward:
            return .chat
        case .favorite:
            return favoriteCategoryFromSharedDetail(message.detail) ?? .text
        default:
            let combined = [message.body, message.detail, richElementsSearchText(message.richElements)]
                .joined(separator: " ")
            return firstDetectedURLText(in: combined) == nil ? .text : .link
        }
    }

    func favoriteCategoryFromSharedDetail(_ detail: String) -> FavoriteCategory? {
        for category in FavoriteCategory.allCases where detail.contains("类别：\(category.shortTitle)") {
            return category
        }
        return nil
    }

    func favoritePreviewKind(for message: ChatMessage, category: FavoriteCategory) -> FavoritePreviewKind {
        switch message.type {
        case .image, .capturedPhoto, .stickerGif:
            return .image
        case .video, .channelsVideo, .channelsLive:
            return .video
        case .music:
            return .link
        case .file:
            return .file
        case .mergedForward:
            return .chatRecord
        case .favorite:
            return favoritePreviewKindFromSharedDetail(message.detail) ?? previewKind(for: category)
        default:
            return previewKind(for: category)
        }
    }

    func favoritePreviewKindFromSharedDetail(_ detail: String) -> FavoritePreviewKind? {
        if detail.contains("类别：\(FavoriteCategory.media.shortTitle)") {
            return detail.localizedCaseInsensitiveContains("视频") ? .video : .image
        }
        if detail.contains("类别：\(FavoriteCategory.file.shortTitle)") { return .file }
        if detail.contains("类别：\(FavoriteCategory.link.shortTitle)") { return .link }
        if detail.contains("类别：\(FavoriteCategory.chat.shortTitle)") { return .chatRecord }
        if detail.contains("类别：\(FavoriteCategory.text.shortTitle)") { return .text }
        return nil
    }

    func previewKind(for category: FavoriteCategory) -> FavoritePreviewKind {
        switch category {
        case .media:
            return .image
        case .file:
            return .file
        case .link:
            return .link
        case .chat:
            return .chatRecord
        case .recent, .text:
            return .text
        }
    }

    func favoriteTitle(for message: ChatMessage) -> String {
        let body = message.body.trimmingCharacters(in: .whitespacesAndNewlines)
        if !body.isEmpty { return String(body.prefix(42)) }
        if message.type == .file, let attachmentURL = message.attachmentURL {
            return attachmentURL.lastPathComponent
        }
        if message.type == .mergedForward {
            return "聊天记录 \(max(message.mergedForwardMessages.count, 1)) 条"
        }
        return message.type.title
    }

    func favoriteSubtitle(for message: ChatMessage, category: FavoriteCategory) -> String {
        switch category {
        case .media:
            switch message.type {
            case .video, .channelsVideo, .channelsLive:
                return "视频收藏"
            case .stickerGif:
                return "GIF 收藏"
            case .music:
                return "音乐收藏"
            default:
                return "图片收藏"
            }
        case .file:
            let ext = message.attachmentURL?.pathExtension.uppercased() ?? ""
            return ext.isEmpty ? "文件收藏" : "\(ext) · 文件收藏"
        case .link:
            return firstDetectedURLText(in: [message.body, message.detail].joined(separator: " ")) == nil
                ? "\(message.type.title)收藏"
                : "链接收藏"
        case .chat:
            return "聊天记录收藏"
        case .recent, .text:
            return "\(message.type.title)收藏"
        }
    }

    func favoriteDetail(for message: ChatMessage, category: FavoriteCategory) -> String {
        let conversationName = state.participants.first { $0.id == message.conversationID }?.displayName
            ?? state.activeFriend?.displayName
            ?? "全部聊天"
        var lines: [String] = [
            "类型：\(message.type.title)",
            "分类：\(category.shortTitle)",
            "发送人：\(message.sender.displayName)",
            "会话：\(conversationName)",
            "时间：\(message.displayTimestamp)"
        ]

        let body = message.body.trimmingCharacters(in: .whitespacesAndNewlines)
        if !body.isEmpty {
            lines.append("内容：\(body)")
        }
        let detail = message.detail.trimmingCharacters(in: .whitespacesAndNewlines)
        if !detail.isEmpty {
            lines.append("详情：\(detail)")
        }
        if let attachmentURL = message.attachmentURL {
            lines.append("附件：\(attachmentURL.lastPathComponent)")
            lines.append("路径：\(attachmentURL.absoluteString)")
        }

        let richElementLines = favoriteRichElementDetails(message.richElements)
        if !richElementLines.isEmpty {
            lines.append("富文本：")
            lines.append(contentsOf: richElementLines)
        }

        let detectedLinks = detectedURLTexts(in: [
            message.body,
            message.detail,
            richElementsSearchText(message.richElements),
            message.attachmentURL?.absoluteString ?? ""
        ].joined(separator: "\n"))
        if !detectedLinks.isEmpty {
            lines.append("链接：")
            lines.append(contentsOf: detectedLinks.map { "- \($0)" })
        }

        if !message.mergedForwardMessages.isEmpty {
            lines.append("聊天记录明细：")
            lines.append(contentsOf: message.mergedForwardMessages.prefix(30).enumerated().map { index, item in
                let text = item.body.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                    ? item.type.title
                    : item.body
                return "\(index + 1). \(item.sender.displayName)：\(text)"
            })
        }

        if lines.count == 5 {
            lines.append("内容：\(message.type.template.secondaryText)")
        }
        return lines.joined(separator: "\n")
    }

    func favoriteRichElementDetails(_ elements: [BubbleRichElement]) -> [String] {
        elements.map { element in
            switch element {
            case .text(let text):
                return "- 文本：\(text)"
            case .blueLink(let label, let url, _):
                return "- 链接：\(label) \(url)"
            case .taggedFile(let label, let url, let prefix):
                return "- 文件链接：\(prefix)\(label) \(url)"
            case .mention(let name):
                return "- @人：\(name)"
            case .aiToken(let token):
                return "- AI 标记：\(token)"
            case .image(let name, let url, let aspect, let access):
                return "- 图片：\(name) \(aspect == .vertical ? "竖图" : "横图") \(access.rawValue) \(url)"
            case .inlineCard(let kind, let title, let subtitle, let url):
                return "- \(kind.rawValue)：\(title) \(subtitle) \(url)"
            case .location(let title, let address, let url):
                return "- 位置：\(title) \(address) \(url)"
            case .quote(let author, let text):
                return "- 引用：\(author)：\(text)"
            case .file(let name, let format, let url, let preview, let access):
                return "- 文件：\(format.rawValue) \(name) \(access.rawValue) \(url)\n  预览：\(preview)"
            }
        }
    }

    func richElementsSearchText(_ elements: [BubbleRichElement]) -> String {
        favoriteRichElementDetails(elements).joined(separator: "\n")
    }

    func detectedURLTexts(in text: String) -> [String] {
        let pattern = #"(?i)\b((?:https?://|www\.)[A-Za-z0-9\-._~:/?#\[\]@!$&'()*+,;=%]+)"#
        guard let regex = try? NSRegularExpression(pattern: pattern) else { return [] }
        let fullRange = NSRange(text.startIndex..<text.endIndex, in: text)
        let matches = regex.matches(in: text, range: fullRange)
        var links: [String] = []
        for match in matches {
            guard let range = Range(match.range(at: 1), in: text) else { continue }
            var end = range.upperBound
            while end > range.lowerBound,
                  trailingURLPunctuation.contains(text[text.index(before: end)]) {
                end = text.index(before: end)
            }
            guard end > range.lowerBound else { continue }
            let link = String(text[range.lowerBound..<end])
            if !links.contains(link) {
                links.append(link)
            }
        }
        return links
    }

    func sendFavoriteShare(
        _ item: FavoriteShareItem,
        account: ChatParticipant,
        profile: ContactCardProfile
    ) {
        let title = item.title.replacingOccurrences(of: "发送：", with: "")
        let detail = "\(item.subtitle)\n类别：\(item.category.shortTitle)\n来自 \(profile.displayName)（\(profile.company)）\n\(item.detail)"
        let attachmentURL: URL?
        if item.previewKind == .image || item.previewKind == .video {
            let previewSeed = ChatMessage(
                id: UUID(),
                conversationID: state.selectedFriendID ?? state.friends.first?.id ?? account.id,
                type: .favorite,
                sender: account,
                body: title,
                detail: detail,
                isOutgoing: true,
                presentation: .avatarOnly,
                timestamp: currentTimestamp(),
                sentAt: Date()
            )
            attachmentURL = makeDemoImageFile(for: previewSeed)
        } else {
            attachmentURL = nil
        }
        insertOutgoingMessage(
            type: .favorite,
            body: title,
            detail: detail,
            attachmentURL: attachmentURL
        )
    }

    func presentWebLinkToolSheet(sourceView: UIView) {
        let alert = UIAlertController(
            title: "网页链接",
            message: "默认链接：\(defaultWebLinkText)\n访问限制：\(linkRequiresApproval ? "后续发送的链接都需要申请" : "按链接判断")",
            preferredStyle: .actionSheet
        )
        alert.addAction(UIAlertAction(title: "发送默认链接", style: .default) { [weak self] _ in
            self?.sendWebLinkMessage(self?.defaultWebLinkText ?? Self.fallbackDefaultWebLinkText)
        })
        alert.addAction(UIAlertAction(title: "输入新链接发送", style: .default) { [weak self] _ in
            self?.presentSendWebLinkAlert(prefilledText: "")
        })
        alert.addAction(UIAlertAction(title: "设置默认链接", style: .default) { [weak self] _ in
            self?.presentSetDefaultWebLinkAlert()
        })
        alert.addAction(UIAlertAction(title: linkRequiresApproval ? "关闭链接申请限制" : "开启链接申请限制", style: .default) { [weak self] _ in
            self?.toggleLinkAccessRequirement()
        })
        alert.addAction(UIAlertAction(title: "取消", style: .cancel))
        presentSheet(alert, sourceView: sourceView)
    }

    func presentSendWebLinkAlert(prefilledText: String) {
        let alert = UIAlertController(
            title: "发送网页链接",
            message: linkRequiresApproval ? "当前设置：发送出去的链接需要申请后访问" : "当前设置：点击链接按链接判断是否需申请",
            preferredStyle: .alert
        )
        alert.addTextField { textField in
            textField.placeholder = "输入网址或描述 + 网址"
            textField.text = prefilledText
            textField.keyboardType = .URL
            textField.autocapitalizationType = .none
        }
        alert.addAction(UIAlertAction(title: "取消", style: .cancel))
        alert.addAction(UIAlertAction(title: "发送", style: .default) { [weak self, weak alert] _ in
            self?.sendWebLinkMessage(alert?.textFields?.first?.text ?? "")
        })
        present(alert, animated: true)
    }

    func presentSetDefaultWebLinkAlert() {
        let alert = UIAlertController(title: "设置默认网页链接", message: nil, preferredStyle: .alert)
        alert.addTextField { [defaultWebLinkText] textField in
            textField.placeholder = "例如：这是自己的官网 https://cc2.cx"
            textField.text = defaultWebLinkText
            textField.keyboardType = .URL
            textField.autocapitalizationType = .none
        }
        alert.addAction(UIAlertAction(title: "取消", style: .cancel))
        alert.addAction(UIAlertAction(title: "保存", style: .default) { [weak self, weak alert] _ in
            self?.saveDefaultWebLink(alert?.textFields?.first?.text ?? "")
        })
        present(alert, animated: true)
    }

    func sendWebLinkMessage(_ rawText: String) {
        guard let text = sanitizedWebLinkText(rawText) else {
            showNotice("请输入有效网页链接")
            return
        }
        insertOutgoingMessage(
            type: .webLink,
            body: text,
            detail: "网页链接\n访问限制：需要申请访问"
        )
        markOutgoingLinksRestrictedIfNeeded(body: text, detail: "网页链接", richElements: [], attachmentURL: nil, force: true)
    }

    func loadDefaultWebLink() {
        let saved = ChatSQLiteStore.shared.string(forKey: Self.defaultWebLinkStorageKey)
            ?? UserDefaults.standard.string(forKey: Self.defaultWebLinkStorageKey)
            ?? ""
        defaultWebLinkText = sanitizedWebLinkText(saved) ?? Self.fallbackDefaultWebLinkText
    }

    func loadLinkAccessSetting() {
        linkRequiresApproval = ChatSQLiteStore.shared.bool(forKey: Self.linkRequiresApprovalStorageKey)
            ?? UserDefaults.standard.bool(forKey: Self.linkRequiresApprovalStorageKey)
    }

    func loadRestrictedSentLinks() {
        let saved = ChatSQLiteStore.shared.stringArray(forKey: Self.restrictedSentLinksStorageKey)
            ?? UserDefaults.standard.stringArray(forKey: Self.restrictedSentLinksStorageKey)
            ?? []
        restrictedSentLinkURLs = Set(saved)
    }

    func saveRestrictedSentLinks() {
        ChatSQLiteStore.shared.setStringArray(Array(restrictedSentLinkURLs).sorted(), forKey: Self.restrictedSentLinksStorageKey)
    }

    func loadAccessRequests() {
        if let saved = ChatSQLiteStore.shared.codable([MediaAccessRequest].self, forKey: Self.accessRequestsStorageKey) {
            mediaAccessRequests = saved
            return
        }
        mediaAccessRequests = UserDefaults.standard.data(forKey: Self.accessRequestsStorageKey)
            .flatMap { try? JSONDecoder().decode([MediaAccessRequest].self, from: $0) }
            ?? []
    }

    func saveAccessRequests() {
        ChatSQLiteStore.shared.setCodable(mediaAccessRequests, forKey: Self.accessRequestsStorageKey)
        if let data = try? JSONEncoder().encode(mediaAccessRequests) {
            UserDefaults.standard.set(data, forKey: Self.accessRequestsStorageKey)
        }
    }

    func resourceAccessPolicy(
        for urlText: String,
        scope: BubbleAccessScope = .recipients,
        expiry: ResourceExpiryPolicy = .sevenDays
    ) -> ResourceAccessPolicy {
        let normalized = normalizedURL(from: urlText)?.absoluteString ?? urlText
        return ResourceAccessPolicy.make(scope: scope, shareURL: normalized, expiry: expiry)
    }

    func markOutgoingLinksRestrictedIfNeeded(body: String, detail: String, richElements: [BubbleRichElement], attachmentURL: URL?, force: Bool = false) {
        guard force || linkRequiresApproval else { return }
        let rawLinks = detectedURLTexts(in: [
            body,
            detail,
            richElementsSearchText(richElements),
            attachmentURL?.absoluteString ?? ""
        ].joined(separator: "\n"))
        let normalizedLinks = rawLinks.compactMap { normalizedURL(from: $0)?.absoluteString }
        guard !normalizedLinks.isEmpty else { return }
        normalizedLinks.forEach { restrictedSentLinkURLs.insert($0) }
        saveRestrictedSentLinks()
    }

    func toggleLinkAccessRequirement() {
        linkRequiresApproval.toggle()
        ChatSQLiteStore.shared.setBool(linkRequiresApproval, forKey: Self.linkRequiresApprovalStorageKey)
        showNotice(linkRequiresApproval ? "后续发送链接将需要申请访问" : "后续发送链接将按链接判断")
    }

    func saveDefaultWebLink(_ rawText: String) {
        guard let text = sanitizedWebLinkText(rawText) else {
            showNotice("请输入有效网页链接")
            return
        }
        defaultWebLinkText = text
        ChatSQLiteStore.shared.setString(text, forKey: Self.defaultWebLinkStorageKey)
        showNotice("已设置默认链接")
    }

    func sanitizedWebLinkText(_ rawText: String) -> String? {
        let trimmed = rawText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return nil }
        if firstDetectedURL(in: trimmed) != nil {
            return trimmed
        }
        if let url = normalizedStandaloneWebURL(from: trimmed) {
            return url.absoluteString
        }
        return nil
    }

    func normalizedStandaloneWebURL(from text: String) -> URL? {
        let lowercased = text.lowercased()
        if lowercased.hasPrefix("http://") || lowercased.hasPrefix("https://") {
            return URL(string: text)
        }
        guard !text.contains(where: { $0.isWhitespace }), text.contains(".") else { return nil }
        return URL(string: "https://\(text)")
    }

    func addToolActions(to alert: UIAlertController) {
        let actions: [(String, ChatMessageType)] = [
            ("\u{62cd}\u{6444}\u{7167}\u{7247}", .capturedPhoto),
            ("\u{9009}\u{62e9}\u{56fe}\u{7247}", .image),
            ("\u{89c6}\u{9891} / \u{77ed}\u{89c6}\u{9891}", .video),
            ("\u{6587}\u{4ef6}", .file),
            ("\u{8bed}\u{97f3}", .voice),
            ("\u{4f4d}\u{7f6e}", .location)
        ]
        actions.forEach { title, type in
            alert.addAction(UIAlertAction(title: title, style: .default) { [weak self] _ in
                self?.handleTool(type)
            })
        }
    }

    func presentVideoOptions() {
        let alert = UIAlertController(title: "\u{89c6}\u{9891}", message: nil, preferredStyle: .actionSheet)
        alert.addAction(UIAlertAction(title: "\u{5f55}\u{5236}\u{89c6}\u{9891}", style: .default) { [weak self] _ in
            self?.presentCamera(mode: .video)
        })
        alert.addAction(UIAlertAction(title: "\u{9009}\u{62e9}\u{89c6}\u{9891}", style: .default) { [weak self] _ in
            self?.presentMediaLibrary(mediaTypes: [UTType.movie.identifier])
        })
        alert.addAction(UIAlertAction(title: "\u{53d6}\u{6d88}", style: .cancel))
        presentSheet(alert, sourceView: rightToolCollectionView)
    }

    enum CameraMode {
        case photo
        case video
    }

    func presentCamera(mode: CameraMode) {
        guard UIImagePickerController.isSourceTypeAvailable(.camera) else {
            showNotice("\u{6ca1}\u{6709}\u{53ef}\u{7528}\u{76f8}\u{673a}")
            return
        }

        let cameraMediaTypes = UIImagePickerController.availableMediaTypes(for: .camera) ?? []
        let requiredType = mode == .photo ? UTType.image.identifier : UTType.movie.identifier
        guard cameraMediaTypes.contains(requiredType) else {
            showNotice(mode == .photo ? "\u{76f8}\u{673a}\u{4e0d}\u{652f}\u{6301}\u{62cd}\u{7167}" : "\u{76f8}\u{673a}\u{4e0d}\u{652f}\u{6301}\u{5f55}\u{50cf}")
            return
        }

        requestCameraAccess { [weak self] granted in
            guard let self else { return }
            guard granted else {
                self.showSettingsNotice("\u{9700}\u{8981}\u{76f8}\u{673a}\u{6743}\u{9650}")
                return
            }

            if mode == .video {
                self.requestMicrophoneAccess { micGranted in
                    guard micGranted else {
                        self.showSettingsNotice("\u{9700}\u{8981}\u{9ea6}\u{514b}\u{98ce}\u{6743}\u{9650}")
                        return
                    }
                    self.presentImagePicker(sourceType: .camera, mediaTypes: [requiredType], cameraMode: .video)
                }
            } else {
                self.presentImagePicker(sourceType: .camera, mediaTypes: [requiredType], cameraMode: .photo)
            }
        }
    }

    func presentMediaLibrary(mediaTypes: [String]) {
        if mediaTypes == [UTType.image.identifier] {
            var configuration = PHPickerConfiguration(photoLibrary: .shared())
            configuration.filter = .images
            configuration.selectionLimit = 9
            let picker = PHPickerViewController(configuration: configuration)
            picker.delegate = self
            present(picker, animated: true)
            return
        }
        guard UIImagePickerController.isSourceTypeAvailable(.photoLibrary) else {
            showNotice("\u{65e0}\u{6cd5}\u{6253}\u{5f00}\u{76f8}\u{518c}")
            return
        }
        presentImagePicker(sourceType: .photoLibrary, mediaTypes: mediaTypes, cameraMode: nil)
    }

    func presentImagePicker(
        sourceType: UIImagePickerController.SourceType,
        mediaTypes: [String],
        cameraMode: UIImagePickerController.CameraCaptureMode?
    ) {
        let picker = UIImagePickerController()
        picker.sourceType = sourceType
        picker.mediaTypes = mediaTypes
        if let cameraMode {
            picker.cameraCaptureMode = cameraMode
            picker.videoQuality = .typeMedium
        }
        picker.delegate = self
        present(picker, animated: true)
    }

    func presentDocumentPicker() {
        let picker = UIDocumentPickerViewController(forOpeningContentTypes: supportedDocumentPickerTypes(), asCopy: true)
        picker.delegate = self
        picker.allowsMultipleSelection = false
        present(picker, animated: true)
    }

    func supportedDocumentPickerTypes() -> [UTType] {
        let officeExtensions = [
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
            "csv", "txt", "md", "rtf", "numbers", "pages", "key",
            "html", "htm", "json", "xml", "yaml", "yml", "log",
            "zip", "rar", "7z", "tar", "gz"
        ]
        var types = officeExtensions.compactMap { UTType(filenameExtension: $0) }
        types.append(contentsOf: [.pdf, .plainText, .text, .html, .json, .xml, .rtf, .zip, .item])
        var seen = Set<String>()
        return types.filter { seen.insert($0.identifier).inserted }
    }

    func installDemoDocumentMessages() {
        var documentURLs: [URL] = []
        if let pdfURL = makeDemoPDFDocument() {
            documentURLs.append(pdfURL)
        }
        if let wordURL = makeDemoWordDocument() {
            documentURLs.append(wordURL)
        }
        if let excelURL = makeDemoExcelSpreadsheet() {
            documentURLs.append(excelURL)
        }
        if let csvURL = makeDemoCSVDocument() {
            documentURLs.append(csvURL)
        }
        guard !documentURLs.isEmpty else { return }

        var fileIndex = 0
        for index in state.messages.indices {
            let message = state.messages[index]
            guard message.type == .file, message.attachmentURL == nil else { continue }
            let url = documentURLs[fileIndex % documentURLs.count]
            fileIndex += 1
            state.messages[index] = replacingMessage(
                message,
                body: (message.body as NSString).pathExtension.isEmpty ? url.lastPathComponent : message.body,
                detail: message.detail.isEmpty ? officePreviewDetail(for: url) : message.detail,
                attachmentURL: url
            )
        }
        persistMessages()
    }

    func officePreviewDetail(for url: URL) -> String {
        switch url.pathExtension.lowercased() {
        case "xlsx", "xls":
            return "可打开预览的真实 Excel 表格"
        case "csv":
            return "可打开预览的真实 CSV 表格"
        case "doc", "docx":
            return "可打开预览的真实 Word 文档"
        case "ppt", "pptx":
            return "可打开预览的真实 PPT 演示文稿"
        case "pdf":
            return "可打开预览的真实 PDF 文档"
        default:
            return "可打开预览的真实文件"
        }
    }

    func makeDemoPDFDocument() -> URL? {
        let url = FileManager.default.temporaryDirectory.appendingPathComponent("周末活动执行方案.pdf")
        let renderer = UIGraphicsPDFRenderer(bounds: CGRect(x: 0, y: 0, width: 595, height: 842))
        do {
            try renderer.writePDF(to: url) { context in
                context.beginPage()
                let titleAttributes: [NSAttributedString.Key: Any] = [
                    .font: UIFont.systemFont(ofSize: 28, weight: .bold),
                    .foregroundColor: UIColor.black
                ]
                let bodyAttributes: [NSAttributedString.Key: Any] = [
                    .font: UIFont.systemFont(ofSize: 16),
                    .foregroundColor: UIColor.darkGray
                ]
                ("周末活动执行方案" as NSString).draw(at: CGPoint(x: 56, y: 70), withAttributes: titleAttributes)
                let body = """
                这是一份真实生成的 PDF 文件，用于模拟聊天里的活动执行文档。

                点击聊天中的 PDF 文件气泡后，会通过 QuickLook 打开这个文档。

                活动信息：
                1. 14:00 到场布置签到桌和展架
                2. 16:00 检查电源、网络和直播机位
                3. 20:00 视频号直播开场
                4. 现场照片、短视频和位置消息均可在聊天中预览
                """
                (body as NSString).draw(
                    in: CGRect(x: 56, y: 128, width: 480, height: 520),
                    withAttributes: bodyAttributes
                )
            }
            return url
        } catch {
            return nil
        }
    }

    func makeDemoWordDocument() -> URL? {
        let url = FileManager.default.temporaryDirectory.appendingPathComponent("人员分工确认.docx")
        let documentXML = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
          <w:body>
            <w:p><w:r><w:rPr><w:b/></w:rPr><w:t>人员分工确认</w:t></w:r></w:p>
            <w:p><w:r><w:t>这是一个真实生成的 DOCX 文件，用于模拟聊天中的人员分工文档。</w:t></w:r></w:p>
            <w:p><w:r><w:t>点击聊天中的 Word 文件气泡后，会通过 QuickLook 打开这个文档。</w:t></w:r></w:p>
            <w:p><w:r><w:t>张三：总控；李明：场地；小王：签到；陈浩：直播；周敏：物料。</w:t></w:r></w:p>
          </w:body>
        </w:document>
        """
        let files: [(name: String, data: Data)] = [
            (
                name: "[Content_Types].xml",
                data: """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                  <Default Extension="xml" ContentType="application/xml"/>
                  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
                </Types>
                """.data(using: .utf8) ?? Data()
            ),
            (
                name: "_rels/.rels",
                data: """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
                </Relationships>
                """.data(using: .utf8) ?? Data()
            ),
            (name: "word/document.xml", data: documentXML.data(using: .utf8) ?? Data())
        ]

        do {
            try makeZipArchive(files: files).write(to: url, options: .atomic)
            return url
        } catch {
            return nil
        }
    }

    func makeDemoExcelSpreadsheet() -> URL? {
        let url = FileManager.default.temporaryDirectory.appendingPathComponent("现场问题登记表.xlsx")
        let sheetXML = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
          <sheetData>
            <row r="1">
              <c r="A1" t="inlineStr"><is><t>问题编号</t></is></c>
              <c r="B1" t="inlineStr"><is><t>负责人</t></is></c>
              <c r="C1" t="inlineStr"><is><t>状态</t></is></c>
              <c r="D1" t="inlineStr"><is><t>截止时间</t></is></c>
            </row>
            <row r="2">
              <c r="A2" t="inlineStr"><is><t>ISSUE-001</t></is></c>
              <c r="B2" t="inlineStr"><is><t>张三</t></is></c>
              <c r="C2" t="inlineStr"><is><t>处理中</t></is></c>
              <c r="D2" t="inlineStr"><is><t>今天 18:00</t></is></c>
            </row>
            <row r="3">
              <c r="A3" t="inlineStr"><is><t>ISSUE-002</t></is></c>
              <c r="B3" t="inlineStr"><is><t>周敏</t></is></c>
              <c r="C3" t="inlineStr"><is><t>已确认</t></is></c>
              <c r="D3" t="inlineStr"><is><t>明天 10:00</t></is></c>
            </row>
            <row r="4">
              <c r="A4" t="inlineStr"><is><t>ISSUE-003</t></is></c>
              <c r="B4" t="inlineStr"><is><t>李明</t></is></c>
              <c r="C4" t="inlineStr"><is><t>待反馈</t></is></c>
              <c r="D4" t="inlineStr"><is><t>本周五</t></is></c>
            </row>
          </sheetData>
        </worksheet>
        """
        let files: [(name: String, data: Data)] = [
            (
                name: "[Content_Types].xml",
                data: """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                  <Default Extension="xml" ContentType="application/xml"/>
                  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
                  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
                  <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
                </Types>
                """.data(using: .utf8) ?? Data()
            ),
            (
                name: "_rels/.rels",
                data: """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
                </Relationships>
                """.data(using: .utf8) ?? Data()
            ),
            (
                name: "xl/workbook.xml",
                data: """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                  <sheets>
                    <sheet name="现场问题登记表" sheetId="1" r:id="rId1"/>
                  </sheets>
                </workbook>
                """.data(using: .utf8) ?? Data()
            ),
            (
                name: "xl/_rels/workbook.xml.rels",
                data: """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
                  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
                </Relationships>
                """.data(using: .utf8) ?? Data()
            ),
            (
                name: "xl/styles.xml",
                data: """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                  <fonts count="1"><font><sz val="11"/><name val="Arial"/></font></fonts>
                  <fills count="1"><fill><patternFill patternType="none"/></fill></fills>
                  <borders count="1"><border/></borders>
                  <cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
                  <cellXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/></cellXfs>
                </styleSheet>
                """.data(using: .utf8) ?? Data()
            ),
            (name: "xl/worksheets/sheet1.xml", data: sheetXML.data(using: .utf8) ?? Data())
        ]

        do {
            try makeZipArchive(files: files).write(to: url, options: .atomic)
            return url
        } catch {
            return nil
        }
    }

    func makeDemoCSVDocument() -> URL? {
        let url = FileManager.default.temporaryDirectory.appendingPathComponent("销售日报.csv")
        let content = """
        日期,门店,客流,成交,客单价
        2026-07-06,深圳南山店,328,56,189.50
        2026-07-06,广州天河店,411,72,205.00
        2026-07-06,杭州西湖店,286,44,176.30
        """
        do {
            try content.write(to: url, atomically: true, encoding: .utf8)
            return url
        } catch {
            return nil
        }
    }

    func makeZipArchive(files: [(name: String, data: Data)]) throws -> Data {
        var archive = Data()
        var centralDirectory = Data()
        var entryCount: UInt16 = 0

        for file in files {
            let nameData = Data(file.name.utf8)
            let crc = crc32(file.data)
            let localHeaderOffset = UInt32(archive.count)
            let dataSize = UInt32(file.data.count)
            let nameLength = UInt16(nameData.count)
            let utf8Flag: UInt16 = 0x0800

            archive.appendUInt32LE(0x04034b50)
            archive.appendUInt16LE(20)
            archive.appendUInt16LE(utf8Flag)
            archive.appendUInt16LE(0)
            archive.appendUInt16LE(0)
            archive.appendUInt16LE(0)
            archive.appendUInt32LE(crc)
            archive.appendUInt32LE(dataSize)
            archive.appendUInt32LE(dataSize)
            archive.appendUInt16LE(nameLength)
            archive.appendUInt16LE(0)
            archive.append(nameData)
            archive.append(file.data)

            centralDirectory.appendUInt32LE(0x02014b50)
            centralDirectory.appendUInt16LE(20)
            centralDirectory.appendUInt16LE(20)
            centralDirectory.appendUInt16LE(utf8Flag)
            centralDirectory.appendUInt16LE(0)
            centralDirectory.appendUInt16LE(0)
            centralDirectory.appendUInt16LE(0)
            centralDirectory.appendUInt32LE(crc)
            centralDirectory.appendUInt32LE(dataSize)
            centralDirectory.appendUInt32LE(dataSize)
            centralDirectory.appendUInt16LE(nameLength)
            centralDirectory.appendUInt16LE(0)
            centralDirectory.appendUInt16LE(0)
            centralDirectory.appendUInt16LE(0)
            centralDirectory.appendUInt16LE(0)
            centralDirectory.appendUInt32LE(0)
            centralDirectory.appendUInt32LE(localHeaderOffset)
            centralDirectory.append(nameData)
            entryCount += 1
        }

        let centralDirectoryOffset = UInt32(archive.count)
        let centralDirectorySize = UInt32(centralDirectory.count)
        archive.append(centralDirectory)
        archive.appendUInt32LE(0x06054b50)
        archive.appendUInt16LE(0)
        archive.appendUInt16LE(0)
        archive.appendUInt16LE(entryCount)
        archive.appendUInt16LE(entryCount)
        archive.appendUInt32LE(centralDirectorySize)
        archive.appendUInt32LE(centralDirectoryOffset)
        archive.appendUInt16LE(0)
        return archive
    }

    func crc32(_ data: Data) -> UInt32 {
        var crc: UInt32 = 0xffff_ffff
        for byte in data {
            crc ^= UInt32(byte)
            for _ in 0..<8 {
                if crc & 1 == 1 {
                    crc = (crc >> 1) ^ 0xedb8_8320
                } else {
                    crc >>= 1
                }
            }
        }
        return crc ^ 0xffff_ffff
    }

    func replacingMessage(
        _ message: ChatMessage,
        sender: ChatParticipant? = nil,
        body: String? = nil,
        detail: String? = nil,
        attachmentURL: URL? = nil
    ) -> ChatMessage {
        ChatMessage(
            id: message.id,
            conversationID: message.conversationID,
            type: message.type,
            sender: sender ?? message.sender,
            body: body ?? message.body,
            detail: detail ?? message.detail,
            isOutgoing: message.isOutgoing,
            presentation: message.presentation,
            timestamp: message.timestamp,
            sentAt: message.sentAt,
            backendMessageID: message.backendMessageID,
            attachmentURL: attachmentURL ?? message.attachmentURL,
            isAI: message.isAI,
            isGroupConversation: message.isGroupConversation,
            richElements: message.richElements,
            contactCard: message.contactCard,
            contactCardAccountID: message.contactCardAccountID,
            quotedMessageID: message.quotedMessageID,
            mergedForwardMessages: message.mergedForwardMessages,
            recipientAccountID: message.recipientAccountID,
            unansweredMetadata: message.unansweredMetadata
        )
    }

    func installDemoVoiceAttachments() {
        for index in state.messages.indices {
            let message = state.messages[index]
            guard message.type == .voice, message.attachmentURL == nil,
                  let url = makeDemoVoiceAudio(for: message)
            else { continue }

            state.messages[index] = ChatMessage(
                id: message.id,
                conversationID: message.conversationID,
                type: message.type,
                sender: message.sender,
                body: message.body,
                detail: message.detail.isEmpty ? "点击播放这条语音" : message.detail,
                isOutgoing: message.isOutgoing,
                presentation: message.presentation,
                timestamp: message.timestamp,
                sentAt: message.sentAt,
                backendMessageID: message.backendMessageID,
                attachmentURL: url,
                isAI: message.isAI,
                isGroupConversation: message.isGroupConversation,
                richElements: message.richElements,
                contactCard: message.contactCard,
                contactCardAccountID: message.contactCardAccountID,
                quotedMessageID: message.quotedMessageID,
                mergedForwardMessages: message.mergedForwardMessages,
                recipientAccountID: message.recipientAccountID,
                unansweredMetadata: message.unansweredMetadata
            )
        }
        persistMessages()
    }

    func installDemoMediaAttachments() {
        let videoURL = makeDemoVideoFile()
        for index in state.messages.indices {
            let message = state.messages[index]
            guard message.attachmentURL == nil else { continue }

            let attachmentURL: URL?
            switch message.type {
            case .image, .capturedPhoto:
                attachmentURL = makeDemoImageFile(for: message)
            case .stickerGif:
                attachmentURL = makeDemoGIFFile(for: message)
            case .video, .channelsVideo:
                attachmentURL = videoURL
            case .favorite where message.detail.contains("图片") || message.detail.contains("照片") || message.detail.contains("截图") || message.detail.contains("物料"):
                attachmentURL = makeDemoImageFile(for: message)
            default:
                attachmentURL = nil
            }

            guard let attachmentURL else { continue }
            state.messages[index] = ChatMessage(
                id: message.id,
                conversationID: message.conversationID,
                type: message.type,
                sender: message.sender,
                body: message.body,
                detail: message.detail,
                isOutgoing: message.isOutgoing,
                presentation: message.presentation,
                timestamp: message.timestamp,
                sentAt: message.sentAt,
                backendMessageID: message.backendMessageID,
                attachmentURL: attachmentURL,
                isAI: message.isAI,
                isGroupConversation: message.isGroupConversation,
                richElements: message.richElements,
                contactCard: message.contactCard,
                contactCardAccountID: message.contactCardAccountID,
                quotedMessageID: message.quotedMessageID,
                mergedForwardMessages: message.mergedForwardMessages,
                recipientAccountID: message.recipientAccountID,
                unansweredMetadata: message.unansweredMetadata
            )
        }
        persistMessages()
    }

    func installDemoMusicAttachments() {
        for index in state.messages.indices {
            let message = state.messages[index]
            guard message.type == .music, message.attachmentURL == nil,
                  let url = makeDemoMusicAudio(for: message)
            else { continue }
            state.messages[index] = replacingMessage(message, attachmentURL: url)
        }
        persistMessages()
    }

    func makeDemoImageFile(for message: ChatMessage) -> URL? {
        let url = FileManager.default.temporaryDirectory
            .appendingPathComponent("demo-image-\(message.id.uuidString).jpg")
        if FileManager.default.fileExists(atPath: url.path) {
            return url
        }

        let size = CGSize(width: 960, height: 640)
        let renderer = UIGraphicsImageRenderer(size: size)
        let image = renderer.image { context in
            let rect = CGRect(origin: .zero, size: size)
            UIColor(red: 0.88, green: 0.94, blue: 0.93, alpha: 1).setFill()
            context.fill(rect)

            UIColor(red: 0.12, green: 0.42, blue: 0.48, alpha: 1).setFill()
            UIBezierPath(roundedRect: CGRect(x: 70, y: 70, width: 820, height: 420), cornerRadius: 32).fill()
            UIColor(red: 0.52, green: 0.82, blue: 0.72, alpha: 1).setFill()
            UIBezierPath(ovalIn: CGRect(x: 120, y: 110, width: 160, height: 160)).fill()
            UIColor.white.withAlphaComponent(0.82).setFill()
            UIBezierPath(roundedRect: CGRect(x: 330, y: 132, width: 460, height: 34), cornerRadius: 17).fill()
            UIBezierPath(roundedRect: CGRect(x: 330, y: 194, width: 340, height: 28), cornerRadius: 14).fill()
            UIBezierPath(roundedRect: CGRect(x: 330, y: 250, width: 410, height: 28), cornerRadius: 14).fill()
            UIColor(red: 0.94, green: 0.76, blue: 0.30, alpha: 1).setFill()
            UIBezierPath(roundedRect: CGRect(x: 150, y: 390, width: 660, height: 56), cornerRadius: 28).fill()

            let title = message.body.isEmpty ? "聊天图片" : message.body
            let attributes: [NSAttributedString.Key: Any] = [
                .font: UIFont.systemFont(ofSize: 36, weight: .bold),
                .foregroundColor: UIColor.white
            ]
            (title as NSString).draw(
                in: CGRect(x: 90, y: 520, width: 780, height: 52),
                withAttributes: attributes
            )
        }

        guard let data = image.jpegData(compressionQuality: 0.88) else { return nil }
        do {
            try data.write(to: url, options: .atomic)
            return url
        } catch {
            return nil
        }
    }

    func makeDemoGIFFile(for message: ChatMessage) -> URL? {
        let url = FileManager.default.temporaryDirectory
            .appendingPathComponent("demo-sticker-\(message.id.uuidString).gif")
        if FileManager.default.fileExists(atPath: url.path) {
            return url
        }

        guard let destination = CGImageDestinationCreateWithURL(url as CFURL, UTType.gif.identifier as CFString, 8, nil) else {
            return nil
        }
        CGImageDestinationSetProperties(destination, [
            kCGImagePropertyGIFDictionary: [kCGImagePropertyGIFLoopCount: 0]
        ] as CFDictionary)

        for index in 0..<8 {
            let image = makeStickerFrame(title: message.body, phase: CGFloat(index) / 7)
            if let cgImage = image.cgImage {
                CGImageDestinationAddImage(destination, cgImage, [
                    kCGImagePropertyGIFDictionary: [kCGImagePropertyGIFDelayTime: 0.11]
                ] as CFDictionary)
            }
        }
        return CGImageDestinationFinalize(destination) ? url : nil
    }

    func makeStickerFrame(title: String, phase: CGFloat) -> UIImage {
        let size = CGSize(width: 480, height: 360)
        let renderer = UIGraphicsImageRenderer(size: size)
        return renderer.image { context in
            let rect = CGRect(origin: .zero, size: size)
            UIColor(red: 0.97, green: 0.91, blue: 0.98, alpha: 1).setFill()
            context.fill(rect)

            let bounce = sin(phase * .pi * 2) * 22
            UIColor(red: 1.0, green: 0.75, blue: 0.22, alpha: 1).setFill()
            UIBezierPath(ovalIn: CGRect(x: 150, y: 62 + bounce, width: 180, height: 180)).fill()
            UIColor(red: 0.18, green: 0.14, blue: 0.10, alpha: 1).setFill()
            UIBezierPath(ovalIn: CGRect(x: 204, y: 132 + bounce, width: 18, height: 28)).fill()
            UIBezierPath(ovalIn: CGRect(x: 258, y: 132 + bounce, width: 18, height: 28)).fill()
            let smile = UIBezierPath()
            smile.move(to: CGPoint(x: 202, y: 188 + bounce))
            smile.addQuadCurve(to: CGPoint(x: 278, y: 188 + bounce), controlPoint: CGPoint(x: 240, y: 224 + bounce))
            smile.lineWidth = 8
            smile.lineCapStyle = .round
            smile.stroke()

            let badgeRect = CGRect(x: 40, y: 270, width: 106, height: 44)
            UIColor(red: 0.50, green: 0.22, blue: 0.72, alpha: 1).setFill()
            UIBezierPath(roundedRect: badgeRect, cornerRadius: 22).fill()
            let badgeAttributes: [NSAttributedString.Key: Any] = [
                .font: UIFont.systemFont(ofSize: 20, weight: .bold),
                .foregroundColor: UIColor.white
            ]
            ("GIF" as NSString).draw(in: badgeRect.insetBy(dx: 32, dy: 9), withAttributes: badgeAttributes)

            let text = title.isEmpty ? "动态贴纸" : title
            let attributes: [NSAttributedString.Key: Any] = [
                .font: UIFont.systemFont(ofSize: 22, weight: .semibold),
                .foregroundColor: UIColor(red: 0.18, green: 0.12, blue: 0.22, alpha: 0.82)
            ]
            (text as NSString).draw(in: CGRect(x: 170, y: 278, width: 260, height: 34), withAttributes: attributes)
        }
    }

    func makeDemoVideoFile() -> URL? {
        let url = FileManager.default.temporaryDirectory.appendingPathComponent("demo-chat-video.mp4")
        if FileManager.default.fileExists(atPath: url.path) {
            return url
        }

        let size = CGSize(width: 640, height: 360)
        guard let writer = try? AVAssetWriter(outputURL: url, fileType: .mp4) else { return nil }
        let settings: [String: Any] = [
            AVVideoCodecKey: AVVideoCodecType.h264,
            AVVideoWidthKey: size.width,
            AVVideoHeightKey: size.height
        ]
        let input = AVAssetWriterInput(mediaType: .video, outputSettings: settings)
        input.expectsMediaDataInRealTime = false
        let adaptor = AVAssetWriterInputPixelBufferAdaptor(
            assetWriterInput: input,
            sourcePixelBufferAttributes: [
                kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_32ARGB,
                kCVPixelBufferWidthKey as String: size.width,
                kCVPixelBufferHeightKey as String: size.height
            ]
        )
        guard writer.canAdd(input) else { return nil }
        writer.add(input)
        writer.startWriting()
        writer.startSession(atSourceTime: .zero)

        let frameCount = 48
        for frame in 0..<frameCount {
            while !input.isReadyForMoreMediaData {
                Thread.sleep(forTimeInterval: 0.01)
            }
            guard let buffer = makeVideoPixelBuffer(size: size, frame: frame, totalFrames: frameCount) else { continue }
            adaptor.append(buffer, withPresentationTime: CMTime(value: CMTimeValue(frame), timescale: 24))
        }
        input.markAsFinished()

        let semaphore = DispatchSemaphore(value: 0)
        writer.finishWriting {
            semaphore.signal()
        }
        _ = semaphore.wait(timeout: .now() + 4)
        return writer.status == .completed ? url : nil
    }

    func makeVideoPixelBuffer(size: CGSize, frame: Int, totalFrames: Int) -> CVPixelBuffer? {
        var pixelBuffer: CVPixelBuffer?
        let attributes = [
            kCVPixelBufferCGImageCompatibilityKey: true,
            kCVPixelBufferCGBitmapContextCompatibilityKey: true
        ] as CFDictionary
        CVPixelBufferCreate(
            kCFAllocatorDefault,
            Int(size.width),
            Int(size.height),
            kCVPixelFormatType_32ARGB,
            attributes,
            &pixelBuffer
        )
        guard let pixelBuffer else { return nil }
        CVPixelBufferLockBaseAddress(pixelBuffer, [])
        defer { CVPixelBufferUnlockBaseAddress(pixelBuffer, []) }

        guard let context = CGContext(
            data: CVPixelBufferGetBaseAddress(pixelBuffer),
            width: Int(size.width),
            height: Int(size.height),
            bitsPerComponent: 8,
            bytesPerRow: CVPixelBufferGetBytesPerRow(pixelBuffer),
            space: CGColorSpaceCreateDeviceRGB(),
            bitmapInfo: CGImageAlphaInfo.noneSkipFirst.rawValue
        ) else { return nil }

        let progress = CGFloat(frame) / CGFloat(max(totalFrames - 1, 1))
        context.setFillColor(UIColor(red: 0.05, green: 0.12 + progress * 0.25, blue: 0.18, alpha: 1).cgColor)
        context.fill(CGRect(origin: .zero, size: size))
        context.setFillColor(UIColor(red: 0.42, green: 0.84, blue: 0.55, alpha: 1).cgColor)
        context.fillEllipse(in: CGRect(x: 80 + progress * 380, y: 116, width: 116, height: 116))
        context.setFillColor(UIColor.white.withAlphaComponent(0.88).cgColor)
        context.fill(CGRect(x: 96, y: 284, width: 448 * progress, height: 12))
        context.setStrokeColor(UIColor.white.withAlphaComponent(0.36).cgColor)
        context.setLineWidth(4)
        context.stroke(CGRect(x: 96, y: 284, width: 448, height: 12))
        return pixelBuffer
    }

    func makeDemoVoiceAudio(for message: ChatMessage) -> URL? {
        let url = FileManager.default.temporaryDirectory
            .appendingPathComponent("demo-voice-\(message.id.uuidString).wav")
        if FileManager.default.fileExists(atPath: url.path) {
            return url
        }

        let sampleRate: UInt32 = 16_000
        let duration: Double = 1.1
        let sampleCount = Int(Double(sampleRate) * duration)
        var pcm = Data()
        let seed = safeHashIndex(message.id.uuidString, modulo: 180)
        let frequency = 540.0 + Double(seed)

        for sampleIndex in 0..<sampleCount {
            let t = Double(sampleIndex) / Double(sampleRate)
            let envelope = min(1.0, Double(sampleIndex) / 1_600.0) * min(1.0, Double(sampleCount - sampleIndex) / 1_600.0)
            let value = sin(2.0 * .pi * frequency * t) * 0.32 * envelope
            pcm.appendInt16LE(Int16(value * Double(Int16.max)))
        }

        var wav = Data()
        wav.append(Data("RIFF".utf8))
        wav.appendUInt32LE(UInt32(36 + pcm.count))
        wav.append(Data("WAVE".utf8))
        wav.append(Data("fmt ".utf8))
        wav.appendUInt32LE(16)
        wav.appendUInt16LE(1)
        wav.appendUInt16LE(1)
        wav.appendUInt32LE(sampleRate)
        wav.appendUInt32LE(sampleRate * 2)
        wav.appendUInt16LE(2)
        wav.appendUInt16LE(16)
        wav.append(Data("data".utf8))
        wav.appendUInt32LE(UInt32(pcm.count))
        wav.append(pcm)

        do {
            try wav.write(to: url, options: .atomic)
            return url
        } catch {
            return nil
        }
    }

    func makeDemoMusicAudio(for message: ChatMessage) -> URL? {
        let url = FileManager.default.temporaryDirectory
            .appendingPathComponent("demo-music-\(message.id.uuidString).wav")
        if FileManager.default.fileExists(atPath: url.path) {
            return url
        }

        let sampleRate: UInt32 = 22_050
        let duration: Double = 16.0
        let sampleCount = Int(Double(sampleRate) * duration)
        let melody = [261.63, 329.63, 392.00, 523.25, 392.00, 329.63, 293.66, 349.23]
        var pcm = Data()

        for sampleIndex in 0..<sampleCount {
            let t = Double(sampleIndex) / Double(sampleRate)
            let beat = Int(t * 2.0) % melody.count
            let note = melody[beat]
            let envelope = min(1.0, Double(sampleIndex) / 4_000.0)
                * min(1.0, Double(sampleCount - sampleIndex) / 4_000.0)
            let value = (
                sin(2.0 * .pi * note * t) * 0.22
                + sin(2.0 * .pi * note * 2.0 * t) * 0.08
                + sin(2.0 * .pi * 110.0 * t) * 0.10
            ) * envelope
            pcm.appendInt16LE(Int16(max(-0.9, min(0.9, value)) * Double(Int16.max)))
        }

        var wav = Data()
        wav.append(Data("RIFF".utf8))
        wav.appendUInt32LE(UInt32(36 + pcm.count))
        wav.append(Data("WAVE".utf8))
        wav.append(Data("fmt ".utf8))
        wav.appendUInt32LE(16)
        wav.appendUInt16LE(1)
        wav.appendUInt16LE(1)
        wav.appendUInt32LE(sampleRate)
        wav.appendUInt32LE(sampleRate * 2)
        wav.appendUInt16LE(2)
        wav.appendUInt16LE(16)
        wav.append(Data("data".utf8))
        wav.appendUInt32LE(UInt32(pcm.count))
        wav.append(pcm)

        do {
            try wav.write(to: url, options: .atomic)
            return url
        } catch {
            return nil
        }
    }

    func presentLocationPicker(messageType: ChatMessageType = .location, seedMessage: ChatMessage? = nil) {
        let controller = LocationPickerViewController(
            messageType: messageType,
            initialQuery: seedMessage?.body
        )
        controller.onPickLocation = { [weak self] pickedLocation, pickedType in
            self?.insertOutgoingMessage(
                type: pickedType,
                body: pickedLocation.title,
                detail: pickedLocation.address
            )
        }

        let navigationController = UINavigationController(rootViewController: controller)
        present(navigationController, animated: true)
    }

    func startTiltQRCodeDetection() {
        guard !motionManager.isDeviceMotionActive,
              motionManager.isDeviceMotionAvailable
        else { return }
        motionManager.deviceMotionUpdateInterval = 0.12
        motionManager.startDeviceMotionUpdates(to: .main) { [weak self] motion, _ in
            guard let self, let motion else { return }
            self.handleDeviceMotionForQRCode(motion)
        }
    }

    func stopTiltQRCodeDetection() {
        motionManager.stopDeviceMotionUpdates()
        tiltCandidateStartedAt = nil
        lastTiltDirection = 0
        isTiltQRCodeArmed = false
        tiltNeutralPitch = nil
        tiltCandidatePeakPitch = 0
    }

    func handleDeviceMotionForQRCode(_ motion: CMDeviceMotion) {
        guard presentedViewController == nil,
              windowState != .minimized,
              abs(motion.userAcceleration.x) < 0.30,
              abs(motion.userAcceleration.y) < 0.30,
              abs(motion.userAcceleration.z) < 0.30
        else {
            resetTiltQRCodeCandidate(keepArmed: true)
            return
        }

        let pitch = motion.attitude.pitch
        if abs(pitch) < 0.28 {
            isTiltQRCodeArmed = true
            tiltNeutralPitch = pitch
            resetTiltQRCodeCandidate(keepArmed: true)
            return
        }

        guard isTiltQRCodeArmed,
              let neutralPitch = tiltNeutralPitch
        else { return }

        let delta = pitch - neutralPitch
        let direction: Int
        if delta > 0.92 {
            direction = 1
        } else if delta < -0.92 {
            direction = -1
        } else {
            if abs(delta) < 0.46 {
                resetTiltQRCodeCandidate(keepArmed: true)
            }
            return
        }

        let now = Date()
        if lastTiltDirection != direction {
            lastTiltDirection = direction
            tiltCandidateStartedAt = now
            tiltCandidatePeakPitch = abs(delta)
            return
        }
        tiltCandidatePeakPitch = max(tiltCandidatePeakPitch, abs(delta))

        guard now.timeIntervalSince(tiltCandidateStartedAt ?? now) > 0.45,
              tiltCandidatePeakPitch > 1.05
        else { return }
        if let lastQRCodePresentationAt,
           now.timeIntervalSince(lastQRCodePresentationAt) < 3.0 {
            return
        }

        lastQRCodePresentationAt = now
        resetTiltQRCodeCandidate(keepArmed: false)
        presentTiltQRCodePage(direction: direction)
    }

    func resetTiltQRCodeCandidate(keepArmed: Bool) {
        tiltCandidateStartedAt = nil
        lastTiltDirection = 0
        tiltCandidatePeakPitch = 0
        if !keepArmed {
            isTiltQRCodeArmed = false
            tiltNeutralPitch = nil
        }
    }

    func presentTiltQRCodePage(direction: Int) {
        let account = state.currentUser
        let conversation = state.activeFriend
        let payload = [
            "app=只发",
            "account=\(account.displayName)",
            "accountId=\(account.id.uuidString)",
            "conversation=\(conversation?.displayName ?? "主页")",
            "conversationId=\(conversation?.id.uuidString ?? "home")",
            "tilt=\(direction > 0 ? "forward" : "backward")"
        ].joined(separator: "&")
        let controller = TiltQRCodeViewController(
            titleText: conversation?.displayName ?? account.displayName,
            subtitleText: direction > 0 ? "前倾打开二维码" : "后倾打开二维码",
            payload: "zhifa://qrcode?\(payload)"
        )
        let navigationController = UINavigationController(rootViewController: controller)
        navigationController.modalPresentationStyle = .fullScreen
        present(navigationController, animated: true)
    }

    func presentContactCardDesigner(accountID: UUID, initialProfile: ContactCardProfile? = nil) {
        guard let account = state.participants.first(where: { $0.id == accountID }) else { return }
        let profile = initialProfile ?? contactCardProfile(for: account)
        let canEdit = account.isCurrentUser
        let controller = ContactCardDesignerViewController(
            account: account,
            profile: profile,
            canEdit: canEdit,
            language: appLanguage
        )
        controller.onSave = { [weak self] updatedProfile in
            self?.state.contactCards[updatedProfile.accountID] = updatedProfile
            self?.rightAccountCollectionView.reloadData()
            self?.showNotice("名片已保存")
        }
        controller.onSend = { [weak self] updatedProfile in
            guard let self else { return }
            self.state.contactCards[updatedProfile.accountID] = updatedProfile
            self.state.selectedAccountID = updatedProfile.accountID
            self.insertContactCardMessage(updatedProfile)
        }

        let navigationController = UINavigationController(rootViewController: controller)
        present(navigationController, animated: true)
    }

    @objc func presentMyAccounts() {
        guard navigationController?.topViewController === self else { return }
        if openApiAccountContextsByID.isEmpty {
            if isOpenApiIMSyncing {
                showNotice("真实帐号正在同步，请稍后")
                return
            }
            showNotice("正在同步真实帐号")
            syncOpenApiIMData(force: true) { [weak self] success in
                guard success else { return }
                self?.presentMyAccounts()
            }
            return
        }
        let controller = MyAccountsViewController(
            accounts: state.currentUsers,
            contactCards: state.contactCards,
            selectedAccountID: state.selectedAccountID,
            language: appLanguage
        )
        controller.onSelectAccount = { [weak self] accountID in
            guard let self else { return }
            self.state.selectedAccountID = accountID
            self.rightAccountCollectionView.reloadData()
        }
        controller.onToggleLanguage = { [weak self, weak controller] in
            guard let self else { return }
            self.toggleAppLanguage()
            controller?.updateLanguage(self.appLanguage)
        }
        controller.onSaveProfile = { [weak self] updatedProfile in
            guard let self else { return }
            self.state.contactCards[updatedProfile.accountID] = updatedProfile
            self.rightAccountCollectionView.reloadData()
            self.showNotice("帐号资料已保存")
        }
        controller.onAddAccount = { [weak self] profile in
            guard let self else { return nil }
            let account = self.makeAccount(from: profile)
            self.state.participants.append(account)
            self.state.contactCards[account.id] = profile
            self.state.selectedAccountID = account.id
            self.updateHeaderForSelection()
            self.rightAccountCollectionView.reloadData()
            self.rightToolCollectionView.reloadData()
            self.showNotice("已添加帐号")
            return account
        }
        controller.onRemoveAccount = { [weak self] accountID in
            guard let self else { return nil }
            let currentAccounts = self.state.currentUsers
            guard currentAccounts.count > 1 else { return nil }
            self.state.participants.removeAll { $0.id == accountID && $0.isCurrentUser }
            self.state.contactCards[accountID] = nil
            if self.state.selectedAccountID == accountID {
                let fallbackID = self.state.currentUsers.first?.id ?? self.state.currentUserID
                self.state.selectedAccountID = fallbackID
            }
            self.updateHeaderForSelection()
            self.rightAccountCollectionView.reloadData()
            self.rightToolCollectionView.reloadData()
            self.updateConnections()
            self.showNotice("帐号已移除")
            return self.state.selectedAccountID
        }

        controller.loadViewIfNeeded()
        navigationItem.backBarButtonItem = UIBarButtonItem(title: localized("返回", "Back"), style: .plain, target: nil, action: nil)
        navigationController?.setNavigationBarHidden(false, animated: false)
        navigationController?.pushViewController(controller, animated: true)
    }

    func makeAccount(from profile: ContactCardProfile) -> ChatParticipant {
        let initials = String(profile.displayName.trimmingCharacters(in: .whitespacesAndNewlines).prefix(1))
        return ChatParticipant(
            id: profile.accountID,
            displayName: profile.displayName.isEmpty ? "新帐号" : profile.displayName,
            tintColor: profile.accentColor,
            initials: initials.isEmpty ? "新" : initials,
            isCurrentUser: true
        )
    }

    func contactCardProfile(for account: ChatParticipant) -> ContactCardProfile {
        state.contactCards[account.id] ?? ContactCardProfile(
            accountID: account.id,
            displayName: account.displayName,
            role: "个人名片",
            company: "",
            wechatID: "",
            phone: "",
            bio: "这是\(account.displayName)的个人名片。",
            styleIndex: 0
        )
    }

    func syncedContactCardProfile(for participant: ChatParticipant) -> ContactCardProfile {
        guard let context = openApiConversationContextsByID[participant.id] else {
            return contactCardProfile(for: participant)
        }
        let ownerName = openApiAccountContextsByID.values.first { $0.wxid == context.ownerWxid }?.nickname
            ?? context.ownerWxid
        let details = [
            context.friendNo.isEmpty ? "" : "好友号：\(context.friendNo)",
            context.remark.isEmpty ? "" : "备注：\(context.remark)",
            context.sourceChannel.isEmpty ? "" : "来源渠道：\(context.sourceChannel)",
            context.customerLevel.isEmpty ? "" : "客户等级：\(context.customerLevel)",
            context.profileKey.isEmpty ? "" : "画像键：\(context.profileKey)",
            context.notes.isEmpty ? "" : "备注说明：\(context.notes)",
            "wxid：\(context.wxid)",
            "所属帐号：\(ownerName)"
        ].filter { !$0.isEmpty }
        let profile = ContactCardProfile(
            accountID: participant.id,
            displayName: context.displayName.isEmpty ? displayName(for: participant) : context.displayName,
            role: context.kind == .chatroom ? "OpenAPI 群聊" : "OpenAPI 好友",
            company: "只发 SCRM",
            wechatID: context.friendNo,
            phone: context.phone,
            bio: details.joined(separator: "\n"),
            styleIndex: safeHashIndex("\(context.ownerWxid)-\(context.wxid)", modulo: 4)
        )
        state.contactCards[participant.id] = profile
        return profile
    }

    func presentContactCardDetail(_ profile: ContactCardProfile) {
        let controller = ContactCardDetailViewController(profile: profile)
        let navigationController = UINavigationController(rootViewController: controller)
        present(navigationController, animated: true)
    }

    func openActiveFriendInfoHomeFromGesture() {
        guard navigationController?.topViewController === self else { return }
        guard activeDirectFriendForInfoHome() != nil else {
            if state.selectedFriendID == nil {
                showNotice("请先从左侧选择一个好友，再左滑进入用户主页")
            } else if let activeFriend = state.activeFriend, isGroupConversation(activeFriend) {
                showNotice("群聊没有个人主页，请选择群里的具体好友")
            } else {
                showNotice("当前不是好友聊天，无法进入用户主页")
            }
            return
        }
        presentActiveFriendInfoHomeFromSwipe()
    }

    func presentActiveFriendInfoHomeFromSwipe() {
        guard navigationController?.topViewController === self else { return }
        guard let friend = activeDirectFriendForInfoHome() else { return }
        let latestMessage = state.latestMessage(for: friend.id)
        let ownerAccountName = ownerAccountForCurrentConversation().map { displayName(for: $0) }
            ?? displayName(for: state.currentUser)
        let profile = syncedContactCardProfile(for: friend)
        let resolvedAvatarURL = sidebarAvatarURL(for: friend.id) ?? friend.avatarURL
        let internalWxid = openApiConversationContextsByID[friend.id]?.wxid ?? ""
        let controller = FriendInfoHomeViewController(
            participant: friend,
            displayName: displayName(for: friend),
            remark: participantRemarksByID[friend.id] ?? "",
            profile: profile,
            avatarURL: resolvedAvatarURL,
            associatedRows: associatedInfoRows(for: friend, profile: profile),
            ownerAccountName: ownerAccountName,
            latestMessage: latestMessage,
            internalWxid: internalWxid
        )
        controller.navigationItem.backBarButtonItem = UIBarButtonItem(
            title: localized("返回", "Back"),
            style: .plain,
            target: nil,
            action: nil
        )
        controller.onOpenContactCard = { [weak controller] profile in
            controller?.navigationController?.pushViewController(
                ContactCardDetailViewController(
                    profile: profile,
                    avatarURL: resolvedAvatarURL,
                    internalWxid: internalWxid
                ),
                animated: true
            )
        }
        controller.onOpenCustomerProfile = { [weak self, weak controller] in
            guard let self,
                  let context = self.openApiConversationContextsByID[friend.id]
            else {
                let alert = UIAlertController(
                    title: "客户档案",
                    message: "当前联系人还没有后端 contactId，请先完成 OpenAPI 联系人同步。",
                    preferredStyle: .alert
                )
                alert.addAction(UIAlertAction(title: "知道了", style: .default))
                controller?.present(alert, animated: true)
                return
            }
            controller?.navigationController?.pushViewController(
                OpenApiCustomerProfileViewController(
                    ownerWxid: context.ownerWxid,
                    contact: friend,
                    context: context
                ),
                animated: true
            )
        }
        controller.onOpenLabelsAndPermissions = { [weak self, weak controller] in
            guard let self,
                  let context = self.openApiConversationContextsByID[friend.id],
                  !context.backendID.isEmpty,
                  !context.wxid.isEmpty
            else {
                let alert = UIAlertController(
                    title: "标签与朋友圈权限",
                    message: "当前联系人缺少后端 contactId 或好友 wxid，请先完成 OpenAPI 联系人同步。",
                    preferredStyle: .alert
                )
                alert.addAction(UIAlertAction(title: "知道了", style: .default))
                controller?.present(alert, animated: true)
                return
            }
            let deviceUUID = self.openApiAccountContextsByID.values.first(where: { $0.wxid == context.ownerWxid })?.clientUuid ?? ""
            controller?.navigationController?.pushViewController(
                OpenApiContactLabelsPermissionsViewController(
                    contactName: self.displayName(for: friend),
                    contactID: context.backendID,
                    friendID: context.wxid,
                    avatarURL: self.sidebarAvatarURL(for: friend.id) ?? friend.avatarURL,
                    account: OpenApiSocialAccount(deviceUUID: deviceUUID, weChatID: context.ownerWxid)
                ),
                animated: true
            )
        }
        navigationItem.backBarButtonItem = UIBarButtonItem(title: "返回", style: .plain, target: nil, action: nil)
        navigationController?.setNavigationBarHidden(false, animated: true)
        navigationController?.pushViewController(controller, animated: true)
    }

    func activeDirectFriendForInfoHome() -> ChatParticipant? {
        guard let friend = state.activeFriend,
              !friend.isCurrentUser,
              !friend.isAIAccount,
              !isGroupParticipant(friend)
        else { return nil }
        return friend
    }

    func associatedInfoRows(for participant: ChatParticipant, profile: ContactCardProfile) -> [(String, String)] {
        guard let context = openApiConversationContextsByID[participant.id] else {
            return []
        }
        let rows: [(String, String)] = [
            ("好友号", context.friendNo),
            ("备注", context.remark),
            ("来源", context.source),
            ("来源详情", context.sourceExt),
            ("来源渠道", context.sourceChannel),
            ("客户等级", context.customerLevel),
            ("画像键", context.profileKey),
            ("资料备注", context.notes)
        ]
        return rows
            .map { ($0.0, $0.1.trimmingCharacters(in: .whitespacesAndNewlines)) }
            .filter { !$0.1.isEmpty && $0.1 != profile.wechatID && $0.1 != profile.phone }
    }

    func handleInlineCardTap(_ card: InlineCardInteraction) {
        switch card.kind {
        case .enterprise:
            let url = normalizedURL(from: card.url) ?? URL(string: "https://cc2.cx")!
            let controller = RichContentDetailViewController(
                mode: .webLink(url: url),
                titleText: card.title,
                subtitleText: "企业微信 · \(card.subtitle)",
                detailText: "企微名片\n\(card.subtitle)\n\(url.absoluteString)\n\n可查看企业成员信息、添加客户或跳转到名片链接。"
            )
            controller.onPrimaryAction = { [weak self] in
                self?.presentLinkJumpConfirmation(urlText: url.absoluteString)
            }
            pushDetailController(controller)
        case .personal:
            presentContactCardDetail(profileForInlinePersonalCard(card))
        case .officialAccount:
            let controller = RichContentDetailViewController(
                mode: .article,
                titleText: card.title,
                subtitleText: "公众号 · \(card.subtitle)",
                detailText: "公众号名片\n\(card.subtitle)\n\(card.url)\n\n可查看公众号资料、最近文章和关注状态。"
            )
            controller.onPrimaryAction = { [weak self] in
                guard let self,
                      let url = self.normalizedURL(from: card.url)
                else { return }
                self.openFirstLaunchCandidate(
                    [ExternalLaunchCandidate(appName: "微信", url: url, requiresInstalledApp: false)],
                    fallbackAppName: "微信"
                )
            }
            pushDetailController(controller)
        case .miniProgramProfile, .miniProgramLink:
            openMiniProgramCard(inlineMiniProgramMessage(from: card))
        case .channelsProfile:
            let controller = RichContentDetailViewController(
                mode: .live,
                titleText: card.title,
                subtitleText: "视频号 · \(card.subtitle)",
                detailText: "视频号名片\n\(card.subtitle)\n\(card.url)\n\n可查看主页、视频内容和直播入口。"
            )
            controller.onPrimaryAction = { [weak self] in
                self?.showNotice("已进入视频号主页：\(card.title)")
            }
            pushDetailController(controller)
        }
    }

    func handleRichMediaInteraction(_ interaction: RichMediaInteraction) {
        switch interaction.action {
        case .recognizeImage:
            showMessageActionNotice("识图结果：\(interaction.title)\n识别到活动海报、门店场景和可访问水印链接。\n\(interaction.url)")
        case .findObject:
            showMessageActionNotice("找物结果：\(interaction.title)\n已模拟定位图片中的门店招牌、物料箱和入口位置。")
        case .openLink:
            presentLinkJumpConfirmation(urlText: interaction.url)
        case .preview:
            presentRichMediaActionSheet(interaction)
        }
    }

    func presentRichMediaActionSheet(_ interaction: RichMediaInteraction) {
        if interaction.kind == .image {
            presentRichMediaDetail(interaction, authorized: true)
            return
        }
        let typeTitle = interaction.kind == .image ? "图片" : "文件"
        let alert = UIAlertController(
            title: "\(typeTitle)：\(interaction.title)",
            message: "\(interaction.preview)\n链接：\(interaction.url)",
            preferredStyle: .actionSheet
        )
        alert.addAction(UIAlertAction(title: interaction.kind == .image ? "查看图片" : "预览文件内容", style: .default) { [weak self] _ in
            self?.presentRichMediaDetail(interaction, authorized: true)
        })
        if interaction.kind == .image {
            alert.addAction(UIAlertAction(title: "识图", style: .default) { [weak self] _ in
                self?.handleRichMediaInteraction(RichMediaInteraction(
                    kind: interaction.kind,
                    action: .recognizeImage,
                    title: interaction.title,
                    url: interaction.url,
                    access: interaction.access,
                    preview: interaction.preview
                ))
            })
            alert.addAction(UIAlertAction(title: "找物", style: .default) { [weak self] _ in
                self?.handleRichMediaInteraction(RichMediaInteraction(
                    kind: interaction.kind,
                    action: .findObject,
                    title: interaction.title,
                    url: interaction.url,
                    access: interaction.access,
                    preview: interaction.preview
                ))
            })
        }
        alert.addAction(UIAlertAction(title: "打开链接", style: .default) { [weak self] _ in
            self?.presentLinkJumpConfirmation(urlText: interaction.url)
        })
        alert.addAction(UIAlertAction(title: "取消", style: .cancel))
        presentSheet(alert, sourceView: messageCollectionView)
    }

    func submitLinkAccessRequest(title: String, urlText: String, scope: BubbleAccessScope = .recipients) {
        guard let url = normalizedURL(from: urlText) else {
            showMessageActionNotice("链接格式无效，无法申请访问。")
            return
        }
        let key = url.absoluteString
        if let request = mediaAccessRequests.first(where: { $0.key == key }) {
            let message = request.status == .approved
                ? "访问已通过，可打开链接：\(key)"
                : "访问申请已发送，请在右侧工具「申请审核」中模拟同意。"
            showMessageActionNotice(message)
            return
        }
        let policy = resourceAccessPolicy(for: key, scope: scope)
        let request = MediaAccessRequest(
            id: UUID(),
            key: key,
            title: title,
            url: key,
            kindTitle: "链接",
            scope: scope,
            shareURL: policy.shareURL,
            expiresAt: policy.expiresAt,
            requesterName: state.currentUser.displayName,
            createdAt: Date(),
            status: .pending
        )
        mediaAccessRequests.insert(request, at: 0)
        saveAccessRequests()
        showMessageActionNotice("访问申请已发送\n发布者同意后可访问：\(key)\n请到右侧工具「申请审核」处理。")
        rightToolCollectionView.reloadData()
    }

    func submitMediaAccessRequest(interaction: RichMediaInteraction) {
        if interaction.kind == .image {
            presentRichMediaDetail(interaction, authorized: true)
            return
        }
        let key = interaction.url
        if let index = mediaAccessRequests.firstIndex(where: { $0.key == key }) {
            let request = mediaAccessRequests[index]
            let message = request.status == .approved
                ? "发布者已同意，可查看：\(interaction.title)"
                : "申请已发送，请在右侧工具「申请审核」中模拟同意。"
            showMessageActionNotice(message)
            return
        }

        let policy = resourceAccessPolicy(for: interaction.url, scope: interaction.access)
        let request = MediaAccessRequest(
            id: UUID(),
            key: key,
            title: interaction.title,
            url: interaction.url,
            kindTitle: interaction.kind == .image ? "图片" : "文件",
            scope: interaction.access,
            shareURL: policy.shareURL,
            expiresAt: policy.expiresAt,
            requesterName: state.currentUser.displayName,
            createdAt: Date(),
            status: .pending
        )
        mediaAccessRequests.insert(request, at: 0)
        saveAccessRequests()
        showMessageActionNotice("申请已发送\n发布者同意后可查看：\(interaction.title)\n请到右侧工具「申请审核」处理。")
        rightToolCollectionView.reloadData()
    }

    func submitMediaAccessRequest(item: ChatMediaPreviewItem) {
        guard item.accessScope != .public else {
            showMessageActionNotice("当前\(item.kindTitle)为公开内容，可直接查看。")
            return
        }
        if let index = mediaAccessRequests.firstIndex(where: { $0.key == item.accessKey }) {
            let request = mediaAccessRequests[index]
            let message = request.status == .approved
                ? "发布者已同意，可查看：\(item.title)"
                : "申请已发送，请到「申请审核」中模拟同意。"
            showMessageActionNotice(message)
            return
        }
        let policy = resourceAccessPolicy(for: item.sourceURLText, scope: item.accessScope)
        let request = MediaAccessRequest(
            id: UUID(),
            key: item.accessKey,
            title: item.title,
            url: item.sourceURLText,
            kindTitle: item.kindTitle,
            scope: item.accessScope,
            shareURL: policy.shareURL,
            expiresAt: policy.expiresAt,
            requesterName: state.currentUser.displayName,
            createdAt: Date(),
            status: .pending
        )
        mediaAccessRequests.insert(request, at: 0)
        saveAccessRequests()
        showMessageActionNotice("申请已发送\n发布者同意后可查看：\(item.title)\n请到右侧工具「申请审核」处理。")
        rightToolCollectionView.reloadData()
    }

    func mediaAccessState(for key: String, scope: BubbleAccessScope) -> MediaAccessState {
        guard scope != .public else { return .viewable }
        guard let request = mediaAccessRequests.first(where: { $0.key == key }) else {
            return .requestable
        }
        return request.status == .approved ? .approved : .requested
    }

    func shouldBlurMediaMessage(_ message: ChatMessage) -> Bool {
        false
    }

    func isProtectedMediaMessage(_ message: ChatMessage) -> Bool {
        switch message.type {
        case .image, .capturedPhoto, .video, .channelsVideo, .stickerGif:
            return true
        default:
            return false
        }
    }

    func approveMediaAccessRequest(key: String) {
        if let index = mediaAccessRequests.firstIndex(where: { $0.key == key }) {
            mediaAccessRequests[index].status = .approved
            saveAccessRequests()
        }
        messageCollectionView.reloadData()
    }

    func presentAccessRequestReviewPage() {
        let controller = AccessRequestReviewViewController(requests: mediaAccessRequests)
        controller.onApprove = { [weak self] key in
            guard let self else { return }
            self.approveMediaAccessRequest(key: key)
            controller.updateRequests(self.mediaAccessRequests)
            self.messageCollectionView.reloadData()
            self.showNotice("已同意查看申请")
        }
        pushDetailController(controller)
    }

    func presentHiddenUsersPage() {
        let controller = HiddenUsersViewController(
            participants: hiddenParticipants(),
            remarks: participantRemarksByID,
            groupIDs: Set(state.friends.filter(isGroupConversation).map(\.id))
        )
        controller.onRestore = { [weak self, weak controller] participantID in
            guard let self else { return }
            self.hiddenParticipantIDs.remove(participantID)
            controller?.update(participants: self.hiddenParticipants(), remarks: self.participantRemarksByID)
            self.refreshParticipantDisplay()
            self.rightToolCollectionView.reloadData()
        }
        pushDetailController(controller)
    }

    func hiddenParticipants() -> [ChatParticipant] {
        state.participants
            .filter { hiddenParticipantIDs.contains($0.id) }
            .sorted { displayName(for: $0).localizedStandardCompare(displayName(for: $1)) == .orderedAscending }
    }

}
