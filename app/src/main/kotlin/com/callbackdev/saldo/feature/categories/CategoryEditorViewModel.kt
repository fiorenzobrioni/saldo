package com.callbackdev.saldo.feature.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.callbackdev.saldo.core.common.coroutines.suspendRunCatching
import com.callbackdev.saldo.core.designsystem.visuals.CategoryVisuals
import com.callbackdev.saldo.core.domain.model.Category
import com.callbackdev.saldo.core.domain.model.CategoryType
import com.callbackdev.saldo.core.domain.repository.BudgetRepository
import com.callbackdev.saldo.core.domain.repository.CategoryRepository
import com.callbackdev.saldo.core.domain.repository.TransactionRepository
import com.callbackdev.saldo.navigation.CategoryEditorRoute
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Immutable UI state of the category editor form. */
data class CategoryEditorUiState(
    val isLoading: Boolean,
    val isNew: Boolean = true,
    val name: String = "",
    val type: CategoryType = CategoryType.EXPENSE,
    val color: Int = CategoryVisuals.defaultColor,
    val icon: String = CategoryVisuals.defaultIconKey,
    /** Set on a failed save attempt to surface the name error. */
    val showValidation: Boolean = false,
    val deleteDialog: CategoryDeleteDialog? = null,
    /** True when deleting also removes the category's monthly budget (CASCADE). */
    val deleteAlsoRemovesBudget: Boolean = false,
) {
    val isNameValid: Boolean get() = name.isNotBlank()
}

/** Confirmation flows for deleting the category being edited. */
sealed interface CategoryDeleteDialog {

    /** The category labels no movement: a plain confirmation is enough. */
    data object Confirm : CategoryDeleteDialog

    /**
     * The category labels [movementCount] movements but no other compatible
     * category exists: they will be left uncategorized.
     */
    data class ConfirmUncategorize(val movementCount: Int) : CategoryDeleteDialog

    /**
     * The category labels [movementCount] movements: they must be reassigned to
     * one of [candidates] (defaulting to [selectedTargetId]) before deletion.
     */
    data class Reassign(
        val movementCount: Int,
        val candidates: List<Category>,
        val selectedTargetId: Long,
    ) : CategoryDeleteDialog
}

/** One-shot events consumed by the editor screen. */
sealed interface CategoryEditorEvent {
    data object Saved : CategoryEditorEvent

    data object Deleted : CategoryEditorEvent

    /** The category to edit no longer exists: leave the screen. */
    data object CategoryMissing : CategoryEditorEvent

    /** A write failed: stay on the screen and let the user retry. */
    data object WriteFailed : CategoryEditorEvent
}

@HiltViewModel(assistedFactory = CategoryEditorViewModel.Factory::class)
@Suppress("TooManyFunctions")
class CategoryEditorViewModel @AssistedInject constructor(
    @Assisted private val route: CategoryEditorRoute,
    private val categoryRepository: CategoryRepository,
    private val transactionRepository: TransactionRepository,
    private val budgetRepository: BudgetRepository,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(route: CategoryEditorRoute): CategoryEditorViewModel
    }

    private val _uiState = MutableStateFlow(
        CategoryEditorUiState(
            isLoading = route.categoryId != null,
            isNew = route.categoryId == null,
            type = route.initialType(),
        ),
    )
    val uiState: StateFlow<CategoryEditorUiState> = _uiState.asStateFlow()

    private val _events = Channel<CategoryEditorEvent>(Channel.BUFFERED)
    val events: Flow<CategoryEditorEvent> = _events.receiveAsFlow()

    /** Guards against a double-tap on save creating two categories; reset on failure. */
    private var isSaving = false

    /** The persisted category being edited; null in create mode. */
    private var existing: Category? = null

    init {
        route.categoryId?.let(::loadCategory)
    }

    private fun loadCategory(categoryId: Long) {
        viewModelScope.launch {
            val category = categoryRepository.getCategory(categoryId)
            if (category == null) {
                _events.send(CategoryEditorEvent.CategoryMissing)
                return@launch
            }
            existing = category
            _uiState.update {
                it.copy(
                    isLoading = false,
                    isNew = false,
                    name = category.name,
                    type = category.type,
                    color = category.color,
                    icon = category.icon,
                )
            }
        }
    }

    fun onNameChanged(name: String) {
        _uiState.update { it.copy(name = name) }
    }

    fun onTypeChanged(type: CategoryType) {
        _uiState.update { it.copy(type = type) }
    }

    fun onColorSelected(color: Int) {
        _uiState.update { it.copy(color = color) }
    }

    fun onIconSelected(icon: String) {
        _uiState.update { it.copy(icon = icon) }
    }

    fun save() {
        val state = _uiState.value
        if (state.isLoading || isSaving) return
        if (!state.isNameValid) {
            _uiState.update { it.copy(showValidation = true) }
            return
        }
        val base = existing
        isSaving = true
        viewModelScope.launch {
            val result = suspendRunCatching {
                val category = Category(
                    id = base?.id ?: 0L,
                    name = state.name.trim(),
                    type = state.type,
                    color = state.color,
                    icon = state.icon,
                    sortOrder = base?.sortOrder ?: categoryRepository.nextSortOrder(),
                    isDefault = base?.isDefault ?: false,
                )
                categoryRepository.upsert(category)
            }
            isSaving = false
            _events.send(
                if (result.isSuccess) CategoryEditorEvent.Saved else CategoryEditorEvent.WriteFailed,
            )
        }
    }

    /** Opens the appropriate deletion flow for the category being edited. */
    fun requestDelete() {
        val category = existing ?: return
        viewModelScope.launch {
            val count = transactionRepository.countForCategory(category.id)
            // The category's budget goes away with it (CASCADE): say so upfront.
            val hasBudget = budgetRepository.getBudgets().any { it.categoryId == category.id }
            val dialog = when {
                count == 0 -> CategoryDeleteDialog.Confirm
                else -> {
                    val candidates = reassignCandidates(category)
                    if (candidates.isEmpty()) {
                        CategoryDeleteDialog.ConfirmUncategorize(count)
                    } else {
                        CategoryDeleteDialog.Reassign(
                            movementCount = count,
                            candidates = candidates,
                            selectedTargetId = defaultTarget(candidates).id,
                        )
                    }
                }
            }
            _uiState.update { it.copy(deleteDialog = dialog, deleteAlsoRemovesBudget = hasBudget) }
        }
    }

    fun onReassignTargetSelected(targetId: Long) {
        _uiState.update { state ->
            val dialog = state.deleteDialog as? CategoryDeleteDialog.Reassign ?: return@update state
            state.copy(deleteDialog = dialog.copy(selectedTargetId = targetId))
        }
    }

    fun confirmDelete() {
        val category = existing ?: return
        val dialog = _uiState.value.deleteDialog ?: return
        _uiState.update { it.copy(deleteDialog = null) }
        viewModelScope.launch {
            val result = suspendRunCatching {
                when (dialog) {
                    is CategoryDeleteDialog.Reassign ->
                        categoryRepository.deleteWithReassignment(category, dialog.selectedTargetId)

                    is CategoryDeleteDialog.Confirm,
                    is CategoryDeleteDialog.ConfirmUncategorize,
                    -> categoryRepository.delete(category)
                }
            }
            _events.send(
                if (result.isSuccess) CategoryEditorEvent.Deleted else CategoryEditorEvent.WriteFailed,
            )
        }
    }

    fun dismissDeleteDialog() {
        _uiState.update { it.copy(deleteDialog = null) }
    }

    /** Categories that can absorb [category]'s movements (compatible type, not itself). */
    private suspend fun reassignCandidates(category: Category): List<Category> =
        categoryRepository.observeCategories().first().filter { candidate ->
            candidate.id != category.id && when (category.type) {
                CategoryType.EXPENSE -> candidate.type.usableForExpenses
                CategoryType.INCOME -> candidate.type.usableForIncomes
                CategoryType.BOTH -> true
            }
        }

    /** Prefers the seeded "Other" bucket, otherwise the first candidate. */
    private fun defaultTarget(candidates: List<Category>): Category =
        candidates.firstOrNull { it.isDefault && it.icon == CategoryVisuals.defaultIconKey }
            ?: candidates.first()

    private fun CategoryEditorRoute.initialType(): CategoryType =
        initialTypeName
            ?.let { runCatching { CategoryType.valueOf(it) }.getOrNull() }
            ?: CategoryType.EXPENSE
}
