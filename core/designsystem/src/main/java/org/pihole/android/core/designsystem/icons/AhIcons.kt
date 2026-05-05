package org.pihole.android.core.designsystem.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Hand-rolled vector icons matching `tokens.jsx` (24×24 viewBox, round caps/joins, stroke 1.5-2). */
object AhIcons {
    val Shield: ImageVector by lazy { strokeIcon("shield") {
        moveTo(12f, 3f); lineTo(20f, 6f); verticalLineTo(12f)
        curveTo(20f, 16.5f, 16.5f, 20f, 12f, 21f)
        curveTo(7.5f, 20f, 4f, 16.5f, 4f, 12f)
        verticalLineTo(6f); close()
    }}
    val Check: ImageVector by lazy { strokeIcon("check") {
        moveTo(4f, 12f); lineTo(10f, 18f); lineTo(20f, 6f)
    }}
    val Close: ImageVector by lazy { strokeIcon("x") {
        moveTo(6f, 6f); lineTo(18f, 18f)
        moveTo(18f, 6f); lineTo(6f, 18f)
    }}
    val Home: ImageVector by lazy { strokeIcon("home") {
        moveTo(3f, 11f); lineTo(12f, 3f); lineTo(21f, 11f)
        moveTo(5f, 10f); verticalLineTo(20f); horizontalLineTo(19f); verticalLineTo(10f)
    }}
    val ListIcon: ImageVector by lazy { strokeIcon("list") {
        moveTo(8f, 6f); horizontalLineTo(20f)
        moveTo(8f, 12f); horizontalLineTo(20f)
        moveTo(8f, 18f); horizontalLineTo(20f)
        moveTo(4f, 6f); horizontalLineTo(4.01f)
        moveTo(4f, 12f); horizontalLineTo(4.01f)
        moveTo(4f, 18f); horizontalLineTo(4.01f)
    }}
    val Search: ImageVector by lazy { strokeIcon("search") {
        moveTo(11f, 11f); arcToRelative(6f, 6f, 0f, true, true, 0.001f, 0f); close()
        moveTo(20f, 20f); lineTo(15.5f, 15.5f)
    }}
    val Rules: ImageVector by lazy { strokeIcon("rules") {
        moveTo(4f, 5f); horizontalLineTo(20f)
        moveTo(4f, 12f); horizontalLineTo(14f)
        moveTo(4f, 19f); horizontalLineTo(20f)
        moveTo(17f, 9f); lineTo(20f, 12f); lineTo(17f, 15f)
    }}
    val Settings: ImageVector by lazy { strokeIcon("settings") {
        moveTo(12f, 8f); arcToRelative(4f, 4f, 0f, true, true, 0.001f, 0f); close()
        moveTo(19.4f, 13f); arcToRelative(7.5f, 7.5f, 0f, false, false, 0f, -2f)
        lineTo(21.5f, 9.5f); lineTo(19.5f, 6.5f); lineTo(17.4f, 7.5f)
        arcToRelative(7.5f, 7.5f, 0f, false, false, -1.7f, -1f)
        lineTo(15f, 4f); horizontalLineTo(11f); lineTo(10.3f, 6.5f)
        arcToRelative(7.5f, 7.5f, 0f, false, false, -1.7f, 1f)
        lineTo(6.5f, 6.5f); lineTo(4.5f, 9.5f); lineTo(6.6f, 11f)
        arcToRelative(7.5f, 7.5f, 0f, false, false, 0f, 2f)
        lineTo(4.5f, 14.5f); lineTo(6.5f, 17.5f); lineTo(8.6f, 16.5f)
        arcToRelative(7.5f, 7.5f, 0f, false, false, 1.7f, 1f)
        lineTo(11f, 20f); horizontalLineTo(15f); lineTo(15.7f, 17.5f)
        arcToRelative(7.5f, 7.5f, 0f, false, false, 1.7f, -1f)
        lineTo(19.5f, 17.5f); lineTo(21.5f, 14.5f); close()
    }}
    val Plus: ImageVector by lazy { strokeIcon("plus") {
        moveTo(12f, 5f); verticalLineTo(19f); moveTo(5f, 12f); horizontalLineTo(19f)
    }}
    val ArrowRight: ImageVector by lazy { strokeIcon("arrow-right") {
        moveTo(5f, 12f); horizontalLineTo(19f)
        moveTo(13f, 6f); lineTo(19f, 12f); lineTo(13f, 18f)
    }}
    val Play: ImageVector by lazy { strokeIcon("play") {
        moveTo(7f, 5f); lineTo(20f, 12f); lineTo(7f, 19f); close()
    }}
    val Stop: ImageVector by lazy { strokeIcon("stop") {
        moveTo(6f, 6f); horizontalLineTo(18f); verticalLineTo(18f); horizontalLineTo(6f); close()
    }}
    val Refresh: ImageVector by lazy { strokeIcon("refresh") {
        moveTo(20f, 12f); arcToRelative(8f, 8f, 0f, true, true, -2.5f, -5.7f)
        moveTo(20f, 4f); verticalLineTo(8f); horizontalLineTo(16f)
    }}
    val Tor: ImageVector by lazy { strokeIcon("tor") {
        // Onion glyph: stacked arcs
        moveTo(12f, 4f); curveTo(7f, 4f, 4f, 8f, 4f, 12f); curveTo(4f, 16f, 7f, 20f, 12f, 20f)
        curveTo(17f, 20f, 20f, 16f, 20f, 12f); curveTo(20f, 8f, 17f, 4f, 12f, 4f); close()
        moveTo(12f, 6.5f); curveTo(9f, 6.5f, 7f, 9f, 7f, 12f); curveTo(7f, 15f, 9f, 17.5f, 12f, 17.5f)
        moveTo(12f, 9f); curveTo(10.5f, 9f, 9.5f, 10.5f, 9.5f, 12f); curveTo(9.5f, 13.5f, 10.5f, 15f, 12f, 15f)
        moveTo(12f, 4f); verticalLineTo(20f)
    }}
    val Globe: ImageVector by lazy { strokeIcon("globe") {
        moveTo(12f, 4f); arcToRelative(8f, 8f, 0f, true, true, 0.001f, 0f); close()
        moveTo(4f, 12f); horizontalLineTo(20f)
        moveTo(12f, 4f); curveTo(8f, 8f, 8f, 16f, 12f, 20f)
        moveTo(12f, 4f); curveTo(16f, 8f, 16f, 16f, 12f, 20f)
    }}
    val Chevron: ImageVector by lazy { strokeIcon("chevron") {
        moveTo(9f, 6f); lineTo(15f, 12f); lineTo(9f, 18f)
    }}
    val MenuDots: ImageVector by lazy { strokeIcon("menu-dots") {
        moveTo(5f, 12f); arcToRelative(1f, 1f, 0f, true, true, 0.001f, 0f); close()
        moveTo(12f, 12f); arcToRelative(1f, 1f, 0f, true, true, 0.001f, 0f); close()
        moveTo(19f, 12f); arcToRelative(1f, 1f, 0f, true, true, 0.001f, 0f); close()
    }}
    val Export: ImageVector by lazy { strokeIcon("export") {
        moveTo(12f, 4f); verticalLineTo(15f)
        moveTo(8f, 8f); lineTo(12f, 4f); lineTo(16f, 8f)
        moveTo(5f, 18f); horizontalLineTo(19f)
    }}
    val Trash: ImageVector by lazy { strokeIcon("trash") {
        moveTo(4f, 6f); horizontalLineTo(20f)
        moveTo(6f, 6f); lineTo(7f, 20f); horizontalLineTo(17f); lineTo(18f, 6f)
        moveTo(10f, 6f); verticalLineTo(4f); horizontalLineTo(14f); verticalLineTo(6f)
    }}
    val Filter: ImageVector by lazy { strokeIcon("filter") {
        moveTo(4f, 6f); horizontalLineTo(20f)
        moveTo(7f, 12f); horizontalLineTo(17f)
        moveTo(10f, 18f); horizontalLineTo(14f)
    }}
    val Pulse: ImageVector by lazy { strokeIcon("pulse") {
        moveTo(3f, 12f); horizontalLineTo(8f); lineTo(10f, 7f); lineTo(13f, 17f); lineTo(15f, 12f); horizontalLineTo(21f)
    }}
    val Lock: ImageVector by lazy { strokeIcon("lock") {
        moveTo(6f, 11f); horizontalLineTo(18f); verticalLineTo(20f); horizontalLineTo(6f); close()
        moveTo(8f, 11f); verticalLineTo(8f); arcToRelative(4f, 4f, 0f, true, true, 8f, 0f); verticalLineTo(11f)
    }}
    val ArrowUpRight: ImageVector by lazy { strokeIcon("arrow-up-right") {
        moveTo(7f, 17f); lineTo(17f, 7f)
        moveTo(8f, 7f); horizontalLineTo(17f); verticalLineTo(16f)
    }}
}

private inline fun strokeIcon(
    name: String,
    crossinline draw: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit,
): ImageVector =
    ImageVector.Builder(
        name = "ah_$name",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).path(
        fill = null,
        stroke = SolidColor(Color.White),
        strokeLineWidth = 1.6f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
        pathFillType = PathFillType.NonZero,
    ) {
        draw()
    }.build()
