package com.example.digitalmonk.service.vpn

import android.util.Log
import com.example.digitalmonk.data.local.prefs.PrefsManager
import com.example.digitalmonk.service.vpn.blocklist.PornDomainBlocklist

/**
 * The brain of the DNS filter.
 *
 * For each DNS query, this engine returns a [FilterDecision] telling
 * [DnsVpnService] what to do with it.
 *
 * Decision priority:
 *  1. SAFESEARCH_REDIRECT  — Redirect Google/Bing/YouTube to their SafeSearch IPs
 *  2. BLOCK (NXDOMAIN)     — Blocked domain (porn, custom blocklist)
 *  3. ALLOW (forward)      — Send to upstream DNS (8.8.8.8)
 */
class DnsFilterEngine(private val prefs: PrefsManager) {

    sealed class FilterDecision {
        /** Forward to upstream DNS normally */
        object Allow : FilterDecision()

        /** Return NXDOMAIN — domain does not exist */
        object Block : FilterDecision()

        /** Return a fake A record pointing to the SafeSearch IP */
        data class SafeSearchRedirect(val redirectIp: String) : FilterDecision()
    }

    fun decide(domain: String, queryType: Int): FilterDecision {
        // ── 1. SafeSearch enforcement ─────────────────────────────────────────
        if (prefs.enforceSafeSearch || prefs.safeSearchEnabled) {
            val safeIp = SAFESEARCH_DOMAINS[domain]
            if (safeIp != null && queryType == DnsPacketParser.TYPE_A) {
                Log.d(TAG, "🔍 SafeSearch redirect: $domain → $safeIp")
                return FilterDecision.SafeSearchRedirect(safeIp)
            }
        }

        // ── 2. Porn / adult content blocking ─────────────────────────────────
        if (prefs.blockPorn || prefs.safeSearchEnabled) {
            if (PornDomainBlocklist.isBlocked(domain)) {
                Log.d(TAG, "🚫 Porn blocked: $domain")
                return FilterDecision.Block
            }
        }

        // ── 3. Custom user-defined domain blocklist ───────────────────────────
        if (isCustomBlocked(domain)) {
            Log.d(TAG, "🚫 Custom blocked: $domain")
            return FilterDecision.Block
        }

        return FilterDecision.Allow
    }

    private fun isCustomBlocked(domain: String): Boolean {
        // TODO: Load from Room DB (custom domains parent adds manually)
        return false
    }

    companion object {
        private const val TAG = "DnsFilterEngine"

        /**
         * SafeSearch redirect map.
         *
         * These IPs are the official "force safe search" IPs published by Google.
         * When a client resolves google.com and gets this IP, Google's servers
         * automatically enforce SafeSearch for all queries from that session.
         *
         * Sources:
         *  - Google:  forcesafesearch.google.com  = 216.239.38.120
         *  - YouTube: restrict.youtube.com         = 216.239.38.119
         *  - Bing:    strict.bing.com              = 204.79.197.220
         */
        private val SAFESEARCH_DOMAINS = mapOf(
            // Google Search
            "google.com"            to "216.239.38.120",
            "www.google.com"        to "216.239.38.120",
            "google.co.in"          to "216.239.38.120",
            "google.co.uk"          to "216.239.38.120",
            "google.com.au"         to "216.239.38.120",
            "google.ca"             to "216.239.38.120",
            "google.de"             to "216.239.38.120",
            "google.fr"             to "216.239.38.120",
            "encrypted.google.com"  to "216.239.38.120",

            // YouTube
            "youtube.com"           to "216.239.38.119",
            "www.youtube.com"       to "216.239.38.119",
            "m.youtube.com"         to "216.239.38.119",
            "youtubei.googleapis.com" to "216.239.38.119",

            // Bing
            "bing.com"              to "204.79.197.220",
            "www.bing.com"          to "204.79.197.220",

            // DuckDuckGo safe mode
            "duckduckgo.com"        to "54.191.125.83",
            "www.duckduckgo.com"    to "54.191.125.83"
        )
    }
}