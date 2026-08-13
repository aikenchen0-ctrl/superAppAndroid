package com.paifa.ubikitouch.accessibility

data class FloatingChatRedPacketSession(
    val accountName: String,
    val recipientNames: List<String>
)

object FloatingChatRedPacketBridge {
    @Volatile private var session: FloatingChatRedPacketSession? = null

    fun open(nextSession: FloatingChatRedPacketSession) {
        session = nextSession
        UbikiAccessibilityService.instance?.requestFloatingChatRedPacket()
    }

    fun currentSession(): FloatingChatRedPacketSession? = session

    fun notifyClosed() {
        session = null
        UbikiAccessibilityService.instance?.onFloatingChatRedPacketClosed()
    }
}
