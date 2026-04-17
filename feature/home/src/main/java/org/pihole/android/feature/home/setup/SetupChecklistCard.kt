package org.pihole.android.feature.home.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
fun SetupChecklistCard(state: SetupUiState) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("setup_checklist_card"),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Checklist", style = MaterialTheme.typography.titleMedium)
            state.steps.forEach { step ->
                val icon = if (step.complete) "✓" else "•"
                Text(
                    "$icon ${step.summary}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.testTag("setup_step_${step.step.name.lowercase()}"),
                )
            }
        }
    }
}
