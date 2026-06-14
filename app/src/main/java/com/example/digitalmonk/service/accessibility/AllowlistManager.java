package com.example.digitalmonk.service.accessibility;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * RAM-only allowlist for temporarily permitting specific settings pages.
 * Auto-expires entries after a timeout so the window closes automatically.
 * Thread-safe singleton — accessed from both UI thread and accessibility thread.
 */
public class AllowlistManager {

    private static final long ALLOW_WINDOW_MS = 15_000L; // 15 seconds

    private static final AllowlistManager INSTANCE = new AllowlistManager();
    public static AllowlistManager getInstance() { return INSTANCE; }
    private AllowlistManager() {}

    // Each entry: keyword that identifies the page + expiry timestamp
    private final Set<AllowEntry> allowedPages =
            Collections.synchronizedSet(new HashSet<>());

    public static class AllowEntry {
        public final String keyword;   // text that identifies the page (e.g. "Accessibility")
        public final long expiresAt;

        public AllowEntry(String keyword, long expiresAt) {
            this.keyword   = keyword;
            this.expiresAt = expiresAt;
        }

        @Override public boolean equals(Object o) {
            if (!(o instanceof AllowEntry)) return false;
            return keyword.equals(((AllowEntry) o).keyword);
        }

        @Override public int hashCode() { return keyword.hashCode(); }
    }

    /** Call this BEFORE opening the settings page. */
    public void allow(String pageKeyword) {
        // Remove stale entry first (so expiry resets)
        allowedPages.remove(new AllowEntry(pageKeyword, 0));
        allowedPages.add(new AllowEntry(
                pageKeyword,
                System.currentTimeMillis() + ALLOW_WINDOW_MS
        ));
    }

    /** Call after returning from settings (or on timeout). */
    public void revoke(String pageKeyword) {
        allowedPages.remove(new AllowEntry(pageKeyword, 0));
    }

    public void revokeAll() {
        allowedPages.clear();
    }

    /** Returns true if this keyword is currently allowed AND not expired. */
    public boolean isAllowed(String pageKeyword) {
        long now = System.currentTimeMillis();
        synchronized (allowedPages) {
            for (AllowEntry entry : allowedPages) {
                if (entry.keyword.equals(pageKeyword) && entry.expiresAt > now) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Check if ANY active allowlist entry matches text visible on screen. */
    public boolean isAnyAllowed(String pageText) {
        if (pageText == null) return false;
        long now = System.currentTimeMillis();
        String lower = pageText.toLowerCase();
        synchronized (allowedPages) {
            for (AllowEntry entry : allowedPages) {
                if (entry.expiresAt > now && lower.contains(entry.keyword.toLowerCase())) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Prune expired entries (call periodically from WatchdogService). */
    public void pruneExpired() {
        long now = System.currentTimeMillis();
        synchronized (allowedPages) {
            allowedPages.removeIf(e -> e.expiresAt <= now);
        }
    }
}