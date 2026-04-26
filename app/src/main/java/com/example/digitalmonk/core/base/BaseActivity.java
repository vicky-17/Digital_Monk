package com.example.digitalmonk.core.base;

import androidx.activity.ComponentActivity;

/**
 * Why we made this file:
 * In Android development, a "Base" class is a structural pattern used to provide
 * common functionality to all child activities. Instead of writing the same setup
 * code (like logging, analytics, or theme application) in every single Activity,
 * we write it once here.
 * * What the file name defines:
 * "Base" indicates this is a parent template class.
 * "Activity" indicates it is part of the UI controller layer of the Android framework.
 * Since it is 'abstract', it cannot be launched on its own; it exists only to be
 * extended by other classes like MainActivity or PinSetupActivity.
 */
public abstract class BaseActivity extends ComponentActivity {
    // Shared logic for all activities can be added here later
    // (e.g., common lifecycle logging or permission checks)
}