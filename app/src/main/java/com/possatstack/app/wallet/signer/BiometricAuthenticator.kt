package com.possatstack.app.wallet.signer

/**
 * Abstraction for gating access to the mnemonic behind a biometric/PIN prompt.
 *
 * Implementations bind this to the Android `BiometricPrompt` API, which requires
 * a `FragmentActivity`. Activity-less contexts (WorkManager, tests) can use a
 * null-object implementation that always returns [AuthResult.Authenticated].
 *
 * The prompt is opt-in per call: the [SignerSecretStore] asks the authenticator
 * only when the underlying Keystore key actually demands it (caught via
 * `UserNotAuthenticatedException`). Callers should not pre-authenticate.
 */
interface BiometricAuthenticator {
    /** Title shown to the user, e.g. "Sign transaction" or "Reveal seed phrase". */
    suspend fun authenticate(purpose: String): AuthResult
}

sealed interface AuthResult {
    data object Authenticated : AuthResult

    data object Cancelled : AuthResult

    data class Failed(val reason: String) : AuthResult
}

/**
 * Fallback authenticator that skips any prompt. Used in tests and on Android
 * code paths that run before the Biometric/Keystore wiring exists (Fase 1).
 * Once Fase 3 wires the real prompt via a `FragmentActivity`, replace the
 * binding in `WalletModule` with the activity-backed implementation.
 */
class NoOpBiometricAuthenticator : BiometricAuthenticator {
    override suspend fun authenticate(purpose: String): AuthResult = AuthResult.Authenticated
}
