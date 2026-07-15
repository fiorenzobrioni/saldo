package com.callbackdev.saldo.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.callbackdev.saldo.core.domain.model.CategoryType

/**
 * Room row for a category.
 *
 * @property color RGB colour (0xRRGGBB) from the shared palette.
 * @property icon stable Material Symbols key.
 */
@Entity(
    tableName = "categories",
    indices = [Index("type"), Index("sortOrder"), Index("sortOrderIncome")],
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    val type: CategoryType,
    val color: Int,
    val icon: String,
    val sortOrder: Int = 0,
    val sortOrderIncome: Int = 0,
    val isDefault: Boolean = false,
)
