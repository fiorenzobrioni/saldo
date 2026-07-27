package com.callbackdev.saldo.feature.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState
import com.callbackdev.saldo.core.domain.model.TransactionType

/**
 * Switches a placed widget between expense and income. The choice is per widget
 * instance and survives reboots, so a widget left on income stays there - but it
 * is runtime state, and the settings screen's "starts on" is untouched by it.
 */
class SetWidgetTypeAction : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val requested = parameters[TypeKey]
            ?.let { name -> TransactionType.entries.firstOrNull { it.name == name } }
            ?: return
        updateAppWidgetState(context, glanceId) { prefs ->
            // CurrentType, not Type: this is where the widget is now, not where
            // it starts. Writing the configured value from here made "starts on"
            // change by itself every time the selector was touched.
            prefs[QuickAddWidgetPrefs.CurrentType] = requested.name
        }
        SaldoQuickAddWidget().update(context, glanceId)
    }

    companion object {
        // Deliberately not the same name as QuickAddWidgetPrefs.Type: these are
        // different namespaces (intent extras vs widget state) and sharing a
        // string made them look like one key read two ways.
        val TypeKey = ActionParameters.Key<String>("quick_add_requested_type")
    }
}
