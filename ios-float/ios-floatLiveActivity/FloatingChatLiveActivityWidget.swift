import ActivityKit
import SwiftUI
import WidgetKit

@main
struct FloatingChatLiveActivityWidgetBundle: WidgetBundle {
    var body: some Widget {
        FloatingChatLiveActivityWidget()
    }
}

struct FloatingChatLiveActivityWidget: Widget {
    private let floatingChatURL = URL(string: "ios-float://chat")

    var body: some WidgetConfiguration {
        ActivityConfiguration(for: FloatingChatActivityAttributes.self) { context in
            FloatingChatLockScreenView(state: context.state)
                .widgetURL(floatingChatURL)
        } dynamicIsland: { context in
            DynamicIsland {
                DynamicIslandExpandedRegion(.leading) {
                    FloatingChatAppBadge(state: context.state, size: .large)
                }
                DynamicIslandExpandedRegion(.center) {
                    FloatingChatIslandTitle(state: context.state)
                }
                DynamicIslandExpandedRegion(.trailing) {
                    FloatingChatCompactStatus(state: context.state)
                }
                DynamicIslandExpandedRegion(.bottom) {
                    FloatingChatFullIslandContent(state: context.state)
                }
            } compactLeading: {
                FloatingChatAppBadge(state: context.state, size: .compact)
            } compactTrailing: {
                FloatingChatCompactStatus(state: context.state, compact: true)
            } minimal: {
                FloatingChatAppBadge(state: context.state, size: .minimal)
            }
            .widgetURL(floatingChatURL)
        }
    }
}

private struct FloatingChatLockScreenView: View {
    let state: FloatingChatActivityAttributes.ContentState

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 10) {
                FloatingChatAppBadge(state: state, size: .large)
                FloatingChatIslandTitle(state: state)
                Spacer(minLength: 8)
                if !state.updatedAtText.isEmpty {
                    Text(state.updatedAtText)
                        .font(.caption2.weight(.medium))
                        .foregroundStyle(.white.opacity(0.58))
                }
            }

            FloatingChatStatusGrid(state: state)
            FloatingChatMessagePreview(state: state)
            FloatingChatIslandInputBar(text: inputText)
        }
        .padding(14)
        .background(
            LinearGradient(
                colors: [
                    Color(red: 0.06, green: 0.08, blue: 0.09),
                    Color(red: 0.03, green: 0.04, blue: 0.05)
                ],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
        )
    }

    private var inputText: String {
        state.draft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? "回到只发继续聊天" : state.draft
    }
}

private struct FloatingChatIslandTitle: View {
    let state: FloatingChatActivityAttributes.ContentState

    var body: some View {
        VStack(alignment: .leading, spacing: 3) {
            HStack(spacing: 6) {
                Text(state.title)
                    .font(.headline.weight(.semibold))
                    .foregroundStyle(.white)
                    .lineLimit(1)
                if state.unreadCount > 0 {
                    Text("\(state.unreadCount)")
                        .font(.caption2.weight(.bold))
                        .foregroundStyle(.white)
                        .padding(.horizontal, 5)
                        .padding(.vertical, 2)
                        .background(Color.red, in: Capsule())
                }
            }
            Text(state.subtitle)
                .font(.caption2.weight(.medium))
                .foregroundStyle(.white.opacity(0.66))
                .lineLimit(1)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

private struct FloatingChatFullIslandContent: View {
    let state: FloatingChatActivityAttributes.ContentState

    var body: some View {
        VStack(spacing: 9) {
            FloatingChatStatusGrid(state: state)
            FloatingChatMessagePreview(state: state)
            FloatingChatIslandInputBar(text: inputText)
        }
        .padding(.top, 2)
    }

    private var inputText: String {
        state.draft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? "打开聊天输入..." : state.draft
    }
}

private struct FloatingChatStatusGrid: View {
    let state: FloatingChatActivityAttributes.ContentState

    var body: some View {
        HStack(spacing: 6) {
            FloatingChatStatusChip(
                title: "IM",
                text: state.imStatusText,
                symbolName: "bubble.left.and.bubble.right.fill",
                accent: imAccent
            )
            FloatingChatStatusChip(
                title: "AI",
                text: state.aiTaskText,
                symbolName: state.aiTaskActive ? "sparkles" : "bolt.fill",
                accent: state.aiTaskActive ? Color(red: 0.96, green: 0.72, blue: 0.28) : Color.white.opacity(0.42),
                isActive: state.aiTaskActive
            )
            FloatingChatStatusChip(
                title: "状态",
                text: activeStatusText,
                symbolName: activeStatusSymbol,
                accent: activeStatusAccent
            )
        }
    }

    private var imAccent: Color {
        switch state.imStatusLevel {
        case "warning": return Color(red: 1.0, green: 0.68, blue: 0.22)
        case "busy": return Color(red: 0.35, green: 0.65, blue: 1.0)
        default: return Color(red: 0.38, green: 0.88, blue: 0.48)
        }
    }

    private var activeStatusText: String {
        state.callStatusKind == "none" ? state.mediaStatusText : state.callStatusText
    }

    private var activeStatusSymbol: String {
        switch state.callStatusKind {
        case "voice": return "phone.fill"
        case "video": return "video.fill"
        default:
            switch state.mediaStatusKind {
            case "voice": return "waveform"
            case "video": return "play.rectangle.fill"
            case "music": return "music.note"
            case "image": return "photo.fill"
            default: return "circle.grid.2x2.fill"
            }
        }
    }

    private var activeStatusAccent: Color {
        state.callStatusKind == "none"
            ? Color(red: 0.42, green: 0.72, blue: 1.0)
            : Color(red: 0.46, green: 0.88, blue: 0.52)
    }
}

private struct FloatingChatStatusChip: View {
    let title: String
    let text: String
    let symbolName: String
    let accent: Color
    var isActive = false

    var body: some View {
        HStack(spacing: 5) {
            ZStack {
                Circle()
                    .fill(accent.opacity(isActive ? 0.28 : 0.18))
                Image(systemName: symbolName)
                    .font(.system(size: 10, weight: .bold))
                    .foregroundStyle(accent)
            }
            .frame(width: 20, height: 20)

            VStack(alignment: .leading, spacing: 1) {
                Text(title)
                    .font(.system(size: 8, weight: .bold))
                    .foregroundStyle(.white.opacity(0.48))
                    .lineLimit(1)
                Text(text)
                    .font(.system(size: 9, weight: .semibold))
                    .foregroundStyle(.white.opacity(0.9))
                    .lineLimit(1)
            }
        }
        .padding(.horizontal, 7)
        .padding(.vertical, 6)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(.white.opacity(0.12), in: RoundedRectangle(cornerRadius: 11, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 11, style: .continuous)
                .stroke(accent.opacity(isActive ? 0.36 : 0.18), lineWidth: 1)
        )
    }
}

private struct FloatingChatMessagePreview: View {
    let state: FloatingChatActivityAttributes.ContentState

    var body: some View {
        VStack(spacing: 5) {
            ForEach(Array(displayMessages.enumerated()), id: \.offset) { _, message in
                FloatingChatIslandMessageRow(message: message)
            }
        }
        .frame(maxWidth: .infinity)
    }

    private var displayMessages: [FloatingChatActivityMessage] {
        let messages = state.messages
        guard !messages.isEmpty else {
            return [
                FloatingChatActivityMessage(sender: "只发", text: "暂无新消息", isOutgoing: false)
            ]
        }
        return Array(messages.suffix(3))
    }
}

private struct FloatingChatAppBadge: View {
    enum Size {
        case minimal
        case compact
        case large
    }

    let state: FloatingChatActivityAttributes.ContentState
    var size: Size = .large

    var body: some View {
        ZStack(alignment: .bottomTrailing) {
            ZStack {
                RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                    .fill(Color.white.opacity(0.08))
                Text(state.appBadgeText)
                    .font(.system(size: textSize, weight: .heavy))
                    .foregroundStyle(.white)
                    .shadow(color: .black.opacity(0.28), radius: 2, x: 0, y: 1)
                    .opacity(size == .minimal ? 0 : 0.32)
                Image(state.appIconAssetName)
                    .resizable()
                    .scaledToFit()
                    .padding(iconPadding)
            }

            if state.unreadCount > 0 && size == .large {
                Text("\(min(state.unreadCount, 99))")
                    .font(.system(size: 9, weight: .bold))
                    .foregroundStyle(.white)
                    .frame(width: 18, height: 18)
                    .background(Color.red, in: Circle())
                    .offset(x: 4, y: 4)
            }
        }
        .frame(width: dimension, height: dimension)
    }

    private var dimension: CGFloat {
        switch size {
        case .minimal: return 18
        case .compact: return 28
        case .large: return 54
        }
    }

    private var cornerRadius: CGFloat {
        switch size {
        case .minimal: return 6
        case .compact: return 8
        case .large: return 14
        }
    }

    private var iconPadding: CGFloat {
        switch size {
        case .minimal: return 1
        case .compact: return 2
        case .large: return 4
        }
    }

    private var textSize: CGFloat {
        switch size {
        case .minimal: return 0
        case .compact: return 10
        case .large: return 16
        }
    }
}

private struct FloatingChatCompactStatus: View {
    let state: FloatingChatActivityAttributes.ContentState
    var compact = false

    var body: some View {
        HStack(spacing: compact ? 3 : 5) {
            Circle()
                .fill(dotColor)
                .frame(width: compact ? 7 : 9, height: compact ? 7 : 9)
            if !compact {
                Text(label)
                    .font(.caption2.weight(.bold))
                    .foregroundStyle(.white.opacity(0.88))
                    .lineLimit(1)
            }
        }
        .padding(.horizontal, compact ? 0 : 7)
        .padding(.vertical, compact ? 0 : 5)
        .background(compact ? Color.clear : Color.white.opacity(0.12), in: Capsule())
    }

    private var label: String {
        if state.aiTaskActive { return "AI" }
        if state.callStatusKind != "none" { return "通话" }
        if state.unreadCount > 0 { return "\(state.unreadCount)" }
        return "在线"
    }

    private var dotColor: Color {
        if state.aiTaskActive { return Color(red: 1.0, green: 0.76, blue: 0.30) }
        if state.callStatusKind != "none" { return Color(red: 0.42, green: 0.88, blue: 0.46) }
        if state.unreadCount > 0 { return .red }
        return Color(red: 0.38, green: 0.88, blue: 0.48)
    }
}

private struct FloatingChatMiniAvatar: View {
    let text: String
    var accent = Color(red: 0.32, green: 0.82, blue: 0.34)

    var body: some View {
        Text(String(text.prefix(1)))
            .font(.caption2.weight(.bold))
            .foregroundStyle(.white)
            .frame(width: 20, height: 20)
            .background(accent, in: Circle())
    }
}

private struct FloatingChatIslandMessageRow: View {
    let message: FloatingChatActivityMessage

    var body: some View {
        HStack(alignment: .bottom, spacing: 5) {
            if message.isOutgoing {
                Spacer(minLength: 22)
            } else {
                FloatingChatMiniAvatar(text: message.sender)
            }

            VStack(alignment: message.isOutgoing ? .trailing : .leading, spacing: 2) {
                Text(message.sender)
                    .font(.system(size: 8, weight: .semibold))
                    .foregroundStyle(.white.opacity(0.48))
                    .lineLimit(1)
                Text(message.text)
                    .font(.caption2.weight(.medium))
                    .foregroundStyle(.white)
                    .lineLimit(2)
                    .padding(.horizontal, 9)
                    .padding(.vertical, 6)
                    .background(
                        message.isOutgoing
                            ? Color(red: 0.18, green: 0.55, blue: 0.96).opacity(0.92)
                            : .white.opacity(0.14),
                        in: RoundedRectangle(cornerRadius: 11, style: .continuous)
                    )
            }
            .frame(maxWidth: 210, alignment: message.isOutgoing ? .trailing : .leading)

            if message.isOutgoing {
                FloatingChatMiniAvatar(text: message.sender, accent: Color(red: 0.18, green: 0.55, blue: 0.96))
            } else {
                Spacer(minLength: 22)
            }
        }
        .frame(maxWidth: .infinity, alignment: message.isOutgoing ? .trailing : .leading)
    }
}

private struct FloatingChatIslandInputBar: View {
    let text: String

    var body: some View {
        HStack(spacing: 8) {
            Image(systemName: "mic.fill")
                .font(.caption2.weight(.bold))
            Text(text)
                .font(.caption2.weight(.medium))
                .foregroundStyle(.white.opacity(0.7))
                .lineLimit(1)
                .frame(maxWidth: .infinity, alignment: .leading)
            Image(systemName: "sparkles")
                .font(.caption2.weight(.bold))
                .foregroundStyle(Color(red: 1.0, green: 0.76, blue: 0.30))
            Image(systemName: "paperplane.fill")
                .font(.caption2.weight(.bold))
                .foregroundStyle(Color(red: 0.42, green: 0.92, blue: 0.38))
        }
        .foregroundStyle(.white.opacity(0.82))
        .padding(.horizontal, 11)
        .padding(.vertical, 8)
        .background(.white.opacity(0.13), in: Capsule())
    }
}
