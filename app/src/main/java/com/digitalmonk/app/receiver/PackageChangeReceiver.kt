package com.digitalmonk.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Why we made this file:
 * In a parental control app, it is critical to monitor when new applications are
 * installed or removed from the device. If a child downloads a new app
 * (like a game or social media platform), this receiver will be triggered.
 * * Eventually, you will use this class to automatically block new apps by default,
 * apply a standard screen time limit, or immediately notify the parent's dashboard
 * on your Vercel/MongoDB backend.
 * 
 * What the file name defines:
 * "Package" is the Android system's term for an application (e.g., com.whatsapp).
 * "Change" signifies an installation, removal, or update of that package.
 */
class PackageChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        // Standard Java safety checks to prevent NullPointerExceptions
        if (intent == null || intent.getAction() == null) {
            return
        }

        val action = intent.getAction()

        // Extract the package name of the app that was installed/removed
        var packageName: String? = null
        if (intent.getData() != null) {
            packageName = intent.getData()!!.getSchemeSpecificPart()
        }

        // TODO: Handle app install/uninstall events.

        /* Example Implementation Logic for later:
        if (Intent.ACTION_PACKAGE_ADDED.equals(action) && packageName != null) {
            Log.i(TAG, "New app installed: " + packageName);

            // 1. Save new app to local Room Database
            // 2. Upload to Vercel/MongoDB backend via API
            // 3. Check PrefsManager if "Block New Apps by Default" is active
        } else if (Intent.ACTION_PACKAGE_REMOVED.equals(action) && packageName != null) {
            Log.i(TAG, "App removed: " + packageName);

            // 1. Clean up local database rules for this app
        }
        */
    }

    companion object {
        private const val TAG = "PackageChangeReceiver"
    }
}