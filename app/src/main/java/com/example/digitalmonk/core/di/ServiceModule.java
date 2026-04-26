package com.example.digitalmonk.core.di;

/**
 * Why we made this file:
 * This file is intended for Dependency Injection (DI). In a large app like
 * Digital Monk, different parts of the system (like the VPN service,
 * Database, or Repositories) need to be shared across the app.
 * * Instead of creating these objects manually everywhere (which is hard to test
 * and manage), we use a DI framework like Hilt or Dagger. This "Module"
 * acts as a factory that provides those objects to the rest of the application.
 *
 * What the file name defines:
 * "Service" indicates it handles the injection of background services or utilities.
 * "Module" is a standard term in DI frameworks for a class that contributes
 * to the object graph.
 */

// TODO: Add Hilt annotations (@Module and @InstallIn) once the
// Hilt dependency is added to your build.gradle.
public class ServiceModule {
    // Once you add Hilt, this will contain @Provides methods
    // to initialize your app's core services.
}