package com.callbackdev.saldo.feature.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    /** Categories usable as expenses (EXPENSE or BOTH), in global sort order. */
    val expenses: List<Category> = emptyList(),
    /** Categories usable as incomes (INCOME or BOTH), in global sort order. */
    val incomes: List<Category> = emptyList(),
    /** Full list in global sort order, used to translate a per-tab reorder. */
    val all: List<Category> = emptyList(),
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
            CategoriesUiState(
                isLoading = false,
                expenses = all.filter { it.type.usableForExpenses },
                incomes = all.filter { it.type.usableForIncomes },
                all = all,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = CategoriesUiState(),
        )

    /**
     * Persists a manual reorder performed inside the [type] tab. [orderedTabIds]
     * is the new order of that tab's categories; the global order keeps every
     * other category in place and only rewrites the reordered tab's slots, so a
     * single [Category.sortOrder] sequence stays the source of truth for both
     * tabs (and the transaction category grid).
     */
    fun persistOrder(type: CategoryType, orderedTabIds: List<Long>) {
        val all = uiState.value.all
        val expectedIds = all.filter { it.type.usableForTab(type) }.map { it.id }
        // Ignore stale drops (a concurrent add/delete changed the tab membership).
        if (expectedIds.toSet() != orderedTabIds.toSet()) return
        if (expectedIds == orderedTabIds) return

        val byId = all.associateBy { it.id }
        val next = orderedTabIds.iterator()
        val newOrder = all.map { category ->
            if (category.type.usableForTab(type)) byId.getValue(next.next()) else category
        }
        viewModelScope.launch { categoryRepository.reorder(newOrder) }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
