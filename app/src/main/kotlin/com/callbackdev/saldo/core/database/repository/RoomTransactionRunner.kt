package com.callbackdev.saldo.core.database.repository

import androidx.room.withTransaction
import com.callbackdev.saldo.core.database.SaldoDatabase
import com.callbackdev.saldo.core.domain.repository.TransactionRunner
import javax.inject.Inject

class RoomTransactionRunner @Inject constructor(
    private val database: SaldoDatabase,
) : TransactionRunner {

    override suspend fun <T> inTransaction(block: suspend () -> T): T =
        database.withTransaction { block() }
}
