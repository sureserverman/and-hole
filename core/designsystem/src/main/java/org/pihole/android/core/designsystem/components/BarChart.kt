package org.pihole.android.core.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.pihole.android.core.designsystem.theme.AhTheme
import kotlin.math.max

/**
 * Mini vertical bar chart (24 bars) for the Home "Activity" card.
 * Rounded caps via clipping; values clamped to [0, max].
 */
@Composable
fun MiniBarChart(
    values: List<Int>,
    modifier: Modifier = Modifier,
    barColor: Color = AhTheme.colors.accent,
    trackColor: Color = AhTheme.colors.border2,
    heightDp: Int = 64,
) {
    val peak = max(values.maxOrNull() ?: 1, 1)
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(heightDp.dp),
    ) {
        if (values.isEmpty()) return@Canvas
        val gap = 3f
        val total = size.width
        val barWidth = (total - gap * (values.size - 1)) / values.size
        values.forEachIndexed { i, v ->
            val ratio = v.toFloat() / peak
            val x = i * (barWidth + gap)
            val barH = size.height * ratio
            // Track
            drawRect(
                color = trackColor,
                topLeft = Offset(x, 0f),
                size = Size(barWidth, size.height),
            )
            // Filled bar
            drawRect(
                color = barColor,
                topLeft = Offset(x, size.height - barH),
                size = Size(barWidth, barH),
            )
        }
    }
}

/** Horizontal bar (used in the Logs Insights "Top blocked" row). */
@Composable
fun HBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    fillColor: Color = AhTheme.colors.blocked,
    trackColor: Color = AhTheme.colors.border2,
    heightDp: Int = 6,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(heightDp.dp),
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(heightDp.dp)) {
            drawRect(color = trackColor, size = size)
            val width = size.width * fraction.coerceIn(0f, 1f)
            drawRect(color = fillColor, size = Size(width, size.height))
        }
    }
}
