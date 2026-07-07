package com.paifa.ubikitouch.core.model

object GestureDefaultAction {
    fun forGesture(gestureType: GestureType): GestureAction {
        return when (gestureType) {
            GestureType.PULL_INWARD -> GestureAction.Back
            GestureType.PULL_INWARD_SHORT -> GestureAction.Back
            GestureType.PULL_INWARD_LONG -> GestureAction.Home
            GestureType.SWIPE_UP -> GestureAction.ExpandFloatingChat
            GestureType.SWIPE_DOWN -> GestureAction.CollapseFloatingChat
            else -> GestureAction.None
        }
    }
}
