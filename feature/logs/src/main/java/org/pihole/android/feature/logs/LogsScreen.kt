package org.pihole.android.feature.logs

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.pihole.android.core.designsystem.components.AhAppBar
import org.pihole.android.core.designsystem.components.Pill
import org.pihole.android.core.designsystem.components.PillVariant
import org.pihole.android.core.designsystem.components.PulseDot
import org.pihole.android.core.designsystem.icons.AhIcons
import org.pihole.android.core.designsystem.theme.AhTheme
import org.pihole.android.data.db.entity.QueryLogEntity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun LogsScreen(
    vm: LogsViewModel,
) {
    val rows by vm.visibleLogs.collectAsStateWithLifecycle()
    val insights by vm.insights.collectAsStateWithLifecycle()
    val searchText by vm.searchQuery.collectAsStateWithLifecycle()
    val decisionFilter by vm.decisionFilter.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showExportDialog by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(vm) {
        vm.events.collect { ev ->
            when (ev) {
                is LogsUiEvent.ShareExport -> {
                    val intent = LogsExportWriter.buildShareIntent(ev.export)
                    context.startActivity(Intent.createChooser(intent, "Share logs"))
                }
                is LogsUiEvent.ToastMessage -> {
                    android.widget.Toast.makeText(context, ev.message, android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val totalToday = insights.decisionCounts.sumOf { it.hits }

    Scaffold(
        containerColor = AhTheme.colors.bg,
        topBar = {
            AhAppBar(
                title = "Logs",
                sub = "streaming · $totalToday seen",
                modifier = Modifier.testTag("logs_top_bar_title"),
                right = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconChip(icon = AhIcons.Export, label = "Export", testTag = "logs_export") {
                            showExportDialog = true
                        }
                        IconChip(icon = AhIcons.Trash, label = "Clear", testTag = "logs_clear") {
                            showClearConfirm = true
                        }
                    }
                },
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                PulseDot(color = AhTheme.colors.accent)
                Text(
                    text = "Tailing live stream",
                    style = AhTheme.text.monoCaption,
                    color = AhTheme.colors.textDim,
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
        ) {
            FilterChipsRow(
                active = decisionFilter,
                onSelect = vm::setDecisionFilter,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
            )
            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                OutlinedTextField(
                    value = searchText,
                    onValueChange = vm::setSearchQuery,
                    label = { Text(stringResource(R.string.logs_search_hint)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("logs_search_field"),
                )
            }

            // Stream header
            StreamHeader(modifier = Modifier.padding(horizontal = 16.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                itemsIndexed(rows, key = { _, item -> item.id }) { idx, row ->
                    StreamRow(
                        row = row,
                        animateEnter = idx == 0,
                        onAllow = { vm.addExactAllowFromBlocked(it) },
                        onBlock = { vm.addExactDenyFromAllowed(it) },
                    )
                }
                item {
                    Box(modifier = Modifier.padding(top = 12.dp)) {
                        LogsInsightsCard(state = insights)
                    }
                }
            }
        }
    }

    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("Export logs") },
            text = { Text("Choose a format to share.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExportDialog = false
                        vm.export(LogsExportFormat.Csv)
                    },
                    modifier = Modifier.testTag("logs_export_csv"),
                ) {
                    Text("CSV")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showExportDialog = false
                        vm.export(LogsExportFormat.Jsonl)
                    },
                    modifier = Modifier.testTag("logs_export_jsonl"),
                ) {
                    Text("JSONL")
                }
            },
        )
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear logs?") },
            text = { Text("This will delete all saved query log entries on this device.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirm = false
                        vm.clearLogs()
                    },
                    modifier = Modifier.testTag("logs_clear_confirm"),
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showClearConfirm = false },
                    modifier = Modifier.testTag("logs_clear_cancel"),
                ) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun IconChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    testTag: String,
    onClick: () -> Unit,
) {
    val c = AhTheme.colors
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .border(1.dp, c.border, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = c.text, modifier = Modifier.size(12.dp))
        Text(text = label, style = AhTheme.text.pill, color = c.text)
    }
}

@Composable
private fun FilterChipsRow(
    active: LogDecisionFilter,
    onSelect: (LogDecisionFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip("All", active == LogDecisionFilter.ALL, "logs_filter_all") { onSelect(LogDecisionFilter.ALL) }
        FilterChip("Blocked", active == LogDecisionFilter.BLOCKED, "logs_filter_blocked") { onSelect(LogDecisionFilter.BLOCKED) }
        FilterChip("Allowed", active == LogDecisionFilter.ALLOWED, "logs_filter_allowed") { onSelect(LogDecisionFilter.ALLOWED) }
        FilterChip("Cached", active == LogDecisionFilter.PASS, "logs_filter_pass") { onSelect(LogDecisionFilter.PASS) }
    }
}

@Composable
private fun FilterChip(
    label: String,
    selected: Boolean,
    testTag: String,
    onClick: () -> Unit,
) {
    val c = AhTheme.colors
    val variant = if (selected) PillVariant.Solid else PillVariant.Mute
    Pill(
        text = label,
        variant = variant,
        modifier = Modifier.testTag(testTag),
        onClick = onClick,
    )
}

@Composable
private fun StreamHeader(modifier: Modifier = Modifier) {
    val c = AhTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = "Time", style = AhTheme.text.monoCaption, color = c.textDim, modifier = Modifier.width(58.dp))
        Text(text = "Domain / source", style = AhTheme.text.monoCaption, color = c.textDim, modifier = Modifier.weight(1f))
        Text(text = "Type", style = AhTheme.text.monoCaption, color = c.textDim, modifier = Modifier.width(40.dp))
        Text(text = "Res", style = AhTheme.text.monoCaption, color = c.textDim, modifier = Modifier.width(50.dp))
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(c.border2),
        )
    }
}

@Composable
private fun StreamRow(
    row: QueryLogEntity,
    animateEnter: Boolean,
    onAllow: (String) -> Unit,
    onBlock: (String) -> Unit,
) {
    val c = AhTheme.colors
    val resColor = when (row.decision) {
        "blocked" -> c.blocked
        "allowed" -> c.allowed
        "pass" -> c.pass
        else -> c.textMute
    }
    val resTag = when (row.decision) {
        "blocked" -> "BLK"
        "allowed" -> "ALW"
        "pass" -> "CCH"
        else -> "—"
    }
    AnimatedVisibility(
        visible = true,
        enter = slideInVertically(initialOffsetY = { -it / 4 }) + fadeIn(),
        exit = fadeOut(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
                .clickable {
                    when (row.decision) {
                        "blocked" -> onAllow(row.qname)
                        "allowed" -> onBlock(row.qname)
                    }
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = formatTimestamp(row.timestamp),
                style = AhTheme.text.monoData.copy(fontSize = 11.5.sp),
                color = c.textDim,
                modifier = Modifier.width(58.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = row.qname,
                    style = AhTheme.text.monoData.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium),
                    color = c.text,
                    maxLines = 1,
                )
                Text(
                    text = formatAttribution(row),
                    style = AhTheme.text.monoCaption,
                    color = c.textMute,
                    maxLines = 1,
                )
            }
            TypePill(text = qtypeLabel(row.qtype), modifier = Modifier.width(40.dp))
            Box(modifier = Modifier.width(50.dp).padding(start = 6.dp)) {
                ResultPill(text = resTag, color = resColor)
            }
        }
    }
}

@Composable
private fun TypePill(text: String, modifier: Modifier = Modifier) {
    val c = AhTheme.colors
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, c.border, RoundedCornerShape(6.dp))
            .padding(horizontal = 4.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = AhTheme.text.pill.copy(fontSize = 9.5.sp), color = c.textMute)
    }
}

@Composable
private fun ResultPill(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, color, RoundedCornerShape(6.dp))
            .padding(horizontal = 4.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = AhTheme.text.pill.copy(fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold),
            color = color,
        )
    }
}

private fun qtypeLabel(qtype: Int): String = when (qtype) {
    1 -> "A"
    28 -> "AAAA"
    65 -> "HTTPS"
    16 -> "TXT"
    33 -> "SRV"
    else -> qtype.toString()
}

private val timestampFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())

private fun formatTimestamp(epochMs: Long): String =
    runCatching { timestampFormatter.format(Instant.ofEpochMilli(epochMs)) }.getOrDefault("—")

private fun formatAttribution(row: QueryLogEntity): String =
    when {
        row.decision == "blocked" && row.matchedSourceId != null ->
            if (row.matchedSourceId == MATCHED_SUBSCRIBED_LIST_SENTINEL) {
                "subscribed list"
            } else {
                "list #${row.matchedSourceId}"
            }
        row.decision == "blocked" && row.matchedRuleId != null ->
            if (row.matchedRuleId == MATCHED_CUSTOM_RULE_SENTINEL) {
                "custom rule"
            } else {
                "rule #${row.matchedRuleId}"
            }
        row.decision == "blocked" -> "blocklist"
        row.decision == "allowed" && row.answeredFromCache -> "cache · ${row.latencyMs}ms"
        row.decision == "allowed" -> "allowed · ${row.latencyMs}ms"
        row.decision == "pass" -> "tor · ${row.latencyMs}ms"
        else -> "decision=${row.decision}"
    }

private const val MATCHED_SUBSCRIBED_LIST_SENTINEL: Long = -1L
private const val MATCHED_CUSTOM_RULE_SENTINEL: Long = -2L
