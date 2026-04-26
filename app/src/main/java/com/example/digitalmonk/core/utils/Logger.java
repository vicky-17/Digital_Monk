package com.example.digitalmonk.core.utils;

import android.util.Log;
import androidx.annotation.Nullable;

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
public class Logger {

    private static final String TAG = Constants.LOG_TAG;

    // Static methods allow us to call Logger.d() without instantiating the class

    public static void d(String msg) {
        Log.d(TAG, msg);
    }

    public static void d(String tag, String msg) {
        Log.d(tag, msg);
    }

    public static void i(String msg) {
        Log.i(TAG, msg);
    }

    public static void i(String tag, String msg) {
        Log.i(tag, msg);
    }

    public static void w(String msg) {
        Log.w(TAG, msg);
    }

    public static void w(String tag, String msg) {
        Log.w(tag, msg);
    }

    public static void e(String msg) {
        Log.e(TAG, msg);
    }

    public static void e(String tag, String msg) {
        Log.e(tag, msg);
    }

    public static void e(String tag, String msg, @Nullable Throwable throwable) {
        if (throwable != null) {
            Log.e(tag, msg, throwable);
        } else {
            Log.e(tag, msg);
        }
    }
}