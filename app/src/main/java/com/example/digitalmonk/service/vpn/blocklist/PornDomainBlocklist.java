package com.example.digitalmonk.service.vpn.blocklist;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Why we made this file:
 * While the BlocklistManager fetches dynamic lists from the internet, you always
 * want a "Seed List" hardcoded into the app. This ensures that even if the child's
 * device is entirely offline or the remote server fails, the most common inappropriate
 * sites are blocked the very first millisecond the VPN turns on.
 *
 * What the file name defines:
 * "PornDomain" specifies the category of restricted content.
 * "Blocklist" identifies it as a security rule set.
 */
public class PornDomainBlocklist {

    // ── Hardcoded Seed List ───────────────────────────────────────────────────
    private static final Set<String> DOMAINS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            // ── Major adult platforms ─────────────────────────────────────────────
            "pornhub.com",
            "xvideos.com",
            "xnxx.com",
            "xhamster.com",
            "redtube.com",
            "youporn.com",
            "tube8.com",
            "spankbang.com",
            "porntrex.com",
            "beeg.com",
            "eporner.com",
            "txxx.com",
            "xtube.com",
            "hclips.com",
            "drtuber.com",
            "tnaflix.com",
            "fapdu.com",
            "empflix.com",
            "fuqer.com",
            "porndig.com",
            "gotporn.com",
            "pornone.com",
            "ok.xxx",
            "porn.com",
            "sex.com",
            "adult.com",
            "xxx.com",
            "nudevista.com",
            "pornmd.com",
            "pornerbros.com",

            // ── Image/content boards ──────────────────────────────────────────────
            "xbabe.com",
            "bravotube.net",
            "pornktube.com",
            "vporn.com",
            "ah-me.com",
            "sunporno.com",
            "porn00.org",
            "free18.net",
            "4tube.com",
            "keezmovies.com",
            "slutload.com",
            "extremetube.com",
            "hardsextube.com",
            "pornid.xxx",
            "hellporno.com",
            "definebabe.com",
            "sexvid.xxx",
            "shesfreaky.com",
            "hotmovs.com",
            "fullporner.com",
            "pornhat.com",
            "xmoviesforyou.com",
            "viptube.com",
            "cliphunter.com",
            "pornoxo.com",

            // ── Cam sites ─────────────────────────────────────────────────────────
            "chaturbate.com",
            "cam4.com",
            "myfreecams.com",
            "bongacams.com",
            "livejasmin.com",
            "stripchat.com",
            "camsoda.com",
            "flirt4free.com",
            "streamate.com",
            "imlive.com",
            "jasmin.com",

            // ── Escort / hookup sites ─────────────────────────────────────────────
            "adultfriendfinder.com",
            "ashley-madison.com",
            "ashleymadison.com",
            "fling.com",
            "instabang.com",
            "alt.com",
            "fetlife.com",

            // ── Adult content stores ──────────────────────────────────────────────
            "brazzers.com",
            "realitykings.com",
            "naughtyamerica.com",
            "digitalplayground.com",
            "bangbros.com",
            "mofos.com",
            "teamskeet.com",
            "wicked.com",
            "kink.com",
            "evilangel.com",
            "colleyentertainment.com",

            // ── Hentai / anime adult ──────────────────────────────────────────────
            "nhentai.net",
            "hanime.tv",
            "hentaihaven.xxx",
            "hentai.com",
            "hentaigasm.com",
            "hentaimama.io",

            // ── Search redirects used by adult sites ──────────────────────────────
            "adultquery.com",
            "adultseek.net",

            // ── Gambling (bonus category) ─────────────────────────────────────────
            "bet365.com",
            "draftkings.com",
            "fanduel.com",
            "pokerstars.com",
            "partypoker.com",
            "888casino.com",
            "casumo.com",
            "betway.com"
    )));

    /**
     * Private constructor to enforce the Utility Class pattern.
     */
    private PornDomainBlocklist() {}

    /**
     * Getter method specifically required by the BlocklistManager.java file
     * we converted in the previous step.
     */
    public static Set<String> getDomains() {
        return DOMAINS;
    }

    /**
     * Returns true if the given domain is in the blocklist,
     * including subdomain matching (e.g. "www.pornhub.com" matches "pornhub.com").
     */
    public static boolean isBlocked(String domain) {
        if (domain == null || domain.isEmpty()) return false;

        String lower = domain.toLowerCase();

        // Fast O(1) direct match
        if (DOMAINS.contains(lower)) return true;

        // Slower O(N) subdomain match
        for (String blocked : DOMAINS) {
            if (lower.endsWith("." + blocked)) {
                return true;
            }
        }

        return false;
    }
}