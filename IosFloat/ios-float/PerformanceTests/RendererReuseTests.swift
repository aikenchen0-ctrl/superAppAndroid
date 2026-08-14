import XCTest
import UIKit
@testable import ios_float

final class RendererReuseTests: XCTestCase {
    func testRegistryUsesConcreteRendererTypesAsReuseIdentifiers() {
        let registry = MessageRendererRegistry.default

        assertReuseIdentifier(
            ObjectIdentifier(SystemMessageRenderer.self),
            types: [.system],
            registry: registry
        )
        assertReuseIdentifier(
            ObjectIdentifier(TextMessageRenderer.self),
            types: TextMessageRenderer.supportedTypes,
            registry: registry
        )
        assertReuseIdentifier(
            ObjectIdentifier(MediaMessageRenderer.self),
            types: MediaMessageRenderer.supportedTypes,
            registry: registry
        )
        assertReuseIdentifier(
            ObjectIdentifier(VoiceCallMessageRenderer.self),
            types: VoiceCallMessageRenderer.supportedTypes,
            registry: registry
        )
        assertReuseIdentifier(
            ObjectIdentifier(CardMessageRenderer.self),
            types: CardMessageRenderer.supportedTypes,
            registry: registry
        )
        assertReuseIdentifier(
            ObjectIdentifier(PaymentMessageRenderer.self),
            types: PaymentMessageRenderer.supportedTypes,
            registry: registry
        )

        let legacyRegistry = MessageRendererRegistry(types: [.text])
        XCTAssertEqual(
            legacyRegistry.reuseIdentifier(for: .text),
            ObjectIdentifier(LegacyMessageRenderer.self)
        )
    }

    func testCollectionReuseIdentifiersFollowRendererGroups() {
        XCTAssertEqual(
            ChatMessageCell.reuseIdentifier(for: .text),
            ChatMessageCell.reuseIdentifier(for: .emoji)
        )
        XCTAssertEqual(
            ChatMessageCell.reuseIdentifier(for: .image),
            ChatMessageCell.reuseIdentifier(for: .video)
        )
        XCTAssertNotEqual(
            ChatMessageCell.reuseIdentifier(for: .text),
            ChatMessageCell.reuseIdentifier(for: .image)
        )
        XCTAssertEqual(
            Set(ChatMessageCell.reuseIdentifiers).count,
            MessageRendererGroup.allCases.count
        )
    }

    func testCellKeepsRendererBubbleAndMountingConstraintsForSameRendererType() throws {
        let cell = makeCell()
        let firstToken = "renderer-reuse-first"
        cell.configure(with: makeMessage(type: .text, body: firstToken))

        let firstBubble = try mountedBubble(in: cell)
        let firstConstraintIDs = mountingConstraintIDs(in: cell, bubble: firstBubble)
        XCTAssertEqual(firstConstraintIDs.count, 4)

        let secondToken = "renderer-reuse-second"
        cell.configure(with: makeMessage(type: .emoji, body: secondToken))

        let secondBubble = try mountedBubble(in: cell)
        XCTAssertTrue(secondBubble === firstBubble)
        XCTAssertEqual(mountingConstraintIDs(in: cell, bubble: secondBubble), firstConstraintIDs)
        XCTAssertFalse(labelTexts(in: secondBubble).contains { $0.contains(firstToken) })
        XCTAssertTrue(labelTexts(in: secondBubble).contains { $0.contains(secondToken) })

        cell.prepareForReuse()
        let preparedBubble = try mountedBubble(in: cell)
        XCTAssertTrue(preparedBubble === firstBubble)
        XCTAssertEqual(mountingConstraintIDs(in: cell, bubble: preparedBubble), firstConstraintIDs)
        XCTAssertFalse(labelTexts(in: preparedBubble).contains { $0.contains(secondToken) })

        let thirdToken = "renderer-reuse-third"
        cell.configure(with: makeMessage(type: .groupNotice, body: thirdToken))
        let thirdBubble = try mountedBubble(in: cell)
        XCTAssertTrue(thirdBubble === firstBubble)
        XCTAssertEqual(mountingConstraintIDs(in: cell, bubble: thirdBubble), firstConstraintIDs)
        XCTAssertTrue(labelTexts(in: thirdBubble).contains { $0.contains(thirdToken) })
    }

    func testCellReplacesRendererOnlyWhenConcreteRendererTypeChanges() throws {
        let cell = makeCell()
        cell.configure(with: makeMessage(type: .text, body: "text-renderer"))
        let textBubble = try mountedBubble(in: cell)
        let textConstraintIDs = mountingConstraintIDs(in: cell, bubble: textBubble)

        cell.configure(with: makeMessage(type: .image, body: "media-renderer"))
        let mediaBubble = try mountedBubble(in: cell)
        let mediaConstraintIDs = mountingConstraintIDs(in: cell, bubble: mediaBubble)
        XCTAssertFalse(mediaBubble === textBubble)
        XCTAssertNil(textBubble.superview)
        XCTAssertNotEqual(mediaConstraintIDs, textConstraintIDs)
        XCTAssertEqual(mediaConstraintIDs.count, 4)

        cell.configure(with: makeMessage(type: .video, body: "same-media-renderer"))
        let reusedMediaBubble = try mountedBubble(in: cell)
        XCTAssertTrue(reusedMediaBubble === mediaBubble)
        XCTAssertEqual(mountingConstraintIDs(in: cell, bubble: reusedMediaBubble), mediaConstraintIDs)

        cell.prepareForReuse()
        XCTAssertTrue(try mountedBubble(in: cell) === mediaBubble)
        XCTAssertEqual(mountingConstraintIDs(in: cell, bubble: mediaBubble), mediaConstraintIDs)

        cell.configure(with: makeMessage(type: .system, body: "system-renderer"))
        let systemBubble = try mountedBubble(in: cell)
        XCTAssertFalse(systemBubble === mediaBubble)
        XCTAssertNil(mediaBubble.superview)
        XCTAssertEqual(mountingConstraintIDs(in: cell, bubble: systemBubble).count, 4)
    }

    func testRendererResetClearsEventClosureAndKeepsItsBubble() {
        let renderer = TextMessageRenderer()
        let bubble = renderer.view
        var eventCount = 0
        renderer.onEvent = { _ in eventCount += 1 }

        renderer.resetForReuse()
        renderer.onEvent?(.toggleSelection(messageID: UUID()))

        XCTAssertEqual(eventCount, 0)
        XCTAssertTrue(renderer.view === bubble)
    }

    private func assertReuseIdentifier(
        _ expected: ObjectIdentifier,
        types: Set<ChatMessageType>,
        registry: MessageRendererRegistry,
        file: StaticString = #filePath,
        line: UInt = #line
    ) {
        for type in types {
            XCTAssertEqual(registry.reuseIdentifier(for: type), expected, type.title, file: file, line: line)
        }
    }

    private func makeCell() -> ChatMessageCell {
        ChatMessageCell(frame: CGRect(x: 0, y: 0, width: 360, height: 240))
    }

    private func makeMessage(type: ChatMessageType, body: String) -> ChatMessage {
        ChatMessage(
            id: UUID(),
            conversationID: UUID(),
            type: type,
            sender: ChatParticipant(
                id: UUID(),
                displayName: "Renderer Test",
                tintColor: .systemTeal,
                initials: "RT"
            ),
            body: body,
            detail: type.template.secondaryText,
            isOutgoing: false,
            presentation: .bare,
            timestamp: "10:00"
        )
    }

    private func mountedBubble(in cell: ChatMessageCell) throws -> ChatMessageBubbleView {
        let bubbles = cell.contentView.subviews.compactMap { $0 as? ChatMessageBubbleView }
        XCTAssertEqual(bubbles.count, 1)
        return try XCTUnwrap(bubbles.first)
    }

    private func mountingConstraintIDs(
        in cell: ChatMessageCell,
        bubble: ChatMessageBubbleView
    ) -> Set<ObjectIdentifier> {
        Set(cell.contentView.constraints.compactMap { constraint in
            let referencesBubble = (constraint.firstItem as AnyObject?) === bubble
                || (constraint.secondItem as AnyObject?) === bubble
            return referencesBubble ? ObjectIdentifier(constraint) : nil
        })
    }

    private func labelTexts(in view: UIView) -> [String] {
        let ownText = (view as? UILabel)?.text.map { [$0] } ?? []
        return ownText + view.subviews.flatMap(labelTexts(in:))
    }
}
