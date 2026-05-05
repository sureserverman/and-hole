package org.pihole.android.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * AH design tokens (the seven background/surface tiers, accent variants, and semantic
 * colors from the design handoff). These complement the M3 ColorScheme — primitives
 * read from here for design-faithful values that don't have a clean M3 slot
 * (border-2, glow, blocked, allowed, pass).
 */
@Immutable
data class AhColors(
    val bg: Color,
    val bg2: Color,
    val surface: Color,
    val surface2: Color,
    val surface3: Color,
    val border: Color,
    val border2: Color,
    val text: Color,
    val textMute: Color,
    val textDim: Color,
    val accent: Color,
    val accent2: Color,
    val accentInk: Color,
    val glow: Color,
    val warn: Color,
    val danger: Color,
    val blocked: Color,
    val allowed: Color,
    val pass: Color,
    val isDark: Boolean,
)

val AhColorsDark = AhColors(
    bg = Color(0xFF0C1618),
    bg2 = Color(0xFF112023),
    surface = Color(0xFF142428),
    surface2 = Color(0xFF1A2F33),
    surface3 = Color(0xFF21393E),
    border = Color(0xFF1F3A40),
    border2 = Color(0x44183238),
    text = Color(0xFFECF3F4),
    textMute = Color(0xFF94AEB2),
    textDim = Color(0xFF6A8589),
    accent = Color(0xFF2DD4C2),
    accent2 = Color(0xFF0FB9A7),
    accentInk = Color(0xFF001917),
    glow = Color(0x2E2DD4C2), // rgba(45,212,194,0.18)
    warn = Color(0xFFF5C563),
    danger = Color(0xFFFF6B8A),
    blocked = Color(0xFFFF8AAB),
    allowed = Color(0xFF2DD4C2),
    pass = Color(0xFF82A0A4),
    isDark = true,
)

val AhColorsLight = AhColors(
    bg = Color(0xFFF3F6F6),
    bg2 = Color(0xFFE9EFF0),
    surface = Color(0xFFFFFFFF),
    surface2 = Color(0xFFF4F8F8),
    surface3 = Color(0xFFEBF1F1),
    border = Color(0xFFD3DEE0),
    border2 = Color(0xFFE1E9EA),
    text = Color(0xFF0D2225),
    textMute = Color(0xFF506A6E),
    textDim = Color(0xFF8AA0A4),
    accent = Color(0xFF0A8E80),
    accent2 = Color(0xFF066A5E),
    accentInk = Color(0xFFFFFFFF),
    glow = Color(0x1F0A8E80),
    warn = Color(0xFFA8770A),
    danger = Color(0xFFB3204A),
    blocked = Color(0xFFB3204A),
    allowed = Color(0xFF0A8E80),
    pass = Color(0xFF8AA0A4),
    isDark = false,
)

val LocalAhColors = compositionLocalOf { AhColorsDark }
