package com.callbackdev.saldo.feature.onboarding

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.callbackdev.saldo.R
import com.callbackdev.saldo.core.common.coroutines.suspendRunCatching
import com.callbackdev.saldo.core.common.di.IoDispatcher
import com.callbackdev.saldo.core.common.money.MoneyInput
import com.callbackdev.saldo.core.common.prefs.UserPreferencesRepository
import com.callbackdev.saldo.core.designsystem.visuals.AccountVisuals
import com.callbackdev.saldo.core.domain.backup.BackupFile
import com.callbackdev.saldo.core.domain.backup.BackupSummary
import com.callbackdev.saldo.core.domain.model.Account
import com.callbackdev.saldo.core.domain.model.AccountType
import com.callbackdev.saldo.core.domain.model.fallbackCurrency
import com.callbackdev.saldo.core.domain.money.MoneyMapper
import com.callbackdev.saldo.core.domain.repository.AccountRepository
import com.callbackdev.saldo.core.domain.usecase.GenerateRecurringMovementsUseCase
import com.callbackdev.saldo.core.domain.usecase.ImportBackupUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.time.LocalDate
import java.util.Currency
import javax.inject.Inject

/** The onboarding pages, in presentation order (ordinal = pager index). */
enum class OnboardingPage { WELCOME, PRIVACY, CURRENCY, ACCOUNT, NOTIFICATIONS }

/** Immutable state of the onboarding flow. */
data class OnboardingUiState(
    val page: OnboardingPage = OnboardingPage.WELCOME,
    val selectedCurrency: Currency = fallbackCurrency,
    val accountName: String = "",
    val balanceInput: String = "",
    /** A validated backup awaiting the user's confirmation, or null. */
    val pendingRestore: BackupSummary? = null,
    /** True while a write or restore is running (CTAs are disabled). */
    val isWorking: Boolean = false,
) {
    val isAccountNameValid: Boolean get() = accountName.isNotBlank()
}

/** One-shot outcomes surfaced as snackbars. */
sealed interface OnboardingEvent {
    data object AccountSaveFailed : OnboardingEvent
    data class RestoreInvalid(val error: ImportBackupUseCase.Error) : OnboardingEvent
    data object RestoreFailed : OnboardingEvent
    data class RestoreCompleted(val summary: BackupSummary) : OnboardingEvent
}

/**
 * Drives the first-launch onboarding: currency choice (persisted as the
 * primary-currency override), inline creation of the first account, and the
 * optional restore from a backup file (same two-step inspect/confirm flow as
 * the Backup screen). Completion itself is owned by the caller
 * ([com.callbackdev.saldo.MainViewModel]), which flips the launch gate.
 */
@HiltViewModel
// One callback per form field/CTA is the natural shape of a guided flow.
@Suppress("LongParameterList", "TooManyFunctions")
class OnboardingViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val accountRepository: AccountRepository,
    private val userPreferences: UserPreferencesRepository,
    private val importBackup: ImportBackupUseCase,
    private val generateRecurringMovements: GenerateRecurringMovementsUseCase,
    private val clock: Clock,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    private val _events = Channel<OnboardingEvent>(Channel.BUFFERED)
    val events: Flow<OnboardingEvent> = _events.receiveAsFlow()

    /** A validated file held between inspection and confirmation. */
    private var pendingFile: BackupFile? = null

    /** Guards against a double-tap creating two accounts; reset on failure. */
    private var isSaving = false

    init {
        // Prefilled so the typical path is amount + one tap; still editable.
        _uiState.update {
            it.copy(accountName = context.getString(R.string.onboarding_account_default_name))
        }
    }

    fun next() {
        _uiState.update { state ->
            val nextOrdinal = (state.page.ordinal + 1)
                .coerceAtMost(OnboardingPage.entries.lastIndex)
            state.copy(page = OnboardingPage.entries[nextOrdinal])
        }
    }

    /** Steps one page back; returns false on the first page (back leaves the app). */
    fun back(): Boolean {
        val current = _uiState.value.page
        if (current == OnboardingPage.WELCOME) return false
        _uiState.update { it.copy(page = OnboardingPage.entries[current.ordinal - 1]) }
        return true
    }

    fun onCurrencySelected(currency: Currency) {
        _uiState.update { state ->
            state.copy(
                selectedCurrency = currency,
                balanceInput = rescale(state.balanceInput, currency),
            )
        }
    }

    /** Persists the currency choice (VISION: picked in onboarding) and advances. */
    fun confirmCurrency() {
        viewModelScope.launch {
            suspendRunCatching {
                userPreferences.setPrimaryCurrencyOverride(_uiState.value.selectedCurrency)
            }
            next()
        }
    }

    fun onAccountNameChanged(name: String) {
        _uiState.update { it.copy(accountName = name) }
    }

    fun onBalanceChanged(raw: String) {
        _uiState.update {
            it.copy(
                balanceInput = MoneyInput.sanitize(
                    raw,
                    MoneyMapper.fractionDigits(it.selectedCurrency),
                ),
            )
        }
    }

    /** Creates the first account with sensible defaults and moves on. */
    fun createAccount() {
        val state = _uiState.value
        if (isSaving || !state.isAccountNameValid) return
        isSaving = true
        _uiState.update { it.copy(isWorking = true) }
        viewModelScope.launch {
            val account = Account(
                name = state.accountName.trim(),
                type = AccountType.CHECKING,
                currency = state.selectedCurrency,
                initialBalance = MoneyInput.parse(state.balanceInput) ?: BigDecimal.ZERO,
                color = AccountVisuals.defaultColorFor(AccountType.CHECKING),
                icon = AccountVisuals.defaultIconFor(AccountType.CHECKING),
                isIncludedInTotal = true,
                isIncludedInBudget = true,
                createdAt = clock.instant(),
            )
            val result = suspendRunCatching { accountRepository.upsert(account) }
            isSaving = false
            _uiState.update { it.copy(isWorking = false) }
            result
                .onSuccess {
                    _uiState.update { it.copy(page = OnboardingPage.NOTIFICATIONS) }
                }
                .onFailure { _events.send(OnboardingEvent.AccountSaveFailed) }
        }
    }

    /** Skips account creation; the dashboard empty state offers it again later. */
    fun skipAccount() {
        _uiState.update { it.copy(page = OnboardingPage.NOTIFICATIONS) }
    }

    /** Validates the picked backup and, if readable, asks for confirmation. */
    fun onRestoreFilePicked(uri: Uri) {
        if (_uiState.value.isWorking) return
        _uiState.update { it.copy(isWorking = true) }
        viewModelScope.launch {
            val inspection = suspendRunCatching { importBackup.inspect(readDocument(uri)) }
            _uiState.update { it.copy(isWorking = false) }
            inspection
                .onSuccess { outcome ->
                    when (outcome) {
                        is ImportBackupUseCase.Inspection.Valid -> {
                            pendingFile = outcome.file
                            _uiState.update { it.copy(pendingRestore = outcome.summary) }
                        }

                        is ImportBackupUseCase.Inspection.Invalid ->
                            _events.send(OnboardingEvent.RestoreInvalid(outcome.error))
                    }
                }
                .onFailure { _events.send(OnboardingEvent.RestoreFailed) }
        }
    }

    /** Restores the confirmed backup, then skips account creation entirely. */
    fun onRestoreConfirmed() {
        val file = pendingFile ?: return
        val summary = _uiState.value.pendingRestore ?: return
        pendingFile = null
        _uiState.update { it.copy(pendingRestore = null, isWorking = true) }
        viewModelScope.launch {
            val result = suspendRunCatching { importBackup.restore(file) }
            // Catch up restored rules right away (a backup may be days old);
            // failures here do not fail the restore (the launch catch-up retries).
            result.onSuccess {
                suspendRunCatching { generateRecurringMovements(LocalDate.now(clock)) }
            }
            _uiState.update { it.copy(isWorking = false) }
            result
                .onSuccess {
                    _events.send(OnboardingEvent.RestoreCompleted(summary))
                    _uiState.update { it.copy(page = OnboardingPage.NOTIFICATIONS) }
                }
                .onFailure { _events.send(OnboardingEvent.RestoreFailed) }
        }
    }

    fun onRestoreDismissed() {
        pendingFile = null
        _uiState.update { it.copy(pendingRestore = null) }
    }

    /** Rescales a typed amount when the currency allows fewer decimals (e.g. JPY). */
    private fun rescale(input: String, currency: Currency): String {
        val digits = MoneyMapper.fractionDigits(currency)
        val parsed = MoneyInput.parse(input) ?: return input
        return if (parsed.scale() > digits) {
            parsed.setScale(digits, RoundingMode.HALF_UP).toPlainString()
        } else {
            input
        }
    }

    private suspend fun readDocument(uri: Uri): String =
        withContext(ioDispatcher) {
            val stream = context.contentResolver.openInputStream(uri)
                ?: throw IOException("Cannot open $uri for reading")
            stream.bufferedReader().use { it.readText() }
        }
}
