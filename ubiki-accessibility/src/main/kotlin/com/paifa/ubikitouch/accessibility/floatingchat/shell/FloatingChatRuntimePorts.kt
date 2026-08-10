package com.paifa.ubikitouch.accessibility.floatingchat.shell

import com.paifa.ubikitouch.accessibility.floatingchat.contract.FloatingChatWindowCommand

fun interface FloatingChatWindowPort {
    fun apply(command: FloatingChatWindowCommand)
}
