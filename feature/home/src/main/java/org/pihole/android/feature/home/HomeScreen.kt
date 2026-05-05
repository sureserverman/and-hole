package org.pihole.android.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.pihole.android.core.designsystem.components.AhAppBar
import org.pihole.android.core.designsystem.components.AhCard
import org.pihole.android.core.designsystem.components.Pill
import org.pihole.android.core.designsystem.components.PillVariant
import org.pihole.android.core.designsystem.components.PulseDot
import org.pihole.android.core.designsystem.components.StatusOrb
import org.pihole.android.core.designsystem.theme.AhTheme
import org.pihole.android.data.runtime.DnsForegroundRuntimeState
import org.pihole.android.feature.home.setup.SetupViewModel
import org.pihole.android.feature.home.setup.ClientConfigCard
import org.pihole.android.feature.home.setup.SetupChecklistCard
import org.pihole.android.feature.home.setup.SetupWizardCard

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    setupViewModel: SetupViewModel,
    onOpenDiagnostics: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val setupState by setupViewModel.uiState.collectAsStateWithLifecycle()

    val protectedNow = uiState.listenerState == DnsForegroundRuntimeState.Running
    val sub = remember(uiState.torRuntimeMode) {
        "protection · cloudflare via ${uiState.torRuntimeMode.lowercase()}"
    }

    Scaffold(
        containerColor = AhTheme.colors.bg,
        topBar = {
            AhAppBar(
                title = "Home",
                sub = sub,
                modifier = Modifier.testTag("home_top_bar_title"),
                right = {
                    Pill(
                        text = if (protectedNow) "Live" else "Idle",
                        variant = if (protectedNow) PillVariant.Solid else PillVariant.Mute,
                    )
                },
            )
        },
    ) { innerPadding ->
        val scroll = rememberScrollState()
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(scroll),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            HeroBlock(protected = protectedNow, uiState = uiState)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
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
                DatasetSummaryCard(uiState)
                RuntimeSummaryCard(uiState)
                RecentBlockedCard(uiState)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(CircleShape)
                        .background(AhTheme.colors.surface)
                        .border(1.dp, AhTheme.colors.accent, CircleShape)
                        .clickable(onClick = onOpenDiagnostics)
                        .padding(vertical = 12.dp)
                        .testTag("home_open_diagnostics"),
                ) {
                    Text(
                        text = "Open diagnostics",
                        style = AhTheme.text.pill,
                        color = AhTheme.colors.accent,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                }

                Text(
                    text = "sing-box quick snippet",
                    style = AhTheme.text.sectionLabel,
                    color = AhTheme.colors.textMute,
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .testTag("home_sing_box_snippet_header"),
                )
                AhCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("home_sing_box_snippet_card"),
                ) {
                    Text(
                        text = singBoxSnippet(uiState.listenerPort),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            fontSize = 11.5.sp,
                        ),
                        color = AhTheme.colors.textMute,
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroBlock(protected: Boolean, uiState: HomeUiState) {
    val c = AhTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StatusOrb(protected = protected)

        androidx.compose.foundation.layout.Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (protected) c.accent else c.textDim),
            )
            Text(
                text = if (protected) "Protection active" else "Protection idle",
                style = AhTheme.text.body,
                color = c.textMute,
            )
        }
        Text(
            text = if (protected) {
                "Tor circuit healthy · ${uiState.adlistCount} adlists active"
            } else {
                "Start the listener to begin filtering DNS"
            },
            style = AhTheme.text.monoCaption,
            color = c.textDim,
            textAlign = TextAlign.Center,
        )
        if (protected && uiState.recentBlockedDomains.isNotEmpty()) {
            androidx.compose.foundation.layout.Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                PulseDot(color = c.accent, sizeDp = 6)
                Text(
                    text = "live · last block ${uiState.recentBlockedDomains.first()}",
                    style = AhTheme.text.monoCaption,
                    color = c.textDim,
                )
            }
        }
    }
}

private fun singBoxSnippet(port: Int): String =
    """
    {
      "dns": {
        "servers": [
          {
            "tag": "andhole_udp",
            "type": "udp",
            "server": "127.0.0.1",
            "server_port": $port
          }
        ],
        "final": "andhole_udp"
      }
    }
    """.trimIndent()
