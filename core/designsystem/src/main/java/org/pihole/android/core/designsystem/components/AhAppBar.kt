package org.pihole.android.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.pihole.android.core.designsystem.theme.AhTheme

/**
 * Sticky top bar with eyebrow ("and-hole · v0.4"), large title, optional sub line and right slot.
 */
@Composable
fun AhAppBar(
    title: String,
    modifier: Modifier = Modifier,
    eyebrow: String = "and-hole · v0.4",
    sub: String? = null,
    right: (@Composable () -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(AhTheme.colors.bg)
            .padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    text = eyebrow,
                    style = AhTheme.text.monoEyebrow,
                    color = AhTheme.colors.textDim,
                )
                Text(
                    text = title,
                    style = AhTheme.text.pageTitle,
                    color = AhTheme.colors.text,
                    modifier = Modifier.padding(top = 2.dp),
                )
                if (sub != null) {
                    Text(
                        text = sub,
                        style = AhTheme.text.monoData.copy(fontSize = 12.sp),
                        color = AhTheme.colors.textMute,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            if (right != null) {
                Box(modifier = Modifier.padding(top = 6.dp)) { right() }
            }
        }
    }
}
