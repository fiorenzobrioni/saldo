package com.callbackdev.saldo.feature.recap

import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.callbackdev.saldo.core.common.prefs.UserPreferencesRepository
import com.callbackdev.saldo.core.common.coroutines.suspendRunCatching
import com.callbackdev.saldo.core.domain.model.Category
import com.callbackdev.saldo.core.domain.model.MonthlyRecap
import com.callbackdev.saldo.core.domain.model.fallbackCurrency
import com.callbackdev.saldo.core.domain.model.primaryCurrency
import com.callbackdev.saldo.core.domain.repository.AccountRepository
import com.callbackdev.saldo.core.domain.repository.CategoryRepository
import com.callbackdev.saldo.core.domain.usecase.GetMonthlyRecapUseCase
import com.callbackdev.saldo.navigation.MonthlyRecapRoute
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.util.Currency

/** Immutable UI state of the monthly recap. */
data class MonthlyRecapUiState(
    val isLoading: Boolean = true,
    val month: YearMonth = YearMonth.of(EPOCH_YEAR, 1),
    val currency: Currency = fallbackCurrency,
    val recap: MonthlyRecap? = null,
    /** Categories referenced by the recap, for name/color/icon resolution. */
    val categoryById: Map<Long, Category> = emptyMap(),
) {
    /** True when the month has nothing to recap. */
    val isEmpty: Boolean get() = !isLoading && recap?.hasData != true

    private companion object {
        const val EPOCH_YEAR = 1970
    }
}

/** One-shot effects of the recap screen. */
sealed interface MonthlyRecapEvent {
    /** The summary image is ready to hand to the share sheet. */
    data class ShareReady(val uri: Uri) : MonthlyRecapEvent
    data object ShareFailed : MonthlyRecapEvent
}

/**
 * Loads the [MonthlyRecap] of the routed month once (the month is completed,
 * its figures cannot change while the screen is open) and resolves the
 * categories it references. Sharing renders off the screen's recorded
 * summary card: the bitmap arrives from the UI, the PNG write and the
 * FileProvider round-trip happen here.
 */
@HiltViewModel(assistedFactory = MonthlyRecapViewModel.Factory::class)
class MonthlyRecapViewModel @AssistedInject constructor(
    @Assisted private val route: MonthlyRecapRoute,
    accountRepository: AccountRepository,
    categoryRepository: CategoryRepository,
    userPreferences: UserPreferencesRepository,
    private val getMonthlyRecap: GetMonthlyRecapUseCase,
    private val recapImageSharer: RecapImageSharer,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(route: MonthlyRecapRoute): MonthlyRecapViewModel
    }

    private val month = YearMonth.of(route.year, route.month)

    private val _uiState = MutableStateFlow(MonthlyRecapUiState(month = month))
    val uiState: StateFlow<MonthlyRecapUiState> = _uiState.asStateFlow()

    private val _events = Channel<MonthlyRecapEvent>(Channel.BUFFERED)
    val events: Flow<MonthlyRecapEvent> = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            val accounts = accountRepository.observeAccountsWithBalance().first()
            val override = userPreferences.primaryCurrencyOverride.first()
            val currency = primaryCurrency(accounts, override)
            val recap = getMonthlyRecap(month, currency)
            val categories = categoryRepository.observeCategories().first()
            _uiState.value = MonthlyRecapUiState(
                isLoading = false,
                month = month,
                currency = currency,
                recap = recap,
                categoryById = categories.associateBy { it.id },
            )
        }
    }

    /** Writes [image] as the shareable PNG and emits the share-sheet event. */
    fun onShareRequested(image: ImageBitmap) {
        viewModelScope.launch {
            suspendRunCatching { recapImageSharer.share(image, month) }
                .onSuccess { uri -> _events.send(MonthlyRecapEvent.ShareReady(uri)) }
                .onFailure { _events.send(MonthlyRecapEvent.ShareFailed) }
        }
    }
}
