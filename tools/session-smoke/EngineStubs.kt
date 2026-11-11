package com.vaydelux.bousyplayer.engine
import android.content.Context
import android.net.Uri

data class PlaybackSnapshot(
    val position: Long = 0L, val duration: Long = 0L, val buffered: Long = 0L,
    val isPlaying: Boolean = false, val canSeek: Boolean = false,
    val loading: Boolean = true, val ended: Boolean = false,
    val error: String? = null
)
interface PlaybackEngine {
    fun load(uri: Uri, startMs: Long, shouldPlay: Boolean, subtitle: Uri?)
    fun snapshot(): PlaybackSnapshot
    fun play(); fun pause(); fun seek(positionMs: Long)
    fun speed(value: Float); fun aspect(value: AspectMode)
    fun selectTrack(id: String, kind: TrackKind); fun disableSubtitles()
    fun release()
}
class Media3Engine(context: Context) : PlaybackEngine by UnusedEngine()
class VlcEngine(context: Context) : PlaybackEngine by UnusedEngine()
class MpvEngine(context: Context) : PlaybackEngine by UnusedEngine()
class UnusedEngine : PlaybackEngine {
    override fun load(uri: Uri,startMs: Long,shouldPlay: Boolean,subtitle: Uri?) = Unit
    override fun snapshot() = PlaybackSnapshot()
    override fun play() = Unit
    override fun pause() = Unit
    override fun seek(positionMs: Long) = Unit
    override fun speed(value: Float) = Unit
    override fun aspect(value: AspectMode) = Unit
    override fun release() = Unit
    override fun selectTrack(id: String, kind: TrackKind) = Unit
    override fun disableSubtitles() = Unit
}
class AudioFocusCoordinator(
    context: Context,
    private val onTransientLoss: () -> Unit,
    private val onPermanentLoss: () -> Unit,
    private val onGain: () -> Unit,
    private val onNoisy: () -> Unit
) {
    companion object {
        var grants = true
        var abandonCount = 0
        var latest: AudioFocusCoordinator? = null
        fun reset() { grants = true; abandonCount = 0; latest = null }
    }
    init { latest = this }
    fun simulateTransientLoss() = onTransientLoss()
    fun simulateFocusGain() = onGain()
    fun acquire() = grants
    fun abandon() { abandonCount++ }
    fun close() { if (latest === this) latest = null }
}
