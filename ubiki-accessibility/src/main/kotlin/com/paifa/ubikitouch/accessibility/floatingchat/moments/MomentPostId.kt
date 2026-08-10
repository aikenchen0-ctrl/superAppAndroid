package com.paifa.ubikitouch.accessibility.floatingchat.moments

private const val ScrmMomentPostIdPrefix = "scrm-moment:"

internal fun scrmCircleIdForMomentPostId(postId: String): Long? {
    return postId
        .takeIf { it.startsWith(ScrmMomentPostIdPrefix) }
        ?.removePrefix(ScrmMomentPostIdPrefix)
        ?.toLongOrNull()
}
