package com.callbackdev.saldo.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.callbackdev.saldo.feature.accounts.AccountEditorScreen
import com.callbackdev.saldo.feature.accounts.AccountsScreen
import com.callbackdev.saldo.feature.categories.CategoriesScreen
import com.callbackdev.saldo.feature.categories.CategoryEditorScreen
import com.callbackdev.saldo.feature.dashboard.DashboardScreen
import com.callbackdev.saldo.feature.recurring.RecurringRuleEditorScreen
import com.callbackdev.saldo.feature.recurring.SubscriptionsScreen
import com.callbackdev.saldo.feature.settings.SettingsScreen
import com.callbackdev.saldo.feature.stats.StatsScreen
import com.callbackdev.saldo.feature.transactions.TransactionEditorScreen
import com.callbackdev.saldo.feature.transactions.TransactionsScreen

/** Height of the Material 3 navigation bar content (excluding the system inset). */
private val BottomBarHeight = 80.dp

/** Duration of the screen and bottom-bar transitions (the 700ms default feels slow). */
private const val NAV_TRANSITION_MS = 300

/** How far the incoming/outgoing screens slide, as a fraction (1/N) of their width. */
private const val SLIDE_DIVISOR = 6

/**
 * Root composable: the Navigation 3 display with a bottom navigation bar shown
 * only on the top-level destinations.
 *
 * The bottom bar is drawn as an overlay on top of the display rather than in a
 * Scaffold slot on purpose: a Scaffold's bottom bar reshapes the shared content
 * area, and animating its visibility makes that area's height jump when the
 * enter/exit animation ends, so a destination that anchors content to the
 * bottom (e.g. the amount keypad) visibly snaps down after opening. With the
 * overlay, every destination is laid out at its final size from the first
 * frame - top-level screens simply reserve [BottomBarHeight] at the bottom -
 * and only the bar itself slides in and out.
 */
@Composable
fun SaldoApp() {
    val backStack = rememberNavBackStack(DashboardRoute)
    val currentRoute = backStack.lastOrNull()
    val isTopLevel = TopLevelDestination.entries.any { it.route == currentRoute }

    // Reserve space for the bar only where it is shown; detail screens fill the
    // whole height so their bottom-anchored content lands in its final place.
    val topLevelModifier = Modifier.padding(bottom = BottomBarHeight)

    Box(modifier = Modifier.fillMaxSize()) {
        NavDisplay(
            backStack = backStack,
            modifier = Modifier.fillMaxSize(),
            onBack = { backStack.removeLastOrNull() },
            // The library default is a 700ms fade, which feels sluggish; a short
            // slide + fade (~300ms) reads as snappy and premium instead.
            transitionSpec = { forwardTransition() },
            popTransitionSpec = { backwardTransition() },
            predictivePopTransitionSpec = { backwardTransition() },
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
            entryProvider = entryProvider {
                entry<DashboardRoute> {
                    DashboardScreen(
                        modifier = topLevelModifier,
                        onNavigateToAccounts = { backStack.add(AccountsRoute) },
                        onCreateFirstAccount = { backStack.add(AccountEditorRoute()) },
                        onNavigateToNewTransaction = { type ->
                            backStack.add(TransactionEditorRoute(initialTypeName = type.name))
                        },
                        onNavigateToEditTransaction = { id ->
                            backStack.add(TransactionEditorRoute(id))
                        },
                        onSeeAllTransactions = { backStack.switchTopLevelTab(TransactionsRoute) },
                        onNavigateToSubscriptions = { backStack.add(SubscriptionsRoute) },
                    )
                }
                entry<TransactionsRoute> {
                    TransactionsScreen(
                        modifier = topLevelModifier,
                        onNavigateToNewTransaction = { backStack.add(TransactionEditorRoute()) },
                        onNavigateToEditTransaction = { id ->
                            backStack.add(TransactionEditorRoute(id))
                        },
                        onNavigateToAccounts = { backStack.add(AccountsRoute) },
                    )
                }
                entry<StatsRoute> { StatsScreen(modifier = topLevelModifier) }
                entry<SettingsRoute> {
                    SettingsScreen(
                        modifier = topLevelModifier,
                        onNavigateToCategories = { backStack.add(CategoriesRoute) },
                        onNavigateToSubscriptions = { backStack.add(SubscriptionsRoute) },
                    )
                }
                entry<AccountsRoute> {
                    AccountsScreen(
                        onNavigateBack = { backStack.removeLastOrNull() },
                        onNavigateToNewAccount = { backStack.add(AccountEditorRoute()) },
                        onNavigateToEditAccount = { id -> backStack.add(AccountEditorRoute(id)) },
                    )
                }
                entry<AccountEditorRoute> { route ->
                    AccountEditorScreen(
                        route = route,
                        onNavigateBack = { backStack.removeLastOrNull() },
                    )
                }
                entry<CategoriesRoute> {
                    CategoriesScreen(
                        onNavigateBack = { backStack.removeLastOrNull() },
                        onNavigateToNewCategory = { type ->
                            backStack.add(CategoryEditorRoute(initialTypeName = type.name))
                        },
                        onNavigateToEditCategory = { id ->
                            backStack.add(CategoryEditorRoute(categoryId = id))
                        },
                    )
                }
                entry<CategoryEditorRoute> { route ->
                    CategoryEditorScreen(
                        route = route,
                        onNavigateBack = { backStack.removeLastOrNull() },
                    )
                }
                entry<TransactionEditorRoute> { route ->
                    TransactionEditorScreen(
                        route = route,
                        onNavigateBack = { backStack.removeLastOrNull() },
                    )
                }
                entry<SubscriptionsRoute> {
                    SubscriptionsScreen(
                        onNavigateBack = { backStack.removeLastOrNull() },
                        onNavigateToNewSubscription = { backStack.add(RecurringRuleEditorRoute()) },
                        onNavigateToEditSubscription = { id ->
                            backStack.add(RecurringRuleEditorRoute(id))
                        },
                    )
                }
                entry<RecurringRuleEditorRoute> { route ->
                    RecurringRuleEditorScreen(
                        route = route,
                        onNavigateBack = { backStack.removeLastOrNull() },
                    )
                }
            },
        )

        AnimatedVisibility(
            visible = isTopLevel,
            enter = slideInVertically(tween(NAV_TRANSITION_MS)) { it },
            exit = slideOutVertically(tween(NAV_TRANSITION_MS)) { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            SaldoBottomBar(
                currentRoute = currentRoute,
                onSelect = { backStack.switchTopLevelTab(it) },
            )
        }
    }
}

/** Push transition: the incoming screen slides in from the right and fades in. */
private fun forwardTransition(): ContentTransform {
    val enter = fadeIn(tween(NAV_TRANSITION_MS, easing = FastOutSlowInEasing)) +
        slideInHorizontally(tween(NAV_TRANSITION_MS, easing = FastOutSlowInEasing)) { it / SLIDE_DIVISOR }
    val exit = fadeOut(tween(NAV_TRANSITION_MS, easing = FastOutSlowInEasing)) +
        slideOutHorizontally(tween(NAV_TRANSITION_MS, easing = FastOutSlowInEasing)) { -it / SLIDE_DIVISOR }
    return enter togetherWith exit
}

/** Pop transition: the reverse of [forwardTransition], sliding back to the right. */
private fun backwardTransition(): ContentTransform {
    val enter = fadeIn(tween(NAV_TRANSITION_MS, easing = FastOutSlowInEasing)) +
        slideInHorizontally(tween(NAV_TRANSITION_MS, easing = FastOutSlowInEasing)) { -it / SLIDE_DIVISOR }
    val exit = fadeOut(tween(NAV_TRANSITION_MS, easing = FastOutSlowInEasing)) +
        slideOutHorizontally(tween(NAV_TRANSITION_MS, easing = FastOutSlowInEasing)) { it / SLIDE_DIVISOR }
    return enter togetherWith exit
}

@Composable
private fun SaldoBottomBar(
    currentRoute: NavKey?,
    onSelect: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBar(modifier = modifier) {
        TopLevelDestination.entries.forEach { destination ->
            val label = stringResource(destination.labelRes)
            NavigationBarItem(
                selected = currentRoute == destination.route,
                onClick = { onSelect(destination.route) },
                icon = {
                    Icon(imageVector = destination.icon, contentDescription = label)
                },
                label = { Text(label) },
            )
        }
    }
}
