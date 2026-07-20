package com.callbackdev.saldo.feature.transactions.importer

import android.content.Context
import android.net.Uri
import com.callbackdev.saldo.R
import com.callbackdev.saldo.core.common.di.IoDispatcher
import com.callbackdev.saldo.core.common.prefs.UserPreferencesRepository
import com.callbackdev.saldo.core.domain.model.Account
import com.callbackdev.saldo.core.domain.model.AccountType
import com.callbackdev.saldo.core.domain.model.Category
import com.callbackdev.saldo.core.domain.model.Tag
import com.callbackdev.saldo.core.domain.model.Transaction
import com.callbackdev.saldo.core.domain.model.TransactionType
import com.callbackdev.saldo.core.domain.repository.AccountRepository
import com.callbackdev.saldo.core.domain.repository.CategoryRepository
import com.callbackdev.saldo.core.domain.repository.TagRepository
import com.callbackdev.saldo.core.domain.repository.TransactionRepository
import com.callbackdev.saldo.core.domain.repository.TransactionRunner
import com.callbackdev.saldo.feature.transactions.localDate
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.IOException
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.Currency
import java.util.Locale
import javax.inject.Inject

/**
 * Reads a CSV file the user picked and turns it into ledger movements, in two
 * phases mirroring the guided restore: [read] then [analyze] produce a dry-run
 * the screen previews without touching anything, and [commit] writes the
 * previewed result inside a single database transaction. The import only ever
 * inserts: it never edits or deletes an existing movement, and duplicates are
 * skipped rather than overwriting their match.
 */
@Suppress("LongParameterList") // Hilt wiring: one dependency per concern.
class TransactionsCsvImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val tagRepository: TagRepository,
    private val transactionRepository: TransactionRepository,
    private val transactionRunner: TransactionRunner,
    private val userPreferences: UserPreferencesRepository,
    private val analyzer: TransactionCsvAnalyzer,
    private val clock: Clock,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    /** The rows and column mapping of a readable file, cached between toggles. */
    data class ParsedCsv(
        internal val dataRows: List<List<String>>,
        internal val mapping: ColumnMapping,
    )

    /** Outcome of opening and structurally validating the picked file. */
    sealed interface ReadResult {
        data class Success(val parsed: ParsedCsv) : ReadResult
        data class Failure(val error: CsvImportError) : ReadResult
    }

    /**
     * Opens [uri], detects the separator, parses it and maps its header. Fails
     * (without reading the rest) when the file is empty, has no recognizable
     * header, carries no data rows, or exceeds [MAX_ROWS].
     */
    suspend fun read(uri: Uri): ReadResult = withContext(ioDispatcher) {
        val text = readText(uri)
        if (text.isBlank()) return@withContext ReadResult.Failure(CsvImportError.EMPTY_FILE)
        val separator = CsvSeparatorSniffer.detect(text)
        val records = CsvReader.parse(text, separator)
        if (records.isEmpty()) return@withContext ReadResult.Failure(CsvImportError.EMPTY_FILE)
        val mapping = CsvHeaderMapper.map(records.first(), columnLabels())
            ?: return@withContext ReadResult.Failure(CsvImportError.UNRECOGNIZED_FORMAT)
        val dataRows = records.drop(1)
        val populated = dataRows.count { row -> row.any { it.isNotBlank() } }
        when {
            populated == 0 -> ReadResult.Failure(CsvImportError.NO_DATA_ROWS)
            populated > MAX_ROWS -> ReadResult.Failure(CsvImportError.TOO_MANY_ROWS)
            else -> ReadResult.Success(ParsedCsv(dataRows, mapping))
        }
    }

    /** Runs the pure analysis of [parsed] against the current data and [options]. */
    suspend fun analyze(parsed: ParsedCsv, options: CsvImportOptions): CsvImportAnalysis =
        withContext(ioDispatcher) {
            analyzer.analyze(parsed.dataRows, parsed.mapping, buildContext(), options)
        }

    /**
     * Writes the analyzed result: creates the missing accounts, categories and
     * tags, then inserts the importable movements and attaches their tags, all
     * atomically. Returns the report of what was written.
     */
    suspend fun commit(analysis: CsvImportAnalysis): CsvImportReport = withContext(ioDispatcher) {
        val existingAccounts = accountRepository.observeAccountsWithBalance().first().map { it.account }
        val existingCategories = categoryRepository.observeCategories().first()
        val existingTags = tagRepository.observeTags().first()
        transactionRunner.inTransaction {
            val accountIds = createAccounts(existingAccounts, analysis.newAccounts)
            val categoryIds = createCategories(existingCategories, analysis.newCategories)
            val tagIds = createTags(existingTags, analysis.newTags)
            analysis.importable.forEach { importable ->
                insertMovement(importable.movement, accountIds, categoryIds, tagIds)
            }
        }
        report(analysis)
    }

    private fun report(analysis: CsvImportAnalysis) = CsvImportReport(
        imported = analysis.importableCount,
        duplicatesSkipped = analysis.duplicateCount,
        invalid = analysis.invalidCount,
        adjusted = analysis.adjustedCount,
        createdAccounts = analysis.newAccounts.map { it.name },
        createdCategories = analysis.newCategories.map { it.name },
        createdTags = analysis.newTags,
        totalRows = analysis.totalRows,
    )

    // --- Commit helpers: return name (normalized) -> id maps, existing plus created. ---

    private suspend fun createAccounts(
        existing: List<Account>,
        pending: List<PendingAccount>,
    ): Map<String, Long> {
        val ids = existing.associate { normalizeName(it.name) to it.id }.toMutableMap()
        pending.forEach { account ->
            val id = accountRepository.upsert(
                Account(
                    name = account.name,
                    type = AccountType.OTHER,
                    currency = account.currency,
                    initialBalance = BigDecimal.ZERO,
                    createdAt = clock.instant(),
                ),
            )
            ids[normalizeName(account.name)] = id
        }
        return ids
    }

    private suspend fun createCategories(
        existing: List<Category>,
        pending: List<PendingCategory>,
    ): Map<String, Long> {
        val ids = existing.associate { normalizeName(it.name) to it.id }.toMutableMap()
        pending.forEachIndexed { index, category ->
            val id = categoryRepository.upsert(
                Category(
                    name = category.name,
                    type = category.type,
                    color = CATEGORY_PALETTE[index % CATEGORY_PALETTE.size],
                    icon = IMPORTED_CATEGORY_ICON,
                    sortOrder = categoryRepository.nextSortOrder(category.type),
                    sortOrderIncome = categoryRepository.nextSortOrder(category.type),
                ),
            )
            ids[normalizeName(category.name)] = id
        }
        return ids
    }

    private suspend fun createTags(existing: List<Tag>, pending: List<String>): Map<String, Long> {
        val ids = existing.associate { normalizeName(it.name) to it.id }.toMutableMap()
        pending.forEach { name ->
            ids[normalizeName(name)] = tagRepository.upsert(Tag(name = name))
        }
        return ids
    }

    private suspend fun insertMovement(
        movement: PendingMovement,
        accountIds: Map<String, Long>,
        categoryIds: Map<String, Long>,
        tagIds: Map<String, Long>,
    ) {
        val accountId = accountIds.getValue(normalizeName(movement.accountName))
        val dateTime = LocalDateTime.of(movement.date, LocalTime.NOON)
        val offset = clock.zone.rules.getOffset(dateTime)
        val transaction = Transaction(
            type = movement.type,
            amount = movement.amount,
            currency = movement.currency,
            accountId = accountId,
            timestamp = dateTime.toInstant(offset),
            zoneOffset = offset,
            transferAccountId = movement.toAccountName?.let { accountIds[normalizeName(it)] },
            transferAmount = movement.transferAmount,
            transferCurrency = movement.transferCurrency,
            categoryId = movement.categoryName?.let { categoryIds[normalizeName(it)] },
            description = movement.description,
            note = movement.note,
        )
        val id = transactionRepository.upsert(transaction)
        val tags = movement.tagNames.mapNotNull { tagIds[normalizeName(it)] }
        if (tags.isNotEmpty()) tagRepository.setTagsForTransaction(id, tags)
    }

    // --- Context building ---

    private suspend fun buildContext(): ImportContext {
        val accounts = accountRepository.observeAccountsWithBalance().first().map { it.account }
        val categories = categoryRepository.observeCategories().first()
        val tags = tagRepository.observeTags().first()
        val transactions = transactionRepository.observeTransactions().first()
        val accountsById = accounts.associateBy { it.id }
        val signatures = transactions.mapNotNull { signatureOf(it, accountsById) }.toSet()
        return ImportContext(
            accounts = accounts,
            categories = categories,
            tags = tags,
            existingSignatures = signatures,
            columnLabels = columnLabels(),
            typeLabels = typeLabels(),
            defaultCurrency = defaultCurrency(accounts),
        )
    }

    private fun signatureOf(transaction: Transaction, accountsById: Map<Long, Account>): String? {
        val account = accountsById[transaction.accountId] ?: return null
        return MovementSignature.of(
            date = transaction.localDate,
            type = transaction.type,
            amount = transaction.amount,
            currency = transaction.currency,
            accountName = account.name,
            description = transaction.description,
        )
    }

    private suspend fun defaultCurrency(accounts: List<Account>): Currency {
        userPreferences.primaryCurrencyOverride.first()?.let { return it }
        val active = accounts.filter { !it.isArchived }
        active.groupingBy { it.currency }.eachCount().maxByOrNull { it.value }?.let { return it.key }
        return runCatching { Currency.getInstance(Locale.getDefault()) }
            .getOrElse { Currency.getInstance("EUR") }
    }

    private suspend fun readText(uri: Uri): String {
        val stream = context.contentResolver.openInputStream(uri)
            ?: throw IOException("Cannot open $uri for reading")
        return stream.bufferedReader().use { it.readText() }
    }

    private fun columnLabels(): Map<CsvField, String> = mapOf(
        CsvField.DATE to context.getString(R.string.csv_header_date),
        CsvField.TYPE to context.getString(R.string.csv_header_type),
        CsvField.CATEGORY to context.getString(R.string.csv_header_category),
        CsvField.DESCRIPTION to context.getString(R.string.csv_header_description),
        CsvField.ACCOUNT to context.getString(R.string.csv_header_account),
        CsvField.TO_ACCOUNT to context.getString(R.string.csv_header_to_account),
        CsvField.AMOUNT to context.getString(R.string.csv_header_amount),
        CsvField.CURRENCY to context.getString(R.string.csv_header_currency),
        CsvField.RECEIVED_AMOUNT to context.getString(R.string.csv_header_received_amount),
        CsvField.RECEIVED_CURRENCY to context.getString(R.string.csv_header_received_currency),
        CsvField.TAGS to context.getString(R.string.csv_header_tags),
        CsvField.NOTE to context.getString(R.string.csv_header_note),
    )

    private fun typeLabels(): Map<TransactionType, String> = mapOf(
        TransactionType.EXPENSE to context.getString(R.string.transaction_type_expense),
        TransactionType.INCOME to context.getString(R.string.transaction_type_income),
        TransactionType.TRANSFER to context.getString(R.string.transaction_type_transfer),
        TransactionType.ADJUSTMENT to context.getString(R.string.transaction_type_adjustment),
    )

    private fun normalizeName(text: String): String =
        text.trim().lowercase(Locale.ROOT).replace(WHITESPACE, " ")

    private companion object {
        /** Upper bound on data rows a single import will process. */
        const val MAX_ROWS = 10_000

        /** Neutral Material Symbols icon for categories created on import. */
        const val IMPORTED_CATEGORY_ICON = "category"

        val WHITESPACE = "\\s+".toRegex()

        /** Colours reused from the seed palette for categories created on import. */
        val CATEGORY_PALETTE = listOf(
            0x5C6BC0, 0x66BB6A, 0xEF5350, 0x42A5F5, 0x26A69A,
            0xEC407A, 0xFFA726, 0xAB47BC, 0x8D6E63, 0x78909C,
        )
    }
}
