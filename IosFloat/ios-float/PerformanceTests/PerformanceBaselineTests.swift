import XCTest
@testable import ios_float

final class PerformanceBaselineTests: XCTestCase {
    private static let largeCorpus = PerformanceDataFixtures.makeLargeCorpus()
    private static let unreadTimeline = PerformanceDataFixtures.makeUnreadTimeline()

    // Clock metrics are intentionally baseline-only. Device-dependent wall-time
    // limits belong in the dedicated on-device performance run, not unit tests.

    func testFixturesMatchGoalScaleAndRemainDeterministic() {
        let corpus = Self.largeCorpus
        XCTAssertEqual(corpus.accounts.count, 10)
        XCTAssertEqual(corpus.conversations.count, 2_000)
        XCTAssertEqual(corpus.messages.count, 50_000)
        XCTAssertEqual(Set(corpus.messages.map(\.conversationID)).count, 2_000)
        XCTAssertEqual(Set(corpus.messages.compactMap(\.recipientAccountID)), Set(corpus.accounts.map(\.id)))
        XCTAssertEqual(corpus.messages.first?.id.uuidString, "50450004-0000-0000-0000-000000000000")
        XCTAssertEqual(corpus.messages.last?.id.uuidString, "50450004-0000-0000-0000-00000000C34F")

        let unread = Self.unreadTimeline
        XCTAssertEqual(unread.accounts.count, 3)
        XCTAssertEqual(unread.conversations.count, 200)
        XCTAssertEqual(unread.messages.count, 1_000)
        XCTAssertEqual(Set(unread.messages.map(\.conversationID)).count, 200)
        XCTAssertEqual(Set(unread.messages.compactMap(\.recipientAccountID)), Set(unread.accounts.map(\.id)))
        XCTAssertTrue(unread.messages.allSatisfy { !$0.isOutgoing })

        let detail = PerformanceDataFixtures.makeDetailConversation()
        XCTAssertEqual(detail.conversations.count, 1)
        XCTAssertEqual(detail.messages.count, 2_000)
        XCTAssertEqual(Set(detail.messages.map(\.conversationID)).count, 1)
        XCTAssertTrue(detail.messages.allSatisfy(\.isGroupConversation))
        let types = Set(detail.messages.map(\.type))
        XCTAssertTrue([.image, .stickerGif, .video, .file, .voice].allSatisfy(types.contains))
        XCTAssertEqual(Set(detail.messages.compactMap(\.recipientAccountID)), Set(detail.accounts.map(\.id)))
    }

    func testReferenceUnreadAggregationPreservesAllIncomingFixtureMessages() {
        let fixture = Self.unreadTimeline
        let pending = PerformanceDataFixtures.aggregatePendingReplies(
            messages: fixture.messages,
            accountIDs: Set(fixture.accounts.map(\.id))
        )

        XCTAssertEqual(pending.count, 1_000)
        XCTAssertEqual(Set(pending.map(\.id)), Set(fixture.messages.map(\.id)))
        XCTAssertEqual(pending.map(\.sentAt), pending.map(\.sentAt).sorted())
    }

    func testPendingReplyIndexMatchesReferenceAcrossIncrementalMutations() throws {
        let fixture = Self.unreadTimeline
        let accountIDs = Set(fixture.accounts.map(\.id))
        let fallbackAccountID = try XCTUnwrap(fixture.accounts.first?.id)
        let keyForMessage: (ChatMessage) -> PendingReplyConversationAccountKey = { message in
            PendingReplyConversationAccountKey(
                conversationID: message.conversationID,
                accountID: message.recipientAccountID ?? fallbackAccountID
            )
        }
        let isRelevant: (ChatMessage) -> Bool = {
            !$0.isAI && !$0.sender.isAIAccount && $0.type != .system && $0.type != .groupNotice
        }
        let isIncoming: (ChatMessage) -> Bool = {
            !$0.isOutgoing && !$0.sender.isCurrentUser && !accountIDs.contains($0.sender.id)
        }

        var messages = fixture.messages
        var index = PendingReplyIndex()
        index.rebuild(
            messages: messages,
            keyForMessage: keyForMessage,
            isRelevant: isRelevant,
            isIncoming: isIncoming
        )
        assertPendingReplyIndex(
            index,
            matches: messages,
            accountIDs: accountIDs
        )

        let seed = try XCTUnwrap(messages.first)
        let account = try XCTUnwrap(fixture.accounts.first { $0.id == seed.recipientAccountID })
        let outgoing = ChatMessage(
            id: UUID(uuidString: "50450006-0000-0000-0000-000000000001")!,
            conversationID: seed.conversationID,
            type: .text,
            sender: account,
            body: "incremental outgoing cutoff",
            detail: "",
            isOutgoing: true,
            presentation: .bare,
            timestamp: "12:00",
            sentAt: seed.sentAt.addingTimeInterval(10_000),
            recipientAccountID: account.id
        )
        messages.append(outgoing)
        index.apply(
            [.insert(outgoing)],
            keyForMessage: keyForMessage,
            isRelevant: isRelevant,
            isIncoming: isIncoming
        )
        assertPendingReplyIndex(index, matches: messages, accountIDs: accountIDs)

        let incoming = ChatMessage(
            id: UUID(uuidString: "50450006-0000-0000-0000-000000000002")!,
            conversationID: seed.conversationID,
            type: .text,
            sender: seed.sender,
            body: "incremental incoming reply",
            detail: "",
            isOutgoing: false,
            presentation: .bare,
            timestamp: "12:01",
            sentAt: outgoing.sentAt.addingTimeInterval(1),
            recipientAccountID: account.id
        )
        messages.append(incoming)
        index.apply(
            [.insert(incoming)],
            keyForMessage: keyForMessage,
            isRelevant: isRelevant,
            isIncoming: isIncoming
        )
        assertPendingReplyIndex(index, matches: messages, accountIDs: accountIDs)

        let updatedIncoming = replacingBody(of: incoming, with: "updated incremental reply")
        messages[messages.count - 1] = updatedIncoming
        index.apply(
            [.update(updatedIncoming)],
            keyForMessage: keyForMessage,
            isRelevant: isRelevant,
            isIncoming: isIncoming
        )
        assertPendingReplyIndex(index, matches: messages, accountIDs: accountIDs)

        messages.removeAll { $0.id == outgoing.id }
        index.apply(
            [.delete(messageID: outgoing.id, affectedConversationID: outgoing.conversationID)],
            keyForMessage: keyForMessage,
            isRelevant: isRelevant,
            isIncoming: isIncoming
        )
        assertPendingReplyIndex(index, matches: messages, accountIDs: accountIDs)
        XCTAssertEqual(index.fullRebuildCount, 1)
        XCTAssertEqual(index.incrementalMutationCount, 4)
    }

    func testLookupIndexCoversEveryMessageExactlyOnce() {
        let fixture = Self.largeCorpus
        let index = PerformanceDataFixtures.makeLookupIndex(messages: fixture.messages)

        XCTAssertEqual(index.messageOffsetByID.count, 50_000)
        XCTAssertEqual(index.messageOffsetsByConversation.count, 2_000)
        XCTAssertEqual(index.latestMessageOffsetByConversation.count, 2_000)
        XCTAssertEqual(index.messageOffsetsByConversation.values.reduce(0) { $0 + $1.count }, 50_000)
        XCTAssertTrue(index.messageOffsetsByConversation.values.allSatisfy { $0.count == 25 })
    }

    func testMessageMutationPlannerEmitsOnlyChangedRows() throws {
        let original = try XCTUnwrap(Self.unreadTimeline.messages.first)
        var planner = MessageMutationPlanner()

        let initial = planner.plan(for: [original])
        XCTAssertEqual(initial.count, 1)
        guard case .insert(let inserted) = initial[0] else {
            return XCTFail("Expected an insert for an unseen message")
        }
        XCTAssertEqual(inserted.id, original.id)
        XCTAssertTrue(planner.plan(for: [original]).isEmpty)

        let updated = replacingBody(of: original, with: original.body + " updated")
        let updates = planner.plan(for: [updated])
        XCTAssertEqual(updates.count, 1)
        guard case .update(let changed) = updates[0] else {
            return XCTFail("Expected an update for changed persisted content")
        }
        XCTAssertEqual(changed.body, updated.body)

        let deletions = planner.plan(for: [])
        XCTAssertEqual(deletions.count, 1)
        guard case .delete(let messageID, let conversationID) = deletions[0] else {
            return XCTFail("Expected a typed delete for a removed message")
        }
        XCTAssertEqual(messageID, original.id)
        XCTAssertEqual(conversationID, original.conversationID)
    }

    func testSQLiteMessageMutationsRoundTripWithoutFullReplacement() throws {
        let temporaryDirectory = FileManager.default.temporaryDirectory
            .appendingPathComponent(UUID().uuidString, isDirectory: true)
        try FileManager.default.createDirectory(
            at: temporaryDirectory,
            withIntermediateDirectories: true
        )
        defer { try? FileManager.default.removeItem(at: temporaryDirectory) }

        let store = ChatSQLiteStore(
            databaseURL: temporaryDirectory.appendingPathComponent("mutations.sqlite")
        )
        let original = try XCTUnwrap(Self.unreadTimeline.messages.first)
        let updated = replacingBody(of: original, with: "incremental sqlite update")

        store.applyMessageMutations([.insert(original)])
        store.waitForPendingWrites()
        XCTAssertEqual(
            store.loadMessages(participants: [original.sender]).first?.body,
            original.body
        )
        XCTAssertEqual(
            store.loadMessages(participants: [original.sender]).first?.unansweredMetadata,
            original.unansweredMetadata
        )

        store.applyMessageMutations([.update(updated)])
        store.waitForPendingWrites()
        let updatedMessages = store.loadMessages(participants: [original.sender])
        XCTAssertEqual(updatedMessages.count, 1)
        XCTAssertEqual(updatedMessages.first?.id, original.id)
        XCTAssertEqual(updatedMessages.first?.body, updated.body)

        store.applyMessageMutations([
            .delete(
                messageID: original.id,
                affectedConversationID: original.conversationID
            )
        ])
        store.waitForPendingWrites()
        XCTAssertTrue(store.loadMessages(participants: [original.sender]).isEmpty)
    }

    func testUnreadPaginationAddsFortyMessagesAndPreservesVisibleAnchor() throws {
        let fixture = Self.unreadTimeline
        let controller = ChatWindowViewController()
        let selectedAccount = try XCTUnwrap(fixture.accounts.first)
        controller.state = ChatDemoState(
            participants: fixture.accounts + fixture.conversations + fixture.groupMembers,
            messages: fixture.messages,
            currentUserID: selectedAccount.id,
            selectedAccountID: selectedAccount.id,
            contactCards: [:],
            selectedFriendID: nil,
            rightToolItems: []
        )
        controller.unreadPreviewMessages = [
            ChatMessage(
                id: UUID(uuidString: "50450005-0000-0000-0000-000000000000")!,
                conversationID: fixture.conversations[0].id,
                type: .system,
                sender: fixture.conversations[0],
                body: "pagination sentinel",
                detail: "",
                isOutgoing: false,
                presentation: .bare,
                timestamp: "00:00",
                sentAt: .distantFuture,
                recipientAccountID: selectedAccount.id
            )
        ]

        controller.homeRenderedMessageLimit = 40
        controller.invalidateVisibleDataCaches()
        let oldMessages = controller.renderedMessages
        let oldLeftItems = controller.visibleLeftItemsForSelectedAccount()
        let baseLeftItemsCacheKeyBeforePagination = controller.baseVisibleLeftItemsCacheKey
        let baseLeftItemIDsBeforePagination = controller.baseVisibleLeftItemsCache.map(\.id)
        let rebuildCountBeforePagination = controller.pendingReplyIndex.fullRebuildCount
        let presentationCacheKeyBeforePagination = controller.pendingReplyPresentationCacheKey
        let oldSnapshot = try XCTUnwrap(controller.unreadTimelineLayoutSnapshot(width: 320))
        let oldOffsetY = max(0, oldSnapshot.contentHeight * 0.35)
        let anchor = try XCTUnwrap(
            UnreadTimelinePaginationAnchor.capture(
                messages: oldMessages,
                snapshot: oldSnapshot,
                contentOffsetY: oldOffsetY
            )
        )

        controller.homeRenderedMessageLimit += controller.homeMessagePageSize
        controller.invalidateHomePaginationCaches()
        XCTAssertEqual(controller.baseVisibleLeftItemsCacheKey, baseLeftItemsCacheKeyBeforePagination)
        XCTAssertEqual(controller.baseVisibleLeftItemsCache.map(\.id), baseLeftItemIDsBeforePagination)
        let newMessages = controller.renderedMessages
        let newLeftItems = controller.visibleLeftItemsForSelectedAccount()
        let newSnapshot = try XCTUnwrap(controller.unreadTimelineLayoutSnapshot(width: 320))
        let restoredOffsetY = try XCTUnwrap(
            anchor.restoredContentOffsetY(
                messages: newMessages,
                snapshot: newSnapshot,
                minimumOffsetY: 0
            )
        )
        let oldAnchorIndex = try XCTUnwrap(oldMessages.firstIndex { $0.id == anchor.messageID })
        let newAnchorIndex = try XCTUnwrap(newMessages.firstIndex { $0.id == anchor.messageID })
        let oldScreenY = oldSnapshot.messageFrames[oldAnchorIndex].minY - oldOffsetY
        let newScreenY = newSnapshot.messageFrames[newAnchorIndex].minY - restoredOffsetY

        XCTAssertEqual(newMessages.count - oldMessages.count, 40)
        XCTAssertGreaterThanOrEqual(newLeftItems.count, oldLeftItems.count)
        XCTAssertEqual(controller.baseVisibleLeftItemsCacheKey, baseLeftItemsCacheKeyBeforePagination)
        XCTAssertEqual(controller.pendingReplyIndex.fullRebuildCount, rebuildCountBeforePagination)
        XCTAssertEqual(controller.pendingReplyPresentationCacheKey, presentationCacheKeyBeforePagination)
        XCTAssertLessThan(abs(newScreenY - oldScreenY), 1)
        let adjacentGroupMessageIndices = newSnapshot.groups.flatMap { group in
            group.messageIndices.dropFirst().filter { newMessages[$0].isGroupConversation }
        }
        XCTAssertFalse(adjacentGroupMessageIndices.isEmpty)
        for index in adjacentGroupMessageIndices {
            XCTAssertEqual(
                newSnapshot.messageTopSpacings[index],
                UnreadTimelineLayoutMetrics.adjacentGroupMessageSpacing,
                accuracy: 0.001
            )
        }
        for group in newSnapshot.groups {
            let firstIndex = try XCTUnwrap(group.messageIndices.first)
            let lastIndex = try XCTUnwrap(group.messageIndices.last)
            if controller.unreadTimelineFoldPresentation(conversationID: group.participantID) != nil {
                let foldIndices = group.messageIndices.filter {
                    newSnapshot.messageFoldBottomOffsets[$0] != nil
                }
                XCTAssertEqual(foldIndices, [lastIndex])
                XCTAssertGreaterThanOrEqual(
                    newSnapshot.messageBottomPaddings[lastIndex],
                    UnreadTimelineLayoutMetrics.foldFooterHeight
                )
            }
            let firstMessageCenterY = newSnapshot.messageFrames[firstIndex].minY
                + newSnapshot.messageTopSpacings[firstIndex]
                + (newSnapshot.messageFrames[firstIndex].height
                    - newSnapshot.messageTopSpacings[firstIndex]
                    - newSnapshot.messageBottomPaddings[firstIndex]) / 2
            XCTAssertEqual(group.avatarFrame.midY, firstMessageCenterY, accuracy: 0.001)
        }
    }

    func testUnreadFoldExpansionPreservesConversationGroupViewportPosition() throws {
        let fixture = Self.unreadTimeline
        let controller = ChatWindowViewController()
        let selectedAccount = try XCTUnwrap(fixture.accounts.first)
        controller.state = ChatDemoState(
            participants: fixture.accounts + fixture.conversations + fixture.groupMembers,
            messages: fixture.messages,
            currentUserID: selectedAccount.id,
            selectedAccountID: selectedAccount.id,
            contactCards: [:],
            selectedFriendID: nil,
            rightToolItems: []
        )
        controller.homeRenderedMessageLimit = fixture.messages.count
        controller.invalidateVisibleDataCaches()

        let oldSnapshot = try XCTUnwrap(controller.unreadTimelineLayoutSnapshot(width: 320))
        let foldedGroup = try XCTUnwrap(oldSnapshot.groups.first { group in
            controller.unreadTimelineFoldPresentation(conversationID: group.participantID) != nil
        })
        let oldOffsetY = max(0, foldedGroup.frame.minY - 36)
        let anchor = try XCTUnwrap(
            UnreadTimelineGroupViewportAnchor.capture(
                participantID: foldedGroup.participantID,
                snapshot: oldSnapshot,
                contentOffsetY: oldOffsetY
            )
        )
        let fold = try XCTUnwrap(
            controller.unreadTimelineFoldPresentation(conversationID: foldedGroup.participantID)
        )
        switch fold.kind {
        case .directMessages:
            let accountID = try XCTUnwrap(fold.accountID)
            let key = PendingReplyConversationAccountKey(
                conversationID: foldedGroup.participantID,
                accountID: accountID
            )
            controller.unreadDirectVisibleLimitByConversationID[key] = 10
            controller.expandedUnreadDirectKeys.insert(key)
        case .groupItems:
            controller.unreadGroupVisibleLimitByConversationID[foldedGroup.participantID] = 8
            controller.expandedUnreadConversationIDs.insert(foldedGroup.participantID)
        }
        controller.invalidateSelectionVisibleDataCaches()

        let newSnapshot = try XCTUnwrap(controller.unreadTimelineLayoutSnapshot(width: 320))
        let restoredOffsetY = try XCTUnwrap(
            anchor.restoredContentOffsetY(
                snapshot: newSnapshot,
                minimumOffsetY: 0,
                maximumOffsetY: newSnapshot.contentHeight
            )
        )
        let newGroupIndex = try XCTUnwrap(
            newSnapshot.groupIndexByParticipantID[foldedGroup.participantID]
        )
        let newGroup = try XCTUnwrap(newSnapshot.groups[safe: newGroupIndex])

        XCTAssertEqual(
            foldedGroup.frame.minY - oldOffsetY,
            newGroup.frame.minY - restoredOffsetY,
            accuracy: 0.001
        )
        XCTAssertEqual(
            foldedGroup.avatarFrame.minY - oldOffsetY,
            newGroup.avatarFrame.minY - oldOffsetY,
            accuracy: 0.001
        )
        XCTAssertGreaterThanOrEqual(
            newGroup.avatarFrame.minY,
            newGroup.frame.minY
        )
        XCTAssertLessThanOrEqual(
            newGroup.avatarFrame.maxY,
            newGroup.frame.maxY + 0.001
        )
    }

    func testUnreadFoldExpansionKeepsEachConversationInOneContiguousSection() throws {
        let fixture = Self.unreadTimeline
        let selectedAccount = try XCTUnwrap(fixture.accounts.first)
        let controller = ChatWindowViewController()
        controller.state = ChatDemoState(
            participants: fixture.accounts + fixture.conversations + fixture.groupMembers,
            messages: fixture.messages,
            currentUserID: selectedAccount.id,
            selectedAccountID: selectedAccount.id,
            contactCards: [:],
            selectedFriendID: nil,
            rightToolItems: []
        )
        controller.homeRenderedMessageLimit = fixture.messages.count
        for conversation in fixture.conversations {
            for account in fixture.accounts {
                controller.unreadDirectVisibleLimitByConversationID[
                    PendingReplyConversationAccountKey(
                        conversationID: conversation.id,
                        accountID: account.id
                    )
                ] = Int.max
            }
            controller.unreadGroupVisibleLimitByConversationID[conversation.id] = Int.max
        }
        controller.invalidateVisibleDataCaches()

        let conversationIDs = controller.pendingReplyHomePresentation().messages.map(\.conversationID)
        let runs = conversationIDs.reduce(into: [UUID]()) { result, conversationID in
            if result.last != conversationID {
                result.append(conversationID)
            }
        }

        XCTAssertEqual(runs.count, Set(conversationIDs).count)
    }

    func testUnreadDirectLimitAppliesToEachAccountForTheSameFriend() throws {
        let accounts = Array(Self.unreadTimeline.accounts.prefix(2))
        let friend = try XCTUnwrap(Self.unreadTimeline.conversations.first)
        let messages = (0..<8).map { index in
            ChatMessage(
                id: UUID(),
                conversationID: friend.id,
                type: .text,
                sender: friend,
                body: "跨帐号消息 \(index)",
                detail: "",
                isOutgoing: false,
                presentation: .bare,
                timestamp: "",
                sentAt: Date(timeIntervalSince1970: TimeInterval(index)),
                recipientAccountID: accounts[index % accounts.count].id
            )
        }

        let presentation = PendingReplyPresentationBuilder.build(
            pendingMessages: messages,
            allMessages: messages,
            currentAccountIDs: Set(accounts.map(\.id)),
            directVisibleLimits: [:]
        )

        XCTAssertEqual(presentation.messages.count, 6)
        XCTAssertEqual(presentation.hiddenDirectMessageCountByConversationID[friend.id], 2)
        XCTAssertEqual(presentation.directMessageCountByConversationID[friend.id], 8)
        XCTAssertEqual(presentation.items.count, 2)
        XCTAssertTrue(presentation.messages.allSatisfy {
            presentation.item(forMessageID: $0.id) != nil
        })
    }

    func testUnreadFoldExpansionKeepsMiddleGroupAndViewportFixedDuringAnimation() throws {
        let fixture = Self.unreadTimeline
        let selectedAccount = try XCTUnwrap(fixture.accounts.first)
        let controller = ChatWindowViewController()
        controller.loadViewIfNeeded()
        controller.view.frame = CGRect(x: 0, y: 0, width: 390, height: 844)
        controller.state = ChatDemoState(
            participants: fixture.accounts + fixture.conversations + fixture.groupMembers,
            messages: fixture.messages,
            currentUserID: selectedAccount.id,
            selectedAccountID: selectedAccount.id,
            contactCards: [:],
            selectedFriendID: nil,
            rightToolItems: []
        )
        controller.hasCompletedOpenApiChatBootstrap = true
        controller.homeRenderedMessageLimit = fixture.messages.count
        controller.invalidateVisibleDataCaches()
        controller.messageCollectionView.reloadData()
        controller.leftCollectionView.reloadData()
        controller.view.layoutIfNeeded()

        let oldSnapshot = try XCTUnwrap(controller.unreadTimelineLayoutSnapshot())
        let foldedGroups = oldSnapshot.groups.filter {
            controller.unreadTimelineFoldPresentation(conversationID: $0.participantID) != nil
        }
        let targetGroup = try XCTUnwrap(foldedGroups[safe: foldedGroups.count / 2])
        let fold = try XCTUnwrap(
            controller.unreadTimelineFoldPresentation(conversationID: targetGroup.participantID)
        )
        let initialOffsetY = max(0, targetGroup.frame.minY - 120)
        controller.messageCollectionView.setContentOffset(
            CGPoint(x: 0, y: initialOffsetY),
            animated: false
        )
        controller.syncUnreadTimelineScroll(from: controller.messageCollectionView)
        let initialAvatarScreenY = targetGroup.avatarFrame.minY - initialOffsetY
        controller.handleUnreadTimelineFoldTap(fold)

        let completed = expectation(description: "middle fold animation completes")
        var maximumOffsetDeviation: CGFloat = 0
        var poll: (() -> Void)!
        poll = {
            maximumOffsetDeviation = max(
                maximumOffsetDeviation,
                abs(controller.messageCollectionView.contentOffset.y - initialOffsetY)
            )
            if controller.isUnreadFoldExpansionInProgress {
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.005, execute: poll)
            } else {
                completed.fulfill()
            }
        }
        poll()
        wait(for: [completed], timeout: 2)

        let newSnapshot = try XCTUnwrap(controller.unreadTimelineLayoutSnapshot())
        let newGroupIndex = try XCTUnwrap(
            newSnapshot.groupIndexByParticipantID[targetGroup.participantID]
        )
        let newGroup = try XCTUnwrap(newSnapshot.groups[safe: newGroupIndex])
        XCTAssertEqual(newGroup.frame.minY, targetGroup.frame.minY, accuracy: 0.001)
        XCTAssertEqual(
            newGroup.avatarFrame.minY - controller.messageCollectionView.contentOffset.y,
            initialAvatarScreenY,
            accuracy: 0.5
        )
        XCTAssertLessThanOrEqual(maximumOffsetDeviation, 0.5)

        let refreshAnchor = try XCTUnwrap(controller.unreadFoldPostExpansionAnchor)
        let refreshCompleted = expectation(description: "fallback refresh restores expanded group")
        XCTAssertTrue(controller.applyUnreadTimelineExternalRefresh(
            oldMessages: [],
            oldLeftItems: [],
            groupAnchor: refreshAnchor,
            fallbackContentOffsetY: controller.messageCollectionView.contentOffset.y,
            completion: refreshCompleted.fulfill
        ))
        wait(for: [refreshCompleted], timeout: 1)

        let refreshedSnapshot = try XCTUnwrap(controller.unreadTimelineLayoutSnapshot())
        let refreshedGroupIndex = try XCTUnwrap(
            refreshedSnapshot.groupIndexByParticipantID[targetGroup.participantID]
        )
        let refreshedGroup = try XCTUnwrap(refreshedSnapshot.groups[safe: refreshedGroupIndex])
        XCTAssertEqual(
            refreshedGroup.avatarFrame.minY - controller.messageCollectionView.contentOffset.y,
            initialAvatarScreenY,
            accuracy: 0.5
        )
    }

    func testConversationSelectionKeepsStableSidebarAndGroupCaches() throws {
        let fixture = Self.unreadTimeline
        let controller = ChatWindowViewController()
        let selectedAccount = try XCTUnwrap(fixture.accounts.first)
        let selectedConversation = try XCTUnwrap(fixture.conversations.first)
        controller.state = ChatDemoState(
            participants: fixture.accounts + fixture.conversations + fixture.groupMembers,
            messages: fixture.messages,
            currentUserID: selectedAccount.id,
            selectedAccountID: selectedAccount.id,
            contactCards: [:],
            selectedFriendID: nil,
            rightToolItems: []
        )
        controller.invalidateVisibleDataCaches()
        _ = controller.visibleLeftItemsForSelectedAccount()
        _ = controller.isGroupConversation(selectedConversation)

        let baseSidebarKey = controller.baseVisibleLeftItemsCacheKey
        let participantLookupKey = controller.participantLookupCacheKey
        let groupMetadataRevision = controller.groupConversationMetadataCacheRevision

        controller.state.selectedFriendID = selectedConversation.id
        controller.invalidateSelectionVisibleDataCaches()
        _ = controller.visibleLeftItemsForSelectedAccount()
        _ = controller.isGroupConversation(selectedConversation)

        XCTAssertEqual(controller.baseVisibleLeftItemsCacheKey, baseSidebarKey)
        XCTAssertEqual(controller.participantLookupCacheKey, participantLookupKey)
        XCTAssertEqual(controller.groupConversationMetadataCacheRevision, groupMetadataRevision)
    }

    func testUnreadConnectionRangeQueryMatchesReferenceScan() throws {
        let fixture = Self.unreadTimeline
        let controller = ChatWindowViewController()
        let selectedAccount = try XCTUnwrap(fixture.accounts.first)
        controller.state = ChatDemoState(
            participants: fixture.accounts + fixture.conversations + fixture.groupMembers,
            messages: fixture.messages,
            currentUserID: selectedAccount.id,
            selectedAccountID: selectedAccount.id,
            contactCards: [:],
            selectedFriendID: nil,
            rightToolItems: []
        )
        controller.homeRenderedMessageLimit = 400
        controller.invalidateVisibleDataCaches()
        let snapshot = try XCTUnwrap(controller.unreadTimelineLayoutSnapshot(width: 320))
        let visibleBounds = CGRect(
            x: 0,
            y: snapshot.contentHeight * 0.43,
            width: 320,
            height: 760
        )
        let optimized = controller.unreadTimelineConnectionIndexPaths(
            snapshot: snapshot,
            visibleBounds: visibleBounds
        )
        var reference: [IndexPath] = []
        for group in snapshot.groups
        where group.frame.maxY >= visibleBounds.minY && group.frame.minY <= visibleBounds.maxY {
            let visiblePositions = group.messageIndices.indices.filter { position in
                let frame = snapshot.messageFrames[group.messageIndices[position]]
                return frame.maxY >= visibleBounds.minY && frame.minY <= visibleBounds.maxY
            }
            guard let first = visiblePositions.first, let last = visiblePositions.last else {
                if let nearest = group.messageIndices.min(by: {
                    abs(snapshot.messageFrames[$0].midY - visibleBounds.midY)
                        < abs(snapshot.messageFrames[$1].midY - visibleBounds.midY)
                }) {
                    reference.append(IndexPath(item: nearest, section: 0))
                }
                continue
            }
            let lower = max(group.messageIndices.startIndex, first - 1)
            let upper = min(group.messageIndices.index(before: group.messageIndices.endIndex), last + 1)
            reference.append(contentsOf: group.messageIndices[lower...upper].map {
                IndexPath(item: $0, section: 0)
            })
        }
        XCTAssertEqual(optimized, reference)
    }

    func testAsyncHeightPrewarmPopulatesCurrentLayoutCache() throws {
        let fixture = Self.unreadTimeline
        let selectedAccount = try XCTUnwrap(fixture.accounts.first)
        let controller = ChatWindowViewController()
        controller.state = ChatDemoState(
            participants: fixture.accounts + fixture.conversations + fixture.groupMembers,
            messages: fixture.messages,
            currentUserID: selectedAccount.id,
            selectedAccountID: selectedAccount.id,
            contactCards: [:],
            selectedFriendID: nil,
            rightToolItems: []
        )
        controller.homeRenderedMessageLimit = 40
        controller.loadViewIfNeeded()
        controller.view.frame = CGRect(x: 0, y: 0, width: 1_024, height: 768)
        controller.view.layoutIfNeeded()
        controller.invalidateVisibleDataCaches()
        controller.clearMessageHeightCache()
        let expectedMessageCount = controller.renderedMessages.count
        XCTAssertGreaterThan(expectedMessageCount, 0)
        XCTAssertGreaterThan(controller.messageCollectionView.bounds.width, 0)

        let completion = expectation(description: "background height prewarm")
        controller.prepareCurrentMessageHeightsForReload {
            XCTAssertGreaterThanOrEqual(controller.messageHeightCache.count, expectedMessageCount)
            completion.fulfill()
        }
        wait(for: [completion], timeout: 3)
    }

    func testPerformanceLargeCorpusConstruction() {
        let options = XCTMeasureOptions()
        options.iterationCount = 5
        var checksum = 0

        measure(metrics: [XCTClockMetric()], options: options) {
            autoreleasepool {
                let fixture = PerformanceDataFixtures.makeLargeCorpus()
                checksum &+= fixture.messages.count
                checksum &+= fixture.conversations.count
                checksum &+= fixture.accounts.count
            }
        }

        XCTAssertGreaterThan(checksum, 0)
    }

    func testPerformanceLargeCorpusLookupIndexConstruction() {
        let fixture = Self.largeCorpus
        let options = XCTMeasureOptions()
        options.iterationCount = 10
        var checksum = 0

        measure(metrics: [XCTClockMetric()], options: options) {
            autoreleasepool {
                let index = PerformanceDataFixtures.makeLookupIndex(messages: fixture.messages)
                checksum &+= index.messageOffsetByID.count
                checksum &+= index.messageOffsetsByConversation.count
            }
        }

        XCTAssertGreaterThan(checksum, 0)
    }

    func testPerformanceUnreadAggregation() {
        let fixture = Self.largeCorpus
        let accountIDs = Set(fixture.accounts.map(\.id))
        let options = XCTMeasureOptions()
        options.iterationCount = 10
        var resultCount = 0

        measure(metrics: [XCTClockMetric()], options: options) {
            autoreleasepool {
                resultCount &+= PerformanceDataFixtures.aggregatePendingReplies(
                    messages: fixture.messages,
                    accountIDs: accountIDs
                ).count
            }
        }

        XCTAssertGreaterThan(resultCount, 0)
    }

    func testPerformancePendingReplyIndexedWindowRead() throws {
        let fixture = Self.largeCorpus
        let accountIDs = Set(fixture.accounts.map(\.id))
        let fallbackAccountID = try XCTUnwrap(fixture.accounts.first?.id)
        var index = PendingReplyIndex()
        index.rebuild(
            messages: fixture.messages,
            keyForMessage: {
                PendingReplyConversationAccountKey(
                    conversationID: $0.conversationID,
                    accountID: $0.recipientAccountID ?? fallbackAccountID
                )
            },
            isRelevant: {
                !$0.isAI && !$0.sender.isAIAccount && $0.type != .system && $0.type != .groupNotice
            },
            isIncoming: {
                !$0.isOutgoing && !$0.sender.isCurrentUser && !accountIDs.contains($0.sender.id)
            }
        )

        let options = XCTMeasureOptions()
        options.iterationCount = 10
        var checksum = 0
        measure(metrics: [XCTClockMetric()], options: options) {
            checksum &+= index.latestMessages(limit: 40).count
        }

        XCTAssertGreaterThanOrEqual(checksum, 400)
    }

    func testPerformanceUnreadTimelineConstruction() {
        let options = XCTMeasureOptions()
        options.iterationCount = 10
        var checksum = 0

        measure(metrics: [XCTClockMetric()], options: options) {
            autoreleasepool {
                let fixture = PerformanceDataFixtures.makeUnreadTimeline()
                checksum &+= fixture.messages.count
                checksum &+= fixture.conversations.count
                checksum &+= fixture.accounts.count
            }
        }

        XCTAssertGreaterThan(checksum, 0)
    }

    func testPerformanceMixedMediaDetailConstruction() {
        let options = XCTMeasureOptions()
        options.iterationCount = 10
        var checksum = 0

        measure(metrics: [XCTClockMetric()], options: options) {
            autoreleasepool {
                let fixture = PerformanceDataFixtures.makeDetailConversation()
                checksum &+= fixture.messages.count
                checksum &+= Set(fixture.messages.map(\.type)).count
            }
        }

        XCTAssertGreaterThan(checksum, 0)
    }

    private func replacingBody(of message: ChatMessage, with body: String) -> ChatMessage {
        ChatMessage(
            id: message.id,
            conversationID: message.conversationID,
            type: message.type,
            sender: message.sender,
            body: body,
            detail: message.detail,
            isOutgoing: message.isOutgoing,
            presentation: message.presentation,
            timestamp: message.timestamp,
            sentAt: message.sentAt,
            attachmentURL: message.attachmentURL,
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

    private func assertPendingReplyIndex(
        _ index: PendingReplyIndex,
        matches messages: [ChatMessage],
        accountIDs: Set<UUID>,
        file: StaticString = #filePath,
        line: UInt = #line
    ) {
        let expected = PerformanceDataFixtures.aggregatePendingReplies(
            messages: messages,
            accountIDs: accountIDs
        )
        let actual = index.latestMessages(limit: index.totalPendingCount)
        XCTAssertEqual(actual.map(\.id), expected.map(\.id), file: file, line: line)
        XCTAssertEqual(index.totalPendingCount, expected.count, file: file, line: line)
    }
}
