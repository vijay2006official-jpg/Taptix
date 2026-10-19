package com.example.taptix.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.widget.RemoteViews
import com.example.taptix.MainActivity
import com.example.taptix.R
import com.example.taptix.data.PreferencesRepository
import com.example.taptix.service.AutoClickService
import com.example.taptix.service.OverlayService

/**
 * 1-Tap Home Screen Widget to toggle Taptix Master Auto-Accept without opening the app.
 */
class TaptixWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (widgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, widgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_TOGGLE_MASTER) {
            val prefs = PreferencesRepository(context)
            val currentSettings = prefs.getSettings()
            val newState = !currentSettings.masterControlEnabled

            prefs.saveMasterControl(newState)

            if (newState) {
                if (android.provider.Settings.canDrawOverlays(context)) {
                    val overlayIntent = Intent(context, OverlayService::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(overlayIntent)
                    } else {
                        context.startService(overlayIntent)
                    }
                }
                AutoClickService.instance?.setClickingActive(true)
            } else {
                AutoClickService.instance?.setClickingActive(false)
            }

            // Update all widgets
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, TaptixWidgetProvider::class.java))
            for (id in ids) {
                updateAppWidget(context, manager, id)
            }
        }
    }

    companion object {
        const val ACTION_TOGGLE_MASTER = "com.example.taptix.ACTION_TOGGLE_MASTER"

        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val prefs = PreferencesRepository(context)
            val settings = prefs.getSettings()
            val isActive = settings.masterControlEnabled

            val views = RemoteViews(context.packageName, R.layout.taptix_widget_layout)

            // Open app on root click
            val openAppIntent = Intent(context, MainActivity::class.java)
            val openAppPending = PendingIntent.getActivity(
                context, 0, openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, openAppPending)

            // Toggle on button click
            val toggleIntent = Intent(context, TaptixWidgetProvider::class.java).apply {
                action = ACTION_TOGGLE_MASTER
            }
            val togglePending = PendingIntent.getBroadcast(
                context, appWidgetId, toggleIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_toggle_btn, togglePending)

            if (isActive) {
                views.setTextViewText(R.id.widget_status, "🟢 ACTIVE • Auto-Accepting")
                views.setTextColor(R.id.widget_status, Color.parseColor("#00E676"))
                views.setTextViewText(R.id.widget_toggle_btn, "STOP")
            } else {
                views.setTextViewText(R.id.widget_status, "⚪ OFF • Tap to Start")
                views.setTextColor(R.id.widget_status, Color.parseColor("#8892A4"))
                views.setTextViewText(R.id.widget_toggle_btn, "START")
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
