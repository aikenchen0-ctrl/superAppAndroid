package com.paifa.univerge.accessibility

/** Immutable preview item snapshot reused for every MOVE in one configuration. */
internal class SideFunctionPreviewItemsCache {
    private var items: List<SideFunctionPanelItem> = emptyList()

    fun replace(next: List<SideFunctionPanelItem>) {
        items = next.toList()
    }

    fun snapshot(): List<SideFunctionPanelItem> = items
}
