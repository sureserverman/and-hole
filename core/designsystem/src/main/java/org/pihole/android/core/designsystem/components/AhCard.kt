package org.pihole.android.core.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.pihole.android.core.designsystem.theme.AhTheme

/**
 * 14dp rounded card with 1dp border in `--ah-border` (or accent if [accent]).
 * No drop shadow — uses border + surface contrast only.
 */
@Composable
fun AhCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(14.dp),
    accent: Boolean = false,
    surfaceColor: Color = AhTheme.colors.surface,
    borderColor: Color? = null,
    cornerRadiusDp: Int = 14,
    content: @Composable () -> Unit,
) {
    val border = borderColor ?: if (accent) AhTheme.colors.accent else AhTheme.colors.border
    val shape = RoundedCornerShape(cornerRadiusDp.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(surfaceColor)
            .border(BorderStroke(1.dp, border), shape)
            .padding(contentPadding),
    ) {
        content()
    }
}
