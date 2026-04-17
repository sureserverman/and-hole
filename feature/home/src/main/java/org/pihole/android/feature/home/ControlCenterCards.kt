package org.pihole.android.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.pihole.android.data.runtime.DnsForegroundRuntimeState
import org.pihole.android.data.runtime.TorRuntimeGlance

@Composable
fun ServiceStatusCard(
    state: HomeUiState,
    onStartListener: () -> Unit,
    onStopListener: () -> Unit,
    onRefreshLists: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home_control_status_card"),
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
            Text("DNS control center", style = MaterialTheme.typography.titleMedium)
            Text(
                if (state.listenerState == DnsForegroundRuntimeState.Running) {
                    "Service: running"
                } else {
                    "Service: stopped"
                },
                modifier = Modifier.testTag("home_service_state"),
                style = MaterialTheme.typography.bodyLarge,
            )
            if (state.dnsServiceDetail.isNotBlank()) {
                Text(state.dnsServiceDetail, style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onStartListener, modifier = Modifier.testTag("home_action_start")) {
                    Text("Start")
                }
                FilledTonalButton(onClick = onStopListener, modifier = Modifier.testTag("home_action_stop")) {
                    Text("Stop")
                }
                FilledTonalButton(onClick = onRefreshLists, modifier = Modifier.testTag("home_action_refresh_lists")) {
                    Text(if (state.refreshing) "Refreshing..." else "Refresh lists")
                }
            }
        }
    }
}

@Composable
fun RuntimeSummaryCard(state: HomeUiState) {
    val torSecondary =
        TorRuntimeGlance.secondaryLine(
            state.torBootstrapProgress,
            state.torBootstrapSummary,
            state.torLastError,
        )
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home_runtime_summary_card"),
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
            Text("Runtime summary", style = MaterialTheme.typography.titleMedium)
            Text("Port: ${state.listenerPort}")
            Text("Bind mode: ${if (state.bindAllInterfaces) "all interfaces (0.0.0.0)" else "loopback only (127.0.0.1)"}")
            Text("Tor runtime", style = MaterialTheme.typography.labelMedium)
            Text(
                state.torRuntimeMode,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.testTag("home_tor_runtime_mode"),
            )
            torSecondary?.let {
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
            Text("Tor: ${state.torLine}")
            Text("SOCKS: ${state.socksLine}")
        }
    }
}

@Composable
fun DatasetSummaryCard(state: HomeUiState) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home_counts_card"),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Policy datasets", style = MaterialTheme.typography.titleMedium)
            Text("Adlists: ${state.adlistCount}")
            Text("Custom rules: ${state.customRuleCount}")
            Text("Local DNS records: ${state.localDnsCount}")
        }
    }
}

@Composable
fun RecentBlockedCard(state: HomeUiState) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home_recent_blocked_card"),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Recent blocked domains", style = MaterialTheme.typography.titleMedium)
            if (state.recentBlockedDomains.isEmpty()) {
                Text("No blocked queries yet", style = MaterialTheme.typography.bodySmall)
            } else {
                state.recentBlockedDomains.forEachIndexed { index, domain ->
                    Text("${index + 1}. $domain", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
