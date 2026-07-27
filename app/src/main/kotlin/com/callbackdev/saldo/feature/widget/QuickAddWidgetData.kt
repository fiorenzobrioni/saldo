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
    /**
     * The account's name when the widget is pinned to one that is still
     * active, null when it follows the app default. Shown as a badge: with two
     * widgets on two accounts, an unlabelled pair is a wrong-account entry
     * waiting to happen. When set, [todayTotal] is scoped to this account too.
     */
    val pinnedAccountName: String? = null,
) {
    /** False when there is nothing to add to yet: no account, or no category of this type. */
    val isReady: Boolean get() = account != null && categories.isNotEmpty()
}
