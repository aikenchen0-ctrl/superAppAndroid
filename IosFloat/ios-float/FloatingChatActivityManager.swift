import Foundation
#if canImport(ActivityKit)
import ActivityKit
#endif

final class FloatingChatActivityManager {
    static let shared = FloatingChatActivityManager()

    private init() {}

    func startOrUpdate(state: FloatingChatActivityState) {
#if canImport(ActivityKit)
        guard #available(iOS 16.1, *) else { return }

        let contentState = FloatingChatActivityAttributes.ContentState(state: state)
        if let activity = Activity<FloatingChatActivityAttributes>.activities.first {
            Task {
                await activity.update(using: contentState)
            }
            return
        }

        do {
            _ = try Activity.request(
                attributes: FloatingChatActivityAttributes(chatID: "floating-chat"),
                contentState: contentState,
                pushType: nil
            )
        } catch {
            NSLog("FloatingChatActivity request failed: \(error.localizedDescription)")
        }
#endif
    }

    func end() {
#if canImport(ActivityKit)
        guard #available(iOS 16.1, *) else { return }
        Task {
            for activity in Activity<FloatingChatActivityAttributes>.activities {
                await activity.end(dismissalPolicy: .immediate)
            }
        }
#endif
    }
}
