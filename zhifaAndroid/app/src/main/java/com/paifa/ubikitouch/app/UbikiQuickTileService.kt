package com.paifa.ubikitouch.app

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.paifa.ubikitouch.accessibility.UbikiAccessibilityService
import com.paifa.ubikitouch.accessibility.UbikiPreferences

class UbikiQuickTileService : TileService() {
    private val preferences: UbikiPreferences by lazy {
        UbikiPreferences(this)
    }

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        if (preferences.isTemporarilyPaused()) {
            preferences.resumeNow()
        } else {
            preferences.pauseFor(DEFAULT_TILE_PAUSE_MS)
        }
        UbikiAccessibilityService.instance?.requestOverlayRefresh()
        updateTile()
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val paused = preferences.isTemporarilyPaused()
        tile.state = if (paused) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE
        tile.label = getString(R.string.tile_label)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = getString(if (paused) R.string.tile_paused else R.string.tile_active)
        }
        tile.updateTile()
    }

    private companion object {
        const val DEFAULT_TILE_PAUSE_MS = 15L * 60L * 1000L
    }
}
