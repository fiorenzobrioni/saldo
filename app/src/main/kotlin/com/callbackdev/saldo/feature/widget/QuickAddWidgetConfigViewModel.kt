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
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class QuickAddWidgetConfigUiState(
    val isLoading: Boolean = true,
    val config: QuickAddWidgetConfig = QuickAddWidgetConfig(),
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
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

    /**
     * True once [initialize] has seeded the form from the stored state. The
     * screen stays on its loading state until then: showing the defaults while
     * the stored configuration is still in flight flashed wrong values at the
     * user, and a toggle tapped in that window was silently overwritten by the
     * seed landing after it.
     */
    private val seeded = MutableStateFlow(false)

    val uiState: StateFlow<QuickAddWidgetConfigUiState> = combine(
        config,
        seeded,
        // Plain rows: this screen shows no balances either.
        accountRepository.observeAccounts(),
        categoryRepository.observeCategories(),
    ) { current, ready, accounts, categories ->
        val type = if (current.type == TransactionType.INCOME) CategoryType.INCOME else CategoryType.EXPENSE
        QuickAddWidgetConfigUiState(
            isLoading = !ready,
            config = current,
            accounts = accounts.filter { !it.isArchived },
            categories = categories.filter { it.type == type || it.type == CategoryType.BOTH },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = QuickAddWidgetConfigUiState(),
    )

    /** Seeds the form from the stored state when reconfiguring an existing widget. */
    fun initialize(stored: QuickAddWidgetConfig) {
        config.value = stored
        seeded.value = true
    }

    fun onAccountSelected(accountId: Long?) {
        config.update { it.copy(accountId = accountId) }
    }

    fun onTypeSelected(type: TransactionType) {
        // Pinned categories belong to the previous type's set and would resolve
        // to nothing after the switch, so the grid goes back to adaptive.
        config.update { it.copy(type = type, pinnedCategoryIds = emptyList()) }
    }

    fun onShowAppShortcutChanged(show: Boolean) {
        config.update { it.copy(showAppShortcut = show) }
    }

    /**
     * On: a hand-picked subset, seeded with the categories currently on screen
     * so the widget never empties. Off: back to the app's own category order.
     */
    fun onCustomCategoriesChanged(custom: Boolean) {
        config.update {
            if (custom) {
                it.copy(pinnedCategoryIds = uiState.value.categories.take(MAX_PINNED).map(Category::id))
            } else {
                it.copy(pinnedCategoryIds = emptyList())
            }
        }
    }

    fun onButtonsSelected(buttons: WidgetActionButtons) {
        config.update { it.copy(buttons = buttons) }
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

    /**
     * Adopts the order the drag settled on. The set must match what is pinned
     * right now: the screen mirrors the list locally while dragging, and a
     * stale drop (a category removed mid-drag by another hand) must not
     * resurrect or lose entries.
     */
    fun onPinnedReordered(orderedIds: List<Long>) {
        config.update { current ->
            if (orderedIds.toSet() == current.pinnedCategoryIds.toSet()) {
                current.copy(pinnedCategoryIds = orderedIds)
            } else {
                current
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val MAX_PINNED = MaxPinnedCategories
    }
}
