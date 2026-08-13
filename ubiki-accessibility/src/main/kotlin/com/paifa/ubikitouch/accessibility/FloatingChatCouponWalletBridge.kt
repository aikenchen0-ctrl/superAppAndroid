package com.paifa.ubikitouch.accessibility

object FloatingChatCouponWalletBridge {
    fun open() {
        UbikiAccessibilityService.instance?.requestFloatingChatCouponWallet()
    }

    fun notifyClosed() {
        UbikiAccessibilityService.instance?.onFloatingChatCouponWalletClosed()
    }
}
