package com.callbackdev.saldo.core.domain.usecase

import com.callbackdev.saldo.core.common.prefs.UserPreferencesRepository
import com.callbackdev.saldo.core.domain.model.Transaction
import com.callbackdev.saldo.core.domain.model.localDate
import com.callbackdev.saldo.core.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

/** A future movement falling due inside the reminder window, for the caller to notify about. */
data class DueMovementReminder(
    val transactionId: Long,
    /** The movement's description, or empty when it has none: the notifier decides the wording. */
    val title: String,
    val transaction: Transaction,
    val dueDate: LocalDate,
    /** Whole days from today to [dueDate] (0 = due today). */
    val daysUntil: Int,
)

/**
 * Finds the future-dated movements the user asked to be reminded about, due
 * within the lead time (ADR 36). A one-off deadline - car tax, a school
 * instalment - is a movement with a date and a reminder, not a yearly recurring
 * rule invented to carry the notification.
 *
 * Deliberately reuses the pre-renewal radar's lead time
 * ([UserPreferencesRepository.renewalReminderPreferences]) rather than adding a
 * second one: "how early do you want to know" is one question, and asking it
 * twice would let the two answers drift apart. The radar's own on/off switch
 * gates this too, so a user who turned reminders off gets none - the per-movement
 * flag says *which* movements are worth a reminder, not whether the app may
 * notify at all.
 *
 * Each movement is reported **once** per date: `lastReminderDate` records the
 * date already announced, so a daily run inside the window does not repeat
 * itself, a run skipped because the device was off still reminds at the first
 * chance, and pushing the date further out re-arms the reminder. The watermark
 * advances even if the notification is later suppressed (permission revoked):
 * a user without notifications loses the reminder, not the data.
 */
@Singleton
class CheckDueMovementRemindersUseCase @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val userPreferences: UserPreferencesRepository,
    private val clock: Clock,
) {

    suspend operator fun invoke(today: LocalDate = LocalDate.now(clock)): List<DueMovementReminder> {
        val prefs = userPreferences.renewalReminderPreferences.first()
        if (!prefs.enabled) return emptyList()
        // From today, not tomorrow: a movement dated today has not been folded
        // into anything the user has seen yet, and "it is today" is the most
        // useful thing this reminder can say.
        return transactionRepository.getDueReminders(today, today.plusDays(prefs.leadDays.toLong()))
            .map { transaction ->
                val dueDate = transaction.localDate
                transactionRepository.updateReminderWatermark(transaction.id, dueDate)
                DueMovementReminder(
                    transactionId = transaction.id,
                    title = transaction.description.orEmpty(),
                    transaction = transaction,
                    dueDate = dueDate,
                    daysUntil = ChronoUnit.DAYS.between(today, dueDate).toInt(),
                )
            }
    }
}
