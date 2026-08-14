import UIKit

private extension UIColor {
    var rgbaComponents: [CGFloat] {
        var red: CGFloat = 0
        var green: CGFloat = 0
        var blue: CGFloat = 0
        var alpha: CGFloat = 0
        getRed(&red, green: &green, blue: &blue, alpha: &alpha)
        return [red, green, blue, alpha]
    }

    convenience init(rgbaComponents components: [CGFloat]) {
        let padded = components + Array(repeating: 1, count: max(0, 4 - components.count))
        self.init(red: padded[0], green: padded[1], blue: padded[2], alpha: padded[3])
    }
}

enum ChatMessageType: Int, CaseIterable, Codable {
    case text = 1
    case emoji
    case stickerGif
    case image
    case capturedPhoto
    case voice
    case video
    case file
    case location
    case liveLocation
    case contactCard
    case groupInvite
    case webLink
    case article
    case miniProgram
    case channelsVideo
    case channelsLive
    case music
    case favorite
    case mergedForward
    case redPacket
    case transfer
    case splitBill
    case coupon
    case voiceCall
    case videoCall
    case quotedReply
    case relay
    case groupNotice
    case system

    var title: String {
        switch self {
        case .text: return "\u{6587}\u{672c}\u{6d88}\u{606f}"
        case .emoji: return "Emoji \u{8868}\u{60c5}"
        case .stickerGif: return "\u{8d34}\u{7eb8} / GIF"
        case .image: return "\u{56fe}\u{7247}"
        case .capturedPhoto: return "\u{62cd}\u{6444}\u{7167}\u{7247}"
        case .voice: return "\u{8bed}\u{97f3}\u{6d88}\u{606f}"
        case .video: return "\u{89c6}\u{9891} / \u{77ed}\u{89c6}\u{9891}"
        case .file: return "\u{6587}\u{4ef6} / \u{6587}\u{6863}"
        case .location: return "\u{4f4d}\u{7f6e}\u{6d88}\u{606f}"
        case .liveLocation: return "\u{5b9e}\u{65f6}\u{4f4d}\u{7f6e}"
        case .contactCard: return "\u{4e2a}\u{4eba}\u{540d}\u{7247}"
        case .groupInvite: return "\u{7fa4}\u{9080}\u{8bf7}\u{5361}"
        case .webLink: return "\u{7f51}\u{9875}\u{94fe}\u{63a5}"
        case .article: return "\u{516c}\u{4f17}\u{53f7}\u{6587}\u{7ae0}"
        case .miniProgram: return "\u{5c0f}\u{7a0b}\u{5e8f}\u{5361}\u{7247}"
        case .channelsVideo: return "\u{89c6}\u{9891}\u{53f7}\u{89c6}\u{9891}"
        case .channelsLive: return "\u{89c6}\u{9891}\u{53f7}\u{76f4}\u{64ad}"
        case .music: return "\u{97f3}\u{4e50}\u{5206}\u{4eab}"
        case .favorite: return "\u{6536}\u{85cf}\u{5206}\u{4eab}"
        case .mergedForward: return "\u{804a}\u{5929}\u{8bb0}\u{5f55}"
        case .redPacket: return "\u{7ea2}\u{5305}"
        case .transfer: return "\u{8f6c}\u{8d26}"
        case .splitBill: return "AA \u{6536}\u{6b3e}"
        case .coupon: return "\u{5fae}\u{4fe1}\u{5361}\u{5238}"
        case .voiceCall: return "\u{8bed}\u{97f3}\u{901a}\u{8bdd}"
        case .videoCall: return "\u{89c6}\u{9891}\u{901a}\u{8bdd}"
        case .quotedReply: return "\u{5f15}\u{7528}\u{56de}\u{590d}"
        case .relay: return "\u{63a5}\u{9f99}\u{6d88}\u{606f}"
        case .groupNotice: return "\u{7fa4}\u{516c}\u{544a}"
        case .system: return "\u{7cfb}\u{7edf}\u{63d0}\u{793a}"
        }
    }

    var symbolName: String {
        switch self {
        case .text: return "text.bubble"
        case .emoji: return "face.smiling"
        case .stickerGif: return "sparkles.rectangle.stack"
        case .image, .capturedPhoto: return "photo"
        case .voice: return "waveform"
        case .video: return "play.rectangle"
        case .file: return "doc"
        case .location, .liveLocation: return "location"
        case .contactCard: return "person.crop.rectangle"
        case .groupInvite: return "person.2"
        case .webLink: return "link"
        case .article: return "newspaper"
        case .miniProgram: return "app"
        case .channelsVideo: return "video"
        case .channelsLive: return "dot.radiowaves.left.and.right"
        case .music: return "music.note"
        case .favorite: return "star"
        case .mergedForward: return "bubble.left.and.bubble.right"
        case .redPacket: return "giftcard"
        case .transfer: return "yensign.circle"
        case .splitBill: return "person.3"
        case .coupon: return "ticket"
        case .voiceCall: return "phone"
        case .videoCall: return "video.fill"
        case .quotedReply: return "quote.bubble"
        case .relay: return "list.bullet.rectangle"
        case .groupNotice: return "megaphone"
        case .system: return "info.circle"
        }
    }

    var accentColor: UIColor {
        switch self {
        case .redPacket, .transfer, .splitBill, .coupon: return UIColor.systemOrange
        case .location, .liveLocation: return UIColor.systemGreen
        case .voiceCall, .videoCall, .voice, .video: return UIColor.systemBlue
        case .system, .groupNotice: return UIColor.systemGray
        case .miniProgram, .channelsVideo, .channelsLive: return UIColor.systemPurple
        default: return UIColor(red: 0.09, green: 0.34, blue: 0.42, alpha: 1)
        }
    }

    var template: MessageRenderTemplate {
        MessageRenderTemplate(
            id: rawValue,
            primaryText: title,
            secondaryText: templateSecondaryText,
            symbolName: symbolName,
            accentColor: accentColor,
            layout: templateLayout,
            preferredHeight: preferredMessageHeight,
            minimumReadableHeight: minimumReadableHeight
        )
    }

    private var templateLayout: MessageTemplateLayout {
        switch self {
        case .text:
            return .plainText
        case .emoji:
            return .largeInline
        case .stickerGif:
            return .mediaSticker
        case .image, .capturedPhoto:
            return .photo
        case .voice:
            return .voiceWave
        case .video:
            return .videoPreview
        case .channelsVideo:
            return .channelsVideoCard
        case .file:
            return .document
        case .location, .liveLocation:
            return .map
        case .contactCard, .groupInvite:
            return .profileCard
        case .webLink, .article, .miniProgram, .channelsLive, .music, .favorite:
            return .richLink
        case .mergedForward:
            return .mergedForward
        case .redPacket, .transfer, .splitBill:
            return .payment
        case .coupon:
            return .coupon
        case .voiceCall, .videoCall:
            return .callRecord
        case .quotedReply:
            return .quoted
        case .relay:
            return .relay
        case .groupNotice:
            return .notice
        case .system:
            return .system
        }
    }

    private var preferredMessageHeight: CGFloat {
        if self == .redPacket {
            return 110
        }
        if self == .quotedReply {
            return 112
        }
        switch templateLayout {
        case .system:
            return 40
        case .plainText, .largeInline:
            return 92
        case .voiceWave, .callRecord:
            return 116
        case .photo, .mediaSticker, .videoPreview:
            return 152
        case .map, .profileCard, .richLink, .document:
            return 168
        case .payment:
            return 108
        case .coupon:
            return 138
        case .quoted, .mergedForward, .relay, .notice:
            return 158
        default:
            return 132
        }
    }

    private var minimumReadableHeight: CGFloat {
        if self == .redPacket {
            return 104
        }
        if self == .quotedReply {
            return 92
        }
        switch templateLayout {
        case .system:
            return 34
        case .plainText:
            return 56
        case .largeInline:
            return 70
        case .photo, .mediaSticker, .videoPreview:
            return 116
        case .voiceWave, .callRecord:
            return 96
        case .map, .profileCard, .richLink, .document:
            return 132
        case .payment:
            return 102
        case .coupon, .quoted, .mergedForward, .relay, .notice:
            return 132
        default:
            return 118
        }
    }

    private var templateSecondaryText: String {
        switch self {
        case .text: return "\u{666e}\u{901a}\u{6587}\u{672c}\u{6c14}\u{6ce1}"
        case .emoji: return "\u{653e}\u{5927}\u{8868}\u{60c5}\u{548c}\u{77ed}\u{6587}\u{672c}"
        case .stickerGif: return "\u{52a8}\u{6001}\u{8d34}\u{7eb8}\u{5361}\u{7247}"
        case .image: return "\u{56fe}\u{7247}\u{9884}\u{89c8}"
        case .capturedPhoto: return "\u{76f8}\u{673a}\u{5feb}\u{7167}"
        case .voice: return "\u{6ce2}\u{5f62}\u{8bed}\u{97f3}\u{6761}"
        case .video: return "\u{77ed}\u{89c6}\u{9891}\u{9884}\u{89c8}"
        case .file: return "\u{6587}\u{6863}\u{9644}\u{4ef6}"
        case .location: return "\u{9759}\u{6001}\u{5730}\u{56fe}\u{5361}"
        case .liveLocation: return "\u{5b9e}\u{65f6}\u{5b9a}\u{4f4d}\u{72b6}\u{6001}"
        case .contactCard: return "\u{4e2a}\u{4eba}\u{8d44}\u{6599}\u{5361}"
        case .groupInvite: return "\u{7fa4}\u{9080}\u{8bf7}\u{5361}"
        case .webLink: return "\u{7f51}\u{9875}\u{6458}\u{8981}\u{5361}"
        case .article: return "\u{56fe}\u{6587}\u{6587}\u{7ae0}\u{5361}"
        case .miniProgram: return "\u{5c0f}\u{7a0b}\u{5e8f}\u{9875}\u{9762}"
        case .channelsVideo: return "\u{89c6}\u{9891}\u{53f7}\u{5185}\u{5bb9}"
        case .channelsLive: return "\u{76f4}\u{64ad}\u{9884}\u{544a}"
        case .music: return "\u{97f3}\u{4e50}\u{5c01}\u{9762}\u{5361}"
        case .favorite: return "\u{6536}\u{85cf}\u{6761}\u{76ee}"
        case .mergedForward: return "\u{591a}\u{6761}\u{804a}\u{5929}\u{6c47}\u{603b}"
        case .redPacket: return "\u{7ea2}\u{5305}\u{5361}\u{9762}"
        case .transfer: return "\u{8f6c}\u{8d26}\u{91d1}\u{989d}"
        case .splitBill: return "\u{7fa4}\u{6536}\u{6b3e}\u{660e}\u{7ec6}"
        case .coupon: return "\u{5361}\u{5238}\u{5165}\u{53e3}"
        case .voiceCall: return "\u{901a}\u{8bdd}\u{8bb0}\u{5f55}"
        case .videoCall: return "\u{89c6}\u{9891}\u{901a}\u{8bdd}\u{8bb0}\u{5f55}"
        case .quotedReply: return "\u{5f15}\u{7528}\u{5185}\u{5bb9}\u{4e0a}\u{7f6e}"
        case .relay: return "\u{63a5}\u{9f99}\u{6761}\u{76ee}"
        case .groupNotice: return "\u{7fa4}\u{901a}\u{77e5}"
        case .system: return "\u{5c45}\u{4e2d}\u{63d0}\u{793a}"
        }
    }
}

enum BubblePresentation: String, CaseIterable, Codable {
    case avatarAndName
    case avatarOnly
    case nameOnly
    case bare
}

enum MessageTemplateLayout: Hashable {
    case plainText
    case largeInline
    case mediaSticker
    case photo
    case voiceWave
    case videoPreview
    case channelsVideoCard
    case document
    case map
    case profileCard
    case richLink
    case mergedForward
    case payment
    case coupon
    case callRecord
    case quoted
    case relay
    case notice
    case system
}

struct MessageRenderTemplate: Hashable {
    let id: Int
    let primaryText: String
    let secondaryText: String
    let symbolName: String
    let accentColor: UIColor
    let layout: MessageTemplateLayout
    let preferredHeight: CGFloat
    let minimumReadableHeight: CGFloat

    static func == (lhs: MessageRenderTemplate, rhs: MessageRenderTemplate) -> Bool {
        lhs.id == rhs.id
    }

    func hash(into hasher: inout Hasher) {
        hasher.combine(id)
    }
}

enum ChatParticipantKind: String, Codable {
    case account
    case contact
    case group
    case groupMember
    case assistant
    case placeholder
}

struct ChatParticipant: Hashable, Codable {
    let id: UUID
    let displayName: String
    let tintColor: UIColor
    let initials: String
    let isCurrentUser: Bool
    let avatarURL: URL?
    let kind: ChatParticipantKind

    private enum CodingKeys: String, CodingKey {
        case id
        case displayName
        case tintColor
        case initials
        case isCurrentUser
        case avatarURL
        case kind
    }

    init(
        id: UUID,
        displayName: String,
        tintColor: UIColor,
        initials: String,
        isCurrentUser: Bool = false,
        avatarURL: URL? = nil,
        kind: ChatParticipantKind? = nil
    ) {
        self.id = id
        self.displayName = displayName
        self.tintColor = tintColor
        self.initials = initials
        self.isCurrentUser = isCurrentUser
        self.avatarURL = avatarURL
        self.kind = kind ?? (isCurrentUser ? .account : .contact)
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        id = try container.decode(UUID.self, forKey: .id)
        displayName = try container.decode(String.self, forKey: .displayName)
        let colorComponents = try container.decode([CGFloat].self, forKey: .tintColor)
        tintColor = UIColor(rgbaComponents: colorComponents)
        initials = try container.decode(String.self, forKey: .initials)
        isCurrentUser = try container.decode(Bool.self, forKey: .isCurrentUser)
        avatarURL = try container.decodeIfPresent(URL.self, forKey: .avatarURL)
        kind = try container.decodeIfPresent(ChatParticipantKind.self, forKey: .kind)
            ?? (isCurrentUser ? .account : .contact)
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(id, forKey: .id)
        try container.encode(displayName, forKey: .displayName)
        try container.encode(tintColor.rgbaComponents, forKey: .tintColor)
        try container.encode(initials, forKey: .initials)
        try container.encode(isCurrentUser, forKey: .isCurrentUser)
        try container.encodeIfPresent(avatarURL, forKey: .avatarURL)
        try container.encode(kind, forKey: .kind)
    }

    static func == (lhs: ChatParticipant, rhs: ChatParticipant) -> Bool {
        lhs.id == rhs.id
    }

    func hash(into hasher: inout Hasher) {
        hasher.combine(id)
    }
}

struct ContactCardProfile: Hashable, Codable {
    let accountID: UUID
    var displayName: String
    var role: String
    var company: String
    var wechatID: String
    var phone: String
    var bio: String
    var styleIndex: Int

    var styleName: String {
        switch styleIndex {
        case 1: return "商务绿"
        case 2: return "科技蓝"
        case 3: return "暖橙"
        default: return "经典"
        }
    }

    var accentColor: UIColor {
        switch styleIndex {
        case 1: return UIColor(red: 0.10, green: 0.56, blue: 0.35, alpha: 1)
        case 2: return UIColor(red: 0.13, green: 0.40, blue: 0.82, alpha: 1)
        case 3: return UIColor(red: 0.86, green: 0.42, blue: 0.18, alpha: 1)
        default: return UIColor(red: 0.12, green: 0.34, blue: 0.42, alpha: 1)
        }
    }

    var summary: String {
        [role, company, wechatID.isEmpty ? "" : "微信 \(wechatID)"]
            .filter { !$0.isEmpty }
            .joined(separator: " · ")
    }

    static func == (lhs: ContactCardProfile, rhs: ContactCardProfile) -> Bool {
        lhs.accountID == rhs.accountID
            && lhs.displayName == rhs.displayName
            && lhs.role == rhs.role
            && lhs.company == rhs.company
            && lhs.wechatID == rhs.wechatID
            && lhs.phone == rhs.phone
            && lhs.bio == rhs.bio
            && lhs.styleIndex == rhs.styleIndex
    }

    func hash(into hasher: inout Hasher) {
        hasher.combine(accountID)
        hasher.combine(displayName)
        hasher.combine(role)
        hasher.combine(company)
        hasher.combine(wechatID)
        hasher.combine(phone)
        hasher.combine(bio)
        hasher.combine(styleIndex)
    }
}

extension ChatParticipant {
    var isAIAccount: Bool {
        kind == .assistant
    }
}

struct SidebarItem: Hashable {
    enum Kind {
        case session
        case account
        case tool
    }

    enum CustomTool: Hashable {
        case accessReview
        case moments
        case autoReply
        case hiddenUsers
        case sideEffect
        case bubble3D
        case sendName
        case paymentStatus
        case finderPublish
        case customerProfile
        case contactRelations
        case momentMaterials
        case wechatMiniProgramShare
        case leftSidebarDisplay
        case blinkVoice
        case voiceAssistant
        case selectionAssist
        case backgroundRemoval
        case uiOperationLab
        case openApiEnvironment
        case openApiBusiness
        case openApiFriends
        case syncChatrooms
    }

    let id: UUID
    let kind: Kind
    let title: String
    let symbolName: String
    let tintColor: UIColor
    let participantID: UUID?
    let targetMessageID: UUID
    let isActive: Bool
    let toolMessageType: ChatMessageType?
    let customTool: CustomTool?
    let avatarURL: URL?
    let isOnline: Bool?
    let compositeAvatarTitles: [String]

    init(
        id: UUID,
        kind: Kind,
        title: String,
        symbolName: String,
        tintColor: UIColor,
        participantID: UUID?,
        targetMessageID: UUID,
        isActive: Bool,
        toolMessageType: ChatMessageType? = nil,
        customTool: CustomTool? = nil,
        avatarURL: URL? = nil,
        isOnline: Bool? = nil,
        compositeAvatarTitles: [String] = []
    ) {
        self.id = id
        self.kind = kind
        self.title = title
        self.symbolName = symbolName
        self.tintColor = tintColor
        self.participantID = participantID
        self.targetMessageID = targetMessageID
        self.isActive = isActive
        self.toolMessageType = toolMessageType
        self.customTool = customTool
        self.avatarURL = avatarURL
        self.isOnline = isOnline
        self.compositeAvatarTitles = compositeAvatarTitles
    }

    static func == (lhs: SidebarItem, rhs: SidebarItem) -> Bool {
        lhs.id == rhs.id
    }

    func hash(into hasher: inout Hasher) {
        hasher.combine(id)
    }
}

struct UnansweredMessageMetadata: Hashable, Codable {
    let mentionedAccountIDs: [UUID]
    let assignedAccountIDs: [UUID]
    let threadID: String?
    let itemID: String?
    let requiresReply: Bool
    let handledAt: Date?

    init(
        mentionedAccountIDs: [UUID] = [],
        assignedAccountIDs: [UUID] = [],
        threadID: String? = nil,
        itemID: String? = nil,
        requiresReply: Bool = false,
        handledAt: Date? = nil
    ) {
        self.mentionedAccountIDs = mentionedAccountIDs
        self.assignedAccountIDs = assignedAccountIDs
        self.threadID = threadID
        self.itemID = itemID
        self.requiresReply = requiresReply
        self.handledAt = handledAt
    }

    func targets(accountID: UUID) -> Bool {
        mentionedAccountIDs.contains(accountID)
            || assignedAccountIDs.contains(accountID)
            || requiresReply
    }

    func markingHandled(at date: Date) -> UnansweredMessageMetadata {
        UnansweredMessageMetadata(
            mentionedAccountIDs: mentionedAccountIDs,
            assignedAccountIDs: assignedAccountIDs,
            threadID: threadID,
            itemID: itemID,
            requiresReply: requiresReply,
            handledAt: date
        )
    }
}

struct ChatMessage: Hashable, Codable {
    let id: UUID
    let conversationID: UUID
    let type: ChatMessageType
    let sender: ChatParticipant
    let body: String
    let detail: String
    let isOutgoing: Bool
    let presentation: BubblePresentation
    let timestamp: String
    let sentAt: Date
    /// Backend message server ID used by message-level OpenAPI actions.
    let backendMessageID: String?
    let attachmentURL: URL?
    let isAI: Bool
    let isGroupConversation: Bool
    let richElements: [BubbleRichElement]
    let contactCard: ContactCardProfile?
    let contactCardAccountID: UUID?
    let quotedMessageID: UUID?
    let mergedForwardMessages: [ChatMessage]
    let recipientAccountID: UUID?
    let unansweredMetadata: UnansweredMessageMetadata?

    init(
        id: UUID,
        conversationID: UUID,
        type: ChatMessageType,
        sender: ChatParticipant,
        body: String,
        detail: String,
        isOutgoing: Bool,
        presentation: BubblePresentation,
        timestamp: String,
        sentAt: Date = Date(),
        backendMessageID: String? = nil,
        attachmentURL: URL? = nil,
        isAI: Bool = false,
        isGroupConversation: Bool = false,
        richElements: [BubbleRichElement] = [],
        contactCard: ContactCardProfile? = nil,
        contactCardAccountID: UUID? = nil,
        quotedMessageID: UUID? = nil,
        mergedForwardMessages: [ChatMessage] = [],
        recipientAccountID: UUID? = nil,
        unansweredMetadata: UnansweredMessageMetadata? = nil
    ) {
        self.id = id
        self.conversationID = conversationID
        self.type = type
        self.sender = sender
        self.body = body
        self.detail = detail
        self.isOutgoing = isOutgoing
        self.presentation = presentation
        self.timestamp = timestamp
        self.sentAt = sentAt
        self.backendMessageID = backendMessageID
        self.attachmentURL = attachmentURL
        self.isAI = isAI
        self.isGroupConversation = isGroupConversation
        self.richElements = richElements
        self.contactCard = contactCard
        self.contactCardAccountID = contactCardAccountID
        self.quotedMessageID = quotedMessageID
        self.mergedForwardMessages = mergedForwardMessages
        self.recipientAccountID = recipientAccountID
        self.unansweredMetadata = unansweredMetadata
    }

    func replacingRecipientAccountID(_ accountID: UUID?) -> ChatMessage {
        ChatMessage(
            id: id,
            conversationID: conversationID,
            type: type,
            sender: sender,
            body: body,
            detail: detail,
            isOutgoing: isOutgoing,
            presentation: presentation,
            timestamp: timestamp,
            sentAt: sentAt,
            backendMessageID: backendMessageID,
            attachmentURL: attachmentURL,
            isAI: isAI,
            isGroupConversation: isGroupConversation,
            richElements: richElements,
            contactCard: contactCard,
            contactCardAccountID: contactCardAccountID,
            quotedMessageID: quotedMessageID,
            mergedForwardMessages: mergedForwardMessages,
            recipientAccountID: accountID,
            unansweredMetadata: unansweredMetadata
        )
    }

    func replacingSender(_ sender: ChatParticipant) -> ChatMessage {
        ChatMessage(
            id: id,
            conversationID: conversationID,
            type: type,
            sender: sender,
            body: body,
            detail: detail,
            isOutgoing: isOutgoing,
            presentation: presentation,
            timestamp: timestamp,
            sentAt: sentAt,
            backendMessageID: backendMessageID,
            attachmentURL: attachmentURL,
            isAI: isAI,
            isGroupConversation: isGroupConversation,
            richElements: richElements,
            contactCard: contactCard,
            contactCardAccountID: contactCardAccountID,
            quotedMessageID: quotedMessageID,
            mergedForwardMessages: mergedForwardMessages,
            recipientAccountID: recipientAccountID,
            unansweredMetadata: unansweredMetadata
        )
    }

    func replacingType(_ type: ChatMessageType) -> ChatMessage {
        ChatMessage(
            id: id, conversationID: conversationID, type: type, sender: sender,
            body: body, detail: detail, isOutgoing: isOutgoing,
            presentation: presentation, timestamp: timestamp, sentAt: sentAt,
            backendMessageID: backendMessageID,
            attachmentURL: attachmentURL, isAI: isAI,
            isGroupConversation: isGroupConversation, richElements: richElements,
            contactCard: contactCard, contactCardAccountID: contactCardAccountID,
            quotedMessageID: quotedMessageID, mergedForwardMessages: mergedForwardMessages,
            recipientAccountID: recipientAccountID, unansweredMetadata: unansweredMetadata
        )
    }

    func replacingUnansweredMetadata(
        _ metadata: UnansweredMessageMetadata?
    ) -> ChatMessage {
        ChatMessage(
            id: id,
            conversationID: conversationID,
            type: type,
            sender: sender,
            body: body,
            detail: detail,
            isOutgoing: isOutgoing,
            presentation: presentation,
            timestamp: timestamp,
            sentAt: sentAt,
            backendMessageID: backendMessageID,
            attachmentURL: attachmentURL,
            isAI: isAI,
            isGroupConversation: isGroupConversation,
            richElements: richElements,
            contactCard: contactCard,
            contactCardAccountID: contactCardAccountID,
            quotedMessageID: quotedMessageID,
            mergedForwardMessages: mergedForwardMessages,
            recipientAccountID: recipientAccountID,
            unansweredMetadata: metadata
        )
    }

    static func == (lhs: ChatMessage, rhs: ChatMessage) -> Bool {
        lhs.id == rhs.id
    }

    func hash(into hasher: inout Hasher) {
        hasher.combine(id)
    }
}

enum MessageMutation {
    case insert(ChatMessage)
    case update(ChatMessage)
    case delete(messageID: UUID, affectedConversationID: UUID)

    var messageID: UUID {
        switch self {
        case .insert(let message), .update(let message):
            return message.id
        case .delete(let messageID, _):
            return messageID
        }
    }

    var affectedConversationID: UUID {
        switch self {
        case .insert(let message), .update(let message):
            return message.conversationID
        case .delete(_, let conversationID):
            return conversationID
        }
    }
}

struct MessageMutationPlanner {
    private var fingerprintsByID: [UUID: Int] = [:]
    private var conversationIDByMessageID: [UUID: UUID] = [:]

    mutating func reset(to messages: [ChatMessage]) {
        fingerprintsByID = Dictionary(
            uniqueKeysWithValues: messages.map { ($0.id, $0.persistenceContentHash) }
        )
        conversationIDByMessageID = Dictionary(
            uniqueKeysWithValues: messages.map { ($0.id, $0.conversationID) }
        )
    }

    mutating func plan(for messages: [ChatMessage]) -> [MessageMutation] {
        var mutations: [MessageMutation] = []
        mutations.reserveCapacity(max(1, messages.count / 20))
        var nextFingerprints: [UUID: Int] = [:]
        var nextConversationIDs: [UUID: UUID] = [:]
        nextFingerprints.reserveCapacity(messages.count)
        nextConversationIDs.reserveCapacity(messages.count)

        for message in messages {
            let fingerprint = message.persistenceContentHash
            nextFingerprints[message.id] = fingerprint
            nextConversationIDs[message.id] = message.conversationID
            guard let previousFingerprint = fingerprintsByID[message.id] else {
                mutations.append(.insert(message))
                continue
            }
            if previousFingerprint != fingerprint {
                mutations.append(.update(message))
            }
        }

        for (messageID, conversationID) in conversationIDByMessageID
        where nextFingerprints[messageID] == nil {
            mutations.append(
                .delete(messageID: messageID, affectedConversationID: conversationID)
            )
        }

        fingerprintsByID = nextFingerprints
        conversationIDByMessageID = nextConversationIDs
        return mutations
    }

    mutating func applyKnownMutations(_ mutations: [MessageMutation]) {
        for mutation in mutations {
            switch mutation {
            case .insert(let message), .update(let message):
                fingerprintsByID[message.id] = message.persistenceContentHash
                conversationIDByMessageID[message.id] = message.conversationID
            case .delete(let messageID, _):
                fingerprintsByID.removeValue(forKey: messageID)
                conversationIDByMessageID.removeValue(forKey: messageID)
            }
        }
    }
}

struct OpenApiDeliveryStatusRecord: Hashable, Codable {
    let messageID: UUID
    let conversationID: UUID
    let taskIDs: [String]
    let stateRawValue: String
    let note: String
    let updatedAt: Date
}

extension ChatMessage {
    var persistenceContentHash: Int {
        var hasher = Hasher()
        hasher.combine(layoutContentHash)
        hasher.combine(recipientAccountID)
        hasher.combine(unansweredMetadata)
        return hasher.finalize()
    }

    var layoutContentHash: Int {
        var hasher = Hasher()
        hasher.combine(id)
        hasher.combine(type)
        hasher.combine(sender.id)
        hasher.combine(sender.displayName)
        hasher.combine(sender.avatarURL?.absoluteString)
        hasher.combine(body)
        hasher.combine(detail)
        hasher.combine(isOutgoing)
        hasher.combine(presentation)
        hasher.combine(timestamp)
        hasher.combine(sentAt.timeIntervalSince1970)
        hasher.combine(attachmentURL?.absoluteString)
        hasher.combine(isAI)
        hasher.combine(isGroupConversation)
        hasher.combine(richElements)
        hasher.combine(contactCard)
        hasher.combine(contactCardAccountID)
        hasher.combine(quotedMessageID)
        hasher.combine(mergedForwardMessages.map(\.id))
        hasher.combine(unansweredMetadata)
        return hasher.finalize()
    }

    var displayTimestamp: String {
        Self.displayTimestamp(for: sentAt)
    }

    static func displayTimestamp(for date: Date, now: Date = Date()) -> String {
        let calendar = Calendar.current
        if calendar.isDate(date, inSameDayAs: now) {
            return timeFormatter.string(from: date)
        }
        if calendar.component(.year, from: date) == calendar.component(.year, from: now) {
            return monthDayTimeFormatter.string(from: date)
        }
        return yearMonthDayTimeFormatter.string(from: date)
    }

    private static let timeFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.dateFormat = "HH:mm"
        return formatter
    }()

    private static let monthDayTimeFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.dateFormat = "M月d日 HH:mm"
        return formatter
    }()

    private static let yearMonthDayTimeFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy年M月d日 HH:mm"
        return formatter
    }()
}

enum BubbleRichElement: Hashable, Codable {
    case text(String)
    case blueLink(label: String, url: String, bracketed: Bool)
    case taggedFile(label: String, url: String, prefix: String)
    case mention(String)
    case aiToken(String)
    case image(name: String, url: String, aspect: BubbleImageAspect, access: BubbleAccessScope)
    case inlineCard(kind: BubbleCardKind, title: String, subtitle: String, url: String)
    case location(title: String, address: String, url: String)
    case quote(author: String, text: String)
    case file(name: String, format: BubbleFileFormat, url: String, preview: String, access: BubbleAccessScope)
}

enum BubbleImageAspect: String, Hashable, Codable {
    case horizontal
    case vertical
}

enum BubbleAccessScope: String, Hashable, Codable {
    case `public` = "公开"
    case fans = "仅好友粉丝"
    case friends = "仅好友"
    case recipients = "仅收件者"
}

enum ResourceExpiryPolicy: String, CaseIterable, Hashable, Codable {
    case never = "永久有效"
    case oneDay = "24小时"
    case sevenDays = "7天"
    case thirtyDays = "30天"

    var timeInterval: TimeInterval? {
        switch self {
        case .never: return nil
        case .oneDay: return 24 * 60 * 60
        case .sevenDays: return 7 * 24 * 60 * 60
        case .thirtyDays: return 30 * 24 * 60 * 60
        }
    }
}

struct ResourceAccessPolicy: Hashable, Codable {
    var scope: BubbleAccessScope
    var shareURL: String
    var expiresAt: Date?
    var requiresReview: Bool
    var reviewStatus: String

    var expiryText: String {
        guard let expiresAt else { return ResourceExpiryPolicy.never.rawValue }
        return ChatMessage.displayTimestamp(for: expiresAt)
    }

    static func make(scope: BubbleAccessScope, shareURL: String, expiry: ResourceExpiryPolicy = .never) -> ResourceAccessPolicy {
        ResourceAccessPolicy(
            scope: scope,
            shareURL: shareURL,
            expiresAt: expiry.timeInterval.map { Date().addingTimeInterval($0) },
            requiresReview: scope != .public,
            reviewStatus: scope == .public ? "可访问" : "待申请"
        )
    }
}

enum BubbleCardKind: String, Hashable, Codable {
    case enterprise = "企微名片"
    case personal = "个人名片"
    case officialAccount = "公众号名片"
    case miniProgramProfile = "小程序名片"
    case channelsProfile = "视频号名片"
    case miniProgramLink = "小程序链接"
}

enum BubbleFileFormat: String, Hashable, Codable {
    case txt = "TXT"
    case md = "MD"
    case word = "DOC"
    case excel = "XLS"
    case powerpoint = "PPT"
    case pdf = "PDF"
    case csv = "CSV"
    case archive = "ZIP"
    case other = "FILE"
}

struct MessageBubbleSchema: Codable, Hashable {
    var version: Int
    var id: UUID
    var conversationID: UUID
    var senderID: UUID
    var senderName: String
    var type: ChatMessageType
    var direction: Direction
    var body: String
    var detail: String
    var sentAt: Date
    var displayTimestamp: String
    var presentation: BubblePresentation
    var isAI: Bool
    var isGroupConversation: Bool
    var accessPolicy: AccessPolicy
    var elements: [Element]
    var links: [LinkRule]
    var audit: Audit

    enum Direction: String, Codable, Hashable {
        case incoming
        case outgoing
        case system
    }

    struct AccessPolicy: Codable, Hashable {
        var defaultScope: BubbleAccessScope
        var requiresReviewForLinks: Bool
        var shareURL: String?
        var expiresAt: Date?
        var reviewQueueStatus: String
        var screenshotReadableLinkRule: String
        var protectedAssetRule: String

        static let defaultPolicy = AccessPolicy(
            defaultScope: .public,
            requiresReviewForLinks: false,
            shareURL: nil,
            expiresAt: nil,
            reviewQueueStatus: "无需审核",
            screenshotReadableLinkRule: "链接必须以完整 URL 存在，截图中可直接识别和访问。",
            protectedAssetRule: "图片和视频直接可看；文件和链接统一使用资源权限模型，非公开资源进入申请审核队列。"
        )
    }

    struct LinkRule: Codable, Hashable {
        var title: String
        var url: String
        var scope: BubbleAccessScope
        var requiresReview: Bool
        var status: String
    }

    struct Audit: Codable, Hashable {
        var source: String
        var createdBy: String
        var createdAt: Date
        var updatedAt: Date
        var revokedAt: Date?
        var serviceTraceID: String?

        static func local(createdAt: Date) -> Audit {
            Audit(
                source: "local-ios-sqlite",
                createdBy: "ios-float",
                createdAt: createdAt,
                updatedAt: createdAt,
                revokedAt: nil,
                serviceTraceID: nil
            )
        }
    }

    enum Element: Codable, Hashable {
        case text(String)
        case link(label: String, url: String, bracketed: Bool, scope: BubbleAccessScope, requiresReview: Bool)
        case fileLink(label: String, url: String, prefix: String, format: BubbleFileFormat, scope: BubbleAccessScope, preview: String)
        case mention(userID: UUID?, name: String)
        case aiToken(String)
        case image(name: String, url: String, aspect: BubbleImageAspect, scope: BubbleAccessScope)
        case video(name: String, url: String, aspect: BubbleImageAspect, scope: BubbleAccessScope)
        case card(kind: BubbleCardKind, title: String, subtitle: String, url: String)
        case location(title: String, address: String, url: String)
        case quote(author: String, text: String, originalMessageID: UUID?)
        case file(name: String, format: BubbleFileFormat, url: String, preview: String, scope: BubbleAccessScope)
    }

    static func from(message: ChatMessage, requiresReviewForLinks: Bool = false) -> MessageBubbleSchema {
        let elements = message.richElements.map { Element.from(richElement: $0, requiresReviewForLinks: requiresReviewForLinks) }
        let links = elements.compactMap { element -> LinkRule? in
            switch element {
            case .link(let label, let url, _, let scope, let requiresReview):
                return LinkRule(title: label, url: url, scope: scope, requiresReview: requiresReview, status: requiresReview ? "待申请" : "可访问")
            case .fileLink(let label, let url, _, _, let scope, _):
                return LinkRule(title: label, url: url, scope: scope, requiresReview: scope != .public, status: scope == .public ? "可访问" : "待申请")
            case .card(_, let title, _, let url):
                return LinkRule(title: title, url: url, scope: .public, requiresReview: requiresReviewForLinks, status: requiresReviewForLinks ? "待申请" : "可访问")
            case .location(let title, _, let url):
                return LinkRule(title: title, url: url, scope: .public, requiresReview: false, status: "可访问")
            case .file(let name, _, let url, _, let scope):
                return LinkRule(title: name, url: url, scope: scope, requiresReview: scope != .public, status: scope == .public ? "可访问" : "待申请")
            case .image(let name, let url, _, let scope), .video(let name, let url, _, let scope):
                return LinkRule(title: name, url: url, scope: scope, requiresReview: scope != .public, status: scope == .public ? "可访问" : "待申请")
            default:
                return nil
            }
        }
        return MessageBubbleSchema(
            version: 1,
            id: message.id,
            conversationID: message.conversationID,
            senderID: message.sender.id,
            senderName: message.sender.displayName,
            type: message.type,
            direction: message.type == .system ? .system : (message.isOutgoing ? .outgoing : .incoming),
            body: message.body,
            detail: message.detail,
            sentAt: message.sentAt,
            displayTimestamp: message.displayTimestamp,
            presentation: message.presentation,
            isAI: message.isAI,
            isGroupConversation: message.isGroupConversation,
            accessPolicy: AccessPolicy(
                defaultScope: .public,
                requiresReviewForLinks: requiresReviewForLinks,
                shareURL: links.first?.url,
                expiresAt: nil,
                reviewQueueStatus: links.contains(where: \.requiresReview) ? "待申请" : "无需审核",
                screenshotReadableLinkRule: "文本、链接、文件索引使用可复制 URL；截图中仍保留 URL。",
                protectedAssetRule: "图片和视频不再加访问限制；文件和链接只保存一份资源索引，按 scope、分享链接和过期策略走申请审核。"
            ),
            elements: elements,
            links: links,
            audit: .local(createdAt: message.sentAt)
        )
    }
}

extension MessageBubbleSchema.Element {
    static func from(richElement: BubbleRichElement, requiresReviewForLinks: Bool) -> MessageBubbleSchema.Element {
        switch richElement {
        case .text(let text):
            return .text(text)
        case .blueLink(let label, let url, let bracketed):
            return .link(label: label, url: url, bracketed: bracketed, scope: .public, requiresReview: requiresReviewForLinks)
        case .taggedFile(let label, let url, let prefix):
            return .fileLink(label: label, url: url, prefix: prefix, format: BubbleFileFormat.infer(from: label), scope: .public, preview: "文件链接")
        case .mention(let name):
            return .mention(userID: nil, name: name)
        case .aiToken(let text):
            return .aiToken(text)
        case .image(let name, let url, let aspect, let access):
            return .image(name: name, url: url, aspect: aspect, scope: access)
        case .inlineCard(let kind, let title, let subtitle, let url):
            return .card(kind: kind, title: title, subtitle: subtitle, url: url)
        case .location(let title, let address, let url):
            return .location(title: title, address: address, url: url)
        case .quote(let author, let text):
            return .quote(author: author, text: text, originalMessageID: nil)
        case .file(let name, let format, let url, let preview, let access):
            return .file(name: name, format: format, url: url, preview: preview, scope: access)
        }
    }
}

extension BubbleFileFormat {
    static func infer(from fileName: String) -> BubbleFileFormat {
        let ext = (fileName as NSString).pathExtension.lowercased()
        switch ext {
        case "txt": return .txt
        case "md": return .md
        case "doc", "docx": return .word
        case "xls", "xlsx": return .excel
        case "ppt", "pptx": return .powerpoint
        case "pdf": return .pdf
        case "csv": return .csv
        case "zip", "rar", "7z": return .archive
        default: return .other
        }
    }
}

struct ChatDemoState {
    var participants: [ChatParticipant]
    var messages: [ChatMessage]
    let currentUserID: UUID
    var selectedAccountID: UUID
    var contactCards: [UUID: ContactCardProfile]
    var selectedFriendID: UUID?
    let rightToolItems: [SidebarItem]

    var currentUser: ChatParticipant {
        participants.first { $0.id == selectedAccountID }
            ?? participants.first { $0.id == currentUserID }
            ?? participants[0]
    }

    var currentUsers: [ChatParticipant] {
        participants.filter(\.isCurrentUser)
    }

    var friends: [ChatParticipant] {
        participants.filter { !$0.isCurrentUser }
    }

    var activeFriend: ChatParticipant? {
        guard let selectedFriendID else { return nil }
        return participants.first { $0.id == selectedFriendID }
    }

    var visibleMessages: [ChatMessage] {
        guard let selectedFriendID else { return messages.filter(canShowForSelectedAccount) }
        let conversationIDs = Set(matchingConversationIDs(for: selectedFriendID))
        return messages.filter { conversationIDs.contains($0.conversationID) && canShowForSelectedAccount($0) }
    }

    var leftItems: [SidebarItem] {
        var latestByConversationID: [UUID: ChatMessage] = [:]
        for message in messages where canShowForSelectedAccount(message) {
            func record(_ conversationID: UUID) {
                guard let current = latestByConversationID[conversationID] else {
                    latestByConversationID[conversationID] = message
                    return
                }
                if current.sentAt < message.sentAt
                    || (current.sentAt == message.sentAt && current.id.uuidString < message.id.uuidString) {
                    latestByConversationID[conversationID] = message
                }
            }
            record(message.conversationID)
            if !message.isGroupConversation {
                record(message.sender.id)
            }
        }

        var seenParticipantIDs = Set<UUID>()
        return friends.compactMap { friend in
            // A display name is editable and non-unique. Sidebar identity must
            // stay bound to the conversation participant UUID.
            guard seenParticipantIDs.insert(friend.id).inserted else { return nil }
            let targetMessage = latestByConversationID[friend.id]
            guard let targetMessage else { return nil }
            return SidebarItem(
                id: friend.id,
                kind: .session,
                title: friend.displayName,
                symbolName: friend.displayName.count > 2 ? "person.2.fill" : "person.fill",
                tintColor: friend.tintColor,
                participantID: friend.id,
                targetMessageID: targetMessage.id,
                isActive: selectedFriendID == friend.id,
                avatarURL: friend.avatarURL
            )
        }
    }

    var rightAccountItems: [SidebarItem] {
        let accounts = currentUsers.isEmpty ? [currentUser] : currentUsers
        return accounts.map { account in
            let targetMessage = visibleMessages.last { $0.sender.id == account.id }
                ?? visibleMessages.last
                ?? messages.last
            return SidebarItem(
                id: account.id,
                kind: .account,
                title: contactCards[account.id]?.displayName ?? account.displayName,
                symbolName: "person.crop.circle.fill",
                tintColor: account.tintColor,
                participantID: account.id,
                targetMessageID: targetMessage?.id ?? account.id,
                isActive: account.id == selectedAccountID,
                avatarURL: account.avatarURL
            )
        }
    }

    func indexOfMessage(id: UUID) -> Int? {
        visibleMessages.firstIndex { $0.id == id }
    }

    func firstVisibleMessage(for participantID: UUID) -> ChatMessage? {
        let conversationIDs = Set(matchingConversationIDs(for: participantID))
        return visibleMessages.first {
            conversationIDs.contains($0.conversationID) && ($0.sender.id == participantID || selectedFriendID == nil)
        }
    }

    func latestMessage(for participantID: UUID) -> ChatMessage? {
        let conversationIDs = Set(matchingConversationIDs(for: participantID))
        return messages
            .filter { message in
                guard canShowForSelectedAccount(message) else { return false }
                return conversationIDs.contains(message.conversationID)
                    || (!message.isGroupConversation && message.sender.id == participantID)
            }
            .max {
                if $0.sentAt == $1.sentAt {
                    return $0.id.uuidString < $1.id.uuidString
                }
                return $0.sentAt < $1.sentAt
            }
    }

    private func matchingConversationIDs(for participantID: UUID) -> [UUID] {
        [participantID]
    }

    private func canShowForSelectedAccount(_ message: ChatMessage) -> Bool {
        guard message.detail.hasPrefix("AI自动回复预测") else { return true }
        guard let receiverID = message.recipientAccountID ?? autoReplyReceiverAccountID(in: message.detail) else { return true }
        return receiverID == selectedAccountID
    }

    private func autoReplyReceiverAccountID(in detail: String) -> UUID? {
        guard detail.hasPrefix("AI自动回复预测") else { return nil }
        return detail
            .components(separatedBy: .newlines)
            .first { $0.hasPrefix("接收帐号ID：") }
            .map { String($0.dropFirst("接收帐号ID：".count)).trimmingCharacters(in: .whitespacesAndNewlines) }
            .flatMap(UUID.init(uuidString:))
    }
}
