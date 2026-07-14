package com.callbackdev.saldo.navigation

import androidx.compose.runtime.mutableStateOf
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SaldoNavigationStateTest {

    private fun state(): SaldoNavigationState = SaldoNavigationState(
        selectedName = mutableStateOf(TopLevelDestination.DASHBOARD.name),
        backStacks = TopLevelDestination.entries.associateWith { NavBackStack(it.route) },
    )

    @Test
    fun `starts on the dashboard with only its stack in use`() {
        val nav = state()

        assertEquals(TopLevelDestination.DASHBOARD, nav.selected)
        assertEquals(listOf(TopLevelDestination.DASHBOARD), nav.destinationsInUse())
        assertEquals(DashboardRoute, nav.currentRoute)
    }

    @Test
    fun `switching tab composes dashboard plus the selected tab`() {
        val nav = state()

        nav.switchTab(TopLevelDestination.STATS)

        assertEquals(
            listOf(TopLevelDestination.DASHBOARD, TopLevelDestination.STATS),
            nav.destinationsInUse(),
        )
        assertEquals(StatsRoute, nav.currentRoute)
    }

    @Test
    fun `each tab keeps its own stack across switches`() {
        val nav = state()
        nav.switchTab(TopLevelDestination.TRANSACTIONS)
        nav.navigate(TransactionEditorRoute(transactionId = 7L))

        nav.switchTab(TopLevelDestination.STATS)
        nav.switchTab(TopLevelDestination.TRANSACTIONS)

        // The editor pushed before leaving the tab is still on top.
        assertEquals(
            TransactionEditorRoute(transactionId = 7L),
            nav.currentRoute,
        )
    }

    @Test
    fun `back pops the tab stack before falling back to the dashboard`() {
        val nav = state()
        nav.switchTab(TopLevelDestination.SETTINGS)
        nav.navigate(AboutRoute)

        nav.goBack()
        assertEquals(SettingsRoute, nav.currentRoute)

        nav.goBack()
        assertEquals(TopLevelDestination.DASHBOARD, nav.selected)
        assertEquals(DashboardRoute, nav.currentRoute)
    }

    @Test
    fun `back at the dashboard root is a no-op for the state`() {
        val nav = state()

        nav.goBack()

        assertEquals(TopLevelDestination.DASHBOARD, nav.selected)
        assertEquals(listOf<NavKey>(DashboardRoute), nav.backStacks.getValue(TopLevelDestination.DASHBOARD).toList())
    }

    @Test
    fun `detail routes land on the selected tab's stack`() {
        val nav = state()
        nav.switchTab(TopLevelDestination.STATS)

        nav.navigate(
            FilteredTransactionsRoute(startEpochDay = 0L, endEpochDayExclusive = 31L),
        )

        assertEquals(2, nav.backStacks.getValue(TopLevelDestination.STATS).size)
        assertEquals(1, nav.backStacks.getValue(TopLevelDestination.DASHBOARD).size)
    }
}
