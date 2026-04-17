package org.pihole.android.feature.logs

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
fun LogsInsightsCard(state: LogsInsightsUiState) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("logs_insights_card"),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Insights", style = MaterialTheme.typography.titleMedium)
            Text(
                "Decisions: ${
                    state.decisionCounts.joinToString(", ") { "${it.decision}=${it.hits}" }
                }",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("logs_insights_decisions"),
            )
            Text(
                "Top blocked: ${
                    state.topBlockedDomains.take(3).joinToString(", ") { "${it.qname} (${it.hits})" }.ifBlank { "none" }
                }",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("logs_insights_top_blocked"),
            )
            Text(
                "Top allowed: ${
                    state.topAllowedDomains.take(3).joinToString(", ") { "${it.qname} (${it.hits})" }.ifBlank { "none" }
                }",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("logs_insights_top_allowed"),
            )
            Text(
                "Cache hits: ${state.cacheHits} | Upstream pass: ${state.upstreamPasses}",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("logs_insights_cache_upstream"),
            )
        }
    }
}
