package com.paifa.ubikitouch.accessibility.floatingchat.message.renderers

/** Transient display state reserved for richer media and AI renderers. */
internal data class MessageRenderState(
    val playing: Boolean = false,
    val aiStreaming: Boolean = false,
    val blurred: Boolean = false,
    val selected: Boolean = false,
    val threeDEnabled: Boolean = false
)
