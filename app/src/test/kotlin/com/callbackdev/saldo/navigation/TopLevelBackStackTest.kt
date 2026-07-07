package com.callbackdev.saldo.navigation

import androidx.navigation3.runtime.NavKey
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TopLevelBackStackTest {

    @Test
    fun `switching to a tab keeps dashboard as root`() {
        val backStack = mutableListOf<NavKey>(DashboardRoute)

        backStack.switchTopLevelTab(StatsRoute)

        assertEquals(listOf<NavKey>(DashboardRoute, StatsRoute), backStack)
    }

    @Test
    fun `switching between tabs never stacks more than two entries`() {
        val backStack = mutableListOf<NavKey>(DashboardRoute)

        backStack.switchTopLevelTab(TransactionsRoute)
        backStack.switchTopLevelTab(StatsRoute)
        backStack.switchTopLevelTab(SettingsRoute)

        assertEquals(listOf<NavKey>(DashboardRoute, SettingsRoute), backStack)
    }

    @Test
    fun `switching to dashboard resets the stack to the root only`() {
        val backStack = mutableListOf<NavKey>(DashboardRoute, SettingsRoute)

        backStack.switchTopLevelTab(DashboardRoute)

        assertEquals(listOf<NavKey>(DashboardRoute), backStack)
    }

    @Test
    fun `reselecting the current tab is a no-op`() {
        val backStack = mutableListOf<NavKey>(DashboardRoute, StatsRoute)

        backStack.switchTopLevelTab(StatsRoute)

        assertEquals(listOf<NavKey>(DashboardRoute, StatsRoute), backStack)
    }
}
