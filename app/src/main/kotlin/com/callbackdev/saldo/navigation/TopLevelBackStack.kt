package com.callbackdev.saldo.navigation

import androidx.navigation3.runtime.NavKey

/**
 * Switches the back stack to a top-level tab.
 *
 * The dashboard is always the root of the stack, so pressing back from any
 * other tab returns to the dashboard and pressing back from the dashboard
 * leaves the app. Reselecting the current tab is a no-op.
 */
fun MutableList<NavKey>.switchTopLevelTab(target: NavKey, root: NavKey = DashboardRoute) {
    if (lastOrNull() == target) return
    clear()
    add(root)
    if (target != root) add(target)
}
