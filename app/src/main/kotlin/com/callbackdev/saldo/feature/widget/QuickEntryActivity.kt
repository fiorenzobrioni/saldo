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
import com.callbackdev.saldo.core.common.applock.AppLockManager
import com.callbackdev.saldo.core.common.applock.AppLockRepository
import com.callbackdev.saldo.core.common.applock.AppLockState
import com.callbackdev.saldo.core.common.applock.bindSecureScreen
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

    @Inject
    lateinit var appLockManager: AppLockManager

    @Inject
    lateinit var appLockRepository: AppLockRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The window is translucent and the sheet sits on the navigation bar:
        // without this the insets it pads for are the wrong ones.
        enableEdgeToEdge()
        bindSecureScreen(appLockRepository)
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
                // The widget must not be a way around the app lock (ADR 39):
                // while the process is LOCKED the sheet asks for the same PIN
                // or biometric, and the unlock opens the whole app session.
                val lockState by appLockManager.state.collectAsStateWithLifecycle()
                when (lockState) {
                    // Translucent window, nothing to cover: the launcher is
                    // what shows while the gate resolves.
                    AppLockState.EVALUATING -> Unit
                    AppLockState.LOCKED -> QuickEntryLockSheet(onDismiss = ::dismiss)
                    AppLockState.UNLOCKED -> QuickEntrySheet(
                        viewModel = hiltViewModel<QuickEntryViewModel, QuickEntryViewModel.Factory>(
                            creationCallback = { factory -> factory.create(route) },
                        ),
                        onDismiss = ::dismiss,
                    )
                }
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
            intent = Intent(context, QuickEntryActivity::class.java)
                // Paired with the empty taskAffinity in the manifest: its own
                // task, so the app's is never dragged in front of the launcher.
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            type = type,
            categoryId = categoryId,
            accountId = accountId,
        )
    }
}
