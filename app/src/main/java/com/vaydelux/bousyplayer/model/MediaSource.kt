package com.vaydelux.bousyplayer.model

import android.net.Uri
import androidx.media3.common.MimeTypes

data class MediaSource(val uri: Uri, val title: String, val isDocument: Boolean) {
    val id: String get() = uri.toString()
}

/** Do not accept javascript, content spoofing in URL fields, userinfo, or plaintext HTTP. */
object SourcePolicy {
    fun isValidNetworkUrl(value: String): Boolean {
        val url = runCatching { java.net.URI(value.trim()) }.getOrNull() ?: return false
        return url.scheme.equals("https", true) &&
            !url.host.isNullOrBlank() && url.rawUserInfo == null &&
            url.rawFragment == null
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
