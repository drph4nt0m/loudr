package me.rhul.loudr.engine

import kotlinx.coroutines.flow.StateFlow

/**
 * Contract for the Loudr audio boost engine.
 *
 * All implementations are non-root only. Root support is explicitly
 * deferred to a future v2.0 effort and is not part of this interface.
 *
 * Implementations must be safe to call from any coroutine context; heavy
 * AudioEffect work should be dispatched to [kotlinx.coroutines.Dispatchers.IO].
 */
interface AudioEngineRepository {

    /** Normalised boost level: 0.0f = no boost, 1.0f = maximum (+1500 mB). */
    val boostLevel: StateFlow<Float>

    /** Whether the boost engine is currently active. */
    val isActive: StateFlow<Boolean>

    /** Current audio session ID the engine is attached to, or -1 if detached. */
    val currentSessionId: StateFlow<Int>

    /**
     * Set the boost gain. [level] must be in [0.0, 1.0].
     * Values are clamped by [me.rhul.loudr.safety.SafetyLimiter] before application.
     */
    suspend fun setBoost(level: Float)

    /** Activate the boost engine for all currently-enabled streams. */
    suspend fun enable()

    /** Deactivate the boost engine and release AudioEffect resources. */
    suspend fun disable()

    /**
     * Attach the engine to a specific audio [sessionId].
     * Called by [AudioSessionMonitor] when a new session is detected.
     */
    fun attachToSession(sessionId: Int)

    /**
     * Detach the engine from the current audio session and release effects.
     * Safe to call even if already detached.
     */
    fun detachFromSession()
}
