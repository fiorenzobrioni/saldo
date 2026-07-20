package com.callbackdev.saldo.feature.transactions.importer

import com.callbackdev.saldo.core.domain.model.CategoryType
import com.callbackdev.saldo.core.domain.model.TransactionType
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Currency

/** What the user lets the import do beyond inserting the movements themselves. */
data class CsvImportOptions(
    /** Skip rows that match a movement already in the ledger or an earlier row. */
    val skipDuplicates: Boolean = true,
    /** Create accounts named in the file that do not exist yet. */
    val createMissingAccounts: Boolean = true,
    /** Create categories named in the file that do not exist yet. */
    val createMissingCategories: Boolean = true,
    /** Create tags named in the file that do not exist yet. */
    val createMissingTags: Boolean = true,
)

/** Why a whole file cannot be imported (as opposed to a single row failing). */
enum class CsvImportError {
    /** The file could not be opened or read at all. */
    UNREADABLE,

    /** The file is empty or has no readable text. */
    EMPTY_FILE,

    /** No header row could be matched to the movement columns. */
    UNRECOGNIZED_FORMAT,

    /** A header was recognized but no data rows follow it. */
    NO_DATA_ROWS,

    /** The file has more rows than a single import is allowed to process. */
    TOO_MANY_ROWS,
}

/** A row that cannot become a movement, with the reason(s) shown in the report. */
enum class RowErrorCode {
    MISSING_AMOUNT,
    INVALID_AMOUNT,
    AMOUNT_OUT_OF_RANGE,
    MISSING_DATE,
    INVALID_DATE,
    MISSING_ACCOUNT,
    UNKNOWN_ACCOUNT,
    ACCOUNT_CURRENCY_MISMATCH,
    INVALID_CURRENCY,
    INCOMPLETE_TRANSFER,
}

/** A tolerated fix applied while reading a row, surfaced so nothing is silent. */
enum class RowAdjustmentCode {
    /** The amount sign was flipped to match its movement type. */
    SIGN_NORMALIZED,

    /** The type was inferred from the amount sign (no usable type column). */
    TYPE_INFERRED,

    /** The currency was taken from the account because the row did not state one. */
    CURRENCY_DEFAULTED,

    /** A named category was dropped because it does not exist and creation is off. */
    CATEGORY_DROPPED,

    /** One or more tags were dropped because they do not exist and creation is off. */
    TAGS_DROPPED,
}

/** Why a row is considered a duplicate. */
enum class DuplicateReason { ALREADY_IN_LEDGER, DUPLICATE_IN_FILE }

/**
 * A resolved movement ready to be written, still referring to its account,
 * category and tags by name: the ids are bound at commit time, after any
 * missing entities are created. Amounts already carry the domain sign.
 */
data class PendingMovement(
    val type: TransactionType,
    val date: LocalDate,
    val amount: BigDecimal,
    val currency: Currency,
    val accountName: String,
    val toAccountName: String?,
    val transferAmount: BigDecimal?,
    val transferCurrency: Currency?,
    val categoryName: String?,
    val description: String?,
    val note: String?,
    val tagNames: List<String>,
    /** Stable key used to detect duplicates against the ledger and the file. */
    val signature: String,
)

/** The outcome of a single data row. [rowNumber] is 1-based, header excluded. */
sealed interface RowOutcome {
    val rowNumber: Int

    /** The row became a movement; [adjustments] lists any tolerated fixes. */
    data class Importable(
        override val rowNumber: Int,
        val movement: PendingMovement,
        val adjustments: List<RowAdjustmentCode>,
    ) : RowOutcome

    /** The row matches an existing or earlier movement and would be skipped. */
    data class Duplicate(
        override val rowNumber: Int,
        val reason: DuplicateReason,
    ) : RowOutcome

    /** The row cannot be read as a movement; [errors] explains why. */
    data class Invalid(
        override val rowNumber: Int,
        val errors: List<RowErrorCode>,
    ) : RowOutcome
}

/** An account the import would create, with the currency inferred for it. */
data class PendingAccount(val name: String, val currency: Currency)

/** A category the import would create, typed after the movements that use it. */
data class PendingCategory(val name: String, val type: CategoryType)

/**
 * The dry-run result: every row's outcome plus the new entities the import
 * would create. Drives the pre-flight preview; the counts are derived so the UI
 * and the report agree.
 */
data class CsvImportAnalysis(
    val rows: List<RowOutcome>,
    val newAccounts: List<PendingAccount>,
    val newCategories: List<PendingCategory>,
    val newTags: List<String>,
) {
    val importable: List<RowOutcome.Importable> = rows.filterIsInstance<RowOutcome.Importable>()
    val importableCount: Int get() = importable.size
    val duplicateCount: Int get() = rows.count { it is RowOutcome.Duplicate }
    val invalidCount: Int get() = rows.count { it is RowOutcome.Invalid }
    val adjustedCount: Int get() = importable.count { it.adjustments.isNotEmpty() }
    val totalRows: Int get() = rows.size

    /** True when there is nothing to write (nothing importable). */
    val isEmpty: Boolean get() = importableCount == 0
}

/**
 * The detailed account of a committed import, shown when it finishes. Mirrors
 * the analysis but reports what actually happened, so a partial failure would
 * still explain itself.
 */
data class CsvImportReport(
    val imported: Int,
    val duplicatesSkipped: Int,
    val invalid: Int,
    val adjusted: Int,
    val createdAccounts: List<String>,
    val createdCategories: List<String>,
    val createdTags: List<String>,
    val totalRows: Int,
)

/**
 * The stage of the guided import shown to the user. Null (held by the screen)
 * means no import is in progress. The flow is [Reading] -> [Preview] (the
 * dry-run the user confirms or tunes) -> [Done] (the report), any step of which
 * can be dismissed to cancel.
 */
sealed interface CsvImportStage {
    /** The file is being opened and parsed. */
    data object Reading : CsvImportStage

    /** The dry-run is ready; [isBusy] covers a re-analysis or the commit. */
    data class Preview(
        val analysis: CsvImportAnalysis,
        val options: CsvImportOptions,
        val isBusy: Boolean = false,
    ) : CsvImportStage

    /** The import finished; [report] details what was written. */
    data class Done(val report: CsvImportReport) : CsvImportStage
}
