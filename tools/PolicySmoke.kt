// Standalone JVM smoke checks for rules that have no Android dependencies.
// Run through tools/check-policy.sh with a local kotlinc + Java installation.
import com.vaydelux.bousyplayer.engine.*
fun checkNamed(name: String, condition: Boolean) {
    check(condition) { "FAILED: $name" }
    println("PASS: $name")
}
fun main() {
    checkNamed("TS selects mpv", EngineKind.preferredFor("stream.ts") == EngineKind.MPV)
    checkNamed("M2TS selects mpv", EngineKind.preferredFor("video.M2TS") == EngineKind.MPV)
    checkNamed("MP4 selects Media3", EngineKind.preferredFor("video.mp4") == EngineKind.MEDIA3)
    checkNamed("pause intent survives buffering", !PlaybackPolicy.shouldResume(false,false,false))
    checkNamed("successful play can resume", PlaybackPolicy.shouldResume(true,false,false))
    checkNamed("end doesn't auto-resume", !PlaybackPolicy.shouldResume(true,true,false))
    checkNamed("fatal error doesn't auto-resume", !PlaybackPolicy.shouldResume(true,false,true))
    checkNamed("focus restores interrupted active play", PlaybackPolicy.resumeAfterFocusGain(true,true,false))
    checkNamed("focus does not undo user pause", !PlaybackPolicy.resumeAfterFocusGain(false,true,false))
    checkNamed("focus does not restart closed session", !PlaybackPolicy.resumeAfterFocusGain(true,true,true))
    checkNamed("retry native pause with explicit intent", PlaybackPolicy.retryUnexpectedNativePause(true,true,false,0))
    checkNamed("retry budget exhausted", !PlaybackPolicy.retryUnexpectedNativePause(true,true,false,2))
    checkNamed("never retry after explicit pause", !PlaybackPolicy.retryUnexpectedNativePause(false,true,false,0))
    checkNamed("never retry after EOF", !PlaybackPolicy.retryUnexpectedNativePause(true,true,true,0))
    checkNamed("seek clamped", PlaybackPolicy.boundedSeek(200,100,true) == 100L)
    checkNamed("seek disabled unknown duration", PlaybackPolicy.boundedSeek(200,0,true) == null)
    checkNamed("seek disabled nonseekable", PlaybackPolicy.boundedSeek(200,100,false) == null)
    checkNamed("native start waits for video surface", !PlaybackPolicy.canStartNative(true, true, false))
    checkNamed("native start waits for configured media", !PlaybackPolicy.canStartNative(true, false, true))
    checkNamed("native start respects explicit pause", !PlaybackPolicy.canStartNative(false, true, true))
    checkNamed("native starts after both ready", PlaybackPolicy.canStartNative(true, true, true))
    checkNamed("async local TS startup may fall back", PlaybackPolicy.shouldAutoFallbackNativeStartup(EngineKind.MPV,false,true,true,false,false,true))
    checkNamed("no async fallback after first successful frames", !PlaybackPolicy.shouldAutoFallbackNativeStartup(EngineKind.MPV,false,true,true,true,false,true))
    checkNamed("no async fallback on HTTPS", !PlaybackPolicy.shouldAutoFallbackNativeStartup(EngineKind.MPV,false,false,true,false,false,true))
    checkNamed("no async fallback for explicitly selected native", !PlaybackPolicy.shouldAutoFallbackNativeStartup(EngineKind.MPV,true,true,true,false,false,true))
    checkNamed("no async fallback when user paused", !PlaybackPolicy.shouldAutoFallbackNativeStartup(EngineKind.MPV,false,true,false,false,false,true))
    checkNamed("auto TS native failure fallback", PlaybackPolicy.engineCandidates(EngineKind.MPV,false) == listOf(EngineKind.MPV, EngineKind.MEDIA3))
    checkNamed("explicit VLC stays explicit", PlaybackPolicy.engineCandidates(EngineKind.VLC,true) == listOf(EngineKind.VLC))
    checkNamed("local MKV chooses Media3 then VLC only at startup",
        PlaybackPolicy.engineCandidates(EngineKind.MEDIA3, false, true) == listOf(EngineKind.MEDIA3, EngineKind.VLC))
    checkNamed("explicit MKV engine never auto-falls back",
        PlaybackPolicy.engineCandidates(EngineKind.MEDIA3, true, true) == listOf(EngineKind.MEDIA3))
    checkNamed("asynchronous local MKV startup error can use VLC",
        PlaybackPolicy.shouldAutoFallbackMatroska(EngineKind.MEDIA3,false,true,true,false,false,true))
    checkNamed("mid-playback MKV failures do not force an engine switch",
        !PlaybackPolicy.shouldAutoFallbackMatroska(EngineKind.MEDIA3,false,true,true,true,false,true))
    checkNamed("network streams never use local MKV fallback",
        !PlaybackPolicy.shouldAutoFallbackMatroska(EngineKind.MEDIA3,false,false,true,false,false,true))
    checkNamed("saved state never restores network URLs",
        !SessionRestorePolicy.canRestore("https://example.test/movie.m3u8",
            setOf("https://example.test/movie.m3u8")))
    checkNamed("saved state rejects revoked document grants",
        !SessionRestorePolicy.canRestore("content://provider/video", emptySet()))
    checkNamed("saved state accepts granted SAF document",
        SessionRestorePolicy.canRestore("content://provider/video",
            setOf("content://provider/video")))
    checkNamed("saved state bounds title size",
        SessionRestorePolicy.safeTitle("X".repeat(500)).length == 140)
    checkNamed("diagnostic hides signed URL and file name", run {
        val error = "Decoder failed for https://private.example/video.mkv?token=secret"
        val detail = PlaybackDiagnosticPolicy.from(EngineKind.MEDIA3, error)
        !detail.code.contains("private") && !detail.guidance.contains("token") &&
        detail.code == "BP-MEDIA3-DECODER"
    })
    checkNamed("subtitle appearance defaults to readable white",
        SubtitleAppearance().size == SubtitleSize.STANDARD &&
        SubtitleAppearance().color == SubtitleColor.WHITE)
    checkNamed("TV large subtitle font is bigger than compact",
        SubtitleSize.LARGE.media3Fraction > SubtitleSize.SMALL.media3Fraction)
    val health = PlaybackHealthRecorder()
    health.sample(true, false, 0L)
    health.sample(true, false, 10_000L)
    health.engineChanged()
    health.sample(false, true, 11_000L)
    health.sample(false, true, 12_000L)
    checkNamed("health errors count rising edges once", health.summary.errorEvents == 1)
    checkNamed("health tracks bounded buffer time and engine handoffs",
        health.summary.longestBufferMs == 10_000L && health.summary.engineHandoffs == 1)
    checkNamed("health metrics exclude media paths or URLs",
        !health.summary.toString().contains("file://"))
    val resources = ResourceTrendRecorder()
    val baseline = resources.record(ResourceObservation(125L, 65L, 18))
    val follow = resources.record(ResourceObservation(145L, 71L, 21))
    checkNamed("resource baseline starts at zero delta",
        baseline.samples == 1 && baseline.javaDeltaMiB == 0L &&
        baseline.nativeDeltaMiB == 0L && baseline.fdDelta == 0)
    checkNamed("manual resource samples report numerical deltas without retaining log entries",
        follow.samples == 2 && follow.javaDeltaMiB == 20L &&
        follow.nativeDeltaMiB == 6L && follow.fdDelta == 3)
    val unknownFd = ResourceTrendRecorder()
    unknownFd.record(ResourceObservation(5, 10, null))
    checkNamed("resource trend handles missing FD visibility",
        unknownFd.record(ResourceObservation(6, 11, 20)).fdDelta == null)
    val retryGate = DecoderRetryGate()
    checkNamed("manual decoder retries allow three bounded restarts",
        (0 until 3).all { retryGate.permit(1_000L) } && !retryGate.permit(1_000L))
    checkNamed("decoder retry window reopens after 30 seconds",
        retryGate.permit(31_001L))
    retryGate.reset()
    checkNamed("manual engine change resets decoder retry gate",
        retryGate.permit(31_002L))
    checkNamed("foreground low-memory warning does not release decoder",
        !MemoryTrimPolicy.shouldReleaseBackgroundDecoder(15))
    checkNamed("UI hidden memory trim permits background release",
        MemoryTrimPolicy.shouldReleaseBackgroundDecoder(20))
    checkNamed("queue bounded to 96 entries on low RAM TVs",
        QueuePolicy.bounded((1..1000).toList()).size == 96)
    checkNamed("auto Media3 avoids pointless retries", PlaybackPolicy.engineCandidates(EngineKind.MEDIA3,false) == listOf(EngineKind.MEDIA3))
    checkNamed("completed videos have no stale resume", PlaybackResumePolicy.startingPosition(95,100) == 0L)
    checkNamed("midpoint resume preserved", PlaybackResumePolicy.startingPosition(40,100) == 40L)
    checkNamed("unknown duration never seeds saved position", PlaybackResumePolicy.startingPosition(40,0) == 0L)
    checkNamed("completion threshold exact", PlaybackResumePolicy.isCompleted(950,1000))
    checkNamed("under completion threshold", !PlaybackResumePolicy.isCompleted(949,1000))
    checkNamed("metadata-only timeline does not confirm pending resume", !PlaybackResumePolicy.resumePositionAcknowledged(82_000, 0, false))
    checkNamed("early native progress does not confirm pending resume", !PlaybackResumePolicy.resumePositionAcknowledged(82_000, 2_000, false))
    checkNamed("resumed timeline accepts keyframe seek tolerance", PlaybackResumePolicy.resumePositionAcknowledged(82_000, 79_000, false))
    checkNamed("native resumed timeline is authoritative", PlaybackResumePolicy.resumePositionAcknowledged(82_000, 88_000, false))
    checkNamed("new videos start at zero without resume pending", PlaybackResumePolicy.resumePositionAcknowledged(0, 0, false))
    checkNamed("stalled automatic TS can fall back after 20 seconds",
        PlaybackPolicy.shouldAutoFallbackNativeStall(EngineKind.MPV, false, true,
            true, false, false, false, 20_000L))
    checkNamed("native startup is not prematurely timed out",
        !PlaybackPolicy.shouldAutoFallbackNativeStall(EngineKind.MPV, false, true,
            true, false, false, false, 19_999L))
    checkNamed("manual native choice cannot time out into a different engine",
        !PlaybackPolicy.shouldAutoFallbackNativeStall(EngineKind.MPV, true, true,
            true, false, false, false, 99_999L))
    checkNamed("paused playback cannot trigger timeout fallback",
        !PlaybackPolicy.shouldAutoFallbackNativeStall(EngineKind.MPV, false, true,
            false, false, false, false, 99_999L))
    checkNamed("audio focus suspension cannot trigger timeout fallback",
        !PlaybackPolicy.shouldAutoFallbackNativeStall(EngineKind.MPV, false, true,
            true, true, false, false, 99_999L))
    checkNamed("ready native player cannot time out into Media3",
        !PlaybackPolicy.shouldAutoFallbackNativeStall(EngineKind.MPV, false, true,
            true, false, true, false, 99_999L))
    checkNamed("HTTPS streams cannot use local native timeout fallback",
        !PlaybackPolicy.shouldAutoFallbackNativeStall(EngineKind.MPV, false, false,
            true, false, false, false, 99_999L))
    checkNamed("only one native stall fallback is permitted",
        !PlaybackPolicy.shouldAutoFallbackNativeStall(EngineKind.MPV, false, true,
            true, false, false, true, 99_999L))
}
