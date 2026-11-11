import android.content.Context
import android.net.Uri
import com.vaydelux.bousyplayer.engine.*
import com.vaydelux.bousyplayer.model.MediaSource
import com.vaydelux.bousyplayer.data.RecentStore

var counter = 0
fun verify(name: String, condition: Boolean) {
    check(condition) { "FAIL: $name" }
    counter++
    println("PASS: $name")
}
class FakeEngine(val type: EngineKind, val chronology: MutableList<String>) : PlaybackEngine {
    var current = PlaybackSnapshot(position = 0L, duration = 300_000L, canSeek = true, loading = false)
    var playingRequested = false
    var stalledOnPlay = false
    var pauseCalls = 0
    var released = 0
    var loadStartMs = -1L
    override fun load(uri: Uri, startMs: Long, shouldPlay: Boolean, subtitle: Uri?) {
        chronology.add("load:${type.name}")
        loadStartMs = startMs
        playingRequested = shouldPlay
        current = current.copy(position = startMs, isPlaying = shouldPlay)
    }
    override fun snapshot() = current
    override fun play() { playingRequested = true; current = current.copy(isPlaying = !stalledOnPlay) }
    override fun pause() { pauseCalls++; playingRequested = false; current = current.copy(isPlaying = false) }
    override fun seek(positionMs: Long) { current = current.copy(position = positionMs) }
    override fun speed(value: Float) = Unit
    override fun aspect(value: AspectMode) = Unit
    override fun release() { released++; chronology.add("release:${type.name}") }
    override fun selectTrack(id: String, kind: TrackKind) = Unit
    override fun disableSubtitles() = Unit
}
val ctx = Context()
fun media(name: String) = MediaSource(Uri.parse("content://files/$name"), name, true)
fun main() {
    val store = RecentStore()
    val trace = mutableListOf<String>()
    val engines = mutableListOf<FakeEngine>()
    val session = PlayerSession(ctx, listOf(media("episode.ts"),media("trailer.mp4")), 0, store) { kind ->
        FakeEngine(kind,trace).also(engines::add)
    }
    verify("initial MPEG-TS uses MPV", session.kind == EngineKind.MPV && engines.size == 1)
    val first = engines.last()
    first.current = first.current.copy(position = 93_000L)
    session.switchEngine(EngineKind.VLC)
    verify("position captured from native before switch", engines.last().loadStartMs == 93_000L)
    verify("previous engine released once", first.released == 1)
    verify("release happens before replacement load", trace.indexOf("release:MPV") < trace.indexOf("load:VLC"))
    session.pause()
    val vlc = engines.last()
    vlc.current = vlc.current.copy(position = 145_000L)
    session.switchEngine(EngineKind.MEDIA3)
    verify("explicit pause preserved across engine switch", !engines.last().playingRequested && !session.wantsPlay)
    verify("position preserved while paused", engines.last().loadStartMs == 145_000L)
    verify("VLC released during handoff", vlc.released == 1)
    session.seekTo(500_000L)
    verify("seek clamps to native duration", engines.last().current.position == 300_000L)
    engines.last().current = engines.last().current.copy(canSeek = false)
    session.refresh()
    session.seekTo(10_000L)
    verify("non-seekable engine refuses seek", engines.last().current.position == 300_000L)
    session.onBackground()
    verify("background snapshot persisted", store.read().first { it.source.title == "episode.ts" }.positionMs == 300_000L)
    session.close(); session.close()
    verify("close is idempotent", engines.last().released == 1)

    val fallbackTrace = mutableListOf<String>()
    val fallbackEngines = mutableListOf<FakeEngine>()
    val fallback = PlayerSession(ctx, listOf(media("broken.ts")), 0, RecentStore()) { kind ->
        if (kind == EngineKind.MPV) throw IllegalStateException("native library missing")
        FakeEngine(kind, fallbackTrace).also(fallbackEngines::add)
    }
    verify("auto TS startup falls back to Media3", fallback.kind == EngineKind.MEDIA3 && fallbackEngines.size == 1)
    fallback.close()

    // A failed engine's timeline is never authoritative. Retry must preserve the
    // latest persisted position even if the constructor/load fails before first frame.
    val savedStore = RecentStore().also { it.update(media("saved.mp4"), 61_000L, 300_000L) }
    val savedTrace = mutableListOf<String>()
    val savedEngines = mutableListOf<FakeEngine>()
    var failuresLeft = 1
    val saved = PlayerSession(ctx, listOf(media("saved.mp4")), 0, savedStore) { kind ->
        if (failuresLeft-- > 0) throw IllegalStateException("temporary initialization error")
        FakeEngine(kind, savedTrace).also(savedEngines::add)
    }
    verify("failed startup reports a recoverable error", saved.engine == null && saved.snapshot.error != null)
    saved.play() // pressing Play after a failed startup must retain Continue Watching
    verify("retry restores persisted seek target", savedEngines.last().loadStartMs == 61_000L)
    saved.close()

    val focusStore = RecentStore()
    AudioFocusCoordinator.reset()
    val focusEngines = mutableListOf<FakeEngine>()
    val focus = PlayerSession(ctx, listOf(media("ending.mp4")), 0, focusStore) { kind ->
        FakeEngine(kind, mutableListOf()).also(focusEngines::add)
    }
    val active = focusEngines.last()
    active.current = active.current.copy(position = 299_000L, ended = true, isPlaying = false)
    focus.refresh()
    verify("end of file releases global audio focus", AudioFocusCoordinator.abandonCount > 0)
    verify("end of file disarms playback intent", !focus.wantsPlay)
    focus.close()

    val asyncStore = RecentStore().also { it.update(media("async.ts"), 51_000L, 300_000L) }
    val asyncTrace = mutableListOf<String>()
    val asyncEngines = mutableListOf<FakeEngine>()
    val asyncSession = PlayerSession(ctx, listOf(media("async.ts")), 0, asyncStore) { kind ->
        FakeEngine(kind, asyncTrace).also(asyncEngines::add)
    }
    val brokenNative = asyncEngines.last()
    brokenNative.current = brokenNative.current.copy(position = 0L, duration = 0L,
        canSeek = false, isPlaying = false, error = "native startup error")
    asyncSession.refresh()
    verify("asynchronous MPV startup error swaps to Media3", asyncSession.kind == EngineKind.MEDIA3 && asyncEngines.size == 2)
    verify("asynchronous startup fallback preserves resume position", asyncEngines.last().loadStartMs == 51_000L)
    verify("failed native engine released before asynchronous fallback", brokenNative.released == 1 && asyncTrace.indexOf("release:MPV") < asyncTrace.indexOf("load:MEDIA3"))
    asyncSession.close()

    val forcedTrace = mutableListOf<String>()
    val forcedEngines = mutableListOf<FakeEngine>()
    val explicit = PlayerSession(ctx, listOf(media("another.mp4")), 0, RecentStore()) { kind ->
        if (kind == EngineKind.VLC) throw IllegalStateException("native library missing")
        FakeEngine(kind, forcedTrace).also(forcedEngines::add)
    }
    explicit.switchEngine(EngineKind.VLC)
    verify("explicit failing VLC does not silently select Media3", explicit.kind == EngineKind.VLC && explicit.engine == null)
    verify("failure reports error state", explicit.snapshot.error != null)
    explicit.close()

    // Switching a saved TS while its decoder is still loading must not erase
    // the resume target when the native engine reports zero temporarily.
    val earlyStore = RecentStore().also { it.update(media("early.ts"), 82_000L, 300_000L) }
    val earlyTrace = mutableListOf<String>()
    val earlyEngines = mutableListOf<FakeEngine>()
    val early = PlayerSession(ctx, listOf(media("early.ts")), 0, earlyStore) { kind ->
        FakeEngine(kind, earlyTrace).also(earlyEngines::add)
    }
    earlyEngines.last().current = PlaybackSnapshot(
        position = 0L, duration = 0L, canSeek = false, loading = true
    )
    early.switchEngine(EngineKind.VLC)
    verify("early engine switch retains saved resume target", earlyEngines.last().loadStartMs == 82_000L)
    verify("early engine switch releases first decoder", earlyEngines.first().released == 1)
    verify("early engine switch preserves play intent", early.wantsPlay && earlyEngines.last().playingRequested)
    early.close()

    // A decoder can reveal the final container duration and even a few seconds
    // of provisional playback before it has actually sought to Continue Watching.
    val waitingStore = RecentStore().also { it.update(media("waiting.ts"), 82_000L, 300_000L) }
    val waitingEngines = mutableListOf<FakeEngine>()
    val waiting = PlayerSession(ctx, listOf(media("waiting.ts")), 0, waitingStore) { kind ->
        FakeEngine(kind, mutableListOf()).also(waitingEngines::add)
    }
    waitingEngines.last().current = PlaybackSnapshot(
        position = 0L, duration = 300_000L, canSeek = true, loading = true
    )
    waiting.refresh()
    waiting.onBackground()
    verify("metadata-only resume never overwrites Continue Watching with zero",
        waitingStore.read().first().positionMs == 82_000L)
    waiting.play()
    waitingEngines.last().current = PlaybackSnapshot(
        position = 2_000L, duration = 300_000L, canSeek = true, loading = false, isPlaying = true
    )
    waiting.refresh()
    waiting.switchEngine(EngineKind.VLC)
    verify("native early 0:02 cannot replace pending 1:22 resume",
        waitingEngines.last().loadStartMs == 82_000L)
    waitingEngines.last().current = PlaybackSnapshot(
        position = 91_000L, duration = 300_000L, canSeek = true, loading = false, isPlaying = true
    )
    waiting.refresh()
    waiting.onBackground()
    verify("acknowledged resumed timeline is saved normally",
        waitingStore.read().first().positionMs == 91_000L)
    waiting.close()

    val zeroStore = RecentStore().also { it.update(media("restart.mp4"), 100_000L, 300_000L) }
    val zeroEngines = mutableListOf<FakeEngine>()
    val zeroSession = PlayerSession(ctx, listOf(media("restart.mp4")), 0, zeroStore) { kind ->
        FakeEngine(kind, mutableListOf()).also(zeroEngines::add)
    }
    val zeroPlayer = zeroEngines.last()
    zeroPlayer.current = zeroPlayer.current.copy(position = 100_000L)
    zeroSession.refresh()
    zeroSession.startOver()
    verify("explicit start-over resets media timeline", zeroPlayer.current.position == 0L)
    zeroSession.onBackground()
    verify("explicit start-over progress is committed", zeroStore.read().first().positionMs == 0L)
    zeroSession.close()

    // AUTO fallback should not be blocked by a native decoder that learned
    // the media duration but never managed to render or advance playback.
    val metadataStore = RecentStore().also { it.update(media("meta.ts"), 110_000L, 300_000L) }
    val metadataEngines = mutableListOf<FakeEngine>()
    val metadataSession = PlayerSession(ctx, listOf(media("meta.ts")), 0, metadataStore) { kind ->
        FakeEngine(kind, mutableListOf()).also(metadataEngines::add)
    }
    metadataEngines.last().current = PlaybackSnapshot(
        position = 0L, duration = 300_000L, canSeek = false,
        loading = false, isPlaying = false, error = "decoder initialization failed"
    )
    metadataSession.refresh()
    verify("metadata-only decoder error still triggers AUTO fallback",
        metadataSession.kind == EngineKind.MEDIA3 && metadataEngines.last().loadStartMs == 110_000L)
    metadataSession.close()

    // A 1:22 pending resume must be the seek origin even if the native player
    // temporarily reports 0:02 while it is preparing the saved keyframe.
    val relativeStore = RecentStore().also { it.update(media("relative.ts"), 82_000L, 300_000L) }
    val relativeEngines = mutableListOf<FakeEngine>()
    val relative = PlayerSession(ctx, listOf(media("relative.ts")), 0, relativeStore) { kind ->
        FakeEngine(kind, mutableListOf()).also(relativeEngines::add)
    }
    val relativeNative = relativeEngines.last()
    relativeNative.current = PlaybackSnapshot(position = 2_000L, duration = 300_000L,
        canSeek = true, isPlaying = true, loading = false)
    relative.refresh()
    relative.seekBy(10_000L)
    verify("D-pad seek during pending resume uses saved position, not provisional 0:02",
        relativeNative.current.position == 92_000L)
    relative.close()

    val failedSeekStore = RecentStore().also { it.update(media("provisional.ts"), 101_000L, 300_000L) }
    val failedSeekEngines = mutableListOf<FakeEngine>()
    val provisional = PlayerSession(ctx, listOf(media("provisional.ts")), 0, failedSeekStore) { kind ->
        FakeEngine(kind, mutableListOf()).also(failedSeekEngines::add)
    }
    val provisionalNative = failedSeekEngines.last()
    provisionalNative.current = PlaybackSnapshot(position = 2_000L, duration = 300_000L,
        canSeek = true, isPlaying = true, loading = false)
    provisional.refresh()
    provisionalNative.current = provisionalNative.current.copy(isPlaying = false,
        error = "decoder failed before applying requested resume")
    provisional.refresh()
    verify("provisional playback before resume does not block automatic decoder fallback",
        provisional.kind == EngineKind.MEDIA3 && failedSeekEngines.last().loadStartMs == 101_000L)
    provisional.close()

    val replayStore = RecentStore()
    val replayTrace = mutableListOf<String>()
    val replayEngines = mutableListOf<FakeEngine>()
    val replay = PlayerSession(ctx, listOf(media("replay.mp4")), 0, replayStore) { kind ->
        FakeEngine(kind, replayTrace).also(replayEngines::add)
    }
    val completedPlayer = replayEngines.last()
    completedPlayer.current = completedPlayer.current.copy(position = 260_000L,
        duration = 300_000L, ended = true, isPlaying = false)
    replay.refresh()
    verify("native EOF records completed position even without final TimeChanged",
        replayStore.read().single().positionMs == 300_000L)
    replay.play()
    verify("Play after EOF starts a new engine from zero", replayEngines.size == 2 &&
        replayEngines.last().loadStartMs == 0L && replayEngines.last().playingRequested)
    verify("finished native player released before replay starts", completedPlayer.released == 1 &&
        replayTrace.indexOf("release:MEDIA3") < replayTrace.lastIndexOf("load:MEDIA3"))
    replay.close()

    val fatalFocusTrace = mutableListOf<FakeEngine>()
    AudioFocusCoordinator.reset()
    val fatal = PlayerSession(ctx, listOf(media("fatal.mp4")), 0, RecentStore()) { kind ->
        FakeEngine(kind, mutableListOf()).also(fatalFocusTrace::add)
    }
    fatalFocusTrace.last().current = fatalFocusTrace.last().current.copy(error = "fatal decoding error")
    fatal.refresh()
    verify("fatal playback error relinquishes system audio focus",
        AudioFocusCoordinator.abandonCount > 0)
    verify("fatal decoder error disarms playback intent", !fatal.wantsPlay)
    verify("fatal decoder is paused once before focus is abandoned", fatalFocusTrace.last().pauseCalls == 1)
    fatal.refresh()
    verify("fatal decoder is not sent repeated Pause commands on UI polls", fatalFocusTrace.last().pauseCalls == 1)
    fatal.close()

    // A visible Play action after a native error must not call play() on the dead engine.
    // Only a fresh decoder with the correct resume position can recover.
    val retryStore = RecentStore().also { it.update(media("retry.mp4"), 61_000L, 300_000L) }
    val retryTrace = mutableListOf<String>()
    val retryEngines = mutableListOf<FakeEngine>()
    val userRetry = PlayerSession(ctx, listOf(media("retry.mp4")), 0, retryStore) { kind ->
        FakeEngine(kind, retryTrace).also(retryEngines::add)
    }
    val dead = retryEngines.last()
    dead.current = dead.current.copy(position = 68_000L, error = "decoder failure", isPlaying = false)
    userRetry.refresh()
    verify("fatal error exposes Play instead of a stale Pause control", !userRetry.wantsPlay)
    userRetry.play()
    verify("Play after fatal error replaces native player", retryEngines.size == 2 && dead.released == 1)
    verify("Play after fatal error retains last good position", retryEngines.last().loadStartMs == 68_000L)
    verify("Play after fatal error re-arms play intent", userRetry.wantsPlay && retryEngines.last().playingRequested)
    verify("fatal retry releases decoder before new load", retryTrace.indexOf("release:MEDIA3") < retryTrace.lastIndexOf("load:MEDIA3"))
    userRetry.close()

    // A native demuxer can hang without reporting a failure. After a bounded
    // monotonic interval, AUTO local TS only switches once to Media3.
    var wallClock = 0L
    val hangStore = RecentStore().also { it.update(media("hang.ts"), 101_000L, 300_000L) }
    val hangTrace = mutableListOf<String>()
    val hangEngines = mutableListOf<FakeEngine>()
    val hang = PlayerSession(ctx, listOf(media("hang.ts")), 0, hangStore,
        clockMs = { wallClock }) { kind ->
        FakeEngine(kind, hangTrace).also(hangEngines::add)
    }
    val stalledDecoder = hangEngines.last()
    stalledDecoder.current = PlaybackSnapshot(position = 0L, duration = 300_000L,
        loading = true, canSeek = false)
    wallClock = 19_999L
    hang.refresh()
    verify("AUTO native startup waits through timeout grace period", hang.kind == EngineKind.MPV && hangEngines.size == 1)
    wallClock = 20_000L
    hang.refresh()
    verify("AUTO native startup stall falls back once", hang.kind == EngineKind.MEDIA3 && hangEngines.size == 2)
    verify("AUTO stall fallback retains pending resume", hangEngines.last().loadStartMs == 101_000L)
    verify("AUTO stall releases old decoder first", stalledDecoder.released == 1 &&
        hangTrace.indexOf("release:MPV") < hangTrace.indexOf("load:MEDIA3"))
    wallClock = 60_000L
    hang.refresh()
    verify("AUTO stall does not allocate additional fallback engines", hangEngines.size == 2)
    hang.close()

    // Waiting in a paused state for over 20 s is not a startup failure.
    var pausedClock = 0L
    val waitingEngines2 = mutableListOf<FakeEngine>()
    val pausedSession = PlayerSession(ctx, listOf(media("userpaused.ts")), 0,
        RecentStore(), clockMs = { pausedClock }) { kind ->
        FakeEngine(kind, mutableListOf()).also(waitingEngines2::add)
    }
    pausedSession.pause()
    pausedClock = 40_000L
    pausedSession.refresh()
    verify("user Pause cancels native timeout fallback", pausedSession.kind == EngineKind.MPV && waitingEngines2.size == 1)
    pausedSession.close()

    // Pause for a minute while the native engine is still loading. A resumed
    // attempt must have a fresh 20s active window, never an instant fallback.
    var resumeClock = 0L
    val resumedEngines = mutableListOf<FakeEngine>()
    val resumed = PlayerSession(ctx, listOf(media("resuming.ts")), 0,
        RecentStore(), clockMs = { resumeClock }) { kind ->
        FakeEngine(kind, mutableListOf()).also(resumedEngines::add)
    }
    resumedEngines.last().current = PlaybackSnapshot(loading = true)
    resumed.pause()
    resumeClock = 60_000L
    resumedEngines.last().stalledOnPlay = true
    resumed.play()
    verify("resuming a paused native startup does not immediately fall back",
        resumed.kind == EngineKind.MPV && resumedEngines.size == 1)
    resumeClock = 79_999L
    resumed.refresh()
    verify("resumed startup receives its full active grace interval",
        resumed.kind == EngineKind.MPV && resumedEngines.size == 1)
    resumeClock = 80_000L
    resumed.refresh()
    verify("resumed stalled startup eventually falls back once",
        resumed.kind == EngineKind.MEDIA3 && resumedEngines.size == 2)
    resumed.close()

    // Temporary audio-focus interruption also suspends the startup deadline.
    var focusClock = 0L
    AudioFocusCoordinator.reset()
    val focusWaitEngines = mutableListOf<FakeEngine>()
    val focusWaiting = PlayerSession(ctx, listOf(media("focus-wait.ts")), 0,
        RecentStore(), clockMs = { focusClock }) { kind ->
        FakeEngine(kind, mutableListOf()).also(focusWaitEngines::add)
    }
    focusWaitEngines.last().current = PlaybackSnapshot(loading = true)
    val focusControl = checkNotNull(AudioFocusCoordinator.latest)
    focusClock = 4_000L
    focusControl.simulateTransientLoss()
    verify("focus interruption pauses decoder without discarding user Play intent",
        focusWaiting.wantsPlay && !focusWaitEngines.last().playingRequested)
    focusClock = 90_000L
    focusWaitEngines.last().stalledOnPlay = true
    focusControl.simulateFocusGain()
    verify("focus gain restarts native startup budget rather than instant fallback",
        focusWaiting.kind == EngineKind.MPV && focusWaitEngines.size == 1)
    focusClock = 109_999L
    focusWaiting.refresh()
    verify("focus-restored startup waits its full active grace interval",
        focusWaiting.kind == EngineKind.MPV && focusWaitEngines.size == 1)
    focusClock = 110_000L
    focusWaiting.refresh()
    verify("focus-restored startup eventually falls back once",
        focusWaiting.kind == EngineKind.MEDIA3 && focusWaitEngines.size == 2)
    focusWaiting.close()


    // AUTO Media3 startup should use bundled LibVLC once for a local MKV
    // when the initial decoder fails before rendering an authoritative timeline.
    val mkvTrace = mutableListOf<String>()
    val mkvEngines = mutableListOf<FakeEngine>()
    val mkvStore = RecentStore().also { it.update(media("show.mkv"), 65_000L, 300_000L) }
    val mkvSession = PlayerSession(ctx, listOf(media("show.mkv")), 0, mkvStore) { kind ->
        FakeEngine(kind, mkvTrace).also(mkvEngines::add)
    }
    verify("local MKV first tries Media3", mkvSession.kind == EngineKind.MEDIA3)
    mkvEngines.last().current = PlaybackSnapshot(position = 0L, duration = 300_000L,
        loading = false, error = "unsupported codec")
    mkvSession.refresh()
    verify("local MKV codec startup failure selects VLC once",
        mkvSession.kind == EngineKind.VLC && mkvEngines.size == 2)
    verify("local MKV fallback preserves saved offset",
        mkvEngines.last().loadStartMs == 65_000L)
    verify("local MKV releases failed decoder before replacement",
        mkvEngines.first().released == 1 &&
        mkvTrace.indexOf("release:MEDIA3") < mkvTrace.indexOf("load:VLC"))
    mkvSession.close()

    val mkvPausedEngines = mutableListOf<FakeEngine>()
    val mkvPaused = PlayerSession(ctx, listOf(media("paused.mkv")), 0, RecentStore()) { kind ->
        FakeEngine(kind, mutableListOf()).also(mkvPausedEngines::add)
    }
    mkvPaused.pause()
    mkvPausedEngines.last().current = mkvPausedEngines.last().current.copy(error = "unsupported codec")
    mkvPaused.refresh()
    verify("paused MKV never silently changes playback engines",
        mkvPaused.kind == EngineKind.MEDIA3 && mkvPausedEngines.size == 1)
    mkvPaused.close()

    val mkvEarlyEngines = mutableListOf<FakeEngine>()
    val mkvEarly = PlayerSession(ctx, listOf(media("earlyfail.mkv")), 0, RecentStore()) { kind ->
        if (kind == EngineKind.MEDIA3) throw IllegalStateException("Media3 unavailable")
        FakeEngine(kind, mutableListOf()).also(mkvEarlyEngines::add)
    }
    verify("synchronous local MKV startup failure tries VLC",
        mkvEarly.kind == EngineKind.VLC && mkvEarlyEngines.size == 1)
    mkvEarly.close()

    val mkvReadyEngines = mutableListOf<FakeEngine>()
    val mkvReady = PlayerSession(ctx, listOf(media("alreadyplaying.mkv")), 0, RecentStore()) { kind ->
        FakeEngine(kind, mutableListOf()).also(mkvReadyEngines::add)
    }
    mkvReadyEngines.last().current = mkvReadyEngines.last().current.copy(
        position = 14_000L, isPlaying = true)
    mkvReady.refresh()
    mkvReadyEngines.last().current = mkvReadyEngines.last().current.copy(
        error = "late media I/O failure", isPlaying = false)
    mkvReady.refresh()
    verify("no automatic engine swap after MKV began playing",
        mkvReady.kind == EngineKind.MEDIA3 && mkvReadyEngines.size == 1)
    mkvReady.close()

    println("PASS: $counter session ownership/behavior checks")
}
