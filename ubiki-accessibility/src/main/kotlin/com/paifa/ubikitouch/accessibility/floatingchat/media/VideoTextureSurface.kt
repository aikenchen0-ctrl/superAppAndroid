package com.paifa.ubikitouch.accessibility.floatingchat.media

import android.content.Context
import android.graphics.SurfaceTexture
import android.view.TextureView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@Composable
internal fun VideoTextureSurface(videoAspectRatio: Float?, modifier: Modifier = Modifier, onSurfaceAvailable: (SurfaceTexture) -> Unit, onSurfaceDestroyed: () -> Unit) {
    AndroidView(
        modifier = modifier,
        factory = { viewContext: Context ->
            TextureView(viewContext).apply {
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) { onSurfaceAvailable(texture) }
                    override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) = Unit
                    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean { onSurfaceDestroyed(); return true }
                    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
                }
            }
        },
        update = { view ->
            if (view.isAvailable) view.surfaceTexture?.let(onSurfaceAvailable)
            applyTextureViewAspectFitTransform(view, videoAspectRatio)
        }
    )
}
