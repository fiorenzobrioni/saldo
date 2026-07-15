package com.callbackdev.saldo.feature.categories

import com.callbackdev.saldo.core.domain.model.CategoryType

/** True when a category of this type can label an expense (EXPENSE or BOTH). */
internal val CategoryType.usableForExpenses: Boolean
    get() = this == CategoryType.EXPENSE || this == CategoryType.BOTH

/** True when a category of this type can label an income (INCOME or BOTH). */
internal val CategoryType.usableForIncomes: Boolean
    get() = this == CategoryType.INCOME || this == CategoryType.BOTH
