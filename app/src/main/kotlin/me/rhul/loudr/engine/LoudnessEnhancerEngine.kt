package me.rhul.loudr.engine

import android.media.audiofx.LoudnessEnhancer
import android.util.Log
import me.rhul.loudr.safety.SafetyLimiter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "LoudnessEnhancerEngine"

/**
 * Non-root audio boost engine using [LoudnessEnhancer].
 *
 * Maps the normalised [0.0, 1.0] boost level to a millibel gain in [0, MAX_GAIN_MB].
 * The gain ceiling is clamped by [SafetyLimiter] before being applied.
 *
 * A single [LoudnessEnhancer] instance is created per audio session and
 * released when [detachFromSession] is called or the session ends.
 */
@Singleton
class LoudnessEnhancerEngine @Inject constructor(
    private val safetyLimiter: SafetyLimiter,
) : AudioEngineRepository {

    companion object {
        /** Maximum gain in millibels — 2000 mB ≈ +20 dB (~300% boost). */
        private const val MAX_GAIN_MB = 2000
    }

    private val _boostLevel     = MutableStateFlow(0f)
    private val _isActive       = MutableStateFlow(false)
    private val _enabledStreams = MutableStateFlow(AudioStream.DEFAULT_ENABLED)
    private val _currentSession = MutableStateFlow(-1)

    override val boostLevel:      StateFlow<Float>          = _boostLevel.asStateFlow()
    override val isActive:        StateFlow<Boolean>         = _isActive.asStateFlow()
    override val enabledStreams:   StateFlow<Set<AudioStream>> = _enabledStreams.asStateFlow()
    override val currentSessionId: StateFlow<Int>            = _currentSession.asStateFlow()

    /** Guarded by the caller — all mutations happen on Dispatchers.IO. */
    private var enhancer: LoudnessEnhancer? = null

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    override suspend fun setBoost(level: Float) {
        // Clamp through the safety limiter so _boostLevel never exceeds the active ceiling.
        // This makes the arc slider physically stop at the ceiling, not just the audio output.
        val clamped = safetyLimiter.clamp(level.coerceIn(0f, 1f))
        _boostLevel.value = clamped
        applyGain(clamped)
    }

    override suspend fun enable() {
        // Attach to the global session (0) if no specific session is active yet.
        // AudioSessionMonitor will re-attach to the correct app session when detected.
        if (_currentSession.value == -1) {
            attachToSession(0)
        }
        _isActive.value = true
        applyGain(_boostLevel.value)
        Log.d(TAG, "Engine enabled — session=${_currentSession.value} boost=${_boostLevel.value}")
    }

    override suspend fun disable() {
        _isActive.value = false
        enhancer?.setEnabled(false)
        Log.d(TAG, "Engine disabled")
    }

    override suspend fun setStreamEnabled(stream: AudioStream, enabled: Boolean) {
        val current = _enabledStreams.value.toMutableSet()
        if (enabled) current.add(stream) else current.remove(stream)
        _enabledStreams.value = current
        // Re-apply: if the affected stream is MEDIA (the primary session carrier),
        // toggle the enhancer accordingly.
        if (stream == AudioStream.MEDIA) {
            if (enabled && _isActive.value) applyGain(_boostLevel.value)
            else enhancer?.setEnabled(false)
        }
    }

    override fun attachToSession(sessionId: Int) {
        if (sessionId == _currentSession.value) return
        releaseEnhancer()
        _currentSession.value = sessionId
        try {
            enhancer = LoudnessEnhancer(sessionId).also { e ->
                e.setEnabled(_isActive.value)
                if (_isActive.value) e.setTargetGain(toMillibels(_boostLevel.value))
            }
            Log.d(TAG, "Attached to session $sessionId")
        } catch (ex: RuntimeException) {
            Log.e(TAG, "Failed to attach LoudnessEnhancer to session $sessionId", ex)
        }
    }

    override fun detachFromSession() {
        releaseEnhancer()
        _currentSession.value = -1
        Log.d(TAG, "Detached from session")
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private fun applyGain(normalised: Float) {
        val enhancerRef = enhancer ?: return
        if (!_isActive.value) return
        val limitedNorm = safetyLimiter.clamp(normalised)
        val mB = toMillibels(limitedNorm)
        try {
            enhancerRef.setTargetGain(mB)
            enhancerRef.setEnabled(true)
            Log.d(TAG, "Gain applied: ${mB}mB (${(limitedNorm * 100).toInt()}%)")
        } catch (ex: RuntimeException) {
            Log.e(TAG, "Failed to apply gain", ex)
        }
    }

    private fun toMillibels(normalised: Float): Int =
        (normalised * MAX_GAIN_MB).toInt()

    private fun releaseEnhancer() {
        try {
            enhancer?.release()
        } catch (ex: RuntimeException) {
            Log.w(TAG, "Exception releasing LoudnessEnhancer", ex)
        } finally {
            enhancer = null
        }
    }
}
