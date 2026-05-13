package me.rhul.loudr.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import me.rhul.loudr.R
import me.rhul.loudr.service.ACTION_TOGGLE_BOOST
import me.rhul.loudr.service.ACTION_BOOST_UP
import me.rhul.loudr.service.ACTION_BOOST_DOWN
import me.rhul.loudr.service.VolumeBoostService
import android.app.PendingIntent
import android.content.ComponentName
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import me.rhul.loudr.engine.AudioEngineRepository

/**
 * Home screen widget provider.
 *
 * L-5 fix: replaced @AndroidEntryPoint / @Inject field injection with
 * [EntryPointAccessors]. AppWidgetProviders are BroadcastReceivers and can be
 * instantiated by the OS before Hilt's application component is ready, causing
 * UninitializedPropertyAccessException with field injection.
 */
class BoostWidgetProvider : AppWidgetProvider() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface BoostWidgetEntryPoint {
        fun engine(): AudioEngineRepository
    }

    private fun engine(context: Context): AudioEngineRepository =
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            BoostWidgetEntryPoint::class.java,
        ).engine()

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val eng = engine(context)
        val isActive = eng.isActive.value
        val boostPct = (eng.boostLevel.value * 300).toInt()

        appWidgetIds.forEach { appWidgetId ->
            updateWidget(context, appWidgetManager, appWidgetId, isActive, boostPct)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action
        if (action == ACTION_TOGGLE_BOOST || action == ACTION_BOOST_UP || action == ACTION_BOOST_DOWN) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, VolumeBoostService::class.java).apply {
                    this.action = action
                },
            )
        }
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    companion object {
        fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            isActive: Boolean,
            boostPct: Int,
        ) {
            val toggleIntent = Intent(context, BoostWidgetProvider::class.java).apply {
                action = ACTION_TOGGLE_BOOST
            }
            val upIntent = Intent(context, BoostWidgetProvider::class.java).apply {
                action = ACTION_BOOST_UP
            }
            val downIntent = Intent(context, BoostWidgetProvider::class.java).apply {
                action = ACTION_BOOST_DOWN
            }

            val togglePendingIntent = PendingIntent.getBroadcast(
                context, appWidgetId, toggleIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val upPendingIntent = PendingIntent.getBroadcast(
                context, appWidgetId + 1000, upIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val downPendingIntent = PendingIntent.getBroadcast(
                context, appWidgetId + 2000, downIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            val views = RemoteViews(context.packageName, R.layout.widget_boost).apply {
                setOnClickPendingIntent(R.id.widget_toggle_button, togglePendingIntent)
                setOnClickPendingIntent(R.id.widget_btn_up, upPendingIntent)
                setOnClickPendingIntent(R.id.widget_btn_down, downPendingIntent)

                val btnText = if (isActive) "$boostPct%" else "OFF"
                setTextViewText(R.id.widget_text_value, btnText)

                val textColor = if (isActive)
                    ContextCompat.getColor(context, R.color.ic_launcher_foreground)
                else
                    ContextCompat.getColor(context, android.R.color.white)
                setTextColor(R.id.widget_text_value, textColor)
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        fun updateAllWidgets(context: Context, isActive: Boolean, boostPct: Int) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, BoostWidgetProvider::class.java))
            ids.forEach { id ->
                updateWidget(context, manager, id, isActive, boostPct)
            }
        }
    }
}
