package com.example.taptix.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Internal non-exported BroadcastReceiver handling widget button click actions.
 * Enforces strict sandbox isolation: cannot be invoked by external applications.
 */
class TaptixWidgetActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == TaptixWidgetProvider.ACTION_TOGGLE_MASTER) {
            TaptixWidgetProvider.handleToggleMaster(context)
        }
    }
}
