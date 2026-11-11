package com.vaydelux.bousyplayer.engine

import android.net.Uri
import android.view.View

/** One foreground owner at a time; callbacks are short-lived, never stored globally. */
data class MediaTrack(val id: String, val label: String, val kind: TrackKind, val selected: Boolean)
data class PlaybackSnapshot(
    val position: Long = 0L,
    val duration: Long = 0L,
    val buffered: Long = 0L,
    val isPlaying: Boolean = false,
    val canSeek: Boolean = false,
    val loading: Boolean = true,
    val ended: Boolean = false,
    val error: String? = null,
    val tracks: List<MediaTrack> = emptyList()
)
interface PlaybackEngine {
    val kind: EngineKind
    val view: View
    fun load(uri: Uri, startMs: Long, shouldPlay: Boolean, subtitle: Uri? = null)
    fun snapshot(): PlaybackSnapshot
    fun play()
    fun pause()
    fun seek(positionMs: Long)
    fun speed(value: Float)
    fun aspect(value: AspectMode)
    fun selectTrack(id: String, kind: TrackKind)
    fun disableSubtitles()
    fun detachView()
    fun release()
}
