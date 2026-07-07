package com.paifa.ubikitouch.core.model

enum class GestureType(val id: String) {
    TAP("tap"),
    DOUBLE_TAP("double_tap"),
    LONG_PRESS("long_press"),
    SWIPE_UP("swipe_up"),
    SWIPE_DOWN("swipe_down"),
    PULL_INWARD("pull_inward"),
    PULL_INWARD_SHORT("pull_inward_short"),
    PULL_INWARD_LONG("pull_inward_long"),
    PULL_INWARD_HOLD("pull_inward_hold"),
    PULL_DIAGONAL_UP("pull_diagonal_up"),
    PULL_DIAGONAL_DOWN("pull_diagonal_down"),
    PULL_DIAGONAL_UP_SHORT("pull_diagonal_up_short"),
    PULL_DIAGONAL_UP_LONG("pull_diagonal_up_long"),
    PULL_DIAGONAL_DOWN_SHORT("pull_diagonal_down_short"),
    PULL_DIAGONAL_DOWN_LONG("pull_diagonal_down_long");

    companion object {
        fun fromId(id: String): GestureType = entries.firstOrNull { it.id == id } ?: TAP
    }
}

fun gestureMappingOrder(): List<GestureType> {
    return listOf(
        GestureType.PULL_INWARD_SHORT,
        GestureType.PULL_INWARD_LONG,
        GestureType.PULL_DIAGONAL_UP_SHORT,
        GestureType.PULL_DIAGONAL_UP_LONG,
        GestureType.PULL_DIAGONAL_DOWN_SHORT,
        GestureType.PULL_DIAGONAL_DOWN_LONG,
        GestureType.PULL_INWARD_HOLD,
        GestureType.SWIPE_UP,
        GestureType.SWIPE_DOWN
    )
}
