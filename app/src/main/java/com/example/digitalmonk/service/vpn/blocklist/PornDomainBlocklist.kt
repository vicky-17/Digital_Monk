package com.example.digitalmonk.service.vpn.blocklist

/**
 * Hard-coded blocklist of adult/porn domains.
 *
 * This is the seed list — BlocklistManager can augment it with
 * remote blocklist updates fetched from a server.
 *
 * Matching strategy:
 *  - Exact match: "pornhub.com"
 *  - Subdomain match: anything ending in ".pornhub.com"
 */
object PornDomainBlocklist {

    val domains: Set<String> = setOf(
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
        "adultfriendfinder.com",
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

        // ── Reddit NSFW (block the API endpoints used for NSFW) ──────────────
        // Note: We don't block all of reddit — just adult subreddit CDN patterns
        // "i.redd.it" would be too broad. Target via keyword filter instead.

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
    )

    /**
     * Returns true if the given domain is in the blocklist,
     * including subdomain matching (e.g. "www.pornhub.com" matches "pornhub.com").
     */
    fun isBlocked(domain: String): Boolean {
        val lower = domain.lowercase()
        if (lower in domains) return true
        // Check if it's a subdomain of a blocked domain
        return domains.any { blocked ->
            lower.endsWith(".$blocked")
        }
    }
}