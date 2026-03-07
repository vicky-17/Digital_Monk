package com.example.digitalmonk.service.vpn.blocklist

import android.content.Context
import android.util.Log
import com.example.digitalmonk.service.vpn.blocklist.PornDomainBlocklist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

/**
 * Manages all domain blocklists.
 *
 * Sources (in priority order):
 *  1. [PornDomainBlocklist]  — hard-coded seed list, always available
 *  2. Remote blocklist files — downloaded from trusted sources (StevenBlack, etc.)
 *  3. Custom user domains    — parent-defined domains (from Room DB)
 *
 * The combined set is stored in memory for fast O(1) lookup during DNS filtering.
 *
 * ── Remote blocklist format ──────────────────────────────────────────────────
 * Standard hosts file format:
 *   0.0.0.0 pornhub.com
 *   0.0.0.0 xvideos.com
 *   # comment lines are ignored
 */
class BlocklistManager(private val context: Context) {

    private val combinedBlocklist = mutableSetOf<String>()
    private var isLoaded = false

    companion object {
        private const val TAG = "BlocklistManager"
        private const val PREFS_NAME = "blocklist_prefs"
        private const val KEY_LAST_UPDATE = "last_update_epoch"
        private const val UPDATE_INTERVAL_MS = 24L * 60 * 60 * 1000  // 24 hours

        /**
         * Trusted blocklist sources.
         * These are well-maintained, community-curated hosts files.
         *
         * StevenBlack's consolidated list:
         *   https://raw.githubusercontent.com/StevenBlack/hosts/master/alternates/porn/hosts
         *
         * This list contains 30,000+ adult domains and is updated regularly.
         */
        private val REMOTE_BLOCKLIST_URLS = listOf(
            // StevenBlack porn-only blocklist
            "https://raw.githubusercontent.com/StevenBlack/hosts/master/alternates/porn/hosts"
            // Add more sources here:
            // "https://raw.githubusercontent.com/hagezi/dns-blocklists/main/hosts/porn.txt"
        )
    }

    /**
     * Initializes the blocklist from all sources.
     * Call this when the VPN service starts.
     */
    suspend fun initialize() = withContext(Dispatchers.IO) {
        if (isLoaded) return@withContext

        // Always start with the hard-coded seed list
        combinedBlocklist.addAll(PornDomainBlocklist.domains)
        Log.d(TAG, "Seed list loaded: ${PornDomainBlocklist.domains.size} domains")

        // Load cached remote list from disk
        loadCachedRemoteList()

        // Fetch updated remote list if enough time has passed
        if (shouldUpdateRemoteList()) {
            fetchRemoteBlocklists()
        }

        isLoaded = true
        Log.i(TAG, "✅ Blocklist ready: ${combinedBlocklist.size} total domains")
    }

    /**
     * Fast O(1) check if a domain should be blocked.
     * Falls back to seed list if remote list hasn't loaded yet.
     */
    fun isBlocked(domain: String): Boolean {
        val lower = domain.lowercase()

        // Check exact match
        if (lower in combinedBlocklist) return true

        // Check subdomain match (e.g., "www.pornhub.com" → matches "pornhub.com")
        return combinedBlocklist.any { blocked ->
            lower.endsWith(".$blocked")
        }
    }

    /**
     * Adds a custom domain to the in-memory blocklist.
     * Persist this to Room DB separately for survival across restarts.
     */
    fun addCustomDomain(domain: String) {
        combinedBlocklist.add(domain.lowercase().trim())
        Log.d(TAG, "Custom domain added: $domain")
    }

    fun removeCustomDomain(domain: String) {
        combinedBlocklist.remove(domain.lowercase().trim())
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private fun loadCachedRemoteList() {
        try {
            val file = context.getFileStreamPath("blocklist_cache.txt")
            if (!file.exists()) return

            var count = 0
            file.bufferedReader().forEachLine { line ->
                val domain = parseHostsLine(line)
                if (domain != null) {
                    combinedBlocklist.add(domain)
                    count++
                }
            }
            Log.d(TAG, "Loaded $count domains from cache")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load cached blocklist: ${e.message}")
        }
    }

    private suspend fun fetchRemoteBlocklists() = withContext(Dispatchers.IO) {
        var totalFetched = 0

        for (url in REMOTE_BLOCKLIST_URLS) {
            try {
                Log.d(TAG, "Fetching blocklist from: $url")
                val connection = URL(url).openConnection()
                connection.connectTimeout = 10_000
                connection.readTimeout    = 30_000

                val newDomains = mutableSetOf<String>()
                connection.getInputStream().bufferedReader().forEachLine { line ->
                    val domain = parseHostsLine(line)
                    if (domain != null) newDomains.add(domain)
                }

                combinedBlocklist.addAll(newDomains)
                totalFetched += newDomains.size

                // Cache to disk
                cacheBlocklist(newDomains)

                Log.i(TAG, "Fetched ${newDomains.size} domains from $url")

            } catch (e: Exception) {
                Log.w(TAG, "Failed to fetch from $url: ${e.message}")
                // Continue with next URL — don't crash if one fails
            }
        }

        if (totalFetched > 0) {
            markUpdateTime()
            Log.i(TAG, "Remote update complete: $totalFetched new domains")
        }
    }

    private fun cacheBlocklist(domains: Set<String>) {
        try {
            context.openFileOutput("blocklist_cache.txt", Context.MODE_PRIVATE).bufferedWriter().use { writer ->
                domains.forEach { writer.write("0.0.0.0 $it\n") }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to cache blocklist: ${e.message}")
        }
    }

    /**
     * Parses a single line from a hosts file.
     * Returns the domain if it's a block entry, null otherwise.
     * Format: "0.0.0.0 domain.com" or "127.0.0.1 domain.com"
     */
    private fun parseHostsLine(line: String): String? {
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("#")) return null

        val parts = trimmed.split(Regex("\\s+"))
        if (parts.size < 2) return null

        val ip = parts[0]
        if (ip != "0.0.0.0" && ip != "127.0.0.1") return null

        val domain = parts[1].lowercase()

        // Skip localhost and invalid entries
        if (domain == "localhost" || domain.contains("#") || domain.length < 4) return null

        return domain
    }

    private fun shouldUpdateRemoteList(): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastUpdate = prefs.getLong(KEY_LAST_UPDATE, 0L)
        return System.currentTimeMillis() - lastUpdate > UPDATE_INTERVAL_MS
    }

    private fun markUpdateTime() {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_UPDATE, System.currentTimeMillis())
            .apply()
    }
}