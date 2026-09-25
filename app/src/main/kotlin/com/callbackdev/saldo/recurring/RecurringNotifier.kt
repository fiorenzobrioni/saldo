package com.callbackdev.saldo.recurring

import android.annotation.SuppressLint
import android.content.Context
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import com.callbackdev.saldo.R
import com.callbackdev.saldo.core.common.money.MoneyFormatter
import com.callbackdev.saldo.core.domain.model.TransactionType
import com.callbackdev.saldo.core.domain.repository.TransactionRepository
import com.callbackdev.saldo.core.domain.usecase.DueMovementReminder
import com.callbackdev.saldo.core.domain.usecase.GeneratedMovement
import com.callbackdev.saldo.core.domain.usecase.UpcomingRenewal
import com.callbackdev.saldo.notifications.SaldoNotifications
import com.callbackdev.saldo.notifications.SaldoNotifications.quietAtNight
import com.callbackdev.saldo.notifications.SaldoNotifications.story
import dagger.hilt.android.qualifiers.ApplicationContext
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Currency
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Posts notifications for movements created by the background generation worker
 * (an informative one for automatic movements, a confirmation one for pending
 * movements) and the opt-in pre-renewal reminder for upcoming charges/credits
 * ("Netflix renews in 3 days"). Tapping any of them opens the app, where the
 * pending movements can be confirmed or skipped.
 *
 * In the family's idiom (see [SaldoNotifications]): collapsed, a title with the
 * count or the name and one sentence; expanded, the same sentence and under it
 * the movements themselves, one per line with its amount, so opening the
 * notification answers "which ones?" without opening the app. Posting is a
 * silent no-op until POST_NOTIFICATIONS is granted.
 */
@Singleton
class RecurringNotifier @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val transactionRepository: TransactionRepository,
) {

    fun createChannels() {
        val manager = NotificationManagerCompat.from(context)
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ACTIVITY, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName(context.getString(R.string.notif_channel_activity_name))
                .build(),
        )
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_CONFIRM, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(context.getString(R.string.notif_channel_confirm_name))
                .build(),
        )
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_UPCOMING, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(context.getString(R.string.notif_channel_upcoming_name))
                .build(),
        )
    }

    suspend fun notify(generated: List<GeneratedMovement>) {
        val autoCount = generated.count { !it.isPending }
        // The single confirm notification (fixed id, replaced on repost) reports
        // every movement still awaiting confirmation, not just this batch.
        val pendingCount = if (generated.any { it.isPending }) {
            transactionRepository.observePendingTransactions().first().size
        } else {
            0
        }
        if (autoCount > 0) {
            post(
                id = ID_ACTIVITY,
                channelId = CHANNEL_ACTIVITY,
                title = context.resources.getQuantityString(
                    R.plurals.notif_activity_title,
                    autoCount,
                    autoCount,
                ),
                body = context.getString(R.string.notif_activity_body),
                details = lines(generated.filterNot { it.isPending }.map { it.line() }),
            )
        }
        if (pendingCount > 0) {
            post(
                id = ID_CONFIRM,
                channelId = CHANNEL_CONFIRM,
                title = context.resources.getQuantityString(
                    R.plurals.notif_confirm_title,
                    pendingCount,
                    pendingCount,
                ),
                body = context.getString(R.string.notif_confirm_body),
                // This batch's: the older ones already had their notification.
                details = lines(generated.filter { it.isPending }.map { it.line() }),
            )
        }
    }

    /**
     * Posts the pre-renewal reminder: a named notification for a single upcoming
     * charge/credit, or one summary for several. Fixed id, replaced on repost.
     */
    fun notifyUpcoming(renewals: List<UpcomingRenewal>) {
        val renewal = renewals.singleOrNull()
        when {
            renewals.isEmpty() -> return

            renewal != null -> post(
                id = ID_UPCOMING,
                channelId = CHANNEL_UPCOMING,
                title = renewal.title(),
                body = renewal.amount?.let { MoneyFormatter.format(it, renewal.currency) }
                    ?: context.getString(R.string.notif_upcoming_body_variable),
                details = listOf(context.getString(R.string.notif_due_on, renewal.dueDate.spoken())),
            )

            else -> post(
                id = ID_UPCOMING,
                channelId = CHANNEL_UPCOMING,
                title = context.resources.getQuantityString(
                    R.plurals.notif_upcoming_summary_title,
                    renewals.size,
                    renewals.size,
                ),
                body = renewals.joinToString(separator = ", ") { it.ruleName },
                details = lines(renewals.map { upcomingLine(it.ruleName, it.dueDate, it.amount, it.currency) }),
            )
        }
    }

    /**
     * Posts the reminders for future-dated movements falling due (ADR 36). The
     * upcoming channel is reused deliberately: from the user's side "something
     * is about to be charged" is one kind of news, whether a subscription
     * renews or a deadline they typed in arrives, and splitting it in two
     * channels would only mean two switches to keep aligned.
     *
     * A separate notification id from the renewal radar's, so the two do not
     * replace each other on the same day.
     */
    fun notifyMovementReminders(reminders: List<DueMovementReminder>) {
        val reminder = reminders.singleOrNull()
        when {
            reminders.isEmpty() -> return

            reminder != null -> post(
                id = ID_MOVEMENT_REMINDER,
                channelId = CHANNEL_UPCOMING,
                title = reminder.title(),
                body = MoneyFormatter.format(
                    reminder.transaction.amount.abs(),
                    reminder.transaction.currency,
                ),
                details = listOf(context.getString(R.string.notif_due_on, reminder.dueDate.spoken())),
            )

            else -> post(
                id = ID_MOVEMENT_REMINDER,
                channelId = CHANNEL_UPCOMING,
                title = context.resources.getQuantityString(
                    R.plurals.notif_movement_reminder_summary_title,
                    reminders.size,
                    reminders.size,
                ),
                body = reminders.joinToString(separator = ", ") { it.label() },
                details = lines(
                    reminders.map {
                        upcomingLine(
                            it.label(),
                            it.dueDate,
                            it.transaction.amount.abs(),
                            it.transaction.currency,
                        )
                    },
                ),
            )
        }
    }

    private fun DueMovementReminder.title(): String = if (daysUntil == 0) {
        context.getString(R.string.notif_movement_reminder_today, label())
    } else {
        context.resources.getQuantityString(
            R.plurals.notif_movement_reminder_title,
            daysUntil,
            label(),
            daysUntil,
        )
    }

    /** The movement's own description, or a neutral stand-in when it has none. */
    private fun DueMovementReminder.label(): String =
        title.ifBlank { context.getString(R.string.notif_movement_reminder_untitled) }

    private fun UpcomingRenewal.title(): String {
        val todayRes = when (type) {
            TransactionType.INCOME -> R.string.notif_upcoming_income_today
            TransactionType.TRANSFER -> R.string.notif_upcoming_transfer_today
            else -> R.string.notif_upcoming_expense_today
        }
        val soonRes = when (type) {
            TransactionType.INCOME -> R.plurals.notif_upcoming_income_title
            TransactionType.TRANSFER -> R.plurals.notif_upcoming_transfer_title
            else -> R.plurals.notif_upcoming_expense_title
        }
        return if (daysUntil == 0) {
            context.getString(todayRes, ruleName)
        } else {
            context.resources.getQuantityString(soonRes, daysUntil, ruleName, daysUntil)
        }
    }

    /** One generated movement: its rule's name and what it moved. */
    private fun GeneratedMovement.line(): String = amount
        ?.let { context.getString(R.string.notif_movement_line, ruleName, MoneyFormatter.format(it, currency)) }
        ?: context.getString(R.string.notif_movement_line_variable, ruleName)

    /** Something falling due: its name, the day, and the amount when it is known. */
    private fun upcomingLine(name: String, date: LocalDate, amount: BigDecimal?, currency: Currency): String =
        amount
            ?.let { money ->
                context.getString(
                    R.string.notif_upcoming_line,
                    name,
                    date.spoken(),
                    MoneyFormatter.format(money, currency),
                )
            }
            ?: context.getString(R.string.notif_upcoming_line_variable, name, date.spoken())

    /**
     * At most [MAX_LINES] lines, then how many more: the system cuts a long body
     * at the bottom, and a count said is better than lines lost without a word.
     */
    private fun lines(all: List<String>): List<String> {
        if (all.size <= MAX_LINES) return all
        val rest = all.size - (MAX_LINES - 1)
        return all.take(MAX_LINES - 1) +
            context.resources.getQuantityString(R.plurals.notif_more_lines, rest, rest)
    }

    /** "sabato 28 settembre": the day as a sentence says it. */
    private fun LocalDate.spoken(): String =
        format(DateTimeFormatter.ofPattern(DAY_PATTERN, context.resources.configuration.locales[0]))

    // Guarded by hasPermission(); lint's flow analysis is intraprocedural.
    @SuppressLint("MissingPermission")
    private fun post(id: Int, channelId: String, title: String, body: String, details: List<String>) {
        if (!SaldoNotifications.hasPermission(context)) return
        val notification = SaldoNotifications.builder(context, channelId)
            .setContentTitle(title)
            .story(body, details)
            .quietAtNight()
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    private companion object {
        const val CHANNEL_ACTIVITY = "recurring_activity"
        const val CHANNEL_CONFIRM = "recurring_confirm"
        const val CHANNEL_UPCOMING = "recurring_upcoming"
        const val ID_ACTIVITY = 1001
        const val ID_CONFIRM = 1002
        const val ID_UPCOMING = 1003
        // 1004/1005 belong to BudgetNotifier and 1006/1007 to CreditCardNotifier:
        // a shared id would make one notification silently replace the other.
        const val ID_MOVEMENT_REMINDER = 1008

        /** Five facts under the headline, as in Chiaro's expanded bodies. */
        const val MAX_LINES = 5
        const val DAY_PATTERN = "EEEE d MMMM"
    }
}
