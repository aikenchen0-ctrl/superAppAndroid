package com.paifa.univerge.core.gesture

import com.paifa.univerge.core.model.EdgeSide
import com.paifa.univerge.core.model.GestureAction
import com.paifa.univerge.core.model.GestureType
import java.util.Collections

/** Identifies a gesture independently from the Android input surface. */
data class GestureBinding(
    val side: EdgeSide?,
    val gesture: GestureType
) {
    val isBottom: Boolean get() = side == null

    companion object {
        fun side(side: EdgeSide, gesture: GestureType): GestureBinding =
            GestureBinding(side = side, gesture = gesture)

        fun bottom(gesture: GestureType): GestureBinding =
            GestureBinding(side = null, gesture = gesture)
    }
}

/** Immutable action table captured by a gesture transaction. */
class GestureBindingSnapshot(
    actions: Map<GestureBinding, GestureAction> = emptyMap(),
    val version: Long = 1L
) {
    init {
        require(version > 0L) { "binding snapshot version must be positive" }
    }

    val actions: Map<GestureBinding, GestureAction> =
        Collections.unmodifiableMap(actions.toMap())

    fun actionFor(binding: GestureBinding): GestureAction =
        actions[binding] ?: GestureAction.None

    fun actionFor(side: EdgeSide, gesture: GestureType): GestureAction =
        actionFor(GestureBinding.side(side, gesture))

    fun bottomActionFor(gesture: GestureType): GestureAction =
        actionFor(GestureBinding.bottom(gesture))

    companion object {
        fun fromConfig(snapshot: com.paifa.univerge.core.gesture.runtime.ConfigSnapshot): GestureBindingSnapshot {
            val entries = buildMap {
                snapshot.sideActions.forEach { (side, sideActions) ->
                    sideActions.forEach { (gesture, action) ->
                        put(GestureBinding.side(side, gesture), action)
                    }
                }
                snapshot.bottomActions.forEach { (gesture, action) ->
                    put(GestureBinding.bottom(gesture), action)
                }
            }
            return GestureBindingSnapshot(entries, snapshot.revision)
        }
    }
}

typealias GestureBindingTable = GestureBindingSnapshot
