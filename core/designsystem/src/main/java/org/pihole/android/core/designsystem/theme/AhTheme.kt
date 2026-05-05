package org.pihole.android.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

/**
 * Convenience accessor: [AhTheme.colors], [AhTheme.text].
 */
object AhTheme {
    val colors: AhColors
        @Composable
        @ReadOnlyComposable
        get() = LocalAhColors.current

    val text: AhTextStyles
        @Composable
        @ReadOnlyComposable
        get() = LocalAhTextStyles.current
}

/**
 * Brand theme wrapper. Builds an M3 ColorScheme from the AH palette so default
 * Material widgets (Switch, Button, TextField, …) pick up the right ink, while
 * primitives in this module use the richer `AhTheme.colors` set directly.
 */
@Composable
fun AhTheme(
    forceDark: Boolean? = null,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val isDark = forceDark ?: systemDark
    val ah = if (isDark) AhColorsDark else AhColorsLight
    val m3 = if (isDark) ah.toDarkM3() else ah.toLightM3()

    CompositionLocalProvider(
        LocalAhColors provides ah,
        LocalAhTextStyles provides AhTextStylesDefault,
    ) {
        MaterialTheme(
            colorScheme = m3,
            typography = AhMaterialTypography,
            shapes = AhMaterialShapes,
            content = content,
        )
    }
}

private fun AhColors.toDarkM3() = darkColorScheme(
    primary = accent,
    onPrimary = accentInk,
    primaryContainer = surface3,
    onPrimaryContainer = accent,
    secondary = accent2,
    onSecondary = accentInk,
    secondaryContainer = surface2,
    onSecondaryContainer = text,
    tertiary = warn,
    onTertiary = accentInk,
    tertiaryContainer = surface2,
    onTertiaryContainer = warn,
    error = danger,
    onError = accentInk,
    errorContainer = surface2,
    onErrorContainer = danger,
    background = bg,
    onBackground = text,
    surface = surface,
    onSurface = text,
    surfaceVariant = surface2,
    onSurfaceVariant = textMute,
    outline = border,
    outlineVariant = border2,
    inverseSurface = text,
    inverseOnSurface = bg,
    inversePrimary = accent2,
    surfaceTint = accent,
    surfaceBright = surface3,
    surfaceDim = bg,
    surfaceContainerLowest = bg,
    surfaceContainerLow = bg2,
    surfaceContainer = surface,
    surfaceContainerHigh = surface2,
    surfaceContainerHighest = surface3,
)

private fun AhColors.toLightM3() = lightColorScheme(
    primary = accent,
    onPrimary = accentInk,
    primaryContainer = surface3,
    onPrimaryContainer = accent2,
    secondary = accent2,
    onSecondary = accentInk,
    secondaryContainer = surface2,
    onSecondaryContainer = text,
    tertiary = warn,
    onTertiary = accentInk,
    tertiaryContainer = surface2,
    onTertiaryContainer = warn,
    error = danger,
    onError = accentInk,
    errorContainer = surface2,
    onErrorContainer = danger,
    background = bg,
    onBackground = text,
    surface = surface,
    onSurface = text,
    surfaceVariant = surface2,
    onSurfaceVariant = textMute,
    outline = border,
    outlineVariant = border2,
    inverseSurface = text,
    inverseOnSurface = bg,
    inversePrimary = accent2,
    surfaceTint = accent,
    surfaceBright = surface,
    surfaceDim = bg2,
    surfaceContainerLowest = surface,
    surfaceContainerLow = surface2,
    surfaceContainer = surface3,
    surfaceContainerHigh = bg2,
    surfaceContainerHighest = bg2,
)
