package com.vaydelux.bousyplayer.model

import android.net.Uri
import androidx.media3.common.MimeTypes

data class MediaSource(val uri: Uri, val title: String, val isDocument: Boolean) {
    val id: String get() = uri.toString()
}

/** Do not accept javascript, content spoofing in URL fields, userinfo, or plaintext HTTP. */
object SourcePolicy {
    const val MAX_STREAM_URL_LENGTH = 8_192
    private val encodedLineBreak = Regex("%0[ad]", RegexOption.IGNORE_CASE)

    fun isValidNetworkUrl(value: String): Boolean {
        if (value.length !in 1..MAX_STREAM_URL_LENGTH ||
            value.any { it.code < 32 || it.code == 127 || it == '\\' } ||
            encodedLineBreak.containsMatchIn(value)) return false
        val trimmed = value.trim()
        // Keep ordinary signed HTTPS URLs; reject malformed ports and odd
        // authority syntax before forwarding to different native decoders.
        if (trimmed.any(Char::isWhitespace)) return false
        val url = runCatching { java.net.URI(trimmed) }.getOrNull() ?: return false
        return url.scheme.equals("https", true) &&
            !url.host.isNullOrBlank() && url.rawUserInfo == null &&
            url.rawFragment == null &&
            !url.rawAuthority.orEmpty().endsWith(":") &&
            (url.port == -1 || url.port in 1..65535)
    }

    fun inferMimeType(uri: Uri): String? = when (uri.lastPathSegment?.substringBefore('?')
        ?.lowercase()?.substringAfterLast('.')) {
        "m3u8" -> MimeTypes.APPLICATION_M3U8
        "mpd" -> MimeTypes.APPLICATION_MPD
        else -> null
    }

    fun clampSeek(positionMs: Long, durationMs: Long): Long =
        if (durationMs <= 0L) 0L else positionMs.coerceIn(0L, durationMs)

    fun displayTime(valueMs: Long): String {
        val s = (valueMs.coerceAtLeast(0L) / 1000L)
        return if (s >= 3600L) "%d:%02d:%02d".format(s / 3600, (s / 60) % 60, s % 60)
        else "%d:%02d".format(s / 60, s % 60)
    }
}

/**
 * Bounded, user-controlled local history. Network stream URLs, unusually long
 * provider identifiers and control/bidi characters never enter saved recents.
 * These rules are pure, so the Android/JVM source gates can exercise them.
 */
object HistoryPolicy {
    const val MAX_URI_LENGTH = 2_048
    const val MAX_TITLE_LENGTH = 140
    const val MAX_HISTORY_JSON_CHARS = 65_536
    const val MAX_DURATION_MS = 30L * 24 * 60 * 60 * 1_000

    fun isPersistableDocumentUri(value: String): Boolean {
        if (value.length !in 11..MAX_URI_LENGTH) return false
        // java.net.URI works in both Android and local JVM tests. Do not use
        // android.net.Uri here: its framework stub throws in plain JUnit.
        val parsed = runCatching { java.net.URI(value) }.getOrNull() ?: return false
        return parsed.scheme == "content" && !parsed.host.isNullOrBlank() &&
            parsed.rawUserInfo == null && parsed.rawFragment == null
    }

    fun safeTitle(value: String): String =
        value.asSequence().filterNot { char ->
            char.code < 32 || char.code == 127 ||
                char in '\u202a'..'\u202e' || char in '\u2066'..'\u2069'
        }.take(MAX_TITLE_LENGTH).joinToString("").trim().ifBlank { "Video" }

    fun safeDuration(value: Long): Long = value.coerceIn(0L, MAX_DURATION_MS)
    fun safePosition(positionMs: Long, durationMs: Long): Long =
        positionMs.coerceIn(0L, safeDuration(durationMs))
}
