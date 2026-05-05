package org.pihole.android.feature.home.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.pihole.android.core.designsystem.components.AhCard
import org.pihole.android.core.designsystem.theme.AhTheme

@Composable
fun SetupChecklistCard(state: SetupUiState) {
    val c = AhTheme.colors
    AhCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("setup_checklist_card"),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "Checklist",
                style = AhTheme.text.body.copy(fontWeight = FontWeight.SemiBold),
                color = c.text,
            )
            state.steps.forEach { step ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("setup_step_${step.step.name.lowercase()}"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(if (step.complete) c.accent else androidx.compose.ui.graphics.Color.Transparent)
                            .border(1.dp, if (step.complete) c.accent else c.border, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (step.complete) "✓" else "•",
                            color = if (step.complete) c.accentInk else c.textMute,
                            style = AhTheme.text.body.copy(fontWeight = FontWeight.SemiBold),
                        )
                    }
                    Text(
                        text = step.summary,
                        style = AhTheme.text.body,
                        color = if (step.complete) c.text else c.textMute,
                    )
                }
            }
        }
    }
}
