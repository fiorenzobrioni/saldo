package com.callbackdev.saldo.feature.widget

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.callbackdev.saldo.R
import com.callbackdev.saldo.feature.applock.LockViewModel
import com.callbackdev.saldo.feature.applock.PinEntryPane
import com.callbackdev.saldo.feature.applock.formatCooldown
import com.callbackdev.saldo.feature.applock.rememberBiometricUnlock

/**
 * The lock pane of the widget quick-add sheet (ADR 39): the widget must not
 * be a way around the app lock, so while the process is LOCKED the sheet
 * shows the same PIN pane as the lock screen, compact, with the biometric
 * prompt auto-launched. The unlock opens the whole process: the app behind
 * is unlocked too, and a recently unlocked app (within the timeout) opens
 * this sheet with no gate at all.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickEntryLockSheet(
    onDismiss: () -> Unit,
    viewModel: LockViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val biometricPrompt = rememberBiometricUnlock(
        title = stringResource(R.string.lock_biometric_prompt_title),
        negativeText = stringResource(R.string.lock_biometric_prompt_negative),
        onSuccess = viewModel::onBiometricUnlocked,
    )
    var autoPrompted by remember { mutableStateOf(false) }
    LaunchedEffect(state.biometricsOffered) {
        if (state.biometricsOffered && !autoPrompted) {
            autoPrompted = true
            biometricPrompt.launch()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        PinEntryPane(
            title = stringResource(R.string.quick_entry_locked_title),
            subtitle = if (state.lockoutRemainingSeconds > 0) {
                stringResource(
                    R.string.lock_cooldown_message,
                    formatCooldown(state.lockoutRemainingSeconds),
                )
            } else {
                stringResource(R.string.quick_entry_locked_subtitle)
            },
            filledDigits = state.filledDigits,
            error = if (state.showError) stringResource(R.string.pin_error_wrong) else null,
            shakeTick = state.shakeTick,
            onDigit = viewModel::onDigit,
            onBackspace = viewModel::onBackspace,
            onBiometric = if (state.biometricsOffered) biometricPrompt::launch else null,
            keypadEnabled = state.lockoutRemainingSeconds == 0L && !state.isVerifying,
            compact = true,
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 16.dp),
        )
    }
}
