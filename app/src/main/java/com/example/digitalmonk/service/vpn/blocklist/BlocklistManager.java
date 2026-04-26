package com.example.digitalmonk.service.vpn.blocklist;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Why we made this file:
 * A DNS-level VPN needs to know exactly which websites to block in real-time.
 * Checking a database for every single network packet is too slow. This manager
 * loads all blocked domains (from local seeds, cached files, and remote servers)
 * directly into device RAM (Memory) for lightning-fast O(1) lookups.
 *
 * What the file name defines:
 * "Blocklist" identifies the data being managed (forbidden domains).
 * "Manager" means it controls the fetching, caching, and querying of this data.
 */
public class BlocklistManager {

    private static final String TAG = "BlocklistManager";
    private static final String PREFS_NAME = "blocklist_prefs";
    private static final String KEY_LAST_UPDATE = "last_update_epoch";
    private static final long UPDATE_INTERVAL_MS = 24L * 60 * 60 * 1000; // 24 hours
    private static final String CACHE_FILE_NAME = "blocklist_cache.txt";

    private static final List<String> REMOTE_BLOCKLIST_URLS = Arrays.asList(
            "https://raw.githubusercontent.com/StevenBlack/hosts/master/alternates/porn/hosts"
            // "https://raw.githubusercontent.com/hagezi/dns-blocklists/main/hosts/porn.txt"
    );

    // Using ConcurrentHashMap.newKeySet() instead of a standard HashSet ensures
    // thread safety when the background updater and the VPN network thread access it simultaneously.
    private final Set<String> combinedBlocklist = ConcurrentHashMap.newKeySet();
    private volatile boolean isLoaded = false;
    private final Context context;

    // Replaces Kotlin Coroutines (Dispatchers.IO)
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();

    public BlocklistManager(Context context) {
        this.context = context.getApplicationContext();
    }

    /**
     * Initializes the blocklist. Call this when the VPN service starts.
     * In Java, we use a callback (Runnable) since we don't have Kotlin's 'suspend' keyword.
     */
    public void initialize(Runnable onComplete) {
        if (isLoaded) {
            if (onComplete != null) onComplete.run();
            return;
        }

        ioExecutor.execute(() -> {
            // Always start with the hard-coded seed list
            combinedBlocklist.addAll(PornDomainBlocklist.getDomains());
            Log.d(TAG, "Seed list loaded: " + PornDomainBlocklist.getDomains().size() + " domains");

            // Load cached remote list from disk
            loadCachedRemoteList();

            // Fetch updated remote list if enough time has passed
            if (shouldUpdateRemoteList()) {
                fetchRemoteBlocklists();
            }

            isLoaded = true;
            Log.i(TAG, "✅ Blocklist ready: " + combinedBlocklist.size() + " total domains");

            if (onComplete != null) {
                onComplete.run();
            }
        });
    }

    /**
     * Fast check if a domain should be blocked.
     */
    public boolean isBlocked(String domain) {
        if (domain == null || domain.isEmpty()) return false;

        String lower = domain.toLowerCase();

        // 1. Check exact match O(1)
        if (combinedBlocklist.contains(lower)) return true;

        // 2. Check subdomain match O(N) (e.g., "www.pornhub.com" matches "pornhub.com")
        for (String blocked : combinedBlocklist) {
            if (lower.endsWith("." + blocked)) {
                return true;
            }
        }
        return false;
    }

    public void addCustomDomain(String domain) {
        if (domain != null) {
            combinedBlocklist.add(domain.toLowerCase().trim());
            Log.d(TAG, "Custom domain added: " + domain);
        }
    }

    public void removeCustomDomain(String domain) {
        if (domain != null) {
            combinedBlocklist.remove(domain.toLowerCase().trim());
        }
    }

    // ── Private I/O Methods ───────────────────────────────────────────────────

    private void loadCachedRemoteList() {
        File file = context.getFileStreamPath(CACHE_FILE_NAME);
        if (!file.exists()) return;

        // try-with-resources automatically closes the streams to prevent memory leaks
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file)))) {
            String line;
            int count = 0;
            while ((line = reader.readLine()) != null) {
                String domain = parseHostsLine(line);
                if (domain != null) {
                    combinedBlocklist.add(domain);
                    count++;
                }
            }
            Log.d(TAG, "Loaded " + count + " domains from cache");
        } catch (Exception e) {
            Log.w(TAG, "Failed to load cached blocklist: " + e.getMessage());
        }
    }

    private void fetchRemoteBlocklists() {
        int totalFetched = 0;

        for (String urlString : REMOTE_BLOCKLIST_URLS) {
            HttpURLConnection connection = null;
            try {
                Log.d(TAG, "Fetching blocklist from: " + urlString);
                URL url = new URL(urlString);
                connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(10_000);
                connection.setReadTimeout(30_000);

                Set<String> newDomains = ConcurrentHashMap.newKeySet();

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        String domain = parseHostsLine(line);
                        if (domain != null) {
                            newDomains.add(domain);
                        }
                    }
                }

                combinedBlocklist.addAll(newDomains);
                totalFetched += newDomains.size();

                // Cache to disk immediately
                cacheBlocklist(newDomains);
                Log.i(TAG, "Fetched " + newDomains.size() + " domains from " + urlString);

            } catch (Exception e) {
                Log.w(TAG, "Failed to fetch from " + urlString + ": " + e.getMessage());
            } finally {
                if (connection != null) connection.disconnect();
            }
        }

        if (totalFetched > 0) {
            markUpdateTime();
            Log.i(TAG, "Remote update complete: " + totalFetched + " new domains");
        }
    }

    private void cacheBlocklist(Set<String> domains) {
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                context.openFileOutput(CACHE_FILE_NAME, Context.MODE_PRIVATE)))) {
            for (String domain : domains) {
                writer.write("0.0.0.0 " + domain + "\n");
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to cache blocklist: " + e.getMessage());
        }
    }

    private String parseHostsLine(String line) {
        String trimmed = line.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("#")) return null;

        String[] parts = trimmed.split("\\s+");
        if (parts.length < 2) return null;

        String ip = parts[0];
        if (!"0.0.0.0".equals(ip) && !"127.0.0.1".equals(ip)) return null;

        String domain = parts[1].toLowerCase();

        // Skip localhost and invalid entries
        if ("localhost".equals(domain) || domain.contains("#") || domain.length() < 4) return null;

        return domain;
    }

    private boolean shouldUpdateRemoteList() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        long lastUpdate = prefs.getLong(KEY_LAST_UPDATE, 0L);
        return System.currentTimeMillis() - lastUpdate > UPDATE_INTERVAL_MS;
    }

    private void markUpdateTime() {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_LAST_UPDATE, System.currentTimeMillis())
                .apply();
    }
}