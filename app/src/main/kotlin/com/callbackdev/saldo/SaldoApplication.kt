package com.callbackdev.saldo

import android.app.Application
import android.os.Build
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.callbackdev.saldo.budget.BudgetNotifier
import com.callbackdev.saldo.budget.BudgetThresholdWatcher
import com.callbackdev.saldo.core.common.di.ApplicationScope
import com.callbackdev.saldo.creditcard.CreditCardNotifier
import com.callbackdev.saldo.feature.widget.SaldoQuickAddWidgetReceiver
import com.callbackdev.saldo.feature.widget.SaldoQuickBarWidgetReceiver
import com.callbackdev.saldo.feature.widget.WidgetRefreshWatcher
import com.callbackdev.saldo.recurring.RecurringNotifier
import com.callbackdev.saldo.recurring.RecurringWorkScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
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
    lateinit var widgetRefreshWatcher: WidgetRefreshWatcher

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
        widgetRefreshWatcher.start(applicationScope)
        RecurringWorkScheduler.schedule(this)
        publishWidgetPreviews()
    }

    /**
     * Regenerates the widget picker's preview (API 35+) so it shows the real
     * layout in the user's palette and categories instead of the static XML.
     * Rate limited by the system to about two calls an hour: a denied call is
     * fine, the previous preview (or the XML fallback) simply stays.
     */
    private fun publishWidgetPreviews() {
        applicationScope.launch {
            // The SDK guard sits in the same scope as the call: lint's NewApi
            // check does not follow guards across lambda boundaries.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                runCatching {
                    val manager = GlanceAppWidgetManager(this@SaldoApplication)
                    manager.setWidgetPreviews(SaldoQuickAddWidgetReceiver::class)
                    manager.setWidgetPreviews(SaldoQuickBarWidgetReceiver::class)
                }
            }
        }
    }
}
