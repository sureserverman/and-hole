package org.pihole.android.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Bundled fonts would go in res/font; we leave them as system defaults until added.
// The visual hierarchy depends on size/weight/letter-spacing more than the family.
val AhFontUi: FontFamily = FontFamily.SansSerif
val AhFontMono: FontFamily = FontFamily.Monospace

/**
 * Extra text styles for the AH design that don't map cleanly to M3 typography slots
 * (eyebrow mono caption, mono data, stat value at 28-44sp, pill chip text, etc.).
 */
@Immutable
data class AhTextStyles(
    val pageTitle: TextStyle,
    val sectionLabel: TextStyle,
    val body: TextStyle,
    val pill: TextStyle,
    val statValue: TextStyle,
    val statValueLg: TextStyle,
    val statUnit: TextStyle,
    val monoCaption: TextStyle,
    val monoData: TextStyle,
    val monoEyebrow: TextStyle,
    val brandMark: TextStyle,
    val streamRow: TextStyle,
)

val AhTextStylesDefault = AhTextStyles(
    pageTitle = TextStyle(
        fontFamily = AhFontUi,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        letterSpacing = (-0.36).sp,
    ),
    sectionLabel = TextStyle(
        fontFamily = AhFontUi,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.5.sp,
        letterSpacing = 0.125.sp,
    ),
    body = TextStyle(
        fontFamily = AhFontUi,
        fontWeight = FontWeight.Medium,
        fontSize = 13.5.sp,
    ),
    pill = TextStyle(
        fontFamily = AhFontUi,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        letterSpacing = 0.22.sp,
    ),
    statValue = TextStyle(
        fontFamily = AhFontUi,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        letterSpacing = (-0.56).sp,
    ),
    statValueLg = TextStyle(
        fontFamily = AhFontUi,
        fontWeight = FontWeight.SemiBold,
        fontSize = 44.sp,
        letterSpacing = (-0.88).sp,
    ),
    statUnit = TextStyle(
        fontFamily = AhFontUi,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
    ),
    monoCaption = TextStyle(
        fontFamily = AhFontMono,
        fontWeight = FontWeight.Normal,
        fontSize = 10.5.sp,
        letterSpacing = 0.45.sp,
    ),
    monoData = TextStyle(
        fontFamily = AhFontMono,
        fontWeight = FontWeight.Normal,
        fontSize = 11.5.sp,
    ),
    monoEyebrow = TextStyle(
        fontFamily = AhFontMono,
        fontWeight = FontWeight.Normal,
        fontSize = 10.5.sp,
        letterSpacing = 1.5.sp,
    ),
    brandMark = TextStyle(
        fontFamily = AhFontUi,
        fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp,
        letterSpacing = (-0.6).sp,
    ),
    streamRow = TextStyle(
        fontFamily = AhFontMono,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
    ),
)

val LocalAhTextStyles = compositionLocalOf { AhTextStylesDefault }

/**
 * Material 3 Typography mapped from the AH scale, so callers that use
 * `MaterialTheme.typography.*` get on-brand defaults.
 */
val AhMaterialTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = AhFontUi,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        letterSpacing = (-0.64).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = AhFontUi,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        letterSpacing = (-0.36).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = AhFontUi,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        letterSpacing = (-0.33).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = AhFontUi,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        letterSpacing = (-0.18).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = AhFontUi,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = AhFontUi,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = AhFontUi,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = AhFontUi,
        fontWeight = FontWeight.Medium,
        fontSize = 13.5.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = AhFontUi,
        fontWeight = FontWeight.Normal,
        fontSize = 12.5.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = AhFontUi,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.5.sp,
        letterSpacing = 0.125.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = AhFontUi,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        letterSpacing = 0.22.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = AhFontMono,
        fontWeight = FontWeight.Normal,
        fontSize = 10.5.sp,
        letterSpacing = 0.45.sp,
    ),
)
