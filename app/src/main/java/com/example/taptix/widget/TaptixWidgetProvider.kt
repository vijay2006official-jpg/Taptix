package com.example.taptix.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.util.Log
import android.widget.RemoteViews
import com.example.taptix.MainActivity
import com.example.taptix.MapZoneActivity
import com.example.taptix.R
import com.example.taptix.data.PreferencesRepository
import com.example.taptix.service.AutoClickService
import com.example.taptix.service.OverlayService

/**
 * 1-Tap Home Screen Widget to toggle Taptix Master Auto-Accept and display active profile state.
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
            // Security: widgets use FLAG_IMMUTABLE PendingIntents so external apps cannot
            // easily forge them, but as defense-in-depth we also ignore intents that carry
            // a spoofed package name extra that doesn't match our own.
            val senderPkg = intent.getStringExtra("android.intent.extra.PACKAGE_NAME")
            if (senderPkg != null && senderPkg != context.packageName) {
                Log.w("TaptixWidget", "Rejected toggle from unknown sender: senderPkg")
                return
            }
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
                context.stopService(Intent(context, OverlayService::class.java)); AutoClickService.instance?.setClickingActive(false)
            }

            notifyStateChanged(context)
        }
    }

    companion object {
        const val ACTION_TOGGLE_MASTER = "com.example.taptix.ACTION_TOGGLE_MASTER"

        fun notifyStateChanged(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, TaptixWidgetProvider::class.java))
            for (id in ids) {
                updateAppWidget(context, manager, id)
            }
        }

        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val prefs = PreferencesRepository(context)
            val settings = prefs.getSettings()
            val isActive = settings.masterControlEnabled
            val activeProfile = prefs.getAreaGroups().firstOrNull { it.isSelected }?.name ?: "All Zones"

            val views = RemoteViews(context.packageName, R.layout.taptix_widget_layout)

            // Open app on root click
            val openAppIntent = Intent(context, MainActivity::class.java)
            val openAppPending = PendingIntent.getActivity(
                context, 0, openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, openAppPending)

            // Open Map Zones & Area Profiles on profile text click
            val openMapIntent = Intent(context, MapZoneActivity::class.java)
            val openMapPending = PendingIntent.getActivity(
                context, 1, openMapIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_profile, openMapPending)

            // Toggle on button click
            val toggleIntent = Intent(context, TaptixWidgetProvider::class.java).apply {
                action = ACTION_TOGGLE_MASTER
            }
            val togglePending = PendingIntent.getBroadcast(
                context, appWidgetId, toggleIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_toggle_btn, togglePending)

            views.setTextViewText(R.id.widget_profile, "Profile: $activeProfile")

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
