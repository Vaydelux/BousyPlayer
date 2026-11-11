package org.videolan.libvlc
import android.content.Context
import android.net.Uri
import android.view.Surface
import java.io.FileDescriptor
class LibVLC(context: Context, options: ArrayList<String>) {
    var released = false
    fun release() { released = true }
}
class Media {
    constructor(vlc: LibVLC, uri: Uri)
    constructor(vlc: LibVLC, descriptor: FileDescriptor)
    fun addOption(option: String) {}
    fun release() {}
}
class MediaPlayer(vlc: LibVLC) {
    companion object { var last: MediaPlayer? = null }
    init { last = this }
    class Event(val type: Int) {
        companion object {
            const val Opening = 1; const val Buffering = 2; const val Playing = 3
            const val Paused = 4; const val EndReached = 5; const val EncounteredError = 6
            const val Stopped = 7
        }
    }
    fun interface EventListener { fun onEvent(event: Event) }
    data class TrackDescription(val id: Int, val name: String?)
    class Vout {
        var attached = false
        var failNextAttach = false
        var attachCount = 0
        var detachCount = 0
        var windowWidth = 0
        var windowHeight = 0
        fun setVideoSurface(surface: Surface, another: Any?) {}
        fun attachViews() {
            if (failNextAttach) {
                failNextAttach = false
                throw IllegalStateException("synthetic LibVLC surface failure")
            }
            attached = true
            attachCount++
        }
        fun setWindowSize(width: Int, height: Int) {
            windowWidth = width
            windowHeight = height
        }
        fun detachViews() { attached = false; detachCount++ }
    }
    val vlcVout = Vout()
    var media: Media? = null
    var isPlaying = false
    var isSeekable = true
    var length = 300000L
    private var _time = 0L
    val time get() = _time
    var rate = 1f
    var audioTracks: List<TrackDescription>? = listOf()
    var spuTracks: List<TrackDescription>? = listOf()
    private var _audioTrack = -1
    private var _spuTrack = -1
    val audioTrack get() = _audioTrack
    val spuTrack get() = _spuTrack
    var playCount = 0
    private var eventListener: EventListener? = null
    var failNextStop = false
    var stopCount = 0
    var releaseCount = 0
    fun emit(type: Int) { eventListener?.onEvent(Event(type)) }
    fun setEventListener(listener: EventListener?) { eventListener = listener }
    fun stop() {
        if (failNextStop) {
            failNextStop = false
            throw IllegalStateException("synthetic native stop failure")
        }
        stopCount++
        isPlaying = false
    }
    fun play() { isPlaying = true; playCount++ }
    fun pause() { isPlaying = false }
    fun setTime(value: Long) { _time = value }
    fun setAudioTrack(value: Int) { _audioTrack = value }
    fun setSpuTrack(value: Int) { _spuTrack = value }
    fun setAspectRatio(value: String?) {}
    fun setScale(value: Float) {}
    fun release() { releaseCount++ }
}
