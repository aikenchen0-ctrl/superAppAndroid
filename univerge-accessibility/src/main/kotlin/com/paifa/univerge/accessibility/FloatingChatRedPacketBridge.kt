package com.paifa.univerge.accessibility

data class FloatingChatRedPacketSession(
    val accountName: String,
    val recipientNames: List<String>
)

object FloatingChatRedPacketBridge {
    @Volatile private var session: FloatingChatRedPacketSession? = null

    fun open(nextSession: FloatingChatRedPacketSession) {
        session = nextSession
        UniVergeAccessibilityService.instance?.requestFloatingChatRedPacket()
    }

    fun currentSession(): FloatingChatRedPacketSession? = session

    fun notifyClosed() {
        session = null
        UniVergeAccessibilityService.instance?.onFloatingChatRedPacketClosed()
    }
}
