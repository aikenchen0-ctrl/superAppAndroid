package com.paifa.ubikitouch.core.model

enum class EdgeSide(val id: String) {
    LEFT("left"),
    RIGHT("right");

    companion object {
        fun fromId(id: String): EdgeSide = entries.firstOrNull { it.id == id } ?: LEFT
    }
}
