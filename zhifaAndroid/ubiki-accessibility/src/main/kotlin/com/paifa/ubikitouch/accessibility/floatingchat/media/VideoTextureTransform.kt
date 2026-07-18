package com.paifa.ubikitouch.accessibility.floatingchat.media

import android.graphics.Matrix
import android.view.TextureView

internal fun textureViewScaleForAspectFit(
    containerWidth: Int,
    containerHeight: Int,
    videoAspectRatio: Float?
): Pair<Float, Float> {
    if (containerWidth <= 0 || containerHeight <= 0 || videoAspectRatio == null || videoAspectRatio <= 0f) {
        return 1f to 1f
    }
    val containerAspectRatio = containerWidth.toFloat() / containerHeight.toFloat()
    return if (videoAspectRatio > containerAspectRatio) {
        1f to (containerAspectRatio / videoAspectRatio)
    } else {
        (videoAspectRatio / containerAspectRatio) to 1f
    }
}

internal fun applyTextureViewAspectFitTransform(
    textureView: TextureView,
    videoAspectRatio: Float?
) {
    val width = textureView.width
    val height = textureView.height
    val (scaleX, scaleY) = textureViewScaleForAspectFit(width, height, videoAspectRatio)
    val matrix = Matrix().apply {
        setScale(scaleX, scaleY, width / 2f, height / 2f)
    }
    textureView.setTransform(matrix)
}
