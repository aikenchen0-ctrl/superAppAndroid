package com.paifa.univerge.accessibility

object FloatingChatCouponWalletBridge {
    fun open() {
        UbikiAccessibilityService.instance?.requestFloatingChatCouponWallet()
    }

    fun notifyClosed() {
        UbikiAccessibilityService.instance?.onFloatingChatCouponWalletClosed()
    }
}
