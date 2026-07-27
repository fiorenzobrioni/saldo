package com.callbackdev.saldo

import android.app.Application
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.os.Build
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
        dropGeneratedWidgetPreviews()
    }

    /**
     * Drops any generated widget preview this app published on API 35+, so the
     * launcher's picker falls back to the static `previewLayout` of each
     * provider.
     *
     * The app used to publish Glance-composed previews here
     * (`GlanceAppWidgetManager.setWidgetPreviews`) and the picker went blank
     * with it. A generated preview is not one option among three: the picker
     * takes the first layer that answers, and a published preview *shadows*
     * both fallbacks that are guaranteed to draw something
     * (`DatabaseWidgetPreviewLoader.generatePreviewInfoBg` reads the generated
     * preview first and consults `previewLayout` only when there is none). It
     * is also rate limited to about two calls an hour, lives only in
     * `system_server` memory - so it is gone after every reboot until the app
     * is opened again - and on the launcher side it sits behind a flag, which
     * makes the picker's appearance vary by device rather than by our code.
     *
     * Removing it is therefore not enough on its own: a device that already
     * stored a bad preview would keep showing it. This clears the stored one,
     * once per launch and idempotently, and is the reason the call survives the
     * feature it undoes.
     */
    private fun dropGeneratedWidgetPreviews() {
        applicationScope.launch {
            // The SDK guard sits in the same scope as the call: lint's NewApi
            // check does not follow guards across lambda boundaries.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                val manager = AppWidgetManager.getInstance(this@SaldoApplication)
                val categories = AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN or
                    AppWidgetProviderInfo.WIDGET_CATEGORY_KEYGUARD or
                    AppWidgetProviderInfo.WIDGET_CATEGORY_SEARCHBOX
                listOf(
                    SaldoQuickAddWidgetReceiver::class.java,
                    SaldoQuickBarWidgetReceiver::class.java,
                ).forEach { receiver ->
                    runCatching {
                        manager.removeWidgetPreview(
                            ComponentName(this@SaldoApplication, receiver),
                            categories,
                        )
                    }
                }
            }
        }
    }
}
