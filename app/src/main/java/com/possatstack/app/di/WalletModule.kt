package com.possatstack.app.di

import com.possatstack.app.wallet.OnChainWalletEngine
import com.possatstack.app.wallet.bitcoin.BdkOnChainEngine
import com.possatstack.app.wallet.chain.ChainDataSource
import com.possatstack.app.wallet.chain.EsploraChainDataSource
import com.possatstack.app.wallet.signer.AndroidKeystoreSignerSecretStore
import com.possatstack.app.wallet.signer.BiometricAuthenticator
import com.possatstack.app.wallet.signer.NoOpBiometricAuthenticator
import com.possatstack.app.wallet.signer.SignerSecretStore
import com.possatstack.app.wallet.storage.SecureWalletStorage
import com.possatstack.app.wallet.storage.WalletStorage
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt bindings for the wallet stack.
 *
 * ## Swapping the chain backend (dev-time)
 *
 * Today the app ships with Esplora. To switch to Kyoto or Floresta:
 *  1. Uncomment the target dep in `libs.versions.toml`.
 *  2. Flip the implementation in `app/build.gradle.kts` + update
 *     `buildConfigField("CHAIN_BACKEND", ...)`.
 *  3. Change the [bindChainDataSource] binding below to point at the new
 *     concrete type.
 *  4. On first run, the engine detects the changed `CHAIN_BACKEND` and wipes
 *     the BDK SQLite cache, forcing a fresh full scan. The mnemonic and LDK
 *     state (when it exists) are not touched.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class WalletModule {
    @Binds
    @Singleton
    abstract fun bindWalletStorage(impl: SecureWalletStorage): WalletStorage

    @Binds
    @Singleton
    abstract fun bindSignerSecretStore(impl: AndroidKeystoreSignerSecretStore): SignerSecretStore

    @Binds
    @Singleton
    abstract fun bindChainDataSource(impl: EsploraChainDataSource): ChainDataSource
    // @Binds @Singleton abstract fun bindChainDataSource(impl: KyotoChainDataSource): ChainDataSource
    // @Binds @Singleton abstract fun bindChainDataSource(impl: FlorestaChainDataSource): ChainDataSource

    @Binds
    @Singleton
    abstract fun bindOnChainWalletEngine(impl: BdkOnChainEngine): OnChainWalletEngine

    companion object {
        /**
         * No-op authenticator for Fase 1–2. Once Fase 3 extracts the Signer
         * and wires BiometricPrompt, swap this provider for an
         * activity-backed implementation.
         */
        @Provides
        @Singleton
        fun provideBiometricAuthenticator(): BiometricAuthenticator = NoOpBiometricAuthenticator()
    }
}
