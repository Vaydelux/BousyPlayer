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
