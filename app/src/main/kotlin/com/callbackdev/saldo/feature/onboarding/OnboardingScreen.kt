package com.callbackdev.saldo.feature.onboarding

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.callbackdev.saldo.R
import com.callbackdev.saldo.core.common.date.withLocaleDateCasing
import com.callbackdev.saldo.core.domain.backup.BackupSummary
import com.callbackdev.saldo.core.domain.usecase.ImportBackupUseCase
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Same permissive set as the Backup screen: SAF providers vary in MIME accuracy. */
private val RESTORE_MIME_TYPES = arrayOf("application/json", "application/octet-stream", "text/plain")

/**
 * First-launch onboarding: a five-page guided flow (welcome, privacy value
 * proposition, currency, first account with optional backup restore,
 * notifications). Pages advance only via their CTAs ([HorizontalPager] with
 * user scrolling off) so the input pages cannot be swiped past mid-form; the
 * system back gesture steps back one page. [onFinished] flips the launch gate:
 * it is called exactly once, from the last page.
 */
@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState(pageCount = { OnboardingPage.entries.size })
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::onRestoreFilePicked) }

    // Both outcomes finish the onboarding: a denial is a valid answer, and the
    // Settings reminder toggle re-asks contextually if the user changes their mind.
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { onFinished() }

    LaunchedEffect(uiState.page) {
        pagerState.animateScrollToPage(uiState.page.ordinal)
    }
    BackHandler(enabled = uiState.page != OnboardingPage.WELCOME) {
        viewModel.back()
    }

    val resources = LocalResources.current
    LaunchedEffect(viewModel, resources) {
        viewModel.events.collect { event ->
            snackbarHostState.showSnackbar(resources.getString(event.messageRes()))
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                // The Scaffold padding already covers the system bars; consume
                // it so imePadding does not re-apply the navigation bar inset.
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .imePadding(),
        ) {
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = false,
                modifier = Modifier.weight(1f),
            ) { pageIndex ->
                when (OnboardingPage.entries[pageIndex]) {
                    OnboardingPage.WELCOME -> WelcomePage()
                    OnboardingPage.PRIVACY -> PrivacyPage()
                    OnboardingPage.CURRENCY -> CurrencyPage(
                        selected = uiState.selectedCurrency,
                        onSelected = viewModel::onCurrencySelected,
                    )
                    OnboardingPage.ACCOUNT -> AccountPage(
                        uiState = uiState,
                        onNameChanged = viewModel::onAccountNameChanged,
                        onBalanceChanged = viewModel::onBalanceChanged,
                    )
                    OnboardingPage.NOTIFICATIONS -> NotificationsPage()
                }
            }
            PageIndicator(current = uiState.page)
            OnboardingActions(
                uiState = uiState,
                onNext = viewModel::next,
                onConfirmCurrency = viewModel::confirmCurrency,
                onCreateAccount = viewModel::createAccount,
                onSkipAccount = viewModel::skipAccount,
                onRestoreRequest = { restoreLauncher.launch(RESTORE_MIME_TYPES) },
                onEnableNotifications = {
                    val granted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS,
                    ) == PackageManager.PERMISSION_GRANTED
                    if (granted) {
                        onFinished()
                    } else {
                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
                onSkipNotifications = onFinished,
            )
        }
    }

    uiState.pendingRestore?.let { summary ->
        RestoreConfirmationDialog(
            summary = summary,
            onConfirm = viewModel::onRestoreConfirmed,
            onDismiss = viewModel::onRestoreDismissed,
        )
    }
}

/** User-facing message for each one-shot outcome. */
private fun OnboardingEvent.messageRes(): Int = when (this) {
    is OnboardingEvent.AccountSaveFailed -> R.string.editor_write_failed
    is OnboardingEvent.RestoreFailed -> R.string.backup_snackbar_restore_failed
    is OnboardingEvent.RestoreCompleted -> R.string.onboarding_restore_done
    is OnboardingEvent.RestoreInvalid -> when (error) {
        ImportBackupUseCase.Error.NOT_A_BACKUP -> R.string.backup_error_not_a_backup
        ImportBackupUseCase.Error.UNSUPPORTED_VERSION -> R.string.backup_error_unsupported_version
        ImportBackupUseCase.Error.CORRUPTED -> R.string.backup_error_corrupted
    }
}

/** The bottom CTA block; buttons change with the page, layout stays anchored. */
@Composable
private fun OnboardingActions(
    uiState: OnboardingUiState,
    onNext: () -> Unit,
    onConfirmCurrency: () -> Unit,
    onCreateAccount: () -> Unit,
    onSkipAccount: () -> Unit,
    onRestoreRequest: () -> Unit,
    onEnableNotifications: () -> Unit,
    onSkipNotifications: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        when (uiState.page) {
            OnboardingPage.WELCOME -> PrimaryCta(
                text = stringResource(R.string.onboarding_welcome_cta),
                onClick = onNext,
            )

            OnboardingPage.PRIVACY -> PrimaryCta(
                text = stringResource(R.string.onboarding_privacy_cta),
                onClick = onNext,
            )

            OnboardingPage.CURRENCY -> PrimaryCta(
                text = stringResource(R.string.onboarding_currency_cta),
                onClick = onConfirmCurrency,
                enabled = !uiState.isWorking,
            )

            OnboardingPage.ACCOUNT -> {
                PrimaryCta(
                    text = stringResource(R.string.onboarding_account_cta),
                    onClick = onCreateAccount,
                    enabled = uiState.isAccountNameValid && !uiState.isWorking,
                )
                SecondaryCta(
                    text = stringResource(R.string.onboarding_account_restore),
                    onClick = onRestoreRequest,
                    enabled = !uiState.isWorking,
                )
                SecondaryCta(
                    text = stringResource(R.string.onboarding_account_skip),
                    onClick = onSkipAccount,
                    enabled = !uiState.isWorking,
                )
            }

            OnboardingPage.NOTIFICATIONS -> {
                PrimaryCta(
                    text = stringResource(R.string.onboarding_notifications_cta),
                    onClick = onEnableNotifications,
                )
                SecondaryCta(
                    text = stringResource(R.string.onboarding_notifications_skip),
                    onClick = onSkipNotifications,
                )
            }
        }
    }
}

@Composable
private fun PrimaryCta(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.large,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
    ) {
        Text(text = text, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun SecondaryCta(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
    ) {
        Text(text)
    }
}

/** Dots above the CTA: the active page reads as a wide pill. */
@Composable
private fun PageIndicator(
    current: OnboardingPage,
    modifier: Modifier = Modifier,
) {
    val pages = OnboardingPage.entries
    val description = stringResource(
        R.string.onboarding_step_of,
        current.ordinal + 1,
        pages.size,
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp, alignment = Alignment.CenterHorizontally),
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .semantics { contentDescription = description },
    ) {
        pages.forEach { page ->
            val active = page == current
            Box(
                modifier = Modifier
                    .height(8.dp)
                    .width(if (active) 24.dp else 8.dp)
                    .animateContentSize()
                    .background(
                        color = if (active) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        shape = CircleShape,
                    ),
            )
        }
    }
}

/**
 * Friendlier variant of the Backup screen's confirmation: same two-step
 * safety, onboarding tone (there is no current data to lose on first launch).
 */
@Composable
private fun RestoreConfirmationDialog(
    summary: BackupSummary,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.onboarding_restore_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(
                        R.string.onboarding_restore_body,
                        formatBackupDate(summary.exportedAt),
                    ),
                )
                Text(
                    stringResource(
                        R.string.backup_restore_dialog_counts,
                        summary.accounts,
                        summary.transactions,
                        summary.categories,
                        summary.recurringRules,
                        summary.tags,
                        summary.budgets,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.onboarding_restore_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.onboarding_restore_cancel))
            }
        },
    )
}

/** Localized "5 Jul 2026" for the restore dialog. */
@Composable
private fun formatBackupDate(instant: Instant): String {
    val locale: Locale = LocalConfiguration.current.locales[0]
    return remember(instant, locale) {
        val pattern = android.text.format.DateFormat.getBestDateTimePattern(locale, "dMMMyyyy")
        instant.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern(pattern, locale))
            .withLocaleDateCasing(locale)
    }
}
