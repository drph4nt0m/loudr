package me.rhul.loudr.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

private const val TAG = "AudioSessionMonitor"

/**
 * Listens for system audio events and drives [AudioEngineRepository] session
 * attachment / detachment accordingly.
 *
 * Registered in AndroidManifest for headset and Bluetooth events.
 * The audio session ID is carried in [AudioManager.EXTRA_AUDIO_SESSION] on
 * [AudioManager.ACTION_AUDIO_SESSION_CHANGED] — no RECORD_AUDIO needed.
 *
 * For headset events, the session is not carried; we simply disable the boost
 * on disconnect (to avoid distortion on built-in speakers) and re-enable when
 * a wired headset reconnects.
 */
@AndroidEntryPoint
class AudioSessionMonitor : BroadcastReceiver() {

    @Inject
    lateinit var engine: AudioEngineRepository

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            // Wired headset plug/unplug
            AudioManager.ACTION_HEADSET_PLUG -> handleHeadsetPlug(intent)

            // Bluetooth A2DP connect/disconnect
            android.bluetooth.BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED ->
                handleBluetoothA2dp(intent)

            // Bluetooth SCO (call audio) connect/disconnect
            android.bluetooth.BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED ->
                handleBluetoothHeadset(intent)

            else -> Log.w(TAG, "Unhandled action: ${intent.action}")
        }
    }

    // -------------------------------------------------------------------------
    // Event handlers
    // -------------------------------------------------------------------------

    private fun handleHeadsetPlug(intent: Intent) {
        val state = intent.getIntExtra("state", -1)
        when (state) {
            1 -> {
                // Headset connected — re-attach to current session if any
                val sessionId = engine.currentSessionId.value
                if (sessionId != -1) {
                    engine.attachToSession(sessionId)
                    Log.d(TAG, "Wired headset connected — re-attached to session $sessionId")
                }
            }
            0 -> {
                // Headset disconnected — detach to prevent distortion on speakers
                engine.detachFromSession()
                Log.d(TAG, "Wired headset disconnected — engine detached")
            }
        }
    }

    private fun handleBluetoothA2dp(intent: Intent) {
        val state = intent.getIntExtra(
            android.bluetooth.BluetoothProfile.EXTRA_STATE,
            android.bluetooth.BluetoothProfile.STATE_DISCONNECTED,
        )
        when (state) {
            android.bluetooth.BluetoothProfile.STATE_CONNECTED -> {
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

    private fun handleBluetoothHeadset(intent: Intent) {
        val state = intent.getIntExtra(
            android.bluetooth.BluetoothProfile.EXTRA_STATE,
            android.bluetooth.BluetoothProfile.STATE_DISCONNECTED,
        )
        when (state) {
            android.bluetooth.BluetoothProfile.STATE_CONNECTED -> {
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
