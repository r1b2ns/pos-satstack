package com.possatstack.app.util

import android.util.Log
import com.possatstack.app.BuildConfig

/**
 * Centralised logger for the application.
 *
 * All log output is suppressed in release builds — only emitted when
 * [BuildConfig.DEBUG] is true, so no sensitive data leaks to production logs.
 *
 * Usage:
 *   AppLogger.info("WalletRepo", "Wallet loaded successfully")
 *   AppLogger.error("WalletRepo", "Sync failed", exception)
 */
object AppLogger {
    fun info(
        tag: String,
        message: String,
    ) {
        if (BuildConfig.DEBUG) Log.i(tag, message)
    }

    fun warning(
        tag: String,
        message: String,
        throwable: Throwable? = null,
    ) {
        if (BuildConfig.DEBUG) Log.w(tag, message, throwable)
    }

    fun error(
        tag: String,
        message: String,
        throwable: Throwable? = null,
    ) {
        if (BuildConfig.DEBUG) Log.e(tag, message, throwable)
    }
}
