package com.paifa.ubikitouch.accessibility.floatingchat.shell

internal enum class BottomPanelMode {
    None,
    Home,
    Contacts,
    Assistant,
    AiVoice,
    Voice,
    Emoji,
    Gift,
    QuickPhrase,
    Card,
    Moments,
    Finder,
    MomentMaterials,
    Favorite,
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
        this == BottomPanelMode.Contacts ||
        this == BottomPanelMode.QuickPhrase ||
        this == BottomPanelMode.Card ||
        this == BottomPanelMode.Moments ||
        this == BottomPanelMode.Finder ||
        this == BottomPanelMode.MomentMaterials ||
        this == BottomPanelMode.Favorite ||
        this == BottomPanelMode.RedPacket ||
        this == BottomPanelMode.Transfer ||
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
