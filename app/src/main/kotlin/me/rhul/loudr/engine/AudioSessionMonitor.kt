package me.rhul.loudr.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.util.Log
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import me.rhul.loudr.data.PreferencesRepository

private const val TAG = "AudioSessionMonitor"

/**
 * Listens for system audio events and drives [AudioEngineRepository] session
 * attachment / detachment accordingly.
 *
 * Uses [EntryPointAccessors] instead of @AndroidEntryPoint / @Inject to safely
 * retrieve dependencies. Static BroadcastReceivers declared in the manifest can
 * be instantiated by the OS before the Hilt application component is ready on
 * some OEM ROMs and Android 12+ background-restriction paths, causing
 * UninitializedPropertyAccessException with field injection (Bug C-3).
 *
 * H-3 fix: headset reconnect now respects the [PreferencesRepository.autoBoostOnHeadphone]
 * preference before re-attaching the engine.
 */
class AudioSessionMonitor : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface AudioSessionMonitorEntryPoint {
        fun engine(): AudioEngineRepository
        fun prefs(): PreferencesRepository
    }

    private fun deps(context: Context): AudioSessionMonitorEntryPoint =
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            AudioSessionMonitorEntryPoint::class.java,
        )

    override fun onReceive(context: Context, intent: Intent) {
        val engine = deps(context).engine()
        when (intent.action) {
            AudioManager.ACTION_HEADSET_PLUG ->
                handleHeadsetPlug(context, intent, engine)

            android.bluetooth.BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED ->
                handleBluetoothA2dp(context, intent, engine)

            android.bluetooth.BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED ->
                handleBluetoothHeadset(context, intent, engine)

            else -> Log.w(TAG, "Unhandled action: ${intent.action}")
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Returns true when the user has opted into auto-boost on headphone connect. */
    private fun isAutoBoostEnabled(context: Context): Boolean =
        runBlocking { deps(context).prefs().autoBoostOnHeadphone.first() }

    private fun handleHeadsetPlug(context: Context, intent: Intent, engine: AudioEngineRepository) {
        val state = intent.getIntExtra("state", -1)
        when (state) {
            1 -> {
                if (!isAutoBoostEnabled(context)) {
                    Log.d(TAG, "Wired headset connected — auto-boost disabled, skipping re-attach")
                    return
                }
                val sessionId = engine.currentSessionId.value
                if (sessionId != -1) {
                    engine.attachToSession(sessionId)
                    Log.d(TAG, "Wired headset connected — re-attached to session $sessionId")
                }
            }
            0 -> {
                engine.detachFromSession()
                Log.d(TAG, "Wired headset disconnected — engine detached")
            }
        }
    }

    private fun handleBluetoothA2dp(context: Context, intent: Intent, engine: AudioEngineRepository) {
        val state = intent.getIntExtra(
            android.bluetooth.BluetoothProfile.EXTRA_STATE,
            android.bluetooth.BluetoothProfile.STATE_DISCONNECTED,
        )
        when (state) {
            android.bluetooth.BluetoothProfile.STATE_CONNECTED -> {
                if (!isAutoBoostEnabled(context)) {
                    Log.d(TAG, "BT A2DP connected — auto-boost disabled, skipping re-attach")
                    return
                }
                val sessionId = engine.currentSessionId.value
                if (sessionId != -1) engine.attachToSession(sessionId)
                Log.d(TAG, "BT A2DP connected — re-attached to session $sessionId")
            }
            android.bluetooth.BluetoothProfile.STATE_DISCONNECTED -> {
                engine.detachFromSession()
                Log.d(TAG, "BT A2DP disconnected — engine detached")
            }
        }
    }

    private fun handleBluetoothHeadset(context: Context, intent: Intent, engine: AudioEngineRepository) {
        val state = intent.getIntExtra(
            android.bluetooth.BluetoothProfile.EXTRA_STATE,
            android.bluetooth.BluetoothProfile.STATE_DISCONNECTED,
        )
        when (state) {
            android.bluetooth.BluetoothProfile.STATE_CONNECTED -> {
                if (!isAutoBoostEnabled(context)) {
                    Log.d(TAG, "BT Headset connected — auto-boost disabled, skipping re-attach")
                    return
                }
                val sessionId = engine.currentSessionId.value
                if (sessionId != -1) engine.attachToSession(sessionId)
                Log.d(TAG, "BT Headset connected — re-attached to session $sessionId")
            }
            android.bluetooth.BluetoothProfile.STATE_DISCONNECTED -> {
                engine.detachFromSession()
                Log.d(TAG, "BT Headset disconnected — engine detached")
            }
        }
    }
}
