package com.callbackdev.saldo.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator

/**
 * Navigation state with one back stack per top-level tab (the Navigation 3
 * "multiple back stacks" recipe adapted to Saldo). Each tab's stack - and,
 * through the per-stack decorators, its saveable state and ViewModels -
 * survives tab switches, so returning to a tab restores scroll position,
 * search, filters and data instantly instead of reloading through a skeleton.
 *
 * The visible stack is composed as `[dashboard stack] + [selected tab stack]`
 * ("exit through home"): back from a tab root lands on the dashboard, back
 * from the dashboard root leaves the app.
 */
@Stable
class SaldoNavigationState(
    private val selectedName: MutableState<String>,
    val backStacks: Map<TopLevelDestination, NavBackStack<NavKey>>,
) {

    /** The selected tab; persisted by enum name so it survives process death. */
    var selected: TopLevelDestination
        get() = TopLevelDestination.entries.firstOrNull { it.name == selectedName.value }
            ?: TopLevelDestination.DASHBOARD
        private set(value) {
            selectedName.value = value.name
        }

    private val currentStack: NavBackStack<NavKey>
        get() = backStacks.getValue(selected)

    /** The route on top of the visible stack (drives the bottom bar visibility). */
    val currentRoute: NavKey?
        get() = currentStack.lastOrNull()

    /** Pushes a detail route onto the selected tab's stack. */
    fun navigate(route: NavKey) {
        currentStack.add(route)
    }

    /**
     * Selects a tab, keeping the previous tab's stack (and state) alive.
     * Reselecting the current tab is a no-op.
     */
    fun switchTab(destination: TopLevelDestination) {
        selected = destination
    }

    /**
     * Handles back: pops the selected tab's stack, or falls back to the
     * dashboard from a tab root. At the dashboard root there is nothing left
     * to pop and the system back closes the app.
     */
    fun goBack() {
        if (currentStack.size > 1) {
            currentStack.removeLastOrNull()
        } else if (selected != TopLevelDestination.DASHBOARD) {
            selected = TopLevelDestination.DASHBOARD
        }
    }

    /** The stacks composed right now: the dashboard root, then the selected tab. */
    fun destinationsInUse(): List<TopLevelDestination> =
        if (selected == TopLevelDestination.DASHBOARD) {
            listOf(TopLevelDestination.DASHBOARD)
        } else {
            listOf(TopLevelDestination.DASHBOARD, selected)
        }
}

/**
 * Creates and remembers the app's navigation state. Every per-tab stack is a
 * [rememberNavBackStack], so stack contents survive configuration changes and
 * process death; the selected tab is a plain saveable string (four fixed tabs
 * need no reflection-based NavKey serialization).
 */
@Composable
fun rememberSaldoNavigationState(): SaldoNavigationState {
    val selectedName = rememberSaveable { mutableStateOf(TopLevelDestination.DASHBOARD.name) }
    val backStacks = TopLevelDestination.entries.associateWith { destination ->
        rememberNavBackStack(destination.route)
    }
    return remember { SaldoNavigationState(selectedName, backStacks) }
}

/**
 * Decorates every tab's entries with its own saveable-state and ViewModel
 * decorators, then flattens the stacks in use into the list [androidx.navigation3.ui.NavDisplay]
 * renders. Decorating per stack (instead of letting NavDisplay decorate the
 * visible list) is the whole point: entries of a hidden tab stay decorated,
 * so their ViewModels and saved state are retained instead of being destroyed
 * on every switch.
 */
@Composable
fun SaldoNavigationState.rememberDecoratedEntries(
    entryProvider: (NavKey) -> NavEntry<NavKey>,
): List<NavEntry<NavKey>> {
    val decorated = backStacks.mapValues { (_, stack) ->
        rememberDecoratedNavEntries(
            backStack = stack,
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator<NavKey>(),
                rememberViewModelStoreNavEntryDecorator<NavKey>(),
            ),
            entryProvider = entryProvider,
        )
    }
    return destinationsInUse().flatMap { decorated.getValue(it) }
}
