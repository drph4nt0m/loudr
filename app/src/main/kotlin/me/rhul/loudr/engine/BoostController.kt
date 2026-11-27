package me.rhul.loudr.engine

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.rhul.loudr.data.PreferencesRepository
import me.rhul.loudr.di.ApplicationScope
import me.rhul.loudr.safety.SafetyLimiter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coordinates boost and bass commands from the UI, notification actions,
 * Quick Settings tile, home screen widgets, and audio device events.
 *
 * Also owns one-time hydration of persisted DataStore preferences into the
 * audio engines so cold starts from widgets, tiles, or BroadcastReceivers
 * restore the user's saved settings before applying commands.
 */
@Singleton
class BoostController @Inject constructor(
    private val engine: AudioEngineRepository,
    private val bassEngine: DynamicsProcessorEngine,
    private val prefs: PreferencesRepository,
    private val safety: SafetyLimiter,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val hydrationMutex = Mutex()
    @Volatile
    private var isHydrated = false
    private var trackingJob: Job? = null

    init {
        // Track exposure duration whenever the boost engine is active
        scope.launch {
            engine.isActive.collectLatest { active ->
                if (active) startTracking() else stopTracking()
            }
        }
    }

    /**
     * Loads persisted settings into [safety], [engine], and [bassEngine] once
     * per process lifetime. Safe to call from any entry point (UI, Service,
     * Widget, QS tile, BroadcastReceiver).
     */
    suspend fun ensureHydrated() {
        if (isHydrated) return
        hydrationMutex.withLock {
            if (isHydrated) return
            val level       = prefs.boostLevel.first()
            val enabled     = prefs.isEnabled.first()
            val limiterOn   = prefs.safetyLimiterEnabled.first()
            val bassEnabled = prefs.bassBoostEnabled.first()
            val bassLevel   = effectiveBassLevel(prefs.bassBoostLevel.first())

            safety.setLimiterEnabled(limiterOn)
            engine.setBoost(level)
            val clamped = engine.boostLevel.value
            if (clamped != level) prefs.setBoostLevel(clamped)

            if (enabled) {
                engine.enable()
                if (bassEnabled) {
                    enableBassOnCurrentSession(bassLevel)
                }
            }
            isHydrated = true
        }
    }

    private fun startTracking() {
        trackingJob?.cancel()
        trackingJob = scope.launch {
            safety.recordExposureTick(engine.boostLevel.value)
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
        ensureHydrated()
        engine.setBoost(level)
        val applied = engine.boostLevel.value
        prefs.setBoostLevel(applied)
        if (engine.isActive.value) {
            safety.recordExposureTick(applied)
        }
    }

    suspend fun toggleActive() {
        ensureHydrated()
        if (engine.isActive.value) {
            engine.disable()
            disableBass()
            prefs.setEnabled(false)
        } else {
            engine.enable()
            prefs.setEnabled(true)
            if (prefs.bassBoostEnabled.first()) {
                enableBassOnCurrentSession(effectiveBassLevel(prefs.bassBoostLevel.first()))
            }
            safety.recordExposureTick(engine.boostLevel.value)
        }
    }

    /**
     * Steps the boost by [deltaPct] percentage points, respecting the current
     * safety ceiling instead of always assuming a fixed 300% maximum.
     */
    suspend fun stepBoost(deltaPct: Int) {
        ensureHydrated()
        val ceiling = safety.ceiling.value          // e.g. 0.5 when limiter on
        val ceilingPct = (ceiling * 300).toInt()    // e.g. 150
        val currentPct = (engine.boostLevel.value * 300).toInt()
        val nextPct = (currentPct + deltaPct).coerceIn(0, ceilingPct)
        setBoost(nextPct / 300f)
    }

    suspend fun setSafetyLimiterEnabled(enabled: Boolean) {
        ensureHydrated()
        safety.setLimiterEnabled(enabled)
        prefs.setSafetyLimiterEnabled(enabled)
        engine.setBoost(engine.boostLevel.value)
        prefs.setBoostLevel(engine.boostLevel.value)
    }

    suspend fun setBassBoostEnabled(enabled: Boolean) {
        ensureHydrated()
        prefs.setBassBoostEnabled(enabled)
        if (enabled && engine.isActive.value) {
            enableBassOnCurrentSession(effectiveBassLevel(prefs.bassBoostLevel.first()))
        } else {
            disableBass()
        }
    }

    /**
     * Called when a wired headset or Bluetooth audio device connects.
     * Activates boost automatically if the user enabled auto-boost on headphone.
     */
    suspend fun onAudioDeviceConnected() {
        ensureHydrated()
        if (!prefs.autoBoostOnHeadphone.first()) return
        val sessionId = currentSessionOrGlobal()
        withContext(Dispatchers.IO) {
            engine.attachToSession(sessionId)
        }
        engine.enable()
        prefs.setEnabled(true)
        if (prefs.bassBoostEnabled.first()) {
            enableBassOnCurrentSession(effectiveBassLevel(prefs.bassBoostLevel.first()))
        }
        safety.recordExposureTick(engine.boostLevel.value)
    }

    /**
     * Called when a wired headset or Bluetooth audio device disconnects.
     * Safely pauses and detaches audio effects so speakers aren't blasted and
     * UI/notification state stays accurate.
     */
    suspend fun onAudioDeviceDisconnected() {
        engine.disable()
        withContext(Dispatchers.IO) {
            engine.detachFromSession()
        }
        disableBass()
        prefs.setEnabled(false)
    }

    /**
     * Releases active audio effects when [me.rhul.loudr.service.VolumeBoostService]
     * is destroyed, without overwriting persisted user preferences.
     */
    suspend fun shutdown() {
        engine.disable()
        withContext(Dispatchers.IO) {
            engine.detachFromSession()
        }
        disableBass()
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun currentSessionOrGlobal(): Int =
        engine.currentSessionId.value.takeIf { it != -1 } ?: 0

    private fun effectiveBassLevel(stored: Float): Float =
        if (stored > 0f) stored else DynamicsProcessorEngine.DEFAULT_BASS_LEVEL

    private suspend fun enableBassOnCurrentSession(level: Float) {
        val session = currentSessionOrGlobal()
        withContext(Dispatchers.IO) {
            bassEngine.attachToSession(session)
            bassEngine.setBassBoost(level)
            bassEngine.setEnabled(true)
        }
    }

    private suspend fun disableBass() {
        withContext(Dispatchers.IO) {
            bassEngine.setEnabled(false)
            bassEngine.detachFromSession()
        }
    }
}
