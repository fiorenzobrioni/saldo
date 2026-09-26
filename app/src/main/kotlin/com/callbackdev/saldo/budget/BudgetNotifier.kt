package com.callbackdev.saldo.budget

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.callbackdev.saldo.R
import com.callbackdev.saldo.core.common.money.MoneyFormatter
import com.callbackdev.saldo.core.domain.model.BudgetLevel
import com.callbackdev.saldo.core.domain.usecase.BudgetAlert
import com.callbackdev.saldo.notifications.BudgetMonth
import com.callbackdev.saldo.notifications.SaldoNotifications
import com.callbackdev.saldo.notifications.SaldoNotifications.quietAtNight
import com.callbackdev.saldo.notifications.SaldoNotifications.story
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Posts the budget threshold notifications (80% reached, limit exceeded), in
 * the family's idiom (see [SaldoNotifications]). One notification per level
 * with a fixed id, replaced on repost.
 *
 * Collapsed, a single alert is named and quantified ("Spesi 400 € di 500 €")
 * and several collapse into a summary. Expanded, the headline stays and the
 * story goes under it: what is left and at what pace per day, or by how much the
 * limit is passed, how much of the month has gone; and a bar per budget, the
 * picture a budget says faster than its lines (`notification_budget_expanded`).
 * The big text stays on the notification all the same, for a watch, a car, and
 * every surface that does not inflate a custom view. Posting is a silent no-op
 * until POST_NOTIFICATIONS is granted.
 */
@Singleton
class BudgetNotifier @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    fun createChannel() {
        NotificationManagerCompat.from(context).createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ALERTS, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(context.getString(R.string.notif_channel_budget_name))
                .build(),
        )
    }

    fun notify(alerts: List<BudgetAlert>, today: LocalDate = LocalDate.now()) {
        val month = BudgetMonth.of(today)
        post(
            id = ID_EXCEEDED,
            titleRes = R.string.notif_budget_exceeded_title,
            summaryRes = R.plurals.notif_budget_exceeded_summary_title,
            alerts = alerts.filter { it.level == BudgetLevel.OVER },
            month = month,
        )
        post(
            id = ID_WARNING,
            titleRes = R.string.notif_budget_warning_title,
            summaryRes = R.plurals.notif_budget_warning_summary_title,
            alerts = alerts.filter { it.level == BudgetLevel.WARNING },
            month = month,
        )
    }

    private fun post(id: Int, titleRes: Int, summaryRes: Int, alerts: List<BudgetAlert>, month: BudgetMonth) {
        val single = alerts.singleOrNull()
        when {
            alerts.isEmpty() -> return

            single != null -> post(
                id = id,
                title = context.getString(titleRes, single.name(), single.percent),
                headline = context.getString(
                    R.string.notif_budget_body,
                    single.spentText(),
                    single.limitText(),
                ),
                details = singleDetails(single, month),
                alerts = alerts,
                month = month,
            )

            else -> post(
                id = id,
                title = context.resources.getQuantityString(summaryRes, alerts.size, alerts.size),
                headline = alerts.joinToString(separator = ", ") { it.name() },
                details = alerts.map { alert ->
                    context.getString(
                        R.string.notif_budget_line,
                        alert.name(),
                        alert.percent,
                        alert.spentText(),
                        alert.limitText(),
                    )
                } + context.getString(R.string.notif_budget_month_elapsed, month.elapsedPercent),
                alerts = alerts,
                month = month,
            )
        }
    }

    /**
     * The story of one budget: what is left and at what pace per day, or by how
     * much the limit is passed; then where the month stands.
     */
    private fun singleDetails(alert: BudgetAlert, month: BudgetMonth): List<String> = buildList {
        val currency = alert.budget.currency
        val remaining = alert.budget.amount - alert.spent
        if (remaining.signum() < 0) {
            add(context.getString(R.string.notif_budget_over_by, MoneyFormatter.format(remaining.negate(), currency)))
        } else {
            val daily = month.dailyAllowance(remaining, currency)
            when {
                month.daysLeft == 1 -> add(
                    context.getString(R.string.notif_budget_left_last_day, MoneyFormatter.format(remaining, currency)),
                )
                daily != null -> add(
                    context.getString(
                        R.string.notif_budget_left,
                        MoneyFormatter.format(remaining, currency),
                        MoneyFormatter.format(daily, currency),
                    ),
                )
            }
        }
        if (month.daysLeft > 1) {
            add(context.resources.getQuantityString(R.plurals.notif_budget_days_left, month.daysLeft, month.daysLeft))
        }
        add(context.getString(R.string.notif_budget_month_elapsed, month.elapsedPercent))
    }

    private fun BudgetAlert.name(): String =
        categoryName ?: context.getString(R.string.budgets_overall_title)

    private fun BudgetAlert.spentText(): String = MoneyFormatter.format(spent, budget.currency)

    private fun BudgetAlert.limitText(): String = MoneyFormatter.format(budget.amount, budget.currency)

    // Guarded by hasPermission(); lint's flow analysis is intraprocedural.
    @SuppressLint("MissingPermission")
    @Suppress("LongParameterList")
    private fun post(
        id: Int,
        title: String,
        headline: String,
        details: List<String>,
        alerts: List<BudgetAlert>,
        month: BudgetMonth,
    ) {
        if (!SaldoNotifications.hasPermission(context)) return
        val builder = SaldoNotifications.builder(context, CHANNEL_ALERTS)
            .setContentTitle(title)
            .story(headline, details)
            .quietAtNight()
        // The picture is an addition, never a condition: if the bars cannot be
        // built, the big text above still tells the whole story.
        runCatching { bars(title, headline, details, alerts, month) }
            .getOrNull()
            ?.let(builder::setCustomBigContentView)
        NotificationManagerCompat.from(context).notify(id, builder.build())
    }

    /**
     * The expanded body with a bar per budget. A single budget keeps its story
     * under the bar; several are their own story, one bar each, so the lines
     * are not repeated under them.
     */
    private fun bars(
        title: String,
        headline: String,
        details: List<String>,
        alerts: List<BudgetAlert>,
        month: BudgetMonth,
    ): RemoteViews = RemoteViews(context.packageName, R.layout.notification_budget_expanded).apply {
        setTextViewText(R.id.notif_title, title)
        setTextViewText(R.id.notif_headline, headline)
        alerts.take(MAX_BARS).forEach { alert -> addView(R.id.notif_budget_rows, row(alert, month)) }
        if (alerts.size == 1 && details.isNotEmpty()) {
            setTextViewText(R.id.notif_details, details.joinToString("\n"))
        } else {
            setViewVisibility(R.id.notif_details, View.GONE)
        }
    }

    private fun row(alert: BudgetAlert, month: BudgetMonth): RemoteViews =
        RemoteViews(context.packageName, R.layout.notification_budget_row).apply {
            setTextViewText(R.id.notif_budget_name, alert.name())
            setTextViewText(
                R.id.notif_budget_value,
                context.getString(R.string.notif_budget_row_value, alert.spentText(), alert.limitText()),
            )
            setProgressBar(R.id.notif_budget_bar, PERCENT, alert.percent.coerceIn(0, PERCENT), false)
            setInt(R.id.notif_budget_bar, "setSecondaryProgress", month.elapsedPercent)
            val (day, night) = when (alert.level) {
                BudgetLevel.OVER -> ERROR_DAY to ERROR_NIGHT
                else -> WARNING_DAY to WARNING_NIGHT
            }
            setColorStateList(
                R.id.notif_budget_bar,
                "setProgressTintList",
                ColorStateList.valueOf(day),
                ColorStateList.valueOf(night),
            )
            setColorStateList(
                R.id.notif_budget_bar,
                "setSecondaryProgressTintList",
                ColorStateList.valueOf(day and BAND_MASK or BAND_ALPHA),
                ColorStateList.valueOf(night and BAND_MASK or BAND_ALPHA),
            )
        }

    private companion object {
        const val CHANNEL_ALERTS = "budget_alerts"
        const val ID_WARNING = 1004
        const val ID_EXCEEDED = 1005

        /** Four bars keep the expanded body under the platform's 256 dp ceiling. */
        const val MAX_BARS = 4
        const val PERCENT = 100

        /**
         * The app's budget colours on the notification's two grounds: the
         * amber of `MoneyColors.warning` and the brand scheme's error, light and
         * dark. Hexes because a notification is drawn by the system, outside
         * any Compose theme.
         */
        const val WARNING_DAY = 0xFF9A6700.toInt()
        const val WARNING_NIGHT = 0xFFFFB74D.toInt()
        const val ERROR_DAY = 0xFFBA1A1A.toInt()
        const val ERROR_NIGHT = 0xFFFFB4AB.toInt()

        /** The month's band: the bar's colour at 30%. */
        const val BAND_MASK = 0x00FFFFFF
        const val BAND_ALPHA = 0x4D000000
    }
}
