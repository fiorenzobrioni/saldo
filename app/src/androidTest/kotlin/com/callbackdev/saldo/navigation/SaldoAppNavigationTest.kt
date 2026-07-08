package com.callbackdev.saldo.navigation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.callbackdev.saldo.MainActivity
import com.callbackdev.saldo.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Compose UI tests run on JUnit 4, as required by the Compose test rules. */
@RunWith(AndroidJUnit4::class)
class SaldoAppNavigationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun string(resId: Int): String =
        composeRule.activity.getString(resId)

    @Test
    fun bottomBar_showsAllTopLevelDestinations() {
        composeRule.onNodeWithText(string(R.string.nav_dashboard)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.nav_transactions)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.nav_stats)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.nav_settings)).assertIsDisplayed()
    }

    @Test
    fun tappingSettingsTab_opensSettingsScreen() {
        composeRule.onNodeWithText(string(R.string.nav_settings)).performClick()

        composeRule.onNodeWithText(string(R.string.settings_accounts)).assertIsDisplayed()
    }

    @Test
    fun settingsAccountsEntry_opensAccountsList() {
        composeRule.onNodeWithText(string(R.string.nav_settings)).performClick()
        composeRule.onNodeWithText(string(R.string.settings_accounts)).performClick()

        composeRule.onNodeWithText(string(R.string.accounts_empty_title)).assertIsDisplayed()
    }
}
