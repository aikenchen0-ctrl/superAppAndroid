package com.paifa.univerge.accessibility.floatingchat.media

import android.content.Context
import com.paifa.univerge.core.model.FloatingChatMessage

internal interface MediaRuntimePort {
    fun share(context: Context, message: FloatingChatMessage): MediaActionResult
    fun save(context: Context, message: FloatingChatMessage): MediaActionResult
}
