package com.callbackdev.saldo.feature.widget

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.callbackdev.saldo.core.common.prefs.ThemeMode
import com.callbackdev.saldo.core.common.prefs.ThemePreferences
import com.callbackdev.saldo.core.common.prefs.UserPreferencesRepository
import com.callbackdev.saldo.core.designsystem.theme.SaldoTheme
import com.callbackdev.saldo.core.domain.model.TransactionType
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * The amount step of the quick-add widget, hosted in a translucent activity so
 * it reads as a sheet over the launcher rather than as "the app opened".
 *
 * This is the deliberate half of ADR 32: the choice happens on the widget,
 * where one tap on glanceable content is what a widget is good at, and the
 * typing happens here, where the real [com.callbackdev.saldo.core.designsystem.component.AmountKeypad]
 * responds instantly, with haptics and motion, instead of a `RemoteViews`
 * round trip per digit.
 */
@AndroidEntryPoint
class QuickEntryActivity : ComponentActivity() {

    @Inject
    lateinit var userPreferences: UserPreferencesRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The window is translucent and the sheet sits on the navigation bar:
        // without this the insets it pads for are the wrong ones.
        enableEdgeToEdge()
        val route = QuickEntryRoute.from(intent)
        setContent {
            val themePreferences by userPreferences.themePreferences
                .collectAsStateWithLifecycle(initialValue = ThemePreferences())
            val darkTheme = when (themePreferences.mode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            SaldoTheme(
                darkTheme = darkTheme,
                dynamicColor = themePreferences.useDynamicColor,
                // The window is translucent: an opaque backdrop would hide the
                // launcher and turn the sheet back into a full screen.
                applyBackground = false,
            ) {
                QuickEntrySheet(
                    viewModel = hiltViewModel<QuickEntryViewModel, QuickEntryViewModel.Factory>(
                        creationCallback = { factory -> factory.create(route) },
                    ),
                    onDismiss = ::dismiss,
                )
            }
        }
    }

    // The sheet animates itself out before this runs; the window itself has no
    // transition to play (`windowAnimationStyle` is null on the theme), so there
    // is nothing here to suppress.
    private fun dismiss() = finish()

    companion object {
        fun intent(
            context: Context,
            type: TransactionType,
            categoryId: Long?,
            accountId: Long?,
        ): Intent = QuickEntryRoute.putExtras(
            intent = Intent(context, QuickEntryActivity::class.java),
            type = type,
            categoryId = categoryId,
            accountId = accountId,
        )
    }
}
