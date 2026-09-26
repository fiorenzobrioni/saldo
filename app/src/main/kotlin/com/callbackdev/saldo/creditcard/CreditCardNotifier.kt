package com.callbackdev.saldo.creditcard

import android.annotation.SuppressLint
import android.content.Context
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import com.callbackdev.saldo.R
import com.callbackdev.saldo.core.common.money.MoneyFormatter
import com.callbackdev.saldo.core.domain.usecase.DueStatement
import com.callbackdev.saldo.notifications.SaldoNotifications
import com.callbackdev.saldo.notifications.SaldoNotifications.quietAtNight
import com.callbackdev.saldo.notifications.SaldoNotifications.story
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Posts the credit card statement notifications: an informative one when an
 * auto-post card has been charged, and a confirmation one when a confirm-mode
 * card has a statement waiting to be paid (tapping opens the app, where the
 * dashboard card settles it). One notification per kind with a fixed id,
 * replaced on repost. In the family's idiom (see [SaldoNotifications]): the
 * collapsed sentence is the amount; expanded, the cycle it covers and, for a
 * statement to pay, the day it is due. Like the other notifiers, posting is a
 * silent no-op until POST_NOTIFICATIONS is granted.
 */
@Singleton
class CreditCardNotifier @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    fun createChannel() {
        NotificationManagerCompat.from(context).createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_STATEMENT, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(context.getString(R.string.notif_channel_statement_name))
                .build(),
        )
    }

    fun notify(statements: List<DueStatement>) {
        post(ID_POSTED, statements.filter { it.autoPosted }, auto = true)
        post(ID_CONFIRM, statements.filterNot { it.autoPosted }, auto = false)
    }

    private fun post(id: Int, statements: List<DueStatement>, auto: Boolean) {
        val single = statements.singleOrNull()
        when {
            statements.isEmpty() -> return

            single != null -> post(
                id = id,
                title = context.getString(
                    if (auto) R.string.notif_statement_posted_title else R.string.notif_statement_confirm_title,
                    single.cardName,
                ),
                body = context.getString(
                    if (auto) R.string.notif_statement_posted_body else R.string.notif_statement_confirm_body,
                    MoneyFormatter.format(single.amount, single.currency),
                ),
                details = buildList {
                    add(
                        context.getString(
                            R.string.notif_statement_cycle,
                            single.cycle.start.spoken(),
                            single.cycle.closing.spoken(),
                        ),
                    )
                    if (!auto) {
                        add(context.getString(R.string.notif_statement_payment_due, single.cycle.paymentDue.spoken()))
                    }
                },
            )

            else -> post(
                id = id,
                title = context.resources.getQuantityString(
                    if (auto) {
                        R.plurals.notif_statement_posted_summary_title
                    } else {
                        R.plurals.notif_statement_confirm_summary_title
                    },
                    statements.size,
                    statements.size,
                ),
                body = statements.joinToString(separator = ", ") { it.cardName },
                details = statements.map {
                    context.getString(
                        R.string.notif_statement_line,
                        it.cardName,
                        MoneyFormatter.format(it.amount, it.currency),
                    )
                },
            )
        }
    }

    /** "28 settembre": a day of the cycle as a sentence says it. */
    private fun LocalDate.spoken(): String =
        format(DateTimeFormatter.ofPattern(DAY_PATTERN, context.resources.configuration.locales[0]))

    // Guarded by hasPermission(); lint's flow analysis is intraprocedural.
    @SuppressLint("MissingPermission")
    private fun post(id: Int, title: String, body: String, details: List<String>) {
        if (!SaldoNotifications.hasPermission(context)) return
        val notification = SaldoNotifications.builder(context, CHANNEL_STATEMENT)
            .setContentTitle(title)
            .story(body, details)
            .quietAtNight()
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    private companion object {
        const val CHANNEL_STATEMENT = "credit_card_statement"
        const val ID_POSTED = 1006
        const val ID_CONFIRM = 1007
        const val DAY_PATTERN = "d MMMM"
    }
}
