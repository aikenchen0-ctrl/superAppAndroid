package com.paifa.univerge.accessibility

object FloatingChatCouponWalletBridge {
    fun open() {
        UniVergeAccessibilityService.instance?.requestFloatingChatCouponWallet()
    }

    fun notifyClosed() {
        UniVergeAccessibilityService.instance?.onFloatingChatCouponWalletClosed()
    }
}
