package com.vaydelux.bousyplayer.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object CinemaColor {
    val background = Color(0xFF061426)
    val panel = Color(0xFF10243A)
    val panelLight = Color(0xFF19354F)
    val accent = Color(0xFF37D7F2)
    val secondary = Color(0xFF82C3FF)
    val text = Color(0xFFF2F7FF)
    val muted = Color(0xFFB4C0D2)
}

@Composable
fun BousyPlayerTheme(content: @Composable () -> Unit) = MaterialTheme(
    colorScheme = darkColorScheme(
        primary = CinemaColor.accent,
        onPrimary = CinemaColor.background,
        secondary = CinemaColor.secondary,
        background = CinemaColor.background,
        onBackground = CinemaColor.text,
        surface = CinemaColor.panel,
        onSurface = CinemaColor.text,
        surfaceVariant = CinemaColor.panelLight,
        onSurfaceVariant = CinemaColor.muted,
    ),
    shapes = Shapes(
        small = RoundedCornerShape(10.dp),
        medium = RoundedCornerShape(16.dp),
        large = RoundedCornerShape(22.dp)
    ),
    content = content
)
