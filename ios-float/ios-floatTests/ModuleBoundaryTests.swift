import XCTest
@testable import ios_float

final class ModuleBoundaryTests: XCTestCase {
    func testTaskResultKeepsTaskIDWhenInitialSuccessIsFalse() {
        let response: [String: Any] = [
            "taskId": 928001,
            "success": false,
            "message": "任务已创建，等待微信端执行"
        ]

        XCTAssertEqual(OpenApiHTTPClient.taskID(from: response), "928001")
    }

    func testMomentPayloadUsesDocumentedIntegerEnums() {
        let publicText = OpenApiMomentAPI.momentPayloadEnumValues(hasImages: false, visibleType: "public")
        XCTAssertEqual(publicText.attachmentType, 0)
        XCTAssertEqual(publicText.visibleType, 0)

        let privateImages = OpenApiMomentAPI.momentPayloadEnumValues(hasImages: true, visibleType: "private")
        XCTAssertEqual(privateImages.attachmentType, 2)
        XCTAssertEqual(privateImages.visibleType, 1)
        XCTAssertEqual(OpenApiMomentAPI.momentPayloadEnumValues(hasImages: false, visibleType: "partVisible").visibleType, 2)
        XCTAssertEqual(OpenApiMomentAPI.momentPayloadEnumValues(hasImages: false, visibleType: "notVisible").visibleType, 3)
    }

    func testManifestPointsToPhysicalModuleDirectories() {
        let rows = AppKits.manifest.rows
        XCTAssertEqual(rows.count, 6)
        XCTAssertTrue(rows.contains { $0.subtitle.contains("Modules/Interaction") })
        XCTAssertTrue(rows.contains { $0.subtitle.contains("Modules/FloatingIM") })
        XCTAssertTrue(rows.contains { $0.subtitle.contains("Modules/MessageRender") })
        XCTAssertTrue(rows.contains { $0.subtitle.contains("Modules/AIInput") })
        XCTAssertTrue(rows.contains { $0.subtitle.contains("Modules/Data") })
        XCTAssertTrue(rows.contains { $0.subtitle.contains("Modules/Business") })
    }

    func testRegistryProvidesAllReusableKits() {
        let registry = AppKits.registry
        XCTAssertEqual(registry.manifest.modules.count, 6)
        XCTAssertEqual(registry.businessKit.pageDescriptors().count, BusinessPageKind.allCases.count)
        XCTAssertTrue(registry.floatingIMKit.visibleConversationIDs().isEmpty)
    }

    func testAIInputDemoProducesUsefulDraft() {
        let expectation = expectation(description: "AI input demo")
        AIInputKitDemo.run(action: .continueWriting, text: "我来处理") { result in
            XCTAssertTrue(result.contains("继续跟进"))
            expectation.fulfill()
        }
        wait(for: [expectation], timeout: 1)
    }

    func testConversationIdentityDoesNotMergeSameNamedParticipants() throws {
        var state = ChatDataFactory.makeDemoState()
        let original = try XCTUnwrap(state.friends.first { !$0.isAIAccount })
        let duplicate = ChatParticipant(
            id: UUID(),
            displayName: original.displayName,
            tintColor: .systemOrange,
            initials: "同"
        )
        let duplicateMessage = ChatMessage(
            id: UUID(),
            conversationID: duplicate.id,
            type: .text,
            sender: duplicate,
            body: "同名联系人也必须是独立会话",
            detail: "",
            isOutgoing: false,
            presentation: .bare,
            timestamp: "10:00"
        )
        state.participants.append(duplicate)
        state.messages.append(duplicateMessage)

        XCTAssertEqual(state.leftItems.filter { $0.title == original.displayName }.count, 2)
        state.selectedFriendID = duplicate.id
        XCTAssertEqual(state.visibleMessages.map(\.conversationID), [duplicate.id])
    }

    func testIncomingOnlyConversationWithoutOwnershipFailsClosed() {
        let firstAccount = ChatParticipant(
            id: UUID(), displayName: "帐号一", tintColor: .systemBlue, initials: "一", isCurrentUser: true
        )
        let secondAccount = ChatParticipant(
            id: UUID(), displayName: "帐号二", tintColor: .systemGreen, initials: "二", isCurrentUser: true
        )
        let contact = ChatParticipant(
            id: UUID(), displayName: "联系人", tintColor: .systemOrange, initials: "联"
        )
        let incoming = ChatMessage(
            id: UUID(),
            conversationID: contact.id,
            type: .text,
            sender: contact,
            body: "只有收到的消息",
            detail: "",
            isOutgoing: false,
            presentation: .bare,
            timestamp: "10:00"
        )

        let firstResolution = FloatingIMConversationResolver.relatedAccountIDs(
            for: contact.id,
            messages: [incoming],
            accounts: [firstAccount, secondAccount]
        )
        let secondResolution = FloatingIMConversationResolver.relatedAccountIDs(
            for: contact.id,
            messages: [incoming],
            accounts: [secondAccount, firstAccount]
        )
        XCTAssertEqual(firstResolution, secondResolution)
        XCTAssertTrue(firstResolution.isEmpty)

        let explicitlyOwned = ChatMessage(
            id: incoming.id,
            conversationID: incoming.conversationID,
            type: incoming.type,
            sender: incoming.sender,
            body: incoming.body,
            detail: incoming.detail,
            isOutgoing: incoming.isOutgoing,
            presentation: incoming.presentation,
            timestamp: incoming.timestamp,
            recipientAccountID: secondAccount.id
        )
        XCTAssertEqual(
            FloatingIMConversationResolver.relatedAccountIDs(
                for: contact.id,
                messages: [explicitlyOwned],
                accounts: [firstAccount, secondAccount]
            ),
            [secondAccount.id]
        )
    }

    func testMessageRendererProducesFittedHeightForEverySeedMessage() {
        let state = ChatDataFactory.makeDemoState()
        let renderer = AppKits.registry.messageRenderKit

        XCTAssertFalse(state.messages.isEmpty)
        for message in state.messages {
            let height = renderer.estimatedMessageHeight(
                message,
                width: 360,
                showsGroupAvatar: true,
                showsGroupName: true,
                usesUnansweredPresentation: false,
                deliveryStatusText: nil
            )
            XCTAssertGreaterThan(height, 27, "\(message.type.title) 未获得有效气泡高度")
        }
    }

    func testEveryMessageTypeProducesMatchingBubbleSchema() {
        for type in ChatMessageType.allCases {
            let sender = ChatParticipant(
                id: UUID(),
                displayName: "Schema Sender",
                tintColor: .systemBlue,
                initials: "S"
            )
            let message = ChatMessage(
                id: UUID(),
                conversationID: UUID(),
                type: type,
                sender: sender,
                body: type.template.primaryText,
                detail: type.template.secondaryText,
                isOutgoing: false,
                presentation: .bare,
                timestamp: "10:00"
            )
            let schema = MessageBubbleSchema.from(message: message)
            XCTAssertEqual(schema.type, type)
        }
    }

    func testDemoMessagesPreserveIdentityAndTypeAcrossCodableRoundTrip() throws {
        let messages = ChatDataFactory.makeDemoState().messages
        let data = try JSONEncoder().encode(messages)
        let decoded = try JSONDecoder().decode([ChatMessage].self, from: data)

        XCTAssertEqual(decoded.map(\.id), messages.map(\.id))
        XCTAssertEqual(decoded.map(\.conversationID), messages.map(\.conversationID))
        XCTAssertEqual(decoded.map(\.type), messages.map(\.type))
        XCTAssertEqual(decoded.map(\.body), messages.map(\.body))
    }

    func testLegacyRenderContractsMatchCurrentDefaults() {
        let state = MessageRenderState.legacyDefault
        XCTAssertFalse(state.isVoicePlaying)
        XCTAssertFalse(state.isAIStreaming)
        XCTAssertFalse(state.screenshotMediaBlurEnabled)
        XCTAssertFalse(state.mediaPrivacyBlurEnabled)
        XCTAssertFalse(state.uses3DAppearance)
        XCTAssertTrue(state.groupDisplay.showsAvatar)
        XCTAssertTrue(state.groupDisplay.showsName)

        let layout = MessageLayoutSpec.legacy(availableWidth: 360)
        XCTAssertEqual(layout.maximumBubbleWidthRatio, 0.82)
        XCTAssertEqual(layout.minimumBubbleWidth, 126)
        XCTAssertEqual(layout.avatarSize, CGSize(width: 36, height: 36))
        XCTAssertEqual(layout.rowSpacing, 8)

        let style = MessageStyleTokens.legacy
        XCTAssertEqual(style.radius.bubble, 15)
        XCTAssertEqual(style.radius.card, 12)
        XCTAssertEqual(style.metrics.maximumBubbleWidthRatio, 0.82)
        XCTAssertEqual(style.typography.meta.pointSize, 10)
    }

    func testEveryMessageCanProjectToRenderModelWithoutChangingIdentity() {
        let messages = ChatDataFactory.makeDemoState().messages
        for message in messages {
            let model = MessageRenderModel(source: message)
            XCTAssertEqual(model.id, message.id)
            XCTAssertEqual(model.type, message.type)
            XCTAssertEqual(model.conversationID, message.conversationID)
        }
    }

    func testRendererRegistryCoversEveryMessageTypeWithPerTypeFallback() {
        let registry = MessageRendererRegistry.default
        XCTAssertTrue(registry.isComplete)
        XCTAssertEqual(registry.registeredTypes, Set(ChatMessageType.allCases))

        for type in ChatMessageType.allCases {
            if type == .system {
                XCTAssertEqual(registry.version(for: type), .componentized)
                XCTAssertTrue(registry.renderer(for: type) is SystemMessageRenderer)
            } else if TextMessageRenderer.supportedTypes.contains(type) {
                XCTAssertEqual(registry.version(for: type), .componentized)
                XCTAssertTrue(registry.renderer(for: type) is TextMessageRenderer)
            } else if MediaMessageRenderer.supportedTypes.contains(type) {
                XCTAssertEqual(registry.version(for: type), .componentized)
                XCTAssertTrue(registry.renderer(for: type) is MediaMessageRenderer)
            } else if VoiceCallMessageRenderer.supportedTypes.contains(type) {
                XCTAssertEqual(registry.version(for: type), .componentized)
                XCTAssertTrue(registry.renderer(for: type) is VoiceCallMessageRenderer)
            } else if CardMessageRenderer.supportedTypes.contains(type) {
                XCTAssertEqual(registry.version(for: type), .componentized)
                XCTAssertTrue(registry.renderer(for: type) is CardMessageRenderer)
            } else if PaymentMessageRenderer.supportedTypes.contains(type) {
                XCTAssertEqual(registry.version(for: type), .componentized)
                XCTAssertTrue(registry.renderer(for: type) is PaymentMessageRenderer)
            } else {
                XCTAssertEqual(registry.version(for: type), .legacy)
                XCTAssertTrue(registry.renderer(for: type) is LegacyMessageRenderer)
            }
        }
    }

    func testRendererGroupsCoverEveryMessageType() {
        let registry = MessageRendererRegistry.default
        let groupedTypes = Dictionary(grouping: ChatMessageType.allCases, by: registry.group(for:))
        XCTAssertEqual(Set(groupedTypes.keys), Set(MessageRendererGroup.allCases))
        XCTAssertEqual(groupedTypes.values.reduce(0) { $0 + $1.count }, ChatMessageType.allCases.count)
    }

    func testLegacyRendererMeasurementMatchesCurrentBubbleMeasurement() throws {
        let message = try XCTUnwrap(ChatDataFactory.makeDemoState().messages.first)
        let model = MessageRenderModel(source: message)
        let state = MessageRenderState.legacyDefault
        let layout = MessageLayoutSpec.legacy(availableWidth: 360)
        let renderer = LegacyMessageRenderer()

        let legacyHeight = ChatMessageBubbleView.estimatedHeight(
            for: message,
            width: 360,
            showsGroupAvatar: true,
            showsGroupName: true
        )
        XCTAssertEqual(renderer.measure(model: model, state: state, layout: layout), legacyHeight)
    }

    func testSystemRendererMatchesLegacyMeasurementAndKeepsStableView() {
        let sender = ChatParticipant(
            id: UUID(),
            displayName: "System",
            tintColor: .systemGray,
            initials: "S"
        )
        let message = ChatMessage(
            id: UUID(),
            conversationID: UUID(),
            type: .system,
            sender: sender,
            body: "系统提示",
            detail: "",
            isOutgoing: false,
            presentation: .bare,
            timestamp: "10:00"
        )
        let model = MessageRenderModel(source: message)
        let state = MessageRenderState.legacyDefault
        let layout = MessageLayoutSpec.legacy(availableWidth: 360)
        let renderer = SystemMessageRenderer()
        let initialView = renderer.view

        renderer.render(model: model, state: state, style: .legacy)
        let expected = ChatMessageBubbleView.estimatedHeight(
            for: message,
            width: 360,
            showsGroupAvatar: true,
            showsGroupName: true
        )

        XCTAssertTrue(renderer.view === initialView)
        XCTAssertEqual(renderer.measure(model: model, state: state, layout: layout), expected)
        renderer.resetForReuse()
        XCTAssertTrue(renderer.view === initialView)
    }

    func testTextRendererMatchesLegacyMeasurementForEverySupportedType() {
        let sender = ChatParticipant(
            id: UUID(),
            displayName: "Text Sender",
            tintColor: .systemBlue,
            initials: "T"
        )
        let state = MessageRenderState.legacyDefault
        let layout = MessageLayoutSpec.legacy(availableWidth: 360)

        for type in TextMessageRenderer.supportedTypes {
            let message = ChatMessage(
                id: UUID(),
                conversationID: UUID(),
                type: type,
                sender: sender,
                body: type.template.primaryText,
                detail: type.template.secondaryText,
                isOutgoing: false,
                presentation: .bare,
                timestamp: "10:00"
            )
            let renderer = TextMessageRenderer()
            let initialView = renderer.view
            let model = MessageRenderModel(source: message)
            renderer.render(model: model, state: state, style: .legacy)

            let expected = ChatMessageBubbleView.estimatedHeight(
                for: message,
                width: 360,
                showsGroupAvatar: true,
                showsGroupName: true
            )
            XCTAssertEqual(renderer.measure(model: model, state: state, layout: layout), expected, type.title)
            XCTAssertTrue(renderer.view === initialView)
            renderer.resetForReuse()
            XCTAssertTrue(renderer.view === initialView)
        }
    }

    func testMessageCellCanReuseAcrossComponentizedRendererGroups() throws {
        let state = ChatDataFactory.makeDemoState()
        let text = try XCTUnwrap(state.messages.first { TextMessageRenderer.supportedTypes.contains($0.type) })
        let media = ChatMessage(
            id: UUID(),
            conversationID: text.conversationID,
            type: .image,
            sender: text.sender,
            body: "图片",
            detail: "",
            isOutgoing: false,
            presentation: .bare,
            timestamp: "10:00"
        )
        let card = ChatMessage(
            id: UUID(),
            conversationID: text.conversationID,
            type: .webLink,
            sender: text.sender,
            body: "网页链接",
            detail: "链接摘要",
            isOutgoing: false,
            presentation: .bare,
            timestamp: "10:00"
        )
        let sender = ChatParticipant(
            id: UUID(),
            displayName: "System",
            tintColor: .systemGray,
            initials: "S"
        )
        let system = ChatMessage(
            id: UUID(),
            conversationID: text.conversationID,
            type: .system,
            sender: sender,
            body: "系统提示",
            detail: "",
            isOutgoing: false,
            presentation: .bare,
            timestamp: "10:00"
        )
        let cell = ChatMessageCell(frame: CGRect(x: 0, y: 0, width: 360, height: 240))

        for message in [text, media, card, system, text] {
            cell.configure(with: message)
            cell.setNeedsLayout()
            cell.layoutIfNeeded()
            let size = cell.contentView.systemLayoutSizeFitting(
                CGSize(width: 360, height: UIView.layoutFittingCompressedSize.height),
                withHorizontalFittingPriority: .required,
                verticalFittingPriority: .fittingSizeLevel
            )
            XCTAssertGreaterThan(size.height, 0, message.type.title)
            cell.prepareForReuse()
        }
    }

    func testMediaRendererMatchesLegacyMeasurementAndResetsForEverySupportedType() {
        let sender = ChatParticipant(
            id: UUID(),
            displayName: "Media Sender",
            tintColor: .systemTeal,
            initials: "M"
        )
        let state = MessageRenderState.legacyDefault
        let layout = MessageLayoutSpec.legacy(availableWidth: 360)

        for type in MediaMessageRenderer.supportedTypes {
            let message = ChatMessage(
                id: UUID(),
                conversationID: UUID(),
                type: type,
                sender: sender,
                body: type.template.primaryText,
                detail: type.template.secondaryText,
                isOutgoing: false,
                presentation: .bare,
                timestamp: "10:00"
            )
            let renderer = MediaMessageRenderer()
            let initialView = renderer.view
            let model = MessageRenderModel(source: message)
            renderer.render(model: model, state: state, style: .legacy)

            let expected = ChatMessageBubbleView.estimatedHeight(
                for: message,
                width: 360,
                showsGroupAvatar: true,
                showsGroupName: true
            )
            XCTAssertEqual(renderer.measure(model: model, state: state, layout: layout), expected, type.title)
            renderer.resetForReuse()
            XCTAssertTrue(renderer.view === initialView)
        }
    }

    func testVoiceCallRendererMatchesLegacyMeasurementAndPlaybackState() {
        let sender = ChatParticipant(
            id: UUID(),
            displayName: "Voice Sender",
            tintColor: .systemGreen,
            initials: "V"
        )
        let layout = MessageLayoutSpec.legacy(availableWidth: 360)

        for type in VoiceCallMessageRenderer.supportedTypes {
            let message = ChatMessage(
                id: UUID(),
                conversationID: UUID(),
                type: type,
                sender: sender,
                body: type.template.primaryText,
                detail: type.template.secondaryText,
                isOutgoing: false,
                presentation: .bare,
                timestamp: "10:00"
            )
            var state = MessageRenderState.legacyDefault
            state.isVoicePlaying = type == .voice
            let renderer = VoiceCallMessageRenderer()
            let initialView = renderer.view
            let model = MessageRenderModel(source: message)
            renderer.render(model: model, state: state, style: .legacy)

            let expected = ChatMessageBubbleView.estimatedHeight(
                for: message,
                width: 360,
                showsGroupAvatar: true,
                showsGroupName: true
            )
            XCTAssertEqual(renderer.measure(model: model, state: state, layout: layout), expected, type.title)
            renderer.resetForReuse()
            XCTAssertTrue(renderer.view === initialView)
        }
    }

    func testCardRendererMatchesLegacyMeasurementForEverySupportedType() {
        let sender = ChatParticipant(
            id: UUID(),
            displayName: "Card Sender",
            tintColor: .systemIndigo,
            initials: "C"
        )
        let state = MessageRenderState.legacyDefault
        let layout = MessageLayoutSpec.legacy(availableWidth: 360)

        for type in CardMessageRenderer.supportedTypes {
            let message = ChatMessage(
                id: UUID(),
                conversationID: UUID(),
                type: type,
                sender: sender,
                body: type.template.primaryText,
                detail: type.template.secondaryText,
                isOutgoing: false,
                presentation: .bare,
                timestamp: "10:00"
            )
            let renderer = CardMessageRenderer()
            let initialView = renderer.view
            let model = MessageRenderModel(source: message)
            renderer.render(model: model, state: state, style: .legacy)

            let expected = ChatMessageBubbleView.estimatedHeight(
                for: message,
                width: 360,
                showsGroupAvatar: true,
                showsGroupName: true
            )
            XCTAssertEqual(renderer.measure(model: model, state: state, layout: layout), expected, type.title)
            renderer.resetForReuse()
            XCTAssertTrue(renderer.view === initialView)
        }
    }

    func testPaymentRendererMatchesLegacyMeasurementAndDeliveryState() {
        let sender = ChatParticipant(
            id: UUID(),
            displayName: "Payment Sender",
            tintColor: .systemRed,
            initials: "P"
        )
        let layout = MessageLayoutSpec.legacy(availableWidth: 360)

        for type in PaymentMessageRenderer.supportedTypes {
            let message = ChatMessage(
                id: UUID(),
                conversationID: UUID(),
                type: type,
                sender: sender,
                body: type.template.primaryText,
                detail: type.template.secondaryText,
                isOutgoing: true,
                presentation: .bare,
                timestamp: "10:00"
            )
            var state = MessageRenderState.legacyDefault
            state.deliveryStatusText = "已领取"
            let renderer = PaymentMessageRenderer()
            let initialView = renderer.view
            let model = MessageRenderModel(source: message)
            renderer.render(model: model, state: state, style: .legacy)

            let expected = ChatMessageBubbleView.estimatedHeight(
                for: message,
                width: 360,
                showsGroupAvatar: true,
                showsGroupName: true
            )
            XCTAssertEqual(renderer.measure(model: model, state: state, layout: layout), expected, type.title)
            renderer.resetForReuse()
            XCTAssertTrue(renderer.view === initialView)
        }
    }
}
