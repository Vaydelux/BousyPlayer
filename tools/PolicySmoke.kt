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
