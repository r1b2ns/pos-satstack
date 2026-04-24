package com.possatstack.app.wallet.signer

import com.possatstack.app.wallet.WalletNetwork

/**
 * Dedicated secure storage for the wallet mnemonic.
 *
 * This store is **separate** from [com.possatstack.app.wallet.storage.WalletStorage]
 * on purpose: descriptors/cache are read on every boot and do not need a
 * biometric gate, whereas the mnemonic is a high-value secret that should only
 * be decrypted when the user is present.
 *
 * Implementations must:
 *  1. Persist the mnemonic encrypted-at-rest, bound to the device.
 *  2. Prefer StrongBox when available, falling back to TEE.
 *  3. Never expose the mnemonic as a [String] (which the JVM interns and
 *     cannot be zeroed). Return a fresh [CharArray] on each read; callers
 *     fill it with `\u0000` after use.
 *  4. When Fase 3 wires biometric auth, invoke [BiometricAuthenticator] lazily
 *     on read, only if the platform throws `UserNotAuthenticatedException`.
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
     * zero it out (`fill('\u0000')`) when done.
     *
     * @throws com.possatstack.app.wallet.WalletError.NoWallet               if nothing has been stored yet.
     * @throws com.possatstack.app.wallet.WalletError.SecretStoreUnavailable if the Keystore refuses
     *         to decrypt (e.g. user cancelled biometric prompt, key invalidated).
     */
    suspend fun readMnemonic(auth: BiometricAuthenticator): CharArray

    /**
     * Return the BIP-39 seed as 64 bytes (PBKDF2-HMAC-SHA512, 2048 rounds, salt
     * `"mnemonic"` + optional passphrase). Reserved for Fase 5 LDK Node, which
     * consumes raw seed bytes rather than the mnemonic words.
     *
     * Callers MUST zero the returned array when done.
     */
    suspend fun readSeedBytes(
        auth: BiometricAuthenticator,
        passphrase: CharArray = CharArray(0),
    ): ByteArray

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
