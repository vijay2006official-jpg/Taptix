package com.example.taptix.service

import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import com.example.taptix.data.PreferencesRepository

@RequiresApi(Build.VERSION_CODES.N)
class TaptixTileService : TileService() {

    private lateinit var prefsRepo: PreferencesRepository

    override fun onCreate() {
        super.onCreate()
        prefsRepo = PreferencesRepository(this)
    }

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        val settings = prefsRepo.getSettings()
        val newState = !settings.masterControlEnabled
        prefsRepo.saveMasterControl(newState)

        if (newState) {
            if (settings.floatingDockEnabled && Settings.canDrawOverlays(this)) {
                OverlayService.showService(this)
            }
            AutoClickService.instance?.setClickingActive(true)
            OverlayService.syncMasterState(this, true)
        } else {
            AutoClickService.instance?.setClickingActive(false)
            OverlayService.syncMasterState(this, false)
        }

        updateTileState()
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val settings = prefsRepo.getSettings()
        if (settings.masterControlEnabled) {
            tile.state = Tile.STATE_ACTIVE
            tile.label = "Taptix ON"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = "Auto-Accept Active"
            }
        } else {
            tile.state = Tile.STATE_INACTIVE
            tile.label = "Taptix OFF"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = "Paused"
            }
        }
        tile.updateTile()
    }
}
