package com.example.digitalmonk.data.repository;

import com.example.digitalmonk.data.model.ParentProfile;

/**
 * Why we made this file:
 * Just like the AppRuleRepository, this interface defines the contract for managing
 * the parent's profile data. It acts as an abstraction layer between the UI
 * (like a Settings screen) and the data source (like SharedPreferences, Room,
 * or a remote MongoDB database).
 *
 * What the file name defines:
 * "Profile" refers to the user account data (e.g., the ParentProfile model).
 * "Repository" is the architectural pattern used to manage and isolate data access.
 */
public interface ProfileRepository {

    // Example methods you might implement later to fetch or update profile data:

    // ParentProfile getProfile();
    // void updateProfileName(String newName);
    // boolean authenticatePin(String enteredPin);
}