package com.callbackdev.saldo.core.database.di

import com.callbackdev.saldo.core.database.repository.RoomAccountRepository
import com.callbackdev.saldo.core.database.repository.RoomCategoryRepository
import com.callbackdev.saldo.core.database.repository.RoomRecurringRuleRepository
import com.callbackdev.saldo.core.database.repository.RoomTagRepository
import com.callbackdev.saldo.core.database.repository.RoomTransactionRepository
import com.callbackdev.saldo.core.domain.repository.AccountRepository
import com.callbackdev.saldo.core.domain.repository.CategoryRepository
import com.callbackdev.saldo.core.domain.repository.RecurringRuleRepository
import com.callbackdev.saldo.core.domain.repository.TagRepository
import com.callbackdev.saldo.core.domain.repository.TransactionRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAccountRepository(impl: RoomAccountRepository): AccountRepository

    @Binds
    @Singleton
    abstract fun bindCategoryRepository(impl: RoomCategoryRepository): CategoryRepository

    @Binds
    @Singleton
    abstract fun bindTransactionRepository(impl: RoomTransactionRepository): TransactionRepository

    @Binds
    @Singleton
    abstract fun bindTagRepository(impl: RoomTagRepository): TagRepository

    @Binds
    @Singleton
    abstract fun bindRecurringRuleRepository(impl: RoomRecurringRuleRepository): RecurringRuleRepository
}
