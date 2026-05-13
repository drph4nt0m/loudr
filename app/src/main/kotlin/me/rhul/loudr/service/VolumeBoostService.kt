package me.rhul.loudr.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import me.rhul.loudr.R
import me.rhul.loudr.engine.AudioEngineRepository
import me.rhul.loudr.ui.MainActivity
import me.rhul.loudr.widget.BoostWidgetProvider
import me.rhul.loudr.widget.ToggleWidgetProvider
import me.rhul.loudr.data.PreferencesRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

private const val TAG             = "VolumeBoostService"
private const val CHANNEL_ID      = "volumax_boost_channel"
private const val NOTIFICATION_ID = 1001

/** Intent action to toggle boost from the notification. */
const val ACTION_TOGGLE_BOOST = "me.rhul.loudr.action.TOGGLE_BOOST"

/** Intent action to increment boost by 10%. */
const val ACTION_BOOST_UP = "me.rhul.loudr.action.BOOST_UP"

/** Intent action to decrement boost by 10%. */
const val ACTION_BOOST_DOWN = "me.rhul.loudr.action.BOOST_DOWN"

/**
 * Persistent foreground service that keeps [AudioEngineRepository] alive
 * while the user is listening to audio.
 *
 * The service shows a minimal persistent notification with three quick actions:
 * boost down (−10%), toggle on/off, and boost up (+10%).
 *
 * The service stops itself when the user explicitly disables the boost via
 * the toggle action or the app's UI. It does NOT auto-stop on audio focus loss
 * to preserve state across calls and notifications.
 */
@AndroidEntryPoint
class VolumeBoostService : Service() {

    @Inject
    lateinit var engine: AudioEngineRepository

    @Inject
    lateinit var boostController: me.rhul.loudr.engine.BoostController

    @Inject
    lateinit var prefs: PreferencesRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    private var isNotificationEnabled = true

    // -------------------------------------------------------------------------
    // Service lifecycle
    // -------------------------------------------------------------------------

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        
        // Fulfill the startForegroundService contract immediately
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                buildNotification(),
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, buildNotification())
        }

        serviceScope.launch {
            prefs.notificationEnabled.collect { enabled ->
                isNotificationEnabled = enabled
                if (enabled) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        startForeground(
                            NOTIFICATION_ID,
                            buildNotification(),
                            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                        )
                    } else {
                        startForeground(NOTIFICATION_ID, buildNotification())
                    }
                } else {
                    stopForeground(STOP_FOREGROUND_REMOVE)
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
        when (intent?.action) {
            ACTION_TOGGLE_BOOST -> { handleToggle(); reviveNotification() }
            ACTION_BOOST_UP     -> { handleBoostStep(10); reviveNotification() }
            ACTION_BOOST_DOWN   -> { handleBoostStep(-10); reviveNotification() }
            null -> {
                // Triggered by app open. If notification is enabled, bring it back
                // in case it was dismissed by the user.
                reviveNotification()
            }
        }
        // Notification updates are handled by the state collectors in onCreate.
        return START_STICKY
    }

    override fun onDestroy() {
        // Disable the audio engine BEFORE cancelling the scope.
        // A launch into a cancelled scope is silently dropped — using runBlocking
        // here is intentional: onDestroy has a strict time budget but disable() is fast.
        runBlocking { engine.disable() }
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

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Loudr Boost",
            NotificationManager.IMPORTANCE_LOW,  // Silent — no sound, no vibration
        ).apply {
            description = "Live boost status and quick controls"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val isActive   = engine.isActive.value
        val boostPct   = (engine.boostLevel.value * 300).toInt()
        
        val title = if (isActive) "Loudr: +$boostPct%" else "Loudr is paused"
        val statusText = if (isActive) "Audio booster is running" else "Tap Start to boost audio"

        val openAppIntent = PendingIntent.getActivity(
            this, 0,
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
            .addAction(R.drawable.ic_volume_down, "−10%", downIntent)
            .addAction(
                if (isActive) R.drawable.ic_boost_off else R.drawable.ic_boost_on,
                if (isActive) "Stop" else "Start",
                toggleIntent,
            )
            .addAction(R.drawable.ic_volume_up, "+10%", upIntent)
            .build()
    }

    private fun reviveNotification() {
        if (!isNotificationEnabled) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                buildNotification(),
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, buildNotification())
        }
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
        PendingIntent.getService(
            this, requestCode,
            Intent(this, VolumeBoostService::class.java).apply { this.action = action },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
