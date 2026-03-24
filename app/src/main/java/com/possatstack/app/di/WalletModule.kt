package com.possatstack.app.di

import com.possatstack.app.wallet.WalletRepository
import com.possatstack.app.wallet.bitcoin.BdkWalletRepository
import com.possatstack.app.wallet.storage.SecureWalletStorage
import com.possatstack.app.wallet.storage.WalletStorage
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class WalletModule {

    @Binds
    @Singleton
    abstract fun bindWalletStorage(impl: SecureWalletStorage): WalletStorage

    @Binds
    @Singleton
    abstract fun bindWalletRepository(impl: BdkWalletRepository): WalletRepository
}
