package me.rhul.loudr.safety

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "SafetyLimiter"

/**
 * Enforces a configurable gain ceiling to protect hearing health.
 *
 * Default ceiling is +6 dB (≈ 200%), equivalent to a normalised value of ~0.8
 * on a 0–1500 mB scale. Users can opt into "Expert Mode" (up to 1.0 / 1500 mB)
 * after explicitly acknowledging the hearing risk warning.
 *
 * The limiter also tracks cumulative loud-exposure duration and emits a
 * [SafetyEvent.ExposureWarning] after [EXPOSURE_WARNING_MINUTES] minutes of
 * sustained high-level output.
 */
@Singleton
class SafetyLimiter @Inject constructor() {

    companion object {
        /**
         * Safe ceiling: 0.5 normalised = 1000 mB = +10 dB.
         * Doubles subjective loudness; safe for typical listening sessions.
         * Display: +150%.
         */
        const val DEFAULT_CEILING: Float = 0.5f

        /**
         * Full ceiling: 1.0 normalised = 2000 mB = +20 dB.
         * Activated when the user explicitly disables the safety limiter.
         * Display: +300%.
         */
        const val EXPERT_CEILING: Float = 1.0f

        /**
         * Exposure tracking threshold: 0.3 normalised = 600 mB = +6 dB.
         * WHO/NIOSH: sustained exposure above +6 dB boost accumulates hearing damage.
         * Display: +90%.
         */
        const val LOUD_THRESHOLD: Float = 0.3f

        /**
         * Warn after 30 minutes at or above [LOUD_THRESHOLD].
         * At +10 dB device output (~100 dB SPL) safe exposure is ≤15 min (NIOSH);
         * 30 min is a conservative middle ground.
         */
        const val EXPOSURE_WARNING_MINUTES: Long = 30L
    }

    private val _ceiling        = MutableStateFlow(DEFAULT_CEILING)
    private val _expertMode     = MutableStateFlow(false)
    private val _safetyEvents   = MutableStateFlow<SafetyEvent?>(null)
    private val _limiterEnabled = MutableStateFlow(true)

    /** Current gain ceiling (normalised [0.0, 1.0]). */
    val ceiling: StateFlow<Float> = _ceiling.asStateFlow()

    /** Whether the safety limiter is actively clamping gain. */
    val limiterEnabled: StateFlow<Boolean> = _limiterEnabled.asStateFlow()

    /** Whether expert mode (elevated ceiling) is active. */
    val expertMode: StateFlow<Boolean> = _expertMode.asStateFlow()

    /** Emits [SafetyEvent]s such as [SafetyEvent.ExposureWarning]. */
    val safetyEvents: StateFlow<SafetyEvent?> = _safetyEvents.asStateFlow()

    // Exposure tracking
    private var loudStartMs: Long? = null

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Clamp [level] to the current ceiling.
     * When the limiter is disabled the gain passes through unclamped (bounded to [0, 1]).
     */
    fun clamp(level: Float): Float =
        if (_limiterEnabled.value) level.coerceIn(0f, _ceiling.value)
        else level.coerceIn(0f, 1f)

    /** Enable or disable the safety limiter. When disabled, gain is uncapped up to full range. */
    fun setLimiterEnabled(enabled: Boolean) {
        _limiterEnabled.value = enabled
        if (!enabled) {
            _ceiling.value = EXPERT_CEILING   // raise ceiling so SafetyLimiter math stays consistent
            Log.w(TAG, "Safety limiter disabled — full range active")
        } else {
            _ceiling.value = DEFAULT_CEILING
            Log.d(TAG, "Safety limiter enabled — ceiling restored to ${DEFAULT_CEILING}")
        }
    }

    /**
     * Enable expert mode after the user has acknowledged the safety warning.
     * Raises the ceiling to [EXPERT_CEILING].
     */
    fun enableExpertMode() {
        _expertMode.value = true
        _ceiling.value = EXPERT_CEILING
        Log.w(TAG, "Expert mode enabled — ceiling raised to ${EXPERT_CEILING}")
    }

    /**
     * Disable expert mode and restore the [DEFAULT_CEILING].
     */
    fun disableExpertMode() {
        _expertMode.value = false
        _ceiling.value = DEFAULT_CEILING
        Log.d(TAG, "Expert mode disabled — ceiling restored to ${DEFAULT_CEILING}")
    }

    /**
     * Set a custom ceiling (only permitted in expert mode).
     * [ceiling] is clamped to [0.0, EXPERT_CEILING].
     */
    fun setCeiling(ceiling: Float) {
        if (!_expertMode.value) {
            Log.w(TAG, "Ignoring custom ceiling — expert mode not active")
            return
        }
        _ceiling.value = ceiling.coerceIn(0f, EXPERT_CEILING)
    }

    /**
     * Should be called each time the gain is actively applied.
     * Tracks loud exposure duration and fires [SafetyEvent.ExposureWarning]
     * if the user has been listening loudly for [EXPOSURE_WARNING_MINUTES].
     */
    fun recordExposureTick(level: Float) {
        if (level >= LOUD_THRESHOLD) {
            val now = System.currentTimeMillis()
            val start = loudStartMs ?: run {
                loudStartMs = now
                now
            }
            val elapsedMinutes = (now - start) / 60_000L
            if (elapsedMinutes >= EXPOSURE_WARNING_MINUTES) {
                _safetyEvents.value = SafetyEvent.ExposureWarning(elapsedMinutes)
                loudStartMs = null  // Reset after warning
                Log.w(TAG, "Exposure warning fired after ${elapsedMinutes}min")
            }
        } else {
            loudStartMs = null  // Reset when level drops
        }
    }

    /** Consume the latest safety event (acknowledges it). */
    fun clearEvent() {
        _safetyEvents.value = null
    }
}

/** Events emitted by [SafetyLimiter]. */
sealed interface SafetyEvent {
    /** Fired after prolonged loud exposure. [minutes] is total elapsed loud time. */
    data class ExposureWarning(val minutes: Long) : SafetyEvent
}
