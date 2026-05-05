package org.pihole.android.core.designsystem.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import org.pihole.android.core.designsystem.icons.AhIcons
import org.pihole.android.core.designsystem.theme.AhTheme

/**
 * Concentric protection-status orb:
 *  - 132dp outer ring (1dp accent at 30% opacity)
 *  - animated pulse ring
 *  - 90dp inner filled disc
 *  - centered shield icon (40dp)
 *
 * `protected = false` desaturates to the mute palette and stops the pulse.
 */
@Composable
fun StatusOrb(
    protected: Boolean,
    modifier: Modifier = Modifier,
) {
    val accent = AhTheme.colors.accent
    val mute = AhTheme.colors.textDim
    val ringColor = if (protected) accent else mute
    val ringOuterAlpha = if (protected) 0.30f else 0.18f
    val pulseAlpha = if (protected) 0.6f else 0f
    val innerFill = if (protected) AhTheme.colors.surface2 else AhTheme.colors.bg2

    val pulse = remember { Animatable(0f) }
    LaunchedEffect(protected) {
        if (protected) {
            pulse.snapTo(0f)
            pulse.animateTo(
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 1600, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
            )
        } else {
            pulse.snapTo(0f)
        }
    }

    Box(modifier = modifier.size(140.dp), contentAlignment = Alignment.Center) {
        // Outer static ring
        Box(
            modifier = Modifier
                .size(132.dp)
                .border(1.dp, ringColor.copy(alpha = ringOuterAlpha), CircleShape),
        )
        // Pulse ring
        if (protected) {
            val scale = 1f + pulse.value * 0.3f
            val alpha = (1f - pulse.value) * pulseAlpha
            Box(
                modifier = Modifier
                    .size(132.dp)
                    .scale(scale)
                    .alpha(alpha)
                    .border(1.dp, ringColor, CircleShape),
            )
        }
        // Inner disc
        Box(
            modifier = Modifier
                .size(90.dp)
                .clip(CircleShape)
                .background(innerFill)
                .border(1.dp, ringColor.copy(alpha = 0.45f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = AhIcons.Shield,
                contentDescription = if (protected) "Protected" else "Idle",
                tint = ringColor,
                modifier = Modifier.size(40.dp),
            )
        }
    }
}

@Composable
fun PulseDot(
    color: Color = AhTheme.colors.accent,
    modifier: Modifier = Modifier,
    sizeDp: Int = 8,
) {
    val pulse = remember { Animatable(0.4f) }
    LaunchedEffect(Unit) {
        pulse.animateTo(
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1400, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        )
    }
    Canvas(modifier = modifier.size(sizeDp.dp)) {
        drawCircle(color = color, alpha = pulse.value)
        drawCircle(
            color = color,
            alpha = pulse.value * 0.4f,
            radius = size.minDimension * 0.85f,
            center = Offset(size.width / 2, size.height / 2),
            style = Stroke(width = 1f),
        )
    }
}
