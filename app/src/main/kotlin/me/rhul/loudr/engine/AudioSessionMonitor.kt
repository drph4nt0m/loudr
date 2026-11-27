package me.rhul.loudr.engine

import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.util.Log
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.rhul.loudr.di.ApplicationScope

private const val TAG = "AudioSessionMonitor"

/**
 * Dynamically registered receiver (owned by [me.rhul.loudr.service.VolumeBoostService])
 * that listens for wired headset and Bluetooth audio connection changes and delegates
 * to [BoostController].
 *
 * Dynamic registration is required because [AudioManager.ACTION_HEADSET_PLUG] is
 * delivered only to dynamically registered receivers (FLAG_RECEIVER_REGISTERED_ONLY),
 * and implicit Bluetooth broadcasts are restricted for manifest receivers on API 26+.
 *
 * Uses [goAsync] + [@ApplicationScope] instead of `runBlocking` so DataStore reads
 * never block the main thread.
 */
class AudioSessionMonitor : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface AudioSessionMonitorEntryPoint {
        fun boostController(): BoostController
        @ApplicationScope
        fun appScope(): CoroutineScope
    }

    private fun deps(context: Context): AudioSessionMonitorEntryPoint =
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            AudioSessionMonitorEntryPoint::class.java,
        )

    override fun onReceive(context: Context, intent: Intent) {
        // Ignore the initial sticky broadcast fired immediately upon dynamic registration
        // so starting the service does not spuriously connect/disconnect the audio engine.
        if (isInitialStickyBroadcast) return

        val isConnected: Boolean? = when (intent.action) {
            AudioManager.ACTION_HEADSET_PLUG -> when (intent.getIntExtra("state", -1)) {
                1    -> true
                0    -> false
                else -> null
            }

            BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED,
            BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED -> when (
                intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1)
            ) {
                BluetoothProfile.STATE_CONNECTED    -> true
                BluetoothProfile.STATE_DISCONNECTED -> false
                else                                -> null
            }

            else -> {
                Log.w(TAG, "Unhandled action: ${intent.action}")
                null
            }
        }

        if (isConnected == null) return

        val entryPoint = deps(context)
        val pendingResult = goAsync()
        entryPoint.appScope().launch {
            try {
                if (isConnected) {
                    Log.d(TAG, "Audio device connected (${intent.action})")
                    entryPoint.boostController().onAudioDeviceConnected()
                } else {
                    Log.d(TAG, "Audio device disconnected (${intent.action})")
                    entryPoint.boostController().onAudioDeviceDisconnected()
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        fun intentFilter(): IntentFilter = IntentFilter().apply {
            addAction(AudioManager.ACTION_HEADSET_PLUG)
            addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
        }
    }
}
