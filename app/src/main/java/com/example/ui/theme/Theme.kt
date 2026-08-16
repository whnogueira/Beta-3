package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DynoDarkColorScheme = darkColorScheme(
    primary = DynoRed,
    onPrimary = TextPrimary,
    primaryContainer = CarbonSurfaceVariant,
    onPrimaryContainer = TextPrimary,
    secondary = DynoCyan,
    onSecondary = CarbonDark,
    secondaryContainer = CarbonSurfaceVariant,
    onSecondaryContainer = TextPrimary,
    tertiary = DynoAmber,
    background = CarbonDark,
    onBackground = TextPrimary,
    surface = CarbonSurface,
    onSurface = TextPrimary,
    surfaceVariant = CarbonSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = CarbonBorder
)

@Composable
fun DynoMobileTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DynoDarkColorScheme,
        typography = Typography,
        content = content
    )
}
