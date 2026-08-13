import UIKit

enum RightToolAction {
    case groupInfo
    case accessReview
    case moments
    case autoReply
    case hiddenUsers
    case leftSidebarDisplay
    case sideEffect
    case bubble3D
    case sendName
    case paymentStatus
    case finderPublish
    case customerProfile
    case contactRelations
    case momentMaterials
    case wechatMiniProgramShare
    case blinkVoice
    case voiceAssistant
    case selectionAssist
    case backgroundRemoval
    case uiOperationLab
    case openApiEnvironment
    case openApiBusiness
    case openApiFriends
    case syncChatrooms
    case messageTool(ChatMessageType, requiresActiveConversation: Bool)
}

enum MessageToolAction {
    case quickPhrases
    case cameraPhoto
    case mediaLibraryImages
    case videoOptions
    case documentPicker
    case voiceHint
    case call(ChatMessageType)
    case stickerSheet
    case stickerGifDemo
    case location(ChatMessageType)
    case contactCardDesigner
    case webLinkSheet
    case sendDemo(ChatMessageType)
    case groupInviteComposer
    case couponWallet
    case multiSelect
    case relayComposer
    case redPacket
    case transfer
    case splitBill
    case favoriteShare
}

enum ToolActionRouter {
    static func rightToolAction(
        for item: SidebarItem,
        groupInfoTitle: String,
        isGroupInfoAvailable: Bool
    ) -> RightToolAction? {
        if item.title == groupInfoTitle {
            return isGroupInfoAvailable ? .groupInfo : nil
        }

        if let customTool = item.customTool {
            return rightToolAction(for: customTool)
        }

        guard let type = item.toolMessageType else { return nil }
        return .messageTool(type, requiresActiveConversation: type != .coupon)
    }

    static func messageToolAction(for type: ChatMessageType) -> MessageToolAction {
        switch type {
        case .text:
            return .quickPhrases
        case .capturedPhoto:
            return .cameraPhoto
        case .image:
            return .mediaLibraryImages
        case .video:
            return .videoOptions
        case .file:
            return .documentPicker
        case .voice:
            return .voiceHint
        case .voiceCall, .videoCall:
            return .call(type)
        case .emoji:
            return .stickerSheet
        case .stickerGif:
            return .stickerGifDemo
        case .location, .liveLocation:
            return .location(type)
        case .contactCard:
            return .contactCardDesigner
        case .webLink:
            return .webLinkSheet
        case .miniProgram:
            return .sendDemo(type)
        case .groupInvite:
            return .groupInviteComposer
        case .coupon:
            return .couponWallet
        case .article, .channelsVideo, .channelsLive, .music, .groupNotice, .system, .quotedReply:
            return .sendDemo(type)
        case .mergedForward:
            return .multiSelect
        case .relay:
            return .relayComposer
        case .redPacket:
            return .redPacket
        case .transfer:
            return .transfer
        case .splitBill:
            return .splitBill
        case .favorite:
            return .favoriteShare
        }
    }

    private static func rightToolAction(for customTool: SidebarItem.CustomTool) -> RightToolAction {
        switch customTool {
        case .accessReview: return .accessReview
        case .moments: return .moments
        case .autoReply: return .autoReply
        case .hiddenUsers: return .hiddenUsers
        case .sideEffect: return .sideEffect
        case .bubble3D: return .bubble3D
        case .sendName: return .sendName
        case .paymentStatus: return .paymentStatus
        case .finderPublish: return .finderPublish
        case .customerProfile: return .customerProfile
        case .contactRelations: return .contactRelations
        case .momentMaterials: return .momentMaterials
        case .wechatMiniProgramShare: return .wechatMiniProgramShare
        case .leftSidebarDisplay: return .leftSidebarDisplay
        case .blinkVoice: return .blinkVoice
        case .voiceAssistant: return .voiceAssistant
        case .selectionAssist: return .selectionAssist
        case .backgroundRemoval: return .backgroundRemoval
        case .uiOperationLab: return .uiOperationLab
        case .openApiEnvironment: return .openApiEnvironment
        case .openApiBusiness: return .openApiBusiness
        case .openApiFriends: return .openApiFriends
        case .syncChatrooms: return .syncChatrooms
        }
    }
}
