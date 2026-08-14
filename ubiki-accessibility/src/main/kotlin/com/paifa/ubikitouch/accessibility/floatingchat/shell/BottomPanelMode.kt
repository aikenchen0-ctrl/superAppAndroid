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

internal fun BottomPanelMode.isCenteredToolFeaturePanel(): Boolean {
    return this == BottomPanelMode.Assistant ||
        this == BottomPanelMode.AiVoice ||
        this == BottomPanelMode.VoiceCall ||
        this == BottomPanelMode.VideoCall ||
        this == BottomPanelMode.Contacts ||
        this == BottomPanelMode.AccountDevice ||
        this == BottomPanelMode.CustomerProfile ||
        this == BottomPanelMode.HiddenUsers ||
        this == BottomPanelMode.SendName ||
        this == BottomPanelMode.VideoShort ||
        this == BottomPanelMode.ChannelsVideo ||
        this == BottomPanelMode.ChannelsLive ||
        this == BottomPanelMode.WebLink ||
        this == BottomPanelMode.Article ||
        this == BottomPanelMode.Music ||
        this == BottomPanelMode.Voice ||
        this == BottomPanelMode.Gallery ||
        this == BottomPanelMode.QuickPhrase ||
        this == BottomPanelMode.Card ||
        this == BottomPanelMode.GroupInvite ||
        this == BottomPanelMode.Moments ||
        this == BottomPanelMode.Finder ||
        this == BottomPanelMode.MomentMaterials ||
        this == BottomPanelMode.Favorite ||
        this == BottomPanelMode.FileDocument ||
        this == BottomPanelMode.SplitBill ||
        this == BottomPanelMode.Location ||
        this == BottomPanelMode.ScrmEmoji ||
        this == BottomPanelMode.ScrmWeAppCard ||
        this == BottomPanelMode.ScrmCardTemplates ||
        this == BottomPanelMode.ScrmBatchSend ||
        this == BottomPanelMode.ScrmOperations
}

internal fun BottomPanelMode.isBottomComposerDrawer(): Boolean {
    return this == BottomPanelMode.Emoji || this == BottomPanelMode.More
}
