package com.example.digitalmonk.data.local.prefs;

import android.content.Context;

/**
 * Why we made this file:
 * In a parental control app, security is a high priority. While standard
 * SharedPreferences are fine for UI settings, sensitive data—like the
 * parent's PIN or authentication tokens—should be stored more securely.
 *
 * This class is designed to wrap "EncryptedSharedPreferences" (or a similar
 * secure storage mechanism). It ensures that even if someone gains root
 * access to the device, they cannot easily read the plain-text PIN
 * of the parent to bypass the app's restrictions.
 *
 * What the file name defines:
 * "Secure" indicates the use of hardware-backed or software-based encryption.
 * "Prefs" identifies its role in managing persistent user preferences.
 */
public class SecurePrefs {

    private final Context context;

    public SecurePrefs(Context context) {
        this.context = context;
    }

    // Logic for EncryptedSharedPreferences will be implemented here
    // to provide an extra layer of protection for parental security.
}