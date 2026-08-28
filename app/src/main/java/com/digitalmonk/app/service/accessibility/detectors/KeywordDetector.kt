package com.digitalmonk.app.service.accessibility.detectors

import android.util.Log
import android.view.accessibility.AccessibilityEvent
import java.util.Locale

/**
 * Why we made this file:
 * While Digital Monk now uses a hardware-level DNS VPN for web filtering,
 * AccessibilityServices are still highly useful for analyzing text on the screen.
 * * This KeywordDetector scans the visible text on the child's screen (like inside
 * a YouTube search bar or a social media feed). If it detects specific forbidden
 * words (e.g., related to self-harm or adult content), it can immediately trigger
 * the GuardianAccessibilityService to block the screen or alert the parent.
 * 
 * What the file name defines:
 * "Keyword" refers to specific strings of text we are searching for.
 * "Detector" signifies its role as an observer/analyzer of screen content.
 */
object KeywordDetector {
    private const val TAG = "KeywordDetector"

    /**
     * Scans an AccessibilityEvent to see if any forbidden keywords are present
     * on the screen.
     * 
     * @param event The accessibility event containing the screen text.
     * @param forbiddenWords A list of words to watch out for.
     * @return true if a bad word is found, false otherwise.
     */
    fun containsForbiddenKeywords(
        event: AccessibilityEvent?,
        forbiddenWords: MutableList<String>?
    ): Boolean {
        if (event == null || forbiddenWords == null || forbiddenWords.isEmpty()) {
            return false
        }

        // Only process events that contain text
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.getEventType() != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) {
            return false
        }

        val textList = event.getText()
        if (textList == null || textList.isEmpty()) {
            return false
        }

        // Convert the on-screen text to a single, lowercase searchable string
        val screenTextBuilder = StringBuilder()
        for (charSequence in textList) {
            if (charSequence != null) {
                screenTextBuilder.append(charSequence.toString().lowercase(Locale.getDefault()))
                    .append(" ")
            }
        }
        val screenText = screenTextBuilder.toString()

        // Check if any forbidden word exists in the screen text
        for (word in forbiddenWords) {
            if (screenText.contains(word.lowercase(Locale.getDefault()))) {
                Log.w(TAG, "🚨 Forbidden keyword detected on screen: " + word)
                return true // Match found! Trigger the block.
            }
        }

        return false
    } // TODO: Consider adding logic to traverse the AccessibilityNodeInfo tree
    // if the event.getText() method misses deeply nested UI text.
}