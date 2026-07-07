package com.paifa.ubikitouch.core.overlay

class ScreenInteractiveState(initialInteractive: Boolean = true) {
    var isInteractive: Boolean = initialInteractive
        private set

    fun updateFromSystem(actualInteractive: Boolean): Boolean {
        val shouldResume = actualInteractive && !isInteractive
        isInteractive = actualInteractive
        return shouldResume
    }

    fun markInteractive() {
        isInteractive = true
    }

    fun markNotInteractive() {
        isInteractive = false
    }
}
