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


extension ChatWindowViewController: UICollectionViewDelegateFlowLayout {
    func collectionView(
        _ collectionView: UICollectionView,
        targetIndexPathForMoveFromItemAt originalIndexPath: IndexPath,
        toProposedIndexPath proposedIndexPath: IndexPath
    ) -> IndexPath {
        guard collectionView === rightToolCollectionView,
              canReorderRightTool(at: proposedIndexPath)
        else { return originalIndexPath }
        return proposedIndexPath
    }

    func collectionView(_ collectionView: UICollectionView, didSelectItemAt indexPath: IndexPath) {
        if collectionView === leftCollectionView {
            cancelLeftScrollPreview()
            guard let item = visibleLeftItemsForSelectedAccount()[safe: indexPath.item] else { return }
            if isHomeTimeline, let participantID = item.participantID {
                let presentation = pendingReplyHomePresentation()
                let unansweredItem = presentation.item(forMessageID: item.targetMessageID)
                if let unansweredItem {
                    let scope = chatWindowRoute.unansweredScope ?? .all
                    let targetAccountIDs = Set(unansweredItem.targetAccountIDs)
                    let accountID = scope.accountID
                        ?? (targetAccountIDs.contains(state.selectedAccountID)
                            ? state.selectedAccountID
                            : state.currentUsers.lazy.map(\.id).first(where: targetAccountIDs.contains))
                    if let accountID {
                        let orderedItemIDs = presentation.items.map(\.id)
                        let sourceIndex = orderedItemIDs.firstIndex(of: unansweredItem.id) ?? 0
                        chatWindowRoute = UnansweredRoutePlanner.conversation(
                            scope: scope,
                            item: unansweredItem,
                            messageID: unansweredItem.representativeMessageID,
                            accountID: accountID,
                            candidateItemIDs: Array(orderedItemIDs.dropFirst(sourceIndex + 1))
                                + Array(orderedItemIDs.prefix(sourceIndex)),
                            contentOffsetY: messageCollectionView.contentOffset.y,
                            completesItemOnSend: false
                        )
                        state.selectedAccountID = accountID
                    }
                } else if let unansweredAccountFilterID {
                    state.selectedAccountID = unansweredAccountFilterID
                }
            }
            if state.selectedFriendID == item.participantID,
               activeDirectFriendForInfoHome() != nil {
                collectionView.deselectItem(at: indexPath, animated: false)
                presentActiveFriendInfoHomeFromSwipe()
                return
            }
            selectFriend(id: item.participantID)
        } else if collectionView === rightAccountCollectionView {
            cancelLeftScrollPreview()
            if handleRightAccountBootstrapSelectionIfNeeded() {
                return
            }
            guard let item = visibleRightAccountItems()[safe: indexPath.item] else { return }
            if let accountID = item.participantID {
                selectRightAccount(accountID: accountID)
            }
        } else if collectionView === rightToolCollectionView {
            cancelLeftScrollPreview()
            guard let item = visibleRightToolItems()[safe: indexPath.item] else { return }
            collectionView.deselectItem(at: indexPath, animated: true)
            guard let action = ToolActionRouter.rightToolAction(
                for: item,
                groupInfoTitle: Self.groupInfoToolTitle,
                isGroupInfoAvailable: state.activeFriend.map(isGroupConversation) ?? false
            ) else { return }
            executeRightToolAction(action, sourceView: collectionView.cellForItem(at: indexPath) ?? rightToolCollectionView)
        } else if collectionView === messageCollectionView {
            guard !isUnreadFoldExpansionInProgress else {
                collectionView.deselectItem(at: indexPath, animated: false)
                return
            }
            guard !isMessageTapSuppressed else {
                collectionView.deselectItem(at: indexPath, animated: false)
                return
            }
            guard let message = renderedMessage(at: indexPath) else { return }
            if isHomeTimeline {
                if isAutoReplyPrediction(message) {
                    presentAutoReplyPredictionActions(for: message)
                    return
                }
                let presentation = pendingReplyHomePresentation()
                guard let item = unansweredItemForDisplayedMessage(
                    message,
                    presentation: presentation
                ) else { return }
                let scope = chatWindowRoute.unansweredScope ?? .all
                let accountID = scope.accountID
                    ?? state.currentUsers.lazy.map(\.id)
                        .first(where: Set(item.targetAccountIDs).contains)
                guard let accountID else {
                    showNotice("无法确定该事项的收件帐号")
                    return
                }
                if selectedUnansweredItemID != item.id {
                    state.selectedAccountID = accountID
                    setUnansweredTimelineRoute(
                        scope: scope,
                        focus: UnansweredTimelineFocus(itemID: item.id, messageID: message.id)
                    )
                    updateHeaderForSelection()
                    updateComposerAvailability()
                    messageCollectionView.reloadData()
                    scheduleConnectionUpdate()
                    DispatchQueue.main.async { [weak self] in
                        guard let self,
                              let accountIndex = self.visibleRightAccountItems().firstIndex(where: {
                                  $0.participantID == accountID
                              })
                        else { return }
                        let accountIndexPath = IndexPath(item: accountIndex, section: 0)
                        self.rightAccountCollectionView.scrollToItem(
                            at: accountIndexPath,
                            at: .centeredVertically,
                            animated: true
                        )
                        self.pulseSidebarItem(
                            in: self.rightAccountCollectionView,
                            at: accountIndexPath
                        )
                        self.scheduleConnectionUpdate()
                    }
                    return
                }
                let orderedItemIDs = presentation.items.map(\.id)
                let itemIndex = orderedItemIDs.firstIndex(of: item.id) ?? 0
                let followingIDs = Array(orderedItemIDs.dropFirst(itemIndex + 1))
                let precedingIDs = Array(orderedItemIDs.prefix(itemIndex))
                chatWindowRoute = UnansweredRoutePlanner.conversation(
                    scope: scope,
                    item: item,
                    messageID: message.id,
                    accountID: accountID,
                    candidateItemIDs: followingIDs + precedingIDs,
                    contentOffsetY: messageCollectionView.contentOffset.y
                )
                state.selectedAccountID = accountID
                selectFriend(id: message.conversationID, focusMessageID: message.id)
                return
            }
            if isMultiSelecting {
                toggleSelectedMessage(message)
            } else {
                let point = lastMessageTapLocation ?? collectionView.convert(collectionView.center, from: collectionView.superview)
                lastMessageTapLocation = nil
                if let cell = collectionView.cellForItem(at: indexPath) as? ChatMessageCell {
                    let pointInCell = collectionView.convert(point, to: cell)
                    if let media = cell.detectedRichMedia(at: pointInCell) {
                        handleRichMediaInteraction(media)
                    } else if let card = cell.detectedInlineCard(at: pointInCell) {
                        handleInlineCardTap(card)
                    } else if let link = cell.detectedLink(at: pointInCell) {
                        presentLinkJumpConfirmation(urlText: link)
                    } else {
                        handleMessageTap(message)
                    }
                } else {
                    handleMessageTap(message)
                }
            }
        }
    }

    func executeRightToolAction(_ action: RightToolAction, sourceView: UIView) {
        switch action {
        case .groupInfo:
            if let group = state.activeFriend, isGroupConversation(group) {
                presentGroupInfoPage(for: group)
            }
        case .accessReview:
            presentAccessRequestReviewPage()
        case .moments:
            presentMomentsPage()
        case .autoReply:
            presentAutoReplyConfigurationPage()
        case .hiddenUsers:
            presentHiddenUsersPage()
        case .leftSidebarDisplay:
            presentLeftSidebarDisplayModeSheet(sourceView: sourceView)
        case .sideEffect:
            presentSideEffectConfigurationPage()
        case .bubble3D:
            toggleBubble3DAppearance()
        case .sendName:
            presentSendNameSetting(sourceView: sourceView)
        case .paymentStatus:
            presentPaymentStatusPage()
        case .finderPublish:
            presentFinderPublishPage()
        case .customerProfile:
            presentCustomerProfilePage()
        case .contactRelations:
            presentContactRelationsPage()
        case .momentMaterials:
            presentMomentMaterialsPage()
        case .wechatMiniProgramShare:
            guard requireActiveConversationForSending() else { return }
            sendWechatMiniProgramShareDemo()
        case .blinkVoice:
            presentBlinkVoiceTest()
        case .voiceAssistant:
            let isVisible = AIVoiceAssistantOverlayCoordinator.shared.toggleFromTool()
            showMessageActionNotice(isVisible ? "语音助手已打开" : "语音助手已隐藏")
        case .selectionAssist:
            toggleSelectionAssistOverlay(sourceView: sourceView)
        case .backgroundRemoval:
            presentBackgroundRemovalConfigurationPage()
        case .uiOperationLab:
            presentUIOperationLabPage()
        case .openApiEnvironment:
            pushOpenApiDemoPage(OpenApiEnvironmentViewController())
        case .openApiBusiness:
            presentOpenApiBusinessPage()
        case .openApiFriends:
            presentOpenApiFriendManagementPage()
        case .syncChatrooms:
            syncOpenApiChatroomsFromTool()
        case .messageTool(let type, let requiresActiveConversation):
            if requiresActiveConversation {
                guard requireActiveConversationForSending() else { return }
            }
            handleTool(type)
        }
    }

    func collectionView(
        _ collectionView: UICollectionView,
        contextMenuConfigurationForItemAt indexPath: IndexPath,
        point: CGPoint
    ) -> UIContextMenuConfiguration? {
        guard collectionView === messageCollectionView,
              let message = renderedMessage(at: indexPath)
        else { return nil }

        guard canShowContextMenu(for: message) else { return nil }
        return UIContextMenuConfiguration(identifier: message.id as NSCopying, previewProvider: nil) { [weak self] _ in
            guard let self else { return UIMenu(children: []) }
            var actions: [UIAction] = []

            if self.isPureTextMessage(message) {
                actions.append(
                    UIAction(title: "复制", image: UIImage(systemName: "doc.on.doc")) { _ in
                        UIPasteboard.general.string = message.body
                        self.showNotice("已复制")
                    }
                )
                actions.append(
                    UIAction(title: "快捷语", image: UIImage(systemName: "text.badge.plus")) { _ in
                        self.presentQuickPhraseSheet(for: message.body)
                    }
                )
            }

            actions.append(
                UIAction(title: "引用", image: UIImage(systemName: "quote.bubble")) { _ in
                    self.setQuotedMessage(message)
                }
            )
            actions.append(
                UIAction(title: "收藏", image: UIImage(systemName: "star")) { _ in
                    self.favoriteMessage(message)
                }
            )
            if self.canForwardMessage(message) {
                actions.append(
                    UIAction(title: "转发", image: UIImage(systemName: "arrowshape.turn.up.right")) { _ in
                        self.presentForwardTargetPicker(mode: .individual, messages: [message])
                    }
                )
            }
            if self.latestOpenApiSendState(for: message) == .failed {
                actions.append(
                    UIAction(title: "重试真实发送", image: UIImage(systemName: "arrow.clockwise.circle")) { _ in
                        self.retryFailedOpenApiDelivery(for: message)
                    }
                )
            }
            actions.append(
                UIAction(title: "多选", image: UIImage(systemName: "checkmark.circle")) { _ in
                    self.setMultiSelecting(true, initialMessage: message)
                }
            )
            actions.append(
                UIAction(title: "删除", image: UIImage(systemName: "trash"), attributes: [.destructive]) { _ in
                    self.deleteMessage(message)
                }
            )

            if self.canShowRecallAction(for: message) {
                let isRecallAvailable = self.canRecall(message)
                let title = isRecallAvailable ? "撤回" : "已超过 2 分钟，不能撤回"
                actions.append(
                    UIAction(
                        title: title,
                        image: UIImage(systemName: isRecallAvailable ? "arrow.uturn.backward" : "clock.badge.exclamationmark"),
                        attributes: isRecallAvailable ? [.destructive] : [.disabled]
                    ) { _ in
                        self.recallMessage(message)
                    }
                )
            }

            return UIMenu(children: actions)
        }
    }

    func isPureTextMessage(_ message: ChatMessage) -> Bool {
        message.type == .text && message.attachmentURL == nil && message.richElements.isEmpty
    }

    func canShowContextMenu(for message: ChatMessage) -> Bool {
        message.type != .system && message.type != .groupNotice
    }

    func retryFailedOpenApiDelivery(for message: ChatMessage) {
        if usesAutoReplyDraftRetryFlow(for: message) {
            sendAutoReplyPrediction(message, allowsOfflineRecovery: false)
        } else {
            retryOpenApiSend(for: message)
        }
    }

    func usesAutoReplyDraftRetryFlow(for message: ChatMessage) -> Bool {
        isAutoReplyPrediction(message) || message.isAI
    }

    func canForwardMessage(_ message: ChatMessage) -> Bool {
        switch message.type {
        case .system, .groupNotice,
             .voice, .voiceCall, .videoCall,
             .liveLocation,
             .redPacket, .transfer, .splitBill:
            return false
        default:
            return true
        }
    }

    func canShowRecallAction(for message: ChatMessage) -> Bool {
        message.isOutgoing && message.type != .system
    }

    func canRecall(_ message: ChatMessage) -> Bool {
        canShowRecallAction(for: message)
            && Date().timeIntervalSince(message.sentAt) <= Self.recallWindow
    }

    func scrollViewDidScroll(_ scrollView: UIScrollView) {
        let isUnreadTimelineRail = isHomeTimeline
            && (scrollView === leftCollectionView || scrollView === messageCollectionView)
        if isUnreadTimelineRail,
           isUnreadFoldExpansionInProgress,
           let lockedOffsetY = unreadFoldLockedContentOffsetY {
            if abs(scrollView.contentOffset.y - lockedOffsetY) > 0.1 {
                isSynchronizingUnreadScroll = true
                scrollView.setContentOffset(
                    CGPoint(x: scrollView.contentOffset.x, y: lockedOffsetY),
                    animated: false
                )
                let pairedScrollView = scrollView === messageCollectionView
                    ? leftCollectionView
                    : messageCollectionView
                pairedScrollView.setContentOffset(
                    CGPoint(x: pairedScrollView.contentOffset.x, y: lockedOffsetY),
                    animated: false
                )
                isSynchronizingUnreadScroll = false
            }
            return
        }
        if isUnreadTimelineRail, isSynchronizingUnreadScroll {
            return
        }
        if isUnreadTimelineRail {
            syncUnreadTimelineScroll(from: scrollView)
        }
        if scrollView === leftCollectionView {
            scheduleLeftScrollPerformanceUpdate(
                preview: scrollView.isDragging || scrollView.isDecelerating || isLeftScrollPreviewGestureActive(),
                floating: true,
                avatarPrefetch: true,
                connection: false
            )
            if isHomeTimeline {
                loadOlderHomeMessagesIfNeeded()
            }
            scheduleConnectionUpdate()
            return
        }
        let didResizeRightRail = updateRightRailHeightForScroll(scrollView)
        if scrollView === messageCollectionView {
            if isOrdinarySingleConversation {
                setMessageHeaderCompact(true)
                headerCompactResetWorkItem?.cancel()
            }
            if isHomeTimeline {
                scheduleLeftScrollPerformanceUpdate(
                    preview: false,
                    floating: true,
                    avatarPrefetch: true,
                    connection: false
                )
            }
            loadOlderHomeMessagesIfNeeded()
            loadOlderOpenApiHistoryIfNeeded()
        }
        if !didResizeRightRail {
            scheduleConnectionUpdate()
        }
    }

    func scrollViewWillBeginDragging(_ scrollView: UIScrollView) {
        dismissComposerInput(animated: true)
        if scrollView === leftCollectionView {
            updateSelectedLeftSidebarFloatingItem(animated: false)
            prefetchVisibleSidebarAvatarWindow(in: leftCollectionView, extraItems: 20)
            updateLeftScrollPreview()
            scheduleLeftScrollPerformanceUpdate(preview: false, floating: true, avatarPrefetch: false, connection: false)
            scheduleConnectionUpdate()
            return
        }
        if scrollView === rightAccountCollectionView || scrollView === rightToolCollectionView {
            rightAccountHeightRatioAtScrollStart = rightAccountHeightRatio
            rightRailContentOffsetAtScrollStart = scrollView.contentOffset.y
            aiffButton.backgroundColor = UIColor(red: 0.47, green: 0.83, blue: 0.08, alpha: 0.28)
        }
    }

    func scrollViewDidEndDragging(_ scrollView: UIScrollView, willDecelerate decelerate: Bool) {
        if scrollView === leftCollectionView {
            updateSelectedLeftSidebarFloatingItem(animated: true)
            if !decelerate {
                stopLeftScrollPerformanceUpdates()
                hideLeftScrollPreview(animated: true)
                scheduleConnectionUpdate()
            } else {
                scheduleLeftScrollPerformanceUpdate(preview: true, floating: true, avatarPrefetch: true, connection: false)
            }
            return
        }
        if scrollView === messageCollectionView {
            scheduleMessageHeaderRestore()
        }
        guard scrollView === rightAccountCollectionView || scrollView === rightToolCollectionView else { return }
        setRightAccountHeightRatio(rightAccountHeightRatio, animated: true)
        if !decelerate {
            aiffButton.backgroundColor = UIColor.white.withAlphaComponent(0.18)
        }
    }

    func scrollViewDidEndDecelerating(_ scrollView: UIScrollView) {
        if scrollView === leftCollectionView {
            updateSelectedLeftSidebarFloatingItem(animated: true)
            stopLeftScrollPerformanceUpdates()
            hideLeftScrollPreview(animated: true)
            scheduleConnectionUpdate()
            return
        }
        if scrollView === rightAccountCollectionView || scrollView === rightToolCollectionView {
            aiffButton.backgroundColor = UIColor.white.withAlphaComponent(0.18)
        }
        if scrollView === messageCollectionView {
            scheduleMessageHeaderRestore()
        }
    }

    func collectionView(
        _ collectionView: UICollectionView,
        layout collectionViewLayout: UICollectionViewLayout,
        sizeForItemAt indexPath: IndexPath
    ) -> CGSize {
        if collectionView === leftCollectionView {
            let baseSize = safeSidebarItemSize(for: collectionView, preferred: Self.sidebarItemSize)
            guard isHomeTimeline,
                  let participantID = visibleLeftItemsForSelectedAccount()[safe: indexPath.item]?.participantID
            else { return baseSize }

            guard let snapshot = unreadTimelineLayoutSnapshot(),
                  let groupIndex = snapshot.groupIndexByParticipantID[participantID],
                  let group = snapshot.groups[safe: groupIndex]
            else { return baseSize }
            return CGSize(width: baseSize.width, height: group.frame.height)
        }
        if collectionView === rightToolCollectionView {
            return safeSidebarItemSize(for: collectionView, preferred: Self.sidebarItemSize)
        }
        if collectionView === rightAccountCollectionView {
            return safeSidebarItemSize(for: collectionView, preferred: Self.rightAccountItemSize)
        }

        guard let message = renderedMessage(at: indexPath) else {
            return CGSize(width: collectionView.bounds.width, height: 1)
        }
        if isHomeTimeline,
           let snapshot = unreadTimelineLayoutSnapshot(),
           let frame = snapshot.messageFrames[safe: indexPath.item] {
            return frame.size
        }
        let groupSettings = groupDisplaySettings(for: message)
        let height = cachedHeight(
            for: message,
            width: collectionView.bounds.width,
            showsGroupAvatar: groupSettings.showsAvatar,
            showsGroupName: groupSettings.showsName
        ) + unreadTimelineMessageTopSpacing(at: indexPath.item)
        return CGSize(width: collectionView.bounds.width, height: height)
    }
}

extension ChatWindowViewController {
    func syncUnreadTimelineScroll(from source: UIScrollView) {
        guard isHomeTimeline,
              !isSynchronizingUnreadScroll,
              leftCollectionView.bounds.height > 0,
              messageCollectionView.bounds.height > 0
        else { return }
        isSynchronizingUnreadScroll = true
        defer { isSynchronizingUnreadScroll = false }
        // A single-account unread timeline should preserve the shared elastic
        // pull-down offset. Otherwise message cells bounce while avatars stay
        // clamped at zero and visibly detach from their groups.
        let y = unansweredAccountFilterID == nil
            ? max(0, source.contentOffset.y)
            : source.contentOffset.y
        if source !== leftCollectionView,
           abs(leftCollectionView.contentOffset.y - y) > 0.5 {
            leftCollectionView.setContentOffset(CGPoint(x: 0, y: y), animated: false)
        }
        if source !== messageCollectionView,
           abs(messageCollectionView.contentOffset.y - y) > 0.5 {
            messageCollectionView.setContentOffset(CGPoint(x: 0, y: y), animated: false)
        }
    }
}


extension ChatWindowViewController {
    func openApiBatchPayload(from object: Any?) -> [String: Any] {
        guard let root = object as? [String: Any] else { return [:] }
        for key in ["data", "result", "payload"] {
            if let nested = root[key] as? [String: Any] {
                return root.merging(nested) { _, nestedValue in nestedValue }
            }
        }
        return root
    }

    func openApiBatchItems(from object: Any?) -> [[String: Any]] {
        if let items = object as? [[String: Any]] {
            return items
        }
        guard let root = object as? [String: Any] else { return [] }
        for key in ["items", "records", "rows", "list"] {
            if let items = root[key] as? [[String: Any]] {
                return items
            }
        }
        for key in ["data", "result", "payload"] {
            let items = openApiBatchItems(from: root[key])
            if !items.isEmpty {
                return items
            }
        }
        return []
    }

    func openApiBatchStatusLooksPending(_ item: [String: Any]) -> Bool {
        let statusText = [
            openApiFirstString(in: item, keys: ["status", "state", "taskStatus"]),
            openApiFirstString(in: item, keys: ["message", "resultCode", "code"])
        ].joined(separator: " ").lowercased()
        let pendingTerms = ["pending", "running", "processing", "queued", "created", "accepted", "ready", "wait", "处理中", "排队", "等待", "已提交", "已创建", "受理"]
        return pendingTerms.contains { statusText.contains($0) }
    }

    func openApiBatchStatusLooksSucceeded(_ item: [String: Any]) -> Bool {
        let statusText = [
            openApiFirstString(in: item, keys: ["status", "state", "taskStatus"]),
            openApiFirstString(in: item, keys: ["message", "resultCode", "code"])
        ].joined(separator: " ").lowercased()
        let successTerms = ["success", "succeed", "succeeded", "done", "completed", "finished", "sent", "已完成", "发送成功", "成功"]
        return successTerms.contains { statusText.contains($0) }
    }

    func uniqueOpenApiTaskIDs(_ taskIDs: [String]) -> [String] {
        taskIDs.reduce(into: [String]()) { result, taskID in
            let cleaned = taskID.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !cleaned.isEmpty, !result.contains(cleaned) else { return }
            result.append(cleaned)
        }
    }

    func openApiFriendlyTaskFailureMessage(_ message: String, action: String) -> String {
        let lowercased = message.lowercased()
        if lowercased.contains("allowchatvoicetasks=false") || lowercased.contains("preset=chat-voice") {
            return "语音文件上传成功，但后台安全态未开启聊天语音发送。请在 SCRM 后台打开 /settings?preset=chat-voice，保存并推送“聊天语音发送测试”后重试。"
        }
        if lowercased.contains("allowchatvideotasks=false") || lowercased.contains("preset=chat-video") {
            return "视频文件上传成功，但后台安全态未开启聊天视频发送。请在 SCRM 后台打开 /settings?preset=chat-video，保存并推送“聊天视频发送测试”后重试。"
        }
        return message
    }

    func awaitOpenApiTaskCompletion(taskID: String, action: String) async throws {
        var latestStateText = ""
        var latestError: Error?
        for attempt in 0..<9 {
            if attempt > 0 {
                let delay = UInt64(650_000_000 + attempt * 250_000_000)
                try await Task.sleep(nanoseconds: delay)
            }
            do {
                let result = try await OpenApiHTTPClient.request("GET", path: "/openapi/v1/tasks/\(taskID)")
                switch openApiTaskPollState(from: result.jsonObject) {
                case .succeeded:
                    return
                case .failed(let message):
                    let rawMessage = message.isEmpty ? "任务失败，taskId=\(taskID)" : message
                    throw OpenApiMediaSendError.taskFailed(
                        action,
                        openApiFriendlyTaskFailureMessage(rawMessage, action: action)
                    )
                case .pending(let message):
                    latestStateText = message
                }
            } catch let error as OpenApiMediaSendError {
                throw error
            } catch {
                latestError = error
            }
        }
        if let latestError {
            throw OpenApiMediaSendError.taskResultUnknown(
                action,
                "任务结果查询失败，taskId=\(taskID)：\(latestError.localizedDescription)"
            )
        }
        throw OpenApiMediaSendError.taskResultUnknown(
            action,
            "任务未返回终态，taskId=\(taskID)\(latestStateText.isEmpty ? "" : "，最新状态：\(latestStateText)")"
        )
    }

    func openApiTaskPollState(from object: Any?) -> OpenApiTaskPollState {
        let payload = openApiTaskPayload(from: object)
        let success = openApiBoolValue(payload["success"])
            ?? openApiBoolValue(payload["isSuccess"])
            ?? openApiBoolValue(payload["succeeded"])
        let resultUnknown = openApiBoolValue(payload["resultUnknown"]) ?? false
        let message = openApiFirstString(
            in: payload,
            keys: ["message", "error", "errorMessage", "status", "state", "resultCode", "code"]
        )
        let sentMessageID = openApiFirstString(
            in: payload,
            keys: ["msgSvrId", "MsgSvrId", "messageSvrId", "MessageSvrId", "wechatMsgId", "WechatMsgId"]
        )
        if !sentMessageID.isEmpty {
            return .succeeded(message)
        }
        let searchable = [
            message,
            openApiFirstString(in: payload, keys: ["status", "state", "taskStatus"]),
            openApiFirstString(in: payload, keys: ["resultCode", "code"])
        ].joined(separator: " ").lowercased()
        let pendingTerms = ["pending", "running", "processing", "queued", "created", "accepted", "wait", "progress", "处理中", "排队", "等待", "已提交", "已创建", "受理", "上传中"]
        if resultUnknown || pendingTerms.contains(where: searchable.contains) {
            return .pending(message)
        }
        let successTerms = ["success", "succeed", "done", "completed", "finished", "ok", "sent", "已完成", "发送成功", "成功"]
        if successTerms.contains(where: searchable.contains) || success == true {
            return .succeeded(message)
        }
        let failureTerms = [
            "fail", "error", "timeout", "cancel", "reject", "denied", "disabled", "not allowed",
            "allowchatvoicetasks=false", "allowchatvideotasks=false",
            "失败", "错误", "异常", "超时", "取消", "拒绝", "未开启", "不允许", "不可用"
        ]
        if failureTerms.contains(where: searchable.contains) {
            return .failed(message)
        }
        return .pending(message)
    }

    func openApiTaskPayload(from object: Any?) -> [String: Any] {
        guard let root = object as? [String: Any] else { return [:] }
        for key in ["data", "result", "payload", "task", "taskResult"] {
            if let nested = root[key] as? [String: Any] {
                return root.merging(nested) { _, nestedValue in nestedValue }
            }
        }
        return root
    }

    func openApiTaskID(from object: Any?) -> String? {
        if let dictionary = object as? [String: Any] {
            for key in ["taskId", "taskID", "task_id"] {
                let value = openApiStringValue(dictionary[key])
                if isValidOpenApiTaskID(value) { return value }
            }
            for key in ["taskResultUrl", "taskResultURL", "taskUrl", "resultUrl"] {
                let value = openApiStringValue(dictionary[key])
                guard !value.isEmpty, let url = OpenApiDisplay.url(from: value) else { continue }
                let last = url.pathComponents.last?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
                if isValidOpenApiTaskID(last), last != "tasks" { return last }
            }
            for key in ["data", "result", "payload", "task", "taskResult"] {
                if let nested = openApiTaskID(from: dictionary[key]) {
                    return nested
                }
            }
        }
        if let array = object as? [Any] {
            for item in array {
                if let nested = openApiTaskID(from: item) {
                    return nested
                }
            }
        }
        return nil
    }

    func isValidOpenApiTaskID(_ value: String) -> Bool {
        let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return false }
        if let number = Int64(trimmed) {
            return number > 0
        }
        return trimmed.range(of: #"^[A-Za-z0-9][A-Za-z0-9_-]*$"#, options: .regularExpression) != nil
    }

    func openApiFirstString(in dictionary: [String: Any], keys: [String]) -> String {
        for key in keys {
            let value = openApiStringValue(dictionary[key])
            if !value.isEmpty { return value }
        }
        if let data = dictionary["data"] as? [String: Any] {
            return openApiFirstString(in: data, keys: keys)
        }
        return ""
    }

    func openApiFirstStringArray(in object: Any?, keys: [String]) -> [String] {
        func strings(from value: Any?) -> [String] {
            if let array = value as? [String] {
                return array
                    .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
                    .filter { !$0.isEmpty }
            }
            if let array = value as? [Any] {
                return array
                    .map(openApiStringValue)
                    .filter { !$0.isEmpty }
            }
            let text = openApiStringValue(value)
            guard !text.isEmpty else { return [] }
            return text
                .components(separatedBy: CharacterSet(charactersIn: ",;|"))
                .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
                .filter { !$0.isEmpty }
        }

        if let dictionary = object as? [String: Any] {
            for key in keys {
                let values = strings(from: dictionary[key])
                if !values.isEmpty { return values }
            }
            for key in ["data", "result", "payload", "item"] {
                let values = openApiFirstStringArray(in: dictionary[key], keys: keys)
                if !values.isEmpty { return values }
            }
        }
        if let array = object as? [Any] {
            for item in array {
                let values = openApiFirstStringArray(in: item, keys: keys)
                if !values.isEmpty { return values }
            }
        }
        return []
    }

    func openApiStringValue(_ value: Any?) -> String {
        if let string = value as? String {
            return string.trimmingCharacters(in: .whitespacesAndNewlines)
        }
        if let number = value as? NSNumber {
            return number.stringValue
        }
        return ""
    }

    func openApiBoolValue(_ value: Any?) -> Bool? {
        if let bool = value as? Bool { return bool }
        if let number = value as? NSNumber { return number.boolValue }
        if let string = value as? String {
            switch string.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() {
            case "true", "1", "yes", "success", "succeeded", "ok": return true
            case "false", "0", "no", "fail", "failed", "error": return false
            default: return nil
            }
        }
        return nil
    }

    func openApiDiagnosticBody(_ text: String) -> String {
        let compact = text
            .replacingOccurrences(of: "\n", with: " ")
            .trimmingCharacters(in: .whitespacesAndNewlines)
        guard compact.count > 220 else { return compact.isEmpty ? "无响应体" : compact }
        return String(compact.prefix(220)) + "..."
    }

    struct OpenApiVoiceEnvironmentContext {
        let recommendedUploadFormats: [String]
        let targetFormat: String
        let ffmpegAvailable: Bool?
        let ffprobeAvailable: Bool?
        let amrEncoderAvailable: Bool?
        let amrEncoderSmokeTestPassed: Bool?
        let amrEncoderSmokeTestSummary: String
        let warnings: [String]
        let message: String
    }

    struct OpenApiVoiceUploadCandidate {
        let url: URL
        let format: String
        let label: String
    }

    struct OpenApiVoiceUploadSelection {
        let result: OpenApiHTTPResult
        let url: String
        let score: Int
        let candidate: OpenApiVoiceUploadCandidate
        let diagnostic: String
    }

    enum OpenApiVoicePlaybackProfile: Int {
        case unsupported = 0
        case wechatContainer = 1
        case silk = 2
        case amr = 3
    }

    func openApiUploadedURL(for url: URL, isVoice: Bool) async throws -> String {
        if isRemoteURL(url) {
            return url.absoluteString
        }
        let uploadResult = try await (isVoice ? OpenApiHTTPClient.uploadVoiceFile(url) : OpenApiHTTPClient.uploadMediaFile(url))
        if let root = uploadResult.jsonObject as? [String: Any],
           let success = openApiBoolValue(root["success"]),
           !success {
            let message = openApiFirstString(in: root, keys: ["message", "error", "errorMessage"])
            throw OpenApiMediaSendError.taskFailed(
                isVoice ? "上传语音" : "上传媒体",
                message.isEmpty ? "后端返回 success=false；HTTP \(uploadResult.statusCode)：\(openApiDiagnosticBody(uploadResult.rawText))" : message
            )
        }
        let uploadedURL = isVoice
            ? openApiVoiceURL(from: uploadResult.jsonObject)
            : openApiMediaURL(from: uploadResult.jsonObject)
        guard let uploadedURL,
              let normalizedURL = openApiNormalizedRemoteURLText(uploadedURL)
        else {
            throw OpenApiMediaSendError.taskResultUnknown(
                isVoice ? "上传语音" : "上传媒体",
                "未返回可发送 URL；HTTP \(uploadResult.statusCode)：\(openApiDiagnosticBody(uploadResult.rawText))"
            )
        }
        return normalizedURL
    }

    func openApiUploadedVoice(for url: URL) async throws -> (url: String, durationSeconds: Int?) {
        if isRemoteURL(url) {
            return (url.absoluteString, nil)
        }
        let environment = await openApiVoiceEnvironmentIfAvailable()
        let candidateResult = await openApiVoiceUploadCandidates(for: url, environment: environment)
        let candidates = candidateResult.candidates
        guard !candidates.isEmpty else {
            throw OpenApiMediaSendError.taskResultUnknown(
                "上传语音",
                "没有可用于 OpenAPI 上传的本地语音格式。\(candidateResult.diagnostics.joined(separator: "；"))"
            )
        }

        var bestSelection: OpenApiVoiceUploadSelection?
        var diagnostics: [String] = candidateResult.diagnostics
        for candidate in candidates {
            do {
                let uploadResult = try await OpenApiHTTPClient.uploadVoiceFile(candidate.url)
                guard let uploadedURL = openApiVoiceURL(from: uploadResult.jsonObject), !uploadedURL.isEmpty else {
                    diagnostics.append("\(candidate.label)：未返回 voiceUrl；\(openApiVoiceUploadDiagnosticSummary(from: uploadResult.jsonObject, rawText: uploadResult.rawText, environment: environment))")
                    continue
                }
                let compatibility = openApiVoiceUploadCompatibility(
                    from: uploadResult.jsonObject,
                    rawText: uploadResult.rawText,
                    environment: environment
                )
                guard compatibility.isPlayable else {
                    diagnostics.append("\(candidate.label)：\(compatibility.diagnostic)")
                    continue
                }
                let score = openApiVoiceUploadScore(from: uploadResult.jsonObject, environment: environment)
                let selection = OpenApiVoiceUploadSelection(
                    result: uploadResult,
                    url: uploadedURL,
                    score: score,
                    candidate: candidate,
                    diagnostic: compatibility.diagnostic
                )
                if bestSelection.map({ score > $0.score }) ?? true {
                    bestSelection = selection
                }
                if score >= 170 {
                    break
                }
            } catch {
                diagnostics.append("\(candidate.label)：\(error.localizedDescription)")
            }
        }

        guard let selection = bestSelection else {
            throw OpenApiMediaSendError.taskResultUnknown(
                "上传语音",
                "未获得三星 Android 微信稳定可播放的 AMR voiceUrl。请检查 /media/voice/environment 是否已开启 AMR-NB 转码；普通 mp3/m4a/wav 或非 AMR 语音封装可能导致三星无法播放和转文字。\(diagnostics.joined(separator: "；"))"
            )
        }
        if selection.score < 100 {
            throw OpenApiMediaSendError.taskResultUnknown(
                "上传语音",
                "\(selection.candidate.label) 返回的 voiceUrl 缺少可播放格式确认：\(openApiVoiceUploadDiagnosticSummary(from: selection.result.jsonObject, rawText: selection.result.rawText, environment: environment))"
            )
        }
        return (selection.url, openApiDurationSeconds(from: selection.result.jsonObject))
    }

    func openApiVoiceEnvironmentIfAvailable() async -> OpenApiVoiceEnvironmentContext? {
        do {
            let result = try await OpenApiHTTPClient.request("GET", path: "/openapi/v1/media/voice/environment")
            let root = result.jsonObject as? [String: Any] ?? [:]
            return OpenApiVoiceEnvironmentContext(
                recommendedUploadFormats: openApiFirstStringArray(in: result.jsonObject, keys: ["recommendedUploadFormats"])
                    .map(openApiNormalizedVoiceFormat)
                    .filter { !$0.isEmpty },
                targetFormat: openApiFirstString(in: root, keys: ["targetFormat"]),
                ffmpegAvailable: openApiBoolValue(root["ffmpegAvailable"]),
                ffprobeAvailable: openApiBoolValue(root["ffprobeAvailable"]),
                amrEncoderAvailable: openApiBoolValue(root["amrEncoderAvailable"]),
                amrEncoderSmokeTestPassed: openApiBoolValue(root["amrEncoderSmokeTestPassed"]),
                amrEncoderSmokeTestSummary: openApiFirstString(in: root, keys: ["amrEncoderSmokeTestSummary"]),
                warnings: openApiFirstStringArray(in: result.jsonObject, keys: ["warnings"]),
                message: openApiFirstString(in: root, keys: ["message", "nextStep"])
            )
        } catch {
            return nil
        }
    }

    func openApiVoiceUploadCandidates(
        for sourceURL: URL,
        environment: OpenApiVoiceEnvironmentContext?
    ) async -> (candidates: [OpenApiVoiceUploadCandidate], diagnostics: [String]) {
        let sourceFormat = openApiNormalizedVoiceFormat(sourceURL.pathExtension)
        var preferredFormats: [String] = []
        if sourceFormat == "wav" {
            preferredFormats.append("wav")
        }
        preferredFormats.append(contentsOf: environment?.recommendedUploadFormats ?? [])
        if !sourceFormat.isEmpty {
            if !preferredFormats.contains(sourceFormat) {
                preferredFormats.append(sourceFormat)
            }
        }
        preferredFormats.append(contentsOf: ["wav", "m4a", "aac", "mp3"])
        preferredFormats = preferredFormats.reduce(into: [String]()) { result, format in
            let normalized = openApiNormalizedVoiceFormat(format)
            guard !normalized.isEmpty, !result.contains(normalized) else { return }
            result.append(normalized)
        }

        var candidates: [OpenApiVoiceUploadCandidate] = []
        var diagnostics: [String] = []
        func appendCandidate(_ candidate: OpenApiVoiceUploadCandidate) {
            guard !candidates.contains(where: { $0.url.path == candidate.url.path }) else { return }
            candidates.append(candidate)
        }

        for format in preferredFormats {
            switch format {
            case let formatValue where formatValue == sourceFormat && !sourceFormat.isEmpty:
                appendCandidate(OpenApiVoiceUploadCandidate(
                    url: sourceURL,
                    format: sourceFormat,
                    label: "原始\(sourceFormat.uppercased())"
                ))
            case "m4a", "aac", "mp4":
                do {
                    let convertedURL = try await m4aVoiceURL(for: sourceURL)
                    appendCandidate(OpenApiVoiceUploadCandidate(
                        url: convertedURL,
                        format: "m4a",
                        label: "iOS AAC/M4A"
                    ))
                } catch {
                    diagnostics.append("M4A转换不可用：\(error.localizedDescription)")
                }
            case "wav":
                if sourceFormat == "wav" {
                    appendCandidate(OpenApiVoiceUploadCandidate(
                        url: sourceURL,
                        format: "wav",
                        label: "原始WAV"
                    ))
                } else {
                    diagnostics.append("WAV仅在本地录音源为 WAV 时直接上传")
                }
            case "mp3":
                if sourceFormat == "mp3" {
                    appendCandidate(OpenApiVoiceUploadCandidate(
                        url: sourceURL,
                        format: "mp3",
                        label: "原始MP3"
                    ))
                } else {
                    diagnostics.append("iOS 原生录音不直接编码 MP3，跳过 MP3 候选")
                }
            case "amr":
                diagnostics.append("iOS 原生录音不直接编码 AMR，等待后端 /media/voice 转码")
            default:
                diagnostics.append("未知语音候选格式 \(format)，已跳过")
            }
        }

        if candidates.isEmpty {
            appendCandidate(OpenApiVoiceUploadCandidate(
                url: sourceURL,
                format: sourceFormat.isEmpty ? "source" : sourceFormat,
                label: "原始音频"
            ))
        }
        return (candidates, diagnostics)
    }

    func openApiNormalizedVoiceFormat(_ value: String) -> String {
        let lowercased = value
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .lowercased()
            .replacingOccurrences(of: ".", with: "")
        switch lowercased {
        case "mpeg", "audio/mpeg": return "mp3"
        case "mp4", "audio/mp4", "aac-lc", "caf-aac": return "m4a"
        case "wave", "x-wav", "audio/wav", "audio/wave": return "wav"
        case "audio/aac": return "aac"
        case "audio/amr", "amr-nb": return "amr"
        default: return lowercased
        }
    }

    func m4aVoiceURL(for sourceURL: URL) async throws -> URL {
        let sourceFormat = openApiNormalizedVoiceFormat(sourceURL.pathExtension)
        if isRemoteURL(sourceURL) || sourceFormat == "m4a" || sourceFormat == "aac" {
            return sourceURL
        }

        let didAccessSecurityScope = sourceURL.startAccessingSecurityScopedResource()
        defer {
            if didAccessSecurityScope {
                sourceURL.stopAccessingSecurityScopedResource()
            }
        }

        let outputURL = try mediaConversionDirectory()
            .appendingPathComponent("voice-\(UUID().uuidString)")
            .appendingPathExtension("m4a")
        if FileManager.default.fileExists(atPath: outputURL.path) {
            try FileManager.default.removeItem(at: outputURL)
        }

        let asset = AVURLAsset(url: sourceURL)
        guard let exportSession = AVAssetExportSession(asset: asset, presetName: AVAssetExportPresetAppleM4A) else {
            throw OpenApiMediaSendError.taskResultUnknown("处理语音", "当前音频无法创建 iOS M4A 转换任务。")
        }
        guard exportSession.supportedFileTypes.contains(.m4a) else {
            throw OpenApiMediaSendError.taskResultUnknown("处理语音", "当前音频不支持导出 M4A。")
        }

        exportSession.outputURL = outputURL
        exportSession.outputFileType = .m4a
        exportSession.shouldOptimizeForNetworkUse = true

        let exportBox = UnsafeSendableBox(exportSession)
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            exportBox.value.exportAsynchronously {
                let exportSession = exportBox.value
                switch exportSession.status {
                case .completed:
                    continuation.resume()
                case .failed:
                    continuation.resume(throwing: exportSession.error ?? OpenApiMediaSendError.taskResultUnknown("处理语音", "M4A 转换失败。"))
                case .cancelled:
                    continuation.resume(throwing: OpenApiMediaSendError.taskResultUnknown("处理语音", "M4A 转换已取消。"))
                default:
                    continuation.resume(throwing: OpenApiMediaSendError.taskResultUnknown("处理语音", "M4A 转换未完成。"))
                }
            }
        }
        return outputURL
    }

    func openApiVoiceUploadScore(from object: Any?, environment: OpenApiVoiceEnvironmentContext?) -> Int {
        guard openApiVoiceURL(from: object) != nil else { return 0 }
        let dictionary = object as? [String: Any] ?? [:]
        let wireFormat = openApiFirstString(in: dictionary, keys: ["wireFormat", "wire_format"])
        let format = openApiFirstString(in: dictionary, keys: ["format"])
        let targetFormat = openApiFirstString(in: dictionary, keys: ["targetFormat", "target_format"])
        let headerAscii = openApiFirstString(in: dictionary, keys: ["headerAscii", "header"])
        let headerHex = openApiFirstString(in: dictionary, keys: ["headerHex"])
        let profile = [
            openApiVoicePlaybackProfile(for: wireFormat),
            openApiVoicePlaybackProfile(for: format),
            openApiVoicePlaybackProfile(for: targetFormat),
            openApiVoicePlaybackProfile(for: headerAscii),
            openApiVoicePlaybackProfile(for: headerHex)
        ].max(by: { $0.rawValue < $1.rawValue }) ?? .unsupported
        var score = 100
        switch profile {
        case .amr:
            score += 80
        case .silk:
            score += 25
        case .wechatContainer:
            score += 12
        case .unsupported:
            break
        }
        if openApiVoicePlaybackProfile(for: environment?.targetFormat ?? "") == .amr {
            score += 10
        }
        if openApiDurationSeconds(from: object) != nil {
            score += 6
        }
        let warnings = openApiFirstStringArray(in: object, keys: ["warnings"]) + (environment?.warnings ?? [])
        if !warnings.isEmpty {
            score -= min(30, warnings.count * 10)
        }
        return score
    }

    func openApiVoiceUploadCompatibility(
        from object: Any?,
        rawText: String,
        environment: OpenApiVoiceEnvironmentContext?
    ) -> (isPlayable: Bool, diagnostic: String) {
        let dictionary = object as? [String: Any] ?? [:]
        let wireFormat = openApiFirstString(in: dictionary, keys: ["wireFormat", "wire_format"])
        let format = openApiFirstString(in: dictionary, keys: ["format"])
        let targetFormat = openApiFirstString(in: dictionary, keys: ["targetFormat", "target_format"])
        let headerAscii = openApiFirstString(in: dictionary, keys: ["headerAscii", "header"])
        let headerHex = openApiFirstString(in: dictionary, keys: ["headerHex"])
        let resultFormatText = [wireFormat, format, targetFormat]
            .joined(separator: " ")
            .trimmingCharacters(in: .whitespacesAndNewlines)
        let headerText = [headerAscii, headerHex].joined(separator: " ")
        let environmentTarget = environment?.targetFormat ?? ""
        let summary = openApiVoiceUploadDiagnosticSummary(from: object, rawText: rawText, environment: environment)
        let resultProfile = openApiVoicePlaybackProfile(for: resultFormatText)
        let headerProfile = openApiVoicePlaybackProfile(for: headerText)
        let environmentProfile = openApiVoicePlaybackProfile(for: environmentTarget)
        let bestProfile = [resultProfile, headerProfile, environmentProfile].max(by: { $0.rawValue < $1.rawValue }) ?? .unsupported

        if openApiVoiceHeaderLooksLikeGenericAudio(headerText),
           resultProfile == .unsupported {
            return (
                false,
                "返回的 voiceUrl 仍是普通音频头，不是微信语音帧；\(summary)"
            )
        }

        if resultProfile == .amr || headerProfile == .amr {
            return (true, "已确认 AMR/AMR-NB，三星 Android 微信兼容性优先；\(summary)")
        }

        if resultFormatText.isEmpty,
           environmentProfile == .amr,
           !openApiVoiceHeaderLooksLikeGenericAudio(headerText) {
            return (true, "按语音环境 AMR 目标格式确认可发送；\(summary)")
        }

        if bestProfile == .silk || bestProfile == .wechatContainer {
            return (
                false,
                "服务端返回的是非 AMR 语音封装，其他手机可能能播，但三星微信可能无法播放或转文字；请让 /media/voice 返回 AMR-NB voiceUrl；\(summary)"
            )
        }

        var reason = "未确认返回的 voiceUrl 已转成三星兼容的 AMR-NB 微信语音格式"
        if environment?.amrEncoderAvailable == false || environment?.amrEncoderSmokeTestPassed == false {
            reason += "；后端 AMR 编码器不可用或自检未通过"
        }
        return (false, "\(reason)；\(summary)")
    }

    func openApiVoiceTextConfirmsWechatPlayable(_ text: String) -> Bool {
        openApiVoicePlaybackProfile(for: text) != .unsupported
    }

    func openApiVoicePlaybackProfile(for text: String) -> OpenApiVoicePlaybackProfile {
        let normalized = text
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .lowercased()
        let compact = normalized
            .replacingOccurrences(of: " ", with: "")
            .replacingOccurrences(of: ":", with: "")
            .replacingOccurrences(of: "-", with: "")
            .replacingOccurrences(of: "_", with: "")
        guard !normalized.isEmpty else { return .unsupported }
        if [
            "amr",
            "amr-nb",
            "amrnb",
            "#!amr",
            "2321414d52"
        ].contains(where: { normalized.contains($0) || compact.contains($0) }) {
            return .amr
        }
        if [
            "silk",
            "#!silk",
            "232153494c4b"
        ].contains(where: { normalized.contains($0) || compact.contains($0) }) {
            return .silk
        }
        if [
            "wechat",
            "weixin",
            "wxvoice",
            "wx-voice",
            "wechat-voice"
        ].contains(where: { normalized.contains($0) || compact.contains($0) }) {
            return .wechatContainer
        }
        return .unsupported
    }

    func openApiVoiceHeaderLooksLikeGenericAudio(_ text: String) -> Bool {
        let normalized = text.lowercased()
        guard !normalized.isEmpty else { return false }
        return [
            "id3",
            "riff",
            "wave",
            "ftyp",
            "mp4",
            "m4a",
            "caff",
            "aiff",
            "aifc",
            "lame"
        ].contains(where: normalized.contains)
    }

    func openApiVoiceUploadDiagnosticSummary(
        from object: Any?,
        rawText: String,
        environment: OpenApiVoiceEnvironmentContext?
    ) -> String {
        let dictionary = object as? [String: Any] ?? [:]
        var parts: [String] = []
        for key in ["format", "wireFormat", "originalFormat", "previewFormat", "targetFormat", "headerAscii", "headerHex", "pcPlaybackNote", "message"] {
            let value = openApiFirstString(in: dictionary, keys: [key])
            if !value.isEmpty {
                parts.append("\(key)=\(value)")
            }
        }
        if let duration = openApiDurationSeconds(from: object) {
            parts.append("durationSeconds=\(duration)")
        }
        if let targetFormat = environment?.targetFormat, !targetFormat.isEmpty {
            parts.append("environment.targetFormat=\(targetFormat)")
        }
        if let ffmpegAvailable = environment?.ffmpegAvailable {
            parts.append("ffmpegAvailable=\(ffmpegAvailable)")
        }
        if let ffprobeAvailable = environment?.ffprobeAvailable {
            parts.append("ffprobeAvailable=\(ffprobeAvailable)")
        }
        if let amrEncoderAvailable = environment?.amrEncoderAvailable {
            parts.append("amrEncoderAvailable=\(amrEncoderAvailable)")
        }
        if let amrEncoderSmokeTestPassed = environment?.amrEncoderSmokeTestPassed {
            parts.append("amrEncoderSmokeTestPassed=\(amrEncoderSmokeTestPassed)")
        }
        if let summary = environment?.amrEncoderSmokeTestSummary, !summary.isEmpty {
            parts.append("amrEncoderSmokeTestSummary=\(summary)")
        }
        let warnings = openApiFirstStringArray(in: object, keys: ["warnings"]) + (environment?.warnings ?? [])
        if !warnings.isEmpty {
            parts.append("warnings=\(warnings.joined(separator: ","))")
        }
        if parts.isEmpty {
            parts.append(openApiDiagnosticBody(rawText))
        }
        return parts.joined(separator: "；")
    }

    func isRemoteURL(_ url: URL) -> Bool {
        let scheme = url.scheme?.lowercased()
        return scheme == "http" || scheme == "https"
    }

    func openApiURL(from text: String) -> URL? {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return nil }
        if let url = URL(string: trimmed), url.scheme != nil {
            return url
        }
        return URL(fileURLWithPath: trimmed)
    }

    func openApiNormalizedRemoteURLText(_ text: String) -> String? {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty,
              let url = OpenApiDisplay.url(from: trimmed),
              let scheme = url.scheme?.lowercased(),
              scheme == "http" || scheme == "https"
        else { return nil }
        return url.absoluteString
    }

    func openApiSanitizedFileName(_ rawName: String, fallbackURL: URL? = nil) -> String {
        func clean(_ value: String) -> String {
            value
                .replacingOccurrences(of: "\n", with: " ")
                .replacingOccurrences(of: "\r", with: " ")
                .trimmingCharacters(in: .whitespacesAndNewlines)
        }

        let raw = clean(rawName)
        let filename: String
        if let url = OpenApiDisplay.url(from: raw), !url.lastPathComponent.isEmpty {
            filename = url.lastPathComponent
        } else {
            filename = raw
                .components(separatedBy: CharacterSet(charactersIn: "/\\"))
                .last
                .map(clean) ?? raw
        }
        let fallback = fallbackURL?.lastPathComponent.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        let resolved = clean(filename).isEmpty ? clean(fallback) : clean(filename)
        guard !resolved.isEmpty else { return "文件" }
        return String(resolved.prefix(160))
    }

    func openApiImageURLString(from element: BubbleRichElement) -> String? {
        if case let .image(_, url, _, _) = element {
            return url
        }
        return nil
    }

    func openApiFirstRichFileURL(in message: ChatMessage) -> URL? {
        for element in message.richElements {
            if case let .file(_, _, url, _, _) = element {
                return openApiURL(from: url)
            }
        }
        return nil
    }

    func openApiFileName(for message: ChatMessage, fallbackURL: URL) -> String {
        for element in message.richElements {
            if case let .file(name, _, _, _, _) = element, !name.isEmpty {
                return openApiSanitizedFileName(name, fallbackURL: fallbackURL)
            }
        }
        if !fallbackURL.lastPathComponent.isEmpty {
            return openApiSanitizedFileName(fallbackURL.lastPathComponent, fallbackURL: fallbackURL)
        }
        if message.type == .file,
           !message.body.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            return openApiSanitizedFileName(message.body, fallbackURL: fallbackURL)
        }
        return "文件"
    }

    func openApiMediaURL(from object: Any?) -> String? {
        openApiURLValue(
            from: object,
            preferredKeys: [
                "fileUrl", "fileURL", "downloadUrl", "downloadURL", "cdnUrl", "cdnURL",
                "sourceUrl", "sourceURL", "remoteUrl", "remoteURL", "resourceUrl", "resourceURL",
                "publicUrl", "publicURL", "imageUrl", "imageURL", "videoUrl", "videoURL",
                "mediaUrl", "mediaURL", "url", "href", "uri", "originalUrl", "originalURL",
                "previewUrl", "previewURL", "path"
            ]
        )
    }

    func openApiVoiceURL(from object: Any?) -> String? {
        openApiURLValue(
            from: object,
            preferredKeys: ["voiceUrl"]
        )
    }

    func openApiDurationSeconds(from object: Any?) -> Int? {
        if let root = object as? [String: Any] {
            for key in ["durationSeconds", "duration", "seconds", "amrFrameDurationSeconds"] {
                if let value = root[key] as? NSNumber, value.intValue > 0 {
                    return value.intValue
                }
                if let text = root[key] as? String,
                   let value = Int(text.trimmingCharacters(in: .whitespacesAndNewlines)),
                   value > 0 {
                    return value
                }
            }
            for key in ["data", "result", "payload", "file", "media", "item"] {
                if let nested = openApiDurationSeconds(from: root[key]) {
                    return nested
                }
            }
        }
        if let array = object as? [Any] {
            for item in array {
                if let value = openApiDurationSeconds(from: item) {
                    return value
                }
            }
        }
        return nil
    }

    func openApiURLValue(from object: Any?, preferredKeys: [String]) -> String? {
        func string(_ value: Any?) -> String? {
            if let text = value as? String {
                let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
                return trimmed.isEmpty ? nil : trimmed
            }
            if let number = value as? NSNumber {
                return number.stringValue
            }
            return nil
        }

        if let value = string(object) {
            return value
        }
        if let root = object as? [String: Any] {
            for key in preferredKeys {
                if let value = string(root[key]) {
                    return value
                }
            }
            for key in [
                "data", "result", "payload", "file", "media", "item", "attachment", "resource",
                "fileInfo", "mediaInfo", "upload", "uploaded", "record", "value",
                "files", "medias", "items", "attachments", "resources", "urls"
            ] {
                if let nested = openApiURLValue(from: root[key], preferredKeys: preferredKeys) {
                    return nested
                }
            }
        }
        if let array = object as? [Any] {
            for item in array {
                if let value = openApiURLValue(from: item, preferredKeys: preferredKeys) {
                    return value
                }
            }
        }
        return nil
    }

    func voiceDurationSeconds(for message: ChatMessage, fileURL: URL) -> Int {
        if let match = (message.body + "\n" + message.detail)
            .range(of: #"(\d+)\s*秒"#, options: .regularExpression) {
            let text = String((message.body + "\n" + message.detail)[match])
                .replacingOccurrences(of: "秒", with: "")
                .trimmingCharacters(in: .whitespacesAndNewlines)
            if let value = Int(text.filter(\.isNumber)), value > 0 {
                return value
            }
        }
        let seconds = (try? AVAudioPlayer(contentsOf: fileURL))?.duration ?? 0
        guard seconds.isFinite, seconds > 0 else { return 1 }
        return max(1, Int(seconds.rounded()))
    }

    func refreshInsertedPaymentMessageLayout(messageID: UUID, delay: TimeInterval) {
        DispatchQueue.main.asyncAfter(deadline: .now() + delay) { [weak self] in
            guard let self,
                  let indexPath = self.renderedIndexPath(of: messageID)
            else { return }
            self.messageCollectionView.collectionViewLayout.invalidateLayout()
            UIView.performWithoutAnimation {
                if self.messageCollectionView.indexPathsForVisibleItems.contains(indexPath) {
                    self.messageCollectionView.reloadItems(at: [indexPath])
                } else {
                    self.messageCollectionView.reloadData()
                }
                self.messageCollectionView.layoutIfNeeded()
            }
            self.messageCollectionView.scrollToItem(at: indexPath, at: .bottom, animated: false)
            self.scheduleConnectionUpdate()
        }
    }

    enum RichTextTokenKind {
        case paidian(label: String, url: String)
        case taggedFile(label: String, url: String)
        case url(String)
        case mention(String)
        case ai(String)
        case image(name: String, url: String, aspect: BubbleImageAspect)
    }

    struct RichTextToken {
        let range: Range<String.Index>
        let kind: RichTextTokenKind
    }

    func richElements(from text: String) -> [BubbleRichElement] {
        let tokens = richTextTokens(in: text)
        guard !tokens.isEmpty else { return [] }

        var elements: [BubbleRichElement] = []
        var cursor = text.startIndex
        for token in tokens {
            if cursor < token.range.lowerBound {
                elements.append(.text(String(text[cursor..<token.range.lowerBound])))
            }
            elements.append(richElement(for: token.kind))
            cursor = token.range.upperBound
        }
        if cursor < text.endIndex {
            elements.append(.text(String(text[cursor..<text.endIndex])))
        }
        return elements
    }

    func richElement(for kind: RichTextTokenKind) -> BubbleRichElement {
        switch kind {
        case .paidian(let label, let url):
            return .blueLink(label: label, url: url, bracketed: true)
        case .taggedFile(let label, let url):
            return .taggedFile(label: label, url: url, prefix: "#")
        case .url(let url):
            return .blueLink(label: url, url: url, bracketed: false)
        case .mention(let name):
            return .mention(name)
        case .ai(let text):
            return .aiToken(text)
        case .image(let name, let url, let aspect):
            return .image(name: name, url: url, aspect: aspect, access: .public)
        }
    }

    func richTextTokens(in text: String) -> [RichTextToken] {
        var tokens: [RichTextToken] = []

        tokens.append(contentsOf: regexMatches(
            in: text,
            pattern: #"\[([^\]\n]{1,24})\]\((https?://[^\s\)]+|www\.[^\s\)]+)\)"#
        ).compactMap { match in
            guard match.ranges.count >= 3,
                  let labelRange = match.ranges[1],
                  let urlRange = match.ranges[2]
            else { return nil }
            return RichTextToken(
                range: match.range,
                kind: .paidian(label: String(text[labelRange]), url: trimmedURLText(String(text[urlRange])))
            )
        })

        tokens.append(contentsOf: regexMatches(
            in: text,
            pattern: #"\[([^\]\n]{1,24})\]\s*(https?://[^\s]+|www\.[^\s]+)"#
        ).compactMap { match in
            guard match.ranges.count >= 3,
                  let labelRange = match.ranges[1],
                  let urlRange = match.ranges[2]
            else { return nil }
            return RichTextToken(
                range: match.range,
                kind: .paidian(label: String(text[labelRange]), url: trimmedURLText(String(text[urlRange])))
            )
        })

        tokens.append(contentsOf: regexMatches(
            in: text,
            pattern: #"(?<!\S)#([^\s#@]{1,48}\.(?:txt|md|doc|docx|pdf|xls|xlsx|ppt|pptx|zip|rar))"#
        ).compactMap { match in
            guard match.ranges.count >= 2,
                  let labelRange = match.ranges[1]
            else { return nil }
            let label = String(text[labelRange])
            return RichTextToken(
                range: match.range,
                kind: .taggedFile(label: label, url: "https://cc2.cx")
            )
        })

        tokens.append(contentsOf: regexMatches(
            in: text,
            pattern: #"(?i)\b(https?://[A-Za-z0-9\-._~:/?#\[\]@!$&'()*+,;=%]+|www\.[A-Za-z0-9\-._~:/?#\[\]@!$&'()*+,;=%]+)"#
        ).compactMap { match in
            let urlText = trimmedURLText(String(text[match.range]))
            if isImageReference(urlText) {
                return RichTextToken(
                    range: match.range,
                    kind: .image(name: imageDisplayName(from: urlText), url: urlText, aspect: imageAspectGuess(from: urlText))
                )
            }
            return RichTextToken(range: match.range, kind: .url(urlText))
        })

        tokens.append(contentsOf: regexMatches(
            in: text,
            pattern: #"(?<!\S)@([\p{Han}A-Za-z0-9_·-]{1,24})"#
        ).compactMap { match in
            guard match.ranges.count >= 2,
                  let nameRange = match.ranges[1]
            else { return nil }
            return RichTextToken(range: match.range, kind: .mention(String(text[nameRange])))
        })

        tokens.append(contentsOf: regexMatches(
            in: text,
            pattern: #"✦\s*([A-Za-z0-9_\-\p{Han}]{1,24})"#
        ).compactMap { match in
            guard match.ranges.count >= 2,
                  let tokenRange = match.ranges[1]
            else { return nil }
            return RichTextToken(range: match.range, kind: .ai(String(text[tokenRange])))
        })

        tokens.append(contentsOf: regexMatches(
            in: text,
            pattern: #"(?i)(?<![\w/.-])([^\s#@]{1,48}\.(?:png|jpg|jpeg|gif|webp))(?![\w/.-])"#
        ).compactMap { match in
            guard match.ranges.count >= 2,
                  let nameRange = match.ranges[1]
            else { return nil }
            let name = String(text[nameRange])
            return RichTextToken(
                range: match.range,
                kind: .image(name: name, url: "https://image.ai/i/\(name.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? name)", aspect: imageAspectGuess(from: name))
            )
        })

        return nonOverlappingTokens(tokens, in: text)
    }

    func nonOverlappingTokens(_ tokens: [RichTextToken], in text: String) -> [RichTextToken] {
        tokens.sorted {
            if $0.range.lowerBound == $1.range.lowerBound {
                return text.distance(from: $0.range.lowerBound, to: $0.range.upperBound)
                    > text.distance(from: $1.range.lowerBound, to: $1.range.upperBound)
            }
            return $0.range.lowerBound < $1.range.lowerBound
        }.reduce(into: [RichTextToken]()) { result, token in
            guard result.last.map({ $0.range.upperBound <= token.range.lowerBound }) ?? true else { return }
            result.append(token)
        }
    }

    func regexMatches(in text: String, pattern: String) -> [(range: Range<String.Index>, ranges: [Range<String.Index>?])] {
        guard let regex = try? NSRegularExpression(pattern: pattern, options: [.caseInsensitive]) else { return [] }
        let fullRange = NSRange(text.startIndex..<text.endIndex, in: text)
        return regex.matches(in: text, range: fullRange).compactMap { match in
            guard let wholeRange = Range(match.range, in: text) else { return nil }
            let ranges = (0..<match.numberOfRanges).map { index -> Range<String.Index>? in
                Range(match.range(at: index), in: text)
            }
            return (range: wholeRange, ranges: ranges)
        }
    }

    func trimmedURLText(_ text: String) -> String {
        var result = text
        let trailing = CharacterSet(charactersIn: ".,!?:;)]}。，！？：；")
        while let scalar = result.unicodeScalars.last, trailing.contains(scalar) {
            result.removeLast()
        }
        return result
    }

    func isImageReference(_ text: String) -> Bool {
        let lower = text.lowercased()
        return [".png", ".jpg", ".jpeg", ".gif", ".webp"].contains { lower.contains($0) }
    }

    func imageDisplayName(from text: String) -> String {
        let trimmed = trimmedURLText(text)
        return URL(string: normalizedURL(from: trimmed)?.absoluteString ?? trimmed)?.lastPathComponent
            .removingPercentEncoding
            .flatMap { $0.isEmpty ? nil : $0 }
            ?? "图片缩略图"
    }

    func imageAspectGuess(from text: String) -> BubbleImageAspect {
        let lower = text.lowercased()
        if lower.contains("vertical") || lower.contains("portrait") || lower.contains("竖") {
            return .vertical
        }
        return .horizontal
    }

    func insertContactCardMessage(_ profile: ContactCardProfile) {
        state.contactCards[profile.accountID] = profile
        let sender = outgoingSenderForSelectedAccount()
        let message = ChatMessage(
            id: UUID(),
            conversationID: currentConversationID(),
            type: .contactCard,
            sender: sender,
            body: profile.displayName,
            detail: profile.summary,
            isOutgoing: true,
            presentation: .avatarOnly,
            timestamp: currentTimestamp(),
            sentAt: Date(),
            contactCard: profile,
            contactCardAccountID: profile.accountID,
            recipientAccountID: sender.id
        )

        state.messages.append(message)
        persistMessages()
        rightAccountCollectionView.reloadData()
        rightToolCollectionView.reloadData()
        messageCollectionView.reloadData()
        if let indexPath = lastRenderedMessageIndexPath() {
            messageCollectionView.scrollToItem(at: indexPath, at: .bottom, animated: true)
        }
        scheduleConnectionUpdate()
    }

    func insertSystemMessage(_ body: String, conversationID: UUID, animated: Bool = true, forceReload: Bool = false) {
        let previousVisibleCount = renderedMessages.count
        let message = ChatMessage(
            id: UUID(),
            conversationID: conversationID,
            type: .system,
            sender: state.currentUser,
            body: body,
            detail: "",
            isOutgoing: false,
            presentation: .bare,
            timestamp: currentTimestamp(),
            sentAt: Date()
        )

        state.messages.append(message)
        persistMessages()
        let newVisibleCount = renderedMessages.count
        guard newVisibleCount > previousVisibleCount else {
            scheduleConnectionUpdate()
            return
        }

        let visibleIndex = newVisibleCount - 1
        let indexPath = IndexPath(item: visibleIndex, section: 0)
        if animated && !forceReload && newVisibleCount == previousVisibleCount + 1 {
            messageCollectionView.insertItems(at: [indexPath])
        } else {
            messageCollectionView.reloadData()
            messageCollectionView.layoutIfNeeded()
        }
        messageCollectionView.scrollToItem(at: indexPath, at: .bottom, animated: true)
        updateConnections()
    }

    func recallMessage(_ message: ChatMessage) {
        guard let removedIndex = state.messages.firstIndex(where: { $0.id == message.id }) else { return }
        guard canRecall(message) else {
            showNotice("已超过 2 分钟，不能撤回")
            return
        }

        state.messages.remove(at: removedIndex)
        let notice = ChatMessage(
            id: UUID(),
            conversationID: message.conversationID,
            type: .system,
            sender: state.currentUser,
            body: "你撤回了一条消息",
            detail: "",
            isOutgoing: false,
            presentation: .bare,
            timestamp: currentTimestamp(),
            sentAt: Date()
        )
        state.messages.insert(notice, at: removedIndex)
        persistMessages()
        messageCollectionView.reloadData()
        rightAccountCollectionView.reloadData()
        rightToolCollectionView.reloadData()
        updateConnections()
    }

    func insertAIMessage(prompt: String, provider: AIProvider = .claude, generatedText: String? = nil) {
        let aiParticipant = state.participants.first { $0.displayName == provider.title }
            ?? ChatParticipant(
                id: UUID(),
                displayName: provider.title,
                tintColor: provider == .codex
                    ? UIColor(red: 0.18, green: 0.38, blue: 0.86, alpha: 1)
                    : UIColor(red: 1.0, green: 0.50, blue: 0.18, alpha: 1),
                initials: provider == .codex ? "CX" : "AI",
                isCurrentUser: false
            )
        let body = generatedText ?? (
            prompt.isEmpty
                ? "\u{8fd9}\u{662f}AI\u{751f}\u{6210}\u{7684}\u{8349}\u{7a3f}\u{6d88}\u{606f}\u{ff0c}\u{4f7f}\u{7528}\u{865a}\u{7ebf}\u{5916}\u{8fb9}\u{6846}\u{8868}\u{793a}\u{4e34}\u{65f6}\u{72b6}\u{6001}\u{3002}"
                : "\u{9488}\u{5bf9}\u{201c}\(prompt)\u{201d}\u{751f}\u{6210}\u{7684} AI \u{8349}\u{7a3f}\u{6d88}\u{606f}\u{3002}"
        )
        let message = ChatMessage(
            id: UUID(),
            conversationID: aiParticipant.id,
            type: .text,
            sender: aiParticipant,
            body: body,
            detail: "",
            isOutgoing: false,
            presentation: .avatarAndName,
            timestamp: currentTimestamp(),
            sentAt: Date(),
            isAI: true
        )

        state.selectedFriendID = aiParticipant.id
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
    }

    func startCodexStreamingReply(prompt: String) {
        let messageID = insertAIStreamingMessage(provider: .codex, body: "Codex 正在生成...")
        var streamedText = ""

        streamCodexMessage(prompt: prompt) { [weak self] delta in
            DispatchQueue.main.async {
                streamedText += delta
                self?.updateAIStreamingMessage(id: messageID, body: streamedText)
            }
        } completion: { [weak self] result in
            DispatchQueue.main.async {
                switch result {
                case .success:
                    if streamedText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                        self?.updateAIStreamingMessage(id: messageID, body: "Codex 未返回内容。")
                    }
                    self?.finishAIStreamingMessage(id: messageID)
                case .failure:
                    self?.updateAIStreamingMessage(id: messageID, body: streamedText.isEmpty ? "Codex 生成失败。" : "\(streamedText)\n\n[生成中断]")
                    self?.finishAIStreamingMessage(id: messageID)
                    self?.showNotice("Codex 生成失败")
                }
            }
        }
    }

    func startCodexStreamingDirectReply(prompt: String) {
        let messageID = insertAIStreamingMessage(provider: .codex, body: "Codex 正在生成回复...")
        var streamedText = ""

        streamCodexMessage(prompt: prompt) { [weak self] delta in
            DispatchQueue.main.async {
                streamedText += delta
                self?.updateAIStreamingMessage(id: messageID, body: streamedText)
            }
        } completion: { [weak self] result in
            DispatchQueue.main.async {
                switch result {
                case .success:
                    if streamedText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                        self?.updateAIStreamingMessage(id: messageID, body: "Codex 未返回内容。")
                    }
                    self?.finishAIStreamingMessage(id: messageID)
                case .failure:
                    self?.updateAIStreamingMessage(
                        id: messageID,
                        body: streamedText.isEmpty ? "Codex 生成失败。" : "\(streamedText)\n\n[生成中断]"
                    )
                    self?.finishAIStreamingMessage(id: messageID)
                    self?.showNotice("Codex 生成失败")
                }
            }
        }
    }

    func startClaudeStreamingReply(prompt: String) {
        let messageID = insertAIStreamingMessage(provider: .claude, body: "Claude 正在生成...")
        var streamedText = ""

        streamClaudeMessage(prompt: prompt) { [weak self] delta in
            DispatchQueue.main.async {
                streamedText += delta
                self?.updateAIStreamingMessage(id: messageID, body: streamedText)
            }
        } completion: { [weak self] result in
            DispatchQueue.main.async {
                switch result {
                case .success:
                    if streamedText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                        self?.updateAIStreamingMessage(id: messageID, body: "Claude 未返回内容。")
                    }
                    self?.finishAIStreamingMessage(id: messageID)
                case .failure:
                    self?.updateAIStreamingMessage(
                        id: messageID,
                        body: streamedText.isEmpty ? "Claude 生成失败，请检查 Claude 配置。" : "\(streamedText)\n\n[生成中断]"
                    )
                    self?.finishAIStreamingMessage(id: messageID)
                    self?.showNotice("Claude 生成失败")
                }
            }
        }
    }

    func startClaudeStreamingDirectReply(prompt: String) {
        let messageID = insertAIStreamingMessage(provider: .claude, body: "Claude 正在生成回复...")
        var streamedText = ""

        streamClaudeMessage(prompt: prompt) { [weak self] delta in
            DispatchQueue.main.async {
                streamedText += delta
                self?.updateAIStreamingMessage(id: messageID, body: streamedText)
            }
        } completion: { [weak self] result in
            DispatchQueue.main.async {
                switch result {
                case .success:
                    if streamedText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                        self?.updateAIStreamingMessage(id: messageID, body: "Claude 未返回内容。")
                    }
                    self?.finishAIStreamingMessage(id: messageID)
                case .failure:
                    self?.updateAIStreamingMessage(
                        id: messageID,
                        body: streamedText.isEmpty ? "Claude 生成失败，请检查 Claude 配置。" : "\(streamedText)\n\n[生成中断]"
                    )
                    self?.finishAIStreamingMessage(id: messageID)
                    self?.showNotice("Claude 生成失败")
                }
            }
        }
    }

    func startCodexStreamingReplyInConversation(prompt: String, conversationID: UUID) {
        let messageID = insertAIStreamingMessage(
            provider: .codex,
            body: "Codex 正在生成回复...",
            conversationID: conversationID,
            shouldSelectConversation: false
        )
        var streamedText = ""

        streamCodexMessage(prompt: prompt) { [weak self] delta in
            DispatchQueue.main.async {
                streamedText += delta
                self?.updateAIStreamingMessage(id: messageID, body: streamedText)
            }
        } completion: { [weak self] result in
            DispatchQueue.main.async {
                switch result {
                case .success:
                    if streamedText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                        self?.updateAIStreamingMessage(id: messageID, body: "Codex 未返回内容。")
                    }
                    self?.finishAIStreamingMessage(id: messageID)
                case .failure:
                    self?.updateAIStreamingMessage(
                        id: messageID,
                        body: streamedText.isEmpty ? "Codex 生成失败。" : "\(streamedText)\n\n[生成中断]"
                    )
                    self?.finishAIStreamingMessage(id: messageID)
                    self?.showNotice("Codex 生成失败")
                }
            }
        }
    }

    @discardableResult
    func insertOutgoingStreamingMessage(body: String, conversationID: UUID) -> UUID {
        let conversation = state.participants.first { $0.id == conversationID }
        let sender = outgoingSenderForSelectedAccount(in: conversation)
        let message = ChatMessage(
            id: UUID(),
            conversationID: conversationID,
            type: .text,
            sender: sender,
            body: body,
            detail: "",
            isOutgoing: true,
            presentation: .avatarOnly,
            timestamp: currentTimestamp(),
            sentAt: Date(),
            isGroupConversation: conversation.map(isGroupConversation) ?? false,
            recipientAccountID: sender.id
        )

        state.selectedFriendID = conversationID
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
        scheduleConnectionUpdate()
        syncIslandActivityIfNeeded()
        return message.id
    }

    @discardableResult
    func insertAIStreamingMessage(
        provider: AIProvider,
        body: String,
        conversationID: UUID? = nil,
        shouldSelectConversation: Bool = true
    ) -> UUID {
        let aiParticipant = state.participants.first { $0.displayName == provider.title }
            ?? ChatParticipant(
                id: UUID(),
                displayName: provider.title,
                tintColor: provider == .codex
                    ? UIColor(red: 0.18, green: 0.38, blue: 0.86, alpha: 1)
                    : UIColor(red: 1.0, green: 0.50, blue: 0.18, alpha: 1),
                initials: provider == .codex ? "CX" : "AI",
                isCurrentUser: false
            )
        let targetConversationID = conversationID ?? aiParticipant.id
        let targetParticipant = state.participants.first { $0.id == targetConversationID }
        let message = ChatMessage(
            id: UUID(),
            conversationID: targetConversationID,
            type: .text,
            sender: aiParticipant,
            body: body,
            detail: "",
            isOutgoing: false,
            presentation: targetParticipant.map(isGroupConversation) == true ? .avatarAndName : .avatarOnly,
            timestamp: currentTimestamp(),
            sentAt: Date(),
            isAI: true,
            isGroupConversation: targetParticipant.map(isGroupConversation) == true
        )

        if shouldSelectConversation {
            state.selectedFriendID = targetConversationID
        }
        state.messages.append(message)
        persistMessages()
        streamingAIMessageIDs.insert(message.id)
        cancelLeftScrollPreview()
        leftCollectionView.reloadData()
        updateLeftCollectionBounceInsets()
        rightAccountCollectionView.reloadData()
        rightToolCollectionView.reloadData()
        if state.selectedFriendID == targetConversationID {
            messageCollectionView.reloadData()
            if let indexPath = lastRenderedMessageIndexPath() {
                messageCollectionView.scrollToItem(at: indexPath, at: .bottom, animated: true)
            }
        } else {
            messageCollectionView.reloadData()
        }
        scheduleConnectionUpdate()
        return message.id
    }

    func updateAIStreamingMessage(id: UUID, body: String) {
        enqueueStreamingMessageUpdate(
            id: id,
            body: body,
            rebuildsRichElements: false
        )
    }

    func finishAIStreamingMessage(id: UUID) {
        flushPendingStreamingMessageUpdates(forcePersistence: true)
        guard streamingAIMessageIDs.remove(id) != nil else { return }
        refreshAIStreamingCells(messageIDs: [id])
        syncIslandActivityIfNeeded()
    }

    func updateStreamingMessage(id: UUID, body: String) {
        enqueueStreamingMessageUpdate(
            id: id,
            body: body,
            rebuildsRichElements: false
        )
    }

    func enqueueStreamingMessageUpdate(
        id: UUID,
        body: String,
        rebuildsRichElements: Bool
    ) {
        let signpostID = PerformanceSignpost.begin("StreamingDeltaUpdate")
        defer { PerformanceSignpost.end("StreamingDeltaUpdate", id: signpostID) }
        pendingStreamingMessageUpdates[id] = PendingStreamingMessageUpdate(
            body: body,
            rebuildsRichElements: rebuildsRichElements
        )
        guard streamingUIFlushWorkItem == nil else { return }
        let workItem = DispatchWorkItem { [weak self] in
            self?.streamingUIFlushWorkItem = nil
            self?.flushPendingStreamingMessageUpdates(forcePersistence: false)
        }
        streamingUIFlushWorkItem = workItem
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.067, execute: workItem)
    }

    func flushPendingStreamingMessageUpdates(forcePersistence: Bool) {
        streamingUIFlushWorkItem?.cancel()
        streamingUIFlushWorkItem = nil
        guard !pendingStreamingMessageUpdates.isEmpty else { return }
        let pending = pendingStreamingMessageUpdates
        pendingStreamingMessageUpdates.removeAll(keepingCapacity: true)
        var changedIDs = Set<UUID>()
        var changedMessages: [ChatMessage] = []

        for (id, update) in pending {
            guard let messageIndex = state.messages.firstIndex(where: { $0.id == id }) else { continue }
            let current = state.messages[messageIndex]
            let updatedMessage = ChatMessage(
                id: current.id,
                conversationID: current.conversationID,
                type: current.type,
                sender: current.sender,
                body: update.body,
                detail: current.detail,
                isOutgoing: current.isOutgoing,
                presentation: current.presentation,
                timestamp: current.timestamp,
                sentAt: current.sentAt,
                backendMessageID: current.backendMessageID,
                attachmentURL: current.attachmentURL,
                isAI: current.isAI,
                isGroupConversation: current.isGroupConversation,
                richElements: update.rebuildsRichElements
                    ? richElements(from: update.body)
                    : current.richElements,
                contactCard: current.contactCard,
                contactCardAccountID: current.contactCardAccountID,
                quotedMessageID: current.quotedMessageID,
                mergedForwardMessages: current.mergedForwardMessages,
                recipientAccountID: current.recipientAccountID,
                unansweredMetadata: current.unansweredMetadata
            )
            state.messages[messageIndex] = updatedMessage
            changedIDs.insert(id)
            changedMessages.append(updatedMessage)
        }

        guard !changedIDs.isEmpty else { return }
        invalidateVisibleDataCaches()
        let visibleIndexPaths = renderedMessages.enumerated().compactMap { index, message in
            changedIDs.contains(message.id) ? IndexPath(item: index, section: 0) : nil
        }.filter { messageCollectionView.indexPathsForVisibleItems.contains($0) }
        if !visibleIndexPaths.isEmpty {
            messageCollectionView.reloadItems(at: visibleIndexPaths)
            if let last = visibleIndexPaths.max(by: { $0.item < $1.item }) {
                messageCollectionView.scrollToItem(at: last, at: .bottom, animated: false)
            }
        }
        refreshUnreadTimelineLayoutsAfterMessageHeightChange()

        let now = Date()
        if forcePersistence || now.timeIntervalSince(lastStreamingPersistenceDate) >= 0.5 {
            persistMessageMutations(changedMessages.map(MessageMutation.update))
            lastStreamingPersistenceDate = now
        }
        scheduleConnectionUpdate()
        syncIslandActivityIfNeeded()
    }

    func refreshUnreadTimelineLayoutsAfterMessageHeightChange() {
        guard isHomeTimeline else { return }
        UIView.performWithoutAnimation {
            messageCollectionView.collectionViewLayout.invalidateLayout()
            leftCollectionView.collectionViewLayout.invalidateLayout()
            messageCollectionView.layoutIfNeeded()
            leftCollectionView.layoutIfNeeded()
            updateVisibleUnreadTimelineAvatarPositions()
            updateLeftCollectionBounceInsets()
        }
        syncUnreadTimelineScroll(from: messageCollectionView)
    }

    func refreshAIStreamingCells(messageIDs: [UUID]) {
        let ids = Set(messageIDs)
        guard !ids.isEmpty else { return }
        let indexPaths = renderedMessages.enumerated().compactMap { index, message -> IndexPath? in
            ids.contains(message.id) ? IndexPath(item: index, section: 0) : nil
        }
        guard !indexPaths.isEmpty else { return }
        messageCollectionView.reloadItems(at: indexPaths)
        scheduleConnectionUpdate()
    }

    func currentTimestamp() -> String {
        let formatter = DateFormatter()
        formatter.dateFormat = "HH:mm"
        return formatter.string(from: Date())
    }

    func setQuotedMessage(_ message: ChatMessage?) {
        quotedMessage = message
        if let message {
            pendingAttachments.removeAll()
            quoteThumbnailView.isHidden = true
            quoteThumbnailView.image = nil
            quotePreviewLabel.text = "引用 \(message.sender.displayName)：\(quoteBody(for: message))"
            quotePreviewHeightConstraint?.constant = 42
            quotePreviewBar.isHidden = false
            inputBar.textField.becomeFirstResponder()
        } else {
            if pendingAttachments.isEmpty {
                quotePreviewLabel.text = nil
                quoteThumbnailView.isHidden = true
                quoteThumbnailView.image = nil
                quotePreviewHeightConstraint?.constant = 0
                quotePreviewBar.isHidden = true
            }
        }
        view.layoutIfNeeded()
        updateConnections()
    }

    @objc func clearQuotedMessage() {
        if !pendingAttachments.isEmpty {
            clearPendingAttachment()
        } else {
            setQuotedMessage(nil)
        }
    }

    func setPendingAttachment(_ attachment: PendingAttachment) {
        setPendingAttachments([attachment], appending: true)
    }

    func setPendingAttachments(_ attachments: [PendingAttachment], appending: Bool) {
        guard !attachments.isEmpty else { return }
        quotedMessage = nil
        if appending {
            pendingAttachments.append(contentsOf: attachments)
        } else {
            pendingAttachments = attachments
        }
        quotePreviewLabel.text = pendingAttachmentPreviewText(for: pendingAttachments)
        quoteThumbnailView.image = pendingAttachmentThumbnail(for: pendingAttachments[0])
        quoteThumbnailView.isHidden = false
        inputBar.textField.enablesReturnKeyAutomatically = false
        quotePreviewHeightConstraint?.constant = 52
        quotePreviewBar.isHidden = false
        inputBar.textField.becomeFirstResponder()
        view.layoutIfNeeded()
        updateConnections()
    }

    func enqueueSelectedImages(_ sources: [SelectedImageAttachment], appending: Bool) {
        guard !sources.isEmpty else { return }

        guard automaticBackgroundRemovalEnabled else {
            let attachments = sources.compactMap { self.makeOriginalPendingImageAttachment($0) }
            guard !attachments.isEmpty else {
                showNotice("图片保存失败")
                return
            }
            setPendingAttachments(attachments, appending: appending)
            return
        }

        guard #available(iOS 17.0, *) else {
            let attachments = sources.compactMap { self.makeOriginalPendingImageAttachment($0) }
            guard !attachments.isEmpty else {
                showNotice("图片保存失败")
                return
            }
            setPendingAttachments(attachments, appending: appending)
            showNotice("当前系统不支持智能扣图，已保留原图")
            return
        }

        showAutomaticBackgroundRemovalLoading(imageCount: sources.count)
        DispatchQueue.global(qos: .userInitiated).async { [weak self] in
            guard let self else { return }
            var removedCount = 0
            var fallbackCount = 0
            let attachments: [PendingAttachment] = sources.compactMap { source in
                do {
                    let output = try BackgroundRemovalProcessor.removeBackground(from: source.image)
                    guard let outputURL = self.saveTransparentImageToTemporaryFile(output) else {
                        fallbackCount += 1
                        return self.makeOriginalPendingImageAttachment(source)
                    }
                    removedCount += 1
                    return PendingAttachment(
                        type: source.type,
                        url: outputURL,
                        title: self.backgroundRemovedTitle(for: source.title),
                        detail: "\(source.detail) · 已自动扣除背景",
                        accessScope: .public,
                        expiryPolicy: .never
                    )
                } catch {
                    fallbackCount += 1
                    return self.makeOriginalPendingImageAttachment(source)
                }
            }

            DispatchQueue.main.async {
                self.hideAutomaticBackgroundRemovalLoading()
                guard !attachments.isEmpty else {
                    self.showNotice("图片保存失败")
                    return
                }
                self.setPendingAttachments(attachments, appending: appending)
                if removedCount == sources.count {
                    self.showNotice("已生成透明背景图片")
                } else if removedCount > 0 {
                    self.showNotice("已处理 \(removedCount) 张图片，其余保留原图")
                } else if fallbackCount > 0 {
                    self.showNotice("未识别到可扣主体，已保留原图")
                }
            }
        }
    }

    func makeOriginalPendingImageAttachment(_ source: SelectedImageAttachment) -> PendingAttachment? {
        guard let url = saveImageToTemporaryFile(source.image) else { return nil }
        return PendingAttachment(
            type: source.type,
            url: url,
            title: source.title,
            detail: source.detail,
            accessScope: .public,
            expiryPolicy: .never
        )
    }

    func backgroundRemovedTitle(for title: String) -> String {
        let trimmed = title.trimmingCharacters(in: .whitespacesAndNewlines)
        let base = (trimmed as NSString).deletingPathExtension
        let resolvedBase = base.isEmpty ? "扣背景图片" : base
        return "\(resolvedBase)-透明背景.png"
    }

    func showAutomaticBackgroundRemovalLoading(imageCount: Int) {
        guard automaticBackgroundRemovalOverlay == nil else { return }

        let overlay = UIView()
        overlay.backgroundColor = UIColor.black.withAlphaComponent(0.16)
        overlay.translatesAutoresizingMaskIntoConstraints = false

        let card = UIVisualEffectView(effect: UIBlurEffect(style: .systemUltraThinMaterialDark))
        card.layer.cornerRadius = 14
        card.layer.cornerCurve = .continuous
        card.clipsToBounds = true
        card.translatesAutoresizingMaskIntoConstraints = false

        let icon = UIImageView(image: UIImage(systemName: "person.crop.rectangle"))
        icon.tintColor = UIColor(red: 0.54, green: 0.92, blue: 0.70, alpha: 1)
        icon.contentMode = .scaleAspectFit
        icon.translatesAutoresizingMaskIntoConstraints = false

        let spinner = UIActivityIndicatorView(style: .medium)
        spinner.color = .white
        spinner.startAnimating()
        spinner.translatesAutoresizingMaskIntoConstraints = false

        let title = UILabel()
        title.text = imageCount > 1 ? "正在扣除 \(imageCount) 张图片的背景" : "正在扣除图片背景"
        title.font = .systemFont(ofSize: 15, weight: .semibold)
        title.textColor = .white
        title.textAlignment = .center
        title.numberOfLines = 0
        title.translatesAutoresizingMaskIntoConstraints = false

        let subtitle = UILabel()
        subtitle.text = "本地处理，不上传原图"
        subtitle.font = .systemFont(ofSize: 12, weight: .medium)
        subtitle.textColor = UIColor.white.withAlphaComponent(0.72)
        subtitle.textAlignment = .center
        subtitle.translatesAutoresizingMaskIntoConstraints = false

        view.addSubview(overlay)
        overlay.addSubview(card)
        card.contentView.addSubview(icon)
        card.contentView.addSubview(spinner)
        card.contentView.addSubview(title)
        card.contentView.addSubview(subtitle)
        automaticBackgroundRemovalOverlay = overlay

        NSLayoutConstraint.activate([
            overlay.topAnchor.constraint(equalTo: view.topAnchor),
            overlay.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            overlay.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            overlay.bottomAnchor.constraint(equalTo: view.bottomAnchor),

            card.centerXAnchor.constraint(equalTo: overlay.centerXAnchor),
            card.centerYAnchor.constraint(equalTo: overlay.centerYAnchor),
            card.widthAnchor.constraint(equalToConstant: 232),
            card.heightAnchor.constraint(equalToConstant: 132),

            icon.centerXAnchor.constraint(equalTo: card.contentView.centerXAnchor, constant: -14),
            icon.topAnchor.constraint(equalTo: card.contentView.topAnchor, constant: 18),
            icon.widthAnchor.constraint(equalToConstant: 28),
            icon.heightAnchor.constraint(equalToConstant: 28),

            spinner.leadingAnchor.constraint(equalTo: icon.trailingAnchor, constant: 8),
            spinner.centerYAnchor.constraint(equalTo: icon.centerYAnchor),

            title.topAnchor.constraint(equalTo: icon.bottomAnchor, constant: 12),
            title.leadingAnchor.constraint(equalTo: card.contentView.leadingAnchor, constant: 16),
            title.trailingAnchor.constraint(equalTo: card.contentView.trailingAnchor, constant: -16),

            subtitle.topAnchor.constraint(equalTo: title.bottomAnchor, constant: 6),
            subtitle.leadingAnchor.constraint(equalTo: title.leadingAnchor),
            subtitle.trailingAnchor.constraint(equalTo: title.trailingAnchor)
        ])
    }

    func hideAutomaticBackgroundRemovalLoading() {
        guard let overlay = automaticBackgroundRemovalOverlay else { return }
        automaticBackgroundRemovalOverlay = nil
        UIView.animate(withDuration: 0.16, animations: {
            overlay.alpha = 0
        }, completion: { _ in
            overlay.removeFromSuperview()
        })
    }

    func presentAttachmentAccessScopePicker(
        type: ChatMessageType,
        url: URL,
        title: String,
        detail: String,
        sourceView: UIView? = nil
    ) {
        guard type == .file else {
            setPendingAttachment(
                PendingAttachment(
                    type: type,
                    url: url,
                    title: title,
                    detail: detail,
                    accessScope: .public,
                    expiryPolicy: .never
                )
            )
            return
        }

        let alert = UIAlertController(
            title: "文件访问权限",
            message: "设置谁可以通过文件链接访问。非公开文件会进入申请审核队列。",
            preferredStyle: .actionSheet
        )
        let scopes: [BubbleAccessScope] = [.public, .fans, .friends, .recipients]
        let expiries: [ResourceExpiryPolicy] = [.sevenDays, .thirtyDays, .never]
        for scope in scopes {
            for expiry in expiries {
                let suffix = expiry == .never ? "" : " · \(expiry.rawValue)有效"
                alert.addAction(UIAlertAction(title: "\(scope.rawValue)\(suffix)", style: .default) { [weak self] _ in
                    self?.setPendingAttachment(
                        PendingAttachment(
                            type: type,
                            url: url,
                            title: title,
                            detail: detail,
                            accessScope: scope,
                            expiryPolicy: expiry
                        )
                    )
                })
            }
        }
        alert.addAction(UIAlertAction(title: "取消", style: .cancel))
        if let popover = alert.popoverPresentationController {
            popover.sourceView = sourceView ?? view
            popover.sourceRect = (sourceView ?? view).bounds
        }
        present(alert, animated: true)
    }

    func clearPendingAttachment() {
        pendingAttachments.removeAll()
        quotePreviewLabel.text = nil
        quoteThumbnailView.image = nil
        quoteThumbnailView.isHidden = true
        inputBar.textField.enablesReturnKeyAutomatically = true
        quotePreviewHeightConstraint?.constant = 0
        quotePreviewBar.isHidden = true
        view.layoutIfNeeded()
        updateConnections()
    }

    @objc func previewPendingAttachment() {
        guard let attachment = pendingAttachments.first else { return }
        switch attachment.type {
        case .image, .capturedPhoto:
            presentImagePreview(url: attachment.url, title: attachment.title)
        case .video:
            presentVideoPreview(url: attachment.url)
        case .file:
            presentFilePreview(url: attachment.url)
        default:
            break
        }
    }

    func sendPendingAttachments(_ attachments: [PendingAttachment], text: String) {
        if attachments.count > 1,
           attachments.allSatisfy({ $0.type == .image || $0.type == .capturedPhoto }) {
            sendPendingImageGroup(attachments, text: text)
            return
        }
        for (index, attachment) in attachments.enumerated() {
            let caption = index == 0 ? text : ""
            let totalText = attachments.count > 1 ? "\n第 \(index + 1)/\(attachments.count) 个附件" : ""
            sendPendingAttachment(attachment, text: caption, extraDetail: totalText)
        }
    }

    func sendPendingImageGroup(_ attachments: [PendingAttachment], text: String) {
        var elements: [BubbleRichElement] = []
        let trimmedText = text.trimmingCharacters(in: .whitespacesAndNewlines)
        if !trimmedText.isEmpty {
            elements.append(contentsOf: richElements(from: trimmedText).isEmpty ? [.text(trimmedText)] : richElements(from: trimmedText))
        }
        for (index, attachment) in attachments.enumerated() {
            elements.append(
                .image(
                    name: attachment.title.isEmpty ? "图片 \(index + 1)" : attachment.title,
                    url: attachment.url.absoluteString,
                    aspect: imageAspect(forLocalImageAt: attachment.url),
                    access: .public
                )
            )
        }
        insertOutgoingMessage(
            type: .text,
            body: trimmedText.isEmpty ? "图片 \(attachments.count) 张" : trimmedText,
            detail: "多图消息 · \(attachments.count) 张",
            richElements: elements
        )
    }

    func imageAspect(forLocalImageAt url: URL) -> BubbleImageAspect {
        guard let image = UIImage(contentsOfFile: url.path)?.trimmedTransparentCanvasIfNeeded(), image.size.height > image.size.width * 1.15 else {
            return .horizontal
        }
        return .vertical
    }

    func sendPendingAttachment(_ attachment: PendingAttachment, text: String, extraDetail: String = "") {
        let body = text.isEmpty ? attachment.title : text
        let linkLine = "公开链接：\(attachment.url.absoluteString)"
        let accessLine = "访问权限：\(attachment.accessScope.rawValue)"
        let expiryLine = "有效期：\(attachment.expiryPolicy.rawValue)"
        let detail = text.isEmpty
            ? "\(attachment.detail)\(extraDetail)\n\(accessLine)\n\(expiryLine)\n\(linkLine)"
            : "\(attachment.title)\n附件说明消息\(extraDetail)\n\(accessLine)\n\(expiryLine)\n\(linkLine)"
        let richElements: [BubbleRichElement]
        if attachment.type == .file {
            richElements = [
                .file(
                    name: attachment.title,
                    format: BubbleFileFormat.infer(from: attachment.title),
                    url: attachment.url.absoluteString,
                    preview: "\(attachment.detail) · \(attachment.accessScope.rawValue) · \(attachment.expiryPolicy.rawValue)",
                    access: attachment.accessScope
                )
            ]
        } else {
            richElements = []
        }
        insertOutgoingMessage(
            type: attachment.type,
            body: body,
            detail: detail,
            attachmentURL: attachment.url,
            richElements: richElements
        )
    }

    func pendingAttachmentPreviewText(for attachments: [PendingAttachment]) -> String {
        guard let attachment = attachments.first else { return "" }
        let countSuffix = attachments.count > 1 ? " 等 \(attachments.count) 个" : ""
        switch attachment.type {
        case .image, .capturedPhoto:
            return "图片待发送：\(attachment.title)\(countSuffix)"
        case .video:
            return "视频待发送：\(attachment.title)\(countSuffix)"
        case .file:
            return "文件待发送：\(attachment.title)\(countSuffix)"
        default:
            return "附件待发送：\(attachment.title)\(countSuffix)"
        }
    }

    func pendingAttachmentThumbnail(for attachment: PendingAttachment) -> UIImage? {
        switch attachment.type {
        case .image, .capturedPhoto:
            return UIImage(contentsOfFile: attachment.url.path)?.trimmedTransparentCanvasIfNeeded()
        case .video:
            return videoThumbnail(url: attachment.url)
        case .file:
            return UIImage(systemName: "doc.text.fill")
        default:
            return UIImage(systemName: attachment.type.symbolName)
        }
    }

    func videoThumbnail(url: URL) -> UIImage? {
        let asset = AVURLAsset(url: url)
        let generator = AVAssetImageGenerator(asset: asset)
        generator.appliesPreferredTrackTransform = true
        guard let cgImage = try? generator.copyCGImage(at: .zero, actualTime: nil) else { return nil }
        return UIImage(cgImage: cgImage)
    }

    func handleBackgroundRemovedImage(_ image: UIImage, originalTitle: String) {
        guard let url = saveTransparentImageToTemporaryFile(image) else {
            showNotice("扣背景结果保存失败")
            return
        }
        let titleBase = originalTitle.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? "扣背景图片" : originalTitle
        setPendingAttachment(
            PendingAttachment(
                type: .image,
                url: url,
                title: "\(titleBase)-透明背景.png",
                detail: "已扣除背景",
                accessScope: .public,
                expiryPolicy: .never
            )
        )
        dismiss(animated: true) { [weak self] in
            self?.showNotice("已生成透明背景图片，可直接发送")
        }
    }

    func quoteSummary(for message: ChatMessage) -> String {
        "\(message.sender.displayName)：\(quoteBody(for: message))"
    }

    func quoteBody(for message: ChatMessage) -> String {
        let raw = message.body.isEmpty ? message.type.title : message.body
        return raw.count > 42 ? "\(String(raw.prefix(42)))..." : raw
    }

    func openQuotedOriginal(for message: ChatMessage) {
        guard let quotedMessageID = message.quotedMessageID else {
            showMessageActionNotice("查看引用消息：\(message.detail.isEmpty ? message.body : message.detail)")
            return
        }
        guard let original = state.messages.first(where: { $0.id == quotedMessageID }) else {
            showNotice("原消息不存在或已被撤回")
            return
        }

        if state.selectedFriendID != original.conversationID {
            selectFriend(id: original.conversationID)
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.36) {
                self.scrollToMessage(id: quotedMessageID)
            }
        } else {
            scrollToMessage(id: quotedMessageID)
        }
    }

    func updateMessageDetail(id: UUID, body: String? = nil, detail: String? = nil) {
        guard let messageIndex = state.messages.firstIndex(where: { $0.id == id }) else { return }
        let current = state.messages[messageIndex]
        let updatedMessage = ChatMessage(
            id: current.id,
            conversationID: current.conversationID,
            type: current.type,
            sender: current.sender,
            body: body ?? current.body,
            detail: detail ?? current.detail,
            isOutgoing: current.isOutgoing,
            presentation: current.presentation,
            timestamp: current.timestamp,
            sentAt: current.sentAt,
            backendMessageID: current.backendMessageID,
            attachmentURL: current.attachmentURL,
            isAI: current.isAI,
            isGroupConversation: current.isGroupConversation,
            richElements: current.richElements,
            contactCard: current.contactCard,
            contactCardAccountID: current.contactCardAccountID,
            quotedMessageID: current.quotedMessageID,
            mergedForwardMessages: current.mergedForwardMessages,
            recipientAccountID: current.recipientAccountID,
            unansweredMetadata: current.unansweredMetadata
        )
        state.messages[messageIndex] = updatedMessage
        persistMessageMutations([.update(updatedMessage)])
        reloadRenderedMessageItem(messageID: id)
        reloadVisibleRightToolItems()
    }

    @discardableResult
    func completeUnansweredReplyAfterSuccessfulSend(_ message: ChatMessage) -> Bool {
        let registeredOrigin = unansweredReplyOriginsByOutgoingMessageID[message.id]
        guard let origin = registeredOrigin ?? unansweredNavigationOrigin,
              UnansweredReplyCompletionPolicy.shouldComplete(
                message: message,
                origin: origin,
                isAutoReplyPrediction: isAutoReplyPrediction(message)
              )
        else { return false }
        let animationSnapshot = unansweredCompletionSnapshotsByOutgoingMessageID
            .removeValue(forKey: message.id)
        if registeredOrigin != nil {
            unansweredReplyOriginsByOutgoingMessageID.removeValue(forKey: message.id)
        }

        // The outgoing message was initially indexed before its remote success
        // state was known. Re-apply just this mutation so a direct conversation
        // closes incrementally instead of rebuilding the complete message index.
        applyPendingReplyIndexMutations([.update(message)])
        markGroupUnansweredItemHandledIfNeeded(origin.sourceFocus)
        setUnansweredTimelineRoute(scope: origin.scope, focus: nil)
        state.selectedFriendID = nil
        invalidateUnansweredCompletionCaches()
        ensureVerticalMessageLayout()
        setQuotedMessage(nil)

        let presentation = pendingReplyHomePresentation()
        let nextFocus = UnansweredReplyAdvancePlanner.nextFocus(
            candidateItemIDs: origin.candidateItemIDs,
            presentation: presentation
        )
        setUnansweredTimelineRoute(scope: origin.scope, focus: nextFocus)

        if let nextFocus,
           let nextItem = presentation.itemByID[nextFocus.itemID] {
            let targetAccountIDs = Set(nextItem.targetAccountIDs)
            if !targetAccountIDs.contains(state.selectedAccountID),
               let nextAccountID = state.currentUsers.lazy.map(\.id).first(where: targetAccountIDs.contains) {
                state.selectedAccountID = nextAccountID
            }
        }

        updateHeaderForSelection()
        updateComposerAvailability()
        reloadVisibleRightToolItems()
        reloadRightAccountItems(
            accountIDs: Set([
                animationSnapshot?.selectedAccountID,
                state.selectedAccountID
            ].compactMap { $0 })
        )

        let finishTransition: () -> Void = { [weak self] in
            guard let self else { return }
            self.messageCollectionView.layoutIfNeeded()
            self.updateLeftCollectionBounceInsets()
            let minimumY = -self.messageCollectionView.adjustedContentInset.top
            let maximumY = max(
                minimumY,
                self.messageCollectionView.contentSize.height
                    - self.messageCollectionView.bounds.height
                    + self.messageCollectionView.adjustedContentInset.bottom
            )
            let preferredOffsetY = animationSnapshot?.contentOffsetY ?? origin.contentOffsetY
            self.messageCollectionView.setContentOffset(
                CGPoint(
                    x: self.messageCollectionView.contentOffset.x,
                    y: min(max(preferredOffsetY, minimumY), maximumY)
                ),
                animated: false
            )
            self.syncUnreadTimelineScroll(from: self.messageCollectionView)
            self.scheduleConnectionUpdate()
            self.scheduleConversationHeightPrewarming()
        }

        guard let animationSnapshot,
              applyUnansweredCompletionTransition(
                snapshot: animationSnapshot,
                completion: finishTransition
              )
        else {
            leftCollectionView.collectionViewLayout.invalidateLayout()
            messageCollectionView.collectionViewLayout.invalidateLayout()
            leftCollectionView.reloadData()
            messageCollectionView.reloadData()
            DispatchQueue.main.async(execute: finishTransition)
            return true
        }
        return true
    }

    @discardableResult
    func applyUnansweredCompletionTransition(
        snapshot: UnansweredCompletionAnimationSnapshot,
        completion: @escaping () -> Void
    ) -> Bool {
        let newMessageIDs = renderedMessages.map(\.id)
        let newLeftIDs = visibleLeftItemsForSelectedAccount().compactMap(\.participantID)
        guard messageCollectionView.numberOfItems(inSection: 0) == snapshot.messageIDs.count,
              leftCollectionView.numberOfItems(inSection: 0) == snapshot.leftParticipantIDs.count,
              unansweredCompletionDifferenceIsSafe(oldIDs: snapshot.messageIDs, newIDs: newMessageIDs),
              unansweredCompletionDifferenceIsSafe(oldIDs: snapshot.leftParticipantIDs, newIDs: newLeftIDs)
        else { return false }

        let newMessageIDSet = Set(newMessageIDs)
        let removedMessageIndexPaths = snapshot.messageIDs.enumerated().compactMap { index, id in
            newMessageIDSet.contains(id) ? nil : IndexPath(item: index, section: 0)
        }

        let removedCells = removedMessageIndexPaths.compactMap {
            messageCollectionView.cellForItem(at: $0)
        }
        let applyDifferences = { [weak self] in
            guard let self else { return }
            var pendingCompletions = 2
            let finishBatch: () -> Void = {
                pendingCompletions -= 1
                guard pendingCompletions == 0 else { return }
                completion()
            }
            self.applyUnansweredCompletionDifference(
                in: self.messageCollectionView,
                oldIDs: snapshot.messageIDs,
                newIDs: newMessageIDs,
                invalidatesLayout: true,
                completion: finishBatch
            )
            self.applyUnansweredCompletionDifference(
                in: self.leftCollectionView,
                oldIDs: snapshot.leftParticipantIDs,
                newIDs: newLeftIDs,
                invalidatesLayout: true,
                completion: finishBatch
            )
        }

        guard !removedCells.isEmpty else {
            applyDifferences()
            return true
        }
        UIView.animate(
            withDuration: 0.16,
            delay: 0,
            options: [.curveEaseIn, .beginFromCurrentState, .allowUserInteraction]
        ) {
            removedCells.forEach { cell in
                cell.alpha = 0
                cell.transform = CGAffineTransform(scaleX: 0.985, y: 0.82)
            }
        } completion: { _ in
            applyDifferences()
        }
        return true
    }

    func unansweredCompletionDifferenceIsSafe<ID: Hashable>(
        oldIDs: [ID],
        newIDs: [ID]
    ) -> Bool {
        guard Set(oldIDs).count == oldIDs.count,
              Set(newIDs).count == newIDs.count
        else { return false }
        let newIDSet = Set(newIDs)
        let oldIDSet = Set(oldIDs)
        return oldIDs.filter(newIDSet.contains) == newIDs.filter(oldIDSet.contains)
    }

    func applyUnansweredCompletionDifference<ID: Hashable>(
        in collectionView: UICollectionView,
        oldIDs: [ID],
        newIDs: [ID],
        invalidatesLayout: Bool,
        completion: @escaping () -> Void
    ) {
        let newIDSet = Set(newIDs)
        let oldIDSet = Set(oldIDs)
        let deletions = oldIDs.enumerated().compactMap { index, id in
            newIDSet.contains(id) ? nil : IndexPath(item: index, section: 0)
        }
        let insertions = newIDs.enumerated().compactMap { index, id in
            oldIDSet.contains(id) ? nil : IndexPath(item: index, section: 0)
        }
        guard !deletions.isEmpty || !insertions.isEmpty else {
            if invalidatesLayout {
                collectionView.collectionViewLayout.invalidateLayout()
            }
            completion()
            return
        }
        collectionView.performBatchUpdates {
            collectionView.deleteItems(at: deletions)
            collectionView.insertItems(at: insertions)
            if invalidatesLayout {
                collectionView.collectionViewLayout.invalidateLayout()
            }
        } completion: { _ in
            completion()
        }
    }

    func markGroupUnansweredItemHandledIfNeeded(_ focus: UnansweredTimelineFocus) {
        guard focus.itemID.discriminator.hasPrefix("group:"),
              let index = state.messages.firstIndex(where: { $0.id == focus.messageID }),
              let metadata = state.messages[index].unansweredMetadata
        else { return }
        let updated = state.messages[index].replacingUnansweredMetadata(
            metadata.markingHandled(at: Date())
        )
        state.messages[index] = updated
        persistMessageMutations(
            [.update(updated)],
            invalidatesVisibleDataCaches: false
        )
    }

    func selectFriend(
        id: UUID?,
        focusMessageID: UUID? = nil,
        restoreHomeOffsetY: CGFloat? = nil
    ) {
        let signpostID = PerformanceSignpost.begin("SelectFriend")
        conversationSelectionRevision += 1
        let selectionRevision = conversationSelectionRevision
        cancelOutstandingCollectionPrefetches()
        cancelLeftScrollPreview()
        cancelConversationHeightPrewarming()
        connectionOverlay.anchors = []
        if let id {
            if case .conversation(_, let routedConversationID, _, _) = chatWindowRoute,
               routedConversationID == id {
                // Preserve an unanswered origin established by the message tap.
            } else {
                setConversationRoute(
                    accountID: state.selectedAccountID,
                    conversationID: id,
                    unansweredOrigin: nil,
                    directoryOriginAccountID: chatWindowRoute.accountDirectoryID
                )
            }
        } else if chatWindowRoute.unansweredScope == nil,
                  !isAccountDirectory {
            setUnansweredTimelineRoute(scope: .all, focus: nil)
        }
        if let id,
           let participant = participantLookupByID()[id],
           let accountID = accountIDForDirectConversation(participant, preferredAccountID: state.selectedAccountID),
            accountID != state.selectedAccountID {
            state.selectedAccountID = accountID
            let origin = unansweredNavigationOrigin.flatMap {
                $0.accountID == accountID ? $0 : nil
            }
            setConversationRoute(
                accountID: accountID,
                conversationID: id,
                unansweredOrigin: origin,
                directoryOriginAccountID: chatWindowRoute.accountDirectoryID
            )
        }
        state.selectedFriendID = id
        openApiHistoryPaginationTriggerConversationID = nil
        syncOpenApiHistoryForSelectedConversation(id)
        invalidateSelectionVisibleDataCaches()
        if id == nil {
            resetHomeMessageWindow()
        }
        ensureVerticalMessageLayout()
        setQuotedMessage(nil)
        updateHeaderForSelection()
        leftCollectionView.collectionViewLayout.invalidateLayout()
        messageCollectionView.collectionViewLayout.invalidateLayout()

        prepareCurrentMessageHeightsForReload { [weak self] in
            defer { PerformanceSignpost.end("SelectFriend", id: signpostID) }
            guard let self, self.conversationSelectionRevision == selectionRevision else { return }
            UIView.performWithoutAnimation {
                self.leftCollectionView.reloadData()
                self.reloadVisibleItems(
                    in: self.rightAccountCollectionView,
                    expectedItemCount: self.visibleRightAccountItems().count
                )
                self.reloadVisibleItems(
                    in: self.rightToolCollectionView,
                    expectedItemCount: self.visibleRightToolItems().count
                )
                self.messageCollectionView.reloadData()
                self.updateLeftCollectionBounceInsets()
            }
            self.messageCollectionView.layoutIfNeeded()
            self.updateLeftCollectionBounceInsets()
            self.ensureSelectedRightAccountItemVisible(animated: true)
            self.ensureSelectedLeftSidebarItemVisible(animated: false)
            if id == nil, let restoreHomeOffsetY {
                let minimumY = -self.messageCollectionView.adjustedContentInset.top
                let maximumY = max(
                    minimumY,
                    self.messageCollectionView.contentSize.height
                        - self.messageCollectionView.bounds.height
                        + self.messageCollectionView.adjustedContentInset.bottom
                )
                self.messageCollectionView.setContentOffset(
                    CGPoint(
                        x: self.messageCollectionView.contentOffset.x,
                        y: min(max(restoreHomeOffsetY, minimumY), maximumY)
                    ),
                    animated: false
                )
                self.syncUnreadTimelineScroll(from: self.messageCollectionView)
                self.updateConnections()
                self.scheduleConversationHeightPrewarming()
                return
            }
            if let focusMessageID {
                self.scrollToMessage(id: focusMessageID, animated: false)
            } else if let indexPath = self.lastRenderedMessageIndexPath() {
                self.messageCollectionView.scrollToItem(
                    at: indexPath,
                    at: .bottom,
                    animated: false
                )
                if id == nil {
                    self.syncUnreadTimelineScroll(from: self.messageCollectionView)
                    self.updateConnections()
                    self.scheduleConversationHeightPrewarming()
                    return
                }
            }
            self.scheduleConnectionUpdate()
            self.scheduleConversationHeightPrewarming()
        }
    }

    func pulseMessage(at indexPath: IndexPath) {
        guard let cell = messageCollectionView.cellForItem(at: indexPath) else { return }
        UIView.animate(withDuration: 0.12, animations: {
            cell.transform = CGAffineTransform(scaleX: 1.025, y: 1.025)
        }, completion: { _ in
            UIView.animate(withDuration: 0.18) {
                cell.transform = .identity
            }
        })
    }

    func openBoundParticipant(for message: ChatMessage) {
        if visibleLeftItemsForSelectedAccount().contains(where: { $0.participantID == message.conversationID }) {
            selectFriend(id: message.conversationID, focusMessageID: message.id)
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.28) {
                if let index = self.visibleLeftItemsForSelectedAccount().firstIndex(where: { $0.participantID == message.conversationID }) {
                    let indexPath = IndexPath(item: index, section: 0)
                    self.leftCollectionView.scrollToItem(at: indexPath, at: .centeredVertically, animated: true)
                    self.pulseSidebarItem(in: self.leftCollectionView, at: indexPath)
                }
            }
            return
        }

        let candidateIDs = message.isOutgoing
            ? [message.sender.id, message.conversationID]
            : [message.sender.id, message.conversationID]

        if let participantID = candidateIDs.first(where: { id in
            visibleLeftItemsForSelectedAccount().contains(where: { $0.participantID == id })
        }) {
            selectFriend(id: participantID, focusMessageID: message.id)
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.28) {
                if let index = self.visibleLeftItemsForSelectedAccount().firstIndex(where: { $0.participantID == participantID }) {
                    let indexPath = IndexPath(item: index, section: 0)
                    self.leftCollectionView.scrollToItem(at: indexPath, at: .centeredVertically, animated: true)
                    self.pulseSidebarItem(in: self.leftCollectionView, at: indexPath)
                }
            }
            return
        }

        let rightItems = visibleRightAccountItems()
        if let participantID = candidateIDs.first(where: { id in
            rightItems.contains(where: { $0.participantID == id })
        }), let index = rightItems.firstIndex(where: { $0.participantID == participantID }) {
            let indexPath = IndexPath(item: index, section: 0)
            rightAccountCollectionView.scrollToItem(at: indexPath, at: .centeredVertically, animated: true)
            pulseSidebarItem(in: rightAccountCollectionView, at: indexPath)
            return
        }

        if let index = visibleLeftItemsForSelectedAccount().firstIndex(where: { $0.participantID == message.conversationID }) {
            let indexPath = IndexPath(item: index, section: 0)
            leftCollectionView.scrollToItem(at: indexPath, at: .centeredVertically, animated: true)
            pulseSidebarItem(in: leftCollectionView, at: indexPath)
            return
        }
        if let index = rightItems.firstIndex(where: { $0.participantID == message.sender.id }) {
            let indexPath = IndexPath(item: index, section: 0)
            rightAccountCollectionView.scrollToItem(at: indexPath, at: .centeredVertically, animated: true)
            pulseSidebarItem(in: rightAccountCollectionView, at: indexPath)
        }
    }

    func handleMessageTap(_ message: ChatMessage) {
        if message.type == .quotedReply {
            openQuotedOriginal(for: message)
            return
        }
        if state.selectedFriendID == nil {
            openBoundParticipant(for: message)
            return
        }
        performMessageAction(message)
    }

    func performMessageAction(_ message: ChatMessage) {
        if isAutoReplyPrediction(message) {
            presentAutoReplyPredictionActions(for: message)
            return
        }

        if resolveOpenApiMediaIfNeeded(for: message) {
            return
        }

        if previewAttachment(for: message) {
            return
        }

        switch message.type {
        case .voiceCall:
            presentCallConfirmation(for: message, isVideo: false)
        case .videoCall:
            presentCallConfirmation(for: message, isVideo: true)
        case .location:
            presentLocationPicker(messageType: .location, seedMessage: message)
        case .liveLocation:
            presentLiveLocationViewer(for: message)
        case .contactCard:
            if let profile = message.contactCard {
                presentContactCardDetail(profile)
            } else if let account = state.participants.first(where: { $0.displayName == message.body || $0.displayName == message.detail }) {
                presentContactCardDetail(contactCardProfile(for: account))
            } else {
                showMessageActionNotice("查看个人名片：\(message.body)")
            }
        case .groupInvite:
            presentGroupInviteAcceptance(for: message)
        case .webLink:
            openWebLinkMessageWithAccessCheck(message)
        case .miniProgram:
            openMiniProgramCard(message)
        case .article:
            presentArticleDetail(for: message)
        case .channelsLive:
            presentChannelsLiveDetail(for: message)
        case .music:
            presentMusicPlayer(for: message)
        case .favorite:
            presentFavoriteDetail(for: message)
        case .mergedForward:
            presentMergedForwardDetail(for: message)
        case .redPacket:
            presentReceiveRedPacket(for: message)
        case .transfer:
            presentReceiveTransfer(for: message)
        case .splitBill:
            presentPaySplitBill(for: message)
        case .coupon:
            presentCouponDetail(for: message)
        case .quotedReply:
            openQuotedOriginal(for: message)
        case .relay:
            presentRelayEditor(for: message)
        case .groupNotice:
            presentNoticeDetail(for: message)
        case .image, .capturedPhoto:
            presentMockMediaPreview(for: message, symbolName: "photo", title: "图片预览")
        case .video:
            presentMockMediaPreview(for: message, symbolName: "play.rectangle.fill", title: "视频预览")
        case .channelsVideo:
            presentChannelsVideoDetail(for: message)
        case .voice:
            playVoiceMessage(message)
        case .file:
            presentMockFilePreview(for: message)
        case .stickerGif:
            presentMockMediaPreview(for: message, symbolName: "sparkles.rectangle.stack", title: "表情预览")
        case .emoji:
            showMessageActionNotice(message.body)
        case .text:
            break
        case .system:
            presentNoticeDetail(for: message)
        }
    }

    func presentAutoReplyPredictionActions(for message: ChatMessage) {
        let previewText = latestAutoReplyPredictionBody(for: message)
        let planStatus = autoReplyCalendarStatus(fromMessageID: message.id)
        let controller = AutoReplyPredictionActionSheetViewController(
            previewText: previewText,
            planStatus: planStatus,
            draftStatus: autoReplyDraftState(from: message).rawValue
        )
        controller.onSelect = { [weak self, weak controller] action in
            guard let self else { return }
            controller?.dismiss(animated: true) {
                switch action {
                case .segment:
                    self.presentDraftSegmentSelection(for: message)
                case .speech:
                    self.speakAutoReplyPrediction(message)
                case .edit:
                    self.presentDraftEditor(for: message)
                case .rewrite:
                    self.transformAutoReplyPrediction(message, mode: .rewrite)
                case .expand:
                    self.transformAutoReplyPrediction(message, mode: .expand)
                case .polish:
                    self.transformAutoReplyPrediction(message, mode: .polish)
                case .send:
                    self.sendAutoReplyPrediction(message)
                case .delete:
                    self.deleteAutoReplyPrediction(message)
                case .executePlan:
                    self.executeAutoReplyPlan(for: message)
                case .retryPlan:
                    self.retryAutoReplyPlan(for: message)
                case .revokePlan:
                    self.revokeAutoReplyCalendarEvent(for: message)
                }
            }
        }
        controller.modalPresentationStyle = .pageSheet
        if let sheet = controller.sheetPresentationController {
            sheet.detents = [.medium(), .large()]
            sheet.prefersGrabberVisible = true
            sheet.preferredCornerRadius = 22
        }
        present(controller, animated: true)
    }

    enum DraftRewriteMode {
        case rewrite
        case expand
        case polish

        var loadingText: String {
            switch self {
            case .rewrite: return "AI正在全部重写..."
            case .expand: return "AI正在增写..."
            case .polish: return "AI正在润色..."
            }
        }

        func prompt(for text: String) -> String {
            switch self {
            case .rewrite:
                return "请完全重写下面这条聊天回复草稿，保留核心意思，但换一种自然、简短、适合微信聊天发送的表达。只返回改写后的正文：\n\(text)"
            case .expand:
                return "请在下面这条聊天回复草稿基础上适当增写一点信息，让它更完整但仍然自然简洁。只返回增写后的正文：\n\(text)"
            case .polish:
                return "请润色下面这条聊天回复草稿，让语气更自然、清楚、礼貌，适合直接发送。只返回润色后的正文：\n\(text)"
            }
        }
    }

    func latestAutoReplyPredictionBody(for message: ChatMessage) -> String {
        state.messages.first(where: { $0.id == message.id })?.body ?? message.body
    }

    func replaceAutoReplyPredictionBody(id: UUID, body: String) {
        let cleaned = body.trimmingCharacters(in: .whitespacesAndNewlines)
        guard let current = state.messages.first(where: { $0.id == id }),
              let plan = autoReplyIntentPlan(from: current)
        else {
            updateAutoReplyStreamingMessage(id: id, body: cleaned.isEmpty ? "收到，我稍后回复你。" : cleaned)
            return
        }
        updateAutoReplyStreamingMessage(
            id: id,
            body: autoReplyDisplayBody(
                reply: cleaned.isEmpty ? "收到，我稍后回复你。" : cleaned,
                plan: plan,
                calendarStatus: autoReplyCalendarStatus(fromMessageID: id)
            )
        )
    }

    func presentDraftSegmentSelection(for message: ChatMessage) {
        let body = suggestedReplyText(fromAutoReplyBody: latestAutoReplyPredictionBody(for: message))
        let segments = draftSegments(in: body)
        guard !segments.isEmpty else {
            showNotice("草稿为空")
            return
        }
        let controller = DraftSegmentSelectionViewController(segments: segments)
        controller.onReplace = { [weak self] selectedText in
            self?.replaceAutoReplyPredictionBody(id: message.id, body: selectedText)
            self?.dismiss(animated: true)
        }
        controller.onAppend = { [weak self] selectedText in
            guard let self else { return }
            let current = self.suggestedReplyText(fromAutoReplyBody: self.latestAutoReplyPredictionBody(for: message))
            let separator = current.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? "" : "\n"
            self.replaceAutoReplyPredictionBody(id: message.id, body: current + separator + selectedText)
            self.dismiss(animated: true)
        }
        controller.onCopy = { [weak self] selectedText in
            UIPasteboard.general.string = selectedText
            self?.showNotice("已复制选中内容")
        }
        controller.onClose = { [weak self] in
            self?.dismiss(animated: true)
        }
        let navigationController = UINavigationController(rootViewController: controller)
        navigationController.modalPresentationStyle = .pageSheet
        if let sheet = navigationController.sheetPresentationController {
            sheet.detents = [.medium(), .large()]
            sheet.prefersGrabberVisible = true
        }
        present(navigationController, animated: true)
    }

    func draftSegments(in text: String) -> [String] {
        let normalized = text
            .replacingOccurrences(of: "\r\n", with: "\n")
            .trimmingCharacters(in: .whitespacesAndNewlines)
        guard !normalized.isEmpty else { return [] }

        var sentenceSegments: [String] = []
        var current = ""
        let punctuation = CharacterSet(charactersIn: "，。！？；、,.!?;:：\n")
        for scalar in normalized.unicodeScalars {
            current.unicodeScalars.append(scalar)
            if punctuation.contains(scalar) {
                let trimmed = current.trimmingCharacters(in: .whitespacesAndNewlines)
                if !trimmed.isEmpty {
                    sentenceSegments.append(trimmed)
                }
                current = ""
            }
        }
        let tail = current.trimmingCharacters(in: .whitespacesAndNewlines)
        if !tail.isEmpty {
            sentenceSegments.append(tail)
        }

        var segments: [String] = []
        for sentence in sentenceSegments.isEmpty ? [normalized] : sentenceSegments {
            segments.append(sentence)
            let words = sentence
                .replacingOccurrences(of: "\n", with: " ")
                .components(separatedBy: .whitespacesAndNewlines)
                .flatMap { $0.components(separatedBy: CharacterSet(charactersIn: "，。！？；、,.!?;:：")) }
                .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
                .filter { !$0.isEmpty && $0.count < sentence.count }
            segments.append(contentsOf: words)
            if sentence.count > 12 && words.count <= 1 {
                segments.append(contentsOf: chunkChineseLikeText(sentence))
            }
        }

        var seen = Set<String>()
        return segments.filter { segment in
            let cleaned = segment.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !cleaned.isEmpty, !seen.contains(cleaned) else { return false }
            seen.insert(cleaned)
            return true
        }
    }

    func chunkChineseLikeText(_ text: String) -> [String] {
        let cleaned = text.trimmingCharacters(in: CharacterSet(charactersIn: "，。！？；、,.!?;:： ").union(.newlines))
        guard cleaned.count > 8 else { return [] }
        var chunks: [String] = []
        var current = ""
        for character in cleaned {
            current.append(character)
            if current.count >= 6 {
                chunks.append(current)
                current = ""
            }
        }
        if !current.isEmpty {
            if let last = chunks.indices.last, current.count <= 2 {
                chunks[last].append(current)
            } else {
                chunks.append(current)
            }
        }
        return chunks
    }

    func speakAutoReplyPrediction(_ message: ChatMessage) {
        if draftSpeechSynthesizer.isSpeaking {
            draftSpeechSynthesizer.stopSpeaking(at: .immediate)
        }
        let body = suggestedReplyText(fromAutoReplyBody: latestAutoReplyPredictionBody(for: message))
            .trimmingCharacters(in: .whitespacesAndNewlines)
        guard !body.isEmpty else {
            showNotice("草稿为空")
            return
        }
        try? AVAudioSession.sharedInstance().setCategory(.playback, mode: .spokenAudio, options: [.duckOthers])
        try? AVAudioSession.sharedInstance().setActive(true)
        let utterance = AVSpeechUtterance(string: body)
        utterance.voice = AVSpeechSynthesisVoice(language: "zh-CN")
        utterance.rate = 0.48
        draftSpeechSynthesizer.speak(utterance)
        showNotice("正在朗读草稿")
    }

    func presentDraftEditor(for message: ChatMessage) {
        let controller = DraftEditorViewController(text: suggestedReplyText(fromAutoReplyBody: latestAutoReplyPredictionBody(for: message)))
        controller.onSave = { [weak self] text in
            self?.replaceAutoReplyPredictionBody(id: message.id, body: text)
            self?.setAutoReplyDraftState(messageID: message.id, state: .ready)
            self?.dismiss(animated: true)
        }
        controller.onCancel = { [weak self] in
            self?.dismiss(animated: true)
        }
        let navigationController = UINavigationController(rootViewController: controller)
        navigationController.modalPresentationStyle = .pageSheet
        if let sheet = navigationController.sheetPresentationController {
            sheet.detents = [.medium(), .large()]
            sheet.prefersGrabberVisible = true
        }
        present(navigationController, animated: true)
    }

    func transformAutoReplyPrediction(_ message: ChatMessage, mode: DraftRewriteMode) {
        guard !autoReplyActiveConversationIDs.contains(message.conversationID) else {
            showNotice("AI 正在生成中")
            return
        }
        let originalBody = suggestedReplyText(fromAutoReplyBody: latestAutoReplyPredictionBody(for: message))
            .trimmingCharacters(in: .whitespacesAndNewlines)
        guard !originalBody.isEmpty else {
            showNotice("草稿为空")
            return
        }
        autoReplyActiveConversationIDs.insert(message.conversationID)
        streamingAIMessageIDs.insert(message.id)
        setAutoReplyDraftState(messageID: message.id, state: .generating)
        replaceAutoReplyPredictionBody(id: message.id, body: mode.loadingText)
        var streamedText = ""
        streamAutoReplyMessage(prompt: mode.prompt(for: originalBody)) { [weak self] delta in
            DispatchQueue.main.async {
                streamedText += delta
                self?.replaceAutoReplyPredictionBody(id: message.id, body: streamedText)
            }
        } completion: { [weak self] result in
            DispatchQueue.main.async {
                guard let self else { return }
                self.autoReplyActiveConversationIDs.remove(message.conversationID)
                switch result {
                case .success:
                    if streamedText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                        self.replaceAutoReplyPredictionBody(id: message.id, body: originalBody)
                    }
                    self.finishAIStreamingMessage(id: message.id)
                    self.setAutoReplyDraftState(messageID: message.id, state: .ready)
                case .failure(let error):
                    self.replaceAutoReplyPredictionBody(id: message.id, body: streamedText.isEmpty ? originalBody : streamedText)
                    self.finishAIStreamingMessage(id: message.id)
                    self.setAutoReplyDraftState(
                        messageID: message.id,
                        state: .generationFailed,
                        failureReason: error.localizedDescription
                    )
                    self.showNotice("草稿处理失败：\(error.localizedDescription)")
                }
                self.reloadVisibleRightToolItems()
            }
        }
    }

    func sendAutoReplyPrediction(
        _ message: ChatMessage,
        allowsOfflineRecovery: Bool = true
    ) {
        guard let index = state.messages.firstIndex(where: { $0.id == message.id }) else { return }
        let current = state.messages[index]
        guard !autoReplySendingMessageIDs.contains(current.id) else {
            showNotice("草稿正在发送，请等待后台回执")
            return
        }
        guard canSendAutoReplyDraft(current) else {
            showNotice("当前草稿尚未生成成功，请先重新生成或编辑后保存")
            return
        }
        let sourceRecipientAccountID = autoReplySourceMessageID(from: current)
            .flatMap { sourceMessageID in
                state.messages.first(where: { $0.id == sourceMessageID })?.recipientAccountID
            }
        let recipientAccountID: UUID? = {
            guard let group = state.activeFriend,
                  isGroupConversation(group),
                  group.id == current.conversationID
            else {
                return sourceRecipientAccountID
                    ?? current.recipientAccountID
                    ?? state.selectedAccountID
            }
            return groupAccountID(for: current, in: group)
        }()
        let cleanBody = suggestedReplyText(fromAutoReplyBody: current.body)
            .trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanBody.isEmpty else {
            showNotice("草稿为空，请编辑或重新生成后再发送")
            return
        }
        let outgoingSender = recipientAccountID
            .flatMap { accountID in state.currentUsers.first(where: { $0.id == accountID }) }
            ?? current.sender
        let outgoingMessage = ChatMessage(
            id: current.id,
            conversationID: current.conversationID,
            type: current.type,
            sender: outgoingSender,
            body: cleanBody,
            detail: "",
            isOutgoing: true,
            presentation: outgoingPresentationForSelectedAccount(default: current.presentation),
            timestamp: current.timestamp,
            sentAt: current.sentAt,
            backendMessageID: current.backendMessageID,
            attachmentURL: current.attachmentURL,
            isAI: false,
            isGroupConversation: current.isGroupConversation,
            richElements: richElements(from: cleanBody),
            contactCard: current.contactCard,
            contactCardAccountID: current.contactCardAccountID,
            quotedMessageID: current.quotedMessageID,
            mergedForwardMessages: current.mergedForwardMessages,
            recipientAccountID: recipientAccountID,
            unansweredMetadata: current.unansweredMetadata
        )
        registerUnansweredReplyOriginIfNeeded(for: outgoingMessage.id)
        if allowsOfflineRecovery,
           let sendContext = openApiSendContext(for: outgoingMessage),
           !sendContext.account.isOnline {
            refreshOfflineAccountAndRetryAutoReply(
                messageID: current.id,
                accountID: sendContext.account.participantID
            )
            return
        }
        autoReplySendingMessageIDs.insert(current.id)
        setAutoReplyDraftState(messageID: current.id, state: .sending)
        let isSendingRemotely = sendOpenApiMessageIfPossible(
            outgoingMessage,
            localMessageAlreadyCommitted: false
        ) { [weak self] result in
            guard let self else { return }
            self.autoReplySendingMessageIDs.remove(current.id)
            switch result {
            case .success:
                guard let latestIndex = self.state.messages.firstIndex(where: { $0.id == current.id }) else { return }
                self.state.messages[latestIndex] = outgoingMessage
                self.streamingAIMessageIDs.remove(current.id)
                self.persistMessageMutations(
                    [.update(outgoingMessage)],
                    invalidatesVisibleDataCaches: false
                )
                self.syncIslandActivityIfNeeded()
                let completedUnansweredItem = self
                    .completeUnansweredReplyAfterSuccessfulSend(outgoingMessage)
                if !completedUnansweredItem {
                    self.reloadRenderedMessageItem(messageID: current.id)
                    self.reloadVisibleRightToolItems()
                }
            case .failure(let error):
                if self.isOpenApiTaskResultUnknown(error) {
                    self.setAutoReplyDraftState(
                        messageID: current.id,
                        state: .sendUnconfirmed,
                        failureReason: error.localizedDescription
                    )
                    self.scheduleAutoReplyOutcomeReconciliation(
                        messageID: current.id,
                        conversationID: current.conversationID
                    )
                    self.showNotice("发送结果待确认，正在同步聊天记录，请勿重复发送")
                    return
                }
                self.setAutoReplyDraftState(
                    messageID: current.id,
                    state: .sendFailed,
                    failureReason: error.localizedDescription
                )
                if allowsOfflineRecovery,
                   self.isOpenApiDeviceOfflineError(error) {
                    let accountID = outgoingMessage.recipientAccountID
                        ?? outgoingMessage.sender.id
                    self.refreshOfflineAccountAndRetryAutoReply(
                        messageID: current.id,
                        accountID: accountID
                    )
                }
            }
        }
        guard isSendingRemotely else {
            autoReplySendingMessageIDs.remove(current.id)
            setAutoReplyDraftState(
                messageID: current.id,
                state: .sendFailed,
                failureReason: "未找到匹配的后端帐号或会话"
            )
            showNotice("未找到匹配的后端帐号或会话，草稿未发送")
            return
        }
        showNotice("正在真实发送，等待后台回执")
    }

    func isOpenApiDeviceOfflineError(_ error: Error) -> Bool {
        let message = error.localizedDescription
        return message.contains("设备未在线")
            || message.contains("连接不存在")
            || message.contains("没有在线微信账号")
            || message.contains("没有在线微信帐号")
            || message.contains("无在线微信账号")
            || message.contains("无在线微信帐号")
            || message.lowercased().contains("device offline")
            || message.lowercased().contains("no online wechat")
    }

    func isOpenApiTaskResultUnknown(_ error: Error) -> Bool {
        if let mediaError = error as? OpenApiMediaSendError,
           case .taskResultUnknown = mediaError {
            return true
        }
        return openApiTaskResultMessageIsUnknown(error.localizedDescription)
    }

    func scheduleAutoReplyOutcomeReconciliation(
        messageID: UUID,
        conversationID: UUID
    ) {
        for delay in [0.8, 4.0, 10.0] {
            DispatchQueue.main.asyncAfter(deadline: .now() + delay) { [weak self] in
                guard let self,
                      let message = self.state.messages.first(where: { $0.id == messageID }),
                      self.isAutoReplyPrediction(message),
                      self.autoReplyDraftState(from: message) == .sendUnconfirmed
                else { return }
                self.syncOpenApiHistoryForSelectedConversation(conversationID)
            }
        }
        DispatchQueue.main.asyncAfter(deadline: .now() + 18) { [weak self] in
            guard let self,
                  let message = self.state.messages.first(where: { $0.id == messageID }),
                  self.isAutoReplyPrediction(message),
                  self.autoReplyDraftState(from: message) == .sendUnconfirmed
            else { return }
            self.setAutoReplyDraftState(
                messageID: messageID,
                state: .sendFailed,
                failureReason: "持续同步后仍未发现对应的发送记录，可确认好友未收到后重试"
            )
            self.showNotice("仍未发现发送记录，请确认好友未收到后重试")
        }
    }

    func refreshOfflineAccountAndRetryAutoReply(
        messageID: UUID,
        accountID: UUID
    ) {
        setAutoReplyDraftState(
            messageID: messageID,
            state: .sendFailed,
            failureReason: "微信设备离线，正在刷新帐号连接状态"
        )
        showNotice("微信设备离线，正在刷新帐号状态；草稿已保留")
        syncOpenApiIMData(force: true, showsNotice: false) { [weak self] success in
            guard let self,
                  let latest = self.state.messages.first(where: { $0.id == messageID })
            else { return }
            guard success,
                  self.openApiAccountContextsByID[accountID]?.isOnline == true
            else {
                self.setAutoReplyDraftState(
                    messageID: messageID,
                    state: .sendFailed,
                    failureReason: "微信设备仍处于离线状态，请设备上线后再次发送"
                )
                self.showNotice("微信设备仍离线，草稿已保留，可上线后重试")
                return
            }
            self.showNotice("微信设备已恢复在线，正在重试发送")
            self.sendAutoReplyPrediction(latest, allowsOfflineRecovery: false)
        }
    }

}
