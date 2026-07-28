package com.callbackdev.saldo.feature.applock

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.callbackdev.saldo.R
import com.callbackdev.saldo.core.common.applock.AutoLockTimeout
import com.callbackdev.saldo.core.designsystem.component.SettingsEntry
import com.callbackdev.saldo.core.designsystem.component.SettingsGroup
import com.callbackdev.saldo.core.designsystem.component.SettingsSectionHeader
import com.callbackdev.saldo.core.designsystem.component.SettingsSwitchRow
import com.callbackdev.saldo.core.designsystem.theme.saldoSurfaces

/**
 * Security settings (ADR 39): the app lock (PIN, biometric shortcut,
 * auto-lock timeout) and the screen-privacy flag. The PIN flows (create,
 * confirm, verify) replace the settings list in place with the shared
 * [PinEntryPane] rather than opening dialogs or routes: a keypad needs the
 * full width, and a modal flow needs no result channel this way.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SecurityViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val flow = state.pinFlow

    // System back while a PIN flow is open cancels the flow, not the screen.
    BackHandler(enabled = flow != null) { viewModel.onFlowDismissed() }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.saldoSurfaces.canvas,
        topBar = {
            TopAppBar(
                scrollBehavior = scrollBehavior,
                title = { Text(stringResource(R.string.security_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (flow != null) viewModel.onFlowDismissed() else onNavigateBack()
                        },
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        AnimatedContent(
            targetState = flow?.let { it.purpose to it.step },
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "security-pin-flow",
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) { flowKey ->
            if (flowKey == null) {
                SecuritySettingsList(
                    state = state,
                    onAppLockToggled = viewModel::onAppLockToggled,
                    onChangePinClicked = viewModel::onChangePinClicked,
                    onBiometricToggled = viewModel::setBiometricUnlockEnabled,
                    onAutoLockTimeoutSelected = viewModel::onAutoLockTimeoutSelected,
                    onSecureScreenChanged = viewModel::onSecureScreenChanged,
                )
            } else {
                // The pane reads the live flow state, not the transition
                // snapshot: digits and errors update inside one step.
                val liveFlow = state.pinFlow ?: return@AnimatedContent
                PinEntryPane(
                    title = flowStepTitle(liveFlow),
                    subtitle = flowStepSubtitle(liveFlow),
                    filledDigits = liveFlow.filledDigits,
                    error = liveFlow.error?.let { stringResource(it.messageRes()) },
                    shakeTick = liveFlow.shakeTick,
                    onDigit = viewModel::onDigit,
                    onBackspace = viewModel::onBackspace,
                    fillHeight = true,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp)
                        .padding(top = 32.dp, bottom = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun SecuritySettingsList(
    state: SecurityUiState,
    onAppLockToggled: (Boolean) -> Unit,
    onChangePinClicked: () -> Unit,
    onBiometricToggled: (Boolean) -> Unit,
    onAutoLockTimeoutSelected: (AutoLockTimeout) -> Unit,
    onSecureScreenChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        SettingsSectionHeader(stringResource(R.string.security_section_app_lock))
        SettingsGroup {
            SettingsSwitchRow(
                title = stringResource(R.string.security_app_lock_title),
                hint = stringResource(R.string.security_app_lock_hint),
                checked = state.lockEnabled,
                onCheckedChange = onAppLockToggled,
            )
            if (state.lockEnabled) {
                SettingsEntry(
                    title = stringResource(R.string.security_change_pin),
                    hint = stringResource(R.string.security_change_pin_hint),
                    icon = Icons.Outlined.Password,
                    onClick = onChangePinClicked,
                )
                if (state.biometricAvailable) {
                    BiometricSwitchRow(
                        checked = state.biometricUnlockEnabled,
                        onToggled = onBiometricToggled,
                    )
                }
                ListItem(
                    headlineContent = { Text(stringResource(R.string.security_auto_lock_title)) },
                    supportingContent = { Text(stringResource(R.string.security_auto_lock_hint)) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
                AutoLockTimeoutSelector(
                    selected = state.autoLockTimeout,
                    onSelected = onAutoLockTimeoutSelected,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                )
            }
        }

        SettingsSectionHeader(stringResource(R.string.security_section_screen_privacy))
        SettingsGroup {
            SettingsSwitchRow(
                title = stringResource(R.string.security_secure_screen_title),
                hint = stringResource(R.string.security_secure_screen_hint),
                checked = state.secureScreenEnabled,
                onCheckedChange = onSecureScreenChanged,
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

/**
 * The biometric opt-in. Turning it ON first proves the biometric actually
 * works with a confirmation prompt; the preference is written only on
 * success. Turning it OFF is immediate.
 */
@Composable
private fun BiometricSwitchRow(
    checked: Boolean,
    onToggled: (Boolean) -> Unit,
) {
    val confirmationPrompt = rememberBiometricUnlock(
        title = stringResource(R.string.security_biometric_confirm_title),
        negativeText = stringResource(R.string.action_cancel),
        onSuccess = { onToggled(true) },
    )
    SettingsSwitchRow(
        title = stringResource(R.string.security_biometric_title),
        hint = stringResource(R.string.security_biometric_hint),
        checked = checked,
        onCheckedChange = { enabled ->
            if (enabled) confirmationPrompt.launch() else onToggled(false)
        },
    )
}

/** Re-lock delay choice, shown only while the lock is on. */
@Composable
private fun AutoLockTimeoutSelector(
    selected: AutoLockTimeout,
    onSelected: (AutoLockTimeout) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = listOf(
        AutoLockTimeout.IMMEDIATELY to R.string.security_auto_lock_immediately,
        AutoLockTimeout.ONE_MINUTE to R.string.security_auto_lock_one_minute,
        AutoLockTimeout.FIVE_MINUTES to R.string.security_auto_lock_five_minutes,
    )
    SingleChoiceSegmentedButtonRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        options.forEachIndexed { index, (timeout, labelRes) ->
            SegmentedButton(
                selected = selected == timeout,
                onClick = { onSelected(timeout) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
            ) {
                Text(stringResource(labelRes))
            }
        }
    }
}

@Composable
private fun flowStepTitle(flow: PinFlowState): String = when (flow.step) {
    PinFlowStep.VERIFY -> stringResource(R.string.pin_verify_title)
    PinFlowStep.CREATE -> when (flow.purpose) {
        PinFlowPurpose.CHANGE -> stringResource(R.string.pin_create_new_title)
        else -> stringResource(R.string.pin_create_title)
    }
    PinFlowStep.CONFIRM -> stringResource(R.string.pin_confirm_title)
}

@Composable
private fun flowStepSubtitle(flow: PinFlowState): String = when (flow.step) {
    PinFlowStep.VERIFY -> stringResource(R.string.pin_verify_subtitle)
    PinFlowStep.CREATE -> stringResource(R.string.pin_create_subtitle)
    PinFlowStep.CONFIRM -> stringResource(R.string.pin_confirm_subtitle)
}

private fun PinFlowError.messageRes(): Int = when (this) {
    PinFlowError.WRONG_PIN -> R.string.pin_error_wrong
    PinFlowError.MISMATCH -> R.string.pin_error_mismatch
}
