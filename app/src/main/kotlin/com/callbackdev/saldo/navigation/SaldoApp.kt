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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.callbackdev.saldo.feature.about.AboutScreen
import com.callbackdev.saldo.feature.accounts.AccountEditorScreen
import com.callbackdev.saldo.feature.backup.BackupScreen
import com.callbackdev.saldo.feature.accounts.AccountsScreen
import com.callbackdev.saldo.feature.budgets.BudgetEditorScreen
import com.callbackdev.saldo.feature.budgets.BudgetsScreen
import com.callbackdev.saldo.feature.categories.CategoriesScreen
import com.callbackdev.saldo.feature.categories.CategoryEditorScreen
import com.callbackdev.saldo.feature.dashboard.DashboardScreen
import com.callbackdev.saldo.feature.recurring.PendingMovementsScreen
import com.callbackdev.saldo.feature.recurring.RecurringRuleEditorScreen
import com.callbackdev.saldo.feature.recurring.RecurrencesScreen
import com.callbackdev.saldo.feature.settings.SettingsScreen
import com.callbackdev.saldo.feature.stats.FilteredTransactionsScreen
import com.callbackdev.saldo.feature.stats.StatsScreen
import com.callbackdev.saldo.feature.transactions.TransactionEditorScreen
import com.callbackdev.saldo.feature.transactions.TransactionsScreen
import com.callbackdev.saldo.core.domain.model.TransactionType

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
fun SaldoApp(
    quickAction: TransactionType? = null,
    onQuickActionHandled: () -> Unit = {},
) {
    // One back stack per tab (Nav3 multiple-back-stacks recipe): switching
    // tabs keeps every tab's ViewModels, scroll and filters alive.
    val nav = rememberSaldoNavigationState()
    val currentRoute = nav.currentRoute
    val isTopLevel = TopLevelDestination.entries.any { it.route == currentRoute }

    // A launcher shortcut asked for a specific movement: open its editor once,
    // on top of whatever is showing, then hand the request back as consumed so a
    // recomposition or rotation cannot re-open it.
    LaunchedEffect(quickAction) {
        if (quickAction != null) {
            nav.navigate(TransactionEditorRoute(initialTypeName = quickAction.name))
            onQuickActionHandled()
        }
    }

    // Reserve space for the bar only where it is shown; detail screens fill the
    // whole height so their bottom-anchored content lands in its final place.
    val topLevelModifier = Modifier.padding(bottom = BottomBarHeight)

    val provider = entryProvider {
        entry<DashboardRoute> {
            DashboardScreen(
                modifier = topLevelModifier,
                onNavigateToAccounts = { nav.navigate(AccountsRoute) },
                onCreateFirstAccount = { nav.navigate(AccountEditorRoute()) },
                onNavigateToNewTransaction = { type ->
                    nav.navigate(TransactionEditorRoute(initialTypeName = type.name))
                },
                onNavigateToEditTransaction = { id ->
                    nav.navigate(TransactionEditorRoute(id))
                },
                onSeeAllTransactions = { nav.switchTab(TopLevelDestination.TRANSACTIONS) },
                onNavigateToRecurrences = { nav.navigate(RecurrencesRoute) },
                onNavigateToPending = { nav.navigate(PendingMovementsRoute) },
                onNavigateToBudgets = { nav.navigate(BudgetsRoute) },
                onNavigateToFiltered = { route -> nav.navigate(route) },
            )
        }
        entry<TransactionsRoute> {
            TransactionsScreen(
                modifier = topLevelModifier,
                onNavigateToNewTransaction = { nav.navigate(TransactionEditorRoute()) },
                onNavigateToEditTransaction = { id ->
                    nav.navigate(TransactionEditorRoute(id))
                },
                onNavigateToAccounts = { nav.navigate(AccountsRoute) },
            )
        }
        entry<StatsRoute> {
            StatsScreen(
                modifier = topLevelModifier,
                onNavigateToFiltered = { route -> nav.navigate(route) },
            )
        }
        entry<SettingsRoute> {
            SettingsScreen(
                modifier = topLevelModifier,
                onNavigateToAccounts = { nav.navigate(AccountsRoute) },
                onNavigateToCategories = { nav.navigate(CategoriesRoute) },
                onNavigateToRecurrences = { nav.navigate(RecurrencesRoute) },
                onNavigateToBudgets = { nav.navigate(BudgetsRoute) },
                onNavigateToBackup = { nav.navigate(BackupRoute) },
                onNavigateToAbout = { nav.navigate(AboutRoute) },
            )
        }
        entry<AccountsRoute> {
            AccountsScreen(
                onNavigateBack = { nav.goBack() },
                onNavigateToNewAccount = { nav.navigate(AccountEditorRoute()) },
                onNavigateToEditAccount = { id -> nav.navigate(AccountEditorRoute(id)) },
            )
        }
        entry<AccountEditorRoute> { route ->
            AccountEditorScreen(
                route = route,
                onNavigateBack = { nav.goBack() },
            )
        }
        entry<CategoriesRoute> {
            CategoriesScreen(
                onNavigateBack = { nav.goBack() },
                onNavigateToNewCategory = { type ->
                    nav.navigate(CategoryEditorRoute(initialTypeName = type.name))
                },
                onNavigateToEditCategory = { id ->
                    nav.navigate(CategoryEditorRoute(categoryId = id))
                },
            )
        }
        entry<CategoryEditorRoute> { route ->
            CategoryEditorScreen(
                route = route,
                onNavigateBack = { nav.goBack() },
            )
        }
        entry<TransactionEditorRoute> { route ->
            TransactionEditorScreen(
                route = route,
                onNavigateBack = { nav.goBack() },
            )
        }
        entry<RecurrencesRoute> {
            RecurrencesScreen(
                onNavigateBack = { nav.goBack() },
                onNavigateToNewRule = { type ->
                    nav.navigate(RecurringRuleEditorRoute(initialTypeName = type.name))
                },
                onNavigateToEditRule = { id ->
                    nav.navigate(RecurringRuleEditorRoute(id))
                },
            )
        }
        entry<RecurringRuleEditorRoute> { route ->
            RecurringRuleEditorScreen(
                route = route,
                onNavigateBack = { nav.goBack() },
            )
        }
        entry<PendingMovementsRoute> {
            PendingMovementsScreen(onNavigateBack = { nav.goBack() })
        }
        entry<BudgetsRoute> {
            BudgetsScreen(
                onNavigateBack = { nav.goBack() },
                onNavigateToEditor = { id -> nav.navigate(BudgetEditorRoute(id)) },
            )
        }
        entry<BudgetEditorRoute> { route ->
            BudgetEditorScreen(
                route = route,
                onNavigateBack = { nav.goBack() },
            )
        }
        entry<FilteredTransactionsRoute> { route ->
            FilteredTransactionsScreen(
                route = route,
                onNavigateBack = { nav.goBack() },
                onNavigateToTransaction = { id ->
                    nav.navigate(TransactionEditorRoute(id))
                },
            )
        }
        entry<BackupRoute> {
            BackupScreen(onNavigateBack = { nav.goBack() })
        }
        entry<AboutRoute> {
            AboutScreen(onNavigateBack = { nav.goBack() })
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        NavDisplay(
            // Entries are decorated per tab stack, so hidden tabs keep their
            // ViewModels and saved state alive across switches.
            entries = nav.rememberDecoratedEntries(provider),
            modifier = Modifier.fillMaxSize(),
            onBack = { nav.goBack() },
            // The library default is a 700ms fade, which feels sluggish; a short
            // slide + fade (~300ms) reads as snappy and premium instead.
            transitionSpec = { forwardTransition() },
            popTransitionSpec = { backwardTransition() },
            predictivePopTransitionSpec = { backwardTransition() },
        )

        AnimatedVisibility(
            visible = isTopLevel,
            enter = slideInVertically(tween(NAV_TRANSITION_MS)) { it },
            exit = slideOutVertically(tween(NAV_TRANSITION_MS)) { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            SaldoBottomBar(
                selected = nav.selected,
                onSelect = { nav.switchTab(it) },
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
    selected: TopLevelDestination,
    onSelect: (TopLevelDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBar(modifier = modifier) {
        TopLevelDestination.entries.forEach { destination ->
            val label = stringResource(destination.labelRes)
            NavigationBarItem(
                selected = selected == destination,
                onClick = { onSelect(destination) },
                icon = {
                    Icon(imageVector = destination.icon, contentDescription = label)
                },
                label = { Text(label) },
            )
        }
    }
}
