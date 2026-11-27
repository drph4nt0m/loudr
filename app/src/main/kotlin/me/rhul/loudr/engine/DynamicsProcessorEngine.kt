package me.rhul.loudr.engine

import android.media.audiofx.DynamicsProcessing
import android.util.Log
import java.util.concurrent.locks.ReentrantLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.concurrent.withLock

private const val TAG = "DynamicsProcessorEngine"

/**
 * Minimal bass boost engine using [DynamicsProcessing] (API 28+).
 *
 * Only a single low-frequency gain stage is configured — keeping it simple
 * and avoiding the complexity of a full multi-band EQ. The post-stage
 * limiter prevents clipping on cheaper speakers at high gain levels.
 *
 * Thread-safety: [lock] serialises all [processor] lifecycle and parameter calls.
 */
@Singleton
class DynamicsProcessorEngine @Inject constructor() {

    companion object {
        private const val BASS_CUTOFF_HZ          = 200f   // Low-band upper bound
        private const val MAX_BASS_GAIN_DB        = 10f    // +10 dB max bass boost
        private const val COMPRESSOR_THRESHOLD_DB = -6f
        private const val COMPRESSOR_RATIO        = 4f

        /** Default normalised bass boost level (+6 dB) when the user toggles Bass Boost on. */
        const val DEFAULT_BASS_LEVEL              = 0.6f
    }

    private val lock = ReentrantLock()
    private var processor: DynamicsProcessing? = null
    private var currentSessionId: Int = -1
    private var isEnabled: Boolean = false
    private var currentLevel: Float = DEFAULT_BASS_LEVEL  // normalised [0.0, 1.0]

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /** Attach to an audio [sessionId] and configure the processing chain. */
    fun attachToSession(sessionId: Int) {
        lock.withLock {
            if (sessionId == currentSessionId && processor != null) return
            releaseLocked()
            val built = buildProcessorLocked(sessionId)
            processor = built
            currentSessionId = if (built != null) sessionId else -1
            if (built != null) {
                Log.d(TAG, "DynamicsProcessor attached to session $sessionId")
            }
        }
    }

    /** Detach and release all resources. */
    fun detachFromSession() {
        lock.withLock {
            releaseLocked()
            currentSessionId = -1
        }
    }

    /** Apply a normalised bass boost [level] in [0.0, 1.0]. */
    fun setBassBoost(level: Float) {
        lock.withLock {
            currentLevel = level.coerceIn(0f, 1f)
            applyBassGainLocked()
        }
    }

    /** Enable or disable the bass boost stage without changing the level. */
    fun setEnabled(enabled: Boolean) {
        lock.withLock {
            isEnabled = enabled
            if (enabled) applyBassGainLocked()
            try {
                processor?.setEnabled(enabled)
            } catch (ex: RuntimeException) {
                Log.w(TAG, "Failed to set DynamicsProcessing enabled=$enabled", ex)
            }
        }
    }

    // -------------------------------------------------------------------------
    // Internal (caller must hold [lock])
    // -------------------------------------------------------------------------

    private fun buildProcessorLocked(sessionId: Int): DynamicsProcessing? {
        // Try stereo first; fall back to mono if the session only has one channel.
        val built = buildProcessorWithChannels(sessionId, 2)
            ?: buildProcessorWithChannels(sessionId, 1)
        if (built == null) {
            Log.e(TAG, "Failed to build DynamicsProcessing for session $sessionId (tried 2ch and 1ch)")
        }
        return built
    }

    private fun buildProcessorWithChannels(sessionId: Int, channelCount: Int): DynamicsProcessing? {
        val config = DynamicsProcessing.Config.Builder(
            DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
            channelCount,
            /* preEqInUse = */ false,
            /* preEqBandCount = */ 0,
            /* mbcInUse = */ false,
            /* mbcBandCount = */ 0,
            /* postEqInUse = */ true,
            /* postEqBandCount = */ 1,
            /* limiterInUse = */ true,
        ).build()

        return try {
            DynamicsProcessing(0, sessionId, config).also { dp ->
                val initialGainDb = if (isEnabled) currentLevel * MAX_BASS_GAIN_DB else 0f
                val band = DynamicsProcessing.EqBand(
                    /* isEnabled = */ true,
                    /* cutoffFrequency = */ BASS_CUTOFF_HZ,
                    /* gain = */ initialGainDb,
                )
                dp.setPostEqBandAllChannelsTo(0, band)

                val limiter = DynamicsProcessing.Limiter(
                    /* inUse    = */ true,
                    /* isEnabled = */ true,
                    /* linkGroup = */ 0,
                    /* attackTime = */ 5f,
                    /* releaseTime = */ 150f,
                    /* ratio = */ COMPRESSOR_RATIO,
                    /* threshold = */ COMPRESSOR_THRESHOLD_DB,
                    /* postGain = */ 0f,
                )
                dp.setLimiterAllChannelsTo(limiter)
                dp.setEnabled(isEnabled)
            }
        } catch (ex: RuntimeException) {
            Log.w(TAG, "DynamicsProcessing failed with ${channelCount}ch for session $sessionId", ex)
            null
        }
    }

    private fun applyBassGainLocked() {
        val dp = processor ?: return
        if (!isEnabled) return
        val gainDb = currentLevel * MAX_BASS_GAIN_DB
        try {
            val band = DynamicsProcessing.EqBand(true, BASS_CUTOFF_HZ, gainDb)
            dp.setPostEqBandAllChannelsTo(0, band)
            Log.d(TAG, "Bass gain applied: ${gainDb}dB")
        } catch (ex: RuntimeException) {
            Log.e(TAG, "Failed to apply bass gain", ex)
        }
    }

    private fun releaseLocked() {
        try {
            processor?.release()
        } catch (ex: RuntimeException) {
            Log.w(TAG, "Exception releasing DynamicsProcessing", ex)
        } finally {
            processor = null
        }
    }
}
