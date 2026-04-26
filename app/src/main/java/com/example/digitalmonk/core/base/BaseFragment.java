package com.example.digitalmonk.core.base;

import androidx.fragment.app.Fragment;

/**
 * Why we made this file:
 * In Android's Modular UI design, a "Fragment" represents a reusable portion of
 * the user interface that lives inside an Activity.
 *
 * We created a "BaseFragment" to act as a central template for every screen in
 * the Digital Monk app. By having all our fragments extend this class instead
 * of the standard Android Fragment, we ensure that every screen has access
 * to shared logic—such as setting up a common toolbar, handling back-button
 * behavior, or managing memory-safe ViewBinding.
 *
 * What the file name defines:
 * "Base" signifies its role as a parent/template class.
 * "Fragment" identifies it as a sub-section of a screen's UI and lifecycle.
 */
public abstract class BaseFragment extends Fragment {
    // Shared logic for UI components or lifecycle tracking
    // can be implemented here to avoid code duplication.
}