package com.curbme.app.service.accessibility.detectors

import android.view.accessibility.AccessibilityNodeInfo

/**
 * Extracts a dynamic comparator text string from the current screen for short-video / reel apps.
 * The comparator string changes whenever the user swipes to a new reel or video.
 */
object ReelTextExtractor {

    /**
     * Extracts dynamic comparator text from rootNode for the given package name, or returns null if not a reel view.
     */
    fun extractComparator(rootNode: AccessibilityNodeInfo?, packageName: String): String? {
        if (rootNode == null) return null

        return when {
            packageName.contains("youtube") -> extractYouTubeReelText(rootNode, packageName)
            packageName.contains("instagram") -> extractInstagramReelText(rootNode, packageName)
            packageName.contains("snapchat") -> extractSnapchatSpotlightText(rootNode, packageName)
            packageName.contains("facebook") -> extractFacebookReelText(rootNode, packageName)
            packageName.contains("musically") || packageName.contains("tiktok") -> extractTikTokReelText(rootNode, packageName)
            else -> null
        }
    }

    private fun extractYouTubeReelText(root: AccessibilityNodeInfo, pkg: String): String? {
        val recycler = root.findAccessibilityNodeInfosByViewId("$pkg:id/reel_recycler")
        if (recycler.isNullOrEmpty()) return null

        val pageContent = root.findAccessibilityNodeInfosByViewId("$pkg:id/reel_player_page_content")
        if (pageContent.isNullOrEmpty()) return ""

        val rawText = collectNodeSubtreeText(pageContent[0], maxDepth = 6)
        return cleanYouTubeComparator(rawText)
    }

    private fun extractInstagramReelText(root: AccessibilityNodeInfo, pkg: String): String? {
        val viewer = root.findAccessibilityNodeInfosByViewId("$pkg:id/clips_viewer_view_pager")
            .ifEmpty { root.findAccessibilityNodeInfosByViewId("$pkg:id/clips_swipe_refresh_layout") }
        if (viewer.isNullOrEmpty()) return null

        var textBuilder = ""
        val captions = root.findAccessibilityNodeInfosByViewId("$pkg:id/clips_captions_component")
        if (!captions.isNullOrEmpty()) {
            textBuilder += collectNodeSubtreeText(captions[0], maxDepth = 4)
        }

        val author = root.findAccessibilityNodeInfosByViewId("$pkg:id/clips_author_username")
        if (!author.isNullOrEmpty()) {
            textBuilder += collectNodeSubtreeText(author[0], maxDepth = 2)
        }

        return textBuilder
    }

    private fun extractSnapchatSpotlightText(root: AccessibilityNodeInfo, pkg: String): String? {
        val container = root.findAccessibilityNodeInfosByViewId("$pkg:id/spotlight_container")
        if (container.isNullOrEmpty()) return null

        val opera = root.findAccessibilityNodeInfosByViewId("$pkg:id/opera_viewer")
        if (opera.isNullOrEmpty()) return ""

        return collectNodeSubtreeText(opera[0], maxDepth = 6)
    }

    private fun extractFacebookReelText(root: AccessibilityNodeInfo, pkg: String): String? {
        val reelsTab = root.findAccessibilityNodeInfosByText("Reels tab details")
            .ifEmpty { root.findAccessibilityNodeInfosByViewId("$pkg:id/reels_viewer_fragment") }
        if (reelsTab.isNullOrEmpty()) return null

        return collectNodeSubtreeText(reelsTab[0], maxDepth = 5)
    }

    private fun extractTikTokReelText(root: AccessibilityNodeInfo, pkg: String): String? {
        val viewer = root.findAccessibilityNodeInfosByViewId("$pkg:id/view_pager_layout_wrapper")
        if (viewer.isNullOrEmpty()) return null

        val title = root.findAccessibilityNodeInfosByViewId("$pkg:id/title")
        return if (!title.isNullOrEmpty()) collectNodeSubtreeText(title[0], maxDepth = 2) else ""
    }

    private fun collectNodeSubtreeText(node: AccessibilityNodeInfo?, maxDepth: Int, currentDepth: Int = 0): String {
        if (node == null || currentDepth > maxDepth || !node.isVisibleToUser) return ""
        val builder = StringBuilder()

        node.text?.toString()?.takeIf { it.isNotBlank() }?.let { builder.append(it).append(" ") }
        node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { builder.append(it).append(" ") }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            builder.append(collectNodeSubtreeText(child, maxDepth, currentDepth + 1))
            @Suppress("DEPRECATION")
            child.recycle()
        }

        return builder.toString().trim()
    }

    private fun cleanYouTubeComparator(value: String): String {
        val compact = value.replace("\n", "")
        if (compact.contains("PostPostPostlike") || compact.length <= 15) return ""
        return compact.replace("Video Progress", "")
            .replace("Tap to watch live", "")
            .replace("Go to channel", "")
            .replace("soundVideo ProgressSearchMoreHomeHomeShortsShortsCreateSubscriptions", "")
            .replace("soundSearchMoreHomeHomeShortsShortsCreateSubscriptions", "")
    }
}
