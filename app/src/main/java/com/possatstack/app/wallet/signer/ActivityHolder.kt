package com.possatstack.app.wallet.signer

import android.app.Activity
import java.lang.ref.WeakReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hilt-provided singleton that hands the currently-resumed [Activity] to
 * components that need it for platform APIs (TAPSIGNER NFC reader mode).
 *
 * The activity registers itself on `onResume` and clears on `onPause`, so
 * components that need to interact with the foreground activity can ask
 * this holder at call time without ever holding an Activity reference
 * directly. If no activity is registered when [current] is called, the
 * caller gets `null` and should surface an error.
 */
@Singleton
class ActivityHolder
    @Inject
    constructor() {
        @Volatile
        private var activityRef: WeakReference<Activity>? = null

        fun attach(activity: Activity) {
            activityRef = WeakReference(activity)
        }

        fun detach(activity: Activity) {
            val current = activityRef?.get()
            if (current == null || current === activity) {
                activityRef = null
            }
        }

        fun current(): Activity? = activityRef?.get()
    }
