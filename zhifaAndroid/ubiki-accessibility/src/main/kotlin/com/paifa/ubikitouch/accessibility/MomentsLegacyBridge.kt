package com.paifa.ubikitouch.accessibility

import com.paifa.ubikitouch.accessibility.floatingchat.moments.scrmCircleIdForMomentPostId as parseMomentPostId

@Suppress("unused")
internal fun scrmCircleIdForMomentPostId(postId: String): Long? = parseMomentPostId(postId)
