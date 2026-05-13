package me.rhul.loudr.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import me.rhul.loudr.R
import me.rhul.loudr.engine.AudioEngineRepository
import me.rhul.loudr.service.ACTION_TOGGLE_BOOST
import me.rhul.loudr.service.VolumeBoostService

/**
 * L-5 fix: replaced @AndroidEntryPoint / @Inject field injection with
 * [EntryPointAccessors] to avoid UninitializedPropertyAccessException when
 * the OS instantiates this receiver before Hilt's application component is ready.
 */
class ToggleWidgetProvider : AppWidgetProvider() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ToggleWidgetEntryPoint {
        fun engine(): AudioEngineRepository
    }

    private fun engine(context: Context): AudioEngineRepository =
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            ToggleWidgetEntryPoint::class.java,
        ).engine()

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val isActive = engine(context).isActive.value
        appWidgetIds.forEach { appWidgetId ->
            updateWidget(context, appWidgetManager, appWidgetId, isActive)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_TOGGLE_BOOST) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, VolumeBoostService::class.java).apply {
                    action = ACTION_TOGGLE_BOOST
                },
            )
        }
    }

    companion object {
        fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            isActive: Boolean,
        ) {
            val toggleIntent = Intent(context, ToggleWidgetProvider::class.java).apply {
                action = ACTION_TOGGLE_BOOST
            }
            val togglePendingIntent = PendingIntent.getBroadcast(
                context, appWidgetId, toggleIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            val views = RemoteViews(context.packageName, R.layout.widget_toggle).apply {
                setOnClickPendingIntent(R.id.widget_toggle_container, togglePendingIntent)

                val color = if (isActive) {
                    ContextCompat.getColor(context, R.color.ic_launcher_foreground)
                } else {
                    ContextCompat.getColor(context, android.R.color.darker_gray)
                }
                setInt(R.id.widget_toggle_icon, "setColorFilter", color)
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        fun updateAllWidgets(context: Context, isActive: Boolean) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, ToggleWidgetProvider::class.java))
            ids.forEach { id ->
                updateWidget(context, manager, id, isActive)
            }
        }
    }
}
