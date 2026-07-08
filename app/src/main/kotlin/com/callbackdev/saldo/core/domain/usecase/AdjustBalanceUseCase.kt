package com.callbackdev.saldo.core.domain.usecase

import com.callbackdev.saldo.core.domain.model.Transaction
import com.callbackdev.saldo.core.domain.model.TransactionType
import com.callbackdev.saldo.core.domain.money.MoneyMapper
import com.callbackdev.saldo.core.domain.repository.AccountRepository
import com.callbackdev.saldo.core.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.first
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import javax.inject.Inject

/**
 * Aligns an account's computed balance with the real-world balance typed by
 * the user, recording a single [TransactionType.ADJUSTMENT] movement that
 * carries the difference.
 *
 * Adjustments never pollute statistics: they are excluded at query level
 * (PLANNING ADR 8). If the typed balance already matches the computed one,
 * nothing is recorded.
 */
class AdjustBalanceUseCase @Inject constructor(
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val clock: Clock,
) {

    suspend operator fun invoke(accountId: Long, realBalance: BigDecimal): Result {
        val account = accountRepository.getAccount(accountId) ?: return Result.AccountNotFound
        val currentBalance = accountRepository.observeAccountBalance(accountId).first()
        val scale = MoneyMapper.fractionDigits(account.currency)
        val delta = realBalance
            .setScale(scale, RoundingMode.HALF_UP)
            .subtract(currentBalance)
        return if (delta.signum() == 0) {
            Result.NoChange
        } else {
            val now = clock.instant()
            transactionRepository.upsert(
                Transaction(
                    type = TransactionType.ADJUSTMENT,
                    amount = delta,
                    currency = account.currency,
                    accountId = accountId,
                    timestamp = now,
                    zoneOffset = clock.zone.rules.getOffset(now),
                ),
            )
            Result.Adjusted(delta)
        }
    }

    sealed interface Result {
        /** An adjustment movement with the signed [delta] was recorded. */
        data class Adjusted(val delta: BigDecimal) : Result

        /** The typed balance already matches the computed one: nothing recorded. */
        data object NoChange : Result

        data object AccountNotFound : Result
    }
}
