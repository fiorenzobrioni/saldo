package com.callbackdev.saldo.feature.widget

import android.content.Intent
import com.callbackdev.saldo.core.domain.model.TransactionType

/**
 * What the widget asked for, decoded from the launching intent. Not a
 * `NavKey`: the quick entry is its own activity over the launcher and never
 * enters the app's Navigation 3 back stack.
 */
data class QuickEntryRoute(
    val type: TransactionType,
    val categoryId: Long?,
    val accountId: Long?,
) {
    companion object {
        const val EXTRA_TYPE = "com.callbackdev.saldo.extra.QUICK_TYPE"
        const val EXTRA_CATEGORY_ID = "com.callbackdev.saldo.extra.QUICK_CATEGORY_ID"
        const val EXTRA_ACCOUNT_ID = "com.callbackdev.saldo.extra.QUICK_ACCOUNT_ID"

        private const val NO_ID = -1L

        fun from(intent: Intent?): QuickEntryRoute = QuickEntryRoute(
            type = intent?.getStringExtra(EXTRA_TYPE)
                ?.let { name -> TransactionType.entries.firstOrNull { it.name == name } }
                ?: TransactionType.EXPENSE,
            categoryId = intent?.getLongExtra(EXTRA_CATEGORY_ID, NO_ID)?.takeIf { it != NO_ID },
            accountId = intent?.getLongExtra(EXTRA_ACCOUNT_ID, NO_ID)?.takeIf { it != NO_ID },
        )

        fun putExtras(intent: Intent, type: TransactionType, categoryId: Long?, accountId: Long?): Intent =
            intent
                .putExtra(EXTRA_TYPE, type.name)
                .putExtra(EXTRA_CATEGORY_ID, categoryId ?: NO_ID)
                .putExtra(EXTRA_ACCOUNT_ID, accountId ?: NO_ID)
    }
}
