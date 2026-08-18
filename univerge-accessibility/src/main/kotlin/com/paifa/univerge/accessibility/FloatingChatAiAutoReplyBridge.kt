package com.paifa.univerge.accessibility

/** 右侧“AI自动回复”入口通过无障碍服务展示独立全屏悬浮页。 */
internal object FloatingChatAiAutoReplyBridge {
    fun open() {
        UniVergeAccessibilityService.instance?.requestFloatingChatAiAutoReply()
    }
}
