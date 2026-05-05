package org.pihole.android.feature.logs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.pihole.android.core.designsystem.components.AhCard
import org.pihole.android.core.designsystem.components.HBar
import org.pihole.android.core.designsystem.theme.AhTheme
import kotlin.math.max

@Composable
fun LogsInsightsCard(state: LogsInsightsUiState) {
    val c = AhTheme.colors
    AhCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("logs_insights_card"),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Top blocked (24h)",
                    style = AhTheme.text.body.copy(fontWeight = FontWeight.SemiBold),
                    color = c.text,
                )
                Text(
                    text = "decisions",
                    style = AhTheme.text.monoCaption,
                    color = c.textDim,
                )
            }

            Text(
                text = state.decisionCounts.joinToString("  ") { "${it.decision}=${it.hits}" }
                    .ifBlank { "no decisions yet" },
                style = AhTheme.text.monoCaption,
                color = c.textMute,
                modifier = Modifier.testTag("logs_insights_decisions"),
            )

            // Top blocked bars
            val maxHits = max(state.topBlockedDomains.maxOfOrNull { it.hits } ?: 1, 1)
            if (state.topBlockedDomains.isEmpty()) {
                Text(
                    text = "—",
                    style = AhTheme.text.monoData,
                    color = c.textDim,
                    modifier = Modifier.testTag("logs_insights_top_blocked"),
                )
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.testTag("logs_insights_top_blocked"),
                ) {
                    state.topBlockedDomains.take(5).forEach { entry ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = entry.qname,
                                style = AhTheme.text.monoData,
                                color = c.text,
                                modifier = Modifier.width(120.dp),
                                maxLines = 1,
                            )
                            Box(modifier = Modifier.weight(1f)) {
                                HBar(fraction = entry.hits.toFloat() / maxHits)
                            }
                            Text(
                                text = entry.hits.toString(),
                                style = AhTheme.text.monoData,
                                color = c.textMute,
                            )
                        }
                    }
                }
            }

            // Top allowed (compact)
            Text(
                text = "Top allowed: ${
                    state.topAllowedDomains.take(3)
                        .joinToString(", ") { "${it.qname} (${it.hits})" }
                        .ifBlank { "none" }
                }",
                style = AhTheme.text.monoCaption,
                color = c.textMute,
                modifier = Modifier.testTag("logs_insights_top_allowed"),
            )
            Text(
                text = "cache=${state.cacheHits} · upstream=${state.upstreamPasses}",
                style = AhTheme.text.monoCaption,
                color = c.textDim,
                modifier = Modifier.testTag("logs_insights_cache_upstream"),
            )
        }
    }
}
