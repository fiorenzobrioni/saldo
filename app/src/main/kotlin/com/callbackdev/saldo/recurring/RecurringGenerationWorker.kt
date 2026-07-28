package com.callbackdev.saldo.recurring

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.callbackdev.saldo.budget.BudgetNotifier
import com.callbackdev.saldo.core.domain.usecase.CheckBudgetThresholdsUseCase
import com.callbackdev.saldo.core.domain.usecase.CheckUpcomingRenewalsUseCase
import com.callbackdev.saldo.core.domain.usecase.GenerateRecurringMovementsUseCase
import com.callbackdev.saldo.core.domain.usecase.ProcessDueCreditCardStatementsUseCase
import com.callbackdev.saldo.creditcard.CreditCardNotifier
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Periodic background generation of due recurring movements (PLANNING ADR 4:
 * covers days the device was off, beyond the app-open catch-up). Idempotent, so
 * overlapping with the catch-up run is harmless. Notifies about what it created,
 * then checks the opt-in pre-renewal reminders - after generation, so an
 * occurrence due today is recorded, not announced as upcoming.
 */
@HiltWorker
@Suppress("LongParameterList") // One Hilt-injected collaborator per pipeline step.
class RecurringGenerationWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val generateRecurringMovements: GenerateRecurringMovementsUseCase,
    private val checkUpcomingRenewals: CheckUpcomingRenewalsUseCase,
    private val notifier: RecurringNotifier,
    private val checkBudgetThresholds: CheckBudgetThresholdsUseCase,
    private val budgetNotifier: BudgetNotifier,
    private val processDueStatements: ProcessDueCreditCardStatementsUseCase,
    private val creditCardNotifier: CreditCardNotifier,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = runCatching {
        val generated = generateRecurringMovements()
        notifier.notify(generated)
        notifier.notifyUpcoming(checkUpcomingRenewals())
        // Auto-post the credit card statements that came due and report the
        // confirm-mode ones (the settlement transfer itself never touches the
        // budget, so its position relative to the budget check is immaterial).
        creditCardNotifier.notify(processDueStatements())
        // After generation, so an automatic charge that crosses a budget
        // threshold alerts on the same run, even with the device untouched.
        // No widget refresh here: the widget shows no totals, so a generated
        // movement changes nothing it draws.
        budgetNotifier.notify(checkBudgetThresholds())
    }.fold(
        onSuccess = { Result.success() },
        onFailure = { Result.retry() },
    )
}
