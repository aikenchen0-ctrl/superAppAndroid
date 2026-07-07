package com.paifa.ubikitouch.core.gesture

class BackGestureCommitController {
    var committed: Boolean = false
        private set
    private var longCommitted: Boolean = false

    fun update(progress: BackGestureProgress, velocityPxPerSecond: Float = 0f): Boolean {
        if (progress.longCommitted) {
            if (longCommitted) return false
            longCommitted = true
            committed = true
            return true
        }
        if (!committed && shouldCommitShort(progress, velocityPxPerSecond)) {
            committed = true
            return true
        }
        return false
    }

    fun reset() {
        committed = false
        longCommitted = false
    }

    private fun shouldCommitShort(progress: BackGestureProgress, velocityPxPerSecond: Float): Boolean {
        if (progress.committed) return true
        val fastEnough = velocityPxPerSecond >= QUICK_COMMIT_VELOCITY_PX_PER_SECOND
        val pulledFarEnough = progress.progress >= QUICK_COMMIT_MIN_PROGRESS
        return fastEnough && pulledFarEnough
    }

    private companion object {
        const val QUICK_COMMIT_VELOCITY_PX_PER_SECOND = 520f
        const val QUICK_COMMIT_MIN_PROGRESS = 0.24f
    }
}
