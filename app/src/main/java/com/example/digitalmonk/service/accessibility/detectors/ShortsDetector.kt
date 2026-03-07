package com.example.digitalmonk.service.accessibility.detectors

import android.view.accessibility.AccessibilityNodeInfo

object ShortsDetector {
    fun shouldBlock(rootNode: AccessibilityNodeInfo?, packageName: String): Boolean {
        if (rootNode == null) return false

        return when (packageName) {
            "com.google.android.youtube" -> containsShortsText(rootNode)
            "com.instagram.android" -> isInstagramReels(rootNode)
            "com.zhiliaoapp.musically" -> true // TikTok is all shorts
            else -> false
        }
    }

    private fun containsShortsText(node: AccessibilityNodeInfo): Boolean {
        // Basic heuristic: look for "Shorts" in node text or content description
        if (node.text?.toString()?.contains("Shorts", ignoreCase = true) == true ||
            node.contentDescription?.toString()?.contains("Shorts", ignoreCase = true) == true
        ) return true

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (containsShortsText(child)) return true
        }
        return false
    }

    private fun isInstagramReels(node: AccessibilityNodeInfo): Boolean {
        // Simple heuristic for Instagram Reels
        return node.contentDescription?.toString()?.contains("Reels", ignoreCase = true) == true
    }
}