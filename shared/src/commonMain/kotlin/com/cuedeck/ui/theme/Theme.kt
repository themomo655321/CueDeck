package com.cuedeck.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val DarkBackground = Color(0xFF121212)
val DarkSurface = Color(0xFF1E1E1E)
val DarkSurfaceVariant = Color(0xFF2A2A2A)
val PanicRed = Color(0xFFE53935)
val GoGreen = Color(0xFF43A047)
val ArmedBlue = Color(0xFF1E88E5)
val WarningAmber = Color(0xFFFFB300)
val TextPrimary = Color(0xFFEEEEEE)
val TextSecondary = Color(0xFFB0B0B0)

private val CueDeckDarkColorScheme = darkColorScheme(
    primary = GoGreen,
    onPrimary = Color.White,
    secondary = ArmedBlue,
    onSecondary = Color.White,
    error = PanicRed,
    onError = Color.White,
    background = DarkBackground,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = TextSecondary
)

@Composable
fun CueDeckTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = CueDeckDarkColorScheme,
        content = content
    )
}
