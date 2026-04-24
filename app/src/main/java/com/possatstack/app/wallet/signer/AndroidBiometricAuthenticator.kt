package com.possatstack.app.wallet.signer

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.possatstack.app.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * [BiometricAuthenticator] backed by [BiometricPrompt].
 *
 * Authentication works with **any** biometric the device exposes (fingerprint,
 * face, iris) OR the device credential (PIN/pattern/password) as a fallback.
 * On success, the Android Keystore unlocks all time-bound auth-required keys
 * for the configured validity window (30s in [MnemonicCipher]), so the same
 * prompt covers mnemonic reveal and PSBT signing within that window.
 *
 * Activity access goes through [ActivityHolder] — this class intentionally
 * does not keep a reference to a FragmentActivity.
 */
@Singleton
class AndroidBiometricAuthenticator
    @Inject
    constructor(
        private val activityHolder: ActivityHolder,
    ) : BiometricAuthenticator {
        override suspend fun authenticate(purpose: String): AuthResult {
            val activity =
                activityHolder.current()
                    ?: run {
                        AppLogger.error(TAG, "No FragmentActivity registered — cannot prompt for biometric")
                        return AuthResult.Failed("No activity available")
                    }

            val manager = BiometricManager.from(activity)
            val status = manager.canAuthenticate(ALLOWED_AUTHENTICATORS)
            if (status != BiometricManager.BIOMETRIC_SUCCESS) {
                AppLogger.error(TAG, "Device cannot authenticate (status=$status)")
                return AuthResult.Failed("Biometric/credential not available: $status")
            }

            return withContext(Dispatchers.Main) {
                suspendCancellableCoroutine { continuation ->
                    val executor = ContextCompat.getMainExecutor(activity)
                    val prompt =
                        BiometricPrompt(
                            activity,
                            executor,
                            object : BiometricPrompt.AuthenticationCallback() {
                                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                                    if (continuation.isActive) continuation.resume(AuthResult.Authenticated)
                                }

                                override fun onAuthenticationError(
                                    errorCode: Int,
                                    errString: CharSequence,
                                ) {
                                    AppLogger.info(TAG, "Auth error $errorCode: $errString")
                                    if (!continuation.isActive) return
                                    val outcome =
                                        if (
                                            errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                                            errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                                            errorCode == BiometricPrompt.ERROR_CANCELED
                                        ) {
                                            AuthResult.Cancelled
                                        } else {
                                            AuthResult.Failed(errString.toString())
                                        }
                                    continuation.resume(outcome)
                                }

                                override fun onAuthenticationFailed() {
                                    // Transient — BiometricPrompt keeps showing until
                                    // success or explicit error. No continuation.resume here.
                                }
                            },
                        )

                    val info =
                        BiometricPrompt.PromptInfo.Builder()
                            .setTitle("Confirm it's you")
                            .setSubtitle(purpose)
                            .setAllowedAuthenticators(ALLOWED_AUTHENTICATORS)
                            .build()

                    prompt.authenticate(info)
                }
            }
        }

        private companion object {
            const val TAG = "BiometricAuth"

            /** Strong biometric (Class 3) OR device credential fallback. */
            const val ALLOWED_AUTHENTICATORS =
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
        }
    }
