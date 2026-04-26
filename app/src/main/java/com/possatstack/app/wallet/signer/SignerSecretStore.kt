package com.possatstack.app.wallet.signer

import com.possatstack.app.wallet.WalletNetwork

/**
 * Dedicated secure storage for the wallet mnemonic.
 *
 * This store is **separate** from [com.possatstack.app.wallet.storage.WalletStorage]
 * on purpose: descriptors/cache are read on every boot, whereas the mnemonic
 * is a high-value secret kept under a hardware-backed AES-256-GCM key.
 *
 * Implementations must:
 *  1. Persist the mnemonic encrypted-at-rest, bound to the device.
 *  2. Prefer StrongBox when available, falling back to TEE.
 *  3. Never expose the mnemonic as a [String] (which the JVM interns and
 *     cannot be zeroed). Return a fresh [CharArray] on each read; callers
 *     overwrite it with `Char(0)` after use.
 *
 * No biometric/PIN prompt is required: the target POS hardware may not have
 * any biometric sensor, so we rely solely on the device-bound master key
 * (AndroidKeyStore + EncryptedSharedPreferences) for at-rest protection.
 */
interface SignerSecretStore {
    /** True if a mnemonic has been previously saved for the current device. */
    fun hasMnemonic(): Boolean

    /**
     * Persist the mnemonic securely. Overwrites any existing value.
     * The store takes a defensive copy — the caller may zero [mnemonic]
     * immediately after this call returns.
     */
    suspend fun saveMnemonic(
        mnemonic: CharArray,
        network: WalletNetwork,
    )

    /**
     * Return the stored mnemonic as a fresh mutable [CharArray]. Callers MUST
     * overwrite it with `Char(0)` when done.
     *
     * @throws com.possatstack.app.wallet.WalletError.NoWallet               if nothing has been stored yet.
     * @throws com.possatstack.app.wallet.WalletError.SecretStoreUnavailable if the Keystore refuses
     *         to decrypt (e.g. key invalidated by device-policy change).
     */
    suspend fun readMnemonic(): CharArray

    /**
     * Return the BIP-39 seed as 64 bytes (PBKDF2-HMAC-SHA512, 2048 rounds, salt
     * `"mnemonic"` + optional passphrase). Reserved for Fase 5 LDK Node, which
     * consumes raw seed bytes rather than the mnemonic words.
     *
     * Callers MUST zero the returned array when done.
     */
    suspend fun readSeedBytes(passphrase: CharArray = CharArray(0)): ByteArray

    /** Persisted network associated with the stored mnemonic, or null if none. */
    fun storedNetwork(): WalletNetwork?

    /** Remove all stored secret material. Irreversible. */
    suspend fun wipe()

    /**
     * Snapshot of the current Keystore protection level. Logged on boot so ops
     * knows whether the device landed on StrongBox, TEE, or software-only.
     */
    fun securityPosture(): SecurityPosture
}
