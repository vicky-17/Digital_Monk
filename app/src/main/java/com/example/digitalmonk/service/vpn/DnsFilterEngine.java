package com.example.digitalmonk.service.vpn;

import android.util.Log;

import com.example.digitalmonk.data.local.prefs.PrefsManager;
import com.example.digitalmonk.service.vpn.blocklist.PornDomainBlocklist;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Why we made this file:
 * The DnsVpnService handles the raw network plumbing, but it is "dumb" — it doesn't
 * know what a packet means. This Engine is the "Brain". For every single website
 * the child's phone tries to visit, this engine receives the domain name and makes
 * a lightning-fast routing decision: Allow, Block, or Redirect.
 *
 * What the file name defines:
 * "DnsFilter" defines its purpose (filtering Domain Name System requests).
 * "Engine" denotes it as a core business-logic processor.
 */
public class DnsFilterEngine {

    private static final String TAG = "DnsFilterEngine";
    private final PrefsManager prefs;

    // ── SafeSearch Map Initialization ─────────────────────────────────────────
    private static final Map<String, String> SAFESEARCH_DOMAINS;

    static {
        Map<String, String> map = new HashMap<>();
        // Google Search
        map.put("google.com", "216.239.38.120");
        map.put("www.google.com", "216.239.38.120");
        map.put("google.co.in", "216.239.38.120");
        map.put("google.co.uk", "216.239.38.120");
        map.put("google.com.au", "216.239.38.120");
        map.put("google.ca", "216.239.38.120");
        map.put("google.de", "216.239.38.120");
        map.put("google.fr", "216.239.38.120");
        map.put("encrypted.google.com", "216.239.38.120");

        // YouTube
        map.put("youtube.com", "216.239.38.119");
        map.put("www.youtube.com", "216.239.38.119");
        map.put("m.youtube.com", "216.239.38.119");
        map.put("youtubei.googleapis.com", "216.239.38.119");

        // Bing
        map.put("bing.com", "204.79.197.220");
        map.put("www.bing.com", "204.79.197.220");

        // DuckDuckGo safe mode
        map.put("duckduckgo.com", "54.191.125.83");
        map.put("www.duckduckgo.com", "54.191.125.83");

        SAFESEARCH_DOMAINS = Collections.unmodifiableMap(map);
    }

    // ── Sealed Class Equivalent ───────────────────────────────────────────────

    /**
     * In Java, we simulate Kotlin's `sealed class` by creating an abstract base
     * class with a private constructor, and nesting the allowed subclasses inside it.
     */
    public abstract static class FilterDecision {
        private FilterDecision() {} // Prevents external subclassing

        /** Forward to upstream DNS normally */
        public static final class Allow extends FilterDecision {
            // Singleton instance to save memory
            public static final Allow INSTANCE = new Allow();
            private Allow() {}
        }

        /** Return NXDOMAIN — domain does not exist */
        public static final class Block extends FilterDecision {
            public static final Block INSTANCE = new Block();
            private Block() {}
        }

        /** Return a fake A record pointing to the SafeSearch IP */
        public static final class SafeSearchRedirect extends FilterDecision {
            private final String redirectIp;

            public SafeSearchRedirect(String redirectIp) {
                this.redirectIp = redirectIp;
            }

            public String getRedirectIp() {
                return redirectIp;
            }
        }
    }

    // ── Core Logic ────────────────────────────────────────────────────────────

    public DnsFilterEngine(PrefsManager prefs) {
        this.prefs = prefs;
    }

    /**
     * Determines the fate of a DNS query.
     * Note: DnsPacketParser.TYPE_A usually equals 1 (standard IPv4 request).
     */
    public FilterDecision decide(String domain, int queryType) {
        if (domain == null) return FilterDecision.Allow.INSTANCE;

        // Normalize the domain so "YouTube.com" matches "youtube.com"
        String lowerDomain = domain.toLowerCase();

        // ── 1. SafeSearch enforcement ─────────────────────────────────────────
        if (prefs.isEnforceSafeSearch() || prefs.isSafeSearchEnabled()) {
            String safeIp = SAFESEARCH_DOMAINS.get(lowerDomain);

            if (safeIp != null && queryType == DnsPacketParser.TYPE_A) {
                Log.d(TAG, "🔍 SafeSearch redirect: " + domain + " → " + safeIp);
                return new FilterDecision.SafeSearchRedirect(safeIp);
            }
        }

        // ── 2. Porn / adult content blocking ─────────────────────────────────
        if (prefs.isBlockPorn() || prefs.isSafeSearchEnabled()) {
            if (PornDomainBlocklist.isBlocked(lowerDomain)) {
                Log.d(TAG, "🚫 Porn blocked: " + domain);
                return FilterDecision.Block.INSTANCE;
            }
        }

        // ── 3. Custom user-defined domain blocklist ───────────────────────────
        if (isCustomBlocked(lowerDomain)) {
            Log.d(TAG, "🚫 Custom blocked: " + domain);
            return FilterDecision.Block.INSTANCE;
        }

        return FilterDecision.Allow.INSTANCE;
    }

    private boolean isCustomBlocked(String domain) {
        // TODO Phase 3: Load from Room DB (custom domains parent adds manually)
        return false;
    }
}