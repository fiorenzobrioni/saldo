package com.callbackdev.saldo.feature.transactions.export

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.callbackdev.saldo.R
import com.callbackdev.saldo.core.common.di.IoDispatcher
import com.callbackdev.saldo.core.common.prefs.CsvSeparator
import com.callbackdev.saldo.core.domain.model.TransactionType
import com.callbackdev.saldo.feature.transactions.TransactionListItem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * Writes the CSV export of the filtered ledger into the app cache and exposes
 * it through the manifest [FileProvider], so it can be handed to the system
 * Share Sheet without any storage permission. Headers and type names are
 * localized at export time.
 */
class TransactionsCsvExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    /** Renders and writes `cache/exports/[fileName]`, returning its shareable [Uri]. */
    suspend fun export(
        fileName: String,
        items: List<TransactionListItem>,
        tagNames: Map<Long, List<String>>,
        separator: CsvSeparator,
    ): Uri = withContext(ioDispatcher) {
        val csv = TransactionCsvBuilder.build(
            items = items,
            tagNames = tagNames,
            typeLabels = typeLabels(),
            labels = columnLabels(),
            separator = separator,
        )
        val directory = File(context.cacheDir, EXPORTS_DIRECTORY).apply { mkdirs() }
        val file = File(directory, fileName)
        file.writeText(csv)
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    private fun typeLabels(): Map<TransactionType, String> = mapOf(
        TransactionType.EXPENSE to context.getString(R.string.transaction_type_expense),
        TransactionType.INCOME to context.getString(R.string.transaction_type_income),
        TransactionType.TRANSFER to context.getString(R.string.transaction_type_transfer),
        TransactionType.ADJUSTMENT to context.getString(R.string.transaction_type_adjustment),
    )

    private fun columnLabels(): CsvColumnLabels = CsvColumnLabels(
        date = context.getString(R.string.csv_header_date),
        type = context.getString(R.string.csv_header_type),
        category = context.getString(R.string.csv_header_category),
        description = context.getString(R.string.csv_header_description),
        account = context.getString(R.string.csv_header_account),
        toAccount = context.getString(R.string.csv_header_to_account),
        amount = context.getString(R.string.csv_header_amount),
        currency = context.getString(R.string.csv_header_currency),
        receivedAmount = context.getString(R.string.csv_header_received_amount),
        receivedCurrency = context.getString(R.string.csv_header_received_currency),
        tags = context.getString(R.string.csv_header_tags),
        note = context.getString(R.string.csv_header_note),
    )

    private companion object {
        /** Matches the `cache-path` exposed in `res/xml/file_paths.xml`. */
        const val EXPORTS_DIRECTORY = "exports"
    }
}
