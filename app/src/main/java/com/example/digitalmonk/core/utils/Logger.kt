package com.example.digitalmonk.core.utils

import android.util.Log

/**
 * Centralized logger. In release builds, debug logs are stripped by ProGuard.
 * Swap implementation here to integrate Crashlytics / Timber without
 * touching every call site.
 */
object Logger {

    private const val TAG = Constants.LOG_TAG

    fun d(tag: String = TAG, msg: String) = Log.d(tag, msg)
    fun i(tag: String = TAG, msg: String) = Log.i(tag, msg)
    fun w(tag: String = TAG, msg: String) = Log.w(tag, msg)
    fun e(tag: String = TAG, msg: String, throwable: Throwable? = null) {
        if (throwable != null) Log.e(tag, msg, throwable)
        else Log.e(tag, msg)
    }
}