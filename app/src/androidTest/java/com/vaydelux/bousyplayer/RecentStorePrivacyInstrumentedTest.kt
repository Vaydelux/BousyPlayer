package com.vaydelux.bousyplayer

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vaydelux.bousyplayer.data.RecentStore
import com.vaydelux.bousyplayer.model.HistoryPolicy
import com.vaydelux.bousyplayer.model.MediaSource
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Executed on API 35 emulator in PR CI; device/OEM coverage is separate. */
@RunWith(AndroidJUnit4::class)
class RecentStorePrivacyInstrumentedTest {
    @Test fun savedHistoryCanBeSanitizedAndClearedWithoutTouchingFiles() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = RecentStore(context)
        store.clear()
        try {
            val local = MediaSource(Uri.parse("content://test.provider/video/1"),
                "Episode\n\u202e" + "x".repeat(500), true)
            store.update(local, 4_000L, 10_000L)
            val entries = store.read()
            assertEquals(1, entries.size)
            assertTrue(entries.single().source.title.length <= HistoryPolicy.MAX_TITLE_LENGTH)
            assertFalse(entries.single().source.title.contains("\n"))
            assertFalse(entries.single().source.title.contains("\u202e"))
            store.update(MediaSource(Uri.parse("https://example.org/movie.m3u8"),
                "stream", false), 2_000L, 5_000L)
            assertEquals(1, store.read().size)
            // Missing or temporarily revoked grants may hide a card, but must
            // never erase the checkpoint the user explicitly saved.
            store.readAccessible()
            assertEquals(1, store.read().size)
            store.clear()
            assertTrue(store.read().isEmpty())
        } finally {
            store.clear()
        }
    }
}
