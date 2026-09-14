package com.paifa.univerge.heavydrag.compose

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.paifa.univerge.heavydrag.core.HeavyDragCoordinator
import com.paifa.univerge.heavydrag.core.HeavyDragSessionSnapshot
import com.paifa.univerge.heavydrag.core.HeavyRect

/** 把 Compose 的全局布局坐标转换为 HeavyDragHost 的局部坐标。 */
internal class HeavyDragCoordinateSpace {
    @Volatile
    var origin: Offset = Offset.Zero

    fun toLocal(rect: Rect): HeavyRect {
        val currentOrigin = origin
        return HeavyRect(
            left = rect.left - currentOrigin.x,
            top = rect.top - currentOrigin.y,
            right = rect.right - currentOrigin.x,
            bottom = rect.bottom - currentOrigin.y
        )
    }
}

val LocalHeavyDragSession = compositionLocalOf<HeavyDragSessionSnapshot?> { null }
val LocalHeavyDragCoordinator = compositionLocalOf<HeavyDragCoordinator?> { null }
internal val LocalHeavyDragCoordinateSpace = compositionLocalOf<HeavyDragCoordinateSpace> {
    error("HeavyDragHost is required")
}
