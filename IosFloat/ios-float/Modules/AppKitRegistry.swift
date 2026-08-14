import Foundation

enum AppKitModule: String, CaseIterable {
    case aispectInteractionSDK = "Aispect 交互 SDK"
    case floatingIMKit = "悬浮 IM Kit"
    case messageRenderKit = "消息渲染 Kit"
    case aiInputKit = "AI 输入框 Kit"
    case dataLayer = "数据层"
    case businessPages = "业务页面模块"

    var directory: String {
        switch self {
        case .aispectInteractionSDK: return "Modules/Interaction"
        case .floatingIMKit: return "Modules/FloatingIM"
        case .messageRenderKit: return "Modules/MessageRender"
        case .aiInputKit: return "Modules/AIInput"
        case .dataLayer: return "Modules/Data"
        case .businessPages: return "Modules/Business"
        }
    }

    var responsibility: String {
        switch self {
        case .aispectInteractionSDK:
            return "眨眼、侧边拉出、灵动岛、手势拖拽等跨页面交互能力。"
        case .floatingIMKit:
            return "左右账号栏、好友/群会话列表、连接线、拖拽转发和悬浮窗口。"
        case .messageRenderKit:
            return "文本、富文本、图片、视频、红包、转账、文件、卡片等消息气泡渲染。"
        case .aiInputKit:
            return "AI 草稿、重写、润色、续写、自动回复、意图计划和日历写入。"
        case .dataLayer:
            return "SQLite 消息、附件索引、KV 配置、OpenAPI 缓存和本地模拟数据。"
        case .businessPages:
            return "朋友圈、素材库、客户档案、视频号发布、OpenAPI 环境等业务页面。"
        }
    }

    var currentStatus: String {
        switch self {
        case .aispectInteractionSDK:
            return "已拆到物理目录，保留 BlinkVoiceKit 与本地交互适配入口。"
        case .floatingIMKit:
            return "已拆到物理目录，提供会话可见性、选中账号和拖拽转发边界协议。"
        case .messageRenderKit:
            return "已拆到物理目录，消息高度估算和气泡 schema 通过 Kit 统一暴露。"
        case .aiInputKit:
            return "已拆到物理目录，提供重写、润色、续写和本地 demo 适配器。"
        case .dataLayer:
            return "已拆到物理目录，SQLite Store 通过数据层 facade 暴露。"
        case .businessPages:
            return "已拆到物理目录，业务页面通过页面目录和路由描述统一管理。"
        }
    }

    var suggestedOwner: String {
        switch self {
        case .aispectInteractionSDK: return "Interaction"
        case .floatingIMKit: return "IM"
        case .messageRenderKit: return "Render"
        case .aiInputKit: return "AI"
        case .dataLayer: return "Data"
        case .businessPages: return "Business"
        }
    }
}

struct AppKitManifest {
    let modules: [AppKitModule]

    static let current = AppKitManifest(modules: AppKitModule.allCases)

    var rows: [(title: String, subtitle: String, owner: String)] {
        modules.map {
            (
                title: $0.rawValue,
                subtitle: "\($0.directory)\n\($0.responsibility)\n\($0.currentStatus)",
                owner: $0.suggestedOwner
            )
        }
    }
}

final class AppKitRegistry {
    static let shared = AppKitRegistry()

    let interactionKit: InteractionKitProviding
    let floatingIMKit: FloatingIMKitProviding
    let dataLayer: ChatDataLayerProviding
    let messageRenderKit: MessageRenderKitProviding
    let aiInputKit: AIInputKitProviding
    let businessKit: BusinessPageKitProviding
    let manifest: AppKitManifest

    private init(
        interactionKit: InteractionKitProviding = DefaultInteractionKitAdapter(),
        floatingIMKit: FloatingIMKitProviding = EmptyFloatingIMKitAdapter(),
        dataLayer: ChatDataLayerProviding = SQLiteChatDataLayerAdapter(),
        messageRenderKit: MessageRenderKitProviding = DefaultMessageRenderKitAdapter(),
        aiInputKit: AIInputKitProviding = LocalAIInputKitAdapter(),
        businessKit: BusinessPageKitProviding = DefaultBusinessPageKitAdapter(),
        manifest: AppKitManifest = .current
    ) {
        self.interactionKit = interactionKit
        self.floatingIMKit = floatingIMKit
        self.dataLayer = dataLayer
        self.messageRenderKit = messageRenderKit
        self.aiInputKit = aiInputKit
        self.businessKit = businessKit
        self.manifest = manifest
    }
}
