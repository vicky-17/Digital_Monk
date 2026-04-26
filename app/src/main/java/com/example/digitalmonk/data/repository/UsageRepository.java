package com.example.digitalmonk.data.repository;

import com.example.digitalmonk.data.model.UsageLog;
import java.util.List;

/**
 * Why we made this file:
 * This interface defines the contract for how the application interacts with
 * app usage data (screen time). By creating a repository interface, the UI
 * components (like the Dashboard or ScreenTimeViewModel) don't need to know
 * if the data is coming from the local Android UsageStatsManager, a local
 * Room database, or the remote Vercel/MongoDB backend.
 *
 * What the file name defines:
 * "Usage" identifies that this handles screen time and app activity logs.
 * "Repository" dictates its role as the data management layer.
 */
public interface UsageRepository {

    // Example methods you will likely implement when building out the data layer:

    // void saveDailyUsage(List<UsageLog> logs);
    // List<UsageLog> getUsageForToday();
    // void syncUsageWithRemoteDatabase();
}