package com.callbackdev.saldo.core.database.seed

import android.content.Context
import androidx.annotation.StringRes
import com.callbackdev.saldo.R
import com.callbackdev.saldo.core.database.entity.CategoryEntity
import com.callbackdev.saldo.core.domain.model.CategoryType

/**
 * The default category set seeded on first launch. Names come from string
 * resources, so the seed is localized to the device language (IT/EN); the user
 * can edit or delete any of them afterwards.
 *
 * Colours are RGB (0xRRGGBB) from a shared palette; icons are Material Symbols keys.
 */
internal object DefaultCategories {

    private data class Seed(
        @param:StringRes val nameRes: Int,
        val icon: String,
        val color: Int,
        val type: CategoryType,
    )

    private val seeds: List<Seed> = listOf(
        // Expenses
        Seed(R.string.seed_category_home, "home", color = 0x5C6BC0, CategoryType.EXPENSE),
        Seed(R.string.seed_category_rent_mortgage, "home_work", color = 0x7E57C2, CategoryType.EXPENSE),
        Seed(R.string.seed_category_groceries, "shopping_cart", color = 0x66BB6A, CategoryType.EXPENSE),
        Seed(R.string.seed_category_dining, "restaurant", color = 0xEF5350, CategoryType.EXPENSE),
        Seed(R.string.seed_category_transport, "commute", color = 0x42A5F5, CategoryType.EXPENSE),
        Seed(R.string.seed_category_car_fuel, "local_gas_station", color = 0x26A69A, CategoryType.EXPENSE),
        Seed(R.string.seed_category_health, "health_and_safety", color = 0xEC407A, CategoryType.EXPENSE),
        Seed(R.string.seed_category_shopping, "shopping_bag", color = 0xFFA726, CategoryType.EXPENSE),
        Seed(R.string.seed_category_travel, "flight", color = 0x29B6F6, CategoryType.EXPENSE),
        Seed(R.string.seed_category_entertainment, "movie", color = 0xAB47BC, CategoryType.EXPENSE),
        Seed(R.string.seed_category_subscriptions, "subscriptions", color = 0x8D6E63, CategoryType.EXPENSE),
        Seed(R.string.seed_category_bills_utilities, "receipt_long", color = 0x78909C, CategoryType.EXPENSE),
        Seed(R.string.seed_category_education, "school", color = 0x9CCC65, CategoryType.EXPENSE),
        Seed(R.string.seed_category_gifts_given, "card_giftcard", color = 0xD4E157, CategoryType.EXPENSE),
        Seed(R.string.seed_category_taxes, "account_balance", color = 0xFF7043, CategoryType.EXPENSE),
        Seed(R.string.seed_category_loans, "request_quote", color = 0x26C6DA, CategoryType.EXPENSE),
        Seed(R.string.seed_category_other_expense, "category", color = 0x90A4AE, CategoryType.EXPENSE),
        // Incomes
        Seed(R.string.seed_category_salary, "payments", color = 0x66BB6A, CategoryType.INCOME),
        Seed(R.string.seed_category_freelance, "work", color = 0x26A69A, CategoryType.INCOME),
        Seed(R.string.seed_category_gifts_received, "redeem", color = 0xF06292, CategoryType.INCOME),
        Seed(R.string.seed_category_refunds, "currency_exchange", color = 0x26C6DA, CategoryType.INCOME),
        Seed(R.string.seed_category_other_income, "category", color = 0x90A4AE, CategoryType.INCOME),
    )

    /** Number of default categories, exposed for tests. */
    val count: Int get() = seeds.size

    /**
     * Builds the localized default category rows. Both sort keys follow list
     * order; the income tab filters to income-usable rows and reads
     * [CategoryEntity.sortOrderIncome], so mirroring the index preserves the
     * seeded order in each tab.
     */
    fun build(context: Context): List<CategoryEntity> = seeds.mapIndexed { index, seed ->
        CategoryEntity(
            name = context.getString(seed.nameRes),
            type = seed.type,
            color = seed.color,
            icon = seed.icon,
            sortOrder = index,
            sortOrderIncome = index,
            isDefault = true,
        )
    }
}
