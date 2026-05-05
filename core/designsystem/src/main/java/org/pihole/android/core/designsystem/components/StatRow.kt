package org.pihole.android.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import org.pihole.android.core.designsystem.theme.AhTheme

/**
 * Label/value row, 8dp vertical padding, 1dp inner divider.
 * Pass [valueMono] = true for data values; use [trailing] slot for switch/select/chevron.
 */
@Composable
fun StatRow(
    label: String,
    modifier: Modifier = Modifier,
    value: String? = null,
    valueMono: Boolean = false,
    showDivider: Boolean = true,
    valueColor: Color = AhTheme.colors.text,
    trailing: (@Composable () -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label,
                style = AhTheme.text.body,
                color = AhTheme.colors.textMute,
            )
            if (trailing != null) {
                trailing()
            } else if (value != null) {
                val style: TextStyle = if (valueMono) AhTheme.text.monoData else AhTheme.text.body
                Text(text = value, style = style, color = valueColor)
            }
        }
        if (showDivider) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(AhTheme.colors.border2),
            )
        }
    }
}
