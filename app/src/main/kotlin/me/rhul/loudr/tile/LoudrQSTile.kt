package me.rhul.loudr.tile

import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import me.rhul.loudr.engine.AudioEngineRepository
import me.rhul.loudr.service.ACTION_TOGGLE_BOOST
import me.rhul.loudr.service.VolumeBoostService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Quick Settings tile — tap to toggle boost on/off.
 * Long-press cycles through 50% → 100% → 150% boost levels.
 *
 * Shows the current boost percentage as the tile subtitle.
 * No additional permissions required beyond those already declared.
 */
@AndroidEntryPoint
class LoudrQSTile : TileService() {

    @Inject
    lateinit var engine: AudioEngineRepository

    private var tileJob: kotlinx.coroutines.Job? = null
    private val tileScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onStartListening() {
        super.onStartListening()
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
        // Toggle via service so state is persisted and notification is updated
        startService(
            Intent(this, VolumeBoostService::class.java).apply {
                action = ACTION_TOGGLE_BOOST
            },
        )
        updateTile()
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    private fun updateTile() {
        val tile    = qsTile ?: return
        val isActive = engine.isActive.value
        val pct      = (engine.boostLevel.value * 300).toInt()

        tile.state    = if (isActive) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.subtitle = if (isActive) "+$pct%" else "Off"
        tile.updateTile()
    }
}
