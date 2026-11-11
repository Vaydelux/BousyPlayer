package com.vaydelux.bousyplayer.model
import android.net.Uri
data class MediaSource(val uri: Uri, val title: String, val isDocument: Boolean)
object SourcePolicy {
    fun clampSeek(position: Long, duration: Long): Long = if (duration <= 0L) 0 else position.coerceIn(0L, duration)
}
