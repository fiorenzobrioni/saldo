package com.callbackdev.saldo

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.callbackdev.saldo.budget.BudgetNotifier
import com.callbackdev.saldo.budget.BudgetThresholdWatcher
import com.callbackdev.saldo.core.common.di.ApplicationScope
import com.callbackdev.saldo.creditcard.CreditCardNotifier
import com.callbackdev.saldo.recurring.RecurringNotifier
import com.callbackdev.saldo.recurring.RecurringWorkScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject

@HiltAndroidApp
class SaldoApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var recurringNotifier: RecurringNotifier

    @Inject
    lateinit var budgetNotifier: BudgetNotifier

    @Inject
    lateinit var creditCardNotifier: CreditCardNotifier

    @Inject
    lateinit var budgetThresholdWatcher: BudgetThresholdWatcher

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    // On-demand WorkManager initialization with the Hilt worker factory (the
    // default initializer is removed in the manifest).
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        recurringNotifier.createChannels()
        budgetNotifier.createChannel()
        creditCardNotifier.createChannel()
        budgetThresholdWatcher.start(applicationScope)
        RecurringWorkScheduler.schedule(this)
    }
}
