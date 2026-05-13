package me.rhul.loudr.engine

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import me.rhul.loudr.data.PreferencesRepository
import me.rhul.loudr.di.ApplicationScope
import me.rhul.loudr.safety.SafetyLimiter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coordinates boost commands from the UI, notification actions, and widgets.
 *
 * C-4 fix: uses the Hilt-provided [@ApplicationScope] coroutine scope instead of
 * a bare [CoroutineScope] that was never cancelled and could not be observed in tests.
 *
 * H-7 fix: [stepBoost] scales against the current safety ceiling so notification
 * +10% / −10% actions never exceed the limiter and the step feels proportionate.
 */
@Singleton
class BoostController @Inject constructor(
    private val engine: AudioEngineRepository,
    private val prefs: PreferencesRepository,
    private val safety: SafetyLimiter,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private var trackingJob: Job? = null

    init {
        // Start tracking job to check exposure duration when active
        scope.launch {
            engine.isActive.collectLatest { active ->
                if (active) startTracking() else stopTracking()
            }
        }
    }

    private fun startTracking() {
        trackingJob?.cancel()
        trackingJob = scope.launch {
            while (true) {
                delay(60_000L) // check every minute
                safety.recordExposureTick(engine.boostLevel.value)
            }
        }
    }

    private fun stopTracking() {
        trackingJob?.cancel()
        trackingJob = null
        safety.recordExposureTick(0f)
    }

    suspend fun setBoost(level: Float) {
        engine.setBoost(level)
        prefs.setBoostLevel(level)
        safety.recordExposureTick(level)
    }

    suspend fun toggleActive() {
        if (engine.isActive.value) {
            engine.disable()
            prefs.setEnabled(false)
        } else {
            engine.enable()
            prefs.setEnabled(true)
        }
    }

    /**
     * Steps the boost by [deltaPct] percentage points, respecting the current
     * safety ceiling instead of always assuming a fixed 300% maximum.
     */
    suspend fun stepBoost(deltaPct: Int) {
        val ceiling = safety.ceiling.value          // e.g. 0.5 when limiter on
        val ceilingPct = (ceiling * 300).toInt()    // e.g. 150
        val currentPct = (engine.boostLevel.value * 300).toInt()
        val nextPct = (currentPct + deltaPct).coerceIn(0, ceilingPct)
        setBoost(nextPct / 300f)
    }
}
