package com.callbackdev.saldo.core.domain.repository

/**
 * Runs [block] inside a single database transaction: either every write in the
 * block is persisted, or none is. Lets domain logic (e.g. recurring generation,
 * which must persist movements and the rule watermark together) stay atomic
 * without depending on Room.
 */
interface TransactionRunner {

    suspend fun <T> inTransaction(block: suspend () -> T): T
}
