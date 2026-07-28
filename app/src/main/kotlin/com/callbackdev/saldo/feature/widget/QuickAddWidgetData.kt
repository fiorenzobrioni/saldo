package com.callbackdev.saldo.feature.widget

import com.callbackdev.saldo.core.domain.model.Category
import com.callbackdev.saldo.core.domain.model.TransactionType

/**
 * The snapshot a placed quick-add widget renders. Glance draws through
 * `RemoteViews` in the launcher's process, so the widget cannot observe flows
 * the way a screen does: it renders a snapshot and is redrawn when something
 * asks it to (see `WidgetRefreshWatcher`).
 *
 * Deliberately free of anything that moves with the ledger: no balances, no
 * daily totals, no usage-derived ordering. What is here changes only when the
 * user edits accounts, categories or the theme, which is what lets the widget
 * stay a static entry point instead of a surface that redraws on every
 * movement.
 */
data class QuickAddWidgetData(
    val type: TransactionType,
    val categories: List<Category>,
    /** False before onboarding: with no account at all, a tap can only open the app. */
    val hasAccounts: Boolean,
    /**
     * The id the quick-entry sheet opens on: the configured account when it is
     * still active, null when the widget follows the app default - the sheet
     * resolves that itself at open time, so the widget does not have to be
     * redrawn to keep up with it.
     */
    val pinnedAccountId: Long? = null,
    /**
     * The account's name when the widget is pinned to one that is still
     * active, null when it follows the app default. Shown as a badge: with two
     * widgets on two accounts, an unlabelled pair is a wrong-account entry
     * waiting to happen.
     */
    val pinnedAccountName: String? = null,
) {
    /** False when there is nothing to add to yet: no account, or no category of this type. */
    val isReady: Boolean get() = hasAccounts && categories.isNotEmpty()
}
