package com.vaydelux.bousyplayer.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class CinemaThemeTest {
    @Test fun storedAppearanceDefaultsSafely() {
        assertEquals(CinemaTheme.OCEAN, CinemaTheme.fromStored(null))
        assertEquals(CinemaTheme.OCEAN, CinemaTheme.fromStored(""))
        assertEquals(CinemaTheme.OCEAN, CinemaTheme.fromStored("malformed"))
    }

    @Test fun everyThemeRoundTripsThroughStableIdentifier() {
        CinemaTheme.entries.forEach { theme ->
            assertEquals(theme, CinemaTheme.fromStored(theme.name))
        }
        assertEquals(4, CinemaTheme.entries.size)
    }
}
