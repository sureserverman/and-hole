package org.pihole.android.core.designsystem.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.pihole.android.core.designsystem.theme.AhTheme

/** 38×22 pill switch matching the design spec; animated knob 0.18s. */
@Composable
fun PillSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val c = AhTheme.colors
    val track = if (checked) c.accent else c.surface2
    val border = if (checked) c.accent else c.border
    val knobColor: Color = if (checked) c.accentInk else c.text
    val offsetTarget = if (checked) 18.dp else 2.dp
    val animatedOffset by animateDpAsState(
        targetValue = offsetTarget,
        animationSpec = tween(durationMillis = 180),
        label = "pill-knob",
    )

    Box(
        modifier = modifier
            .size(width = 38.dp, height = 22.dp)
            .clip(CircleShape)
            .background(track)
            .border(1.dp, border, CircleShape)
            .clickable(enabled = enabled) { onCheckedChange(!checked) },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .offset(x = animatedOffset)
                .size(18.dp)
                .clip(CircleShape)
                .background(knobColor),
        )
    }
}
