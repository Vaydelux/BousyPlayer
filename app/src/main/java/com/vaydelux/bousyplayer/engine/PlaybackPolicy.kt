package com.vaydelux.bousyplayer.engine

/** Android-free policy contracts, so engine-selection logic can be tested on the JVM. */
enum class EngineKind(val label: String) {
    MEDIA3("Media3"), MPV("libmpv"), VLC("LibVLC");
    companion object {
        /** TS is treated as a best-effort native-demuxer case; not an assertion of seekability. */
        fun preferredFor(vararg names: String): EngineKind =
            if (names.any { name ->
                    name.substringBefore('?').substringAfterLast('.').lowercase() in
                        setOf("ts", "m2ts", "mts")
                }) MPV else MEDIA3
        /** Known Matroska suffix only; unknown formats retain the Media3 default. */
        fun isMatroska(vararg names: String): Boolean =
            names.any { name -> name.substringBefore('?').substringAfterLast('.')
                .equals("mkv", ignoreCase = true) }
    }
}
enum class AspectMode(val label: String) { FIT("Fit"), ZOOM("Zoom"), STRETCH("Stretch") }
enum class TrackKind { AUDIO, SUBTITLE }

/** Preserves explicit Pause through switching, seeks and buffering. */
object PlaybackPolicy {
    fun shouldResume(playRequested: Boolean, mediaEnded: Boolean, fatalError: Boolean): Boolean =
        playRequested && !mediaEnded && !fatalError

    fun resumeAfterFocusGain(playRequested: Boolean, suspendedByFocus: Boolean, closed: Boolean): Boolean =
        playRequested && suspendedByFocus && !closed

    fun retryUnexpectedNativePause(
        playRequested: Boolean,
        started: Boolean,
        finished: Boolean,
        retries: Int,
        maxRetries: Int = 2
    ): Boolean = playRequested && started && !finished && retries < maxRetries

    fun boundedSeek(positionMs: Long, durationMs: Long, seekable: Boolean): Long? =
        if (!seekable || durationMs <= 0L) null else positionMs.coerceIn(0L, durationMs)

    /** Native video starts only after its owner has attached a valid output surface. */
    fun canStartNative(playRequested: Boolean, mediaConfigured: Boolean, surfaceAttached: Boolean): Boolean =
        playRequested && mediaConfigured && surfaceAttached

    /** An asynchronous native startup error may fall back only for local AUTO playback.
     *  Don't mistake mid-playback problems or remote/network failures for codec errors.
     */
    fun shouldAutoFallbackNativeStartup(
        kind: EngineKind, explicit: Boolean, localDocument: Boolean,
        playRequested: Boolean, everReady: Boolean, alreadyAttempted: Boolean,
        hasError: Boolean
    ): Boolean = kind == EngineKind.MPV && !explicit && localDocument &&
        playRequested && !everReady && !alreadyAttempted && hasError

    /**
     * A native engine may remain stuck in startup without emitting an error.
     * Only a foreground, automatically selected local MPV attempt can time out.
     * A suspended/paused player must never be treated as stalled.
     */
    fun shouldAutoFallbackNativeStall(
        kind: EngineKind, explicit: Boolean, localDocument: Boolean,
        playRequested: Boolean, suspendedByFocus: Boolean,
        everReady: Boolean, alreadyAttempted: Boolean,
        elapsedMs: Long, timeoutMs: Long = 20_000L
    ): Boolean = kind == EngineKind.MPV && !explicit && localDocument &&
        playRequested && !suspendedByFocus && !everReady && !alreadyAttempted &&
        elapsedMs >= timeoutMs

    /**
     * A local MKV can have audio/video codecs that Media3 cannot initialize.
     * Offer one LibVLC alternative only at startup, never for HTTPS or a manual choice.
     */
    fun engineCandidates(preferred: EngineKind, explicit: Boolean, localMatroska: Boolean = false): List<EngineKind> =
        when {
            explicit -> listOf(preferred)
            preferred == EngineKind.MPV -> listOf(EngineKind.MPV, EngineKind.MEDIA3)
            preferred == EngineKind.MEDIA3 && localMatroska -> listOf(EngineKind.MEDIA3, EngineKind.VLC)
            else -> listOf(preferred)
        }

    /**
     * Some Media3 MKV decoders never emit an error or a first frame.
     * Automatic local MKV startup may switch to VLC once after 25 seconds
     * of continuous Play intent. Paused, focus-suspended, network and
     * already-running media must not silently change engines.
     */
    fun shouldAutoFallbackMatroskaStall(
        current: EngineKind, explicit: Boolean, localMatroska: Boolean,
        playRequested: Boolean, suspendedByFocus: Boolean,
        everReady: Boolean, alreadyAttempted: Boolean,
        elapsedMs: Long, timeoutMs: Long = 25_000L
    ): Boolean = current == EngineKind.MEDIA3 && !explicit && localMatroska &&
        playRequested && !suspendedByFocus && !everReady && !alreadyAttempted &&
        elapsedMs >= timeoutMs

    fun shouldAutoFallbackMatroska(
        current: EngineKind, explicit: Boolean, localMatroska: Boolean,
        playRequested: Boolean, everReady: Boolean, alreadyAttempted: Boolean,
        hasError: Boolean
    ): Boolean = current == EngineKind.MEDIA3 && !explicit && localMatroska &&
        playRequested && !everReady && !alreadyAttempted && hasError
}

/** Pure rules for persisted local playback positions (never store network credentials). */
object PlaybackResumePolicy {
    /** Duration, buffering and Play notifications alone do not confirm a saved seek.
     * A native demuxer can expose all of those while still reporting 0:00.
     * Allow limited keyframe seek imprecision, without trusting an unrelated early time.
     */
    fun resumePositionAcknowledged(requestedMs: Long, reportedMs: Long, ended: Boolean): Boolean =
        requestedMs <= 0L || ended || (reportedMs >= 0L &&
            reportedMs >= (requestedMs - 5_000L).coerceAtLeast(1L))

    /**
     * Unlike initial Continue Watching, an explicit seek may move backwards.
     * Old decoder timestamps far beyond the requested target must not be
     * accepted as confirmation of the new seek.
     */
    fun explicitSeekAcknowledged(requestedMs: Long, reportedMs: Long): Boolean {
        if (requestedMs < 0L || reportedMs < 0L) return false
        val low = (requestedMs - 5_000L).coerceAtLeast(0L)
        val high = if (requestedMs > Long.MAX_VALUE - 5_000L) Long.MAX_VALUE
            else requestedMs + 5_000L
        return reportedMs in low..high
    }

    fun isCompleted(positionMs: Long, durationMs: Long): Boolean =
        durationMs > 0 && positionMs >= durationMs - durationMs / 20L

    fun startingPosition(positionMs: Long, durationMs: Long): Long {
        if (positionMs <= 0L || durationMs <= 0L || isCompleted(positionMs, durationMs)) return 0L
        return positionMs.coerceAtMost(durationMs)
    }
}

/** Bounds eagerly composed TV queue controls and SAF grants on low-memory devices. */
object QueuePolicy {
    const val MAX_ITEMS = 96
    fun <T> bounded(items: List<T>): List<T> = items.take(MAX_ITEMS)
}

/** No raw player errors, media URLs, or filenames appear in support diagnostics. */
data class PlaybackDiagnostic(val code: String, val guidance: String)

object PlaybackDiagnosticPolicy {
    fun from(kind: EngineKind, rawError: String): PlaybackDiagnostic {
        val category = when {
            rawError.contains("focus", ignoreCase = true) -> "AUDIO"
            rawError.contains("subtitle", ignoreCase = true) ||
                rawError.contains("caption", ignoreCase = true) -> "TEXT"
            rawError.contains("timeout", ignoreCase = true) ||
                rawError.contains("timed out", ignoreCase = true) -> "STARTUP"
            rawError.contains("document", ignoreCase = true) ||
                rawError.contains("permission", ignoreCase = true) ||
                rawError.contains("source", ignoreCase = true) -> "SOURCE"
            rawError.contains("codec", ignoreCase = true) ||
                rawError.contains("decode", ignoreCase = true) ||
                rawError.contains("media3", ignoreCase = true) -> "DECODER"
            else -> "PLAYBACK"
        }
        val guidance = when (category) {
            "AUDIO" -> "Audio is unavailable. Check the sound output, then retry."
            "TEXT" -> "The selected subtitle track could not be applied."
            "STARTUP" -> "The player did not start. Try Retry or another engine."
            "SOURCE" -> "The media source could not be opened. Check file access or network."
            "DECODER" -> "This engine could not decode the video. Try another engine."
            else -> "Playback stopped unexpectedly. Try Retry or another engine."
        }
        return PlaybackDiagnostic("BP-${kind.name}-$category", guidance)
    }
}

/** Only an Android-restored, previously granted local document may be reopened. */
object SessionRestorePolicy {
    const val MAX_URI_LENGTH = 2_048
    const val MAX_TITLE_LENGTH = 140

    fun canRestore(uri: String, readGrants: Set<String>): Boolean =
        uri.length in 11..MAX_URI_LENGTH && uri.startsWith("content://") &&
            uri in readGrants

    fun safeTitle(title: String): String =
        title.take(MAX_TITLE_LENGTH).ifBlank { "Previous video" }
}

/** TV-readable caption controls; no Android dependencies in this policy module. */
enum class SubtitleSize(val label: String, val media3Fraction: Float, val mpvFontSize: Double) {
    SMALL("Small", 0.047f, 42.0),
    STANDARD("Standard", 0.061f, 57.0),
    LARGE("Large", 0.080f, 76.0)
}
enum class SubtitleColor(val label: String, val mpvHex: String) {
    WHITE("White", "#FFFFFFFF"),
    YELLOW("Yellow", "#FFFF00FF"),
    BLACK("Black on light panel", "#000000FF")
}
data class SubtitleAppearance(
    val size: SubtitleSize = SubtitleSize.STANDARD,
    val color: SubtitleColor = SubtitleColor.WHITE
)

/** Bounded, local-only session health counters: no media URI, titles, IPs or file paths. */
data class PlaybackHealth(
    val samples: Int, val bufferingSamples: Int, val errorEvents: Int,
    val engineHandoffs: Int, val longestBufferMs: Long
) {
    val bufferingPercent: Int
        get() = if (samples == 0) 0 else (bufferingSamples.toLong() * 100L / samples).toInt()
}
class PlaybackHealthRecorder {
    private var samples = 0
    private var bufferingSamples = 0
    private var errorEvents = 0
    private var engineHandoffs = 0
    private var previousError = false
    private var bufferingSince: Long? = null
    private var longestBufferMs = 0L

    fun sample(buffering: Boolean, error: Boolean, nowMs: Long) {
        if (samples >= 4_096) {
            samples /= 2
            bufferingSamples /= 2
        }
        samples++
        if (buffering) bufferingSamples++
        if (error && !previousError) errorEvents = (errorEvents + 1).coerceAtMost(999)
        previousError = error
        if (buffering) {
            if (bufferingSince == null) bufferingSince = nowMs
            val elapsed = (nowMs - (bufferingSince ?: nowMs)).coerceIn(0L, 120_000L)
            if (elapsed > longestBufferMs) longestBufferMs = elapsed
        } else {
            bufferingSince = null
        }
    }
    fun engineChanged() {
        engineHandoffs = (engineHandoffs + 1).coerceAtMost(999)
    }
    val summary: PlaybackHealth
        get() = PlaybackHealth(samples, bufferingSamples, errorEvents, engineHandoffs, longestBufferMs)
}

/**
 * Constant-memory, manually sampled resource trend. A positive delta is not
 * evidence of a leak: GC, decoder caching, and native allocations fluctuate.
 * No filenames, process identity, media identifiers, or timestamps are saved.
 */
data class ResourceObservation(
    val javaHeapMiB: Long, val nativeHeapMiB: Long, val openFds: Int?
)
data class ResourceTrend(
    val samples: Int, val javaDeltaMiB: Long, val nativeDeltaMiB: Long, val fdDelta: Int?
)
class ResourceTrendRecorder {
    private var baseline: ResourceObservation? = null
    private var count = 0
    fun record(observed: ResourceObservation): ResourceTrend {
        val safe = ResourceObservation(
            observed.javaHeapMiB.coerceIn(0L, 1_000_000L),
            observed.nativeHeapMiB.coerceIn(0L, 1_000_000L),
            observed.openFds?.coerceIn(0, 100_000)
        )
        if (baseline == null) baseline = safe
        val first = checkNotNull(baseline)
        count = (count + 1).coerceAtMost(1_024)
        return ResourceTrend(
            count,
            safe.javaHeapMiB - first.javaHeapMiB,
            safe.nativeHeapMiB - first.nativeHeapMiB,
            if (safe.openFds != null && first.openFds != null) {
                safe.openFds - first.openFds
            } else null
        )
    }
}

/**
 * Avoid uncontrolled native decoder churn when Retry is tapped repeatedly.
 * Only manual, same-source Retry attempts are limited; switching media or
 * explicitly selecting another playback engine resets the retry window.
 */
class DecoderRetryGate(
    private val maxRestarts: Int = 3,
    private val windowMs: Long = 30_000L
) {
    private var windowStart: Long? = null
    private var restartCount = 0
    fun permit(nowMs: Long): Boolean {
        val start = windowStart
        if (start == null || nowMs < start || nowMs - start >= windowMs) {
            windowStart = nowMs
            restartCount = 0
        }
        if (restartCount >= maxRestarts) return false
        restartCount++
        return true
    }
    fun reset() {
        windowStart = null
        restartCount = 0
    }
}

/** Android TRIM_MEMORY_UI_HIDDEN and background levels (20+) permit release.
 *  Running-low levels (5/10/15) must not interrupt foreground playback.
 */
object MemoryTrimPolicy {
    fun shouldReleaseBackgroundDecoder(level: Int): Boolean = level >= 20
}

/**
 * Privacy-safe observations of session progress. These phases are display-only:
 * the tracker never calls play(), seek(), retry(), or changes engine selection.
 */
enum class PlaybackPhase(val label: String) {
    STARTING("Starting playback"),
    PLAYING("Playing"),
    REBUFFERING("Rebuffering"),
    SEEKING("Seeking"),
    SEEK_DELAYED("Seek not yet confirmed"),
    PAUSED("Paused"),
    WAITING("Waiting for decoder"),
    ENDED("Finished"),
    ERROR("Playback unavailable")
}

data class PlaybackPhaseInput(
    val hasEngine: Boolean,
    val requestedPlay: Boolean,
    val focusSuspended: Boolean,
    val loading: Boolean,
    val isPlaying: Boolean,
    val ended: Boolean,
    val hasError: Boolean,
    val seekPending: Boolean,
    val nowMs: Long
)

data class PlaybackPhaseReport(val phase: PlaybackPhase, val phaseDurationMs: Long = 0L)

class PlaybackPhaseTracker(private val seekWarningMs: Long = 12_000L) {
    private var hasPlayed = false
    private var explicitSeekStartedMs: Long? = null
    private var currentPhase: PlaybackPhase? = null
    private var currentPhaseStartedMs = 0L

    fun reset() {
        hasPlayed = false
        explicitSeekStartedMs = null
        currentPhase = null
        currentPhaseStartedMs = 0L
    }

    fun explicitSeekRequested(nowMs: Long) {
        explicitSeekStartedMs = nowMs
    }

    /** Ignore time spent manually paused or waiting for audio focus. */
    fun resumeAfterSuspension(nowMs: Long) {
        if (explicitSeekStartedMs != null) explicitSeekStartedMs = nowMs
    }

    fun observe(input: PlaybackPhaseInput): PlaybackPhaseReport {
        if (input.isPlaying && !input.loading && !input.hasError) hasPlayed = true
        if (!input.seekPending) explicitSeekStartedMs = null
        val pendingAt = explicitSeekStartedMs
        val phase = when {
            input.hasError -> PlaybackPhase.ERROR
            input.ended -> PlaybackPhase.ENDED
            !input.requestedPlay || input.focusSuspended -> PlaybackPhase.PAUSED
            pendingAt != null && input.seekPending &&
                input.nowMs >= pendingAt &&
                input.nowMs - pendingAt >= seekWarningMs -> PlaybackPhase.SEEK_DELAYED
            pendingAt != null && input.seekPending -> PlaybackPhase.SEEKING
            input.loading && hasPlayed -> PlaybackPhase.REBUFFERING
            input.loading -> PlaybackPhase.STARTING
            input.isPlaying -> PlaybackPhase.PLAYING
            !input.hasEngine -> PlaybackPhase.STARTING
            hasPlayed -> PlaybackPhase.WAITING
            else -> PlaybackPhase.STARTING
        }
        if (phase != currentPhase || input.nowMs < currentPhaseStartedMs) {
            currentPhase = phase
            currentPhaseStartedMs = input.nowMs
        }
        return PlaybackPhaseReport(
            phase,
            (input.nowMs - currentPhaseStartedMs).coerceIn(0L, 3_600_000L)
        )
    }
}
