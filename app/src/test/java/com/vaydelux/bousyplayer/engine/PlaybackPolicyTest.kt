package com.vaydelux.bousyplayer.engine

import org.junit.Assert.*
import org.junit.Test

class PlaybackPolicyTest {
    @Test fun tsPrefersMpvAndCommonFormatsUseMedia3() {
        assertEquals(EngineKind.MPV, EngineKind.preferredFor("episode.ts"))
        assertEquals(EngineKind.MPV, EngineKind.preferredFor("MOVIE.M2TS"))
        assertEquals(EngineKind.MPV, EngineKind.preferredFor("https://cdn.example/movie.mts?x=1"))
        assertEquals(EngineKind.MEDIA3, EngineKind.preferredFor("movie.mkv"))
        assertEquals(EngineKind.MEDIA3, EngineKind.preferredFor("movie.mp4"))
        assertEquals(EngineKind.MEDIA3, EngineKind.preferredFor("movie.m3u8"))
    }
    @Test fun manualPauseIsNeverOverriddenByBufferRecovery() {
        assertFalse(PlaybackPolicy.shouldResume(false, false, false))
        assertFalse(PlaybackPolicy.shouldResume(true, true, false))
        assertFalse(PlaybackPolicy.shouldResume(true, false, true))
        assertTrue(PlaybackPolicy.shouldResume(true, false, false))
    }
    @Test fun focusGainOnlyResumesAnActiveInterruptedSession() {
        assertTrue(PlaybackPolicy.resumeAfterFocusGain(true, true, false))
        assertFalse(PlaybackPolicy.resumeAfterFocusGain(false, true, false))
        assertFalse(PlaybackPolicy.resumeAfterFocusGain(true, false, false))
        assertFalse(PlaybackPolicy.resumeAfterFocusGain(true, true, true))
    }
    @Test fun unexpectedNativePauseRecoveryHasABoundedBudget() {
        assertTrue(PlaybackPolicy.retryUnexpectedNativePause(true, true, false, 0))
        assertTrue(PlaybackPolicy.retryUnexpectedNativePause(true, true, false, 1))
        assertFalse(PlaybackPolicy.retryUnexpectedNativePause(true, true, false, 2))
        assertFalse(PlaybackPolicy.retryUnexpectedNativePause(false, true, false, 0))
        assertFalse(PlaybackPolicy.retryUnexpectedNativePause(true, false, false, 0))
        assertFalse(PlaybackPolicy.retryUnexpectedNativePause(true, true, true, 0))
    }
    @Test fun noSeekWithoutVerifiedDurationAndSeekableDemuxer() {
        assertNull(PlaybackPolicy.boundedSeek(12, 0, true))
        assertNull(PlaybackPolicy.boundedSeek(12, 100, false))
        assertEquals(100L, PlaybackPolicy.boundedSeek(200, 100, true))
        assertEquals(0L, PlaybackPolicy.boundedSeek(-2, 100, true))
    }
    @Test fun nativeRenderingWaitsForSurfaceAndUserIntent() {
        assertFalse(PlaybackPolicy.canStartNative(true, true, false))
        assertFalse(PlaybackPolicy.canStartNative(true, false, true))
        assertFalse(PlaybackPolicy.canStartNative(false, true, true))
        assertTrue(PlaybackPolicy.canStartNative(true, true, true))
    }
    @Test fun asynchronousFallbackIsLocalStartupOnly() {
        assertTrue(PlaybackPolicy.shouldAutoFallbackNativeStartup(EngineKind.MPV, false, true, true, false, false, true))
        assertFalse(PlaybackPolicy.shouldAutoFallbackNativeStartup(EngineKind.MPV, false, true, true, true, false, true))
        assertFalse(PlaybackPolicy.shouldAutoFallbackNativeStartup(EngineKind.MPV, false, false, true, false, false, true))
        assertFalse(PlaybackPolicy.shouldAutoFallbackNativeStartup(EngineKind.MPV, true, true, true, false, false, true))
    }
    @Test fun nativeStartupStallFallbackIsBoundedAndRequiresActiveAutoLocalPlayback() {
        assertFalse(PlaybackPolicy.shouldAutoFallbackNativeStall(
            EngineKind.MPV, false, true, true, false, false, false, 19_999L))
        assertTrue(PlaybackPolicy.shouldAutoFallbackNativeStall(
            EngineKind.MPV, false, true, true, false, false, false, 20_000L))
        assertFalse(PlaybackPolicy.shouldAutoFallbackNativeStall(
            EngineKind.MPV, true, true, true, false, false, false, 40_000L))
        assertFalse(PlaybackPolicy.shouldAutoFallbackNativeStall(
            EngineKind.MPV, false, true, false, false, false, false, 40_000L))
        assertFalse(PlaybackPolicy.shouldAutoFallbackNativeStall(
            EngineKind.MPV, false, true, true, true, false, false, 40_000L))
        assertFalse(PlaybackPolicy.shouldAutoFallbackNativeStall(
            EngineKind.MPV, false, false, true, false, false, false, 40_000L))
        assertFalse(PlaybackPolicy.shouldAutoFallbackNativeStall(
            EngineKind.MPV, false, true, true, false, true, false, 40_000L))
        assertFalse(PlaybackPolicy.shouldAutoFallbackNativeStall(
            EngineKind.MPV, false, true, true, false, false, true, 40_000L))
    }
    @Test fun matroskaAutoFallbackRespectsLocalSourceAndManualIntent() {
        assertTrue(EngineKind.isMatroska("MOVIE.MKV"))
        assertFalse(EngineKind.isMatroska("movie.mp4"))
        assertEquals(listOf(EngineKind.MEDIA3, EngineKind.VLC),
            PlaybackPolicy.engineCandidates(EngineKind.MEDIA3, false, true))
        assertEquals(listOf(EngineKind.MEDIA3),
            PlaybackPolicy.engineCandidates(EngineKind.MEDIA3, true, true))
        assertTrue(PlaybackPolicy.shouldAutoFallbackMatroska(
            EngineKind.MEDIA3, false, true, true, false, false, true))
        assertFalse(PlaybackPolicy.shouldAutoFallbackMatroska(
            EngineKind.MEDIA3, true, true, true, false, false, true))
        assertFalse(PlaybackPolicy.shouldAutoFallbackMatroska(
            EngineKind.MEDIA3, false, false, true, false, false, true))
        assertFalse(PlaybackPolicy.shouldAutoFallbackMatroska(
            EngineKind.MEDIA3, false, true, false, false, false, true))
        assertFalse(PlaybackPolicy.shouldAutoFallbackMatroska(
            EngineKind.MEDIA3, false, true, true, true, false, true))
        assertFalse(PlaybackPolicy.shouldAutoFallbackMatroska(
            EngineKind.MEDIA3, false, true, true, false, true, true))
    }
    @Test fun restoredPlaybackRequiresPersistedLocalDocumentGrant() {
        assertTrue(SessionRestorePolicy.canRestore(
            "content://provider/videos/123", setOf("content://provider/videos/123")))
        assertFalse(SessionRestorePolicy.canRestore(
            "content://provider/videos/123", emptySet()))
        assertFalse(SessionRestorePolicy.canRestore(
            "https://example.test/live.ts", setOf("https://example.test/live.ts")))
        assertFalse(SessionRestorePolicy.canRestore(
            "content://" + "a".repeat(3000), setOf("content://" + "a".repeat(3000))))
        assertEquals(140, SessionRestorePolicy.safeTitle("x".repeat(300)).length)
    }
    @Test fun diagnosticsNeverExposeRawMediaSource() {
        val info = PlaybackDiagnosticPolicy.from(
            EngineKind.VLC, "cannot decode https://private.test/video?token=secret")
        assertEquals("BP-VLC-DECODER", info.code)
        assertFalse(info.guidance.contains("private.test"))
        assertFalse(info.guidance.contains("token="))
    }
    @Test fun captionAppearanceAndTelemetryRemainBoundedAndPrivate() {
        assertTrue(SubtitleSize.LARGE.media3Fraction > SubtitleSize.SMALL.media3Fraction)
        assertEquals(SubtitleColor.WHITE, SubtitleAppearance().color)
        val recorder = PlaybackHealthRecorder()
        recorder.sample(true, false, 0L)
        recorder.sample(true, false, 200_000L)
        recorder.sample(false, true, 210_000L)
        recorder.sample(false, true, 220_000L)
        assertEquals(120_000L, recorder.summary.longestBufferMs)
        assertEquals(1, recorder.summary.errorEvents)
        assertEquals(2, recorder.summary.bufferingSamples)
        repeat(5_000) { recorder.sample(false, false, it.toLong()) }
        assertTrue(recorder.summary.samples <= 4_096)
        assertFalse(recorder.summary.toString().contains("file://"))
    }
    @Test fun queueIsLimitedBeforeBuildingTvControls() {
        assertEquals(QueuePolicy.MAX_ITEMS, QueuePolicy.bounded((1..1000).toList()).size)
        assertEquals(listOf(1, 2), QueuePolicy.bounded(listOf(1, 2)))
    }

    @Test fun autoNativeStartupMayFallbackButExplicitNeverDoes() {
        assertEquals(listOf(EngineKind.MPV, EngineKind.MEDIA3),
            PlaybackPolicy.engineCandidates(EngineKind.MPV, false))
        assertEquals(listOf(EngineKind.VLC),
            PlaybackPolicy.engineCandidates(EngineKind.VLC, true))
        assertEquals(listOf(EngineKind.MEDIA3),
            PlaybackPolicy.engineCandidates(EngineKind.MEDIA3, false))
    }
    @Test fun completedVideoDoesNotResumeAtTheCredits() {
        assertTrue(PlaybackResumePolicy.isCompleted(950_000L, 1_000_000L))
        assertEquals(0L, PlaybackResumePolicy.startingPosition(950_000L, 1_000_000L))
        assertEquals(425_000L, PlaybackResumePolicy.startingPosition(425_000L, 1_000_000L))
        assertEquals(0L, PlaybackResumePolicy.startingPosition(4_000L, 0L))
        assertFalse(PlaybackResumePolicy.isCompleted(0L, 0L))
    }

}
