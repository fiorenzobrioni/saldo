package com.callbackdev.saldo.screenshots

import android.content.Context
import androidx.annotation.StringRes
import com.callbackdev.saldo.R
import com.callbackdev.saldo.core.designsystem.visuals.AccountVisuals
import com.callbackdev.saldo.core.domain.model.Account
import com.callbackdev.saldo.core.domain.model.AccountType
import com.callbackdev.saldo.core.domain.model.Category
import com.callbackdev.saldo.core.domain.model.CreditCardConfig
import com.callbackdev.saldo.core.domain.model.RecurrenceFrequency
import com.callbackdev.saldo.core.domain.model.RecurringRule
import com.callbackdev.saldo.core.domain.model.SavingsGoal
import com.callbackdev.saldo.core.domain.model.Transaction
import com.callbackdev.saldo.core.domain.model.TransactionType
import com.callbackdev.saldo.core.domain.repository.AccountRepository
import com.callbackdev.saldo.core.domain.repository.BudgetRepository
import com.callbackdev.saldo.core.domain.rates.ExchangeRate
import com.callbackdev.saldo.core.domain.repository.CategoryRepository
import com.callbackdev.saldo.core.domain.repository.ExchangeRateRepository
import com.callbackdev.saldo.core.domain.repository.RecurringRuleRepository
import com.callbackdev.saldo.core.domain.repository.SavingsGoalRepository
import com.callbackdev.saldo.core.domain.repository.TransactionRepository
import com.callbackdev.saldo.core.domain.usecase.GenerateRecurringMovementsUseCase
import com.callbackdev.saldo.core.domain.usecase.ProcessDueCreditCardStatementsUseCase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.math.BigDecimal
import java.math.MathContext
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.Currency
import java.util.Random
import javax.inject.Inject

/**
 * The README screenshots' sample ledger: one plausible person in Milan, from
 * July to Thursday 24 September 2026, 18:40. Five accounts (one in pounds), a
 * salary, rent, bills and subscriptions, three months of everyday spending
 * (August has the summer holiday), a budget, a savings goal, a friend who owes
 * a dinner, and the ECB rates of the last quarter.
 *
 * Written through the app's own repositories, and the recurring movements and
 * the credit card payments are produced by the app's own use cases, so every
 * figure on screen comes from the same code as on a phone. Small everyday
 * expenses are drawn from a seeded [Random]: the same ledger on every run.
 */
class SampleLedger @Inject constructor(
    @ApplicationContext private val context: Context,
    private val accounts: AccountRepository,
    private val categories: CategoryRepository,
    private val transactions: TransactionRepository,
    private val rules: RecurringRuleRepository,
    private val budgets: BudgetRepository,
    private val goals: SavingsGoalRepository,
    private val exchangeRates: ExchangeRateRepository,
    private val generateRecurring: GenerateRecurringMovementsUseCase,
    private val processStatements: ProcessDueCreditCardStatementsUseCase,
) {
    private val eur = Currency.getInstance("EUR")
    private val gbp = Currency.getInstance("GBP")
    private lateinit var seededCategories: Map<String, Category>

    private var checking = 0L
    private var travelFund = 0L
    private var card = 0L
    private var cash = 0L

    suspend fun seed() {
        seededCategories = categories.observeCategories().first().associateBy { it.name }
        seedAccounts()
        seedRules()
        seedEverydaySpending()
        seedOneOffs()
        seedToday()
        // What the app does at every start: catch up the recurring movements and
        // settle the credit card statements that came due (auto-post card).
        generateRecurring(TODAY)
        processStatements(TODAY)
        seedPlans()
        seedExchangeRates()
    }

    private suspend fun seedAccounts() {
        checking = account("Current account", AccountType.CHECKING, "3850.00")
        travelFund = account("Travel fund", AccountType.SAVINGS, "900.00")
        card = account(
            "Credit card", AccountType.CREDIT_CARD, "0.00",
            CreditCardConfig(
                statementClosingDay = 25,
                paymentDueDay = 10,
                linkedAccountId = checking,
                creditLimit = money("2500.00"),
                autoPost = true,
                lastSettledClosing = LocalDate.of(2026, 6, 25),
            ),
        )
        cash = account("Cash", AccountType.CASH, "85.00")
        // Left over from a year in London: the one foreign balance, counted in
        // the total at the ECB rate (ADR 40).
        account("UK account", AccountType.CHECKING, "640.00", currency = gbp)
    }

    /** An account as the editor creates it, with its type's preset icon and color. */
    private suspend fun account(
        name: String,
        type: AccountType,
        initialBalance: String,
        creditCard: CreditCardConfig? = null,
        currency: Currency = eur,
    ): Long = accounts.upsert(
        Account(
            name = name,
            type = type,
            currency = currency,
            initialBalance = money(initialBalance),
            color = AccountVisuals.defaultColorFor(type),
            icon = AccountVisuals.defaultIconFor(type),
            sortOrder = accounts.nextSortOrder(type),
            createdAt = at(LocalDate.of(2026, 6, 20), 9, 0).toInstant(),
            creditCard = creditCard,
        ),
    )

    private suspend fun seedRules() {
        rule("Rent", R.string.seed_category_rent_mortgage, "720.00", day = 1, account = checking)
        rule(
            "Salary", R.string.seed_category_salary, "2450.00", day = 27, account = checking,
            type = TransactionType.INCOME,
        )
        rule("Netflix", R.string.seed_category_subscriptions, "13.99", day = 3, account = card)
        rule("Gym", R.string.seed_category_health, "45.00", day = 5, account = card)
        rule("Spotify", R.string.seed_category_subscriptions, "11.99", day = 8, account = card)
        rule("Fibre broadband", R.string.seed_category_bills_utilities, "27.90", day = 15, account = checking)
        rule(
            "Electricity and gas", R.string.seed_category_bills_utilities, "94.20", day = 20, account = checking,
            frequency = RecurrenceFrequency.BIMONTHLY,
        )
        rule(
            "Car insurance", R.string.seed_category_car_fuel, "486.00", day = 12, account = checking,
            frequency = RecurrenceFrequency.ANNUAL, start = LocalDate.of(2026, 10, 12),
        )
        rules.upsert(
            RecurringRule(
                name = "Travel fund",
                type = TransactionType.TRANSFER,
                currency = eur,
                accountId = checking,
                frequency = RecurrenceFrequency.MONTHLY,
                startDate = LocalDate.of(2026, 7, 28),
                amount = money("200.00"),
                dayOfReference = 28,
                transferAccountId = travelFund,
                transferAmount = money("200.00"),
                transferCurrency = eur,
                color = AccountVisuals.defaultColorFor(AccountType.SAVINGS),
                icon = AccountVisuals.defaultIconFor(AccountType.SAVINGS),
            ),
        )
    }

    /** Groceries twice a week, coffee at the bar, a dinner out most weeks, fuel twice a month. */
    private suspend fun seedEverydaySpending() {
        val random = Random(SEED)
        val shops = listOf("Esselunga", "Coop", "Lidl", "Local market")
        val dinners = listOf("Pizzeria Da Gino", "Sushi Zen", "Trattoria del Ponte", "Drinks")
        var day = LocalDate.of(2026, 7, 1)
        while (day < TODAY) {
            val holiday = day in HOLIDAY
            if (!holiday && day.dayOfWeek.value in setOf(2, 6)) {
                expense(
                    shops[random.nextInt(shops.size)], R.string.seed_category_groceries,
                    cents(random, 2_200, 7_400), day, 18, account = if (random.nextBoolean()) card else checking,
                )
            }
            if (!holiday && day.dayOfWeek.value <= 5 && random.nextInt(10) < 3) {
                expense("Coffee", R.string.seed_category_dining, cents(random, 130, 450), day, 8, account = cash)
            }
            if (!holiday && day.dayOfWeek.value == 5 && random.nextInt(10) < 7) {
                expense(
                    dinners[random.nextInt(dinners.size)], R.string.seed_category_dining,
                    cents(random, 1_800, 4_800), day, 21, account = card,
                )
            }
            if (!holiday && day.dayOfMonth in setOf(4, 18)) {
                expense("Fuel", R.string.seed_category_car_fuel, cents(random, 5_500, 7_000), day, 12, checking)
            }
            day = day.plusDays(1)
        }
    }

    private suspend fun seedOneOffs() {
        // Cash withdrawals: transfers, never spending.
        listOf(LocalDate.of(2026, 7, 6), LocalDate.of(2026, 8, 3), LocalDate.of(2026, 9, 7)).forEach {
            transfer("Cash withdrawal", "100.00", it, from = checking, to = cash)
        }
        expense("Amazon", R.string.seed_category_shopping, "34.90", LocalDate.of(2026, 7, 12), 20, card)
        expense("Train Milan-Bologna", R.string.seed_category_travel, "39.90", LocalDate.of(2026, 7, 17), 7, card)
        expense("Pharmacy", R.string.seed_category_health, "18.40", LocalDate.of(2026, 7, 22), 17, cash)
        expense("Concert", R.string.seed_category_entertainment, "55.00", LocalDate.of(2026, 7, 25), 22, card)
        // The summer holiday in Puglia.
        expense("Ferry and tolls", R.string.seed_category_travel, "86.50", LocalDate.of(2026, 8, 8), 9, card)
        expense("Masseria Le Pietre", R.string.seed_category_travel, "640.00", LocalDate.of(2026, 8, 8), 15, card)
        expense("Seaside restaurant", R.string.seed_category_dining, "72.00", LocalDate.of(2026, 8, 10), 21, card)
        expense("Beach club", R.string.seed_category_entertainment, "30.00", LocalDate.of(2026, 8, 11), 10, cash)
        expense("Holiday groceries", R.string.seed_category_groceries, "48.70", LocalDate.of(2026, 8, 12), 18, card)
        expense("Gift for Anna", R.string.seed_category_gifts_given, "40.00", LocalDate.of(2026, 8, 20), 18, card)
        expense("Zara", R.string.seed_category_shopping, "45.95", LocalDate.of(2026, 8, 29), 16, card)
        // September.
        expense("Decathlon", R.string.seed_category_shopping, "59.99", LocalDate.of(2026, 9, 6), 11, card)
        income("Website project", R.string.seed_category_freelance, "420.00", LocalDate.of(2026, 9, 11), 10, checking)
        expense("Cinema", R.string.seed_category_entertainment, "17.00", LocalDate.of(2026, 9, 13), 21, card)
        expense("Pharmacy", R.string.seed_category_health, "12.90", LocalDate.of(2026, 9, 17), 18, cash)
        // Paid for Luca's share of a dinner: a loan, out of the statistics (ADR 34).
        transactions.upsert(
            Transaction(
                type = TransactionType.EXPENSE,
                amount = money("-60.00"),
                currency = eur,
                accountId = card,
                timestamp = at(LocalDate.of(2026, 9, 19), 22, 30).toInstant(),
                zoneOffset = at(LocalDate.of(2026, 9, 19), 22, 30).offset,
                categoryId = category(R.string.seed_category_dining),
                description = "Birthday dinner",
                isExcludedFromStats = true,
                counterparty = "Luca",
            ),
        )
    }

    private suspend fun seedToday() {
        expense("Coffee", R.string.seed_category_dining, "1.80", TODAY, 8, cash, minute = 10)
        expense("Lunch", R.string.seed_category_dining, "11.50", TODAY, 13, checking, minute = 5)
        expense("Esselunga", R.string.seed_category_groceries, "46.35", TODAY, 18, card, minute = 5)
    }

    private suspend fun seedPlans() {
        budgets.setOverallBudget(money("2000.00"), eur)
        budgets.upsertCategoryBudget(category(R.string.seed_category_groceries), money("500.00"), eur)
        budgets.upsertCategoryBudget(category(R.string.seed_category_dining), money("180.00"), eur)
        budgets.upsertCategoryBudget(category(R.string.seed_category_shopping), money("150.00"), eur)
        goals.upsert(
            SavingsGoal(
                name = "Trip to Japan",
                targetAmount = money("3500.00"),
                currency = eur,
                accountId = travelFund,
                targetDate = LocalDate.of(2027, 4, 30),
            ),
        )
    }

    /**
     * The ECB reference rates the app would have cached by now: every working
     * day of the last quarter, each currency drifting from about its level of
     * the time (sample values, not historical ones) by a seeded random walk.
     */
    private suspend fun seedExchangeRates() {
        val random = Random(SEED)
        val levels = EcbLevels.mapValues { (_, level) -> BigDecimal(level) }.toMutableMap()
        val rates = mutableListOf<ExchangeRate>()
        var day = TODAY.minusDays(RATE_HISTORY_DAYS)
        while (day <= TODAY) {
            if (day.dayOfWeek.value <= 5) {
                levels.replaceAll { _, level ->
                    // At most 0.4% a day either way, rounded to the ECB's five digits.
                    val drift = BigDecimal.valueOf(random.nextInt(81) - 40L, 4)
                    level.multiply(BigDecimal.ONE + drift).round(MathContext(5))
                }
                levels.forEach { (code, level) -> rates += ExchangeRate(code, day, level) }
            }
            day = day.plusDays(1)
        }
        exchangeRates.store(rates)
    }

    private suspend fun rule(
        name: String,
        @StringRes categoryName: Int,
        amount: String,
        day: Int,
        account: Long,
        type: TransactionType = TransactionType.EXPENSE,
        frequency: RecurrenceFrequency = RecurrenceFrequency.MONTHLY,
        start: LocalDate = firstOccurrence(day),
    ) {
        // The avatar a user would pick in the editor: the category's own.
        val category = seeded(categoryName)
        rules.upsert(
            RecurringRule(
                name = name,
                type = type,
                currency = eur,
                accountId = account,
                frequency = frequency,
                startDate = start,
                amount = money(amount),
                categoryId = category.id,
                dayOfReference = day,
                color = category.color,
                icon = category.icon,
            ),
        )
    }

    private suspend fun expense(
        description: String,
        @StringRes categoryName: Int,
        amount: String,
        date: LocalDate,
        hour: Int,
        account: Long,
        minute: Int = 0,
    ) = movement(TransactionType.EXPENSE, description, categoryName, money(amount).negate(), date, hour, minute, account)

    private suspend fun income(
        description: String,
        @StringRes categoryName: Int,
        amount: String,
        date: LocalDate,
        hour: Int,
        account: Long,
    ) = movement(TransactionType.INCOME, description, categoryName, money(amount), date, hour, 0, account)

    @Suppress("LongParameterList")
    private suspend fun movement(
        type: TransactionType,
        description: String,
        @StringRes categoryName: Int,
        amount: BigDecimal,
        date: LocalDate,
        hour: Int,
        minute: Int,
        account: Long,
    ) {
        val time = at(date, hour, minute)
        transactions.upsert(
            Transaction(
                type = type,
                amount = amount,
                currency = eur,
                accountId = account,
                timestamp = time.toInstant(),
                zoneOffset = time.offset,
                categoryId = category(categoryName),
                description = description,
            ),
        )
    }

    private suspend fun transfer(description: String, amount: String, date: LocalDate, from: Long, to: Long) {
        val time = at(date, 12, 0)
        transactions.upsert(
            Transaction(
                type = TransactionType.TRANSFER,
                amount = money(amount).negate(),
                currency = eur,
                accountId = from,
                timestamp = time.toInstant(),
                zoneOffset = time.offset,
                transferAccountId = to,
                transferAmount = money(amount),
                transferCurrency = eur,
                description = description,
            ),
        )
    }

    private fun category(@StringRes name: Int): Long = seeded(name).id

    private fun seeded(@StringRes name: Int): Category =
        checkNotNull(seededCategories[context.getString(name)]) { "No seeded category ${context.getString(name)}" }

    private fun money(amount: String): BigDecimal = BigDecimal(amount).setScale(2)

    /** A random amount between [minCents] and [maxCents], as euros. */
    private fun cents(random: Random, minCents: Int, maxCents: Int): String =
        BigDecimal.valueOf((minCents + random.nextInt(maxCents - minCents + 1)).toLong(), 2).toPlainString()

    private fun firstOccurrence(day: Int): LocalDate = LocalDate.of(2026, 7, day)

    private fun at(date: LocalDate, hour: Int, minute: Int) = LocalDateTime.of(date, LocalTime.of(hour, minute)).atZone(ZONE)

    companion object {
        val ZONE: ZoneId = ZoneId.of("Europe/Rome")
        val TODAY: LocalDate = LocalDate.of(2026, 9, 24)
        val clock: Clock = Clock.fixed(LocalDateTime.of(TODAY, LocalTime.of(18, 40)).atZone(ZONE).toInstant(), ZONE)

        private const val SEED = 24_09_2026L
        private const val RATE_HISTORY_DAYS = 92L

        /** Euro reference rates, roughly at their late-2026 levels: one euro buys this much. */
        private val EcbLevels = mapOf(
            "USD" to "1.1720", "JPY" to "173.10", "CZK" to "24.350", "DKK" to "7.4640",
            "GBP" to "0.86900", "HUF" to "391.20", "PLN" to "4.2610", "RON" to "5.0780",
            "SEK" to "10.980", "CHF" to "0.93500", "ISK" to "143.90", "NOK" to "11.680",
            "TRY" to "48.600", "AUD" to "1.7760", "BRL" to "6.2800", "CAD" to "1.6230",
            "CNY" to "8.3550", "HKD" to "9.1150", "IDR" to "19350", "ILS" to "3.9400",
            "INR" to "103.20", "KRW" to "1628.0", "MXN" to "21.700", "MYR" to "4.9500",
            "NZD" to "1.9950", "PHP" to "66.800", "SGD" to "1.5050", "THB" to "37.600",
            "ZAR" to "20.450",
        )
        private val HOLIDAY = LocalDate.of(2026, 8, 8)..LocalDate.of(2026, 8, 16)
    }
}
