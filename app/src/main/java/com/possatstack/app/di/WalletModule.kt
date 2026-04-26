package com.possatstack.app.di

import com.possatstack.app.wallet.OnChainWalletEngine
import com.possatstack.app.wallet.bitcoin.BdkOnChainEngine
import com.possatstack.app.wallet.chain.ChainDataSource
import com.possatstack.app.wallet.chain.ChainSyncProvider
import com.possatstack.app.wallet.chain.EsploraChainDataSource
import com.possatstack.app.wallet.chain.EsploraChainSyncProvider
import com.possatstack.app.wallet.payment.DefaultPaymentOrchestrator
import com.possatstack.app.wallet.payment.PaymentOrchestrator
import com.possatstack.app.wallet.signer.AndroidBiometricAuthenticator
import com.possatstack.app.wallet.signer.AndroidKeystoreSignerSecretStore
import com.possatstack.app.wallet.signer.BdkSeedSigner
import com.possatstack.app.wallet.signer.BiometricAuthenticator
import com.possatstack.app.wallet.signer.Signer
import com.possatstack.app.wallet.signer.SignerSecretStore
import com.possatstack.app.wallet.signer.TapsignerNfcSigner
import com.possatstack.app.wallet.signer.tapsigner.AndroidNfcSessionLauncher
import com.possatstack.app.wallet.signer.tapsigner.NfcSessionLauncher
import com.possatstack.app.wallet.signer.tapsigner.TapsignerCrypto
import com.possatstack.app.wallet.signer.tapsigner.UnavailableTapsignerCrypto
import com.possatstack.app.wallet.storage.SecureWalletStorage
import com.possatstack.app.wallet.storage.WalletStorage
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
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
 *
 * ## Signer multibinding (Fase 5.1)
 *
 * Signers are contributed as a `Set<Signer>` and consumed through
 * [com.possatstack.app.wallet.signer.SignerRegistry]. Adding a new
 * signer (Coldcard, airgap QR) means adding one more `@IntoSet` entry
 * below — nothing else downstream has to change. The default entry
 * point for callers that do not show a picker is
 * `SignerRegistry.default()`; the picker sheet uses `registry.all`.
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

    // Active chain backend: Esplora HTTP via BDK's built-in EsploraClient.
    // Kyoto (BIP-157/158 CBF light client) is also wired in this module
    // (KyotoChainDataSource + KyotoChainSyncProvider) but inactive — to
    // switch, replace `EsploraChainDataSource` with
    // `com.possatstack.app.wallet.chain.KyotoChainDataSource` here AND
    // `EsploraChainSyncProvider` with
    // `com.possatstack.app.wallet.chain.KyotoChainSyncProvider` below,
    // then flip CHAIN_BACKEND in app/build.gradle.kts to "kyoto" so the
    // engine wipes the BDK cache on first boot and runs a fresh scan.
    @Binds
    @Singleton
    abstract fun bindChainDataSource(impl: EsploraChainDataSource): ChainDataSource

    /**
     * Drives the actual scan pipeline (filter download, update apply, persist).
     * Each backend ships its own provider; bind the one matching
     * [bindChainDataSource] above.
     */
    @Binds
    @Singleton
    abstract fun bindChainSyncProvider(impl: EsploraChainSyncProvider): ChainSyncProvider

    @Binds
    @Singleton
    abstract fun bindOnChainWalletEngine(impl: BdkOnChainEngine): OnChainWalletEngine

    /** Default signer used by engines that do not present a picker to the user. */
    @Binds
    @Singleton
    abstract fun bindDefaultSigner(impl: BdkSeedSigner): Signer

    /** Contributes the in-app seed signer to the registry. */
    @Binds
    @IntoSet
    @Singleton
    abstract fun bindSeedSignerIntoSet(impl: BdkSeedSigner): Signer

    /** Contributes the TAPSIGNER NFC signer to the registry. */
    @Binds
    @IntoSet
    @Singleton
    abstract fun bindTapsignerNfcSignerIntoSet(impl: TapsignerNfcSigner): Signer

    /** NFC session launcher used by the TAPSIGNER signer to get a connected IsoDep. */
    @Binds
    @Singleton
    internal abstract fun bindNfcSessionLauncher(impl: AndroidNfcSessionLauncher): NfcSessionLauncher

    /**
     * Biometric prompt implementation. Uses [androidx.biometric.BiometricPrompt]
     * through [com.possatstack.app.wallet.signer.ActivityHolder] so it stays
     * activity-agnostic from the caller's perspective.
     */
    @Binds
    @Singleton
    abstract fun bindBiometricAuthenticator(impl: AndroidBiometricAuthenticator): BiometricAuthenticator

    /**
     * Top-level payment orchestrator. Consumed by the cobrança UI
     * (`ChargeViewModel`, `ChargeDetailsViewModel`); wraps
     * [OnChainWalletEngine] today and will wrap future `LightningEngine`
     * / `BearerMethodEngine` in Fase 5.
     */
    @Binds
    @Singleton
    abstract fun bindPaymentOrchestrator(impl: DefaultPaymentOrchestrator): PaymentOrchestrator

    companion object {
        /**
         * secp256k1 crypto backing the TAPSIGNER ECDH handshake. Shipped as a
         * placeholder that fails loudly at the handshake step — swap for a
         * real secp256k1-backed impl during the hardware-QA pass (see
         * docs/phase-5.md).
         */
        @Provides
        @Singleton
        internal fun provideTapsignerCrypto(): TapsignerCrypto = UnavailableTapsignerCrypto()
    }
}
