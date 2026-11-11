package com.vaydelux.bousyplayer

import android.content.Context
import android.net.Uri
import android.view.View
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vaydelux.bousyplayer.data.RecentStore
import com.vaydelux.bousyplayer.engine.*
import com.vaydelux.bousyplayer.model.MediaSource
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Requires a connected Android device or emulator. The ordinary GitHub build
 * compiles this suite without claiming that it executed on physical hardware.
 *
 * Use an in-memory fake decoder and an unpersisted content URI; this does not
 * open real files or change the user's saved Continue Watching entries.
 */
@RunWith(AndroidJUnit4::class)
class PlayerSessionLifecycleInstrumentedTest {
    private class FakeDecoder(context: Context, override val kind: EngineKind) : PlaybackEngine {
        override val view: View = View(context)
        var released = 0
        var snapshotFailsRemaining = 0
        var loadedAt = -1L
        var state = PlaybackSnapshot(
            duration = 300_000L, canSeek = true, loading = false
        )

        override fun load(uri: Uri, startMs: Long, shouldPlay: Boolean, subtitle: Uri?) {
            loadedAt = startMs
            state = state.copy(position = startMs, isPlaying = shouldPlay,
                loading = false, ended = false, error = null)
        }
        override fun snapshot(): PlaybackSnapshot {
            if (snapshotFailsRemaining > 0) {
                snapshotFailsRemaining--
                throw IllegalStateException("synthetic secret should not appear in UI")
            }
            return state
        }
        override fun play() { state = state.copy(isPlaying = true) }
        override fun pause() { state = state.copy(isPlaying = false) }
        override fun seek(positionMs: Long) { state = state.copy(position = positionMs) }
        override fun speed(value: Float) = Unit
        override fun aspect(value: AspectMode) = Unit
        override fun selectTrack(id: String, kind: TrackKind) = Unit
        override fun disableSubtitles() = Unit
        override fun detachView() = Unit
        override fun release() { released++ }
    }

    @Test
    fun hiddenMemoryTrimIsIdempotentAndRequiresExplicitPlayForReopening() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val decoders = mutableListOf<FakeDecoder>()
        val source = MediaSource(Uri.parse("content://bousyplayer.test/trim.mp4"),
            "trim.mp4", false)
        val player = PlayerSession(context, listOf(source), 0, RecentStore(context),
            initialPlay = false, engineFactory = { kind ->
                FakeDecoder(context, kind).also(decoders::add)
            })
        try {
            assertEquals(1, decoders.size)
            assertFalse(player.wantsPlay)
            decoders.last().state = decoders.last().state.copy(position = 61_000L)
            player.onBackground()
            player.onMemoryPressure(15)
            assertEquals(0, decoders.first().released)
            player.onMemoryPressure(20)
            player.onMemoryPressure(80)
            assertEquals(1, decoders.first().released)
            assertNull(player.engine)
            assertFalse(player.wantsPlay)
            player.onForeground()
            assertEquals(1, decoders.size)
            assertFalse(player.wantsPlay)
            player.play()
            assertEquals(2, decoders.size)
            assertEquals(61_000L, decoders.last().loadedAt)
        } finally {
            player.close()
        }
        assertEquals(1, decoders.last().released)
    }

    @Test
    fun failedFirstNativeSnapshotIsReleasedAndNotOwnedByTheSession() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val decoders = mutableListOf<FakeDecoder>()
        val src = MediaSource(Uri.parse("content://bousyplayer.test/invalid.mp4"),
            "invalid.mp4", false)
        val session = PlayerSession(context, listOf(src), 0, RecentStore(context),
            initialPlay = false, engineFactory = { kind ->
                FakeDecoder(context, kind).also {
                    it.snapshotFailsRemaining = 1
                    decoders.add(it)
                }
            })
        assertEquals(1, decoders.size)
        assertNull(session.engine)
        assertEquals(1, decoders[0].released)
        assertNotNull(session.snapshot.error)
        assertFalse(session.wantsPlay)
        session.close()
        assertEquals(1, decoders[0].released)
    }

    @Test
    fun transientSnapshotFaultDoesNotReallocateNativeEngine() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val decoders = mutableListOf<FakeDecoder>()
        val src = MediaSource(Uri.parse("content://bousyplayer.test/status.mp4"),
            "status.mp4", false)
        val session = PlayerSession(context, listOf(src), 0, RecentStore(context),
            initialPlay = false, engineFactory = { kind ->
                FakeDecoder(context, kind).also(decoders::add)
            })
        try {
            decoders.single().snapshotFailsRemaining = 2
            session.refresh()
            assertNull(session.snapshot.error)
            assertEquals(1, decoders.size)
            session.refresh()
            assertNull(session.snapshot.error)
            assertEquals(2, session.playbackSnapshotFaults)
            session.refresh()
            assertEquals(0, session.consecutivePlaybackSnapshotFaults)
            assertEquals(1, decoders.size)
        } finally {
            session.close()
        }
        assertEquals(1, decoders[0].released)
    }

    @Test
    fun switchingEnginePreservesExplicitPauseAndReleasesPreviousDecoder() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val decoders = mutableListOf<FakeDecoder>()
        val source = MediaSource(Uri.parse("content://bousyplayer.test/paused.mkv"),
            "paused.mkv", false)
        val player = PlayerSession(context, listOf(source), 0, RecentStore(context),
            initialPlay = false, engineFactory = { kind ->
                FakeDecoder(context, kind).also(decoders::add)
            })
        try {
            decoders.first().state = decoders.first().state.copy(position = 22_000L)
            player.switchEngine(EngineKind.VLC)
            assertEquals(2, decoders.size)
            assertEquals(1, decoders.first().released)
            assertEquals(22_000L, decoders.last().loadedAt)
            assertEquals(EngineKind.VLC, player.kind)
            assertFalse(player.wantsPlay)
            assertFalse(decoders.last().state.isPlaying)
        } finally {
            player.close()
        }
        assertEquals(1, decoders.last().released)
    }
}
