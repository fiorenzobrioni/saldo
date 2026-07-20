package com.callbackdev.saldo.feature.transactions.importer

import com.callbackdev.saldo.core.common.csv.CsvFormulaGuard
import com.callbackdev.saldo.core.domain.model.Account
import com.callbackdev.saldo.core.domain.model.Category
import com.callbackdev.saldo.core.domain.model.CategoryType
import com.callbackdev.saldo.core.domain.model.Tag
import com.callbackdev.saldo.core.domain.model.TransactionType
import com.callbackdev.saldo.feature.transactions.importer.CsvFieldParsers.parseAmount
import com.callbackdev.saldo.feature.transactions.importer.CsvFieldParsers.parseCurrency
import com.callbackdev.saldo.feature.transactions.importer.CsvFieldParsers.parseDate
import java.math.BigDecimal
import java.text.Normalizer
import java.time.LocalDate
import java.util.Currency
import java.util.Locale
import javax.inject.Inject

/**
 * Everything the analyzer needs to resolve a row against the existing data,
 * gathered by the importer from the repositories. [existingSignatures] keys the
 * current ledger for duplicate detection (built with [MovementSignature]);
 * [typeLabels] and [columnLabels] carry the app's localized names so a file
 * exported in the user's language is recognized without relying on the aliases.
 */
data class ImportContext(
    val accounts: List<Account>,
    val categories: List<Category>,
    val tags: List<Tag>,
    val existingSignatures: Set<String>,
    val columnLabels: Map<CsvField, String>,
    val typeLabels: Map<TransactionType, String>,
    val defaultCurrency: Currency,
)

/**
 * Turns parsed CSV rows into an outcome per row: a ready-to-write movement, a
 * duplicate to skip, or an error with its reasons. It resolves accounts,
 * categories and tags by name against the existing data (scheduling the missing
 * ones for creation when allowed), normalizes amount signs to their movement
 * type, infers a missing type from the sign, and detects duplicates both
 * against the ledger and within the file. It is pure: no I/O, no Android.
 */
class TransactionCsvAnalyzer @Inject constructor() {

    /** Largest magnitude accepted for an amount; guards the minor-unit conversion. */
    private val maxAmount = BigDecimal("1000000000000")

    fun analyze(
        dataRows: List<List<String>>,
        mapping: ColumnMapping,
        context: ImportContext,
        options: CsvImportOptions,
    ): CsvImportAnalysis {
        val run = Run(mapping, context, options)
        var rowNumber = 0
        for (row in dataRows) {
            if (row.all { it.isBlank() }) continue
            rowNumber++
            run.consume(rowNumber, row)
        }
        return run.build()
    }

    /** A destination for a movement leg, resolved or scheduled for creation. */
    private data class ResolvedAccount(val name: String, val currency: Currency, val isNew: Boolean)

    /** The transfer-only legs of a movement. */
    private data class TransferLegs(
        val toAccount: ResolvedAccount,
        val transferAmount: BigDecimal,
        val transferCurrency: Currency,
    )

    /** What a successfully parsed row would create, applied only if it is kept. */
    private data class Creations(
        val accounts: List<ResolvedAccount>,
        val category: PendingCategory?,
        val newTags: List<String>,
    )

    private data class Candidate(
        val movement: PendingMovement,
        val adjustments: List<RowAdjustmentCode>,
        val creations: Creations,
    )

    /** The resolved skeleton of a row, before categories, tags and notes. */
    private class RowShape(
        val type: TransactionType,
        val inferred: Boolean,
        val date: LocalDate,
        val amount: BigDecimal,
        val source: ResolvedAccount,
        val transfer: TransferLegs?,
    )

    /** Category name plus its pending creation, or null when there is none. */
    private data class CategoryResolution(val name: String, val pending: PendingCategory?)

    /** Kept tag names (existing plus newly created) and which of them are new. */
    private data class TagResolution(val kept: List<String>, val newNames: List<String>)

    /** Mutable state of a single [analyze] pass. */
    private inner class Run(
        private val mapping: ColumnMapping,
        private val context: ImportContext,
        private val options: CsvImportOptions,
    ) {
        private val accountsByName = context.accounts.associateBy { normalizeName(it.name) }
        private val categoriesByName = context.categories.associateBy { normalizeName(it.name) }
        private val tagsByName = context.tags.associateBy { normalizeName(it.name) }
        private val typeByAlias = buildTypeAliases(context.typeLabels)

        private val seenSignatures = mutableSetOf<String>()
        private val outcomes = mutableListOf<RowOutcome>()
        private val newAccounts = linkedMapOf<String, PendingAccount>()
        private val newCategories = linkedMapOf<String, PendingCategory>()
        private val newTags = linkedMapOf<String, String>()

        fun consume(rowNumber: Int, row: List<String>) {
            val errors = mutableListOf<RowErrorCode>()
            val candidate = parseRow(row, errors)
            if (candidate == null || errors.isNotEmpty()) {
                outcomes += RowOutcome.Invalid(rowNumber, errors.distinct())
                return
            }
            outcomes += classify(rowNumber, candidate)
        }

        /** Applies duplicate detection and, when kept, records the row's creations. */
        private fun classify(rowNumber: Int, candidate: Candidate): RowOutcome {
            val signature = candidate.movement.signature
            if (options.skipDuplicates) {
                if (signature in context.existingSignatures) {
                    return RowOutcome.Duplicate(rowNumber, DuplicateReason.ALREADY_IN_LEDGER)
                }
                if (signature in seenSignatures) {
                    return RowOutcome.Duplicate(rowNumber, DuplicateReason.DUPLICATE_IN_FILE)
                }
            }
            seenSignatures += signature
            register(candidate.creations)
            return RowOutcome.Importable(rowNumber, candidate.movement, candidate.adjustments)
        }

        private fun register(creations: Creations) {
            creations.accounts.filter { it.isNew }.forEach { account ->
                newAccounts.putIfAbsent(normalizeName(account.name), PendingAccount(account.name, account.currency))
            }
            creations.category?.let { newCategories.putIfAbsent(normalizeName(it.name), it) }
            creations.newTags.forEach { name -> newTags.putIfAbsent(normalizeName(name), name) }
        }

        private fun parseRow(row: List<String>, errors: MutableList<RowErrorCode>): Candidate? {
            val adjustments = mutableListOf<RowAdjustmentCode>()
            val amount = amountField(row, errors)
            val date = dateField(row, errors)
            if (amount == null || date == null) return null
            val rowCurrency = currencyField(row, errors)
            val (type, inferred) = resolveType(row, amount, adjustments)
            val source = resolveAccount(accountField(row), rowCurrency, errors, adjustments) ?: return null
            val transfer = if (type == TransactionType.TRANSFER) {
                resolveTransfer(row, source.currency, amount, errors, adjustments) ?: return null
            } else {
                null
            }
            if (errors.isNotEmpty()) return null
            return buildCandidate(row, RowShape(type, inferred, date, amount, source, transfer), adjustments)
        }

        private fun buildCandidate(
            row: List<String>,
            shape: RowShape,
            adjustments: MutableList<RowAdjustmentCode>,
        ): Candidate {
            val signed = signedAmount(shape.type, shape.amount)
            if (!shape.inferred && signFlipped(shape.type, shape.amount)) {
                adjustments += RowAdjustmentCode.SIGN_NORMALIZED
            }
            val category = resolveCategory(row, shape.type, adjustments)
            val tags = resolveTags(row, adjustments)
            val description = textValue(row, CsvField.DESCRIPTION)
            val movement = PendingMovement(
                type = shape.type,
                date = shape.date,
                amount = signed,
                currency = shape.source.currency,
                accountName = shape.source.name,
                toAccountName = shape.transfer?.toAccount?.name,
                transferAmount = shape.transfer?.transferAmount,
                transferCurrency = shape.transfer?.transferCurrency,
                categoryName = category?.name,
                description = description,
                note = textValue(row, CsvField.NOTE),
                tagNames = tags.kept,
                signature = MovementSignature.of(
                    shape.date, shape.type, signed, shape.source.currency, shape.source.name, description,
                ),
            )
            val accounts = listOfNotNull(shape.source, shape.transfer?.toAccount)
            return Candidate(movement, adjustments.distinct(), Creations(accounts, category?.pending, tags.newNames))
        }

        private fun amountField(row: List<String>, errors: MutableList<RowErrorCode>): BigDecimal? {
            val raw = mapping.rawValue(row, CsvField.AMOUNT)?.trim().orEmpty()
            if (raw.isEmpty()) {
                errors += RowErrorCode.MISSING_AMOUNT
                return null
            }
            val amount = parseAmount(raw)
            if (amount == null) {
                errors += RowErrorCode.INVALID_AMOUNT
                return null
            }
            if (amount.abs() >= maxAmount) {
                errors += RowErrorCode.AMOUNT_OUT_OF_RANGE
                return null
            }
            return amount
        }

        private fun dateField(row: List<String>, errors: MutableList<RowErrorCode>): LocalDate? {
            val raw = mapping.rawValue(row, CsvField.DATE)?.trim().orEmpty()
            if (raw.isEmpty()) {
                errors += RowErrorCode.MISSING_DATE
                return null
            }
            val date = parseDate(raw)
            if (date == null) errors += RowErrorCode.INVALID_DATE
            return date
        }

        /** Row currency, or null when absent; a present-but-unknown code is an error. */
        private fun currencyField(row: List<String>, errors: MutableList<RowErrorCode>): Currency? {
            val raw = mapping.rawValue(row, CsvField.CURRENCY)?.trim().orEmpty()
            if (raw.isEmpty()) return null
            val currency = parseCurrency(raw)
            if (currency == null) errors += RowErrorCode.INVALID_CURRENCY
            return currency
        }

        private fun accountField(row: List<String>): String = textValue(row, CsvField.ACCOUNT).orEmpty()

        /** A text field trimmed and un-guarded (formula prefix removed), null when blank. */
        private fun textValue(row: List<String>, field: CsvField): String? =
            mapping.rawValue(row, field)?.trim()?.ifEmpty { null }?.let(CsvFormulaGuard::strip)

        private fun resolveType(
            row: List<String>,
            amount: BigDecimal,
            adjustments: MutableList<RowAdjustmentCode>,
        ): Pair<TransactionType, Boolean> {
            val raw = mapping.rawValue(row, CsvField.TYPE)?.trim().orEmpty()
            val explicit = if (raw.isEmpty()) null else typeByAlias[normalizeToken(raw)]
            if (explicit != null) return explicit to false
            adjustments += RowAdjustmentCode.TYPE_INFERRED
            val inferredType = if (amount.signum() > 0) TransactionType.INCOME else TransactionType.EXPENSE
            return inferredType to true
        }

        @Suppress("LongParameterList") // Resolution needs the row, the intended currency and both sinks.
        private fun resolveAccount(
            rawName: String,
            rowCurrency: Currency?,
            errors: MutableList<RowErrorCode>,
            adjustments: MutableList<RowAdjustmentCode>,
        ): ResolvedAccount? {
            if (rawName.isEmpty()) {
                errors += RowErrorCode.MISSING_ACCOUNT
                return null
            }
            val existing = accountsByName[normalizeName(rawName)]
            if (existing != null) {
                if (rowCurrency != null && rowCurrency != existing.currency) {
                    errors += RowErrorCode.ACCOUNT_CURRENCY_MISMATCH
                    return null
                }
                return ResolvedAccount(existing.name, existing.currency, isNew = false)
            }
            if (!options.createMissingAccounts) {
                errors += RowErrorCode.UNKNOWN_ACCOUNT
                return null
            }
            if (rowCurrency == null) adjustments += RowAdjustmentCode.CURRENCY_DEFAULTED
            return ResolvedAccount(rawName, rowCurrency ?: context.defaultCurrency, isNew = true)
        }

        @Suppress("LongParameterList") // The transfer leg depends on the source currency and both sinks.
        private fun resolveTransfer(
            row: List<String>,
            sourceCurrency: Currency,
            amount: BigDecimal,
            errors: MutableList<RowErrorCode>,
            adjustments: MutableList<RowAdjustmentCode>,
        ): TransferLegs? {
            val toName = textValue(row, CsvField.TO_ACCOUNT).orEmpty()
            if (toName.isEmpty()) {
                errors += RowErrorCode.INCOMPLETE_TRANSFER
                return null
            }
            val receivedCurrency = mapping.rawValue(row, CsvField.RECEIVED_CURRENCY)?.trim()
                ?.takeIf { it.isNotEmpty() }?.let(::parseCurrency)
            val toAccount = resolveAccount(toName, receivedCurrency, errors, adjustments) ?: return null
            val transferCurrency = receivedCurrency ?: toAccount.currency
            val received = mapping.rawValue(row, CsvField.RECEIVED_AMOUNT)?.trim()?.let(::parseAmount)
            val transferAmount = when {
                received != null -> received.abs()
                transferCurrency == sourceCurrency -> amount.abs()
                else -> {
                    errors += RowErrorCode.INCOMPLETE_TRANSFER
                    return null
                }
            }
            return TransferLegs(toAccount, transferAmount, transferCurrency)
        }

        private fun resolveCategory(
            row: List<String>,
            type: TransactionType,
            adjustments: MutableList<RowAdjustmentCode>,
        ): CategoryResolution? {
            if (type != TransactionType.EXPENSE && type != TransactionType.INCOME) return null
            val raw = textValue(row, CsvField.CATEGORY).orEmpty()
            if (raw.isEmpty()) return null
            val existing = categoriesByName[normalizeName(raw)]
            if (existing != null) return CategoryResolution(existing.name, null)
            if (!options.createMissingCategories) {
                adjustments += RowAdjustmentCode.CATEGORY_DROPPED
                return null
            }
            val categoryType = if (type == TransactionType.INCOME) CategoryType.INCOME else CategoryType.EXPENSE
            return CategoryResolution(raw, PendingCategory(raw, categoryType))
        }

        private fun resolveTags(
            row: List<String>,
            adjustments: MutableList<RowAdjustmentCode>,
        ): TagResolution {
            val raw = textValue(row, CsvField.TAGS).orEmpty()
            if (raw.isEmpty()) return TagResolution(emptyList(), emptyList())
            val names = raw.split(',', ';').map { it.trim() }.filter { it.isNotEmpty() }.distinct()
            val kept = mutableListOf<String>()
            val created = mutableListOf<String>()
            var dropped = false
            for (name in names) {
                val existing = tagsByName[normalizeName(name)]
                when {
                    existing != null -> kept += existing.name
                    options.createMissingTags -> {
                        kept += name
                        created += name
                    }
                    else -> dropped = true
                }
            }
            if (dropped) adjustments += RowAdjustmentCode.TAGS_DROPPED
            return TagResolution(kept.distinct(), created.distinct())
        }

        fun build(): CsvImportAnalysis = CsvImportAnalysis(
            rows = outcomes,
            newAccounts = newAccounts.values.toList(),
            newCategories = newCategories.values.toList(),
            newTags = newTags.values.toList(),
        )
    }

    /** Domain sign convention of [PendingMovement.amount]; mirrors the editor. */
    private fun signedAmount(type: TransactionType, parsed: BigDecimal): BigDecimal = when (type) {
        TransactionType.EXPENSE, TransactionType.TRANSFER -> parsed.abs().negate()
        TransactionType.INCOME -> parsed.abs()
        TransactionType.ADJUSTMENT -> parsed
    }

    /** True when the raw sign disagreed with the type and had to be flipped. */
    private fun signFlipped(type: TransactionType, parsed: BigDecimal): Boolean = when (type) {
        TransactionType.EXPENSE, TransactionType.TRANSFER -> parsed.signum() > 0
        TransactionType.INCOME -> parsed.signum() < 0
        TransactionType.ADJUSTMENT -> false
    }

    private fun buildTypeAliases(labels: Map<TransactionType, String>): Map<String, TransactionType> {
        val aliases = mutableMapOf<String, TransactionType>()
        BUILT_IN_TYPE_ALIASES.forEach { (type, tokens) ->
            tokens.forEach { aliases[normalizeToken(it)] = type }
        }
        labels.forEach { (type, label) -> aliases[normalizeToken(label)] = type }
        return aliases
    }

    private fun normalizeName(text: String): String =
        text.trim().lowercase(Locale.ROOT).replace(WHITESPACE, " ")

    private fun normalizeToken(text: String): String =
        Normalizer.normalize(text.trim().lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(DIACRITICS, "")
            .filter { it.isLetterOrDigit() }

    private companion object {
        val WHITESPACE = "\\s+".toRegex()
        val DIACRITICS = "\\p{InCombiningDiacriticalMarks}+".toRegex()

        val BUILT_IN_TYPE_ALIASES: Map<TransactionType, List<String>> = mapOf(
            TransactionType.EXPENSE to listOf("spesa", "uscita", "expense", "debit", "addebito", "withdrawal"),
            TransactionType.INCOME to listOf("entrata", "income", "credit", "accredito", "deposit"),
            TransactionType.TRANSFER to listOf("trasferimento", "transfer", "giroconto", "bonifico"),
            TransactionType.ADJUSTMENT to listOf("rettifica", "adjustment", "correzione"),
        )
    }
}
