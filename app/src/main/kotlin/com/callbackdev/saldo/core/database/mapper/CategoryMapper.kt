package com.callbackdev.saldo.core.database.mapper

import com.callbackdev.saldo.core.database.entity.CategoryEntity
import com.callbackdev.saldo.core.domain.model.Category

fun CategoryEntity.toDomain(): Category = Category(
    id = id,
    name = name,
    type = type,
    color = color,
    icon = icon,
    sortOrder = sortOrder,
    sortOrderIncome = sortOrderIncome,
    isDefault = isDefault,
)

fun Category.toEntity(): CategoryEntity = CategoryEntity(
    id = id,
    name = name,
    type = type,
    color = color,
    icon = icon,
    sortOrder = sortOrder,
    sortOrderIncome = sortOrderIncome,
    isDefault = isDefault,
)
