package com.callbackdev.saldo.recurring

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.callbackdev.saldo.core.domain.usecase.GenerateRecurringMovementsUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Periodic background generation of due recurring movements (PLANNING ADR 4:
 * covers days the device was off, beyond the app-open catch-up). Idempotent, so
 * overlapping with the catch-up run is harmless. Notifies about what it created.
 */
@HiltWorker
class RecurringGenerationWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val generateRecurringMovements: GenerateRecurringMovementsUseCase,
    private val notifier: RecurringNotifier,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = runCatching {
        val generated = generateRecurringMovements()
        notifier.notify(generated)
    }.fold(
        onSuccess = { Result.success() },
        onFailure = { Result.retry() },
    )
}
