package com.digitalmonk.app.service.accessibility

import java.util.Collections
import java.util.Locale

/**
 * RAM-only allowlist for temporarily permitting specific settings pages.
 * Auto-expires entries after a timeout so the window closes automatically.
 * Thread-safe singleton — accessed from both UI thread and accessibility thread.
 */
class AllowlistManager private constructor() {
    // Each entry: keyword that identifies the page + expiry timestamp
    private val allowedPages: MutableSet<AllowEntry> = Collections.synchronizedSet<AllowEntry?>(
        HashSet<AllowEntry?>()
    )

    class AllowEntry(// text that identifies the page (e.g. "Accessibility")
        val keyword: String, val expiresAt: Long
    ) {
        override fun equals(o: Any?): Boolean {
            if (o !is AllowEntry) return false
            return keyword == o.keyword
        }

        override fun hashCode(): Int {
            return keyword.hashCode()
        }
    }

    /** Call this BEFORE opening the settings page.  */
    fun allow(pageKeyword: String) {
        // Remove stale entry first (so expiry resets)
        allowedPages.remove(AllowEntry(pageKeyword, 0))
        allowedPages.add(
            AllowEntry(
                pageKeyword,
                System.currentTimeMillis() + ALLOW_WINDOW_MS
            )
        )
    }

    /** Call after returning from settings (or on timeout).  */
    fun revoke(pageKeyword: String) {
        allowedPages.remove(AllowEntry(pageKeyword, 0))
    }

    fun revokeAll() {
        allowedPages.clear()
    }

    /** Returns true if this keyword is currently allowed AND not expired.  */
    fun isAllowed(pageKeyword: String?): Boolean {
        val now = System.currentTimeMillis()
        synchronized(allowedPages) {
            for (entry in allowedPages) {
                if (entry.keyword == pageKeyword && entry.expiresAt > now) {
                    return true
                }
            }
        }
        return false
    }

    /** Check if ANY active allowlist entry matches text visible on screen.  */
    fun isAnyAllowed(pageText: String?): Boolean {
        if (pageText == null) return false
        val now = System.currentTimeMillis()
        val lower = pageText.lowercase(Locale.getDefault())
        synchronized(allowedPages) {
            for (entry in allowedPages) {
                if (entry.expiresAt > now && lower.contains(entry.keyword.lowercase(Locale.getDefault()))) {
                    return true
                }
            }
        }
        return false
    }

    /** Prune expired entries (call periodically from WatchdogService).  */
    fun pruneExpired() {
        val now = System.currentTimeMillis()
        synchronized(allowedPages) {
            allowedPages.removeIf { e: AllowEntry? -> e!!.expiresAt <= now }
        }
    }

    companion object {
        private const val ALLOW_WINDOW_MS = 15000L // 15 seconds

        private val _instance: AllowlistManager by lazy { AllowlistManager() }

        @JvmStatic
        fun getInstance(): AllowlistManager = _instance
    }
}