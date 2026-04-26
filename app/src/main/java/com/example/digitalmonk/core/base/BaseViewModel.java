package com.example.digitalmonk.core.base;

import androidx.lifecycle.ViewModel;

/**
 * Why we made this file:
 * In the MVVM (Model-View-ViewModel) architecture, the ViewModel is responsible for
 * preparing and managing the data for an Activity or Fragment. It is "lifecycle-aware,"
 * meaning it survives configuration changes like screen rotations.
 *
 * By creating a "BaseViewModel," we establish a foundation for all business logic
 * in the app. This allows us to implement universal features once—such as a
 * common "isLoading" LiveData state, error handling logic, or connection to
 * a repository—ensuring that every specific ViewModel (like DashboardViewModel)
 * follows the same pattern.
 *
 * What the file name defines:
 * "Base" indicates it is a parent template.
 * "ViewModel" defines its role in the architecture as the data provider and
 * logic holder for the UI.
 */
public abstract class BaseViewModel extends ViewModel {
    // Shared observable data (like Error Messages or Loading States)
    // can be defined here for all ViewModels to use.
}