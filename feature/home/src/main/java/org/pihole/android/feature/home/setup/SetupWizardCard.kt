package org.pihole.android.feature.home.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
fun SetupWizardCard(
    state: SetupUiState,
    onComplete: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("setup_wizard_card"),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Guided setup", style = MaterialTheme.typography.titleMedium)
            Text(
                "Finish the key setup steps so your device routes queries through the local DNS listener reliably.",
                style = MaterialTheme.typography.bodySmall,
            )
            if (state.recommendedActions.isNotEmpty()) {
                state.recommendedActions.forEach { action ->
                    Text("- $action", style = MaterialTheme.typography.bodySmall)
                }
            }
            Button(
                onClick = onComplete,
                modifier = Modifier.testTag("setup_complete_button"),
            ) {
                Text("Finish onboarding")
            }
        }
    }
}
