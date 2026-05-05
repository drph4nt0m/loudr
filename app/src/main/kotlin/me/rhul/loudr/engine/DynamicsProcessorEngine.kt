package me.rhul.loudr.engine

import android.media.audiofx.DynamicsProcessing
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "DynamicsProcessorEngine"

/**
 * Minimal bass boost engine using [DynamicsProcessing] (API 28+).
 *
 * Only a single low-frequency gain stage is configured — keeping it simple
 * and avoiding the complexity of a full multi-band EQ. The post-stage
 * compressor prevents clipping on cheaper speakers at high gain levels.
 *
 * This engine is independent of [LoudnessEnhancerEngine] and is only
 * activated when the user enables bass boost in Settings.
 */
@Singleton
@RequiresApi(Build.VERSION_CODES.P)
class DynamicsProcessorEngine @Inject constructor() {

    companion object {
        private const val BASS_CUTOFF_HZ    = 200f   // Low-band upper bound
        private const val MAX_BASS_GAIN_DB  = 10f    // +10 dB max bass boost
        private const val COMPRESSOR_THRESHOLD_DB = -6f
        private const val COMPRESSOR_RATIO   = 4f
    }

    private var processor: DynamicsProcessing? = null
    private var currentSessionId: Int = -1
    private var isEnabled: Boolean = false
    private var currentLevel: Float = 0f  // normalised [0.0, 1.0]

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /** Attach to an audio [sessionId] and configure the processing chain. */
    fun attachToSession(sessionId: Int) {
        if (sessionId == currentSessionId) return
        release()
        currentSessionId = sessionId
        buildProcessor(sessionId)
        Log.d(TAG, "DynamicsProcessor attached to session $sessionId")
    }

    /** Detach and release all resources. */
    fun detachFromSession() {
        release()
        currentSessionId = -1
    }

    /** Apply a normalised bass boost [level] in [0.0, 1.0]. */
    fun setBassBoost(level: Float) {
        currentLevel = level.coerceIn(0f, 1f)
        applyBassGain()
    }

    /** Enable or disable the bass boost stage without changing the level. */
    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
        processor?.setEnabled(enabled)
        if (enabled) applyBassGain()
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    private fun buildProcessor(sessionId: Int) {
        val config = DynamicsProcessing.Config.Builder(
            DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
            /* channelCount = */ 2,
            /* preEqInUse = */ false,
            /* preEqBandCount = */ 0,
            /* mbcInUse = */ false,
            /* mbcBandCount = */ 0,
            /* postEqInUse = */ true,
            /* postEqBandCount = */ 1,
            /* limiterInUse = */ true,
        ).build()

        try {
            processor = DynamicsProcessing(0, sessionId, config).also { dp ->
                // Configure a single post-EQ band for bass
                val band = DynamicsProcessing.EqBand(
                    /* isEnabled = */ true,
                    /* cutoffFrequency = */ BASS_CUTOFF_HZ,
                    /* gain = */ 0f,
                )
                dp.setPostEqBandAllChannelsTo(0, band)

                // Configure output limiter to avoid clipping
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
            Log.e(TAG, "Failed to build DynamicsProcessing for session $sessionId", ex)
        }
    }

    private fun applyBassGain() {
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

    private fun release() {
        try {
            processor?.release()
        } catch (ex: RuntimeException) {
            Log.w(TAG, "Exception releasing DynamicsProcessing", ex)
        } finally {
            processor = null
        }
    }
}
