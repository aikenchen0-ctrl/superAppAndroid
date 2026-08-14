import UIKit

struct InteractionSnapshotDragPayload: Equatable {
    let title: String
    let isVideo: Bool
}

protocol InteractionKitProviding {
    func installGlobalInteractions(on window: UIWindow)
    func snapshotDragPayloadTitle(defaultTitle: String) -> String
}

final class DefaultInteractionKitAdapter: InteractionKitProviding {
    private let summaryOverlayCoordinator = SafeAreaSummaryOverlayCoordinator()

    func installGlobalInteractions(on window: UIWindow) {
        summaryOverlayCoordinator.install(on: window)
    }

    func snapshotDragPayloadTitle(defaultTitle: String) -> String {
        defaultTitle.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? "页面快照" : defaultTitle
    }
}

enum InteractionKitDemo {
    static func snapshotDragPayload() -> InteractionSnapshotDragPayload {
        InteractionSnapshotDragPayload(title: "页面快照", isVideo: false)
    }
}
