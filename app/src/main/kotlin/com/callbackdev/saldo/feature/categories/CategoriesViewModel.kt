package com.callbackdev.saldo.feature.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.callbackdev.saldo.core.common.coroutines.suspendRunCatching
import com.callbackdev.saldo.core.domain.model.Category
import com.callbackdev.saldo.core.domain.model.CategoryType
import com.callbackdev.saldo.core.domain.repository.CategoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Immutable UI state for the category list screen. */
data class CategoriesUiState(
    val isLoading: Boolean = true,
    /** Categories usable as expenses (EXPENSE or BOTH), in expense sort order. */
    val expenses: List<Category> = emptyList(),
    /** Categories usable as incomes (INCOME or BOTH), in income sort order. */
    val incomes: List<Category> = emptyList(),
) {
    fun forTab(type: CategoryType): List<Category> =
        if (type == CategoryType.INCOME) incomes else expenses
}

@HiltViewModel
class CategoriesViewModel @Inject constructor(
    private val categoryRepository: CategoryRepository,
) : ViewModel() {

    val uiState: StateFlow<CategoriesUiState> =
        categoryRepository.observeCategories().map { all ->
            // observeCategories() is globally ordered by sortOrder, which is the
            // expense tab's key; the income tab reads its own sortOrderIncome.
            CategoriesUiState(
                isLoading = false,
                expenses = all.filter { it.type.usableForExpenses },
                incomes = all.filter { it.type.usableForIncomes }
                    .sortedWith(compareBy({ it.sortOrderIncome }, { it.id })),
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = CategoriesUiState(),
        )

    /**
     * Persists a manual reorder performed inside the [type] tab. [orderedTabIds]
     * is the new order of that tab's categories; only that tab's sort key is
     * rewritten, so reordering one tab never disturbs the other (BOTH categories
     * keep an independent position in each).
     */
    fun persistOrder(type: CategoryType, orderedTabIds: List<Long>) {
        val tabCategories = uiState.value.forTab(type)
        val expectedIds = tabCategories.map { it.id }
        // Ignore stale drops (a concurrent add/delete changed the tab membership).
        if (expectedIds.toSet() != orderedTabIds.toSet()) return
        if (expectedIds == orderedTabIds) return

        val byId = tabCategories.associateBy { it.id }
        val ordered = orderedTabIds.map { byId.getValue(it) }
        viewModelScope.launch {
            // No user-facing error: on failure the list simply snaps back to
            // the persisted order at the next emission.
            suspendRunCatching { categoryRepository.reorder(type, ordered) }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
