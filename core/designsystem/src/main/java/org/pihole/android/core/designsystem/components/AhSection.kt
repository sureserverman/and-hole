package org.pihole.android.core.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.pihole.android.core.designsystem.theme.AhTheme

/** Labeled section: 16dp top / 4dp bottom, label 10dp above content. */
@Composable
fun AhSection(
    label: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label.uppercase(),
                style = AhTheme.text.sectionLabel,
                color = AhTheme.colors.textMute,
            )
            if (hint != null) {
                Text(
                    text = hint,
                    style = AhTheme.text.monoEyebrow,
                    color = AhTheme.colors.textDim,
                )
            }
        }
        Column(modifier = Modifier.padding(top = 10.dp, start = 16.dp, end = 16.dp)) {
            content()
        }
    }
}
