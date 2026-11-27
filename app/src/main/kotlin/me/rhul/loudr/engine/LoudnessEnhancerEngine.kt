package me.rhul.loudr.engine

import android.media.audiofx.LoudnessEnhancer
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import me.rhul.loudr.safety.SafetyLimiter
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
 * Thread-safety: [lock] serialises all reads and writes to [enhancer] and
 * [_currentSession], and heavy AudioEffect Binder calls are dispatched on [Dispatchers.IO].
 *
 * Global session 0 fallback is used when no app-specific session ID is available.
 */
@Singleton
class LoudnessEnhancerEngine @Inject constructor(
    private val safetyLimiter: SafetyLimiter,
) : AudioEngineRepository {

    companion object {
        /** Maximum gain in millibels — 2000 mB ≈ +20 dB (+300% boost). */
        private const val MAX_GAIN_MB = 2000
    }

    private val _boostLevel     = MutableStateFlow(0f)
    private val _isActive       = MutableStateFlow(false)
    private val _currentSession = MutableStateFlow(-1)

    override val boostLevel:       StateFlow<Float>   = _boostLevel.asStateFlow()
    override val isActive:         StateFlow<Boolean> = _isActive.asStateFlow()
    override val currentSessionId: StateFlow<Int>     = _currentSession.asStateFlow()

    /**
     * Guards all [enhancer] and [_currentSession] reads and writes.
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
        withContext(Dispatchers.IO) {
            lock.withLock {
                applyGainLocked(clamped)
            }
        }
    }

    override suspend fun enable() {
        withContext(Dispatchers.IO) {
            lock.withLock {
                _isActive.value = true
                val session = _currentSession.value
                if (session != -1 && enhancer != null) {
                    applyGainLocked(_boostLevel.value)
                } else {
                    attachToSessionLocked(if (session != -1) session else 0)
                }
            }
            Log.d(TAG, "Engine enabled — session=${_currentSession.value} boost=${_boostLevel.value}")
        }
    }

    override suspend fun disable() {
        withContext(Dispatchers.IO) {
            lock.withLock {
                _isActive.value = false
                try {
                    enhancer?.setEnabled(false)
                } catch (ex: RuntimeException) {
                    Log.w(TAG, "Exception disabling enhancer", ex)
                }
            }
            Log.d(TAG, "Engine disabled")
        }
    }

    override fun attachToSession(sessionId: Int) {
        lock.withLock {
            attachToSessionLocked(sessionId)
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
    // Internal helpers (caller must hold [lock])
    // -------------------------------------------------------------------------

    private fun attachToSessionLocked(sessionId: Int) {
        if (sessionId == _currentSession.value && enhancer != null) return
        releaseEnhancerLocked()
        try {
            val limitedNorm = safetyLimiter.clamp(_boostLevel.value)
            val created = LoudnessEnhancer(sessionId).also { e ->
                if (_isActive.value) {
                    e.setTargetGain(toMillibels(limitedNorm))
                }
                e.setEnabled(_isActive.value)
            }
            enhancer = created
            _currentSession.value = sessionId
            Log.d(TAG, "Attached to session $sessionId")
        } catch (ex: RuntimeException) {
            _currentSession.value = -1
            Log.e(TAG, "Failed to attach LoudnessEnhancer to session $sessionId", ex)
        }
    }

    private fun applyGainLocked(normalised: Float) {
        if (!_isActive.value) return
        val e = enhancer ?: return
        val limitedNorm = safetyLimiter.clamp(normalised)
        val mB = toMillibels(limitedNorm)
        try {
            e.setTargetGain(mB)
            e.setEnabled(true)
            Log.d(TAG, "Gain applied: ${mB}mB (${(limitedNorm * 100).toInt()}%)")
        } catch (ex: RuntimeException) {
            Log.e(TAG, "Failed to apply gain", ex)
        }
    }

    private fun toMillibels(normalised: Float): Int =
        (normalised * MAX_GAIN_MB).toInt()

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
