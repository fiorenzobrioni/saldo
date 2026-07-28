package com.callbackdev.saldo.feature.applock

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.callbackdev.saldo.R
import com.callbackdev.saldo.core.common.applock.AppLockState
import com.callbackdev.saldo.core.designsystem.theme.saldoSurfaces
import java.util.Locale

/**
 * The lock gate, composed above the whole app content in `MainActivity`
 * (never a navigation route, ADR 39): the Nav3 back stacks live untouched
 * underneath and re-locking never loses where the user was.
 *
 * EVALUATING draws an opaque surface (fail-closed: never a frame of
 * balances before the decision); LOCKED draws the lock screen; UNLOCKED
 * draws nothing.
 */
@Composable
fun AppLockGate(modifier: Modifier = Modifier) {
    val viewModel: LockViewModel = hiltViewModel()
    val lockState by viewModel.lockState.collectAsStateWithLifecycle()
    when (lockState) {
        AppLockState.EVALUATING -> Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.saldoSurfaces.canvas)
                .consumeAllPointerInput(),
        )
        AppLockState.LOCKED -> LockScreen(viewModel = viewModel, modifier = modifier)
        AppLockState.UNLOCKED -> Unit
    }
}

/** Full-screen PIN + biometric unlock, drawn over the app content. */
@Composable
private fun LockScreen(
    viewModel: LockViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val biometricPrompt = rememberBiometricUnlock(
        title = stringResource(R.string.lock_biometric_prompt_title),
        negativeText = stringResource(R.string.lock_biometric_prompt_negative),
        onSuccess = viewModel::onBiometricUnlocked,
    )

    // The prompt opens by itself once per lock event (this composable enters
    // the tree fresh on every re-lock); cancelling it leaves the keypad, and
    // the fingerprint key re-launches it on demand.
    var autoPrompted by remember { mutableStateOf(false) }
    LaunchedEffect(state.biometricsOffered) {
        if (state.biometricsOffered && !autoPrompted) {
            autoPrompted = true
            biometricPrompt.launch()
        }
    }

    Surface(
        color = MaterialTheme.saldoSurfaces.canvas,
        // An overlay Surface does not consume touches by itself: without
        // this, taps on empty areas would reach the app underneath.
        modifier = modifier
            .fillMaxSize()
            .consumeAllPointerInput(),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
        ) {
            Spacer(Modifier.height(64.dp))
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(64.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineSmall,
            )
            PinEntryPane(
                title = null,
                subtitle = lockSubtitle(state),
                filledDigits = state.filledDigits,
                error = if (state.showError) stringResource(R.string.pin_error_wrong) else null,
                shakeTick = state.shakeTick,
                onDigit = viewModel::onDigit,
                onBackspace = viewModel::onBackspace,
                onBiometric = if (state.biometricsOffered) biometricPrompt::launch else null,
                keypadEnabled = state.lockoutRemainingSeconds == 0L && !state.isVerifying,
                fillHeight = true,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 16.dp),
            )
        }
    }
}

/** The line under the app name: the cooldown countdown when running, the invite otherwise. */
@Composable
private fun lockSubtitle(state: LockUiState): String =
    if (state.lockoutRemainingSeconds > 0) {
        stringResource(
            R.string.lock_cooldown_message,
            formatCooldown(state.lockoutRemainingSeconds),
        )
    } else {
        stringResource(R.string.lock_screen_subtitle)
    }

/** "1:05" style countdown; locale-aware digits. */
internal fun formatCooldown(seconds: Long): String =
    String.format(Locale.getDefault(), "%d:%02d", seconds / 60, seconds % 60)

/**
 * Makes an overlay swallow every touch that lands on it, so nothing leaks
 * to the covered content. Children (the keypad) still win the hit test.
 */
internal fun Modifier.consumeAllPointerInput(): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        do {
            val event = awaitPointerEvent()
            event.changes.forEach { it.consume() }
        } while (event.changes.any { it.pressed })
    }
}
