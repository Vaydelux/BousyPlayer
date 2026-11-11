package com.vaydelux.bousyplayer.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Four consistent, contrast-conscious dark palettes across phone, tablet and TV. */
enum class CinemaTheme(val label: String) {
    OCEAN("Ocean"),
    OBSIDIAN("Obsidian"),
    AMETHYST("Amethyst"),
    EMBER("Ember");

    companion object {
        fun fromStored(raw: String?): CinemaTheme =
            entries.firstOrNull { it.name == raw } ?: OCEAN
    }
}
private data class CinemaPalette(
    val background: Color,
    val panel: Color,
    val panelLight: Color,
    val accent: Color,
    val secondary: Color,
    val text: Color,
    val muted: Color
)

private val ocean = CinemaPalette(
    Color(0xFF061426), Color(0xFF10243A), Color(0xFF19354F),
    Color(0xFF37D7F2), Color(0xFF82C3FF), Color(0xFFF2F7FF), Color(0xFFB4C0D2))
private val obsidian = CinemaPalette(
    Color(0xFF101216), Color(0xFF1C2026), Color(0xFF2A3039),
    Color(0xFFA8F0C8), Color(0xFFC7D1DB), Color(0xFFF4F6F7), Color(0xFFBDC7D2))
private val amethyst = CinemaPalette(
    Color(0xFF140E27), Color(0xFF241B3F), Color(0xFF352953),
    Color(0xFFD5A4FF), Color(0xFFA9B9FF), Color(0xFFF7F3FF), Color(0xFFC9BCD9))
private val ember = CinemaPalette(
    Color(0xFF21130F), Color(0xFF33201B), Color(0xFF4B2F26),
    Color(0xFFFFC078), Color(0xFFFFD7A4), Color(0xFFFFF5ED), Color(0xFFE4CABB))

private val LocalCinemaPalette = staticCompositionLocalOf { ocean }

/** All app surfaces share the current theme instead of hard-coded color values. */
object CinemaColor {
    val background: Color @Composable get() = LocalCinemaPalette.current.background
    val panel: Color @Composable get() = LocalCinemaPalette.current.panel
    val panelLight: Color @Composable get() = LocalCinemaPalette.current.panelLight
    val accent: Color @Composable get() = LocalCinemaPalette.current.accent
    val secondary: Color @Composable get() = LocalCinemaPalette.current.secondary
    val text: Color @Composable get() = LocalCinemaPalette.current.text
    val muted: Color @Composable get() = LocalCinemaPalette.current.muted
}

@Composable
fun BousyPlayerTheme(theme: CinemaTheme = CinemaTheme.OCEAN, content: @Composable () -> Unit) {
    val palette = when (theme) {
        CinemaTheme.OCEAN -> ocean
        CinemaTheme.OBSIDIAN -> obsidian
        CinemaTheme.AMETHYST -> amethyst
        CinemaTheme.EMBER -> ember
    }
    CompositionLocalProvider(LocalCinemaPalette provides palette) {
        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = palette.accent,
                onPrimary = palette.background,
                secondary = palette.secondary,
                background = palette.background,
                onBackground = palette.text,
                surface = palette.panel,
                onSurface = palette.text,
                surfaceVariant = palette.panelLight,
                onSurfaceVariant = palette.muted,
            ),
            shapes = Shapes(
                small = RoundedCornerShape(10.dp),
                medium = RoundedCornerShape(16.dp),
                large = RoundedCornerShape(22.dp)
            ),
            content = content
        )
    }
}
