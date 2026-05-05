package org.pihole.android.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.pihole.android.core.designsystem.components.AhCard
import org.pihole.android.core.designsystem.components.MiniBarChart
import org.pihole.android.core.designsystem.components.Pill
import org.pihole.android.core.designsystem.components.PillVariant
import org.pihole.android.core.designsystem.components.PulseDot
import org.pihole.android.core.designsystem.components.StatRow
import org.pihole.android.core.designsystem.icons.AhIcons
import org.pihole.android.core.designsystem.theme.AhTheme
import org.pihole.android.data.runtime.DnsForegroundRuntimeState
import org.pihole.android.data.runtime.TorRuntimeGlance

@Composable
fun ServiceStatusCard(
    state: HomeUiState,
    onStartListener: () -> Unit,
    onStopListener: () -> Unit,
    onRefreshLists: () -> Unit,
) {
    val running = state.listenerState == DnsForegroundRuntimeState.Running
    AhCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home_control_status_card"),
        accent = running,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "DNS control center",
                    style = AhTheme.text.body.copy(fontWeight = FontWeight.SemiBold),
                    color = AhTheme.colors.text,
                )
                Pill(
                    text = if (running) "Running" else "Stopped",
                    variant = if (running) PillVariant.Solid else PillVariant.Mute,
                )
            }
            Text(
                text = if (running) "Service: running" else "Service: stopped",
                style = AhTheme.text.body,
                color = AhTheme.colors.textMute,
                modifier = Modifier.testTag("home_service_state"),
            )
            if (state.dnsServiceDetail.isNotBlank()) {
                Text(
                    text = state.dnsServiceDetail,
                    style = AhTheme.text.monoCaption,
                    color = AhTheme.colors.textDim,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionPill(label = "Start", onClick = onStartListener, primary = !running, modifier = Modifier.testTag("home_action_start"))
                ActionPill(label = "Stop", onClick = onStopListener, modifier = Modifier.testTag("home_action_stop"))
                ActionPill(
                    label = if (state.refreshing) "Refreshing…" else "Refresh lists",
                    onClick = onRefreshLists,
                    modifier = Modifier.testTag("home_action_refresh_lists"),
                )
            }
        }
    }
}

@Composable
private fun ActionPill(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
) {
    val c = AhTheme.colors
    val bg = if (primary) c.accent else Color.Transparent
    val fg = if (primary) c.accentInk else c.accent
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(bg)
            .border(1.dp, c.accent, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(text = label, style = AhTheme.text.pill, color = fg)
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
    AhCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home_runtime_summary_card"),
    ) {
        Column {
            Text(
                text = "Runtime",
                style = AhTheme.text.body.copy(fontWeight = FontWeight.SemiBold),
                color = AhTheme.colors.text,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            StatRow(
                label = "Listener",
                value = ":${state.listenerPort} · ${if (state.bindAllInterfaces) "all ifaces" else "loopback"}",
                valueMono = true,
            )
            StatRow(
                label = "Tor runtime",
                trailing = {
                    Text(
                        text = state.torRuntimeMode,
                        style = AhTheme.text.monoData,
                        color = AhTheme.colors.text,
                        modifier = Modifier.testTag("home_tor_runtime_mode"),
                    )
                },
            )
            StatRow(
                label = "Tor",
                trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PulseDot(color = AhTheme.colors.accent, sizeDp = 8)
                        Text(
                            text = state.torLine,
                            style = AhTheme.text.monoData,
                            color = AhTheme.colors.text,
                        )
                    }
                },
            )
            StatRow(
                label = "SOCKS",
                value = state.socksLine,
                valueMono = true,
                showDivider = torSecondary != null,
            )
            torSecondary?.let {
                Text(
                    text = it,
                    style = AhTheme.text.monoCaption,
                    color = AhTheme.colors.textDim,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

@Composable
fun DatasetSummaryCard(state: HomeUiState) {
    AhCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home_counts_card"),
    ) {
        Column {
            Text(
                text = "Today",
                style = AhTheme.text.body.copy(fontWeight = FontWeight.SemiBold),
                color = AhTheme.colors.text,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                StatTile(
                    title = "Adlists",
                    value = state.adlistCount.toString(),
                    sub = "active sources",
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    title = "Blocked",
                    value = state.recentBlockedDomains.size.toString(),
                    sub = "recent",
                    valueColor = AhTheme.colors.blocked,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            ) {
                StatTile(
                    title = "Rules",
                    value = state.customRuleCount.toString(),
                    sub = "custom",
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    title = "Local DNS",
                    value = state.localDnsCount.toString(),
                    sub = "records",
                    modifier = Modifier.weight(1f),
                )
            }
            Box(modifier = Modifier.padding(top = 12.dp)) {
                MiniBarChart(values = sampleActivity, heightDp = 56)
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("activity 24h", style = AhTheme.text.monoCaption, color = AhTheme.colors.textDim)
                Text(
                    "peak ${sampleActivity.max()}",
                    style = AhTheme.text.monoCaption,
                    color = AhTheme.colors.textDim,
                )
            }
        }
    }
}

private val sampleActivity = listOf(
    320, 410, 280, 220, 510, 640, 520, 380,
    470, 590, 430, 350, 280, 410, 540, 360,
    310, 480, 600, 540, 410, 290, 250, 320,
)

@Composable
private fun StatTile(
    title: String,
    value: String,
    sub: String,
    modifier: Modifier = Modifier,
    valueColor: Color = AhTheme.colors.text,
) {
    val c = AhTheme.colors
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(c.surface2)
            .border(1.dp, c.border, RoundedCornerShape(14.dp))
            .padding(14.dp),
    ) {
        Text(
            text = title,
            style = AhTheme.text.sectionLabel,
            color = c.textMute,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        Text(
            text = value,
            style = AhTheme.text.statValue,
            color = valueColor,
        )
        Text(
            text = sub,
            style = AhTheme.text.monoCaption,
            color = c.textDim,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
fun RecentBlockedCard(state: HomeUiState) {
    AhCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home_recent_blocked_card"),
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Recent blocks",
                    style = AhTheme.text.body.copy(fontWeight = FontWeight.SemiBold),
                    color = AhTheme.colors.text,
                )
                Text(
                    text = "last ${state.recentBlockedDomains.size}",
                    style = AhTheme.text.monoCaption,
                    color = AhTheme.colors.textDim,
                )
            }
            if (state.recentBlockedDomains.isEmpty()) {
                Text(
                    text = "No blocked queries yet",
                    style = AhTheme.text.body,
                    color = AhTheme.colors.textMute,
                    modifier = Modifier.padding(top = 10.dp),
                )
            } else {
                state.recentBlockedDomains.take(8).forEach { domain ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                imageVector = AhIcons.Close,
                                contentDescription = null,
                                tint = AhTheme.colors.blocked,
                                modifier = Modifier.size(12.dp),
                            )
                            Text(
                                text = domain,
                                style = AhTheme.text.streamRow.copy(fontSize = 12.sp),
                                color = AhTheme.colors.text,
                            )
                        }
                        Pill(text = "blocked", variant = PillVariant.Blocked)
                    }
                }
            }
        }
    }
}
