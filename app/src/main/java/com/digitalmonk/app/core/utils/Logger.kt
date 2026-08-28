package com.digitalmonk.app.core.utils

import android.util.Log

/**
 * Why we made this file:
 * In a professional application, you never want to call 'Log.d' directly
 * throughout your codebase. If you do, and you later want to hide logs
 * in the release version or send errors to a service like Firebase
 * Crashlytics, you would have to change hundreds of files.
 * 
 * This "Wrapper" class centralizes all logging. By calling Logger.d()
 * instead of Log.d(), we can change how the entire app handles data
 * just by editing this one file.
 * 
 * What the file name defines:
 * "Logger" is a standard utility name for a class that handles
 * system output and diagnostic information.
 */
object Logger {
    private val TAG = Constants.LOG_TAG

    // Static methods allow us to call Logger.d() without instantiating the class
    fun d(msg: String) {
        Log.d(TAG, msg)
    }

    fun d(tag: String?, msg: String) {
        Log.d(tag, msg)
    }

    fun i(msg: String) {
        Log.i(TAG, msg)
    }

    fun i(tag: String?, msg: String) {
        Log.i(tag, msg)
    }

    fun w(msg: String) {
        Log.w(TAG, msg)
    }

    fun w(tag: String?, msg: String) {
        Log.w(tag, msg)
    }

    fun e(msg: String) {
        Log.e(TAG, msg)
    }

    fun e(tag: String?, msg: String) {
        Log.e(tag, msg)
    }

    fun e(tag: String?, msg: String, throwable: Throwable?) {
        if (throwable != null) {
            Log.e(tag, msg, throwable)
        } else {
            Log.e(tag, msg)
        }
    }
}