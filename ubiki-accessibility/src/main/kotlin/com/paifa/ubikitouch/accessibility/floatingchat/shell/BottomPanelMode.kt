package com.paifa.ubikitouch.accessibility.floatingchat.shell

internal enum class BottomPanelMode {
    None,
    Home,
    Contacts,
    AccountDevice,
    CustomerProfile,
    HiddenUsers,
    SendName,
    VideoShort,
    ChannelsVideo,
    ChannelsLive,
    WebLink,
    Article,
    Music,
    Gallery,
    ToolbarSearch,
    ToolbarScan,
    ToolbarAddFriend,
    Assistant,
    AiVoice,
    UiComponents,
    AiAutoReply,
    ContactRelations,
    LeftSidebar,
    FriendManagement,
    FavoriteLibrary,
    FinderPublish,
    MaterialLibrary,
    OpenApiWorkbench,
    BackgroundRemoval,
    MiniProgram,
    ReviewRequests,
    Voice,
    VoiceCall,
    VideoCall,
    Emoji,
    Gift,
    QuickPhrase,
    Card,
    GroupInfo,
    GroupInvite,
    Relay,
    SideEffect,
    Moments,
    Finder,
    MomentMaterials,
    Favorite,
    FileDocument,
    CouponWallet,
    RedPacket,
    Transfer,
    SplitBill,
    Location,
    ScrmEmoji,
    ScrmWeAppCard,
    ScrmCardTemplates,
    ScrmBatchSend,
    ScrmOperations,
    More
}

/**
 * 右侧功能与工具栏入口统一复用聊天根的全屏工作区，避免以独立 Window 打开时出现 BadTokenException。
 * 测试流程：打开任意功能入口，确认 surface 根覆盖完整悬浮区域；只有表情和更多保留为输入区小面板。
 */
internal fun BottomPanelMode.isFullscreenWorkspace(): Boolean {
    return when (this) {
        BottomPanelMode.None,
        BottomPanelMode.Home,
        BottomPanelMode.Emoji,
        BottomPanelMode.More -> false
        else -> true
    }
}

internal fun BottomPanelMode.isBottomComposerDrawer(): Boolean {
    return this == BottomPanelMode.Emoji || this == BottomPanelMode.More
}
