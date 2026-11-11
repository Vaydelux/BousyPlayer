package com.vaydelux.bousyplayer.engine

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.SurfaceHolder
import android.view.SurfaceView
import java.util.concurrent.atomic.AtomicInteger
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer

/** Native compatibility engine. Native calls remain on the owner/main thread. */
class VlcEngine(private val context: Context) : PlaybackEngine {
    override val kind = EngineKind.VLC
    private val vlc = LibVLC(context.applicationContext, arrayListOf("--no-video-title-show"))
    private val native = try { MediaPlayer(vlc) } catch (failure: Throwable) {
        vlc.release()
        throw failure
    }
    private var activeDescriptor: ParcelFileDescriptor? = null
    @Volatile private var errorMessage: String? = null
    @Volatile private var finished = false
    @Volatile private var opening = false
    @Volatile private var requestedPlay = false
    private var attached = false
    private var mediaConfigured = false
    @Volatile private var released = false
    private val pauseRecoveries = AtomicInteger(0)
    @Volatile private var playbackStarted = false
    @Volatile private var lastTrackPollMs = -1L
    @Volatile private var cachedTracks: List<MediaTrack> = emptyList()
    private var recoveryPending = false // main-thread only; deduplicates native Paused events
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    @Volatile private var loadGeneration = 0L
    private var surfaceWidth = 0
    private var surfaceHeight = 0
    private var aspectMode = AspectMode.FIT
    override val view: SurfaceView = SurfaceView(context).apply {
        keepScreenOn = true
        holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                if (released || !holder.surface.isValid) return
                runCatching {
                    native.vlcVout.setVideoSurface(holder.surface, null)
                    native.vlcVout.attachViews()
                    attached = true
                    native.vlcVout.setWindowSize(width.coerceAtLeast(1), height.coerceAtLeast(1))
                    applyAspect()
                    // The Activity creates the SurfaceView after PlayerSession.load().
                    // Don't start decoding before VLC has a video output surface.
                    if (PlaybackPolicy.canStartNative(requestedPlay, mediaConfigured, attached) &&
                        !native.isPlaying) native.play()
                }.onFailure { errorMessage = "LibVLC cannot attach video output" }
            }
            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                surfaceWidth = width
                surfaceHeight = height
                if (attached) {
                    runCatching {
                        native.vlcVout.setWindowSize(width, height)
                        applyAspect()
                    }.onFailure { errorMessage = "LibVLC display resize failed" }
                }
            }
            override fun surfaceDestroyed(holder: SurfaceHolder) { detachView() }
        })
    }

    init {
        try {
        native.setEventListener { event ->
            if (released) return@setEventListener
            when (event.type) {
                MediaPlayer.Event.Opening, MediaPlayer.Event.Buffering -> opening = true
                MediaPlayer.Event.Playing -> {
                    lastTrackPollMs = -1L
                    opening = false
                    playbackStarted = true
                    // Pause is a toggle on this LibVLC version: respect explicit intent.
                    val generation = loadGeneration
                    main.post {
                        if (!released && generation == loadGeneration) {
                            recoveryPending = false
                            if (!requestedPlay && native.isPlaying) native.pause()
                            runCatching { attemptResumeSeek(generation, 0) }.onFailure {
                                errorMessage = "LibVLC could not restore playback position"
                            }
                            if (requestedPlay && pauseRecoveries.get() > 0) {
                                val timeAtStart = runCatching { native.time }.getOrDefault(-1L)
                                main.postDelayed({
                                    if (!released && generation == loadGeneration && requestedPlay &&
                                        runCatching { native.isPlaying }.getOrDefault(false) &&
                                        runCatching { native.time }.getOrDefault(-1L) - timeAtStart >= 3_000L
                                    ) pauseRecoveries.set(0)
                                }, 5_000L)
                            }
                        }
                    }
                }
                MediaPlayer.Event.Paused -> {
                    opening = false
                    // VLC can emit Paused during native seeking/rebuffering without a
                    // user Pause. Restore Play only when explicit intent remains armed,
                    // using a bounded recovery budget to prevent endless retry loops.
                    val generation = loadGeneration
                    main.post {
                        if (released || generation != loadGeneration || !requestedPlay || !playbackStarted || finished) {
                            return@post
                        }
                        if (recoveryPending || runCatching { native.isPlaying }.getOrDefault(false)) return@post
                        if (PlaybackPolicy.retryUnexpectedNativePause(
                                requestedPlay, playbackStarted, finished, pauseRecoveries.get())) {
                            pauseRecoveries.incrementAndGet()
                            recoveryPending = true
                            opening = true
                            runCatching { native.play() }.onFailure {
                                errorMessage = "LibVLC could not resume after a native pause"
                                recoveryPending = false
                                opening = false
                            }
                            // A native Play call may return without ever rendering audio/video.
                            // Do not leave the UI in an endless recovery state.
                            main.postDelayed({
                                if (!released && generation == loadGeneration && recoveryPending && requestedPlay) {
                                    recoveryPending = false
                                    opening = false
                                    if (!runCatching { native.isPlaying }.getOrDefault(false)) {
                                        errorMessage = "LibVLC did not recover playback after a native pause"
                                    }
                                }
                            }, 2_500L)
                        } else {
                            errorMessage = "LibVLC stopped repeatedly without a user pause"
                        }
                    }
                }
                MediaPlayer.Event.EndReached -> { finished = true; opening = false }
                MediaPlayer.Event.EncounteredError -> {
                    errorMessage = "LibVLC could not decode or open this source"
                    opening = false
                }
                MediaPlayer.Event.Stopped -> {
                    if (requestedPlay && playbackStarted && !finished) {
                        errorMessage = "LibVLC stopped unexpectedly"
                    }
                }
            }
        }
        } catch (failure: Throwable) {
            released = true
            main.removeCallbacksAndMessages(null)
            runCatching { native.release() }
            runCatching { vlc.release() }
            throw failure
        }
    }

    override fun load(uri: Uri, startMs: Long, shouldPlay: Boolean, subtitle: Uri?) {
        check(!released)
        errorMessage = null
        finished = false
        opening = true
        playbackStarted = false
        pauseRecoveries.set(0)
        cachedTracks = emptyList()
        lastTrackPollMs = -1L
        recoveryPending = false
        requestedPlay = shouldPlay
        loadGeneration++
        resumePosition = startMs.coerceAtLeast(0L)
        // Network and local SAF URIs are supplied directly. Native engine capability
        // varies with providers and formats; surface failures remain visible.
        // Explicitly stop previous native demuxer before closing a previous SAF descriptor.
        runCatching { native.stop() }
        activeDescriptor?.close()
        activeDescriptor = null
        mediaConfigured = false
        val nextFd = if (uri.scheme == "content")
            context.contentResolver.openFileDescriptor(uri, "r") ?: error("Document unavailable")
        else null
        val media = try {
            if (nextFd != null) Media(vlc, nextFd.fileDescriptor) else Media(vlc, uri)
        } catch (failure: Throwable) {
            nextFd?.close()
            throw failure
        }
        try {
            media.addOption(":network-caching=600")
            native.media = media
            activeDescriptor = nextFd
            mediaConfigured = true
        } catch (failure: Throwable) {
            nextFd?.close()
            throw failure
        } finally { media.release() }
        // Unlike a play-then-pause sequence, this never emits audio if the user
        // explicitly paused before switching engines.
        if (PlaybackPolicy.canStartNative(shouldPlay, mediaConfigured, attached)) native.play()
        else if (!shouldPlay) opening = false
        // The first Playing event schedules a bounded seek when native timing is reliable.
        // External subtitles are selected using Media3 by PlayerSession.
    }

    private var resumePosition = 0L

    private fun attemptResumeSeek(generation: Long, attempt: Int) {
        if (released || generation != loadGeneration || resumePosition <= 0L) return
        if (native.isSeekable) {
            runCatching { native.setTime(resumePosition) }.onSuccess {
                resumePosition = 0L
            }.onFailure {
                errorMessage = "LibVLC could not seek to the saved position"
            }
        } else if (attempt < 8) {
            main.postDelayed({ attemptResumeSeek(generation, attempt + 1) }, 250L)
        }
    }

    override fun snapshot(): PlaybackSnapshot {
        if (released) return PlaybackSnapshot(loading = false)
        val now = SystemClock.elapsedRealtime()
        if (lastTrackPollMs < 0L || now - lastTrackPollMs >= 2_000L) {
            val audioTracks = runCatching {
                native.audioTracks?.map { MediaTrack("a:${it.id}", it.name ?: "Audio", TrackKind.AUDIO, it.id == native.audioTrack) }
            }.getOrNull().orEmpty()
            val subs = runCatching {
                native.spuTracks?.filter { it.id >= 0 }?.map {
                    MediaTrack("s:${it.id}", it.name ?: "Subtitle", TrackKind.SUBTITLE, it.id == native.spuTrack)
                }
            }.getOrNull().orEmpty()
            cachedTracks = audioTracks + subs
            lastTrackPollMs = now
        }
        val nativePlaying = runCatching { native.isPlaying }.getOrDefault(false)
        return PlaybackSnapshot(
            position = runCatching { native.time }.getOrDefault(0L).coerceAtLeast(0),
            duration = runCatching { native.length }.getOrDefault(0L).coerceAtLeast(0),
            buffered = 0,
            isPlaying = nativePlaying,
            canSeek = runCatching { native.isSeekable }.getOrDefault(false),
            loading = opening && !nativePlaying,
            ended = finished,
            error = errorMessage,
            tracks = cachedTracks
        )
    }
    override fun play() {
        requestedPlay = true
        if (released) return
        if (finished) {
            finished = false
            if (native.isSeekable) native.setTime(0L)
        }
        if (PlaybackPolicy.canStartNative(requestedPlay, mediaConfigured, attached) &&
            !native.isPlaying) native.play()
    }
    override fun pause() {
        requestedPlay = false
        recoveryPending = false
        if (!released && native.isPlaying) native.pause()
    }
    override fun seek(positionMs: Long) {
        if (!released && native.isSeekable) native.setTime(positionMs.coerceAtLeast(0))
    }
    override fun speed(value: Float) { if (!released) native.rate = value }
    override fun aspect(value: AspectMode) { aspectMode = value; applyAspect() }
    private fun applyAspect() {
        if (released || !attached) return
        when (aspectMode) {
            AspectMode.FIT -> { native.setAspectRatio(null); native.setScale(0f) }
            AspectMode.STRETCH -> {
                if (surfaceWidth > 0 && surfaceHeight > 0) {
                    native.setAspectRatio("$surfaceWidth:$surfaceHeight")
                    native.setScale(0f)
                }
            }
            AspectMode.ZOOM -> { native.setAspectRatio(null); native.setScale(1.3f) }
        }
    }
    override fun selectTrack(id: String, kind: TrackKind) {
        val number = id.substringAfter(':', "").toIntOrNull() ?: return
        if (released) return
        if (kind == TrackKind.AUDIO) native.setAudioTrack(number) else native.setSpuTrack(number)
        lastTrackPollMs = -1L
    }
    override fun disableSubtitles() { if (!released) { native.setSpuTrack(-1); lastTrackPollMs = -1L } }
    override fun detachView() {
        if (attached) { runCatching { native.vlcVout.detachViews() }; attached = false }
    }
    override fun release() {
        if (released) return
        released = true
        requestedPlay = false
        mediaConfigured = false
        main.removeCallbacksAndMessages(null)
        runCatching { native.setEventListener(null) }
        detachView()
        runCatching { native.stop() }
        runCatching { native.release() }
        runCatching { activeDescriptor?.close() }
        activeDescriptor = null
        runCatching { vlc.release() }
    }
}
