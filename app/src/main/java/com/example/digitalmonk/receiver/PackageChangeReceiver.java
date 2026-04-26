package com.example.digitalmonk.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

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
public class PackageChangeReceiver extends BroadcastReceiver {

    private static final String TAG = "PackageChangeReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        // Standard Java safety checks to prevent NullPointerExceptions
        if (intent == null || intent.getAction() == null) {
            return;
        }

        String action = intent.getAction();

        // Extract the package name of the app that was installed/removed
        String packageName = null;
        if (intent.getData() != null) {
            packageName = intent.getData().getSchemeSpecificPart();
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
}