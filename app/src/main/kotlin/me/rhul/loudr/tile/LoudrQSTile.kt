package me.rhul.loudr.tile

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import me.rhul.loudr.R
import me.rhul.loudr.engine.AudioEngineRepository
import me.rhul.loudr.service.ACTION_TOGGLE_BOOST
import me.rhul.loudr.service.VolumeBoostService
import javax.inject.Inject

/**
 * Quick Settings tile — tap to toggle boost on/off.
 *
 * Shows the current boost percentage as the tile subtitle on Android 10+
 * (and in the tile label on Android 9 / API 28).
 */
@AndroidEntryPoint
class LoudrQSTile : TileService() {

    @Inject
    lateinit var engine: AudioEngineRepository

    private var tileJob: Job? = null
    private val tileScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onStartListening() {
        super.onStartListening()
        tileJob?.cancel()
        tileJob = tileScope.launch {
            launch { engine.isActive.collect { updateTile() } }
            launch { engine.boostLevel.collect { updateTile() } }
        }
    }

    override fun onStopListening() {
        tileJob?.cancel()
        tileJob = null
        super.onStopListening()
    }

    override fun onDestroy() {
        tileScope.cancel()
        super.onDestroy()
    }

    override fun onClick() {
        // Toggle via foreground service so state is persisted and notification is updated
        VolumeBoostService.start(this, ACTION_TOGGLE_BOOST)
        updateTile()
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    private fun updateTile() {
        val tile     = qsTile ?: return
        val isActive = engine.isActive.value
        val pct      = (engine.boostLevel.value * 300).toInt()

        tile.state = if (isActive) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (isActive) "+$pct%" else getString(R.string.widget_off)
        } else {
            tile.label = if (isActive) {
                "${getString(R.string.app_name)} +$pct%"
            } else {
                getString(R.string.qs_tile_label)
            }
        }
        tile.updateTile()
    }
}
