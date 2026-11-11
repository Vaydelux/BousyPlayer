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

    /** Only AUTO native startup can fall back; explicit selection is authoritative. */
    fun engineCandidates(preferred: EngineKind, explicit: Boolean): List<EngineKind> =
        if (explicit || preferred == EngineKind.MEDIA3) listOf(preferred)
        else listOf(preferred, EngineKind.MEDIA3)
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

    fun isCompleted(positionMs: Long, durationMs: Long): Boolean =
        durationMs > 0 && positionMs >= durationMs - durationMs / 20L

    fun startingPosition(positionMs: Long, durationMs: Long): Long {
        if (positionMs <= 0L || durationMs <= 0L || isCompleted(positionMs, durationMs)) return 0L
        return positionMs.coerceAtMost(durationMs)
    }
}
