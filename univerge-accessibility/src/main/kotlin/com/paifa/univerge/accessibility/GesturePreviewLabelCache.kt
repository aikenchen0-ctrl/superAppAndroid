package com.paifa.univerge.accessibility

import com.paifa.univerge.core.model.GestureAction

/** Action labels resolved at configuration boundaries, never during MOVE. */
internal class GesturePreviewLabelCache {
    private var labels: Map<GestureAction, String> = emptyMap()

    fun replace(next: Map<GestureAction, String>) {
        labels = next.toMap()
    }

    fun clear() {
        labels = emptyMap()
    }

    fun label(action: GestureAction, resolve: () -> String): String =
        labels[action] ?: resolve()
}
