package org.pihole.android.feature.home.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.pihole.android.core.designsystem.components.AhCard
import org.pihole.android.core.designsystem.theme.AhTheme

@Composable
fun SetupWizardCard(
    state: SetupUiState,
    onComplete: () -> Unit,
) {
    val c = AhTheme.colors
    val total = state.steps.size.coerceAtLeast(1)
    val done = state.steps.count { it.complete }
    AhCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("setup_wizard_card"),
        accent = true,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "STEP ${(done + 1).toString().padStart(2, '0')} • ${total.toString().padStart(2, '0')}",
                style = AhTheme.text.monoEyebrow,
                color = c.accent,
            )
            Text(
                text = "Guided setup",
                style = AhTheme.text.pageTitle.copy(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
                color = c.text,
            )
            Text(
                text = "Finish the key setup steps so your device routes queries through the local DNS listener reliably.",
                style = AhTheme.text.body,
                color = c.textMute,
            )
            if (state.recommendedActions.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    state.recommendedActions.forEach { action ->
                        Text(
                            text = "→ $action",
                            style = AhTheme.text.monoCaption,
                            color = c.textMute,
                        )
                    }
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CircleShape)
                    .background(c.accent)
                    .clickable(onClick = onComplete)
                    .padding(vertical = 12.dp)
                    .testTag("setup_complete_button"),
            ) {
                Text(
                    text = "Mark complete →",
                    style = AhTheme.text.pill,
                    color = c.accentInk,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

