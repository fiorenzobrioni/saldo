package com.callbackdev.saldo.navigation

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.callbackdev.saldo.MainActivity
import com.callbackdev.saldo.R
import com.callbackdev.saldo.core.common.prefs.UserPreferencesEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/** Compose UI tests run on JUnit 4, as required by the Compose test rules. */
@RunWith(AndroidJUnit4::class)
class SaldoAppNavigationTest {

    private val composeRule = createAndroidComposeRule<MainActivity>()

    /**
     * The onboarding gate has to be satisfied before the activity starts, and
     * the Compose rule launches it as soon as it is evaluated, before any
     * `@Before` runs: hence the chain, with the seeding on the outside.
     *
     * Without it these tests only pass on a device that already holds data,
     * which is how they used to pass on the developer's phone and fail on a
     * clean emulator: there is no bottom bar to look at while the onboarding
     * is on screen.
     */
    @get:Rule
    val rules: RuleChain = RuleChain
        .outerRule(CompletedOnboardingRule())
        .around(composeRule)

    private fun string(resId: Int): String =
        composeRule.activity.getString(resId)

    @Test
    fun bottomBar_showsAllTopLevelDestinations() {
        composeRule.onNodeWithText(string(R.string.nav_dashboard)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.nav_transactions)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.nav_stats)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.nav_settings)).assertIsDisplayed()
    }

    // The settings screen is one scrolling column and the management section
    // sits below the fold on a phone-sized screen, so the entry has to be
    // scrolled into view before it can be seen or tapped: without it the node
    // exists in the tree but is not displayed, which is what a user would see.
    @Test
    fun tappingSettingsTab_opensSettingsScreen() {
        composeRule.onNodeWithText(string(R.string.nav_settings)).performClick()

        composeRule.onNodeWithText(string(R.string.settings_categories))
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun settingsCategoriesEntry_opensCategoriesList() {
        composeRule.onNodeWithText(string(R.string.nav_settings)).performClick()
        composeRule.onNodeWithText(string(R.string.settings_categories))
            .performScrollTo()
            .performClick()

        composeRule.onNodeWithText(string(R.string.categories_title)).assertIsDisplayed()
    }
}

/**
 * Marks the onboarding as done on the app's own preferences store, reached
 * through the Hilt graph: building a second `DataStore` on the same file would
 * clash with the one the app is about to create.
 */
private class CompletedOnboardingRule : ExternalResource() {

    override fun before() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val entryPoint = EntryPointAccessors.fromApplication(
            context,
            UserPreferencesEntryPoint::class.java,
        )
        runBlocking { entryPoint.userPreferences().setOnboardingCompleted() }
    }
}
