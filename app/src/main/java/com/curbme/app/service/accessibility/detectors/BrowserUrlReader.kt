package com.curbme.app.service.accessibility.detectors

import android.view.accessibility.AccessibilityNodeInfo
import java.net.URI

/**
 * Extracts and normalizes domain names from browser address bar accessibility nodes.
 */
object BrowserUrlReader {

    private val BROWSER_URL_BAR_IDS: Map<String, List<String>> = mapOf(
        "com.android.chrome" to listOf("url_bar", "location_bar"),
        "com.chrome.beta" to listOf("url_bar", "location_bar"),
        "com.chrome.canary" to listOf("url_bar", "location_bar"),
        "com.chrome.dev" to listOf("url_bar", "location_bar"),
        "com.brave.browser" to listOf("url_bar", "location_bar"),
        "org.mozilla.firefox" to listOf("mozac_browser_toolbar_url_view", "url_bar_title"),
        "org.mozilla.firefox_beta" to listOf("mozac_browser_toolbar_url_view", "url_bar_title"),
        "org.mozilla.fenix" to listOf("mozac_browser_toolbar_url_view", "url_bar_title"),
        "com.microsoft.emmx" to listOf("url_bar", "search_box"),
        "com.sec.android.app.sbrowser" to listOf("location_bar_edit_text", "url_bar"),
        "com.opera.browser" to listOf("url_field", "url_bar"),
        "com.opera.mini.native" to listOf("url_field", "url_bar"),
        "com.vivaldi.browser" to listOf("url_bar", "location_bar"),
        "com.kiwibrowser.browser" to listOf("url_bar", "location_bar"),
        "com.duckduckgo.mobile.android" to listOf("omnibar_text_input", "search_box")
    )

    /**
     * Extracts the domain name currently open in the browser, or null if not in a supported browser/address bar.
     */
    fun readDomain(rootNode: AccessibilityNodeInfo?, packageName: String): String? {
        if (rootNode == null) return null
        val urlBarIds = BROWSER_URL_BAR_IDS[packageName] ?: return null

        for (idName in urlBarIds) {
            val fullId = "$packageName:id/$idName"
            val nodes = rootNode.findAccessibilityNodeInfosByViewId(fullId)
            if (!nodes.isNullOrEmpty()) {
                for (node in nodes) {
                    val rawText = (node.text ?: node.contentDescription)?.toString()
                    @Suppress("DEPRECATION")
                    node.recycle()
                    if (!rawText.isNullOrBlank()) {
                        val domain = extractDomainFromText(rawText)
                        if (domain != null) return domain
                    }
                }
            }
        }

        return null
    }

    private fun extractDomainFromText(inputText: String): String? {
        val trimmed = inputText.trim()
        if (trimmed.isEmpty()) return null

        val urlCandidate = if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) trimmed else "https://$trimmed"

        return try {
            val uri = URI(urlCandidate)
            val host = uri.host ?: return null
            if (!host.contains('.')) return null
            host.lowercase().removePrefix("www.")
        } catch (_: Exception) {
            null
        }
    }
}
