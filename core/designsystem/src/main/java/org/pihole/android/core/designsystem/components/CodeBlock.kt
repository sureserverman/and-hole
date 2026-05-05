package org.pihole.android.core.designsystem.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.pihole.android.core.designsystem.icons.AhIcons
import org.pihole.android.core.designsystem.theme.AhTheme

/**
 * Card shaped 14dp with a header bar (filename + Copy) and a syntax-mute body.
 * The terminal cursor blinks.
 */
@Composable
fun CodeBlockCard(
    fileName: String,
    body: String,
    modifier: Modifier = Modifier,
    onCopy: () -> Unit = {},
) {
    val c = AhTheme.colors
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.surface)
            .border(1.dp, c.border, shape),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(c.surface2)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = fileName,
                style = AhTheme.text.monoData.copy(fontSize = 11.5.sp),
                color = c.accent,
            )
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(c.surface3)
                    .clickable(onClick = onCopy)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = AhIcons.Export,
                    contentDescription = null,
                    tint = c.textMute,
                    modifier = Modifier.size(12.dp),
                )
                Text(
                    text = "Copy",
                    style = AhTheme.text.body.copy(fontSize = 11.sp),
                    color = c.textMute,
                )
            }
        }
        Box(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = body,
                    style = AhTheme.text.streamRow.copy(fontSize = 11.5.sp),
                    color = c.textMute,
                )
                BlinkingCursor()
            }
        }
    }
}

@Composable
private fun BlinkingCursor() {
    val transition = rememberInfiniteTransition(label = "cursor")
    val alpha by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "cursor-alpha",
    )
    Box(
        modifier = Modifier
            .alpha(alpha)
            .size(width = 7.dp, height = 14.dp)
            .background(AhTheme.colors.accent),
    )
}
