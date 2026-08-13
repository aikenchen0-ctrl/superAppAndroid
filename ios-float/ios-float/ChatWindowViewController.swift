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
import WebKit

final class ChatWindowViewController: UIViewController {
    struct PendingStreamingMessageUpdate {
        let body: String
        let rebuildsRichElements: Bool
    }

    static let quickPhraseStorageKey = "ChatWindowQuickPhrases"
    static let defaultWebLinkStorageKey = "ChatWindowDefaultWebLink"
    static let linkRequiresApprovalStorageKey = "ChatWindowLinkRequiresApproval"
    static let restrictedSentLinksStorageKey = "ChatWindowRestrictedSentLinks"
    static let accessRequestsStorageKey = "ChatWindowAccessRequests"
    static let codexBaseURLStorageKey = "ChatWindowCodexBaseURL"
    static let codexAPIKeyStorageKey = "ChatWindowCodexAPIKey"
    static let codexModelStorageKey = "ChatWindowCodexModel"
    static let claudeBaseURLStorageKey = "ChatWindowClaudeBaseURL"
    static let claudeAPIKeyStorageKey = "ChatWindowClaudeAPIKey"
    static let claudeModelStorageKey = "ChatWindowClaudeModel"
    static let autoReplyEnabledStorageKey = "ChatWindowAutoReplyEnabled"
    static let autoReplyBaseURLStorageKey = "ChatWindowAutoReplyBaseURL"
    static let autoReplyAPIKeyStorageKey = "ChatWindowAutoReplyAPIKey"
    static let autoReplyModelStorageKey = "ChatWindowAutoReplyModel"
    static let appLanguageStorageKey = "ChatWindowAppLanguage"
    static let userFavoriteItemsStorageKey = "ChatWindowUserFavoriteItems"
    static let sideEffectTemplateStorageKey = "ChatWindowSideEffectTemplate"
    static let sideEffectFillColorStorageKey = "ChatWindowSideEffectFillColor"
    static let sideEffectStrokeColorStorageKey = "ChatWindowSideEffectStrokeColor"
    static let bubble3DAppearanceStorageKey = "ChatWindowBubble3DAppearance"
    static let automaticBackgroundRemovalStorageKey = "ChatWindowAutomaticBackgroundRemoval"
    static let leftSidebarDisplayModeStorageKey = "ChatWindowLeftSidebarDisplayMode"
    static let openApiIMSnapshotStorageKey = "ChatWindowOpenApiIMSnapshot"
    static let openApiIMSnapshotUpdatedAtStorageKey = "ChatWindowOpenApiIMSnapshotUpdatedAt"
    static let openApiIMSnapshotSchemaVersionStorageKey = "ChatWindowOpenApiIMSnapshotSchemaVersion"
    static let openApiIMSnapshotSchemaVersion = "2-canonical-conversation-relations"
    static let openApiChatSyncCheckpointStorageKey = "ChatWindowOpenApiChatSyncCheckpoint"
    static let openApiChatMappingVersionStorageKey = "ChatWindowOpenApiChatMappingVersion"
    static let openApiChatMappingVersion = "3-media-url-restoration"
    static let openApiGroupMemberDiagnosticsStorageKey = "ChatWindowOpenApiGroupMemberDiagnostics"
    static let groupDisplaySettingsStorageKey = "ChatWindowGroupDisplaySettings"
    static let openApiIMSnapshotRefreshInterval: TimeInterval = 15 * 60
    static let openApiChatPollingInterval: TimeInterval = 8
    static let fallbackDefaultWebLinkText = "这是自己的官网 https://cc2.cx"
    static let defaultCodexBaseURLText = "https://cc2.cx/v1"
    static let defaultCodexAPIKey = ""
    static let defaultCodexModel = "gpt-5.5"
    static let defaultClaudeBaseURLText = "https://api.anthropic.com/v1"
    static let defaultClaudeAPIKey = ""
    static let defaultClaudeModel = "claude-sonnet-4-20250514"
static let recallWindow: TimeInterval = 2 * 60

    enum WindowState {
        case expanded
        case compact
        case minimized
    }

    enum AIProvider: Int, CaseIterable {
        case claude
        case codex

        var title: String {
            switch self {
            case .claude: return "Claude AI"
            case .codex: return "Codex"
            }
        }
    }

    enum EdgeLaunchTarget {
        case douyin
        case wechat
        case codex

        var title: String {
            switch self {
            case .douyin: return "抖音"
            case .wechat: return "微信"
            case .codex: return "Codex"
            }
        }

        var color: UIColor {
            switch self {
            case .douyin:
                return UIColor(red: 0.12, green: 0.13, blue: 0.15, alpha: 0.92)
            case .wechat:
                return UIColor(red: 0.20, green: 0.73, blue: 0.32, alpha: 0.90)
            case .codex:
                return UIColor(red: 0.34, green: 0.43, blue: 0.95, alpha: 0.92)
            }
        }
    }

    struct ExternalLaunchCandidate {
        let appName: String
        let url: URL
        let requiresInstalledApp: Bool
        let pasteboardText: String?

        init(appName: String, url: URL, requiresInstalledApp: Bool, pasteboardText: String? = nil) {
            self.appName = appName
            self.url = url
            self.requiresInstalledApp = requiresInstalledApp
            self.pasteboardText = pasteboardText
        }
    }

    struct SideEffectTemplate: Hashable {
        let index: Int
        let title: String
        let subtitle: String
        let symbolName: String
    }

    enum ForwardMode {
        case individual
        case merged
    }

    struct CodexConfiguration {
        var baseURLText: String
        var apiKey: String
        var model: String

        var baseURL: URL? {
            URL(string: baseURLText.trimmingCharacters(in: .whitespacesAndNewlines))
        }
    }

    struct AutoReplyConfiguration {
        var isEnabled: Bool
        var baseURLText: String
        var apiKey: String
        var model: String

        var baseURL: URL? {
            URL(string: baseURLText.trimmingCharacters(in: .whitespacesAndNewlines))
        }
    }

    struct AutoReplyTestCase: Hashable {
        let id = UUID()
        let title: String
        let body: String
        let prefersGroup: Bool
    }

    struct MessageHeightCacheKey: Hashable {
        let messageID: UUID
        let messageHash: Int
        let width: Int
        let showsGroupAvatar: Bool
        let showsGroupName: Bool
        let usesUnansweredPresentation: Bool
        let deliveryStatusText: String?
    }

    struct MessageHeightPrewarmInput {
        let key: MessageHeightCacheKey
        let message: ChatMessage
        let width: CGFloat
        let showsGroupAvatar: Bool
        let showsGroupName: Bool
        let usesUnansweredPresentation: Bool
        let deliveryStatusText: String?
    }

    final class MessageMediaPrefetchGroup {
        var requests: [MediaThumbnailRequest] = []
        var remainingRequestCount: Int

        init(remainingRequestCount: Int) {
            self.remainingRequestCount = remainingRequestCount
        }
    }

    static let groupInfoToolTitle = "群信息"
    static let accessReviewToolTitle = "申请审核"
    static let momentsToolTitle = "朋友圈"
    static let autoReplyToolTitle = "AI自动回复"
    static let hiddenUsersToolTitle = "隐藏用户"
    static let sideEffectToolTitle = "侧边特效"
    static let bubble3DToolTitle = "3D气泡"
    static let sendNameToolTitle = "携带名字"
    static let paymentStatusToolTitle = "支付状态"
    static let finderPublishToolTitle = "视频号发布"
    static let customerProfileToolTitle = "客户档案"
    static let contactRelationsToolTitle = "通讯录"
    static let momentMaterialsToolTitle = "素材库"
    static let wechatMiniProgramToolTitle = "微信小程序"
    static let leftSidebarDisplayToolTitle = "左侧显示"
    static let blinkVoiceToolTitle = "眨眼测试"
    static let voiceAssistantToolTitle = "语音助手"
    static let selectionAssistToolTitle = "摇杆套索"
    static let backgroundRemovalToolTitle = "智能扣图"
    static let uiOperationLabToolTitle = "UI组件"
    static let openApiEnvironmentToolTitle = "OpenAPI"
    static let openApiBusinessToolTitle = "帐号设备"
    static let openApiFriendsToolTitle = "好友管理"
    static let syncChatroomsToolTitle = "同步群聊"
    static let jdMiniProgramShareLink = "#小程序://京东购物丨点外卖领国补/cI6ikHg6SbgDYYi"
    static let jdMiniProgramTitle = "京东购物丨点外卖领国补"
    static let jdMiniProgramSourceName = "京东购物"
    static let messageSeedRevisionKey = "message_seed_revision"
    static let messageSeedRevision = "2026-07-05-no-preloaded-media-or-notices-v2"
    static let sidebarRailWidth: CGFloat = 51
    static let sidebarItemSize = CGSize(
        width: UnreadTimelineLayoutMetrics.avatarSideLength,
        height: UnreadTimelineLayoutMetrics.avatarSideLength
    )
    static let rightAccountItemSize = CGSize(width: 50, height: 44)

    var state = ChatDataFactory.makeLiveLoadingState()
    let conversationStateStore = ConversationStateStore()
    let chromeView = FloatingChromeView()
    let headerView = UIView()
    let islandButton = UIButton(type: .system)
    let headerNameButton = UIButton(type: .system)
    let headerConversationEditButton = UIButton(type: .system)
    let headerScanAddButton = UIButton(type: .system)
    let headerUnreadBadgeView = UIView()
    let accountButton = UIButton(type: .system)
    let globalSearchButton = UIButton(type: .system)
    let titleLabel = UILabel()
    let titleSubtitleLabel = UILabel()
    let headerTitleStack = UIStackView()
    let leftCollectionView: UICollectionView
    let messageCollectionView: UICollectionView
    let rightAccountCollectionView: UICollectionView
    let rightToolCollectionView: UICollectionView
    let rightRailContainer = UIView()
    let aiffButton = UIButton(type: .system)
    let inputBarBottomFillView = UIView()
    let quotePreviewBar = UIView()
    let quoteAccentView = UIView()
    let quoteThumbnailView = UIImageView()
    let quotePreviewLabel = UILabel()
    let quoteCloseButton = UIButton(type: .system)
    let multiSelectBar = UIView()
    let multiForwardButton = UIButton(type: .system)
    let multiMergeButton = UIButton(type: .system)
    let multiDeleteButton = UIButton(type: .system)
    let multiCancelButton = UIButton(type: .system)
    let mentionPanelView = UIView()
    let mentionScrollView = UIScrollView()
    let mentionStackView = UIStackView()
    let inputBar = InputBarView()
    let voiceRecordingPromptView = UIView()
    let voiceRecordingPromptIconView = UIImageView(image: UIImage(systemName: "mic.fill"))
    let voiceRecordingPromptLabel = UILabel()
    let emojiPanelView = UIView()
    let emojiScrollView = UIScrollView()
    let emojiGridView = UIStackView()
    let connectionOverlay = ConnectionOverlayView()
    let conversationGestureHintView = UIView()
    let localIslandIconView = UIImageView(image: UIImage(named: "IslandAppIcon"))
    let rightEdgeDouyinFeedbackView = UIView()
    let rightEdgeDouyinBlobLayer = CAShapeLayer()
    let rightEdgeDouyinRimLayer = CAShapeLayer()
    let rightEdgeDouyinHighlightLayer = CAShapeLayer()
    let rightEdgeDouyinTrailLayer = CAShapeLayer()
    let rightEdgeDouyinArrowView = UIImageView(image: UIImage(systemName: "chevron.left"))
    let rightEdgeDouyinHintLabel = UILabel()
    let leftEdgeFeedbackView = UIView()
    let leftEdgeBlobLayer = CAShapeLayer()
    let leftEdgeRimLayer = CAShapeLayer()
    let leftEdgeHighlightLayer = CAShapeLayer()
    let leftEdgeTrailLayer = CAShapeLayer()
    let leftEdgeArrowView = UIImageView(image: UIImage(systemName: "chevron.right"))
    let leftEdgeHintLabel = UILabel()
    let leftScrollPreviewView = UIView()
    let leftFriendTotalBadgeView = UIVisualEffectView(effect: UIBlurEffect(style: .systemChromeMaterial))
    let leftFriendTotalBadgeLabel = UILabel()
    let selectedLeftSidebarFloatingView = SelectedLeftSidebarFloatingView()
    var selectedLeftSidebarFloatingTopConstraint: NSLayoutConstraint?
    var leftScrollPreviewCardsByID: [UUID: UIView] = [:]
    var leftScrollPreviewPanGesture: UIPanGestureRecognizer?
    var sidebarAvatarPrefetchTasks: [URL: MediaThumbnailRequest] = [:]
    var messageMediaPrefetchGroups: [UUID: MessageMediaPrefetchGroup] = [:]
    var messageMediaPrefetchIDsByIndexPath: [IndexPath: UUID] = [:]
    var selectionAssistOverlayView: UIVisualEffectView?
    var selectionAssistCursorView: UIView?
    var selectionLassoOverlayView: SelectionLassoOverlayView?
    static let leftPreviewNameLabelTag = 8201
    static let leftPreviewMessageLabelTag = 8202
    static let leftPreviewTimeLabelTag = 8203
    static let leftPreviewFriendAvatarTag = 8204
    static let leftPreviewAccountAvatarTag = 8205

    var windowState: WindowState = .expanded
    var frameConstraints: [NSLayoutConstraint] = []
    var widthConstraint: NSLayoutConstraint?
    var heightConstraint: NSLayoutConstraint?
    var centerXConstraint: NSLayoutConstraint?
    var topConstraint: NSLayoutConstraint?
    var bottomConstraint: NSLayoutConstraint?
    var accountRailHeightConstraint: NSLayoutConstraint?
    var contentBottomConstraint: NSLayoutConstraint?
    var inputBarBottomConstraint: NSLayoutConstraint?
    var emojiPanelHeightConstraint: NSLayoutConstraint?
    var quotePreviewHeightConstraint: NSLayoutConstraint?
    var mentionPanelHeightConstraint: NSLayoutConstraint?
    var lastDragCenter = CGPoint.zero
    var lastSize = CGSize.zero
    var rightAccountHeightRatio: CGFloat = 0.48
    var rightAccountHeightRatioAtScrollStart: CGFloat = 0.48
    var rightRailContentOffsetAtScrollStart: CGFloat = 0
    var lastSidebarLayoutWidths: [ObjectIdentifier: CGFloat] = [:]
    var customRightToolOrderKeys: [String] = []
    let minimumRightAccountItemsVisible: CGFloat = 3
    let minimumRightToolItemsVisible: CGFloat = 3
    var didMoveToIslandForBackground = false
    var isMessagePersistenceReady = false
    var messageMutationPlanner = MessageMutationPlanner()
    var audioRecorder: AVAudioRecorder?
    var audioPlayer: AVAudioPlayer?
    var playingVoiceMessageID: UUID?
    var audioRecordingURL: URL?
    var audioRecordingStartedAt: Date?
    var isVoicePressActive = false
    var previewURL: URL?
    var quotedMessage: ChatMessage?
    var pendingAttachments: [PendingAttachment] = []
    var isMultiSelecting = false
    var selectedMessageIDs = Set<UUID>()
    var isMentionPickerPresented = false
    var quickPhrases: [String] = []
    var userFavoriteItemsByAccountID: [UUID: [FavoriteShareItem]] = [:]
    var defaultWebLinkText = ChatWindowViewController.fallbackDefaultWebLinkText
    var linkRequiresApproval = false
    var restrictedSentLinkURLs = Set<String>()
    var selectedAIProvider: AIProvider = .codex
    var codexModels: [String] = []
    var claudeModels: [String] = []
    var codexConfiguration = CodexConfiguration(
        baseURLText: ChatWindowViewController.defaultCodexBaseURLText,
        apiKey: ChatWindowViewController.defaultCodexAPIKey,
        model: ChatWindowViewController.defaultCodexModel
    )
    var claudeConfiguration = CodexConfiguration(
        baseURLText: ChatWindowViewController.defaultClaudeBaseURLText,
        apiKey: ChatWindowViewController.defaultClaudeAPIKey,
        model: ChatWindowViewController.defaultClaudeModel
    )
    var autoReplyModels: [String] = []
    var autoReplyConfiguration = AutoReplyConfiguration(
        isEnabled: false,
        baseURLText: ChatWindowViewController.defaultCodexBaseURLText,
        apiKey: ChatWindowViewController.defaultCodexAPIKey,
        model: ChatWindowViewController.defaultCodexModel
    )
    var appLanguage: AppLanguage = .zh
    var sideEffectTemplateIndex = 0
    var sideEffectFillColor = UIColor.white.withAlphaComponent(0.88)
    var sideEffectStrokeColor = UIColor(red: 0.32, green: 0.49, blue: 0.98, alpha: 0.48)
    var bubble3DAppearanceEnabled = false
    var automaticBackgroundRemovalEnabled = false
    var leftSidebarDisplayMode: LeftSidebarDisplayMode = .friendsAndGroups
    var automaticBackgroundRemovalOverlay: UIView?
    var removedGroupMemberIDsByGroupID: [UUID: Set<UUID>] = [:]
    var groupDisplaySettingsByGroupID: [UUID: GroupDisplaySettings] = [:]
    var explicitGroupMemberIDsByGroupID: [UUID: [UUID]] = [:]
    var unansweredAccountColorIndexByID: [UUID: Int] = [:]
    var hasLoadedUnansweredAccountColorAssignments = false
    var participantRemarksByID: [UUID: String] = [:]
    var hiddenParticipantIDs = Set<UUID>()
    var sendNameEnabledByAccountID: [UUID: Bool] = [:]
    var openApiAccountContextsByID: [UUID: OpenApiWeChatAccountContext] = [:]
    var openApiConversationContextsByID: [UUID: OpenApiConversationContext] = [:]
    var openApiConversationContextsByAccountID: [UUID: [UUID: OpenApiConversationContext]] = [:]
    var openApiRelatedAccountIDsByConversationID: [UUID: Set<UUID>] = [:]
    var openApiFriendRequestContexts: [OpenApiFriendRequestContext] = []
    var openApiDeliveryStatusesByMessageID: [UUID: OpenApiDeliveryStatusRecord] = [:]
    var openApiIMBootstrapState: OpenApiIMBootstrapState = .loading
    var isOpenApiIMSyncing = false
    var hasLoadedOpenApiIMSnapshot = false
    var openApiIMSnapshotCacheDate: Date?
    var openApiChatSyncCheckpoint = OpenApiChatSyncCheckpoint()
    var openApiChatSyncTask: Task<Void, Never>?
    var openApiChatSyncStartWorkItem: DispatchWorkItem?
    var openApiChatPollTimer: Timer?
    var isOpenApiChatSyncing = false
    var hasCompletedOpenApiChatBootstrap = false
    var openApiHistorySyncKeys = Set<String>()
    var openApiHistoryNextCursorByKey: [String: String] = [:]
    var openApiHistoryExhaustedKeys = Set<String>()
    var openApiHistoryPaginationTriggerConversationID: UUID?
    var openApiHydratedGroupMemberKeys = Set<String>()
    var openApiGroupMemberHydrationInFlightKeys = Set<String>()
    var lastAppliedOpenApiIMSnapshot: OpenApiIMSnapshot?
    var mediaAccessRequests: [MediaAccessRequest] = []
    var lastMessageTapLocation: CGPoint?
    var streamingAIMessageIDs = Set<UUID>()
    var pendingStreamingMessageUpdates: [UUID: PendingStreamingMessageUpdate] = [:]
    var streamingUIFlushWorkItem: DispatchWorkItem?
    var lastStreamingPersistenceDate = Date.distantPast
    let draftSpeechSynthesizer = AVSpeechSynthesizer()
    var autoReplyProcessedMessageIDs = Set<UUID>()
    var autoReplyActiveConversationIDs = Set<UUID>()
    var autoReplySendingMessageIDs = Set<UUID>()
    var unansweredReplyOriginsByOutgoingMessageID: [UUID: UnansweredNavigationOrigin] = [:]
    var unansweredCompletionSnapshotsByOutgoingMessageID: [UUID: UnansweredCompletionAnimationSnapshot] = [:]
    var autoReplyTimer: Timer?
    let blinkClosedAutoReplyTrigger = BlinkClosedGestureTrigger()
    let inputLongEyeRewriteTrigger = BlinkLongEyeInputTrigger()
    var isInputLongEyeRewriteStreaming = false
    var suppressMessageTapUntil = Date.distantPast
    let calendarEventStore = EKEventStore()
    var screenshotMediaBlurEnabled = false
    var isScreenshotBlurPromptVisible = false
    var frameCoordinatorDisplayLink: CADisplayLink?
    var connectionUpdateNeeded = false
    var lastConnectionUpdateTimestamp: CFTimeInterval = 0
    var deferredConnectionLayoutPassCount = 0
    let scrollingConnectionUpdateInterval: CFTimeInterval = 1.0 / 30.0
    var didScrollInitialMessagesToBottom = false
    var hasCompletedInitialNavigationLayoutRefresh = false
    var lastNavigationLayoutRefreshSize = CGSize.zero
    var hasPresentedInitialChatWindow = false
    let homeInitialMessageLimit = 40
    let homeMessagePageSize = 40
    var homeRenderedMessageLimit = 40
    var isHomePaginationInProgress = false
    var isHomePaginationScheduled = false
    var unreadPreviewMessages: [ChatMessage] = []
    var chatWindowRoute: ChatWindowRoute = .unanswered(scope: .all, focus: nil)
    var unreadDirectVisibleLimitByConversationID: [PendingReplyConversationAccountKey: Int] = [:]
    var expandedUnreadDirectKeys: Set<PendingReplyConversationAccountKey> = []
    var unreadGroupVisibleLimitByConversationID: [UUID: Int] = [:]
    var expandedUnreadConversationIDs: Set<UUID> = []
    var unreadFoldExpansionGeneration = 0
    var isUnreadFoldExpansionInProgress = false
    var unreadFoldLockedContentOffsetY: CGFloat?
    var unreadFoldPostExpansionAnchor: UnreadTimelineGroupViewportAnchor?
    var needsUnreadTimelineRefreshAfterExpansion = false
    var deferredOpenApiIMSnapshotAfterUnreadExpansion: OpenApiIMSnapshot?
    var shouldPersistDeferredOpenApiIMSnapshot = true
    var pendingReplyPresentationCacheKey = ""
    var pendingReplyPresentationCache: PendingReplyPresentation?
    var headerCompactResetWorkItem: DispatchWorkItem?
    var isMessageHeaderCompact = false
    let unansweredEmptyStateLabel: UILabel = {
        let label = UILabel()
        label.numberOfLines = 0
        label.textAlignment = .center
        label.font = .preferredFont(forTextStyle: .subheadline)
        label.adjustsFontForContentSizeCategory = true
        label.textColor = UIColor.white.withAlphaComponent(0.78)
        label.isAccessibilityElement = true
        label.isHidden = true
        return label
    }()
    var visibleLeftItemsCacheKey = ""
    var visibleLeftItemsCache: [SidebarItem] = []
    var baseVisibleLeftItemsCacheKey = ""
    var baseVisibleLeftItemsCache: [SidebarItem] = []
    var visibleLeftIndexCacheKey = ""
    var visibleLeftIndexByParticipantID: [UUID: Int] = [:]
    var visibleRightAccountItemsCacheKey = ""
    var visibleRightAccountItemsCache: [SidebarItem] = []
    var participantLookupCacheKey = ""
    var participantLookupCache: [UUID: ChatParticipant] = [:]
    var groupConversationMetadataCacheRevision = -1
    var groupConversationIDsCache: Set<UUID> = []
    var inferredGroupMembersCache: [UUID: [ChatParticipant]] = [:]
    var resolvedGroupMembersCache: [UUID: [ChatParticipant]] = [:]
    var latestSidebarMessagesCacheKey = ""
    var latestSidebarMessagesCache: [UUID: ChatMessage] = [:]
    var latestSidebarSummaryCacheKey = ""
    var latestSidebarSummaryCache: [UUID: (text: String, time: String)] = [:]
    var renderedMessagesCacheKey = ""
    var renderedMessagesCache: [ChatMessage] = []
    var renderedMessagesByConversationCacheKey = ""
    var renderedMessagesByConversationCache: [UUID: [ChatMessage]] = [:]
    var unreadTimelineLayoutCacheKey = ""
    var unreadTimelineLayoutCache: UnreadTimelineLayoutSnapshot?
    var unreadTimelineMeasuredAvatarTopByParticipantID: [UUID: CGFloat] = [:]
    var pendingReplyIndex = PendingReplyIndex()
    var isBuildingPendingReplyIndex = false
    let pendingReplyIndexAsyncResultStore = PendingReplyIndexAsyncResultStore()
    var pendingReplyIndexBuildGeneration = 0
    var pendingReplyIndexRebuildWorkItem: DispatchWorkItem?
#if DEBUG
    var hasStartedPerformanceAutoRun = false
#endif
    var visibleDataRevision = 0
    var accountSwitchRevision = 0
    var conversationSelectionRevision = 0
    var messageHeightCache: [MessageHeightCacheKey: CGFloat] = [:]
    var messageHeightCacheInsertionOrder: [MessageHeightCacheKey] = []
    let maximumMessageHeightCacheEntries = 2_000
    var lastMessageHeightCacheWidth: CGFloat = 0
    var conversationHeightPrewarmGeneration = 0
    let messageHeightPrewarmQueue = DispatchQueue(
        label: "local.ios-float.message-height-prewarm",
        qos: .userInitiated
    )
    var isSynchronizingUnreadScroll = false
    let connectionViewportInset: CGFloat = 12
    var leftScrollNeedsPreviewUpdate = false
    var leftScrollNeedsFloatingUpdate = false
    var leftScrollNeedsConnectionUpdate = false
    var leftScrollNeedsAvatarPrefetch = false
    var leftScrollFrameCounter = 0
    weak var activeNoticeView: UIView?
    var activeNoticeDismissWorkItem: DispatchWorkItem?
    var mediaDragView: UIView?
    var mediaDragTitleLabel: UILabel?
    var mediaDragHighlightedParticipantID: UUID?
    var messageDropHighlightedIndexPath: IndexPath?
    var messageDropHintView: UIView?
    var messageDropHintLabel: UILabel?
    var messageTrashDropView: UIVisualEffectView?
    var messageTrashDropIconView: UIImageView?
    var messageTrashDropLidView: UIView?
    var messageTrashDropLabel: UILabel?
    weak var messageDragLongPressGesture: UILongPressGestureRecognizer?
    var messageDragCandidate: ChatMessage?
    var messageDragStartScreenPoint = CGPoint.zero
    var isMessageDragActive = false
    weak var rightEdgeDouyinPanGesture: UIPanGestureRecognizer?
    weak var leftEdgePanGesture: UIPanGestureRecognizer?
    weak var conversationProfileAreaPanGesture: UIPanGestureRecognizer?
    var conversationProfilePanStartPoint = CGPoint.zero
    var lastConversationProfileOpenAt: Date?
    var rightEdgeDouyinGestureStartPoint = CGPoint.zero
    var leftEdgeGestureStartPoint = CGPoint.zero
    var rightEdgeGestureCurrentTarget: EdgeLaunchTarget?
    var leftEdgeGestureCurrentTarget: EdgeLaunchTarget?
    let motionManager = CMMotionManager()
    var lastQRCodePresentationAt: Date?
    var tiltCandidateStartedAt: Date?
    var lastTiltDirection: Int = 0
    var isTiltQRCodeArmed = false
    var tiltNeutralPitch: Double?
    var tiltCandidatePeakPitch: Double = 0
    var isLeftEdgePullConfirmed = false
    var isRightEdgeGestureActive = false
    var isLeftEdgeGestureActive = false
    let emojiItems = [
        "🙂", "🥺", "😍", "😠", "😎", "😭", "😴", "🤐", "😪", "😆",
        "🥵", "😡", "😜", "😁", "🙄", "☹️", "😳", "😤", "🥶", "🤔",
        "😊", "😯", "🙄", "😑", "😰", "😄", "🤠", "🤣", "🫢", "🤫",
        "😵‍💫", "😱", "💀", "📸", "🤭", "😅", "😮‍💨", "🤲", "😬", "😙",
        "😷", "😂", "😨", "😔", "😞", "🫡", "🫣", "😉", "😏", "😊",
        "🤗", "🤢", "🤒", "😐", "😲", "😶", "😪", "😼", "🤨", "😘",
        "🤩", "😒", "666", "👌", "😮‍💨", "😭", "🥹", "💋", "❤️", "💔",
        "🫡", "👍", "🙏", "👏", "💪", "🎉", "🔥", "✨", "🌹", "🍀"
    ]

    init() {
        leftCollectionView = UICollectionView(
            frame: .zero,
            collectionViewLayout: Self.makeSidebarLayout()
        )
        messageCollectionView = UICollectionView(
            frame: .zero,
            collectionViewLayout: Self.makeMessageLayout()
        )
        rightAccountCollectionView = UICollectionView(
            frame: .zero,
            collectionViewLayout: Self.makeSidebarLayout()
        )
        rightToolCollectionView = UICollectionView(
            frame: .zero,
            collectionViewLayout: Self.makeSidebarLayout()
        )
        super.init(nibName: nil, bundle: nil)
    }

    required init?(coder: NSCoder) {
        leftCollectionView = UICollectionView(
            frame: .zero,
            collectionViewLayout: Self.makeSidebarLayout()
        )
        messageCollectionView = UICollectionView(
            frame: .zero,
            collectionViewLayout: Self.makeMessageLayout()
        )
        rightAccountCollectionView = UICollectionView(
            frame: .zero,
            collectionViewLayout: Self.makeSidebarLayout()
        )
        rightToolCollectionView = UICollectionView(
            frame: .zero,
            collectionViewLayout: Self.makeSidebarLayout()
        )
        super.init(coder: coder)
    }

    override func loadView() {
        view = UIView()
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        let isPerformanceScenario = ChatDataFactory.isPerformanceScenarioRequested
        navigationController?.setNavigationBarHidden(true, animated: false)
        view.backgroundColor = UIColor(red: 0.62, green: 0.70, blue: 0.72, alpha: 1)
        loadAppLanguage()
        loadQuickPhrases()
        loadUserFavoriteItems()
        loadDefaultWebLink()
        loadLinkAccessSetting()
        loadAccessRequests()
        loadRestrictedSentLinks()
        loadCodexConfiguration()
        loadClaudeConfiguration()
        loadAutoReplyConfiguration()
        loadSideEffectConfiguration()
        loadBubble3DAppearanceConfiguration()
        loadAutomaticBackgroundRemovalConfiguration()
        loadLeftSidebarDisplayModeConfiguration()
        loadGroupDisplaySettings()
        if !isPerformanceScenario {
            installDemoDocumentMessages()
            installDemoMediaAttachments()
            installDemoVoiceAttachments()
            installDemoMusicAttachments()
            loadPersistedMessagesOrSeedStore()
            loadPersistedOpenApiDeliveryStatuses()
        }
        customRightToolOrderKeys = defaultRightToolOrderKeys()
        setupChrome()
        setupHeader()
        setupInputBar()
        setupCollections()
        setupGestures()
        setupKeyboardObservers()
        setupSnapshotDragObserver()
        let aiInputAssistant = AIInputFloatingAssistant.shared
        aiInputAssistant.install()
        setupScreenshotProtectionObserver()
        if !isPerformanceScenario {
            loadOpenApiChatSyncCheckpoint()
            loadPersistedOpenApiIMSnapshotIfAvailable()
        }
        updateHeaderForSelection()
        applyWindowState(.expanded, animated: false)
        startConnectionTracking()
        startTiltQRCodeDetection()
        if !isPerformanceScenario {
            configureAutoReplyTimer()
            syncOpenApiIMDataIfNeeded()
        }
    }

    deinit {
        NotificationCenter.default.removeObserver(self)
        cancelOutstandingCollectionPrefetches()
        cancelLeftScrollPreview()
        stopConnectionTracking()
        stopTiltQRCodeDetection()
        autoReplyTimer?.invalidate()
        openApiChatPollTimer?.invalidate()
        openApiChatSyncStartWorkItem?.cancel()
        pendingReplyIndexRebuildWorkItem?.cancel()
        openApiChatSyncTask?.cancel()
        activeNoticeDismissWorkItem?.cancel()
        audioPlayer?.stop()
    }

}

extension UIView {
    func wrapped(insets: UIEdgeInsets) -> UIView {
        let container = UIView()
        container.backgroundColor = UIColor.secondarySystemGroupedBackground
        container.layer.cornerRadius = 14
        container.layer.cornerCurve = .continuous
        container.translatesAutoresizingMaskIntoConstraints = false
        translatesAutoresizingMaskIntoConstraints = false
        container.addSubview(self)
        NSLayoutConstraint.activate([
            topAnchor.constraint(equalTo: container.topAnchor, constant: insets.top),
            leadingAnchor.constraint(equalTo: container.leadingAnchor, constant: insets.left),
            trailingAnchor.constraint(equalTo: container.trailingAnchor, constant: -insets.right),
            bottomAnchor.constraint(equalTo: container.bottomAnchor, constant: -insets.bottom)
        ])
        return container
    }
}


struct PickedLocation {
    let title: String
    let address: String
    let coordinate: CLLocationCoordinate2D
}



struct ChannelsVideoForwardPayload {
    let title: String
    let detail: String
    let videoURL: URL?
}

final class RichContentDetailViewController: UIViewController {
    enum Mode {
        case webLink(url: URL)
        case article
        case miniProgram
        case live
    }

    var onPrimaryAction: (() -> Void)?

    let mode: Mode
    let titleText: String
    let subtitleText: String
    let detailText: String
    let stackView = UIStackView()

    init(mode: Mode, titleText: String, subtitleText: String, detailText: String) {
        self.mode = mode
        self.titleText = titleText
        self.subtitleText = subtitleText
        self.detailText = detailText
        super.init(nibName: nil, bundle: nil)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        title = navigationTitle
        view.backgroundColor = UIColor.systemGroupedBackground
        navigationItem.leftBarButtonItem = UIBarButtonItem(title: "返回", style: .plain, target: self, action: #selector(close))
        configureLayout()
    }

    var navigationTitle: String {
        switch mode {
        case .webLink: return "网页预览"
        case .article: return "图文详情"
        case .miniProgram: return "小程序"
        case .live: return "直播间"
        }
    }

    var primaryTitle: String {
        switch mode {
        case .webLink: return "打开浏览器"
        case .article: return "打开文章"
        case .miniProgram: return "打开对应 App"
        case .live: return "进入直播"
        }
    }

    var symbolName: String {
        switch mode {
        case .webLink: return "safari"
        case .article: return "newspaper.fill"
        case .miniProgram: return "app.fill"
        case .live: return "dot.radiowaves.left.and.right"
        }
    }

    func configureLayout() {
        let scrollView = UIScrollView()
        scrollView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(scrollView)

        stackView.axis = .vertical
        stackView.spacing = 14
        stackView.translatesAutoresizingMaskIntoConstraints = false
        scrollView.addSubview(stackView)

        NSLayoutConstraint.activate([
            scrollView.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor),
            scrollView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            scrollView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            scrollView.bottomAnchor.constraint(equalTo: view.bottomAnchor),
            stackView.topAnchor.constraint(equalTo: scrollView.contentLayoutGuide.topAnchor, constant: 16),
            stackView.leadingAnchor.constraint(equalTo: scrollView.frameLayoutGuide.leadingAnchor, constant: 16),
            stackView.trailingAnchor.constraint(equalTo: scrollView.frameLayoutGuide.trailingAnchor, constant: -16),
            stackView.bottomAnchor.constraint(equalTo: scrollView.contentLayoutGuide.bottomAnchor, constant: -24)
        ])

        stackView.addArrangedSubview(makeHeroCard())
        stackView.addArrangedSubview(makeBodyCard())
        stackView.addArrangedSubview(makePrimaryButton())
    }

    func makeHeroCard() -> UIView {
        let card = UIView()
        card.backgroundColor = UIColor.secondarySystemGroupedBackground
        card.layer.cornerRadius = 18
        card.layer.cornerCurve = .continuous
        card.translatesAutoresizingMaskIntoConstraints = false

        let icon = UIImageView(image: UIImage(systemName: symbolName))
        icon.tintColor = UIColor(red: 0.10, green: 0.44, blue: 0.72, alpha: 1)
        icon.contentMode = .scaleAspectFit
        icon.translatesAutoresizingMaskIntoConstraints = false

        let titleLabel = UILabel()
        titleLabel.text = titleText
        titleLabel.font = .systemFont(ofSize: 22, weight: .bold)
        titleLabel.textColor = .label
        titleLabel.numberOfLines = 3
        titleLabel.translatesAutoresizingMaskIntoConstraints = false

        let subtitleLabel = UILabel()
        subtitleLabel.text = subtitleText
        subtitleLabel.font = .systemFont(ofSize: 13, weight: .medium)
        subtitleLabel.textColor = .secondaryLabel
        subtitleLabel.numberOfLines = 2
        subtitleLabel.translatesAutoresizingMaskIntoConstraints = false

        let preview = UIView()
        preview.backgroundColor = UIColor(red: 0.10, green: 0.20, blue: 0.24, alpha: 1)
        preview.layer.cornerRadius = 14
        preview.layer.cornerCurve = .continuous
        preview.translatesAutoresizingMaskIntoConstraints = false

        let previewLabel = UILabel()
        previewLabel.text = previewText
        previewLabel.font = .systemFont(ofSize: 15, weight: .semibold)
        previewLabel.textColor = .white
        previewLabel.numberOfLines = 3
        previewLabel.translatesAutoresizingMaskIntoConstraints = false
        preview.addSubview(previewLabel)

        [icon, titleLabel, subtitleLabel, preview].forEach { card.addSubview($0) }

        NSLayoutConstraint.activate([
            card.heightAnchor.constraint(greaterThanOrEqualToConstant: 260),
            icon.leadingAnchor.constraint(equalTo: card.leadingAnchor, constant: 18),
            icon.topAnchor.constraint(equalTo: card.topAnchor, constant: 18),
            icon.widthAnchor.constraint(equalToConstant: 34),
            icon.heightAnchor.constraint(equalToConstant: 34),
            titleLabel.leadingAnchor.constraint(equalTo: icon.trailingAnchor, constant: 12),
            titleLabel.trailingAnchor.constraint(equalTo: card.trailingAnchor, constant: -18),
            titleLabel.topAnchor.constraint(equalTo: icon.topAnchor, constant: -2),
            subtitleLabel.leadingAnchor.constraint(equalTo: titleLabel.leadingAnchor),
            subtitleLabel.trailingAnchor.constraint(equalTo: titleLabel.trailingAnchor),
            subtitleLabel.topAnchor.constraint(equalTo: titleLabel.bottomAnchor, constant: 8),
            preview.leadingAnchor.constraint(equalTo: card.leadingAnchor, constant: 18),
            preview.trailingAnchor.constraint(equalTo: card.trailingAnchor, constant: -18),
            preview.topAnchor.constraint(equalTo: subtitleLabel.bottomAnchor, constant: 18),
            preview.bottomAnchor.constraint(equalTo: card.bottomAnchor, constant: -18),
            preview.heightAnchor.constraint(greaterThanOrEqualToConstant: 132),
            previewLabel.leadingAnchor.constraint(equalTo: preview.leadingAnchor, constant: 18),
            previewLabel.trailingAnchor.constraint(equalTo: preview.trailingAnchor, constant: -18),
            previewLabel.centerYAnchor.constraint(equalTo: preview.centerYAnchor)
        ])
        return card
    }

    var previewText: String {
        switch mode {
        case .webLink(let url): return "网页预览\n\(url.host ?? url.absoluteString)\n点击按钮可跳转浏览器"
        case .article: return "图文封面\n包含标题、来源、摘要和正文阅读"
        case .miniProgram: return "小程序页面\n点击后跳转对应 App 打开"
        case .live: return "直播画面\n实时互动、预约、进入直播间"
        }
    }

    func makeBodyCard() -> UIView {
        let label = UILabel()
        label.text = bodyText
        label.font = .systemFont(ofSize: 16)
        label.textColor = .label
        label.numberOfLines = 0
        label.backgroundColor = UIColor.secondarySystemGroupedBackground
        label.layer.cornerRadius = 14
        label.layer.cornerCurve = .continuous
        label.clipsToBounds = true
        label.translatesAutoresizingMaskIntoConstraints = false
        label.heightAnchor.constraint(greaterThanOrEqualToConstant: 150).isActive = true
        return label.wrapped(insets: UIEdgeInsets(top: 16, left: 16, bottom: 16, right: 16))
    }

    var bodyText: String {
        switch mode {
        case .article:
            return "\(detailText)\n\n今天的活动内容已整理为图文详情，可查看封面、正文摘要、关键时间、负责人和后续动作。这个页面用于替代原先只有提示的静态卡片。"
        case .live:
            return "\(detailText)\n\n直播间状态：可预约、可进入、可查看互动。当前为本地模拟直播页面，能完整展示直播入口流程。"
        case .miniProgram:
            return "\(detailText)\n\n小程序不在当前 App 内预览，点击后会跳转到微信、抖音等对应 App 打开。"
        case .webLink(let url):
            return "\(detailText.isEmpty ? "网页链接详情" : detailText)\n\n目标地址：\(url.absoluteString)"
        }
    }

    func makePrimaryButton() -> UIButton {
        let button = UIButton(type: .system)
        button.setTitle(primaryTitle, for: .normal)
        button.titleLabel?.font = .systemFont(ofSize: 16, weight: .semibold)
        button.tintColor = .white
        button.backgroundColor = UIColor(red: 0.10, green: 0.44, blue: 0.72, alpha: 1)
        button.layer.cornerRadius = 20
        button.layer.cornerCurve = .continuous
        button.heightAnchor.constraint(equalToConstant: 44).isActive = true
        button.addTarget(self, action: #selector(primaryAction), for: .touchUpInside)
        return button
    }

    @objc private func primaryAction() {
        if let onPrimaryAction {
            onPrimaryAction()
            return
        }
        if case .article = mode {
            let alert = UIAlertController(title: "已收藏文章", message: nil, preferredStyle: .alert)
            present(alert, animated: true)
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.8) {
                alert.dismiss(animated: true)
            }
        }
    }

    @objc private func close() {
        navigationController?.popViewController(animated: true)
    }
}

final class ChannelsVideoDetailViewController: UIViewController {
    var onShowNotice: ((String) -> Void)?
    var onForward: ((ChannelsVideoForwardPayload) -> Void)?

    let titleText: String
    let detailText: String
    let videoURL: URL?
    let playerViewController = AVPlayerViewController()
    let titleLabel = UILabel()
    let authorLabel = UILabel()
    let followButton = UIButton(type: .system)
    let likeButton = UIButton(type: .system)
    let commentButton = UIButton(type: .system)
    let favoriteButton = UIButton(type: .system)
    let shareButton = UIButton(type: .system)
    let commentPreviewLabel = UILabel()
    let feedControl = UISegmentedControl(items: ["推荐", "关注"])
    let homeButton = UIButton(type: .system)
    let nextVideoButton = UIButton(type: .system)
    var commentOverlayView: UIView?
    var commentPanelView: UIView?
    var commentInputField: UITextField?
    var commentPanelBottomConstraint: NSLayoutConstraint?
    var commentRows: [String] = [
        "篮球邮差Melo：今天这条更新很适合转发。",
        "张三：已收藏，晚点再看。"
    ]
    var isFollowed = false
    var isLiked = false
    var isFavorited = false
    var likeCount = 1945
    var commentCount = 280
    var favoriteCount = 1268
    var shareCount = 736
    var currentFeedIndex = 0
    var currentVideoIndex = 0
    var currentPlaybackRate: Float = 1.0
    var longPressRestoreRate: Float?
    var longPressWasPlaying = false
    var speedHintLabel: UILabel?

    init(titleText: String, detailText: String, videoURL: URL?) {
        self.titleText = titleText
        self.detailText = detailText
        self.videoURL = videoURL
        super.init(nibName: nil, bundle: nil)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        title = "视频号"
        view.backgroundColor = .black
        navigationItem.leftBarButtonItem = UIBarButtonItem(title: "返回", style: .plain, target: self, action: #selector(close))
        configurePlayer()
        configureInfoPanel()
        registerCommentKeyboardObservers()
        updateActionButtons()
    }

    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        playerViewController.player?.playImmediately(atRate: currentPlaybackRate)
        DispatchQueue.main.async { [weak self] in
            guard let self else { return }
            self.playerViewController.player?.playImmediately(atRate: self.currentPlaybackRate)
        }
    }

    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        playerViewController.player?.pause()
    }

    deinit {
        NotificationCenter.default.removeObserver(self)
    }

    func configurePlayer() {
        addChild(playerViewController)
        playerViewController.view.translatesAutoresizingMaskIntoConstraints = false
        playerViewController.view.backgroundColor = .black
        playerViewController.showsPlaybackControls = true
        view.addSubview(playerViewController.view)
        playerViewController.didMove(toParent: self)
        if let videoURL {
            playerViewController.player = AVPlayer(url: videoURL)
        }
        let longPress = UILongPressGestureRecognizer(target: self, action: #selector(handleVideoSpeedLongPress(_:)))
        longPress.minimumPressDuration = 0.28
        longPress.cancelsTouchesInView = false
        playerViewController.view.addGestureRecognizer(longPress)

        NSLayoutConstraint.activate([
            playerViewController.view.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor),
            playerViewController.view.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            playerViewController.view.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            playerViewController.view.heightAnchor.constraint(equalTo: view.heightAnchor, multiplier: 0.54)
        ])
    }

    @objc private func handleVideoSpeedLongPress(_ gesture: UILongPressGestureRecognizer) {
        guard let player = playerViewController.player else { return }
        switch gesture.state {
        case .began:
            longPressRestoreRate = currentPlaybackRate
            longPressWasPlaying = player.rate > 0 || player.timeControlStatus == .playing
            player.playImmediately(atRate: 2.0)
            showSpeedHint()
        case .ended, .cancelled, .failed:
            let restoreRate = longPressRestoreRate ?? currentPlaybackRate
            longPressRestoreRate = nil
            if longPressWasPlaying {
                currentPlaybackRate = restoreRate
                player.rate = restoreRate
            } else {
                player.pause()
            }
            longPressWasPlaying = false
            hideSpeedHint()
        default:
            break
        }
    }

    func showSpeedHint() {
        if speedHintLabel == nil {
            let label = UILabel()
            label.text = "2x 快进中"
            label.textAlignment = .center
            label.font = .systemFont(ofSize: 16, weight: .bold)
            label.textColor = .white
            label.backgroundColor = UIColor.black.withAlphaComponent(0.48)
            label.layer.cornerRadius = 18
            label.layer.cornerCurve = .continuous
            label.clipsToBounds = true
            label.translatesAutoresizingMaskIntoConstraints = false
            playerViewController.view.addSubview(label)
            speedHintLabel = label
            NSLayoutConstraint.activate([
                label.centerXAnchor.constraint(equalTo: playerViewController.view.centerXAnchor),
                label.centerYAnchor.constraint(equalTo: playerViewController.view.centerYAnchor),
                label.widthAnchor.constraint(greaterThanOrEqualToConstant: 112),
                label.heightAnchor.constraint(equalToConstant: 36)
            ])
        }
        playerViewController.view.bringSubviewToFront(speedHintLabel!)
        speedHintLabel?.alpha = 1
    }

    func hideSpeedHint() {
        UIView.animate(withDuration: 0.16) {
            self.speedHintLabel?.alpha = 0
        }
    }

    func configureInfoPanel() {
        let panel = UIView()
        panel.backgroundColor = .black
        panel.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(panel)

        feedControl.selectedSegmentIndex = 0
        feedControl.selectedSegmentTintColor = .white
        feedControl.setTitleTextAttributes([.foregroundColor: UIColor.white.withAlphaComponent(0.72), .font: UIFont.systemFont(ofSize: 13, weight: .semibold)], for: .normal)
        feedControl.setTitleTextAttributes([.foregroundColor: UIColor.black, .font: UIFont.systemFont(ofSize: 13, weight: .bold)], for: .selected)
        feedControl.addTarget(self, action: #selector(changeFeed), for: .valueChanged)
        feedControl.translatesAutoresizingMaskIntoConstraints = false

        titleLabel.text = cleanedTitle
        titleLabel.font = .systemFont(ofSize: 17, weight: .semibold)
        titleLabel.textColor = .white
        titleLabel.numberOfLines = 3
        titleLabel.lineBreakMode = .byTruncatingTail
        titleLabel.setContentCompressionResistancePriority(.required, for: .vertical)
        titleLabel.translatesAutoresizingMaskIntoConstraints = false

        let avatarLabel = UILabel()
        avatarLabel.text = String(authorName.prefix(1)).uppercased()
        avatarLabel.textAlignment = .center
        avatarLabel.textColor = .white
        avatarLabel.font = .systemFont(ofSize: 18, weight: .bold)
        avatarLabel.backgroundColor = UIColor(red: 0.95, green: 0.42, blue: 0.16, alpha: 1)
        avatarLabel.layer.cornerRadius = 23
        avatarLabel.layer.cornerCurve = .continuous
        avatarLabel.clipsToBounds = true
        avatarLabel.translatesAutoresizingMaskIntoConstraints = false

        authorLabel.text = "\(authorName)\n1个朋友关注"
        authorLabel.font = .systemFont(ofSize: 14, weight: .medium)
        authorLabel.textColor = UIColor.white.withAlphaComponent(0.9)
        authorLabel.numberOfLines = 2
        authorLabel.setContentCompressionResistancePriority(.required, for: .vertical)
        authorLabel.translatesAutoresizingMaskIntoConstraints = false

        followButton.titleLabel?.font = .systemFont(ofSize: 13, weight: .semibold)
        followButton.layer.cornerRadius = 15
        followButton.layer.cornerCurve = .continuous
        followButton.addTarget(self, action: #selector(toggleFollow), for: .touchUpInside)
        followButton.translatesAutoresizingMaskIntoConstraints = false

        configureSmallTextButton(homeButton, title: "主页", symbol: "person.crop.square")
        homeButton.addTarget(self, action: #selector(openAuthorHome), for: .touchUpInside)
        homeButton.translatesAutoresizingMaskIntoConstraints = false

        configureSmallTextButton(nextVideoButton, title: "下一条", symbol: "chevron.down.circle")
        nextVideoButton.addTarget(self, action: #selector(showNextVideo), for: .touchUpInside)
        nextVideoButton.translatesAutoresizingMaskIntoConstraints = false

        commentPreviewLabel.text = "精选评论：这条视频号内容可以继续跟进，点击评论可参与互动。"
        commentPreviewLabel.font = .systemFont(ofSize: 13, weight: .regular)
        commentPreviewLabel.textColor = UIColor.white.withAlphaComponent(0.72)
        commentPreviewLabel.numberOfLines = 1
        commentPreviewLabel.lineBreakMode = .byTruncatingTail
        commentPreviewLabel.translatesAutoresizingMaskIntoConstraints = false

        let actionStack = UIStackView(arrangedSubviews: [likeButton, commentButton, favoriteButton, shareButton])
        actionStack.axis = .horizontal
        actionStack.spacing = 8
        actionStack.distribution = .fillEqually
        actionStack.translatesAutoresizingMaskIntoConstraints = false

        [feedControl, titleLabel, avatarLabel, authorLabel, followButton, homeButton, nextVideoButton, commentPreviewLabel, actionStack].forEach(panel.addSubview)

        NSLayoutConstraint.activate([
            panel.topAnchor.constraint(equalTo: playerViewController.view.bottomAnchor),
            panel.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            panel.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            panel.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor),
            panel.heightAnchor.constraint(greaterThanOrEqualToConstant: 228),

            feedControl.topAnchor.constraint(equalTo: panel.topAnchor, constant: 10),
            feedControl.leadingAnchor.constraint(equalTo: panel.leadingAnchor, constant: 18),
            feedControl.widthAnchor.constraint(equalToConstant: 132),
            feedControl.heightAnchor.constraint(equalToConstant: 30),

            titleLabel.topAnchor.constraint(equalTo: feedControl.bottomAnchor, constant: 10),
            titleLabel.leadingAnchor.constraint(equalTo: panel.leadingAnchor, constant: 18),
            titleLabel.trailingAnchor.constraint(equalTo: panel.trailingAnchor, constant: -18),

            avatarLabel.leadingAnchor.constraint(equalTo: titleLabel.leadingAnchor),
            avatarLabel.topAnchor.constraint(equalTo: titleLabel.bottomAnchor, constant: 12),
            avatarLabel.widthAnchor.constraint(equalToConstant: 46),
            avatarLabel.heightAnchor.constraint(equalToConstant: 46),

            authorLabel.leadingAnchor.constraint(equalTo: avatarLabel.trailingAnchor, constant: 11),
            authorLabel.centerYAnchor.constraint(equalTo: avatarLabel.centerYAnchor),
            authorLabel.trailingAnchor.constraint(lessThanOrEqualTo: followButton.leadingAnchor, constant: -12),

            followButton.trailingAnchor.constraint(equalTo: panel.trailingAnchor, constant: -18),
            followButton.centerYAnchor.constraint(equalTo: avatarLabel.centerYAnchor),
            followButton.widthAnchor.constraint(equalToConstant: 72),
            followButton.heightAnchor.constraint(equalToConstant: 30),

            nextVideoButton.trailingAnchor.constraint(equalTo: panel.trailingAnchor, constant: -18),
            nextVideoButton.topAnchor.constraint(equalTo: feedControl.topAnchor),
            nextVideoButton.widthAnchor.constraint(equalToConstant: 84),
            nextVideoButton.heightAnchor.constraint(equalToConstant: 30),

            homeButton.trailingAnchor.constraint(equalTo: nextVideoButton.leadingAnchor, constant: -8),
            homeButton.centerYAnchor.constraint(equalTo: nextVideoButton.centerYAnchor),
            homeButton.widthAnchor.constraint(equalToConstant: 68),
            homeButton.heightAnchor.constraint(equalToConstant: 30),

            commentPreviewLabel.leadingAnchor.constraint(equalTo: titleLabel.leadingAnchor),
            commentPreviewLabel.trailingAnchor.constraint(equalTo: titleLabel.trailingAnchor),
            commentPreviewLabel.topAnchor.constraint(equalTo: avatarLabel.bottomAnchor, constant: 12),

            actionStack.leadingAnchor.constraint(equalTo: panel.leadingAnchor, constant: 14),
            actionStack.trailingAnchor.constraint(equalTo: panel.trailingAnchor, constant: -14),
            actionStack.bottomAnchor.constraint(equalTo: panel.bottomAnchor, constant: -12),
            actionStack.heightAnchor.constraint(equalToConstant: 54),
            commentPreviewLabel.bottomAnchor.constraint(lessThanOrEqualTo: actionStack.topAnchor, constant: -10)
        ])

        likeButton.addTarget(self, action: #selector(toggleLike), for: .touchUpInside)
        commentButton.addTarget(self, action: #selector(showComments), for: .touchUpInside)
        favoriteButton.addTarget(self, action: #selector(toggleFavorite), for: .touchUpInside)
        shareButton.addTarget(self, action: #selector(shareVideo), for: .touchUpInside)
    }

    var cleanedTitle: String {
        titleText
            .replacingOccurrences(of: "视频号：", with: "")
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .isEmpty ? "视频号视频" : titleText.replacingOccurrences(of: "视频号：", with: "").trimmingCharacters(in: .whitespacesAndNewlines)
    }

    var authorName: String {
        let lines = detailText
            .components(separatedBy: .newlines)
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
        if let source = lines.first(where: { $0.hasPrefix("作者：") || $0.hasPrefix("视频号：") }) {
            let name = source
                .replacingOccurrences(of: "作者：", with: "")
                .replacingOccurrences(of: "视频号：", with: "")
                .trimmingCharacters(in: .whitespacesAndNewlines)
            if !name.isEmpty { return name }
        }
        return "篮球邮差Melo"
    }

    func updateActionButtons() {
        configureActionButton(likeButton, symbolName: isLiked ? "heart.fill" : "heart", title: "\(likeCount)")
        configureActionButton(commentButton, symbolName: "bubble.right", title: "\(commentCount)")
        configureActionButton(favoriteButton, symbolName: isFavorited ? "star.fill" : "star", title: "\(favoriteCount)")
        configureActionButton(shareButton, symbolName: "arrowshape.turn.up.right", title: "\(shareCount)")
        likeButton.tintColor = isLiked ? UIColor(red: 1, green: 0.25, blue: 0.35, alpha: 1) : .white
        favoriteButton.tintColor = isFavorited ? UIColor(red: 1, green: 0.72, blue: 0.18, alpha: 1) : .white

        followButton.setTitle(isFollowed ? "已关注" : "关注", for: .normal)
        followButton.tintColor = isFollowed ? UIColor.white.withAlphaComponent(0.82) : .black
        followButton.backgroundColor = isFollowed ? UIColor.white.withAlphaComponent(0.15) : .white
    }

    func configureActionButton(_ button: UIButton, symbolName: String, title: String) {
        var config = UIButton.Configuration.plain()
        config.image = UIImage(systemName: symbolName)
        config.title = title
        config.imagePlacement = .top
        config.imagePadding = 5
        config.baseForegroundColor = .white
        button.configuration = config
    }

    func configureSmallTextButton(_ button: UIButton, title: String, symbol: String) {
        var config = UIButton.Configuration.filled()
        config.image = UIImage(systemName: symbol)
        config.title = title
        config.imagePadding = 4
        config.contentInsets = NSDirectionalEdgeInsets(top: 5, leading: 8, bottom: 5, trailing: 8)
        config.baseForegroundColor = .white
        config.baseBackgroundColor = UIColor.white.withAlphaComponent(0.14)
        button.configuration = config
        button.layer.cornerRadius = 15
        button.layer.cornerCurve = .continuous
        button.clipsToBounds = true
    }

    @objc private func toggleFollow() {
        isFollowed.toggle()
        updateActionButtons()
        onShowNotice?(isFollowed ? "已关注视频号" : "已取消关注")
    }

    @objc private func changeFeed() {
        currentFeedIndex = feedControl.selectedSegmentIndex
        let feedName = currentFeedIndex == 0 ? "推荐" : "关注"
        commentPreviewLabel.text = "\(feedName)流 · 已切换到\(feedName)视频流，继续上滑/下一条可浏览。"
        onShowNotice?("已切换到\(feedName)流")
    }

    @objc private func openAuthorHome() {
        let controller = ChannelsAuthorHomeViewController(authorName: authorName, followed: isFollowed)
        controller.onFollowChanged = { [weak self] followed in
            self?.isFollowed = followed
            self?.updateActionButtons()
        }
        navigationController?.pushViewController(controller, animated: true)
    }

    @objc private func showNextVideo() {
        currentVideoIndex += 1
        titleLabel.text = currentVideoIndex.isMultiple(of: 2) ? "门店开业现场回放 · 推荐" : "客户到店体验短片 · 关注"
        commentPreviewLabel.text = "已切换到第 \(currentVideoIndex + 1) 条视频 · 自动播放中"
        likeCount = 1200 + currentVideoIndex * 137
        commentCount = 80 + currentVideoIndex * 23
        favoriteCount = 420 + currentVideoIndex * 31
        shareCount = 90 + currentVideoIndex * 17
        isLiked = false
        isFavorited = false
        updateActionButtons()
        playerViewController.player?.seek(to: .zero)
        playerViewController.player?.playImmediately(atRate: currentPlaybackRate)
    }

    @objc private func toggleLike() {
        isLiked.toggle()
        likeCount += isLiked ? 1 : -1
        updateActionButtons()
    }

    @objc private func toggleFavorite() {
        isFavorited.toggle()
        favoriteCount += isFavorited ? 1 : -1
        updateActionButtons()
        onShowNotice?(isFavorited ? "已收藏视频号视频" : "已取消收藏")
    }

    @objc private func shareVideo() {
        shareCount += 1
        updateActionButtons()
        onForward?(
            ChannelsVideoForwardPayload(
                title: cleanedTitle,
                detail: detailText.isEmpty ? "\(authorName) · 视频号" : detailText,
                videoURL: videoURL
            )
        )
    }

    @objc private func showComments() {
        presentCommentPanel()
    }

    func presentCommentPanel() {
        dismissCommentPanel(animated: false)

        let overlay = UIControl()
        overlay.backgroundColor = UIColor.black.withAlphaComponent(0.42)
        overlay.alpha = 0
        overlay.addTarget(self, action: #selector(dismissCommentPanelFromOverlay), for: .touchUpInside)
        overlay.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(overlay)

        let panel = UIView()
        panel.backgroundColor = UIColor(red: 0.08, green: 0.08, blue: 0.09, alpha: 1)
        panel.layer.cornerRadius = 22
        panel.layer.cornerCurve = .continuous
        panel.layer.maskedCorners = [.layerMinXMinYCorner, .layerMaxXMinYCorner]
        panel.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(panel)

        let grabber = UIView()
        grabber.backgroundColor = UIColor.white.withAlphaComponent(0.28)
        grabber.layer.cornerRadius = 2
        grabber.translatesAutoresizingMaskIntoConstraints = false

        let titleLabel = UILabel()
        titleLabel.text = "评论"
        titleLabel.font = .systemFont(ofSize: 17, weight: .semibold)
        titleLabel.textColor = .white
        titleLabel.translatesAutoresizingMaskIntoConstraints = false

        let closeButton = UIButton(type: .system)
        closeButton.setImage(UIImage(systemName: "xmark"), for: .normal)
        closeButton.tintColor = .white.withAlphaComponent(0.82)
        closeButton.addTarget(self, action: #selector(dismissCommentPanelFromOverlay), for: .touchUpInside)
        closeButton.translatesAutoresizingMaskIntoConstraints = false

        let commentsStack = UIStackView()
        commentsStack.axis = .vertical
        commentsStack.spacing = 12
        commentsStack.translatesAutoresizingMaskIntoConstraints = false
        commentRows.forEach { commentsStack.addArrangedSubview(makeCommentRow(text: $0)) }

        let inputContainer = UIView()
        inputContainer.backgroundColor = UIColor.white.withAlphaComponent(0.10)
        inputContainer.layer.cornerRadius = 18
        inputContainer.layer.cornerCurve = .continuous
        inputContainer.translatesAutoresizingMaskIntoConstraints = false

        let inputField = UITextField()
        inputField.placeholder = "写评论"
        inputField.attributedPlaceholder = NSAttributedString(
            string: "写评论",
            attributes: [.foregroundColor: UIColor.white.withAlphaComponent(0.45)]
        )
        inputField.textColor = .white
        inputField.tintColor = .white
        inputField.font = .systemFont(ofSize: 15)
        inputField.returnKeyType = .send
        inputField.addTarget(self, action: #selector(sendCommentFromInput), for: .editingDidEndOnExit)
        inputField.translatesAutoresizingMaskIntoConstraints = false

        let sendButton = UIButton(type: .system)
        sendButton.setTitle("发送", for: .normal)
        sendButton.titleLabel?.font = .systemFont(ofSize: 14, weight: .semibold)
        sendButton.tintColor = .black
        sendButton.backgroundColor = .white
        sendButton.layer.cornerRadius = 15
        sendButton.layer.cornerCurve = .continuous
        sendButton.addTarget(self, action: #selector(sendCommentFromInput), for: .touchUpInside)
        sendButton.translatesAutoresizingMaskIntoConstraints = false

        panel.addSubview(grabber)
        panel.addSubview(titleLabel)
        panel.addSubview(closeButton)
        panel.addSubview(commentsStack)
        panel.addSubview(inputContainer)
        inputContainer.addSubview(inputField)
        inputContainer.addSubview(sendButton)

        commentPanelBottomConstraint = panel.bottomAnchor.constraint(equalTo: view.bottomAnchor, constant: 360)
        NSLayoutConstraint.activate([
            overlay.topAnchor.constraint(equalTo: view.topAnchor),
            overlay.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            overlay.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            overlay.bottomAnchor.constraint(equalTo: view.bottomAnchor),

            panel.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            panel.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            panel.heightAnchor.constraint(equalTo: view.heightAnchor, multiplier: 0.44),
            commentPanelBottomConstraint!,

            grabber.topAnchor.constraint(equalTo: panel.topAnchor, constant: 10),
            grabber.centerXAnchor.constraint(equalTo: panel.centerXAnchor),
            grabber.widthAnchor.constraint(equalToConstant: 38),
            grabber.heightAnchor.constraint(equalToConstant: 4),

            titleLabel.topAnchor.constraint(equalTo: panel.topAnchor, constant: 24),
            titleLabel.leadingAnchor.constraint(equalTo: panel.leadingAnchor, constant: 20),

            closeButton.trailingAnchor.constraint(equalTo: panel.trailingAnchor, constant: -18),
            closeButton.centerYAnchor.constraint(equalTo: titleLabel.centerYAnchor),
            closeButton.widthAnchor.constraint(equalToConstant: 32),
            closeButton.heightAnchor.constraint(equalToConstant: 32),

            commentsStack.topAnchor.constraint(equalTo: titleLabel.bottomAnchor, constant: 20),
            commentsStack.leadingAnchor.constraint(equalTo: panel.leadingAnchor, constant: 20),
            commentsStack.trailingAnchor.constraint(equalTo: panel.trailingAnchor, constant: -20),
            commentsStack.bottomAnchor.constraint(lessThanOrEqualTo: inputContainer.topAnchor, constant: -18),

            inputContainer.leadingAnchor.constraint(equalTo: panel.leadingAnchor, constant: 16),
            inputContainer.trailingAnchor.constraint(equalTo: panel.trailingAnchor, constant: -16),
            inputContainer.bottomAnchor.constraint(equalTo: panel.safeAreaLayoutGuide.bottomAnchor, constant: -12),
            inputContainer.heightAnchor.constraint(equalToConstant: 44),

            inputField.leadingAnchor.constraint(equalTo: inputContainer.leadingAnchor, constant: 14),
            inputField.centerYAnchor.constraint(equalTo: inputContainer.centerYAnchor),
            inputField.trailingAnchor.constraint(equalTo: sendButton.leadingAnchor, constant: -10),

            sendButton.trailingAnchor.constraint(equalTo: inputContainer.trailingAnchor, constant: -7),
            sendButton.centerYAnchor.constraint(equalTo: inputContainer.centerYAnchor),
            sendButton.widthAnchor.constraint(equalToConstant: 58),
            sendButton.heightAnchor.constraint(equalToConstant: 30)
        ])

        commentOverlayView = overlay
        commentPanelView = panel
        commentInputField = inputField
        view.layoutIfNeeded()

        commentPanelBottomConstraint?.constant = 0
        UIView.animate(withDuration: 0.24, delay: 0, options: [.curveEaseOut]) {
            overlay.alpha = 1
            self.view.layoutIfNeeded()
        } completion: { _ in
            inputField.becomeFirstResponder()
        }
    }

    func makeCommentRow(text: String) -> UIView {
        let label = UILabel()
        label.text = text
        label.font = .systemFont(ofSize: 14, weight: .regular)
        label.textColor = UIColor.white.withAlphaComponent(0.86)
        label.numberOfLines = 0
        return label
    }

    @objc private func sendCommentFromInput() {
        let text = commentInputField?.text?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        guard !text.isEmpty else { return }
        commentRows.append("我：\(text)")
        commentCount += 1
        commentPreviewLabel.text = "我：\(text)"
        updateActionButtons()
        onShowNotice?("评论已发送")
        dismissCommentPanel(animated: true)
    }

    @objc private func dismissCommentPanelFromOverlay() {
        dismissCommentPanel(animated: true)
    }

    func dismissCommentPanel(animated: Bool) {
        commentInputField?.resignFirstResponder()
        guard let overlay = commentOverlayView, let panel = commentPanelView else { return }
        let updates = {
            overlay.alpha = 0
            self.commentPanelBottomConstraint?.constant = panel.bounds.height + 24
            self.view.layoutIfNeeded()
        }
        let completion: (Bool) -> Void = { _ in
            overlay.removeFromSuperview()
            panel.removeFromSuperview()
            self.commentOverlayView = nil
            self.commentPanelView = nil
            self.commentInputField = nil
            self.commentPanelBottomConstraint = nil
        }
        if animated {
            UIView.animate(withDuration: 0.20, delay: 0, options: [.curveEaseIn], animations: updates, completion: completion)
        } else {
            completion(true)
        }
    }

    func registerCommentKeyboardObservers() {
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(handleCommentKeyboardFrame(_:)),
            name: UIResponder.keyboardWillChangeFrameNotification,
            object: nil
        )
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(handleCommentKeyboardFrame(_:)),
            name: UIResponder.keyboardWillHideNotification,
            object: nil
        )
    }

    @objc private func handleCommentKeyboardFrame(_ notification: Notification) {
        guard commentPanelView != nil else { return }
        let keyboardFrame = (notification.userInfo?[UIResponder.keyboardFrameEndUserInfoKey] as? NSValue)?.cgRectValue ?? .zero
        let duration = (notification.userInfo?[UIResponder.keyboardAnimationDurationUserInfoKey] as? NSNumber)?.doubleValue ?? 0.25
        let converted = view.convert(keyboardFrame, from: nil)
        let overlap = max(0, view.bounds.maxY - converted.minY)
        commentPanelBottomConstraint?.constant = notification.name == UIResponder.keyboardWillHideNotification ? 0 : -overlap
        UIView.animate(withDuration: duration) {
            self.view.layoutIfNeeded()
        }
    }

    @objc private func close() {
        navigationController?.popViewController(animated: true)
    }
}

final class ChannelsAuthorHomeViewController: UIViewController, UICollectionViewDataSource, UICollectionViewDelegateFlowLayout {
    var onFollowChanged: ((Bool) -> Void)?

    let authorName: String
    var isFollowed: Bool
    let collectionView: UICollectionView
    let followButton = UIButton(type: .system)

    init(authorName: String, followed: Bool) {
        self.authorName = authorName
        self.isFollowed = followed
        let layout = UICollectionViewFlowLayout()
        layout.minimumInteritemSpacing = 2
        layout.minimumLineSpacing = 2
        collectionView = UICollectionView(frame: .zero, collectionViewLayout: layout)
        super.init(nibName: nil, bundle: nil)
        title = "视频号主页"
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .black
        navigationItem.leftBarButtonItem = UIBarButtonItem(title: "返回", style: .plain, target: self, action: #selector(close))
        configureHeader()
        configureCollection()
        updateFollowButton()
    }

    func configureHeader() {
        let header = UIView()
        header.backgroundColor = .black
        header.translatesAutoresizingMaskIntoConstraints = false

        let avatar = UILabel()
        avatar.text = String(authorName.prefix(1)).uppercased()
        avatar.textAlignment = .center
        avatar.textColor = .white
        avatar.font = .systemFont(ofSize: 28, weight: .bold)
        avatar.backgroundColor = UIColor(red: 0.95, green: 0.42, blue: 0.16, alpha: 1)
        avatar.layer.cornerRadius = 34
        avatar.layer.cornerCurve = .continuous
        avatar.clipsToBounds = true
        avatar.translatesAutoresizingMaskIntoConstraints = false

        let nameLabel = UILabel()
        nameLabel.text = authorName
        nameLabel.font = .systemFont(ofSize: 22, weight: .bold)
        nameLabel.textColor = .white
        nameLabel.translatesAutoresizingMaskIntoConstraints = false

        let metaLabel = UILabel()
        metaLabel.text = "视频号 · 粉丝 8.6万 · 作品 128 · 直播 12"
        metaLabel.font = .systemFont(ofSize: 13, weight: .medium)
        metaLabel.textColor = UIColor.white.withAlphaComponent(0.68)
        metaLabel.translatesAutoresizingMaskIntoConstraints = false

        let bioLabel = UILabel()
        bioLabel.text = "记录门店活动、客户现场和品牌内容。这里是本地模拟的视频号个人主页。"
        bioLabel.font = .systemFont(ofSize: 13)
        bioLabel.textColor = UIColor.white.withAlphaComponent(0.82)
        bioLabel.numberOfLines = 2
        bioLabel.translatesAutoresizingMaskIntoConstraints = false

        followButton.addTarget(self, action: #selector(toggleFollow), for: .touchUpInside)
        followButton.translatesAutoresizingMaskIntoConstraints = false

        [avatar, nameLabel, metaLabel, bioLabel, followButton].forEach(header.addSubview)
        view.addSubview(header)
        NSLayoutConstraint.activate([
            header.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor),
            header.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            header.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            header.heightAnchor.constraint(equalToConstant: 190),

            avatar.leadingAnchor.constraint(equalTo: header.leadingAnchor, constant: 18),
            avatar.topAnchor.constraint(equalTo: header.topAnchor, constant: 18),
            avatar.widthAnchor.constraint(equalToConstant: 68),
            avatar.heightAnchor.constraint(equalToConstant: 68),

            nameLabel.leadingAnchor.constraint(equalTo: avatar.trailingAnchor, constant: 14),
            nameLabel.topAnchor.constraint(equalTo: avatar.topAnchor, constant: 4),
            nameLabel.trailingAnchor.constraint(equalTo: followButton.leadingAnchor, constant: -12),

            metaLabel.leadingAnchor.constraint(equalTo: nameLabel.leadingAnchor),
            metaLabel.topAnchor.constraint(equalTo: nameLabel.bottomAnchor, constant: 6),
            metaLabel.trailingAnchor.constraint(equalTo: header.trailingAnchor, constant: -18),

            followButton.trailingAnchor.constraint(equalTo: header.trailingAnchor, constant: -18),
            followButton.centerYAnchor.constraint(equalTo: avatar.centerYAnchor),
            followButton.widthAnchor.constraint(equalToConstant: 82),
            followButton.heightAnchor.constraint(equalToConstant: 34),

            bioLabel.leadingAnchor.constraint(equalTo: header.leadingAnchor, constant: 18),
            bioLabel.trailingAnchor.constraint(equalTo: header.trailingAnchor, constant: -18),
            bioLabel.topAnchor.constraint(equalTo: avatar.bottomAnchor, constant: 18)
        ])
    }

    func configureCollection() {
        collectionView.backgroundColor = .black
        collectionView.dataSource = self
        collectionView.delegate = self
        collectionView.register(ChannelsAuthorWorkCell.self, forCellWithReuseIdentifier: ChannelsAuthorWorkCell.reuseIdentifier)
        collectionView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(collectionView)
        NSLayoutConstraint.activate([
            collectionView.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 190),
            collectionView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            collectionView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            collectionView.bottomAnchor.constraint(equalTo: view.bottomAnchor)
        ])
    }

    func updateFollowButton() {
        followButton.setTitle(isFollowed ? "已关注" : "关注", for: .normal)
        followButton.titleLabel?.font = .systemFont(ofSize: 14, weight: .bold)
        followButton.tintColor = isFollowed ? .white : .black
        followButton.backgroundColor = isFollowed ? UIColor.white.withAlphaComponent(0.18) : .white
        followButton.layer.cornerRadius = 17
        followButton.layer.cornerCurve = .continuous
    }

    func collectionView(_ collectionView: UICollectionView, numberOfItemsInSection section: Int) -> Int { 12 }
    func collectionView(_ collectionView: UICollectionView, cellForItemAt indexPath: IndexPath) -> UICollectionViewCell {
        let cell = collectionView.dequeueReusableCell(withReuseIdentifier: ChannelsAuthorWorkCell.reuseIdentifier, for: indexPath) as? ChannelsAuthorWorkCell
            ?? ChannelsAuthorWorkCell()
        cell.configure(index: indexPath.item)
        return cell
    }
    func collectionView(_ collectionView: UICollectionView, layout collectionViewLayout: UICollectionViewLayout, sizeForItemAt indexPath: IndexPath) -> CGSize {
        let width = floor((collectionView.bounds.width - 4) / 3)
        return CGSize(width: width, height: width * 1.45)
    }
    func collectionView(_ collectionView: UICollectionView, didSelectItemAt indexPath: IndexPath) {
        let alert = UIAlertController(title: "视频号作品", message: "打开第 \(indexPath.item + 1) 条作品，模拟进入视频流播放。", preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: "确定", style: .default))
        present(alert, animated: true)
    }
    @objc private func toggleFollow() {
        isFollowed.toggle()
        updateFollowButton()
        onFollowChanged?(isFollowed)
    }
    @objc private func close() {
        navigationController?.popViewController(animated: true)
    }
}

final class ChannelsAuthorWorkCell: UICollectionViewCell {
    static let reuseIdentifier = "ChannelsAuthorWorkCell"
    let titleLabel = UILabel()
    let playIcon = UIImageView(image: UIImage(systemName: "play.fill"))

    override init(frame: CGRect) {
        super.init(frame: frame)
        contentView.backgroundColor = UIColor(red: 0.12, green: 0.12, blue: 0.14, alpha: 1)
        playIcon.tintColor = .white
        playIcon.translatesAutoresizingMaskIntoConstraints = false
        titleLabel.font = .systemFont(ofSize: 11, weight: .semibold)
        titleLabel.textColor = .white
        titleLabel.numberOfLines = 2
        titleLabel.translatesAutoresizingMaskIntoConstraints = false
        contentView.addSubview(playIcon)
        contentView.addSubview(titleLabel)
        NSLayoutConstraint.activate([
            playIcon.centerXAnchor.constraint(equalTo: contentView.centerXAnchor),
            playIcon.centerYAnchor.constraint(equalTo: contentView.centerYAnchor),
            playIcon.widthAnchor.constraint(equalToConstant: 24),
            playIcon.heightAnchor.constraint(equalToConstant: 24),
            titleLabel.leadingAnchor.constraint(equalTo: contentView.leadingAnchor, constant: 8),
            titleLabel.trailingAnchor.constraint(equalTo: contentView.trailingAnchor, constant: -8),
            titleLabel.bottomAnchor.constraint(equalTo: contentView.bottomAnchor, constant: -8)
        ])
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    func configure(index: Int) {
        titleLabel.text = index.isMultiple(of: 3) ? "直播回放 \(index + 1)" : "门店短视频 \(index + 1)"
        contentView.backgroundColor = [
            UIColor(red: 0.18, green: 0.12, blue: 0.22, alpha: 1),
            UIColor(red: 0.12, green: 0.18, blue: 0.22, alpha: 1),
            UIColor(red: 0.22, green: 0.16, blue: 0.12, alpha: 1)
        ][index % 3]
    }
}


struct OpenApiQRCodePayload {
    var qrContent = ""
    var qrURLText = ""
    var imageURL: URL?

    var hasDisplayableValue: Bool {
        imageURL != nil || !qrContent.isEmpty || !qrURLText.isEmpty
    }
}

final class TiltQRCodeViewController: UIViewController {
    let titleText: String
    let subtitleText: String
    let payload: String
    let copyPayloadText: String
    let imageURL: URL?
    let hintText: String
    var imageLoadTask: Task<Void, Never>?

    init(
        titleText: String,
        subtitleText: String,
        payload: String,
        copyPayload: String? = nil,
        imageURL: URL? = nil,
        hintText: String = "前后倾斜手机可快速打开此二维码"
    ) {
        self.titleText = titleText
        self.subtitleText = subtitleText
        self.payload = payload
        self.copyPayloadText = copyPayload ?? payload
        self.imageURL = imageURL
        self.hintText = hintText
        super.init(nibName: nil, bundle: nil)
        title = "二维码"
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    deinit {
        imageLoadTask?.cancel()
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = UIColor(red: 0.94, green: 0.97, blue: 0.98, alpha: 1)
        navigationItem.leftBarButtonItem = UIBarButtonItem(title: "返回", style: .plain, target: self, action: #selector(close))
        navigationItem.rightBarButtonItem = UIBarButtonItem(title: "复制", style: .done, target: self, action: #selector(copyPayload))
        configure()
    }

    func configure() {
        let container = UIView()
        container.backgroundColor = .white
        container.layer.cornerRadius = 22
        container.layer.cornerCurve = .continuous
        container.layer.shadowColor = UIColor.black.withAlphaComponent(0.12).cgColor
        container.layer.shadowOpacity = 1
        container.layer.shadowRadius = 18
        container.layer.shadowOffset = CGSize(width: 0, height: 10)
        container.translatesAutoresizingMaskIntoConstraints = false

        let titleLabel = UILabel()
        titleLabel.text = titleText
        titleLabel.font = .systemFont(ofSize: 24, weight: .bold)
        titleLabel.textColor = UIColor(red: 0.10, green: 0.15, blue: 0.18, alpha: 1)
        titleLabel.textAlignment = .center
        titleLabel.numberOfLines = 2
        titleLabel.translatesAutoresizingMaskIntoConstraints = false

        let subtitleLabel = UILabel()
        subtitleLabel.text = subtitleText
        subtitleLabel.font = .systemFont(ofSize: 14, weight: .medium)
        subtitleLabel.textColor = UIColor(red: 0.33, green: 0.43, blue: 0.47, alpha: 1)
        subtitleLabel.textAlignment = .center
        subtitleLabel.translatesAutoresizingMaskIntoConstraints = false

        let qrImageView = UIImageView(image: imageURL == nil ? qrImage(from: payload) : UIImage(systemName: "qrcode.viewfinder"))
        qrImageView.contentMode = .scaleAspectFit
        qrImageView.backgroundColor = .white
        qrImageView.tintColor = UIColor(red: 0.16, green: 0.22, blue: 0.24, alpha: 1)
        qrImageView.layer.cornerRadius = 14
        qrImageView.clipsToBounds = true
        qrImageView.translatesAutoresizingMaskIntoConstraints = false

        let payloadLabel = UILabel()
        payloadLabel.text = imageURL?.absoluteString ?? payload
        payloadLabel.font = .monospacedSystemFont(ofSize: 11, weight: .regular)
        payloadLabel.textColor = UIColor(red: 0.38, green: 0.45, blue: 0.48, alpha: 1)
        payloadLabel.textAlignment = .center
        payloadLabel.numberOfLines = 3
        payloadLabel.lineBreakMode = .byTruncatingMiddle
        payloadLabel.translatesAutoresizingMaskIntoConstraints = false

        let hintLabel = UILabel()
        hintLabel.text = hintText
        hintLabel.font = .systemFont(ofSize: 13, weight: .semibold)
        hintLabel.textColor = UIColor(red: 0.12, green: 0.45, blue: 0.42, alpha: 1)
        hintLabel.textAlignment = .center
        hintLabel.translatesAutoresizingMaskIntoConstraints = false

        view.addSubview(container)
        [titleLabel, subtitleLabel, qrImageView, payloadLabel, hintLabel].forEach(container.addSubview)

        NSLayoutConstraint.activate([
            container.centerYAnchor.constraint(equalTo: view.centerYAnchor),
            container.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 28),
            container.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -28),

            titleLabel.topAnchor.constraint(equalTo: container.topAnchor, constant: 28),
            titleLabel.leadingAnchor.constraint(equalTo: container.leadingAnchor, constant: 22),
            titleLabel.trailingAnchor.constraint(equalTo: container.trailingAnchor, constant: -22),

            subtitleLabel.topAnchor.constraint(equalTo: titleLabel.bottomAnchor, constant: 8),
            subtitleLabel.leadingAnchor.constraint(equalTo: titleLabel.leadingAnchor),
            subtitleLabel.trailingAnchor.constraint(equalTo: titleLabel.trailingAnchor),

            qrImageView.topAnchor.constraint(equalTo: subtitleLabel.bottomAnchor, constant: 24),
            qrImageView.centerXAnchor.constraint(equalTo: container.centerXAnchor),
            qrImageView.widthAnchor.constraint(equalTo: container.widthAnchor, multiplier: 0.68),
            qrImageView.heightAnchor.constraint(equalTo: qrImageView.widthAnchor),

            payloadLabel.topAnchor.constraint(equalTo: qrImageView.bottomAnchor, constant: 20),
            payloadLabel.leadingAnchor.constraint(equalTo: titleLabel.leadingAnchor),
            payloadLabel.trailingAnchor.constraint(equalTo: titleLabel.trailingAnchor),

            hintLabel.topAnchor.constraint(equalTo: payloadLabel.bottomAnchor, constant: 18),
            hintLabel.leadingAnchor.constraint(equalTo: titleLabel.leadingAnchor),
            hintLabel.trailingAnchor.constraint(equalTo: titleLabel.trailingAnchor),
            hintLabel.bottomAnchor.constraint(equalTo: container.bottomAnchor, constant: -28)
        ])

        if let imageURL {
            loadQRCodeImage(from: imageURL, into: qrImageView, fallbackPayload: payload, statusLabel: subtitleLabel)
        }
    }

    func loadQRCodeImage(from url: URL, into imageView: UIImageView, fallbackPayload: String, statusLabel: UILabel) {
        imageLoadTask?.cancel()
        imageLoadTask = Task { [weak self, weak imageView, weak statusLabel] in
            do {
                let (data, response) = try await URLSession.shared.data(from: url)
                guard !Task.isCancelled else { return }
                guard let http = response as? HTTPURLResponse,
                      200..<300 ~= http.statusCode,
                      let image = UIImage(data: data)
                else {
                    await MainActor.run {
                        statusLabel?.text = "二维码图片加载失败，已改用二维码内容生成"
                        imageView?.image = self?.qrImage(from: fallbackPayload)
                    }
                    return
                }
                await MainActor.run {
                    imageView?.image = image
                }
            } catch {
                await MainActor.run {
                    statusLabel?.text = "二维码图片加载失败，已改用二维码内容生成"
                    imageView?.image = self?.qrImage(from: fallbackPayload)
                }
            }
        }
    }

    func qrImage(from string: String) -> UIImage? {
        guard let data = string.data(using: .utf8),
              let filter = CIFilter(name: "CIQRCodeGenerator")
        else { return nil }
        filter.setValue(data, forKey: "inputMessage")
        filter.setValue("M", forKey: "inputCorrectionLevel")
        guard let outputImage = filter.outputImage else { return nil }
        let transform = CGAffineTransform(scaleX: 12, y: 12)
        let scaledImage = outputImage.transformed(by: transform)
        return UIImage(ciImage: scaledImage)
    }

    @objc private func close() {
        dismiss(animated: true)
    }

    @objc private func copyPayload() {
        UIPasteboard.general.string = copyPayloadText
    }
}





struct GroupInviteDraft {
    let groupName: String
    let remark: String
    let activity: String
    let members: [ChatParticipant]
    let groupNicknames: [ChatParticipant: String]
}

final class GroupInviteComposerViewController: UIViewController {
    var onSend: ((GroupInviteDraft) -> Void)?

    let accounts: [ChatParticipant]
    var selectedIDs: Set<UUID>
    var nicknameFields: [UUID: UITextField] = [:]
    let groupNameField = UITextField()
    let remarkField = UITextField()
    let activityField = UITextField()
    let tableView = UITableView(frame: .zero, style: .insetGrouped)
    let sendButton = UIButton(type: .system)

    init(accounts: [ChatParticipant], currentAccountID: UUID) {
        self.accounts = accounts
        self.selectedIDs = Set(accounts.prefix(3).map(\.id))
        if self.selectedIDs.isEmpty {
            self.selectedIDs.insert(currentAccountID)
        }
        super.init(nibName: nil, bundle: nil)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        title = "群邀请卡"
        view.backgroundColor = UIColor.systemGroupedBackground
        navigationItem.leftBarButtonItem = UIBarButtonItem(title: "返回", style: .plain, target: self, action: #selector(goBack))
        configureLayout()
        updateSendButton()
    }

    func configureLayout() {
        let header = UIStackView()
        header.axis = .vertical
        header.spacing = 10
        header.layoutMargins = UIEdgeInsets(top: 14, left: 16, bottom: 12, right: 16)
        header.isLayoutMarginsRelativeArrangement = true

        configureTextField(groupNameField, placeholder: "群名", text: "周末活动执行群")
        configureTextField(remarkField, placeholder: "备注", text: "现场执行沟通")
        configureTextField(activityField, placeholder: "活动", text: "周末商场开业活动")
        [groupNameField, remarkField, activityField].forEach { header.addArrangedSubview($0) }
        tableView.tableHeaderView = header
        header.frame = CGRect(x: 0, y: 0, width: view.bounds.width, height: 194)

        tableView.dataSource = self
        tableView.delegate = self
        tableView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(tableView)

        sendButton.setTitle("发送群邀请卡", for: .normal)
        sendButton.titleLabel?.font = .systemFont(ofSize: 16, weight: .semibold)
        sendButton.tintColor = .white
        sendButton.backgroundColor = UIColor.systemGreen
        sendButton.layer.cornerRadius = 22
        sendButton.layer.cornerCurve = .continuous
        sendButton.addTarget(self, action: #selector(send), for: .touchUpInside)
        sendButton.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(sendButton)

        NSLayoutConstraint.activate([
            tableView.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor),
            tableView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            tableView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            tableView.bottomAnchor.constraint(equalTo: sendButton.topAnchor, constant: -10),
            sendButton.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 18),
            sendButton.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -18),
            sendButton.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor, constant: -12),
            sendButton.heightAnchor.constraint(equalToConstant: 46)
        ])
    }

    func configureTextField(_ field: UITextField, placeholder: String, text: String) {
        field.placeholder = placeholder
        field.text = text
        field.backgroundColor = UIColor.secondarySystemGroupedBackground
        field.layer.cornerRadius = 12
        field.layer.cornerCurve = .continuous
        field.font = .systemFont(ofSize: 15)
        field.leftView = UIView(frame: CGRect(x: 0, y: 0, width: 12, height: 1))
        field.leftViewMode = .always
        field.heightAnchor.constraint(equalToConstant: 44).isActive = true
    }

    func updateSendButton() {
        let count = selectedIDs.count
        sendButton.setTitle("发送群邀请卡（已选 \(count)/3）", for: .normal)
        sendButton.isEnabled = count == 3
        sendButton.alpha = count == 3 ? 1 : 0.45
    }

    @objc private func send() {
        view.endEditing(true)
        let selectedMembers = accounts.filter { selectedIDs.contains($0.id) }
        guard selectedMembers.count == 3 else { return }
        let nicknames = selectedMembers.reduce(into: [ChatParticipant: String]()) { result, account in
            result[account] = nicknameFields[account.id]?.text?.trimmingCharacters(in: .whitespacesAndNewlines) ?? account.displayName
        }
        onSend?(GroupInviteDraft(
            groupName: groupNameField.text ?? "",
            remark: remarkField.text ?? "",
            activity: activityField.text ?? "",
            members: selectedMembers,
            groupNicknames: nicknames
        ))
    }

    @objc private func goBack() {
        navigationController?.popViewController(animated: true)
    }
}

extension GroupInviteComposerViewController: UITableViewDataSource, UITableViewDelegate {
    func numberOfSections(in tableView: UITableView) -> Int { 1 }

    func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        accounts.count
    }

    func tableView(_ tableView: UITableView, titleForHeaderInSection section: Int) -> String? {
        "选择右侧帐号中的 3 个成员，并设置群中的名字"
    }

    func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let identifier = "GroupInviteAccountCell"
        let cell = tableView.dequeueReusableCell(withIdentifier: identifier) ?? UITableViewCell(style: .subtitle, reuseIdentifier: identifier)
        let account = accounts[indexPath.row]
        cell.textLabel?.text = account.displayName
        cell.detailTextLabel?.text = "右侧帐号 · 点击选择/取消"
        cell.imageView?.image = UIImage(systemName: "person.crop.circle.fill")
        cell.imageView?.tintColor = account.tintColor
        cell.accessoryType = selectedIDs.contains(account.id) ? .checkmark : .none

        let field = nicknameFields[account.id] ?? UITextField()
        field.placeholder = "群中的名字"
        field.text = field.text?.isEmpty == false ? field.text : account.displayName
        field.font = .systemFont(ofSize: 13)
        field.textAlignment = .right
        field.frame = CGRect(x: 0, y: 0, width: 120, height: 34)
        field.borderStyle = .roundedRect
        nicknameFields[account.id] = field
        cell.accessoryView = field
        return cell
    }

    func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
        tableView.deselectRow(at: indexPath, animated: true)
        let account = accounts[indexPath.row]
        if selectedIDs.contains(account.id) {
            selectedIDs.remove(account.id)
        } else if selectedIDs.count < 3 {
            selectedIDs.insert(account.id)
        }
        updateSendButton()
        tableView.reloadRows(at: [indexPath], with: .none)
    }
}



final class CodexAssistantViewController: UIViewController, UITableViewDataSource, UITableViewDelegate, UITextFieldDelegate {
    struct Message {
        let isUser: Bool
        var text: String
    }

    let assistantTitle: String
    let modelName: String
    let introText: String
    let thinkingText: String
    let emptyText: String
    let failureText: String
    let placeholder: String
    let streamHandler: (String, @escaping (String) -> Void, @escaping (Result<Void, Error>) -> Void) -> Void
    let tableView = UITableView(frame: .zero, style: .plain)
    let inputBar = UIView()
    let textField = UITextField()
    let sendButton = UIButton(type: .system)
    var inputBottomConstraint: NSLayoutConstraint?
    var messages: [Message] = []

    init(
        assistantTitle: String = "Codex",
        modelName: String,
        introText: String = "我是 Codex，可以直接问我问题。",
        thinkingText: String = "Codex 正在思考...",
        emptyText: String = "Codex 未返回内容。",
        failureText: String = "Codex 生成失败。",
        placeholder: String = "问 Codex...",
        streamHandler: @escaping (String, @escaping (String) -> Void, @escaping (Result<Void, Error>) -> Void) -> Void
    ) {
        self.assistantTitle = assistantTitle
        self.modelName = modelName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? "gpt-5.5" : modelName
        self.introText = introText
        self.thinkingText = thinkingText
        self.emptyText = emptyText
        self.failureText = failureText
        self.placeholder = placeholder
        self.streamHandler = streamHandler
        self.messages = [Message(isUser: false, text: introText)]
        super.init(nibName: nil, bundle: nil)
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        title = assistantTitle
        view.backgroundColor = UIColor(white: 0.95, alpha: 1)
        navigationItem.leftBarButtonItem = UIBarButtonItem(title: "返回", style: .plain, target: self, action: #selector(closePage))
        configureTable()
        configureInputBar()
        NotificationCenter.default.addObserver(self, selector: #selector(handleKeyboard(_:)), name: UIResponder.keyboardWillChangeFrameNotification, object: nil)
        NotificationCenter.default.addObserver(self, selector: #selector(handleKeyboard(_:)), name: UIResponder.keyboardWillHideNotification, object: nil)
    }

    func configureTable() {
        tableView.backgroundColor = .clear
        tableView.separatorStyle = .none
        tableView.keyboardDismissMode = .interactive
        tableView.dataSource = self
        tableView.delegate = self
        tableView.register(CodexAssistantMessageCell.self, forCellReuseIdentifier: CodexAssistantMessageCell.reuseIdentifier)
        tableView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(tableView)
    }

    func configureInputBar() {
        inputBar.backgroundColor = UIColor(white: 0.98, alpha: 0.98)
        inputBar.layer.borderWidth = 1
        inputBar.layer.borderColor = UIColor.black.withAlphaComponent(0.08).cgColor
        inputBar.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(inputBar)

        textField.placeholder = placeholder
        textField.backgroundColor = .white
        textField.layer.cornerRadius = 18
        textField.layer.cornerCurve = .continuous
        textField.layer.borderWidth = 1
        textField.layer.borderColor = UIColor.black.withAlphaComponent(0.08).cgColor
        textField.leftView = UIView(frame: CGRect(x: 0, y: 0, width: 12, height: 1))
        textField.leftViewMode = .always
        textField.returnKeyType = .send
        textField.delegate = self
        textField.translatesAutoresizingMaskIntoConstraints = false

        sendButton.setTitle("发送", for: .normal)
        sendButton.titleLabel?.font = .systemFont(ofSize: 15, weight: .semibold)
        sendButton.tintColor = .white
        sendButton.backgroundColor = UIColor(red: 0.34, green: 0.43, blue: 0.95, alpha: 1)
        sendButton.layer.cornerRadius = 17
        sendButton.layer.cornerCurve = .continuous
        sendButton.addTarget(self, action: #selector(sendPrompt), for: .touchUpInside)
        sendButton.translatesAutoresizingMaskIntoConstraints = false

        let modelLabel = UILabel()
        modelLabel.text = "模型 \(modelName)"
        modelLabel.font = .systemFont(ofSize: 11, weight: .medium)
        modelLabel.textColor = UIColor.black.withAlphaComponent(0.42)
        modelLabel.translatesAutoresizingMaskIntoConstraints = false

        inputBar.addSubview(textField)
        inputBar.addSubview(sendButton)
        inputBar.addSubview(modelLabel)
        inputBottomConstraint = inputBar.bottomAnchor.constraint(equalTo: view.bottomAnchor)

        NSLayoutConstraint.activate([
            tableView.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor),
            tableView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            tableView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            tableView.bottomAnchor.constraint(equalTo: inputBar.topAnchor),

            inputBar.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            inputBar.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            inputBottomConstraint!,

            modelLabel.topAnchor.constraint(equalTo: inputBar.topAnchor, constant: 8),
            modelLabel.leadingAnchor.constraint(equalTo: inputBar.leadingAnchor, constant: 16),
            modelLabel.trailingAnchor.constraint(lessThanOrEqualTo: inputBar.trailingAnchor, constant: -16),

            textField.topAnchor.constraint(equalTo: modelLabel.bottomAnchor, constant: 7),
            textField.leadingAnchor.constraint(equalTo: inputBar.leadingAnchor, constant: 12),
            textField.bottomAnchor.constraint(equalTo: inputBar.safeAreaLayoutGuide.bottomAnchor, constant: -10),
            textField.heightAnchor.constraint(equalToConstant: 38),

            sendButton.leadingAnchor.constraint(equalTo: textField.trailingAnchor, constant: 8),
            sendButton.trailingAnchor.constraint(equalTo: inputBar.trailingAnchor, constant: -12),
            sendButton.centerYAnchor.constraint(equalTo: textField.centerYAnchor),
            sendButton.widthAnchor.constraint(equalToConstant: 58),
            sendButton.heightAnchor.constraint(equalToConstant: 34)
        ])
    }

    @objc private func closePage() {
        navigationController?.popViewController(animated: true)
    }

    @objc private func sendPrompt() {
        let prompt = textField.text?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        guard !prompt.isEmpty else { return }
        textField.text = nil
        sendButton.isEnabled = false
        messages.append(Message(isUser: true, text: prompt))
        messages.append(Message(isUser: false, text: thinkingText))
        tableView.reloadData()
        scrollToBottom(animated: true)

        let replyIndex = messages.count - 1
        var streamedText = ""
        streamHandler(prompt, { [weak self] delta in
            DispatchQueue.main.async {
                let signpostID = PerformanceSignpost.begin("StreamingDeltaUpdate")
                defer { PerformanceSignpost.end("StreamingDeltaUpdate", id: signpostID) }
                guard let self else { return }
                streamedText += delta
                self.messages[replyIndex].text = streamedText
                self.tableView.reloadRows(at: [IndexPath(row: replyIndex, section: 0)], with: .none)
                self.scrollToBottom(animated: false)
            }
        }, { [weak self] result in
            DispatchQueue.main.async {
                guard let self else { return }
                self.sendButton.isEnabled = true
                switch result {
                case .success:
                    if streamedText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                        self.messages[replyIndex].text = self.emptyText
                    }
                case .failure:
                    self.messages[replyIndex].text = streamedText.isEmpty ? self.failureText : "\(streamedText)\n\n[生成中断]"
                }
                self.tableView.reloadRows(at: [IndexPath(row: replyIndex, section: 0)], with: .none)
                self.scrollToBottom(animated: true)
            }
        })
    }

    @objc private func handleKeyboard(_ notification: Notification) {
        guard let userInfo = notification.userInfo,
              let frameValue = userInfo[UIResponder.keyboardFrameEndUserInfoKey] as? NSValue
        else { return }
        let keyboardFrame = view.convert(frameValue.cgRectValue, from: nil)
        let overlap = max(0, view.bounds.maxY - keyboardFrame.minY)
        inputBottomConstraint?.constant = -overlap
        let duration = (userInfo[UIResponder.keyboardAnimationDurationUserInfoKey] as? NSNumber)?.doubleValue ?? 0.25
        UIView.animate(withDuration: duration) {
            self.view.layoutIfNeeded()
        }
    }

    func scrollToBottom(animated: Bool) {
        guard !messages.isEmpty else { return }
        tableView.scrollToRow(at: IndexPath(row: messages.count - 1, section: 0), at: .bottom, animated: animated)
    }

    func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        messages.count
    }

    func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell = tableView.dequeueReusableCell(withIdentifier: CodexAssistantMessageCell.reuseIdentifier, for: indexPath) as! CodexAssistantMessageCell
        cell.configure(with: messages[indexPath.row])
        return cell
    }

    func textFieldShouldReturn(_ textField: UITextField) -> Bool {
        sendPrompt()
        return true
    }
}

final class CodexAssistantMessageCell: UITableViewCell {
    static let reuseIdentifier = "CodexAssistantMessageCell"

    let bubbleView = UIView()
    let messageLabel = UILabel()
    var leadingConstraint: NSLayoutConstraint?
    var trailingConstraint: NSLayoutConstraint?

    override init(style: UITableViewCell.CellStyle, reuseIdentifier: String?) {
        super.init(style: style, reuseIdentifier: reuseIdentifier)
        selectionStyle = .none
        backgroundColor = .clear
        contentView.addSubview(bubbleView)
        bubbleView.addSubview(messageLabel)
        bubbleView.layer.cornerRadius = 14
        bubbleView.layer.cornerCurve = .continuous
        bubbleView.translatesAutoresizingMaskIntoConstraints = false
        messageLabel.font = .systemFont(ofSize: 15)
        messageLabel.numberOfLines = 0
        messageLabel.translatesAutoresizingMaskIntoConstraints = false
        leadingConstraint = bubbleView.leadingAnchor.constraint(equalTo: contentView.leadingAnchor, constant: 16)
        trailingConstraint = bubbleView.trailingAnchor.constraint(equalTo: contentView.trailingAnchor, constant: -16)
        NSLayoutConstraint.activate([
            bubbleView.topAnchor.constraint(equalTo: contentView.topAnchor, constant: 6),
            bubbleView.bottomAnchor.constraint(equalTo: contentView.bottomAnchor, constant: -6),
            bubbleView.widthAnchor.constraint(lessThanOrEqualTo: contentView.widthAnchor, multiplier: 0.76),
            messageLabel.topAnchor.constraint(equalTo: bubbleView.topAnchor, constant: 10),
            messageLabel.leadingAnchor.constraint(equalTo: bubbleView.leadingAnchor, constant: 12),
            messageLabel.trailingAnchor.constraint(equalTo: bubbleView.trailingAnchor, constant: -12),
            messageLabel.bottomAnchor.constraint(equalTo: bubbleView.bottomAnchor, constant: -10)
        ])
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    func configure(with message: CodexAssistantViewController.Message) {
        messageLabel.text = message.text
        leadingConstraint?.isActive = !message.isUser
        trailingConstraint?.isActive = message.isUser
        bubbleView.backgroundColor = message.isUser
            ? UIColor(red: 0.34, green: 0.43, blue: 0.95, alpha: 1)
            : UIColor.white
        messageLabel.textColor = message.isUser ? .white : UIColor(red: 0.12, green: 0.14, blue: 0.16, alpha: 1)
    }
}

final class SideEffectConfigurationViewController: UIViewController, UICollectionViewDataSource, UICollectionViewDelegateFlowLayout, UIColorPickerViewControllerDelegate {
    enum ColorTarget {
        case fill
        case stroke
    }

    let templates: [ChatWindowViewController.SideEffectTemplate]
    var selectedIndex: Int
    var fillColor: UIColor
    var strokeColor: UIColor
    let collectionView: UICollectionView
    let fillButton = UIButton(type: .system)
    let strokeButton = UIButton(type: .system)
    var colorTarget: ColorTarget = .fill
    var onChange: ((Int, UIColor, UIColor) -> Void)?

    init(
        templates: [ChatWindowViewController.SideEffectTemplate],
        selectedIndex: Int,
        fillColor: UIColor,
        strokeColor: UIColor
    ) {
        self.templates = templates
        self.selectedIndex = selectedIndex
        self.fillColor = fillColor
        self.strokeColor = strokeColor
        let layout = UICollectionViewFlowLayout()
        layout.minimumLineSpacing = 10
        layout.minimumInteritemSpacing = 10
        collectionView = UICollectionView(frame: .zero, collectionViewLayout: layout)
        super.init(nibName: nil, bundle: nil)
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        title = "侧边特效"
        view.backgroundColor = UIColor(white: 0.96, alpha: 1)
        navigationItem.leftBarButtonItem = UIBarButtonItem(title: "返回", style: .plain, target: self, action: #selector(closePage))

        collectionView.backgroundColor = .clear
        collectionView.dataSource = self
        collectionView.delegate = self
        collectionView.register(SideEffectTemplateCell.self, forCellWithReuseIdentifier: SideEffectTemplateCell.reuseIdentifier)
        collectionView.translatesAutoresizingMaskIntoConstraints = false

        let titleLabel = UILabel()
        titleLabel.text = "选择模板"
        titleLabel.font = .systemFont(ofSize: 18, weight: .semibold)
        titleLabel.translatesAutoresizingMaskIntoConstraints = false

        configureColorButton(fillButton, title: "填充色", color: fillColor)
        configureColorButton(strokeButton, title: "边线色", color: strokeColor)

        view.addSubview(titleLabel)
        view.addSubview(collectionView)
        view.addSubview(fillButton)
        view.addSubview(strokeButton)

        NSLayoutConstraint.activate([
            titleLabel.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 18),
            titleLabel.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 18),
            titleLabel.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -18),

            collectionView.topAnchor.constraint(equalTo: titleLabel.bottomAnchor, constant: 12),
            collectionView.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 14),
            collectionView.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -14),
            collectionView.heightAnchor.constraint(equalToConstant: 300),

            fillButton.topAnchor.constraint(equalTo: collectionView.bottomAnchor, constant: 16),
            fillButton.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 18),
            fillButton.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -18),
            fillButton.heightAnchor.constraint(equalToConstant: 46),

            strokeButton.topAnchor.constraint(equalTo: fillButton.bottomAnchor, constant: 10),
            strokeButton.leadingAnchor.constraint(equalTo: fillButton.leadingAnchor),
            strokeButton.trailingAnchor.constraint(equalTo: fillButton.trailingAnchor),
            strokeButton.heightAnchor.constraint(equalToConstant: 46)
        ])
    }

    func configureColorButton(_ button: UIButton, title: String, color: UIColor) {
        button.contentHorizontalAlignment = .left
        button.backgroundColor = .white
        button.layer.cornerRadius = 10
        button.layer.borderWidth = 1
        button.layer.borderColor = color.withAlphaComponent(0.55).cgColor
        button.setTitle("\(title)  \(color.hexString)", for: .normal)
        button.tintColor = UIColor(red: 0.10, green: 0.14, blue: 0.18, alpha: 1)
        button.applyContentInsets(top: 0, leading: 14, bottom: 0, trailing: 14)
        button.translatesAutoresizingMaskIntoConstraints = false
        button.addTarget(self, action: #selector(pickColor(_:)), for: .touchUpInside)
    }

    @objc private func pickColor(_ sender: UIButton) {
        colorTarget = sender === fillButton ? .fill : .stroke
        let picker = UIColorPickerViewController()
        picker.delegate = self
        picker.selectedColor = colorTarget == .fill ? fillColor : strokeColor
        present(picker, animated: true)
    }

    func colorPickerViewControllerDidSelectColor(_ viewController: UIColorPickerViewController) {
        switch colorTarget {
        case .fill:
            fillColor = viewController.selectedColor
            configureColorButton(fillButton, title: "填充色", color: fillColor)
        case .stroke:
            strokeColor = viewController.selectedColor
            configureColorButton(strokeButton, title: "边线色", color: strokeColor)
        }
        onChange?(selectedIndex, fillColor, strokeColor)
    }

    @objc private func closePage() {
        navigationController?.popViewController(animated: true)
    }

    func collectionView(_ collectionView: UICollectionView, numberOfItemsInSection section: Int) -> Int {
        templates.count
    }

    func collectionView(_ collectionView: UICollectionView, cellForItemAt indexPath: IndexPath) -> UICollectionViewCell {
        let cell = collectionView.dequeueReusableCell(withReuseIdentifier: SideEffectTemplateCell.reuseIdentifier, for: indexPath) as! SideEffectTemplateCell
        let template = templates[indexPath.item]
        cell.configure(template: template, isSelected: template.index == selectedIndex, fillColor: fillColor, strokeColor: strokeColor)
        return cell
    }

    func collectionView(_ collectionView: UICollectionView, didSelectItemAt indexPath: IndexPath) {
        selectedIndex = templates[indexPath.item].index
        collectionView.reloadData()
        onChange?(selectedIndex, fillColor, strokeColor)
    }

    func collectionView(_ collectionView: UICollectionView, layout collectionViewLayout: UICollectionViewLayout, sizeForItemAt indexPath: IndexPath) -> CGSize {
        let width = floor((collectionView.bounds.width - 10) / 2)
        return CGSize(width: width, height: 90)
    }
}

final class SideEffectTemplateCell: UICollectionViewCell {
    static let reuseIdentifier = "SideEffectTemplateCell"

    let iconView = UIImageView()
    let titleLabel = UILabel()
    let subtitleLabel = UILabel()
    let swatchView = UIView()

    override init(frame: CGRect) {
        super.init(frame: frame)
        contentView.backgroundColor = .white
        contentView.layer.cornerRadius = 12
        contentView.layer.borderWidth = 1
        contentView.layer.cornerCurve = .continuous

        iconView.contentMode = .scaleAspectFit
        iconView.translatesAutoresizingMaskIntoConstraints = false
        titleLabel.font = .systemFont(ofSize: 14, weight: .semibold)
        titleLabel.translatesAutoresizingMaskIntoConstraints = false
        subtitleLabel.font = .systemFont(ofSize: 11)
        subtitleLabel.textColor = UIColor.black.withAlphaComponent(0.50)
        subtitleLabel.numberOfLines = 2
        subtitleLabel.translatesAutoresizingMaskIntoConstraints = false
        swatchView.layer.cornerRadius = 7
        swatchView.layer.cornerCurve = .continuous
        swatchView.translatesAutoresizingMaskIntoConstraints = false

        contentView.addSubview(iconView)
        contentView.addSubview(titleLabel)
        contentView.addSubview(subtitleLabel)
        contentView.addSubview(swatchView)

        NSLayoutConstraint.activate([
            iconView.topAnchor.constraint(equalTo: contentView.topAnchor, constant: 12),
            iconView.leadingAnchor.constraint(equalTo: contentView.leadingAnchor, constant: 12),
            iconView.widthAnchor.constraint(equalToConstant: 24),
            iconView.heightAnchor.constraint(equalToConstant: 24),
            swatchView.trailingAnchor.constraint(equalTo: contentView.trailingAnchor, constant: -12),
            swatchView.centerYAnchor.constraint(equalTo: iconView.centerYAnchor),
            swatchView.widthAnchor.constraint(equalToConstant: 28),
            swatchView.heightAnchor.constraint(equalToConstant: 14),
            titleLabel.topAnchor.constraint(equalTo: iconView.bottomAnchor, constant: 8),
            titleLabel.leadingAnchor.constraint(equalTo: contentView.leadingAnchor, constant: 12),
            titleLabel.trailingAnchor.constraint(equalTo: contentView.trailingAnchor, constant: -12),
            subtitleLabel.topAnchor.constraint(equalTo: titleLabel.bottomAnchor, constant: 3),
            subtitleLabel.leadingAnchor.constraint(equalTo: titleLabel.leadingAnchor),
            subtitleLabel.trailingAnchor.constraint(equalTo: titleLabel.trailingAnchor)
        ])
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    func configure(template: ChatWindowViewController.SideEffectTemplate, isSelected: Bool, fillColor: UIColor, strokeColor: UIColor) {
        iconView.image = UIImage(systemName: template.symbolName)
        iconView.tintColor = strokeColor
        titleLabel.text = template.title
        subtitleLabel.text = template.subtitle
        swatchView.backgroundColor = fillColor
        swatchView.layer.borderWidth = 1
        swatchView.layer.borderColor = strokeColor.cgColor
        contentView.layer.borderColor = (isSelected ? strokeColor : UIColor.black.withAlphaComponent(0.08)).cgColor
        contentView.layer.borderWidth = isSelected ? 2 : 1
    }
}





final class AppKitModulesViewController: UITableViewController {
    let rows = AppKitRegistry.shared.manifest.rows

    override func viewDidLoad() {
        super.viewDidLoad()
        title = "模块化"
        tableView = UITableView(frame: .zero, style: .insetGrouped)
        tableView.register(OpenApiFieldCell.self, forCellReuseIdentifier: OpenApiFieldCell.reuseIdentifier)
        tableView.backgroundColor = UIColor(red: 0.95, green: 0.96, blue: 0.97, alpha: 1)
        showCircularPageLoading(title: "加载模块清单")
    }

    override func numberOfSections(in tableView: UITableView) -> Int { 2 }

    override func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        section == 0 ? rows.count : 4
    }

    override func tableView(_ tableView: UITableView, titleForHeaderInSection section: Int) -> String? {
        section == 0 ? "可复用 Kit 边界" : "迁移原则"
    }

    override func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell = tableView.dequeueReusableCell(withIdentifier: OpenApiFieldCell.reuseIdentifier, for: indexPath) as? OpenApiFieldCell
            ?? OpenApiFieldCell(style: .default, reuseIdentifier: OpenApiFieldCell.reuseIdentifier)
        if indexPath.section == 0 {
            let row = rows[indexPath.row]
            cell.configure(OpenApiFieldRow(key: row.owner, value: row.title, subtitle: row.subtitle))
        } else {
            let principles = [
                OpenApiFieldRow(key: "1", value: "先加协议和适配器", subtitle: "不直接搬动主控制器里的稳定业务逻辑。"),
                OpenApiFieldRow(key: "2", value: "先抽数据层和渲染层", subtitle: "SQLite、消息 Schema、气泡估高可先复用。"),
                OpenApiFieldRow(key: "3", value: "业务页面逐个迁移", subtitle: "朋友圈、视频号、客户档案、素材库保持独立 ViewController。"),
                OpenApiFieldRow(key: "4", value: "每次拆分都编译安装", subtitle: "保证不破坏现有真机可用状态。")
            ]
            cell.configure(principles[indexPath.row])
        }
        return cell
    }
}







final class BackgroundRemovalConfigurationViewController: UIViewController {
    var onEnabledChanged: ((Bool) -> Void)?
    var onChooseImage: (() -> Void)?

    let isSupported: Bool
    var isEnabled: Bool
    let enableSwitch = UISwitch()
    let statusLabel = UILabel()
    let actionButton = UIButton(type: .system)

    init(isEnabled: Bool, isSupported: Bool) {
        self.isEnabled = isEnabled
        self.isSupported = isSupported
        super.init(nibName: nil, bundle: nil)
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        title = "智能扣图"
        view.backgroundColor = UIColor(red: 0.95, green: 0.97, blue: 0.96, alpha: 1)
        navigationItem.leftBarButtonItem = UIBarButtonItem(title: "返回", style: .plain, target: self, action: #selector(closePage))
        configureContent()
        updateStateAppearance()
    }

    func configureContent() {
        let scrollView = UIScrollView()
        scrollView.alwaysBounceVertical = true
        scrollView.translatesAutoresizingMaskIntoConstraints = false

        let contentStack = UIStackView()
        contentStack.axis = .vertical
        contentStack.spacing = 12
        contentStack.translatesAutoresizingMaskIntoConstraints = false

        view.addSubview(scrollView)
        scrollView.addSubview(contentStack)
        NSLayoutConstraint.activate([
            scrollView.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor),
            scrollView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            scrollView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            scrollView.bottomAnchor.constraint(equalTo: view.bottomAnchor),

            contentStack.topAnchor.constraint(equalTo: scrollView.contentLayoutGuide.topAnchor, constant: 16),
            contentStack.leadingAnchor.constraint(equalTo: scrollView.frameLayoutGuide.leadingAnchor, constant: 16),
            contentStack.trailingAnchor.constraint(equalTo: scrollView.frameLayoutGuide.trailingAnchor, constant: -16),
            contentStack.bottomAnchor.constraint(equalTo: scrollView.contentLayoutGuide.bottomAnchor, constant: -24)
        ])

        contentStack.addArrangedSubview(makeHeaderCard())
        contentStack.addArrangedSubview(makeToggleCard())
        contentStack.addArrangedSubview(makeProcessingCard())
        contentStack.addArrangedSubview(actionButton)
        contentStack.addArrangedSubview(makeFootnote())

        actionButton.heightAnchor.constraint(equalToConstant: 48).isActive = true
        configureActionButton()
    }

    func makeHeaderCard() -> UIView {
        let card = makeCard()

        let iconHost = UIView()
        iconHost.backgroundColor = UIColor(red: 0.10, green: 0.56, blue: 0.40, alpha: 1)
        iconHost.layer.cornerRadius = 14
        iconHost.layer.cornerCurve = .continuous
        iconHost.translatesAutoresizingMaskIntoConstraints = false

        let icon = UIImageView(image: UIImage(systemName: "person.crop.rectangle"))
        icon.tintColor = .white
        icon.contentMode = .scaleAspectFit
        icon.translatesAutoresizingMaskIntoConstraints = false
        iconHost.addSubview(icon)

        let titleLabel = UILabel()
        titleLabel.text = "为照片生成透明背景"
        titleLabel.font = .systemFont(ofSize: 19, weight: .bold)
        titleLabel.textColor = UIColor(red: 0.08, green: 0.14, blue: 0.16, alpha: 1)
        titleLabel.translatesAutoresizingMaskIntoConstraints = false

        let subtitleLabel = UILabel()
        subtitleLabel.text = "开启后，新拍摄或新选择的图片会在发送前自动处理。关闭时仍按原图发送。"
        subtitleLabel.font = .systemFont(ofSize: 14)
        subtitleLabel.textColor = UIColor(red: 0.31, green: 0.38, blue: 0.40, alpha: 1)
        subtitleLabel.numberOfLines = 0
        subtitleLabel.translatesAutoresizingMaskIntoConstraints = false

        statusLabel.font = .systemFont(ofSize: 12, weight: .semibold)
        statusLabel.textAlignment = .center
        statusLabel.layer.cornerRadius = 8
        statusLabel.layer.cornerCurve = .continuous
        statusLabel.clipsToBounds = true
        statusLabel.translatesAutoresizingMaskIntoConstraints = false

        card.addSubview(iconHost)
        card.addSubview(titleLabel)
        card.addSubview(subtitleLabel)
        card.addSubview(statusLabel)

        NSLayoutConstraint.activate([
            iconHost.topAnchor.constraint(equalTo: card.topAnchor, constant: 16),
            iconHost.leadingAnchor.constraint(equalTo: card.leadingAnchor, constant: 16),
            iconHost.widthAnchor.constraint(equalToConstant: 52),
            iconHost.heightAnchor.constraint(equalToConstant: 52),

            icon.centerXAnchor.constraint(equalTo: iconHost.centerXAnchor),
            icon.centerYAnchor.constraint(equalTo: iconHost.centerYAnchor),
            icon.widthAnchor.constraint(equalToConstant: 28),
            icon.heightAnchor.constraint(equalToConstant: 28),

            titleLabel.topAnchor.constraint(equalTo: iconHost.topAnchor, constant: 1),
            titleLabel.leadingAnchor.constraint(equalTo: iconHost.trailingAnchor, constant: 12),
            titleLabel.trailingAnchor.constraint(equalTo: card.trailingAnchor, constant: -16),

            statusLabel.leadingAnchor.constraint(equalTo: titleLabel.leadingAnchor),
            statusLabel.topAnchor.constraint(equalTo: titleLabel.bottomAnchor, constant: 7),
            statusLabel.heightAnchor.constraint(equalToConstant: 24),
            statusLabel.widthAnchor.constraint(greaterThanOrEqualToConstant: 68),

            subtitleLabel.topAnchor.constraint(equalTo: iconHost.bottomAnchor, constant: 14),
            subtitleLabel.leadingAnchor.constraint(equalTo: iconHost.leadingAnchor),
            subtitleLabel.trailingAnchor.constraint(equalTo: card.trailingAnchor, constant: -16),
            subtitleLabel.bottomAnchor.constraint(equalTo: card.bottomAnchor, constant: -16)
        ])
        return card
    }

    func makeToggleCard() -> UIView {
        let card = makeCard()

        let titleLabel = UILabel()
        titleLabel.text = "自动处理新照片"
        titleLabel.font = .systemFont(ofSize: 16, weight: .semibold)
        titleLabel.textColor = UIColor(red: 0.10, green: 0.14, blue: 0.16, alpha: 1)
        titleLabel.translatesAutoresizingMaskIntoConstraints = false

        let detailLabel = UILabel()
        detailLabel.text = isSupported
            ? "只影响之后从相册选择或相机拍摄的图片。已有图片与视频不会改变。"
            : "需要 iOS 17 或更高版本。当前系统会继续按原图发送。"
        detailLabel.font = .systemFont(ofSize: 13)
        detailLabel.textColor = UIColor(red: 0.36, green: 0.42, blue: 0.44, alpha: 1)
        detailLabel.numberOfLines = 0
        detailLabel.translatesAutoresizingMaskIntoConstraints = false

        enableSwitch.isOn = isEnabled
        enableSwitch.isEnabled = isSupported
        enableSwitch.onTintColor = UIColor(red: 0.10, green: 0.58, blue: 0.42, alpha: 1)
        enableSwitch.translatesAutoresizingMaskIntoConstraints = false
        enableSwitch.addTarget(self, action: #selector(enableSwitchChanged), for: .valueChanged)

        card.addSubview(titleLabel)
        card.addSubview(detailLabel)
        card.addSubview(enableSwitch)
        NSLayoutConstraint.activate([
            titleLabel.topAnchor.constraint(equalTo: card.topAnchor, constant: 16),
            titleLabel.leadingAnchor.constraint(equalTo: card.leadingAnchor, constant: 16),
            titleLabel.trailingAnchor.constraint(lessThanOrEqualTo: enableSwitch.leadingAnchor, constant: -12),

            enableSwitch.trailingAnchor.constraint(equalTo: card.trailingAnchor, constant: -16),
            enableSwitch.centerYAnchor.constraint(equalTo: titleLabel.centerYAnchor),

            detailLabel.topAnchor.constraint(equalTo: titleLabel.bottomAnchor, constant: 8),
            detailLabel.leadingAnchor.constraint(equalTo: titleLabel.leadingAnchor),
            detailLabel.trailingAnchor.constraint(equalTo: card.trailingAnchor, constant: -16),
            detailLabel.bottomAnchor.constraint(equalTo: card.bottomAnchor, constant: -16)
        ])
        return card
    }

    func makeProcessingCard() -> UIView {
        let card = makeCard()

        let icon = UIImageView(image: UIImage(systemName: "lock.shield"))
        icon.tintColor = UIColor(red: 0.18, green: 0.40, blue: 0.68, alpha: 1)
        icon.contentMode = .scaleAspectFit
        icon.translatesAutoresizingMaskIntoConstraints = false

        let titleLabel = UILabel()
        titleLabel.text = "处理方式"
        titleLabel.font = .systemFont(ofSize: 15, weight: .semibold)
        titleLabel.textColor = UIColor(red: 0.10, green: 0.14, blue: 0.16, alpha: 1)
        titleLabel.translatesAutoresizingMaskIntoConstraints = false

        let detailLabel = UILabel()
        detailLabel.text = "使用设备端 Vision 前景实例遮罩生成 PNG。识别不到主体或处理失败时，会自动保留原图，不会中断发送。"
        detailLabel.font = .systemFont(ofSize: 13)
        detailLabel.textColor = UIColor(red: 0.36, green: 0.42, blue: 0.44, alpha: 1)
        detailLabel.numberOfLines = 0
        detailLabel.translatesAutoresizingMaskIntoConstraints = false

        card.addSubview(icon)
        card.addSubview(titleLabel)
        card.addSubview(detailLabel)
        NSLayoutConstraint.activate([
            icon.topAnchor.constraint(equalTo: card.topAnchor, constant: 16),
            icon.leadingAnchor.constraint(equalTo: card.leadingAnchor, constant: 16),
            icon.widthAnchor.constraint(equalToConstant: 22),
            icon.heightAnchor.constraint(equalToConstant: 22),

            titleLabel.centerYAnchor.constraint(equalTo: icon.centerYAnchor),
            titleLabel.leadingAnchor.constraint(equalTo: icon.trailingAnchor, constant: 10),
            titleLabel.trailingAnchor.constraint(equalTo: card.trailingAnchor, constant: -16),

            detailLabel.topAnchor.constraint(equalTo: icon.bottomAnchor, constant: 10),
            detailLabel.leadingAnchor.constraint(equalTo: icon.leadingAnchor),
            detailLabel.trailingAnchor.constraint(equalTo: card.trailingAnchor, constant: -16),
            detailLabel.bottomAnchor.constraint(equalTo: card.bottomAnchor, constant: -16)
        ])
        return card
    }

    func makeFootnote() -> UILabel {
        let label = UILabel()
        label.text = "图片预览中的“更多操作 - 扣除背景”仍可单独使用。"
        label.font = .systemFont(ofSize: 12)
        label.textColor = UIColor(red: 0.42, green: 0.47, blue: 0.49, alpha: 1)
        label.numberOfLines = 0
        label.textAlignment = .center
        label.translatesAutoresizingMaskIntoConstraints = false
        return label
    }

    func makeCard() -> UIView {
        let card = UIView()
        card.backgroundColor = .white
        card.layer.cornerRadius = 12
        card.layer.cornerCurve = .continuous
        card.layer.borderWidth = 1
        card.layer.borderColor = UIColor.black.withAlphaComponent(0.06).cgColor
        card.translatesAutoresizingMaskIntoConstraints = false
        return card
    }

    func configureActionButton() {
        var configuration = UIButton.Configuration.filled()
        configuration.cornerStyle = .medium
        configuration.imagePadding = 8
        actionButton.configuration = configuration
        actionButton.translatesAutoresizingMaskIntoConstraints = false
        actionButton.addTarget(self, action: #selector(chooseImage), for: .touchUpInside)
    }

    func updateStateAppearance() {
        enableSwitch.isOn = isEnabled
        if isEnabled {
            statusLabel.text = "  已开启  "
            statusLabel.textColor = UIColor(red: 0.06, green: 0.42, blue: 0.28, alpha: 1)
            statusLabel.backgroundColor = UIColor(red: 0.80, green: 0.94, blue: 0.86, alpha: 1)
        } else if isSupported {
            statusLabel.text = "  默认关闭  "
            statusLabel.textColor = UIColor(red: 0.34, green: 0.40, blue: 0.44, alpha: 1)
            statusLabel.backgroundColor = UIColor(red: 0.90, green: 0.92, blue: 0.93, alpha: 1)
        } else {
            statusLabel.text = "  系统不支持  "
            statusLabel.textColor = UIColor(red: 0.60, green: 0.30, blue: 0.06, alpha: 1)
            statusLabel.backgroundColor = UIColor(red: 0.99, green: 0.91, blue: 0.80, alpha: 1)
        }

        var configuration = actionButton.configuration ?? .filled()
        configuration.title = isEnabled ? "选择图片并扣背景" : "开启后可选择图片"
        configuration.image = UIImage(systemName: isEnabled ? "photo.badge.plus" : "lock")
        configuration.baseBackgroundColor = isEnabled
            ? UIColor(red: 0.10, green: 0.58, blue: 0.42, alpha: 1)
            : UIColor(red: 0.52, green: 0.56, blue: 0.60, alpha: 1)
        configuration.baseForegroundColor = .white
        actionButton.configuration = configuration
        actionButton.isEnabled = isEnabled && isSupported
    }

    @objc private func enableSwitchChanged() {
        isEnabled = enableSwitch.isOn
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
        updateStateAppearance()
        onEnabledChanged?(isEnabled)
    }

    @objc private func chooseImage() {
        guard isEnabled, isSupported else { return }
        onChooseImage?()
    }

    @objc private func closePage() {
        navigationController?.popViewController(animated: true)
    }
}
