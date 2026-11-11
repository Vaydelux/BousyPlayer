package com.vaydelux.bousyplayer.engine

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.vaydelux.bousyplayer.data.RecentStore
import com.vaydelux.bousyplayer.model.MediaSource
import com.vaydelux.bousyplayer.model.SourcePolicy

/** The only foreground owner of native player resources and the current queue item. */
class PlayerSession(
    private val context: Context,
    val playlist: List<MediaSource>,
    initialIndex: Int,
    private val recents: RecentStore,
    // A recovered Activity must never unexpectedly restart audio/video.
    private val initialPlay: Boolean = true,
    // Monotonic clock injectable for deterministic startup-stall regression tests.
    private val clockMs: () -> Long = { System.nanoTime() / 1_000_000L },
    // Allows deterministic ownership/failure tests without loading Android native codecs.
    private val engineFactory: (EngineKind) -> PlaybackEngine = { candidate ->
        when (candidate) {
            EngineKind.MEDIA3 -> Media3Engine(context)
            EngineKind.VLC -> VlcEngine(context)
            EngineKind.MPV -> MpvEngine(context)
        }
    }
) {
    var index by mutableStateOf(initialIndex.coerceIn(0, (playlist.size - 1).coerceAtLeast(0)))
        private set
    var engine by mutableStateOf<PlaybackEngine?>(null)
        private set
    var snapshot by mutableStateOf(PlaybackSnapshot())
        private set
    var speed by mutableStateOf(1.0f)
        private set
    var aspect by mutableStateOf(AspectMode.FIT)
        private set
    var subtitleAppearance by mutableStateOf(SubtitleAppearance())
        private set
    private val healthRecorder = PlaybackHealthRecorder()
    val playbackHealth: PlaybackHealth get() = healthRecorder.summary
    var kind by mutableStateOf(EngineKind.MEDIA3)
        private set
    var message by mutableStateOf<String?>(null)
        private set
    var wantsPlay by mutableStateOf(initialPlay)
        private set
    private var externalSubtitle: Uri? = null
    private var closed = false
    private var lastSavedPosition = -1L
    private var lastSavedIndex = -1
    private var manualEngine = false
    // Remember the position chosen for the last load attempt. A native constructor/load
    // failure has no decoder timeline, so Retry must not replace a saved resume with 0.
    private var lastAttemptedPositionMs = 0L
    private var nativeStartupFallbackAttempted = false
    private var compatibilityFallbackAttempted = false
    private var engineEverReady = false
    private var nativeStartupAtMs = 0L

    /**
     * A native startup deadline measures continuous *active* playback intent.
     * Time spent in a user Pause, app background, or transient audio-focus loss
     * must not count toward the 20-second automatic MPV fallback.
     */
    private fun restartUnconfirmedNativeStartupWindow() {
        if (kind == EngineKind.MPV && !engineEverReady) {
            nativeStartupAtMs = clockMs()
        }
    }
    // A decoder may know the container duration before it applies a saved seek.
    // Do not overwrite Continue Watching or switch from a transient 0:00/0:02
    // timeline until its reported position acknowledges the requested resume.
    private var resumePositionAcknowledged = true
    private var pendingSeekToStart = false
    private var suspendedByFocus = false
    private val audioFocus = AudioFocusCoordinator(
        context,
        onTransientLoss = {
            if (wantsPlay && !closed) {
                suspendedByFocus = true
                runCatching { engine?.pause() }
                refresh()
            }
        },
        onPermanentLoss = {
            suspendedByFocus = false
            if (!closed) {
                wantsPlay = false
                runCatching { engine?.pause() }
                refresh()
                saveProgress()
                message = "Playback paused: audio focus lost"
            }
        },
        onGain = {
            if (PlaybackPolicy.resumeAfterFocusGain(wantsPlay, suspendedByFocus, closed)) {
                restartUnconfirmedNativeStartupWindow()
                suspendedByFocus = false
                runCatching { engine?.play() }
                refresh()
            }
        },
        onNoisy = { if (!closed) pause() }
    )

    private fun acknowledgePosition(reported: PlaybackSnapshot): Boolean {
        if (pendingSeekToStart) {
            // The previous native time may remain visible after a user requests
            // Start Over. Do not persist that stale old time as the new progress.
            if (reported.position > 5_000L) return false
            pendingSeekToStart = false
            resumePositionAcknowledged = true
        }
        if (resumePositionAcknowledged) return true
        if (PlaybackResumePolicy.resumePositionAcknowledged(
                lastAttemptedPositionMs, reported.position, reported.ended
            )) {
            resumePositionAcknowledged = true
        }
        return resumePositionAcknowledged
    }

    private fun latestPosition(): Long {
        engine?.let { current ->
            snapshot = runCatching { current.snapshot() }.getOrDefault(snapshot)
        }
        return if (acknowledgePosition(snapshot)) snapshot.position.coerceAtLeast(0L)
        else lastAttemptedPositionMs
    }

    val source: MediaSource get() = playlist[index]
    private fun isLocalMatroska(): Boolean = source.uri.scheme == "content" &&
        EngineKind.isMatroska(source.title, source.uri.lastPathSegment.orEmpty())

    init {
        require(playlist.isNotEmpty())
        kind = EngineKind.preferredFor(source.title, source.uri.lastPathSegment.orEmpty())
        open(index, null)
        if (!initialPlay && engine != null) {
            message = "Previous local video restored. Press Play to continue."
        }
    }

    private fun captureCurrentProgress() {
        // Saving an in-flight native seek as zero destroys an otherwise valid resume.
        latestPosition()
        saveProgress()
    }

    private fun open(newIndex: Int, requestedPosition: Long?) {
        if (closed || newIndex !in playlist.indices) return
        val switchingItem = newIndex != index
        if (switchingItem) {
            nativeStartupFallbackAttempted = false
            compatibilityFallbackAttempted = false
        }
        captureCurrentProgress()
        engine?.let { old ->
            // Release old native resources before allocating any new engine.
            healthRecorder.engineChanged()
            engine = null
            runCatching { old.release() }
        }
        index = newIndex
        snapshot = PlaybackSnapshot()
        engineEverReady = false
        nativeStartupAtMs = clockMs()
        val last = recents.read().firstOrNull { it.source.uri == source.uri }
        val resumeAt = if (requestedPosition != null) requestedPosition.coerceAtLeast(0L)
            else PlaybackResumePolicy.startingPosition(last?.positionMs ?: 0L, last?.durationMs ?: 0L)
        lastAttemptedPositionMs = resumeAt
        resumePositionAcknowledged = resumeAt == 0L
        pendingSeekToStart = false
        val shouldStart = wantsPlay && !suspendedByFocus && audioFocus.acquire()
        val focusDenied = wantsPlay && !shouldStart && !suspendedByFocus
        if (focusDenied) {
            wantsPlay = false
            message = "Audio focus unavailable: press Play to try again"
        }
        // Try the *automatically preferred* native demuxer first, but do not strand
        // users on startup when that native library is unavailable. Explicit engine
        // choices always remain explicit; network/decoder runtime errors stay visible.
        val selected = kind
        val candidates = PlaybackPolicy.engineCandidates(selected, manualEngine, isLocalMatroska())
        var lastError: Throwable? = null
        for (candidate in candidates) {
            val next = try {
                engineFactory(candidate)
            } catch (failure: Throwable) {
                lastError = failure
                continue
            }
            try {
                next.speed(speed)
                next.aspect(aspect)
                // Unavailable caption styling must not prevent video playback.
                runCatching { next.applySubtitleAppearance(subtitleAppearance) }
                next.load(source.uri, resumeAt, shouldStart, externalSubtitle)
                engine = next
                kind = candidate
                if (selected == EngineKind.MEDIA3 && candidate == EngineKind.VLC) {
                    compatibilityFallbackAttempted = true
                }
                message = when {
                    focusDenied -> "Audio focus unavailable: press Play to try again"
                    candidate != selected -> "${selected.label} unavailable; using ${candidate.label}"
                    else -> null
                }
                snapshot = next.snapshot()
                return
            } catch (failure: Throwable) {
                lastError = failure
                runCatching { next.release() }
            }
        }
        audioFocus.abandon()
        message = "Could not open ${source.title} with ${selected.label} (${lastError?.javaClass?.simpleName ?: "Unknown"})"
        snapshot = PlaybackSnapshot(loading = false, error = message)

    }

    fun refresh() {
        if (closed) return
        engine?.let { active ->
            snapshot = try { active.snapshot() } catch (e: Exception) {
                snapshot.copy(loading = false, error = "Playback state unavailable")
            }
        }
        healthRecorder.sample(
            buffering = snapshot.loading && wantsPlay && !suspendedByFocus && snapshot.error == null,
            error = snapshot.error != null, nowMs = clockMs()
        )
        // Container metadata alone does not prove successful decoder startup.
        // Keep AUTO local-file fallback possible when native demuxing discovers
        // duration but fails before playback actually begins.
        // A native decoder can briefly advance at 0:02 while its requested 1:22
        // resume seek is still pending. That provisional playback does not count as
        // a successful startup, or a subsequent decoder failure would never fall back.
        val timelineAcknowledged = acknowledgePosition(snapshot)
        if (timelineAcknowledged &&
            (snapshot.isPlaying || snapshot.position > 0L || snapshot.ended)
        ) engineEverReady = true
        val localDocument = source.uri.scheme == "content"
        val stalled = PlaybackPolicy.shouldAutoFallbackNativeStall(
            kind, manualEngine, localDocument, wantsPlay, suspendedByFocus,
            engineEverReady, nativeStartupFallbackAttempted,
            (clockMs() - nativeStartupAtMs).coerceAtLeast(0L)
        )
        if (PlaybackPolicy.shouldAutoFallbackNativeStartup(
                kind, manualEngine, localDocument, wantsPlay,
                engineEverReady, nativeStartupFallbackAttempted,
                snapshot.error != null || stalled
            )) {
            nativeStartupFallbackAttempted = true
            val initial = latestPosition()
            val timedOutWithoutError = stalled && snapshot.error == null
            kind = EngineKind.MEDIA3
            open(index, initial)
            if (engine != null) message = if (timedOutWithoutError) {
                "libmpv startup timed out; switched to Media3"
            } else {
                "libmpv could not start; switched to Media3"
            }
            return
        }
        if (PlaybackPolicy.shouldAutoFallbackMatroska(
                kind, manualEngine, isLocalMatroska(), wantsPlay,
                engineEverReady, compatibilityFallbackAttempted, snapshot.error != null
            )) {
            compatibilityFallbackAttempted = true
            val position = latestPosition()
            kind = EngineKind.VLC
            open(index, position)
            if (engine != null) message = "Media3 could not start this MKV; using LibVLC"
            return
        }
        if (snapshot.ended) {
            // EndReached can arrive before the final native timestamp. Completion is
            // authoritative; do not leave a nearly-completed Continue Watching row.
            wantsPlay = false
            suspendedByFocus = false
            audioFocus.abandon()
            saveProgress()
        } else if (snapshot.error != null) {
            // Native decoders may report a fatal error while their audio renderer
            // is still active. Pause once before relinquishing Android audio focus;
            // never trust UI state alone to silence a failed native engine.
            if (wantsPlay) runCatching { engine?.pause() }
            wantsPlay = false
            suspendedByFocus = false
            audioFocus.abandon()
        } else if (source.isDocument && snapshot.duration > 0L &&
            kotlin.math.abs(snapshot.position - lastSavedPosition) > 5_000L
        ) saveProgress()
    }

    /** Stable seek target for a brief remote HUD overlay while native time is still stale. */
    fun seekDisplayPosition(): Long =
        if (!resumePositionAcknowledged || pendingSeekToStart) lastAttemptedPositionMs
        else snapshot.position.coerceAtLeast(0L)

    fun playPause() { if (wantsPlay) pause() else play() }
    fun play() {
        if (closed) return
        if (engine == null || snapshot.error != null) {
            // Failed native players generally cannot restart with play() alone.
            // User-requested Play on an error explicitly replaces the engine.
            wantsPlay = true
            suspendedByFocus = false
            retry()
            return
        }
        if (snapshot.ended) {
            // Replay from EOF is uniform across Media3, mpv and VLC. Not all
            // native demuxers can seek backward after EndReached, so replace the
            // finished player with one clean session at 0:00.
            wantsPlay = true
            suspendedByFocus = false
            open(index, 0L)
            return
        }
        if (!audioFocus.acquire()) {
            // A denied focus request must never leave an already-running decoder audible.
            runCatching { engine?.pause() }
            message = "Audio focus unavailable: cannot start sound"
            wantsPlay = false
            refresh()
            return
        }
        // A user may resume after hours of Pause. Give a still-unconfirmed native
        // startup a fresh active window rather than triggering instant fallback.
        if (!wantsPlay || suspendedByFocus) restartUnconfirmedNativeStartupWindow()
        suspendedByFocus = false
        wantsPlay = true
        message = null
        runCatching { engine?.play() }
        refresh()
    }
    fun pause() {
        if (closed) return
        wantsPlay = false
        suspendedByFocus = false
        runCatching { engine?.pause() }
        audioFocus.abandon()
        refresh()
        saveProgress()
    }
    fun seekTo(ms: Long) {
        if (!snapshot.canSeek || snapshot.duration <= 0L) return
        val target = SourcePolicy.clampSeek(ms, snapshot.duration)
        val targetEngine = engine ?: return
        val accepted = runCatching { targetEngine.seek(target) }.isSuccess
        if (!accepted) return
        // Explicit seek supersedes any pending automatic resume. Wait for the
        // engine's reported position before persisting the new timeline.
        lastAttemptedPositionMs = target
        resumePositionAcknowledged = false
        pendingSeekToStart = target == 0L
        refresh()
    }
    fun seekBy(deltaMs: Long) {
        // During async Resume, 0:00 is a provisional decoder timestamp. D-pad
        // seeking should be relative to the pending resume target, not reset it.
        val from = latestPosition()
        seekTo(if (deltaMs > 0L && from > Long.MAX_VALUE - deltaMs) Long.MAX_VALUE
            else if (deltaMs < 0L && from < Long.MIN_VALUE - deltaMs) Long.MIN_VALUE
            else from + deltaMs)
    }
    fun startOver() = seekTo(0L)
    fun changeSpeed(next: Float) {
        speed = next.coerceIn(0.5f, 2f)
        runCatching { engine?.speed(speed) }
    }
    fun changeAspect(next: AspectMode) {
        aspect = next
        runCatching { engine?.aspect(next) }
    }
    fun changeSubtitleAppearance(next: SubtitleAppearance) {
        if (closed) return
        subtitleAppearance = next
        val accepted = runCatching { engine?.applySubtitleAppearance(next) == true }.getOrDefault(false)
        message = if (accepted) {
            "Subtitle appearance applied. Availability depends on the subtitle format."
        } else {
            "Live subtitle appearance is unavailable in ${kind.label}; try Media3 or libmpv."
        }
    }
    val diagnostic: PlaybackDiagnostic?
        get() = snapshot.error?.let { PlaybackDiagnosticPolicy.from(kind, it) }

    fun setTrack(id: String, type: TrackKind) {
        if (closed) return
        // Track IDs belong to one engine instance; reject stale menu selections.
        val track = snapshot.tracks.firstOrNull { it.id == id && it.kind == type }
        if (track == null) {
            message = "This track is no longer available. Reopen Audio / CC."
            return
        }
        val active = engine ?: return
        val failed = runCatching { active.selectTrack(id, type) }.isFailure
        if (failed) {
            message = "Track selection failed. Try another audio or subtitle track."
            return
        }
        refresh()
        message = "Requested ${if (type == TrackKind.AUDIO) "audio" else "subtitle"} track. Checking playback..."
    }
    fun disableSubtitles() {
        if (closed) return
        val active = engine ?: return
        val failed = runCatching { active.disableSubtitles() }.isFailure
        if (failed) {
            message = "Could not disable captions with this engine."
            return
        }
        refresh()
        message = "Subtitle disable request sent."
    }
    fun addSubtitle(uri: Uri) {
        if (closed) return
        if (uri.scheme != "content") {
            message = "Choose a subtitle through the Android document picker."
            return
        }
        externalSubtitle = uri
        val position = latestPosition()
        if (kind != EngineKind.MEDIA3) { kind = EngineKind.MEDIA3; manualEngine = true }
        open(index, position)
        if (engine != null && snapshot.error == null) {
            message = "External subtitle requested through Media3. Check available tracks."
        }
    }
    fun switchEngine(nextKind: EngineKind) {
        if (closed || nextKind == kind) return
        val position = latestPosition()
        kind = nextKind
        manualEngine = true
        if (kind != EngineKind.MEDIA3) externalSubtitle = null
        open(index, position)
    }
    private fun goTo(nextIndex: Int) {
        if (closed || nextIndex !in playlist.indices || nextIndex == index) return
        wantsPlay = true
        externalSubtitle = null
        if (!manualEngine) kind = EngineKind.preferredFor(
            playlist[nextIndex].title, playlist[nextIndex].uri.lastPathSegment.orEmpty()
        )
        open(nextIndex, null)
    }
    fun next() = goTo(index + 1)
    fun previous() = goTo(index - 1)
    fun openPlaylistIndex(value: Int) = goTo(value)
    fun retry() {
        if (closed) return
        // Retry is an explicit user intent to resume; fatal errors disarm wantsPlay.
        wantsPlay = true
        suspendedByFocus = false
        // An asynchronous decoder error may arrive before any real timeline is reported.
        // In that case preserve the original resume target instead of starting at 0:00.
        val current = latestPosition()
        val position = if (current > 0L) current else lastAttemptedPositionMs
        open(index, position)
    }

    private fun saveProgress() {
        if (!source.isDocument || snapshot.duration <= 0L || snapshot.position < 0L ||
            !acknowledgePosition(snapshot)) return
        if (index == lastSavedIndex &&
            (if (snapshot.ended) snapshot.duration else snapshot.position) == lastSavedPosition
        ) return
        // An explicit native EOF can precede its final TimeChanged notification.
        // Persist completion rather than an old mid-file position.
        val savedPosition = if (snapshot.ended) snapshot.duration else snapshot.position
        recents.update(source, savedPosition, snapshot.duration)
        lastSavedIndex = index
        lastSavedPosition = savedPosition
    }
    fun onBackground() {
        // Save the decoder's authoritative timeline before a background interruption.
        latestPosition()
        pause()
    }
    fun close() {
        if (closed) return
        captureCurrentProgress()
        closed = true
        engine?.let { runCatching { it.release() } }
        engine = null
        audioFocus.close()
    }
}
