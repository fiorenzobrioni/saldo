package com.callbackdev.saldo.feature.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState
import com.callbackdev.saldo.core.domain.model.TransactionType

/**
 * Switches a placed widget between expense and income. The choice is per widget
 * instance and survives reboots, so a widget set to income stays that way.
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
            prefs[QuickAddWidgetPrefs.Type] = requested.name
        }
        SaldoQuickAddWidget().update(context, glanceId)
    }

    companion object {
        val TypeKey = ActionParameters.Key<String>("quick_add_type")
    }
}
