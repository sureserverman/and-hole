package org.pihole.android.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.pihole.android.feature.home.setup.SetupViewModel
import org.pihole.android.feature.home.setup.ClientConfigCard
import org.pihole.android.feature.home.setup.SetupChecklistCard
import org.pihole.android.feature.home.setup.SetupWizardCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    setupViewModel: SetupViewModel,
    onOpenDiagnostics: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val setupState by setupViewModel.uiState.collectAsStateWithLifecycle()

    val snippet = remember(uiState.listenerPort) {
        """
        {
          "dns": {
            "servers": [
              {
                "tag": "andhole_udp",
                "type": "udp",
                "server": "127.0.0.1",
                "server_port": ${uiState.listenerPort}
              }
            ],
            "final": "andhole_udp"
          }
        }
        """.trimIndent()
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Home", modifier = Modifier.testTag("home_top_bar_title")) },
            )
        },
    ) { innerPadding ->
        val scroll = rememberScrollState()
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(scroll),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (setupState.showOnboarding) {
                SetupWizardCard(
                    state = setupState,
                    onComplete = setupViewModel::completeOnboardingIfReady,
                )
                SetupChecklistCard(state = setupState)
                ClientConfigCard(
                    state = setupState,
                    onSelectMode = setupViewModel::selectClientMode,
                    onDismissBindRecommendation = setupViewModel::dismissBindAllInterfacesRecommendation,
                )
            }

            ServiceStatusCard(
                state = uiState,
                onStartListener = viewModel::startListener,
                onStopListener = viewModel::stopListener,
                onRefreshLists = viewModel::refreshLists,
            )

            RuntimeSummaryCard(uiState)
            DatasetSummaryCard(uiState)
            RecentBlockedCard(uiState)

            Button(
                onClick = onOpenDiagnostics,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("home_open_diagnostics"),
            ) {
                Text("Open diagnostics")
            }

            Text(
                "sing-box quick snippet",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.testTag("home_sing_box_snippet_header"),
            )
            androidx.compose.material3.Card(
                modifier = Modifier.testTag("home_sing_box_snippet_card"),
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                shape = MaterialTheme.shapes.large,
            ) {
                Text(text = snippet, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
