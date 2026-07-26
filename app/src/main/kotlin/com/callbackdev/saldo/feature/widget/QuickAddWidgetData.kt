package com.callbackdev.saldo.feature.widget

import com.callbackdev.saldo.core.domain.model.Account
import com.callbackdev.saldo.core.domain.model.Category
import com.callbackdev.saldo.core.domain.model.TransactionType

/**
 * The snapshot a placed quick-add widget renders. Glance draws through
 * `RemoteViews` in the launcher's process, so the widget cannot observe flows
 * the way a screen does: it renders a snapshot and is redrawn when something
 * asks it to (see `WidgetRefreshWatcher`).
 */
data class QuickAddWidgetData(
    val type: TransactionType,
    val account: Account?,
    val categories: List<Category>,
    /** Already localized: money becomes a String in the presentation layer, never below it. */
    val todayTotal: String?,
    val showTodayTotal: Boolean,
) {
    /** False when there is nothing to add to yet: no account, or no category of this type. */
    val isReady: Boolean get() = account != null && categories.isNotEmpty()
}
