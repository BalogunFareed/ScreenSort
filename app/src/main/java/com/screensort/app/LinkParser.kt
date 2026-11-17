package com.screensort.app

import android.net.Uri

data class LinkInfo(val sourceApp: String?, val handle: String?)

object LinkParser {

    private val xReserved = setOf(
        "i", "home", "search", "hashtag", "intent", "share", "explore", "messages", "notifications"
    )
    private val instagramReserved = setOf(
        "p", "reel", "reels", "tv", "stories", "explore", "share", "accounts"
    )

    fun parse(link: String?): LinkInfo {
        if (link.isNullOrBlank()) return LinkInfo(null, null)

        val uri = Uri.parse(link.trim())
        val host = (uri.host ?: "").lowercase()
            .removePrefix("www.")
            .removePrefix("m.")
            .removePrefix("mobile.")
        val segments = uri.pathSegments ?: emptyList()
        val first = segments.firstOrNull()

        return when {
            host == "x.com" || host == "twitter.com" -> {
                val handle = if (first != null && first.lowercase() !in xReserved) "@$first" else null
                LinkInfo("Twitter/X", handle)
            }
            host == "t.co" -> LinkInfo("Twitter/X", null)
            host.endsWith("linkedin.com") || host == "lnkd.in" -> LinkInfo("LinkedIn", null)
            host.endsWith("tiktok.com") -> {
                val handle = if (first != null && first.startsWith("@")) first else null
                LinkInfo("TikTok", handle)
            }
            host.endsWith("instagram.com") -> {
                val handle = if (first != null && first.lowercase() !in instagramReserved) "@$first" else null
                LinkInfo("Instagram", handle)
            }
            host.endsWith("youtube.com") || host == "youtu.be" -> {
                val handle = if (first != null && first.startsWith("@")) first else null
                LinkInfo("YouTube", handle)
            }
            host.endsWith("facebook.com") || host == "fb.watch" -> LinkInfo("Facebook", null)
            host == "wa.me" || host.endsWith("whatsapp.com") -> LinkInfo("WhatsApp", null)
            host == "t.me" -> {
                val handle = if (first != null) "@$first" else null
                LinkInfo("Telegram", handle)
            }
            else -> LinkInfo(null, null)
        }
    }
}