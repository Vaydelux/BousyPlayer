package com.vaydelux.bousyplayer.model

import org.junit.Assert.*
import org.junit.Test

class SourcePolicyTest {
    @Test fun secureNetworkUrlOnly() {
        assertTrue(SourcePolicy.isValidNetworkUrl("https://cdn.example.com/video.m3u8"))
        assertFalse(SourcePolicy.isValidNetworkUrl("http://example.com/unsafe.ts"))
        assertFalse(SourcePolicy.isValidNetworkUrl("javascript:alert(1)"))
        assertFalse(SourcePolicy.isValidNetworkUrl("https://u:pass@example.com/media.mp4"))
        assertFalse(SourcePolicy.isValidNetworkUrl("not a URL"))
    }
    @Test fun rejectsMalformedUntrustedMediaUrls() {
        assertFalse(SourcePolicy.isValidNetworkUrl("https://example.com:99999/a"))
        assertFalse(SourcePolicy.isValidNetworkUrl("https://example.com:0/a"))
        assertFalse(SourcePolicy.isValidNetworkUrl("https://example.com:/a"))
        assertFalse(SourcePolicy.isValidNetworkUrl("https://example.com/%0Ainjected"))
        assertFalse(SourcePolicy.isValidNetworkUrl("https://example.com/\r\ninjected"))
        assertFalse(SourcePolicy.isValidNetworkUrl("https://example.com/" + "x".repeat(8_192)))
        assertTrue(SourcePolicy.isValidNetworkUrl("https://cdn.example.com:8443/v.m3u8?token=abc"))
    }
    @Test fun localHistoryIsBoundedAndSanitized() {
        assertTrue(HistoryPolicy.isPersistableDocumentUri("content://provider/video/1"))
        assertFalse(HistoryPolicy.isPersistableDocumentUri("https://example.org/movie"))
        assertFalse(HistoryPolicy.isPersistableDocumentUri("content://provider/" + "x".repeat(2_048)))
        assertEquals("Titleok", HistoryPolicy.safeTitle("Title\n\u202eok"))
        assertEquals(HistoryPolicy.MAX_TITLE_LENGTH, HistoryPolicy.safeTitle("X".repeat(1_000)).length)
        assertEquals("Video", HistoryPolicy.safeTitle("\r\n"))
        assertEquals(0L, HistoryPolicy.safeDuration(-5L))
        assertEquals(HistoryPolicy.MAX_DURATION_MS,
            HistoryPolicy.safePosition(Long.MAX_VALUE, Long.MAX_VALUE))
    }
    @Test fun safGrantVisibilityNeverMistakesMissingPermissionsForDeletion() {
        val uri = "content://provider/video/1"
        assertTrue(HistoryPolicy.isVisibleWithReadGrant(uri, null))
        assertFalse(HistoryPolicy.isVisibleWithReadGrant(uri, emptySet()))
        assertFalse(HistoryPolicy.isVisibleWithReadGrant(uri, setOf("content://provider/other")))
        assertTrue(HistoryPolicy.isVisibleWithReadGrant(uri, setOf(uri)))
    }
    @Test fun seekClampsToKnownBounds() {
        assertEquals(0, SourcePolicy.clampSeek(-10, 1000))
        assertEquals(1000, SourcePolicy.clampSeek(1500, 1000))
        assertEquals(0, SourcePolicy.clampSeek(100, 0))
        assertEquals(500, SourcePolicy.clampSeek(500, 1000))
    }
    @Test fun timelineIsTruthful() {
        assertEquals("0:00", SourcePolicy.displayTime(-1))
        assertEquals("1:05", SourcePolicy.displayTime(65_000))
        assertEquals("1:01:01", SourcePolicy.displayTime(3_661_000))
    }
}
