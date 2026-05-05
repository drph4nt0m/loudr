package me.rhul.loudr.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import me.rhul.loudr.R
import me.rhul.loudr.engine.AudioEngineRepository
import me.rhul.loudr.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
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

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // -------------------------------------------------------------------------
    // Service lifecycle
    // -------------------------------------------------------------------------

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        Log.d(TAG, "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE_BOOST -> handleToggle()
            ACTION_BOOST_UP     -> handleBoostStep(+0.10f)
            ACTION_BOOST_DOWN   -> handleBoostStep(-0.10f)
        }
        updateNotification()
        return START_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        serviceScope.launch { engine.disable() }
        Log.d(TAG, "Service destroyed")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // -------------------------------------------------------------------------
    // Action handlers
    // -------------------------------------------------------------------------

    private fun handleToggle() {
        serviceScope.launch(Dispatchers.IO) {
            if (engine.isActive.value) engine.disable() else engine.enable()
        }
    }

    private fun handleBoostStep(delta: Float) {
        serviceScope.launch(Dispatchers.IO) {
            val next = (engine.boostLevel.value + delta).coerceIn(0f, 1f)
            engine.setBoost(next)
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
        val statusText = if (isActive) "+$boostPct% · Tap to open" else "Paused · Tap to activate"

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
            .setContentTitle("Loudr")
            .setContentText(statusText)
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setSilent(true)
            .addAction(R.drawable.ic_volume_down, "−10%", downIntent)
            .addAction(
                if (isActive) R.drawable.ic_boost_off else R.drawable.ic_boost_on,
                if (isActive) "Off" else "On",
                toggleIntent,
            )
            .addAction(R.drawable.ic_volume_up, "+10%", upIntent)
            .build()
    }

    private fun updateNotification() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun pendingServiceIntent(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            this, requestCode,
            Intent(this, VolumeBoostService::class.java).apply { this.action = action },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
