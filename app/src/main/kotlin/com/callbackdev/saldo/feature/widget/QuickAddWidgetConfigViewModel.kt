package com.callbackdev.saldo.feature.widget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.callbackdev.saldo.core.domain.model.Account
import com.callbackdev.saldo.core.domain.model.Category
import com.callbackdev.saldo.core.domain.model.CategoryType
import com.callbackdev.saldo.core.domain.model.TransactionType
import com.callbackdev.saldo.core.domain.repository.AccountRepository
import com.callbackdev.saldo.core.domain.repository.CategoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class QuickAddWidgetConfigUiState(
    val isLoading: Boolean = true,
    val config: QuickAddWidgetConfig = QuickAddWidgetConfig(),
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    /** True when this widget has been through here before, so the action reads "update". */
    val isConfigured: Boolean = false,
) {
    /** Null means the widget follows the app's default account, which is the default. */
    val selectedAccount: Account? get() = accounts.firstOrNull { it.id == config.accountId }
}

/**
 * Backs the widget's configuration screen. Everything here is optional: the
 * widget already works with the app defaults, so this only exists to pin a
 * different account, a fixed set of categories, or to start on income.
 */
@HiltViewModel
class QuickAddWidgetConfigViewModel @Inject constructor(
    accountRepository: AccountRepository,
    categoryRepository: CategoryRepository,
) : ViewModel() {

    private val config = MutableStateFlow(QuickAddWidgetConfig())
    private val configured = MutableStateFlow(false)

    val uiState: StateFlow<QuickAddWidgetConfigUiState> = combine(
        config,
        configured,
        accountRepository.observeAccountsWithBalance(),
        categoryRepository.observeCategories(),
    ) { current, isConfigured, accounts, categories ->
        val type = if (current.type == TransactionType.INCOME) CategoryType.INCOME else CategoryType.EXPENSE
        QuickAddWidgetConfigUiState(
            isLoading = false,
            config = current,
            accounts = accounts.map { it.account }.filter { !it.isArchived },
            categories = categories.filter { it.type == type || it.type == CategoryType.BOTH },
            isConfigured = isConfigured,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = QuickAddWidgetConfigUiState(),
    )

    /** Seeds the form from the stored state when reconfiguring an existing widget. */
    fun initialize(stored: QuickAddWidgetConfig, isConfigured: Boolean) {
        config.value = stored
        configured.value = isConfigured
    }

    fun onAccountSelected(accountId: Long?) {
        config.update { it.copy(accountId = accountId) }
    }

    fun onTypeSelected(type: TransactionType) {
        // Pinned categories belong to the previous type's set and would resolve
        // to nothing after the switch, so the grid goes back to adaptive.
        config.update { it.copy(type = type, pinnedCategoryIds = emptyList()) }
    }

    fun onShowTodayTotalChanged(show: Boolean) {
        config.update { it.copy(showTodayTotal = show) }
    }

    fun onUseMostUsedChanged(useMostUsed: Boolean) {
        config.update {
            if (useMostUsed) {
                it.copy(pinnedCategoryIds = emptyList())
            } else {
                // Seeding with the categories currently on screen means turning
                // the switch off never empties the widget.
                it.copy(pinnedCategoryIds = uiState.value.categories.take(MAX_PINNED).map(Category::id))
            }
        }
    }

    fun onAppearanceSelected(appearance: WidgetAppearance) {
        config.update { it.copy(appearance = appearance) }
    }

    fun onCategoryToggled(categoryId: Long) {
        config.update { current ->
            val pinned = current.pinnedCategoryIds
            when {
                categoryId in pinned -> current.copy(pinnedCategoryIds = pinned - categoryId)
                pinned.size >= MAX_PINNED -> current
                else -> current.copy(pinnedCategoryIds = pinned + categoryId)
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val MAX_PINNED = SaldoQuickAddWidget.MaxCategorySlots
    }
}
