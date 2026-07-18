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
    MomentMaterials,
    Favorite,
    RedPacket,
    Transfer,
    Location,
    More
}

internal fun BottomPanelMode.isCenteredToolFeaturePanel(): Boolean {
    return this == BottomPanelMode.Assistant ||
        this == BottomPanelMode.AiVoice ||
        this == BottomPanelMode.Contacts ||
        this == BottomPanelMode.QuickPhrase ||
        this == BottomPanelMode.Card ||
        this == BottomPanelMode.Moments ||
        this == BottomPanelMode.MomentMaterials ||
        this == BottomPanelMode.Favorite ||
        this == BottomPanelMode.RedPacket ||
        this == BottomPanelMode.Transfer ||
        this == BottomPanelMode.Location
}
