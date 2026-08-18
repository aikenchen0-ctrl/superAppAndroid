package com.paifa.univerge.accessibility.floatingchat.shell

import com.paifa.univerge.accessibility.floatingchat.contract.FloatingChatWindowCommand

fun interface FloatingChatWindowPort {
    fun apply(command: FloatingChatWindowCommand)
}
