package org.pihole.android.feature.home.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp

@Composable
fun ClientConfigCard(
    state: SetupUiState,
    onSelectMode: (String) -> Unit,
    onDismissBindRecommendation: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("setup_client_config_card"),
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
            Text("Client mode", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onSelectMode("sing_box") }, modifier = Modifier.testTag("setup_mode_sing_box")) {
                    Text("sing-box")
                }
                Button(onClick = { onSelectMode("android_private_dns") }, modifier = Modifier.testTag("setup_mode_private_dns")) {
                    Text("Private DNS")
                }
                Button(onClick = { onSelectMode("other") }, modifier = Modifier.testTag("setup_mode_other")) {
                    Text("Other")
                }
            }
            Text(
                "Selected: ${state.selectedClientMode.ifBlank { "none" }}",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                state.generatedConfig,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("setup_generated_config"),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { clipboard.setText(AnnotatedString(state.generatedConfig)) },
                    modifier = Modifier.testTag("setup_copy_config"),
                ) {
                    Text("Copy config")
                }
                Button(
                    onClick = onDismissBindRecommendation,
                    modifier = Modifier.testTag("setup_dismiss_bind_recommendation"),
                ) {
                    Text("Dismiss bind-all hint")
                }
            }
        }
    }
}
