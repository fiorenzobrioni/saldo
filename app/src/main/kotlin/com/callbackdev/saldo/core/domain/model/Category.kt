package com.callbackdev.saldo.core.domain.model

/**
 * A label for expenses or incomes. The default set is seeded on first launch
 * (localized) and is fully editable by the user.
 *
 * @property color RGB colour (0xRRGGBB) from the shared palette; the UI applies opacity.
 * @property icon stable key of a Material Symbols icon.
 * @property sortOrder manual position within the expense tab (EXPENSE and BOTH).
 * @property sortOrderIncome manual position within the income tab (INCOME and
 *   BOTH). Kept separate from [sortOrder] so reordering one tab never disturbs
 *   the relative order of BOTH categories in the other.
 */
data class Category(
    val name: String,
    val type: CategoryType,
    val color: Int,
    val icon: String,
    val id: Long = 0L,
    val sortOrder: Int = 0,
    val sortOrderIncome: Int = 0,
    val isDefault: Boolean = false,
)
