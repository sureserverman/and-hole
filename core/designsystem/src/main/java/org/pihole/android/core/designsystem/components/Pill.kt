package org.pihole.android.core.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import org.pihole.android.core.designsystem.theme.AhTheme

enum class PillVariant { Ghost, Solid, Mute, Warn, Danger, Blocked }

/** Inline pill chip (4×10 padding, 999px radius, 1dp border). */
@Composable
fun Pill(
    text: String,
    modifier: Modifier = Modifier,
    variant: PillVariant = PillVariant.Ghost,
    leading: ImageVector? = null,
    onClick: (() -> Unit)? = null,
) {
    val c = AhTheme.colors
    val (bg, fg, br) = when (variant) {
        PillVariant.Ghost -> Triple(Color.Transparent, c.accent, c.accent)
        PillVariant.Solid -> Triple(c.accent, c.accentInk, c.accent)
        PillVariant.Mute -> Triple(Color.Transparent, c.textMute, c.border)
        PillVariant.Warn -> Triple(c.warn.copy(alpha = 0.12f), c.warn, c.warn)
        PillVariant.Danger -> Triple(Color.Transparent, c.danger, c.danger)
        PillVariant.Blocked -> Triple(Color.Transparent, c.blocked, c.blocked)
    }
    val base = modifier
        .clip(CircleShape)
        .background(bg)
        .border(BorderStroke(1.dp, br), CircleShape)
    val withClick = if (onClick != null) base.clickable(onClick = onClick) else base
    Row(
        modifier = withClick.padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (leading != null) {
            Icon(
                imageVector = leading,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(12.dp),
            )
        }
        Text(text = text.uppercase(), style = AhTheme.text.pill, color = fg)
    }
}

/** Small status dot. */
@Composable
fun PillDot(
    color: Color,
    modifier: Modifier = Modifier,
    size: Int = 8,
) {
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(color),
    )
}
