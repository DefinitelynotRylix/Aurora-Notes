package com.aurora.notes.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration

data class DetectedUrlRange(
    val start: Int,
    val end: Int,
    val rawText: String,
    val normalizedUrl: String
)

object LinkHelper {

    const val URL_ANNOTATION_TAG = "AURORA_CLICKABLE_URL"
    val LinkPurpleColor = Color(0xFF9B51E0)

    private val urlRegex = Regex(
        pattern = """((?:https?://|www\.)[^\s<>"']+)""",
        option = RegexOption.IGNORE_CASE
    )

    private fun trimTrailingPunctuation(raw: String): String {
        var clean = raw
        while (clean.isNotEmpty()) {
            val last = clean.last()
            if (last in charArrayOf('.', ',', ';', ':', '!', '?', '\'', '"')) {
                clean = clean.dropLast(1)
            } else if (last == ')' && clean.count { it == '(' } < clean.count { it == ')' }) {
                clean = clean.dropLast(1)
            } else if (last == ']' && clean.count { it == '[' } < clean.count { it == ']' }) {
                clean = clean.dropLast(1)
            } else {
                break
            }
        }
        return clean
    }

    fun normalizeBrowserUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) return ""
        return if (
            trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) {
            trimmed
        } else {
            "https://$trimmed"
        }
    }

    fun extractLinks(text: String): List<DetectedUrlRange> {
        if (text.length < 4) return emptyList()
        if (!text.contains("http", ignoreCase = true) && !text.contains("www.", ignoreCase = true)) {
            return emptyList()
        }
        val results = ArrayList<DetectedUrlRange>()
        val matches = urlRegex.findAll(text)
        for (match in matches) {
            val rawMatch = match.value
            val cleanUrl = trimTrailingPunctuation(rawMatch)
            if (cleanUrl.length >= 4) {
                val startIdx = match.range.first
                val endIdx = startIdx + cleanUrl.length
                results.add(
                    DetectedUrlRange(
                        start = startIdx,
                        end = endIdx,
                        rawText = cleanUrl,
                        normalizedUrl = normalizeBrowserUrl(cleanUrl)
                    )
                )
            }
        }
        return results
    }

    fun buildLinkAnnotatedString(
        text: String,
        linkColor: Color = LinkPurpleColor
    ): AnnotatedString {
        val links = extractLinks(text)
        return buildAnnotatedString {
            append(text)
            for (link in links) {
                val s = link.start.coerceIn(0, text.length)
                val e = link.end.coerceIn(0, text.length)
                if (s < e) {
                    addStyle(
                        style = SpanStyle(
                            color = linkColor,
                            fontWeight = FontWeight.SemiBold,
                            textDecoration = TextDecoration.Underline
                        ),
                        start = s,
                        end = e
                    )
                    addStringAnnotation(
                        tag = URL_ANNOTATION_TAG,
                        annotation = link.normalizedUrl,
                        start = s,
                        end = e
                    )
                }
            }
        }
    }

    /**
     * Opens the given URL in the user's Android default set browser.
     */
    fun openUrlInDefaultBrowser(context: Context, rawUrl: String) {
        val normalizedUrl = normalizeBrowserUrl(rawUrl)
        if (normalizedUrl.isEmpty()) return
        try {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(normalizedUrl)).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(browserIntent)
            LocalLogger.security("LINK", "Opened link in Android default browser: $normalizedUrl")
        } catch (e: Exception) {
            LocalLogger.error("LINK", "Could not open browser for $normalizedUrl: ${e.localizedMessage}")
            Toast.makeText(
                context,
                "No default browser found to open $normalizedUrl",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}
