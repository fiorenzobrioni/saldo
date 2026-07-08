package com.callbackdev.saldo.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.callbackdev.saldo.feature.accounts.AccountEditorScreen
import com.callbackdev.saldo.feature.accounts.AccountsScreen
import com.callbackdev.saldo.feature.categories.CategoriesScreen
import com.callbackdev.saldo.feature.categories.CategoryEditorScreen
import com.callbackdev.saldo.feature.dashboard.DashboardScreen
import com.callbackdev.saldo.feature.settings.SettingsScreen
import com.callbackdev.saldo.feature.stats.StatsScreen
import com.callbackdev.saldo.feature.transactions.TransactionEditorScreen
import com.callbackdev.saldo.feature.transactions.TransactionsScreen

/** Root composable: scaffold with bottom navigation and Navigation 3 display. */
@Composable
fun SaldoApp() {
    val backStack = rememberNavBackStack(DashboardRoute)
    val currentRoute = backStack.lastOrNull()
    val isTopLevel = TopLevelDestination.entries.any { it.route == currentRoute }

    Scaffold(
        // Each screen owns its insets (top app bars, FABs); the outer scaffold
        // only carves out space for the bottom navigation bar.
        contentWindowInsets = WindowInsets(0.dp),
        bottomBar = {
            AnimatedVisibility(
                visible = isTopLevel,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it }),
            ) {
                NavigationBar {
                    TopLevelDestination.entries.forEach { destination ->
                        val label = stringResource(destination.labelRes)
                        NavigationBarItem(
                            selected = currentRoute == destination.route,
                            onClick = { backStack.switchTopLevelTab(destination.route) },
                            icon = {
                                Icon(imageVector = destination.icon, contentDescription = label)
                            },
                            label = { Text(label) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavDisplay(
            backStack = backStack,
            modifier = Modifier.padding(innerPadding),
            onBack = { backStack.removeLastOrNull() },
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
            entryProvider = entryProvider {
                entry<DashboardRoute> {
                    DashboardScreen(
                        onNavigateToAccounts = { backStack.add(AccountsRoute) },
                        onCreateFirstAccount = { backStack.add(AccountEditorRoute()) },
                        onNavigateToNewTransaction = { type ->
                            backStack.add(TransactionEditorRoute(initialTypeName = type.name))
                        },
                        onNavigateToEditTransaction = { id ->
                            backStack.add(TransactionEditorRoute(id))
                        },
                        onSeeAllTransactions = { backStack.switchTopLevelTab(TransactionsRoute) },
                    )
                }
                entry<TransactionsRoute> {
                    TransactionsScreen(
                        onNavigateToNewTransaction = { backStack.add(TransactionEditorRoute()) },
                        onNavigateToEditTransaction = { id ->
                            backStack.add(TransactionEditorRoute(id))
                        },
                        onNavigateToAccounts = { backStack.add(AccountsRoute) },
                    )
                }
                entry<StatsRoute> { StatsScreen() }
                entry<SettingsRoute> {
                    SettingsScreen(
                        onNavigateToCategories = { backStack.add(CategoriesRoute) },
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
            },
        )
    }
}
