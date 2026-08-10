package com.paifa.ubikitouch.accessibility.floatingchat.contract

data class OverlayButtonProps(
    val label: String,
    val enabled: Boolean = true,
    val style: OverlayButtonStyle = OverlayButtonStyle.Primary
)

enum class OverlayButtonStyle {
    Primary,
    Secondary,
    Destructive
}

sealed interface OverlayButtonEvent {
    data object Clicked : OverlayButtonEvent
}

data class OverlayAvatarProps(
    val id: String,
    val label: String,
    val colorArgb: Long,
    val imageUrl: String? = null,
    val online: Boolean = false,
    val badgeText: String? = null
)

sealed interface OverlayAvatarEvent {
    data class Clicked(val id: String) : OverlayAvatarEvent
}

data class OverlayTextFieldProps(
    val value: String,
    val placeholder: String,
    val enabled: Boolean = true,
    val errorMessage: String? = null
)

sealed interface OverlayTextFieldEvent {
    data class ValueChanged(val value: String) : OverlayTextFieldEvent
    data object Submitted : OverlayTextFieldEvent
}

data class OverlayStatusProps(
    val message: String,
    val tone: StatusTone = StatusTone.Neutral,
    val visible: Boolean = true
)

enum class StatusTone {
    Neutral,
    Info,
    Success,
    Warning,
    Error
}
