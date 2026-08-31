package com.curbme.app.service.vpn.blocklist

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.FileInputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.concurrent.Volatile

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
class BlocklistManager(context: Context) {
    // Using ConcurrentHashMap.newKeySet() instead of a standard HashSet ensures
    // thread safety when the background updater and the VPN network thread access it simultaneously.
    private val combinedBlocklist: MutableSet<String?> = ConcurrentHashMap.newKeySet<String?>()

    @Volatile
    private var isLoaded = false
    private val context: Context

    // Replaces Kotlin Coroutines (Dispatchers.IO)
    private val ioExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    init {
        this.context = context.getApplicationContext()
    }

    /**
     * Initializes the blocklist. Call this when the VPN service starts.
     * In Java, we use a callback (Runnable) since we don't have Kotlin's 'suspend' keyword.
     */
    fun initialize(onComplete: Runnable?) {
        if (isLoaded) {
            if (onComplete != null) onComplete.run()
            return
        }

        ioExecutor.execute(Runnable {
            // Always start with the hard-coded seed list
            combinedBlocklist.addAll(PornDomainBlocklist.domains)
            Log.d(TAG, "Seed list loaded: " + PornDomainBlocklist.domains.size + " domains")

            // Load cached remote list from disk
            loadCachedRemoteList()

            // Fetch updated remote list if enough time has passed
            if (shouldUpdateRemoteList()) {
                fetchRemoteBlocklists()
            }

            isLoaded = true
            Log.i(TAG, "✅ Blocklist ready: " + combinedBlocklist.size + " total domains")
            if (onComplete != null) {
                onComplete.run()
            }
        })
    }

    /**
     * Fast check if a domain should be blocked.
     */
    fun isBlocked(domain: String?): Boolean {
        if (domain == null || domain.isEmpty()) return false

        val lower = domain.lowercase(Locale.getDefault())

        // 1. Check exact match O(1)
        if (combinedBlocklist.contains(lower)) return true

        // 2. Check subdomain match O(N) (e.g., "www.pornhub.com" matches "pornhub.com")
        for (blocked in combinedBlocklist) {
            if (lower.endsWith("." + blocked)) {
                return true
            }
        }
        return false
    }

    fun addCustomDomain(domain: String?) {
        if (domain != null) {
            combinedBlocklist.add(domain.lowercase(Locale.getDefault()).trim { it <= ' ' })
            Log.d(TAG, "Custom domain added: " + domain)
        }
    }

    fun removeCustomDomain(domain: String?) {
        if (domain != null) {
            combinedBlocklist.remove(domain.lowercase(Locale.getDefault()).trim { it <= ' ' })
        }
    }

    // ── Private I/O Methods ───────────────────────────────────────────────────
    private fun loadCachedRemoteList() {
        val file = context.getFileStreamPath(CACHE_FILE_NAME)
        if (!file.exists()) return

        // try-with-resources automatically closes the streams to prevent memory leaks
        try {
            BufferedReader(InputStreamReader(FileInputStream(file))).use { reader ->
                var line: String?
                var count = 0
                while ((reader.readLine().also { line = it }) != null) {
                    val domain = parseHostsLine(line!!)
                    if (domain != null) {
                        combinedBlocklist.add(domain)
                        count++
                    }
                }
                Log.d(TAG, "Loaded " + count + " domains from cache")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load cached blocklist: " + e.message)
        }
    }

    private fun fetchRemoteBlocklists() {
        var totalFetched = 0

        for (urlString in REMOTE_BLOCKLIST_URLS) {
            var connection: HttpURLConnection? = null
            try {
                Log.d(TAG, "Fetching blocklist from: " + urlString)
                val url = URL(urlString)
                connection = url.openConnection() as HttpURLConnection?
                connection!!.setConnectTimeout(10000)
                connection.setReadTimeout(30000)

                val newDomains: MutableSet<String?> = ConcurrentHashMap.newKeySet<String?>()

                BufferedReader(InputStreamReader(connection.getInputStream())).use { reader ->
                    var line: String?
                    while ((reader.readLine().also { line = it }) != null) {
                        val domain = parseHostsLine(line!!)
                        if (domain != null) {
                            newDomains.add(domain)
                        }
                    }
                }
                combinedBlocklist.addAll(newDomains)
                totalFetched += newDomains.size

                // Cache to disk immediately
                cacheBlocklist(newDomains)
                Log.i(TAG, "Fetched " + newDomains.size + " domains from " + urlString)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to fetch from " + urlString + ": " + e.message)
            } finally {
                if (connection != null) connection.disconnect()
            }
        }

        if (totalFetched > 0) {
            markUpdateTime()
            Log.i(TAG, "Remote update complete: " + totalFetched + " new domains")
        }
    }

    private fun cacheBlocklist(domains: MutableSet<String?>) {
        try {
            BufferedWriter(
                OutputStreamWriter(
                    context.openFileOutput(CACHE_FILE_NAME, Context.MODE_PRIVATE)
                )
            ).use { writer ->
                for (domain in domains) {
                    writer.write("0.0.0.0 " + domain + "\n")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to cache blocklist: " + e.message)
        }
    }

    private fun parseHostsLine(line: String): String? {
        val trimmed = line.trim { it <= ' ' }
        if (trimmed.isEmpty() || trimmed.startsWith("#")) return null

        val parts: Array<String?> =
            trimmed.split("\\s+".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        if (parts.size < 2) return null

        val ip = parts[0]
        if ("0.0.0.0" != ip && "127.0.0.1" != ip) return null

        val domain = parts[1]!!.lowercase(Locale.getDefault())

        // Skip localhost and invalid entries
        if ("localhost" == domain || domain.contains("#") || domain.length < 4) return null

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

    companion object {
        private const val TAG = "BlocklistManager"
        private const val PREFS_NAME = "blocklist_prefs"
        private const val KEY_LAST_UPDATE = "last_update_epoch"
        private val UPDATE_INTERVAL_MS = 24L * 60 * 60 * 1000 // 24 hours
        private const val CACHE_FILE_NAME = "blocklist_cache.txt"

        private val REMOTE_BLOCKLIST_URLS = mutableListOf<String?>(
            "https://raw.githubusercontent.com/StevenBlack/hosts/master/alternates/porn/hosts" // "https://raw.githubusercontent.com/hagezi/dns-blocklists/main/hosts/porn.txt"
        )
    }
}