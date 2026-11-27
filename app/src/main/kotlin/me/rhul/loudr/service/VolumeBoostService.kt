package me.rhul.loudr.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import me.rhul.loudr.R
import me.rhul.loudr.data.PreferencesRepository
import me.rhul.loudr.engine.AudioEngineRepository
import me.rhul.loudr.engine.AudioSessionMonitor
import me.rhul.loudr.engine.BoostController
import me.rhul.loudr.ui.MainActivity
import me.rhul.loudr.widget.BoostWidgetProvider
import me.rhul.loudr.widget.ToggleWidgetProvider
import javax.inject.Inject

private const val TAG                         = "VolumeBoostService"
private const val CHANNEL_ID                  = "volumax_boost_channel"
private const val NOTIFICATION_ID             = 1001
private const val EXTRA_STARTED_AS_FOREGROUND = "me.rhul.loudr.extra.STARTED_AS_FOREGROUND"

/** Intent action to toggle boost from the notification. */
const val ACTION_TOGGLE_BOOST = "me.rhul.loudr.action.TOGGLE_BOOST"

/** Intent action to increment boost by 10%. */
const val ACTION_BOOST_UP = "me.rhul.loudr.action.BOOST_UP"

/** Intent action to decrement boost by 10%. */
const val ACTION_BOOST_DOWN = "me.rhul.loudr.action.BOOST_DOWN"

/**
 * Persistent foreground service that keeps [AudioEngineRepository] alive
 * while the user is listening to audio, hosts the dynamic [AudioSessionMonitor]
 * for headphone/Bluetooth events, and exposes quick notification controls.
 */
@AndroidEntryPoint
class VolumeBoostService : Service() {

    @Inject
    lateinit var engine: AudioEngineRepository

    @Inject
    lateinit var boostController: BoostController

    @Inject
    lateinit var prefs: PreferencesRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val audioSessionMonitor = AudioSessionMonitor()
    private var receiverRegistered = false
    private var isNotificationEnabled = true

    // -------------------------------------------------------------------------
    // Service lifecycle
    // -------------------------------------------------------------------------

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        // Fulfill the startForegroundService contract immediately
        enterForeground()

        // Register dynamic audio-device receiver (required for ACTION_HEADSET_PLUG on Android)
        ContextCompat.registerReceiver(
            this,
            audioSessionMonitor,
            AudioSessionMonitor.intentFilter(),
            ContextCompat.RECEIVER_EXPORTED,
        )
        receiverRegistered = true

        // Hydrate persisted state in case the service started cold (e.g. START_STICKY / widget)
        serviceScope.launch(Dispatchers.IO) {
            boostController.ensureHydrated()
        }

        serviceScope.launch {
            prefs.notificationEnabled.collect { enabled ->
                isNotificationEnabled = enabled
                if (enabled) {
                    enterForeground()
                } else {
                    exitForeground()
                }
            }
        }

        // Single collector for both state flows — avoids double notification on every change.
        serviceScope.launch {
            combine(engine.isActive, engine.boostLevel) { _, _ -> Unit }.collect {
                updateNotification()
                updateWidgets()
            }
        }

        Log.d(TAG, "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val startedAsForeground = intent?.getBooleanExtra(EXTRA_STARTED_AS_FOREGROUND, false) == true

        if (isNotificationEnabled) {
            // Bring notification back if dismissed and satisfy startForegroundService contract
            enterForeground()
        } else if (startedAsForeground) {
            // Android requires startForeground() after startForegroundService() even if
            // the user disabled the persistent notification; enter then exit immediately.
            enterForeground()
            exitForeground()
        }

        when (intent?.action) {
            ACTION_TOGGLE_BOOST -> handleToggle()
            ACTION_BOOST_UP     -> handleBoostStep(10)
            ACTION_BOOST_DOWN   -> handleBoostStep(-10)
        }

        return START_STICKY
    }

    override fun onDestroy() {
        if (receiverRegistered) {
            try {
                unregisterReceiver(audioSessionMonitor)
            } catch (ex: IllegalArgumentException) {
                Log.w(TAG, "AudioSessionMonitor was already unregistered", ex)
            }
            receiverRegistered = false
        }

        // Release audio effects BEFORE cancelling the scope.
        // runBlocking is intentional: onDestroy has a strict budget and shutdown is fast.
        runBlocking { boostController.shutdown() }
        serviceScope.cancel()
        Log.d(TAG, "Service destroyed")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // -------------------------------------------------------------------------
    // Action handlers
    // -------------------------------------------------------------------------

    private fun handleToggle() {
        serviceScope.launch(Dispatchers.IO) {
            boostController.toggleActive()
        }
    }

    private fun handleBoostStep(deltaPct: Int) {
        serviceScope.launch(Dispatchers.IO) {
            boostController.stepBoost(deltaPct)
        }
    }

    // -------------------------------------------------------------------------
    // Notification
    // -------------------------------------------------------------------------

    private fun enterForeground() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun exitForeground() {
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW, // Silent — no sound, no vibration
        ).apply {
            description = getString(R.string.notification_channel_description)
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val isActive = engine.isActive.value
        val boostPct = (engine.boostLevel.value * 300).toInt()

        val title = if (isActive) {
            "${getString(R.string.app_name)}: +$boostPct%"
        } else {
            "${getString(R.string.app_name)} · ${getString(R.string.boost_off)}"
        }
        val statusText = if (isActive) {
            getString(R.string.boost_active)
        } else {
            getString(R.string.action_toggle)
        }

        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )

        val toggleIntent = pendingServiceIntent(ACTION_TOGGLE_BOOST, requestCode = 1)
        val upIntent     = pendingServiceIntent(ACTION_BOOST_UP,     requestCode = 2)
        val downIntent   = pendingServiceIntent(ACTION_BOOST_DOWN,   requestCode = 3)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_boost_notification)
            .setContentTitle(title)
            .setContentText(statusText)
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setSilent(true)
            .addAction(R.drawable.ic_volume_down, getString(R.string.action_boost_down), downIntent)
            .addAction(
                if (isActive) R.drawable.ic_boost_off else R.drawable.ic_boost_on,
                if (isActive) getString(R.string.action_stop) else getString(R.string.action_start),
                toggleIntent,
            )
            .addAction(R.drawable.ic_volume_up, getString(R.string.action_boost_up), upIntent)
            .build()
    }

    private fun updateNotification() {
        if (!isNotificationEnabled) return
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun updateWidgets() {
        val isActive = engine.isActive.value
        val boostPct = (engine.boostLevel.value * 300).toInt()
        BoostWidgetProvider.updateAllWidgets(this, isActive, boostPct)
        ToggleWidgetProvider.updateAllWidgets(this, isActive)
    }

    private fun pendingServiceIntent(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getForegroundService(
            this,
            requestCode,
            Intent(this, VolumeBoostService::class.java).apply {
                this.action = action
                putExtra(EXTRA_STARTED_AS_FOREGROUND, true)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    companion object {
        /**
         * Safely starts [VolumeBoostService] as a foreground service, catching
         * [IllegalStateException] (including Android 12+ ForegroundServiceStartNotAllowedException).
         */
        fun start(context: Context, action: String? = null) {
            val intent = Intent(context, VolumeBoostService::class.java).apply {
                this.action = action
                putExtra(EXTRA_STARTED_AS_FOREGROUND, true)
            }
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (ex: IllegalStateException) {
                Log.w(TAG, "Unable to start foreground service (action=$action)", ex)
            }
        }
    }
}
