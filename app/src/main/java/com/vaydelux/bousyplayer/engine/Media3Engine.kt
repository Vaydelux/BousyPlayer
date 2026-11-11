package com.vaydelux.bousyplayer.engine

import android.content.Context
import android.net.Uri
import android.graphics.Color
import android.graphics.Typeface
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import com.vaydelux.bousyplayer.model.SourcePolicy

@OptIn(UnstableApi::class)
class Media3Engine(context: Context) : PlaybackEngine {
    override val kind = EngineKind.MEDIA3
    private val native = ExoPlayer.Builder(context.applicationContext).build()
    private var failed: String? = null
    // Track groups change rarely; allocating formats and labels on every 400ms UI tick
    // is unnecessary pressure on 32-bit/low-memory Android TV devices.
    private var displayedTracks: List<MediaTrack> = emptyList()
    private fun displayTracks(tracks: Tracks): List<MediaTrack> = tracks.groups.take(24).flatMapIndexed { groupIndex, group ->
        val kind = when (group.type) {
            C.TRACK_TYPE_AUDIO -> TrackKind.AUDIO
            C.TRACK_TYPE_TEXT -> TrackKind.SUBTITLE
            else -> null
        } ?: return@flatMapIndexed emptyList()
        (0 until minOf(group.length, 32)).map { index ->
            val format = group.getTrackFormat(index)
            MediaTrack(
                id = "$groupIndex:$index",
                label = buildString {
                    append(format.label ?: format.language ?: "Track ${index + 1}")
                    format.codecs?.let { append(" · "); append(it) }
                }.take(120),
                kind = kind,
                selected = group.isTrackSelected(index)
            )
        }
    }
    override val view: PlayerView = PlayerView(context).apply {
        useController = false
        keepScreenOn = true
        player = native
        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
        subtitleView?.setApplyEmbeddedFontSizes(false)
        subtitleView?.setFractionalTextSize(0.06f)
    }
    private val listener = object : Player.Listener {
        override fun onTracksChanged(tracks: Tracks) {
            displayedTracks = displayTracks(tracks)
        }
        override fun onPlayerError(error: PlaybackException) {
            failed = "Media3 playback error: ${error.errorCodeName}"
        }
    }
    init {
        // The session owns audio focus for every engine to avoid duplicate owners.
        native.setAudioAttributes(AudioAttributes.DEFAULT, false)
        // A single session-level receiver must also work for native mpv/VLC.
        native.setHandleAudioBecomingNoisy(false)
        native.addListener(listener)
    }

    override fun load(uri: Uri, startMs: Long, shouldPlay: Boolean, subtitle: Uri?) {
        failed = null
        displayedTracks = emptyList()
        val item = MediaItem.Builder().setUri(uri)
        SourcePolicy.inferMimeType(uri)?.let(item::setMimeType)
        if (subtitle != null) {
            val lower = subtitle.lastPathSegment.orEmpty().lowercase()
            val mime = when {
                lower.endsWith(".vtt") -> MimeTypes.TEXT_VTT
                lower.endsWith(".ass") || lower.endsWith(".ssa") -> MimeTypes.TEXT_SSA
                else -> MimeTypes.APPLICATION_SUBRIP
            }
            item.setSubtitleConfigurations(listOf(
                MediaItem.SubtitleConfiguration.Builder(subtitle)
                    .setMimeType(mime)
                    .setLanguage("und")
                    .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                    .build()
            ))
        }
        native.playWhenReady = false
        native.setMediaItem(item.build(), startMs.coerceAtLeast(0L))
        native.prepare()
        native.playWhenReady = shouldPlay
    }

    override fun snapshot(): PlaybackSnapshot {
        val state = native.playbackState
        return PlaybackSnapshot(
            position = native.currentPosition.coerceAtLeast(0L),
            duration = native.duration.takeIf { it != C.TIME_UNSET && it > 0L } ?: 0L,
            buffered = native.bufferedPosition.coerceAtLeast(0L),
            isPlaying = native.isPlaying,
            canSeek = native.isCurrentMediaItemSeekable,
            loading = state == Player.STATE_BUFFERING || state == Player.STATE_IDLE,
            ended = state == Player.STATE_ENDED,
            error = failed,
            tracks = displayedTracks
        )
    }
    override fun play() {
        if (native.playbackState == Player.STATE_ENDED) native.seekTo(0L)
        native.playWhenReady = true
    }
    override fun pause() { native.playWhenReady = false }
    override fun seek(positionMs: Long) {
        // Session may have observed seekability 400ms ago; a live stream can
        // become non-seekable between that snapshot and the user command.
        if (!native.isCurrentMediaItemSeekable) throw IllegalStateException("Seek unavailable")
        native.seekTo(positionMs.coerceAtLeast(0L))
    }
    override fun speed(value: Float) { native.setPlaybackSpeed(value) }
    override fun aspect(value: AspectMode) {
        view.resizeMode = when (value) {
            AspectMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
            AspectMode.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            AspectMode.STRETCH -> AspectRatioFrameLayout.RESIZE_MODE_FILL
        }
    }
    override fun selectTrack(id: String, kind: TrackKind) {
        val parts = id.split(':')
        val groupIndex = parts.getOrNull(0)?.toIntOrNull() ?: return
        val trackIndex = parts.getOrNull(1)?.toIntOrNull() ?: return
        val group = native.currentTracks.groups.getOrNull(groupIndex) ?: return
        if (trackIndex !in 0 until group.length) return
        val type = if (kind == TrackKind.AUDIO) C.TRACK_TYPE_AUDIO else C.TRACK_TYPE_TEXT
        if (group.type != type) return
        native.trackSelectionParameters = native.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(type, false)
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, trackIndex))
            .build()
    }
    override fun disableSubtitles() {
        native.trackSelectionParameters = native.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
    }
    override fun applySubtitleAppearance(appearance: SubtitleAppearance): Boolean {
        val captions = view.subtitleView ?: return false
        val foreground = when (appearance.color) {
            SubtitleColor.WHITE -> Color.WHITE
            SubtitleColor.YELLOW -> Color.YELLOW
            SubtitleColor.BLACK -> Color.BLACK
        }
        val panel = if (appearance.color == SubtitleColor.BLACK) {
            Color.argb(225, 255, 255, 255)
        } else {
            Color.argb(195, 0, 0, 0)
        }
        val outline = if (appearance.color == SubtitleColor.BLACK) Color.WHITE else Color.BLACK
        captions.setApplyEmbeddedStyles(false)
        captions.setApplyEmbeddedFontSizes(false)
        captions.setFractionalTextSize(appearance.size.media3Fraction)
        captions.setStyle(CaptionStyleCompat(foreground, panel, Color.TRANSPARENT,
            CaptionStyleCompat.EDGE_TYPE_OUTLINE, outline, Typeface.DEFAULT_BOLD))
        return true
    }
    override fun detachView() { view.player = null }
    override fun release() {
        displayedTracks = emptyList()
        view.keepScreenOn = false
        native.removeListener(listener)
        detachView()
        native.release()
    }
}
