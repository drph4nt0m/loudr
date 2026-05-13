package me.rhul.loudr.engine

import android.media.audiofx.LoudnessEnhancer
import android.util.Log
import me.rhul.loudr.safety.SafetyLimiter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.locks.ReentrantLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.concurrent.withLock

private const val TAG = "LoudnessEnhancerEngine"

/**
 * Non-root audio boost engine using [LoudnessEnhancer].
 *
 * Maps the normalised [0.0, 1.0] boost level to a millibel gain in [0, MAX_GAIN_MB].
 * The gain ceiling is clamped by [SafetyLimiter] before being applied.
 *
 * Thread-safety: [lock] serialises all reads and writes to [enhancer] since
 * [attachToSession] is called on the main thread (BroadcastReceiver) while
 * [setBoost] / [enable] / [disable] run on Dispatchers.IO.
 *
 * Session 0 fallback removed (Bug H-2): the engine now waits for a real session ID.
 * When [enable] is called before a session is available, [_isActive] is set to true
 * and gain is applied automatically in [attachToSession].
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
    private val _currentSession = MutableStateFlow(-1)

    override val boostLevel:       StateFlow<Float>   = _boostLevel.asStateFlow()
    override val isActive:         StateFlow<Boolean>  = _isActive.asStateFlow()
    override val currentSessionId: StateFlow<Int>      = _currentSession.asStateFlow()

    /**
     * Guards all [enhancer] reads and writes.
     * Always acquired non-interruptibly; hold time is minimal (AudioEffect API calls only).
     */
    private val lock = ReentrantLock()
    private var enhancer: LoudnessEnhancer? = null

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    override suspend fun setBoost(level: Float) {
        val clamped = safetyLimiter.clamp(level.coerceIn(0f, 1f))
        _boostLevel.value = clamped
        applyGain(clamped)
    }

    override suspend fun enable() {
        _isActive.value = true
        // Apply to existing session if available.
        // If no session yet, applyGain will be triggered inside attachToSession
        // because it checks _isActive.value.
        if (_currentSession.value != -1) {
            applyGain(_boostLevel.value)
        }
        Log.d(TAG, "Engine enabled — session=${_currentSession.value} boost=${_boostLevel.value}")
    }

    override suspend fun disable() {
        _isActive.value = false
        lock.withLock {
            try {
                enhancer?.setEnabled(false)
            } catch (ex: RuntimeException) {
                Log.w(TAG, "Exception disabling enhancer", ex)
            }
        }
        Log.d(TAG, "Engine disabled")
    }

    override fun attachToSession(sessionId: Int) {
        if (sessionId == _currentSession.value) return
        lock.withLock {
            releaseEnhancerLocked()
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
    }

    override fun detachFromSession() {
        lock.withLock {
            releaseEnhancerLocked()
            _currentSession.value = -1
        }
        Log.d(TAG, "Detached from session")
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private fun applyGain(normalised: Float) {
        if (!_isActive.value) return
        val limitedNorm = safetyLimiter.clamp(normalised)
        val mB = toMillibels(limitedNorm)
        lock.withLock {
            val e = enhancer ?: return
            try {
                e.setTargetGain(mB)
                e.setEnabled(true)
                Log.d(TAG, "Gain applied: ${mB}mB (${(limitedNorm * 100).toInt()}%)")
            } catch (ex: RuntimeException) {
                Log.e(TAG, "Failed to apply gain", ex)
            }
        }
    }

    private fun toMillibels(normalised: Float): Int =
        (normalised * MAX_GAIN_MB).toInt()

    /** Must be called with [lock] held. */
    private fun releaseEnhancerLocked() {
        val e = enhancer
        enhancer = null
        try {
            e?.release()
        } catch (ex: RuntimeException) {
            Log.w(TAG, "Exception releasing LoudnessEnhancer", ex)
        }
    }
}
