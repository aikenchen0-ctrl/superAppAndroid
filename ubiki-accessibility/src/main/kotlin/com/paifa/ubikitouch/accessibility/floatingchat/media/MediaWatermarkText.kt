package com.paifa.ubikitouch.accessibility.floatingchat.media

internal fun mediaWatermarkText(resourceUrl: String?, thumbnailUrl: String?): String =
    resourceUrl ?: thumbnailUrl.orEmpty()
