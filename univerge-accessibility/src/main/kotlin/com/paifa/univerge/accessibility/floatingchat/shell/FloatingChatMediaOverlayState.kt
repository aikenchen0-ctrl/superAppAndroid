package com.paifa.univerge.accessibility.floatingchat.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import com.paifa.univerge.accessibility.floatingchat.media.MediaActionResult
import com.paifa.univerge.core.model.FloatingChatMessage

internal class FloatingChatMediaOverlayState {
    var actionMessage by mutableStateOf<FloatingChatMessage?>(null)
        private set

    var actionStatus by mutableStateOf<String?>(null)
        private set

    val favoriteMediaIds: SnapshotStateMap<String, Boolean> = mutableStateMapOf()

    fun openActions(message: FloatingChatMessage) {
        actionMessage = message
    }

    fun closeActions() {
        actionMessage = null
    }

    fun clearStatus() {
        actionStatus = null
    }

    fun applyActionResult(result: MediaActionResult) {
        actionStatus = result.status
    }
}

@Composable
internal fun rememberFloatingChatMediaOverlayState(): FloatingChatMediaOverlayState {
    return remember { FloatingChatMediaOverlayState() }
}
