package com.possatstack.app.wallet.signer

import androidx.fragment.app.FragmentActivity
import java.lang.ref.WeakReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hilt-provided singleton that hands the currently-resumed
 * [FragmentActivity] to [AndroidBiometricAuthenticator].
 *
 * BiometricPrompt needs a [FragmentActivity] to attach its DialogFragment to,
 * but ViewModels/Services must stay context-agnostic. The activity registers
 * itself on `onStart` and clears on `onStop`, so components that need to
 * prompt the user can ask this holder at call time without ever seeing the
 * activity via a direct reference.
 *
 * If no activity is currently registered when [current] is called, the
 * caller gets `null` and should surface an error — it means the app is in
 * the background or a service is misbehaving.
 */
@Singleton
class ActivityHolder
    @Inject
    constructor() {
        @Volatile
        private var activityRef: WeakReference<FragmentActivity>? = null

        fun attach(activity: FragmentActivity) {
            activityRef = WeakReference(activity)
        }

        fun detach(activity: FragmentActivity) {
            val current = activityRef?.get()
            if (current == null || current === activity) {
                activityRef = null
            }
        }

        fun current(): FragmentActivity? = activityRef?.get()
    }
