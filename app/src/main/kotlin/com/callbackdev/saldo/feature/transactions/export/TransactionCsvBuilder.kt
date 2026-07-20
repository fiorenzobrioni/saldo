package com.callbackdev.saldo.feature.transactions.export

import com.callbackdev.saldo.core.common.csv.CsvFormulaGuard
import com.callbackdev.saldo.core.common.prefs.CsvSeparator
import com.callbackdev.saldo.core.domain.model.TransactionType
import com.callbackdev.saldo.feature.transactions.TransactionListItem
import com.callbackdev.saldo.feature.transactions.localDate
import java.math.BigDecimal

/** Localized header labels for the CSV columns, in column order. */
data class CsvColumnLabels(
    val date: String,
    val type: String,
    val category: String,
    val description: String,
    val account: String,
    val toAccount: String,
    val amount: String,
    val currency: String,
    val receivedAmount: String,
    val receivedCurrency: String,
    val tags: String,
    val note: String,
    val recurring: String,
)

/**
 * Renders a list of resolved movements as an RFC 4180 CSV document.
 *
 * Conventions:
 * - the decimal separator follows the column separator, so the file opens
 *   cleanly in a spreadsheet either way: `;` pairs with comma decimals (Excel
 *   with an Italian locale), `,` with dot decimals (international CSV);
 * - dates are ISO (`yyyy-MM-dd`), in the movement's own timezone (ADR 7);
 * - amounts are the signed effect on the account, in the account's currency;
 *   the received columns carry the incoming leg of cross-currency transfers;
 * - the document starts with a BOM so spreadsheets detect UTF-8, and fields
 *   containing the separator, quotes or newlines are quoted.
 */
object TransactionCsvBuilder {

    private const val BOM = "\uFEFF"
    private const val LINE_SEPARATOR = "\r\n"
    private const val TAG_SEPARATOR = ", "

    fun build(
        items: List<TransactionListItem>,
        tagNames: Map<Long, List<String>>,
        typeLabels: Map<TransactionType, String>,
        labels: CsvColumnLabels,
        separator: CsvSeparator,
        recurringMark: String,
    ): String {
        val header = listOf(
            labels.date, labels.type, labels.category, labels.description,
            labels.account, labels.toAccount, labels.amount, labels.currency,
            labels.receivedAmount, labels.receivedCurrency, labels.tags, labels.note,
            labels.recurring,
        )
        val rows = items.map { item ->
            val transaction = item.transaction
            listOf(
                transaction.localDate.toString(),
                typeLabels[transaction.type] ?: transaction.type.name,
                // Text fields carry user input: guard them against spreadsheet
                // formula injection. Amounts, dates and codes stay untouched.
                CsvFormulaGuard.guard(item.category?.name.orEmpty()),
                CsvFormulaGuard.guard(transaction.description.orEmpty()),
                CsvFormulaGuard.guard(item.account?.name.orEmpty()),
                CsvFormulaGuard.guard(item.toAccount?.name.orEmpty()),
                formatAmount(transaction.amount, separator),
                transaction.currency.currencyCode,
                transaction.transferAmount?.let { formatAmount(it, separator) }.orEmpty(),
                transaction.transferCurrency?.currencyCode.orEmpty(),
                CsvFormulaGuard.guard(tagNames[transaction.id].orEmpty().joinToString(TAG_SEPARATOR)),
                CsvFormulaGuard.guard(transaction.note.orEmpty()),
                // Informational flag only: the export marks movements a recurring
                // rule generated, but the import never rebuilds that link (it has
                // no rule to attach to), so an imported movement is always manual.
                if (transaction.recurringRuleId != null) recurringMark else "",
            )
        }
        return buildString {
            append(BOM)
            (listOf(header) + rows).forEach { fields ->
                append(fields.joinToString(separator.symbol.toString()) { escape(it, separator.symbol) })
                append(LINE_SEPARATOR)
            }
        }
    }

    /** Plain decimal with the convention-matching decimal separator; no grouping. */
    private fun formatAmount(amount: BigDecimal, separator: CsvSeparator): String {
        val plain = amount.toPlainString()
        return if (separator == CsvSeparator.SEMICOLON) plain.replace('.', ',') else plain
    }

    /** Characters (besides the separator itself) that force a field to be quoted. */
    private val QUOTE_TRIGGERS = charArrayOf('"', '\n', '\r')

    private fun escape(field: String, separator: Char): String =
        if (field.any { it == separator || it in QUOTE_TRIGGERS }) {
            "\"" + field.replace("\"", "\"\"") + "\""
        } else {
            field
        }
}
