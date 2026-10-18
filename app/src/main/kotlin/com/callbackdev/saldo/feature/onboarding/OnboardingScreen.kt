package com.callbackdev.saldo.feature.onboarding

import android.Manifest
import android.content.Context
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.unit.IntOffset
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.callbackdev.saldo.R
import com.callbackdev.saldo.core.common.date.withLocaleDateCasing
import com.callbackdev.saldo.core.designsystem.component.AmountKeypadHost
import com.callbackdev.saldo.core.designsystem.component.AmountTarget
import com.callbackdev.saldo.core.domain.backup.BackupSummary
import com.callbackdev.saldo.core.domain.money.MoneyMapper
import com.callbackdev.saldo.core.domain.usecase.ImportBackupUseCase
import com.callbackdev.saldo.feature.backup.UnlockPassphraseDialog
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Same permissive set as the Backup screen: SAF providers vary in MIME accuracy. */
private val RESTORE_MIME_TYPES = arrayOf("application/json", "application/octet-stream", "text/plain")

/**
 * First-launch onboarding (ADR 55): four pages (welcome with the three promises,
 * currency, first account with optional backup restore, notifications) in the
 * family's frame: the page's words centered in the room above the buttons, the
 * buttons at the bottom in the same place on every page, no picture and no page
 * indicator. Pages advance only via their CTAs, so the input pages cannot be
 * swiped past mid-form; the system back gesture steps back one page. [onFinished]
 * flips the launch gate: it is called exactly once, from the last page.
 */
@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
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

    val reduced = remember { animationsOff(context) }
    BackHandler(enabled = uiState.page != OnboardingPage.WELCOME) {
        viewModel.back()
    }

    // The account name comes first, so the keypad waits to be asked for; it
    // never survives leaving the account page.
    var keypadOpen by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(uiState.page) {
        if (uiState.page != OnboardingPage.ACCOUNT) keypadOpen = false
    }
    // Registered after the page-back handler, so it is the one that wins.
    BackHandler(enabled = keypadOpen) { keypadOpen = false }
    val balanceTarget = AmountTarget(
        value = uiState.balanceInput,
        fractionDigits = MoneyMapper.fractionDigits(uiState.selectedCurrency),
        // The first account can start in the red, like any other.
        allowNegative = true,
        onValueChange = viewModel::onBalanceChanged,
    )

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
            AnimatedContent(
                targetState = uiState.page,
                transitionSpec = { pageTransition(initialState, targetState, reduced) },
                label = "onboarding",
                modifier = Modifier.weight(1f),
            ) { page ->
                OnboardingPageFrame {
                    val where = stringResource(
                        R.string.onboarding_step_of,
                        page.ordinal + 1,
                        OnboardingPage.entries.size,
                    )
                    when (page) {
                        OnboardingPage.WELCOME -> WelcomePage(where)
                        OnboardingPage.CURRENCY -> CurrencyPage(
                            selected = uiState.selectedCurrency,
                            onSelected = viewModel::onCurrencySelected,
                            where = where,
                        )
                        OnboardingPage.ACCOUNT -> AccountPage(
                            uiState = uiState,
                            onNameChanged = viewModel::onAccountNameChanged,
                            balanceTarget = balanceTarget,
                            onActivateBalance = { keypadOpen = true },
                            onCloseKeypad = { keypadOpen = false },
                            where = where,
                        )
                        OnboardingPage.NOTIFICATIONS -> NotificationsPage(where)
                    }
                }
            }
            AmountKeypadHost(
                target = balanceTarget.takeIf { keypadOpen },
                onHide = { keypadOpen = false },
                modifier = Modifier.padding(horizontal = 12.dp),
            )
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

    uiState.unlockRequest?.let { request ->
        UnlockPassphraseDialog(
            request = request,
            onSubmit = viewModel::onUnlockPassphraseSubmitted,
            onDismiss = viewModel::onUnlockDismissed,
        )
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
        ImportBackupUseCase.Error.WRONG_PASSPHRASE -> R.string.backup_error_wrong_passphrase
        ImportBackupUseCase.Error.UNSUPPORTED_CONTAINER -> R.string.backup_error_unsupported_container
    }
}

/**
 * The page's words, centered in the room above the buttons; a page taller than the room (the
 * currency list, any page at a large text size) scrolls from its top. On an open foldable the
 * column keeps a phone's width.
 */
@Composable
private fun OnboardingPageFrame(content: @Composable ColumnScope.() -> Unit) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val room = maxHeight
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .heightIn(min = room)
                    .widthIn(max = PAGE_MAX_WIDTH)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
                content = content,
            )
        }
    }
}

private val PAGE_MAX_WIDTH = 640.dp
private const val PAGE_MILLIS = 300

/** Forward slides in from the end, back from the start; a fade alone under reduced motion. */
private fun pageTransition(from: OnboardingPage, to: OnboardingPage, reduced: Boolean): ContentTransform {
    if (reduced) return fadeIn(tween(REDUCED_FADE_MILLIS)) togetherWith fadeOut(tween(REDUCED_FADE_MILLIS))
    val shift = if (to.ordinal > from.ordinal) 6 else -6
    val slide = tween<IntOffset>(PAGE_MILLIS, easing = FastOutSlowInEasing)
    return (fadeIn(tween(PAGE_MILLIS)) + slideInHorizontally(slide) { it / shift }) togetherWith
        (fadeOut(tween(PAGE_MILLIS)) + slideOutHorizontally(slide) { -it / shift })
}

private const val REDUCED_FADE_MILLIS = 100

/** True when the user has turned system animations off (scale 0). */
private fun animationsOff(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

/** The bottom CTA block; buttons change with the page, the block stays anchored at the bottom. */
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
            .widthIn(max = PAGE_MAX_WIDTH)
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 16.dp)
            .animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (uiState.page) {
            OnboardingPage.WELCOME -> PrimaryCta(
                text = stringResource(R.string.onboarding_welcome_cta),
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
                QuietCta(
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
                QuietCta(
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
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp),
    ) {
        Text(text)
    }
}

/** A real alternative to the page's main step (restoring a backup instead of a new account). */
@Composable
private fun SecondaryCta(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp),
    ) {
        Text(text)
    }
}

/** The quiet way past: "later" is a real answer and costs nothing. */
@Composable
private fun QuietCta(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp),
    ) {
        Text(text)
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
                        summary.savingsGoals,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
                if (summary.hasSettings) {
                    Text(
                        text = stringResource(R.string.onboarding_restore_settings),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
