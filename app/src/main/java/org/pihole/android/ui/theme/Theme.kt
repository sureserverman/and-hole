package org.pihole.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme =
    lightColorScheme(
        primary = PiholeLightPrimary,
        onPrimary = PiholeLightOnPrimary,
        primaryContainer = PiholeLightPrimaryContainer,
        onPrimaryContainer = PiholeLightOnPrimaryContainer,
        secondary = PiholeLightSecondary,
        onSecondary = PiholeLightOnSecondary,
        secondaryContainer = PiholeLightSecondaryContainer,
        onSecondaryContainer = PiholeLightOnSecondaryContainer,
        tertiary = PiholeLightTertiary,
        onTertiary = PiholeLightOnTertiary,
        tertiaryContainer = PiholeLightTertiaryContainer,
        onTertiaryContainer = PiholeLightOnTertiaryContainer,
        error = PiholeLightError,
        onError = PiholeLightOnError,
        errorContainer = PiholeLightErrorContainer,
        onErrorContainer = PiholeLightOnErrorContainer,
        background = PiholeLightBackground,
        onBackground = PiholeLightOnBackground,
        surface = PiholeLightSurface,
        onSurface = PiholeLightOnSurface,
        surfaceVariant = PiholeLightSurfaceVariant,
        onSurfaceVariant = PiholeLightOnSurfaceVariant,
        outline = PiholeLightOutline,
        outlineVariant = PiholeLightOutlineVariant,
        scrim = PiholeLightScrim,
        inverseSurface = PiholeLightInverseSurface,
        inverseOnSurface = PiholeLightInverseOnSurface,
        inversePrimary = PiholeLightInversePrimary,
        surfaceTint = PiholeLightSurfaceTint,
        surfaceBright = PiholeLightSurfaceBright,
        surfaceDim = PiholeLightSurfaceDim,
        surfaceContainerLowest = PiholeLightSurfaceContainerLowest,
        surfaceContainerLow = PiholeLightSurfaceContainerLow,
        surfaceContainer = PiholeLightSurfaceContainer,
        surfaceContainerHigh = PiholeLightSurfaceContainerHigh,
        surfaceContainerHighest = PiholeLightSurfaceContainerHighest,
    )

private val DarkColorScheme =
    darkColorScheme(
        primary = PiholeDarkPrimary,
        onPrimary = PiholeDarkOnPrimary,
        primaryContainer = PiholeDarkPrimaryContainer,
        onPrimaryContainer = PiholeDarkOnPrimaryContainer,
        secondary = PiholeDarkSecondary,
        onSecondary = PiholeDarkOnSecondary,
        secondaryContainer = PiholeDarkSecondaryContainer,
        onSecondaryContainer = PiholeDarkOnSecondaryContainer,
        tertiary = PiholeDarkTertiary,
        onTertiary = PiholeDarkOnTertiary,
        tertiaryContainer = PiholeDarkTertiaryContainer,
        onTertiaryContainer = PiholeDarkOnTertiaryContainer,
        error = PiholeDarkError,
        onError = PiholeDarkOnError,
        errorContainer = PiholeDarkErrorContainer,
        onErrorContainer = PiholeDarkOnErrorContainer,
        background = PiholeDarkBackground,
        onBackground = PiholeDarkOnBackground,
        surface = PiholeDarkSurface,
        onSurface = PiholeDarkOnSurface,
        surfaceVariant = PiholeDarkSurfaceVariant,
        onSurfaceVariant = PiholeDarkOnSurfaceVariant,
        outline = PiholeDarkOutline,
        outlineVariant = PiholeDarkOutlineVariant,
        scrim = PiholeDarkScrim,
        inverseSurface = PiholeDarkInverseSurface,
        inverseOnSurface = PiholeDarkInverseOnSurface,
        inversePrimary = PiholeDarkInversePrimary,
        surfaceTint = PiholeDarkSurfaceTint,
        surfaceBright = PiholeDarkSurfaceBright,
        surfaceDim = PiholeDarkSurfaceDim,
        surfaceContainerLowest = PiholeDarkSurfaceContainerLowest,
        surfaceContainerLow = PiholeDarkSurfaceContainerLow,
        surfaceContainer = PiholeDarkSurfaceContainer,
        surfaceContainerHigh = PiholeDarkSurfaceContainerHigh,
        surfaceContainerHighest = PiholeDarkSurfaceContainerHighest,
    )

@Composable
fun PiholeTheme(content: @Composable () -> Unit) {
    val darkTheme = isSystemInDarkTheme()
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = PiholeTypography,
        shapes = PiholeShapes,
        content = content,
    )
}
