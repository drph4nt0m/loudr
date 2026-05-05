package me.rhul.loudr.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import me.rhul.loudr.R
import me.rhul.loudr.service.ACTION_TOGGLE_BOOST
import me.rhul.loudr.service.VolumeBoostService
import android.app.PendingIntent

/**
 * Home screen widget provider.
 *
 * Supports two sizes:
 *  - 1×1: power toggle button only
 *  - 2×1: toggle + current boost percentage label
 *
 * Widget state is driven by the last known values from [VolumeBoostService].
 * Tapping the toggle sends [ACTION_TOGGLE_BOOST] to the service.
 */
class BoostWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context:          Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds:     IntArray,
    ) {
        appWidgetIds.forEach { appWidgetId ->
            updateWidget(context, appWidgetManager, appWidgetId)
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

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    companion object {
        fun updateWidget(
            context:          Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId:      Int,
        ) {
            val toggleIntent = Intent(context, BoostWidgetProvider::class.java).apply {
                action = ACTION_TOGGLE_BOOST
            }
            val togglePendingIntent = PendingIntent.getBroadcast(
                context, appWidgetId, toggleIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            val views = RemoteViews(context.packageName, R.layout.widget_boost).apply {
                setOnClickPendingIntent(R.id.widget_toggle_button, togglePendingIntent)
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
